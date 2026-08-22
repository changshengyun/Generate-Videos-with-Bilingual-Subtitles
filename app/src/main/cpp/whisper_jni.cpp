#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <iomanip>
#include <iterator>
#include <locale>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

#include "whisper.h"
#include "ggml.h"

namespace {

constexpr const char * kLogTag = "WhisperJNI";

struct WavAudio {
    int sample_rate = 0;
    int channels = 0;
    std::vector<float> samples;
};

struct NativeSession {
    explicit NativeSession(whisper_context * value) : context(value) {}

    std::mutex inference_mutex;
    std::mutex state_mutex;
    whisper_context * context = nullptr;
    bool active = false;
    bool closing = false;
    std::atomic<bool> abort_requested{false};
};

struct JavaCancellationContext {
    JavaVM * vm = nullptr;
    jobject token = nullptr;
    jmethodID is_cancelled = nullptr;
    NativeSession * session = nullptr;
    bool reported = false;
};

class TranscriptionCancelled final : public std::runtime_error {
public:
    TranscriptionCancelled() : std::runtime_error("Whisper transcription cancelled.") {}
};

std::mutex g_registry_mutex;
std::unordered_map<jlong, std::shared_ptr<NativeSession>> g_sessions;
std::atomic<jlong> g_next_handle{1};

bool whisper_abort_callback(void * user_data) {
    auto * cancellation = static_cast<JavaCancellationContext *>(user_data);
    if (cancellation == nullptr) {
        return true;
    }
    if (cancellation->session != nullptr &&
        cancellation->session->abort_requested.load(std::memory_order_acquire)) {
        if (!cancellation->reported) {
            cancellation->reported = true;
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "event=whisper_abort_callback_true source=native");
        }
        return true;
    }
    if (cancellation->vm == nullptr || cancellation->token == nullptr) {
        return false;
    }

    JNIEnv * env = nullptr;
    bool attached = false;
    const jint env_result = cancellation->vm->GetEnv(
        reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (env_result == JNI_EDETACHED) {
        if (cancellation->vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return true;
        }
        attached = true;
    } else if (env_result != JNI_OK || env == nullptr) {
        return true;
    }

    const jboolean cancelled = env->CallBooleanMethod(
        cancellation->token,
        cancellation->is_cancelled);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        if (attached) cancellation->vm->DetachCurrentThread();
        return true;
    }
    if (attached) cancellation->vm->DetachCurrentThread();
    if (cancelled == JNI_TRUE && !cancellation->reported) {
        cancellation->reported = true;
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "event=whisper_abort_callback_true source=token");
    }
    return cancelled == JNI_TRUE;
}

uint16_t read_u16(std::istream & input) {
    uint8_t bytes[2]{};
    input.read(reinterpret_cast<char *>(bytes), sizeof(bytes));
    return static_cast<uint16_t>(bytes[0]) |
           static_cast<uint16_t>(bytes[1] << 8);
}

uint32_t read_u32(std::istream & input) {
    uint8_t bytes[4]{};
    input.read(reinterpret_cast<char *>(bytes), sizeof(bytes));
    return static_cast<uint32_t>(bytes[0]) |
           static_cast<uint32_t>(bytes[1] << 8) |
           static_cast<uint32_t>(bytes[2] << 16) |
           static_cast<uint32_t>(bytes[3] << 24);
}

bool read_tag(std::istream & input, const char * expected) {
    char tag[4]{};
    input.read(tag, sizeof(tag));
    return input && std::equal(std::begin(tag), std::end(tag), expected);
}

