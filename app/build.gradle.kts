plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localModelAssets = layout.buildDirectory.dir("generated/local-model-assets")
val prepareLocalModelAssets = tasks.register<Copy>("prepareLocalModelAssets") {
    val toolsDirectory = rootProject.file("tools")
    from(toolsDirectory.resolve("ggml-small.en-q5_1.bin")) {
        into("models")
    }
    from(toolsDirectory.resolve("opus-mt-en-zh")) {
        include(
            "encoder_model_quantized.onnx",
            "decoder_model_merged_quantized.onnx",
            "source.spm",
            "target.spm",
            "tokenizer.json",
            "config.json",
            "generation_config.json",
        )
        into("local_models/opus-mt-en-zh")
    }
    into(localModelAssets)
}

val whisperNativeEnabled =
    providers.gradleProperty("enableWhisperNative").orNull.toBoolean()

android {
    namespace = "com.example.lyriccaptioner"
    compileSdk = 36
    if (whisperNativeEnabled) {
        ndkVersion = "27.3.13750724"
    }

    defaultConfig {
        applicationId = "com.example.lyriccaptioner"
        minSdk = 26
        targetSdk = 35
        versionCode = 4400
        versionName = "4.4.0"

        if (whisperNativeEnabled) {
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++17", "-O3")
                }
            }
        }
        ndk {
            // The checked-in FFmpegKit AAR is verified for both the ARM64 target and the x86_64 emulator.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    sourceSets["main"].assets.srcDir(localModelAssets)

    if (whisperNativeEnabled) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareLocalModelAssets)
}

dependencies {
    implementation(files("libs/ffmpeg-kit-lts-minimal-gpl-16kb-6.1.4.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
