cmake_minimum_required(VERSION 3.22.1)

project(lyriccaptioner_whisper LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

get_filename_component(
    WHISPER_SOURCE_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp"
    ABSOLUTE
)

if(NOT EXISTS "${WHISPER_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "whisper.cpp source is missing. Run tools/setup-whisper-native.ps1 first.")
endif()

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_SOURCE_DIR}" whisper.cpp)

add_library(
    lyriccaptioner_whisper
    SHARED
    whisper_jni.cpp
)

target_include_directories(
    lyriccaptioner_whisper
    PRIVATE
    "${WHISPER_SOURCE_DIR}/include"
)

target_link_libraries(
    lyriccaptioner_whisper
    PRIVATE
    whisper
    android
    log
)