WavAudio read_pcm16_wav(const std::string & path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        throw std::runtime_error("Could not open extracted WAV file.");
    }
    if (!read_tag(input, "RIFF")) {
        throw std::runtime_error("Audio file is not RIFF WAV.");
    }
    read_u32(input);
    if (!read_tag(input, "WAVE")) {
        throw std::runtime_error("Audio file has no WAVE signature.");
    }

    uint16_t audio_format = 0;
    uint16_t bits_per_sample = 0;
    WavAudio audio;
    std::vector<int16_t> pcm;

    while (input && (pcm.empty() || audio.sample_rate == 0)) {
        char chunk_id[4]{};
        input.read(chunk_id, sizeof(chunk_id));
        if (!input) {
            break;
        }
        const uint32_t chunk_size = read_u32(input);
        const std::streampos chunk_start = input.tellg();
        const std::string id(chunk_id, sizeof(chunk_id));

        if (id == "fmt ") {
            audio_format = read_u16(input);
            audio.channels = read_u16(input);
            audio.sample_rate = static_cast<int>(read_u32(input));
            read_u32(input);
            read_u16(input);
            bits_per_sample = read_u16(input);
        } else if (id == "data") {
            if (chunk_size % sizeof(int16_t) != 0) {
                throw std::runtime_error("WAV data contains a partial PCM16 sample.");
            }
            pcm.resize(chunk_size / sizeof(int16_t));
            input.read(reinterpret_cast<char *>(pcm.data()), chunk_size);
        }

        input.clear();
        input.seekg(chunk_start + static_cast<std::streamoff>(chunk_size + (chunk_size & 1U)));
    }

    if (audio_format != 1 || bits_per_sample != 16) {
        throw std::runtime_error("Whisper bridge requires PCM16 WAV audio.");
    }
    if (audio.channels != 1 || audio.sample_rate != 16000) {
        throw std::runtime_error("Whisper bridge requires 16 kHz mono audio.");
    }
    if (pcm.empty()) {
        throw std::runtime_error("Extracted WAV contains no audio samples.");
    }

    audio.samples.reserve(pcm.size());
    for (const int16_t sample : pcm) {
        audio.samples.push_back(static_cast<float>(sample) / 32768.0F);
    }
    return audio;
}

void throw_java(JNIEnv * env, const char * class_name, const std::string & message) {
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message.c_str());
        env->DeleteLocalRef(exception_class);
    }
}

