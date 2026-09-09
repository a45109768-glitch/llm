cmake_minimum_required(VERSION 3.22.1)
project(pocketai_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/prismml/include/llama.h")
    set(PRISM_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/prismml/include")
    set(PRISM_GGML_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/prismml/ggml/include")
elseif(EXISTS "/tmp/prismml_llama/include/llama.h")
    set(PRISM_INCLUDE_DIR "/tmp/prismml_llama/include")
    set(PRISM_GGML_INCLUDE_DIR "/tmp/prismml_llama/ggml/include")
else()
    message(FATAL_ERROR "PrismML llama.cpp headers not found")
endif()

add_library(pocketai_llama SHARED
    pocketai_llama_jni.cpp
)

target_include_directories(pocketai_llama PRIVATE
    ${PRISM_INCLUDE_DIR}
    ${PRISM_GGML_INCLUDE_DIR}
)

find_library(log-lib log)

# Link against the PrismML shared libraries for the target ABI
target_link_libraries(pocketai_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so
    ${log-lib}
)
