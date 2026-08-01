#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <stdexcept>
#include <string>
#include <thread>
#include <memory>
#include <vector>

#include "whisper.h"

namespace {

constexpr const char * kLogTag = "WhisperJNI";

struct WavAudio {
    int sample_rate = 0;
    int channels = 0;
    std::vector<float> samples;
};

struct JavaCancellationContext {
    JavaVM * vm = nullptr;
    jobject token = nullptr;
    jmethodID is_cancelled = nullptr;
};

bool whisper_abort_callback(void * user_data) {
    auto * cancellation = static_cast<JavaCancellationContext *>(user_data);
    if (cancellation == nullptr || cancellation->vm == nullptr || cancellation->token == nullptr) {
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

    while (input && (!pcm.size() || audio.sample_rate == 0)) {
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

}  // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_lyriccaptioner_processing_WhisperNativeBridge_nativeTranscribe(
    JNIEnv * env,
    jobject,
    jstring model_path_value,
    jstring audio_path_value,
    jint sample_rate,
    jint channels,
    jobject cancellation_token
) {
    if (model_path_value == nullptr || audio_path_value == nullptr) {
        throw_java(env, "java/lang/IllegalArgumentException", "Model and audio paths are required.");
        return nullptr;
    }
    if (sample_rate != 16000 || channels != 1) {
        throw_java(env, "java/lang/IllegalArgumentException", "Whisper input must be 16 kHz mono.");
        return nullptr;
    }

    const char * model_chars = env->GetStringUTFChars(model_path_value, nullptr);
    const char * audio_chars = env->GetStringUTFChars(audio_path_value, nullptr);
    if (model_chars == nullptr || audio_chars == nullptr) {
        if (model_chars != nullptr) {
            env->ReleaseStringUTFChars(model_path_value, model_chars);
        }
        if (audio_chars != nullptr) {
            env->ReleaseStringUTFChars(audio_path_value, audio_chars);
        }
        return nullptr;
    }
    const std::string model_path(model_chars);
    const std::string audio_path(audio_chars);
    env->ReleaseStringUTFChars(model_path_value, model_chars);
    env->ReleaseStringUTFChars(audio_path_value, audio_chars);

    try {
        __android_log_print(ANDROID_LOG_INFO, kLogTag, "event=whisper_jni_transcribe_started");
        WavAudio audio = read_pcm16_wav(audio_path);

        whisper_context_params context_params = whisper_context_default_params();
        context_params.use_gpu = false;
        std::unique_ptr<whisper_context, decltype(&whisper_free)> context(
            whisper_init_from_file_with_params(model_path.c_str(), context_params),
            &whisper_free);
        if (!context) {
            throw std::runtime_error("Could not load the selected Whisper model.");
        }

        whisper_full_params params =
            whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
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

        JavaCancellationContext cancellation;
        if (cancellation_token != nullptr) {
            env->GetJavaVM(&cancellation.vm);
            cancellation.token = env->NewGlobalRef(cancellation_token);
            jclass token_class = env->GetObjectClass(cancellation_token);
            cancellation.is_cancelled = env->GetMethodID(token_class, "isCancelled", "()Z");
            env->DeleteLocalRef(token_class);
            if (cancellation.token == nullptr || cancellation.is_cancelled == nullptr) {
                if (cancellation.token != nullptr) env->DeleteGlobalRef(cancellation.token);
                throw std::runtime_error("Could not prepare Whisper cancellation callback.");
            }
            params.abort_callback = whisper_abort_callback;
            params.abort_callback_user_data = &cancellation;
        }

        const int whisper_result = whisper_full(
                context.get(),
                params,
                audio.samples.data(),
                static_cast<int>(audio.samples.size()));
        const bool cancelled = cancellation.token != nullptr &&
            whisper_abort_callback(&cancellation);
        if (cancellation.token != nullptr) env->DeleteGlobalRef(cancellation.token);
        if (whisper_result != 0) {
            if (cancelled) {
                throw_java(
                    env,
                    "java/util/concurrent/CancellationException",
                    "Whisper transcription cancelled.");
                return nullptr;
            }
            throw std::runtime_error("Whisper transcription failed.");
        }

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

        const int segment_count = whisper_full_n_segments(context.get());
        jobjectArray result = env->NewObjectArray(
            segment_count,
            segment_class,
            nullptr);
        if (result == nullptr) {
            env->DeleteLocalRef(segment_class);
            throw std::runtime_error("Could not allocate Whisper segment array.");
        }

        for (int index = 0; index < segment_count; ++index) {
            const int64_t start_ms = whisper_full_get_segment_t0(context.get(), index) * 10;
            const int64_t end_ms = whisper_full_get_segment_t1(context.get(), index) * 10;
            const std::string text = trim_text(
                whisper_full_get_segment_text(context.get(), index));
            jstring java_text = new_java_string(env, text);
            jobject segment = env->NewObject(
                segment_class,
                constructor,
                static_cast<jlong>(start_ms),
                static_cast<jlong>(std::max(end_ms, start_ms + 10)),
                java_text,
                static_cast<jfloat>(segment_confidence(context.get(), index)));
            env->SetObjectArrayElement(result, index, segment);
            env->DeleteLocalRef(segment);
            env->DeleteLocalRef(java_text);
        }

        env->DeleteLocalRef(segment_class);
        __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "event=whisper_jni_transcribe_completed segmentCount=%d",
            segment_count);
        return result;
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "event=whisper_jni_transcribe_failed");
        throw_java(env, "java/lang/IllegalStateException", error.what());
        return nullptr;
    }
}