std::string java_string(JNIEnv * env, jstring value, const char * field_name) {
    if (value == nullptr) {
        throw std::invalid_argument(std::string(field_name) + " is required.");
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        throw std::runtime_error(std::string("Could not read ") + field_name + ".");
    }
    const std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

float segment_confidence(whisper_context * context, int segment_index) {
    const int token_count = whisper_full_n_tokens(context, segment_index);
    if (token_count <= 0) {
        return 0.0F;
    }
    float total = 0.0F;
    int included = 0;
    const whisper_token end_of_text = whisper_token_eot(context);
    for (int token_index = 0; token_index < token_count; ++token_index) {
        if (whisper_full_get_token_id(context, segment_index, token_index) >= end_of_text) {
            continue;
        }
        const float probability =
            whisper_full_get_token_p(context, segment_index, token_index);
        if (std::isfinite(probability)) {
            total += std::clamp(probability, 0.0F, 1.0F);
            ++included;
        }
    }
    return included == 0 ? 0.0F : total / static_cast<float>(included);
}

std::vector<jchar> utf8_to_utf16(const std::string & text) {
    std::vector<jchar> output;
    output.reserve(text.size());
    for (size_t index = 0; index < text.size();) {
        const uint8_t first = static_cast<uint8_t>(text[index]);
        uint32_t codepoint = 0;
        size_t length = 0;
        if (first < 0x80) {
            codepoint = first;
            length = 1;
        } else if ((first & 0xE0) == 0xC0 && index + 1 < text.size()) {
            codepoint = first & 0x1F;
            length = 2;
        } else if ((first & 0xF0) == 0xE0 && index + 2 < text.size()) {
            codepoint = first & 0x0F;
            length = 3;
        } else if ((first & 0xF8) == 0xF0 && index + 3 < text.size()) {
            codepoint = first & 0x07;
            length = 4;
        } else {
            codepoint = 0xFFFD;
            length = 1;
        }
        bool valid = true;
        for (size_t offset = 1; offset < length; ++offset) {
            const uint8_t continuation = static_cast<uint8_t>(text[index + offset]);
            if ((continuation & 0xC0) != 0x80) {
                valid = false;
                break;
            }
            codepoint = (codepoint << 6) | (continuation & 0x3F);
        }
        if (!valid || codepoint > 0x10FFFF ||
            (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
            codepoint = 0xFFFD;
            length = 1;
        }
        if (codepoint <= 0xFFFF) {
            output.push_back(static_cast<jchar>(codepoint));
        } else {
            codepoint -= 0x10000;
            output.push_back(static_cast<jchar>(0xD800 + (codepoint >> 10)));
            output.push_back(static_cast<jchar>(0xDC00 + (codepoint & 0x3FF)));
        }
        index += length;
    }
    return output;
}

jstring new_java_string(JNIEnv * env, const std::string & text) {
    const std::vector<jchar> utf16 = utf8_to_utf16(text);
    return env->NewString(utf16.data(), static_cast<jsize>(utf16.size()));
}

std::string trim_text(const char * raw_text) {
    std::string text = raw_text == nullptr ? "" : raw_text;
    const auto first = text.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) {
        return "";
    }
    const auto last = text.find_last_not_of(" \t\r\n");
    return text.substr(first, last - first + 1);
}

std::string json_escape(const char * raw_text) {
    const std::string text = raw_text == nullptr ? "" : raw_text;
    constexpr char kHex[] = "0123456789abcdef";
    std::string escaped;
    escaped.reserve(text.size());
    for (const unsigned char value : text) {
        switch (value) {
            case '\"': escaped += "\\\""; break;
            case '\\': escaped += "\\\\"; break;
            case '\b': escaped += "\\b"; break;
            case '\f': escaped += "\\f"; break;
            case '\n': escaped += "\\n"; break;
            case '\r': escaped += "\\r"; break;
            case '\t': escaped += "\\t"; break;
            default:
                if (value < 0x20U || value == 0x7fU) {
                    escaped += "\\u00";
                    escaped += kHex[(value >> 4U) & 0x0fU];
                    escaped += kHex[value & 0x0fU];
                } else {
                    escaped += static_cast<char>(value);
                }
        }
    }
    return escaped;
}

std::string run_raw_diagnostic(
    const std::string & model_path,
    const std::string & audio_path
) {
    const WavAudio audio = read_pcm16_wav(audio_path);
    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;

    const auto init_started = std::chrono::steady_clock::now();
    std::unique_ptr<whisper_context, decltype(&whisper_free)> context(
        whisper_init_from_file_with_params(model_path.c_str(), context_params),
        &whisper_free);
    const auto init_finished = std::chrono::steady_clock::now();
    if (!context) {
        throw std::runtime_error("Could not load the diagnostic Whisper model.");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "auto";
    params.translate = false;
    params.detect_language = false;
    params.no_context = true;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    const unsigned int hardware_threads = std::thread::hardware_concurrency();
    params.n_threads = static_cast<int>(
        std::clamp(hardware_threads == 0 ? 2U : hardware_threads, 1U, 4U));

    __android_log_print(
        ANDROID_LOG_INFO,
        "WhisperDiag",
        "event=context_created whisper_version=%s ggml_version=%s ggml_commit=%s",
        whisper_version(),
        ggml_version(),
        ggml_commit());
    __android_log_print(
        ANDROID_LOG_INFO,
        "WhisperDiag",
        "event=params fresh_context=1 context_reuse=0 no_context=1 language=auto threads=%d",
        params.n_threads);

    const auto inference_started = std::chrono::steady_clock::now();
    const int whisper_result = whisper_full(
        context.get(),
        params,
        audio.samples.data(),
        static_cast<int>(audio.samples.size()));
    const auto inference_finished = std::chrono::steady_clock::now();

    const auto init_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        init_finished - init_started).count();
    const auto inference_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        inference_finished - inference_started).count();
    const int segment_count = whisper_result == 0
        ? whisper_full_n_segments(context.get())
        : 0;

    std::ostringstream report;
    report.imbue(std::locale::classic());
    report << std::fixed << std::setprecision(6);
    report
        << "{\"whisper_version\":\"" << json_escape(whisper_version())
        << "\",\"whisper_commit\":\"" << json_escape(ggml_commit())
        << "\",\"ggml_version\":\"" << json_escape(ggml_version())
        << "\",\"fresh_context\":true,\"context_reuse\":false"
        << ",\"no_context\":true,\"language\":\"auto\",\"threads\":" << params.n_threads
        << ",\"sample_count\":" << audio.samples.size()
        << ",\"wav_duration_ms\":"
        << (audio.samples.size() * 1000ULL / static_cast<uint64_t>(audio.sample_rate))
        << ",\"context_init_ms\":" << init_ms
        << ",\"inference_ms\":" << inference_ms
        << ",\"whisper_full_return_code\":" << whisper_result
        << ",\"segment_count\":" << segment_count
        << ",\"segments\":[";

    for (int segment_index = 0; segment_index < segment_count; ++segment_index) {
        if (segment_index > 0) report << ',';
        const int token_count = whisper_full_n_tokens(context.get(), segment_index);
        double probability_sum = 0.0;
        int probability_count = 0;
        for (int token_index = 0; token_index < token_count; ++token_index) {
            const float probability = whisper_full_get_token_p(
                context.get(), segment_index, token_index);
            if (std::isfinite(probability)) {
                probability_sum += probability;
                ++probability_count;
            }
        }
        const double average_probability = probability_count == 0
            ? 0.0
            : probability_sum / static_cast<double>(probability_count);
        report
            << "{\"index\":" << segment_index
            << ",\"start_ms\":" << whisper_full_get_segment_t0(context.get(), segment_index) * 10
            << ",\"end_ms\":" << whisper_full_get_segment_t1(context.get(), segment_index) * 10
            << ",\"text\":\""
            << json_escape(whisper_full_get_segment_text(context.get(), segment_index))
            << "\",\"no_speech_prob\":"
            << whisper_full_get_segment_no_speech_prob(context.get(), segment_index)
            << ",\"avg_token_prob\":" << average_probability
            << ",\"token_count\":" << token_count
            << ",\"tokens\":[";
        for (int token_index = 0; token_index < token_count; ++token_index) {
            if (token_index > 0) report << ',';
            report
                << "{\"id\":"
                << whisper_full_get_token_id(context.get(), segment_index, token_index)
                << ",\"text\":\""
                << json_escape(whisper_full_get_token_text(context.get(), segment_index, token_index))
                << "\",\"probability\":"
                << whisper_full_get_token_p(context.get(), segment_index, token_index)
                << '}';
        }
        report << "]}";
    }
    report << "]}";

    __android_log_print(
        ANDROID_LOG_INFO,
        "WhisperDiag",
        "event=whisper_full_exited result=%d inference_ms=%lld segment_count=%d",
        whisper_result,
        static_cast<long long>(inference_ms),
        segment_count);
    context.reset();
    __android_log_print(ANDROID_LOG_INFO, "WhisperDiag", "event=context_freed");
    return report.str();
}

