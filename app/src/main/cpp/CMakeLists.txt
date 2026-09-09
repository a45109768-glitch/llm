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

set(PRISM_ABI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

# If the target ABI is arm64-v8a and libllama.so is missing, download prebuilt binaries
if(NOT EXISTS "${PRISM_ABI_DIR}/libllama.so" AND "${ANDROID_ABI}" STREQUAL "arm64-v8a")
    message(STATUS "PrismML native libraries missing in ${PRISM_ABI_DIR}. Downloading...")
    file(MAKE_DIRECTORY "${PRISM_ABI_DIR}")
    set(PRISM_TAR "${CMAKE_BINARY_DIR}/llama-prism-arm64.tar.gz")
    file(DOWNLOAD
        "https://github.com/PrismML-Eng/llama.cpp/releases/download/prism-b10683-d8f26ee/llama-prism-b10683-d8f26ee-bin-android-arm64.tar.gz"
        "${PRISM_TAR}"
        STATUS PRISM_DL_STATUS
        LOG PRISM_DL_LOG
    )
    list(GET PRISM_DL_STATUS 0 PRISM_DL_CODE)
    if(PRISM_DL_CODE EQUAL 0)
        execute_process(
            COMMAND tar -xzf "${PRISM_TAR}" -C "${CMAKE_BINARY_DIR}"
            RESULT_VARIABLE PRISM_TAR_CODE
        )
        if(PRISM_TAR_CODE EQUAL 0)
            file(COPY "${CMAKE_BINARY_DIR}/llama-prism-b10683-d8f26ee/libllama.so" DESTINATION "${PRISM_ABI_DIR}")
            file(COPY "${CMAKE_BINARY_DIR}/llama-prism-b10683-d8f26ee/libggml.so" DESTINATION "${PRISM_ABI_DIR}")
            file(COPY "${CMAKE_BINARY_DIR}/llama-prism-b10683-d8f26ee/libggml-base.so" DESTINATION "${PRISM_ABI_DIR}")
            file(COPY "${CMAKE_BINARY_DIR}/llama-prism-b10683-d8f26ee/libggml-cpu-android_armv8.0_1.so"
                 DESTINATION "${PRISM_ABI_DIR}")
            file(COPY "${CMAKE_BINARY_DIR}/llama-prism-b10683-d8f26ee/libggml-cpu-android_armv8.0_1.so"
                 DESTINATION "${PRISM_ABI_DIR}"
                 RENAME "libggml-cpu.so")
            message(STATUS "PrismML native libraries downloaded and installed successfully.")
        endif()
    endif()
endif()

# Link against the PrismML shared libraries for the target ABI
target_link_libraries(pocketai_llama PRIVATE
    ${PRISM_ABI_DIR}/libllama.so
    ${PRISM_ABI_DIR}/libggml.so
    ${PRISM_ABI_DIR}/libggml-cpu.so
    ${PRISM_ABI_DIR}/libggml-base.so
    ${log-lib}
)