std::shared_ptr<NativeSession> find_session(jlong handle) {
    if (handle <= 0) {
        return nullptr;
    }
    std::lock_guard<std::mutex> registry_lock(g_registry_mutex);
    const auto entry = g_sessions.find(handle);
    return entry == g_sessions.end() ? nullptr : entry->second;
}

jlong create_session(const std::string & model_path) {
    whisper_context_params context_params = whisper_context_default_params();
    context_params.use_gpu = false;
    std::unique_ptr<whisper_context, decltype(&whisper_free)> pending_context(
        whisper_init_from_file_with_params(model_path.c_str(), context_params),
        &whisper_free);
    if (!pending_context) {
        throw std::runtime_error("Could not load the selected Whisper model.");
    }

    const std::shared_ptr<NativeSession> session =
        std::make_shared<NativeSession>(pending_context.get());
    const jlong handle = g_next_handle.fetch_add(1, std::memory_order_relaxed);
    if (handle <= 0) {
        throw std::runtime_error("Whisper native handle space is exhausted.");
    }
    {
        std::lock_guard<std::mutex> registry_lock(g_registry_mutex);
        g_sessions.emplace(handle, session);
    }
    pending_context.release();
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "event=whisper_context_created handle=%lld",
        static_cast<long long>(handle));
    return handle;
}

void erase_session(jlong handle, const std::shared_ptr<NativeSession> & session) {
    std::lock_guard<std::mutex> registry_lock(g_registry_mutex);
    const auto entry = g_sessions.find(handle);
    if (entry != g_sessions.end() && entry->second == session) {
        g_sessions.erase(entry);
    }
}

void release_locked_session(jlong handle, const std::shared_ptr<NativeSession> & session) {
    whisper_context * context = nullptr;
    {
        std::lock_guard<std::mutex> state_lock(session->state_mutex);
        session->closing = true;
        session->active = false;
        session->abort_requested.store(false, std::memory_order_release);
        context = session->context;
        session->context = nullptr;
    }
    erase_session(handle, session);
    if (context != nullptr) {
        whisper_free(context);
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_context_freed handle=%lld",
            static_cast<long long>(handle));
    }
}

void free_session(jlong handle) {
    const std::shared_ptr<NativeSession> session = find_session(handle);
    if (!session) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_context_free_ignored handle=%lld",
            static_cast<long long>(handle));
        return;
    }
    {
        std::lock_guard<std::mutex> state_lock(session->state_mutex);
        session->closing = true;
    }
    std::unique_lock<std::mutex> inference_lock(session->inference_mutex);
    release_locked_session(handle, session);
}

void request_abort(jlong handle) {
    const std::shared_ptr<NativeSession> session = find_session(handle);
    if (!session) {
        throw std::invalid_argument("Unknown or released Whisper context handle.");
    }
    bool active = false;
    {
        std::lock_guard<std::mutex> state_lock(session->state_mutex);
        if (session->closing || session->context == nullptr) {
            throw std::invalid_argument("Unknown or released Whisper context handle.");
        }
        active = session->active;
        if (active) {
            session->abort_requested.store(true, std::memory_order_release);
        }
    }
    __android_log_print(
        ANDROID_LOG_INFO,
        kLogTag,
        "event=whisper_abort_requested handle=%lld active=%d",
        static_cast<long long>(handle),
        active ? 1 : 0);
}

whisper_full_params make_full_params(JavaCancellationContext * cancellation) {
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "auto";
    params.translate = false;
    params.detect_language = false;
    params.no_context = false;
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    const unsigned int hardware_threads = std::thread::hardware_concurrency();
    params.n_threads = static_cast<int>(
        std::clamp(hardware_threads == 0 ? 2U : hardware_threads, 1U, 4U));
    params.abort_callback = whisper_abort_callback;
    params.abort_callback_user_data = cancellation;
    return params;
}

void prepare_cancellation(
    JNIEnv * env,
    jobject cancellation_token,
    NativeSession * session,
    JavaCancellationContext * cancellation
) {
    cancellation->session = session;
    if (cancellation_token == nullptr) {
        return;
    }
    if (env->GetJavaVM(&cancellation->vm) != JNI_OK) {
        throw std::runtime_error("Could not access the Java VM for Whisper cancellation.");
    }
    cancellation->token = env->NewGlobalRef(cancellation_token);
    jclass token_class = env->GetObjectClass(cancellation_token);
    cancellation->is_cancelled = env->GetMethodID(token_class, "isCancelled", "()Z");
    env->DeleteLocalRef(token_class);
    if (cancellation->token == nullptr || cancellation->is_cancelled == nullptr) {
        if (cancellation->token != nullptr) {
            env->DeleteGlobalRef(cancellation->token);
            cancellation->token = nullptr;
        }
        throw std::runtime_error("Could not prepare Whisper cancellation callback.");
    }
}

void clear_cancellation(JNIEnv * env, JavaCancellationContext * cancellation) {
    if (cancellation->token != nullptr) {
        env->DeleteGlobalRef(cancellation->token);
        cancellation->token = nullptr;
    }
}

jobjectArray build_segments(JNIEnv * env, whisper_context * context) {
    jclass segment_class = env->FindClass(
        "com/example/lyriccaptioner/processing/WhisperSegment");
    if (segment_class == nullptr) {
        throw std::runtime_error("Could not find WhisperSegment class.");
    }
    jmethodID constructor = env->GetMethodID(
        segment_class,
        "<init>",
        "(JJLjava/lang/String;F)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(segment_class);
        throw std::runtime_error("Could not find WhisperSegment constructor.");
    }

    const int segment_count = whisper_full_n_segments(context);
    jobjectArray result = env->NewObjectArray(segment_count, segment_class, nullptr);
    if (result == nullptr) {
        env->DeleteLocalRef(segment_class);
        throw std::runtime_error("Could not allocate Whisper segment array.");
    }

    for (int index = 0; index < segment_count; ++index) {
        const int64_t start_ms = whisper_full_get_segment_t0(context, index) * 10;
        const int64_t end_ms = whisper_full_get_segment_t1(context, index) * 10;
        const std::string text = trim_text(whisper_full_get_segment_text(context, index));
        jstring java_text = new_java_string(env, text);
        if (java_text == nullptr) {
            env->DeleteLocalRef(segment_class);
            throw std::runtime_error("Could not allocate Whisper segment text.");
        }
        jobject segment = env->NewObject(
            segment_class,
            constructor,
            static_cast<jlong>(start_ms),
            static_cast<jlong>(std::max(end_ms, start_ms + 10)),
            java_text,
            static_cast<jfloat>(segment_confidence(context, index)));
        env->DeleteLocalRef(java_text);
        if (segment == nullptr) {
            env->DeleteLocalRef(segment_class);
            throw std::runtime_error("Could not allocate Whisper segment.");
        }
        env->SetObjectArrayElement(result, index, segment);
        env->DeleteLocalRef(segment);
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(segment_class);
            throw std::runtime_error("Could not populate Whisper segment array.");
        }
    }

    env->DeleteLocalRef(segment_class);
    return result;
}

jobjectArray transcribe_session(
    JNIEnv * env,
    jlong handle,
    const std::string & audio_path,
    jint sample_rate,
    jint channels,
    jobject cancellation_token
) {
    if (sample_rate != 16000 || channels != 1) {
        throw std::invalid_argument("Whisper input must be 16 kHz mono.");
    }
    const std::shared_ptr<NativeSession> session = find_session(handle);
    if (!session) {
        throw std::invalid_argument("Unknown or released Whisper context handle.");
    }

    std::unique_lock<std::mutex> inference_lock(session->inference_mutex);
    whisper_context * context = nullptr;
    {
        std::lock_guard<std::mutex> state_lock(session->state_mutex);
        if (session->closing || session->context == nullptr) {
            throw std::invalid_argument("Unknown or released Whisper context handle.");
        }
        session->abort_requested.store(false, std::memory_order_release);
        session->active = true;
        context = session->context;
    }

    JavaCancellationContext cancellation;
    try {
        WavAudio audio = read_pcm16_wav(audio_path);
        prepare_cancellation(env, cancellation_token, session.get(), &cancellation);
        whisper_full_params params = make_full_params(&cancellation);
        if (whisper_abort_callback(&cancellation)) {
            throw TranscriptionCancelled();
        }

        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_jni_inference_started handle=%lld",
            static_cast<long long>(handle));
        const int whisper_result = whisper_full(
            context,
            params,
            audio.samples.data(),
            static_cast<int>(audio.samples.size()));
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_full_exited handle=%lld result=%d",
            static_cast<long long>(handle),
            whisper_result);

        const bool cancelled = whisper_abort_callback(&cancellation);
        if (whisper_result != 0) {
            if (cancelled) {
                throw TranscriptionCancelled();
            }
            throw std::runtime_error("Whisper transcription failed.");
        }
        if (cancelled) {
            throw TranscriptionCancelled();
        }

        jobjectArray result = build_segments(env, context);
        if (whisper_abort_callback(&cancellation)) {
            env->DeleteLocalRef(result);
            throw TranscriptionCancelled();
        }
        clear_cancellation(env, &cancellation);
        {
            std::lock_guard<std::mutex> state_lock(session->state_mutex);
            session->active = false;
            session->abort_requested.store(false, std::memory_order_release);
        }
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_jni_transcribe_completed handle=%lld",
            static_cast<long long>(handle));
        return result;
    } catch (...) {
        clear_cancellation(env, &cancellation);
        {
            std::lock_guard<std::mutex> state_lock(session->state_mutex);
            session->active = false;
            session->closing = true;
            session->abort_requested.store(false, std::memory_order_release);
        }
        // The handle becomes immediately non-reusable. The Kotlin runtime calls
        // freeContext only after its inference worker has completely returned; that call
        // then waits on inference_mutex before invoking whisper_free.
        throw;
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_lyriccaptioner_processing_WhisperNativeSessionBridge_nativeCreateContext(
    JNIEnv * env,
    jobject,
    jstring model_path_value
) {
    try {
        return create_session(java_string(env, model_path_value, "Model path"));
    } catch (const std::invalid_argument & error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "event=whisper_context_create_failed");
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
    return 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_lyriccaptioner_processing_WhisperNativeSessionBridge_nativeTranscribeContext(
    JNIEnv * env,
    jobject,
    jlong context_handle,
    jstring audio_path_value,
    jint sample_rate,
    jint channels,
    jobject cancellation_token
) {
    try {
        const std::string audio_path = java_string(env, audio_path_value, "Audio path");
        return transcribe_session(
            env,
            context_handle,
            audio_path,
            sample_rate,
            channels,
            cancellation_token);
    } catch (const TranscriptionCancelled & error) {
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_jni_transcribe_cancelled handle=%lld",
            static_cast<long long>(context_handle));
        throw_java(env, "java/util/concurrent/CancellationException", error.what());
    } catch (const std::invalid_argument & error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception & error) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            kLogTag,
            "event=whisper_jni_transcribe_failed handle=%lld",
            static_cast<long long>(context_handle));
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lyriccaptioner_processing_WhisperNativeSessionBridge_nativeRequestAbort(
    JNIEnv * env,
    jobject,
    jlong context_handle
) {
    try {
        request_abort(context_handle);
    } catch (const std::exception & error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_lyriccaptioner_processing_WhisperNativeSessionBridge_nativeFreeContext(
    JNIEnv *,
    jobject,
    jlong context_handle
) {
    free_session(context_handle);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_lyriccaptioner_AsrDiagnosticInstrumentation_nativeRunWhisperDebug(
    JNIEnv * env,
    jobject,
    jstring model_path_value,
    jstring audio_path_value
) {
    try {
        const std::string model_path = java_string(env, model_path_value, "Model path");
        const std::string audio_path = java_string(env, audio_path_value, "Audio path");
        return new_java_string(env, run_raw_diagnostic(model_path, audio_path));
    } catch (const std::invalid_argument & error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception & error) {
        __android_log_print(
            ANDROID_LOG_ERROR,
            "WhisperDiag",
            "event=diagnostic_failed error=%s",
            error.what());
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
    return nullptr;
}
