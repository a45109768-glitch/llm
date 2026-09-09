#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <deque>
#include <mutex>
#include <atomic>
#include <sstream>
#include <algorithm>
#include <cstring>
#include <sys/stat.h>
#include <unistd.h>
#include <cerrno>

#include "llama.h"
#include "ggml.h"
#include "ggml-backend.h"

#define TAG "PocketAI_PrismLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global state for logging and control
static std::mutex g_log_mutex;
static std::string g_last_error_log;
static std::deque<std::string> g_recent_log_lines;
static std::atomic<bool> g_stop_requested(false);

// Callback to capture llama.cpp and ggml logs
static void llama_log_callback(enum ggml_log_level level, const char * text, void * /* user_data */) {
    if (!text) return;

    std::lock_guard<std::mutex> lock(g_log_mutex);

    // Keep ring buffer of last 50 log lines for detailed diagnostics
    if (g_recent_log_lines.size() >= 50) {
        g_recent_log_lines.pop_front();
    }
    g_recent_log_lines.push_back(std::string(text));

    // Capture warnings, errors, and error continuation lines
    if (level == GGML_LOG_LEVEL_ERROR || level == GGML_LOG_LEVEL_WARN ||
        (level == GGML_LOG_LEVEL_CONT && !g_last_error_log.empty())) {
        if (g_last_error_log.size() < 8192) {
            g_last_error_log += text;
        }
        if (level == GGML_LOG_LEVEL_ERROR) {
            LOGE("%s", text);
        } else if (level == GGML_LOG_LEVEL_WARN) {
            LOGW("%s", text);
        } else {
            LOGI("%s", text);
        }
    } else {
        LOGI("%s", text);
    }
}

static void clear_last_error() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    g_last_error_log.clear();
    g_recent_log_lines.clear();
}

static std::string get_last_error() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    return g_last_error_log;
}

static std::string get_recent_logs() {
    std::lock_guard<std::mutex> lock(g_log_mutex);
    std::string combined;
    for (const auto & line : g_recent_log_lines) {
        combined += line;
    }
    return combined;
}

static void register_all_log_callbacks() {
    ggml_log_set(llama_log_callback, nullptr);
    llama_log_set(llama_log_callback, nullptr);
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeInitBackend(JNIEnv * /* env */, jclass /* clazz */) {
    LOGI("Initializing PrismML llama backend...");
    clear_last_error();
    register_all_log_callbacks();
    ggml_backend_load_all();
    llama_backend_init();
    LOGI("PrismML llama backend initialized successfully.");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeGetBackendInfo(JNIEnv *env, jclass /* clazz */) {
    std::ostringstream oss;
#if defined(__aarch64__)
    const char * abi = "arm64-v8a";
#elif defined(__x86_64__)
    const char * abi = "x86_64";
#else
    const char * abi = "unknown";
#endif
    oss << "PrismML llama.cpp (branch: prism, commit: d8f26ee, ABI: " << abi
        << ", supports_mmap: " << (llama_supports_mmap() ? "true" : "false") << ")";
    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT jlong JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeLoadModel(
    JNIEnv *env, jclass /* clazz */,
    jstring jModelPath, jint nGpuLayers, jboolean useMmap, jobjectArray errorHolder) {

    if (!jModelPath) {
        LOGE("Model path is null");
        return 0;
    }

    const char * model_path = env->GetStringUTFChars(jModelPath, nullptr);
    LOGI("nativeLoadModel: Loading model from path '%s' (gpu_layers: %d, mmap: %d)",
         model_path, nGpuLayers, useMmap);

    clear_last_error();
    register_all_log_callbacks();

    // Verify file accessibility and size before attempting native load
    struct stat st;
    int stat_res = stat(model_path, &st);
    int stat_errno = errno;
    if (stat_res != 0) {
        LOGE("Model file stat failed for '%s': errno=%d (%s)", model_path, stat_errno, strerror(stat_errno));
    } else {
        LOGI("Model file info: path='%s', size=%lld bytes (%.2f MB), mode=%o, read_access=%d",
             model_path, (long long)st.st_size, (double)st.st_size / (1024.0 * 1024.0),
             st.st_mode, access(model_path, R_OK));
        if (st.st_size == 0) {
            LOGE("Model file '%s' is empty (0 bytes)!", model_path);
        }
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;
    model_params.load_mode = useMmap ? LLAMA_LOAD_MODE_MMAP : LLAMA_LOAD_MODE_NONE;

    struct llama_model * model = nullptr;
    std::string captured_exception;

    try {
        model = llama_model_load_from_file(model_path, model_params);
    } catch (const std::exception & e) {
        captured_exception = e.what();
        LOGE("llama_model_load_from_file threw C++ exception: %s", e.what());
    } catch (...) {
        captured_exception = "Unknown C++ exception thrown during llama_model_load_from_file";
        LOGE("llama_model_load_from_file threw an unknown C++ exception");
    }

    // Fallback: If mmap loading returned null, attempt non-mmap load
    if (!model && useMmap) {
        int mmap_errno = errno;
        LOGW("Initial load with MMAP returned null, errno=%d (%s). Retrying with load_mode = LLAMA_LOAD_MODE_NONE...",
             mmap_errno, strerror(mmap_errno));
        model_params.load_mode = LLAMA_LOAD_MODE_NONE;
        try {
            model = llama_model_load_from_file(model_path, model_params);
        } catch (const std::exception & e) {
            captured_exception = e.what();
            LOGE("Fallback load without mmap threw C++ exception: %s", e.what());
        } catch (...) {
            captured_exception = "Unknown C++ exception during fallback model load without mmap";
            LOGE("Fallback load threw unknown exception");
        }
        if (model) {
            LOGI("Model loaded successfully using non-mmap fallback (LLAMA_LOAD_MODE_NONE)!");
        }
    }

    env->ReleaseStringUTFChars(jModelPath, model_path);

    if (!model) {
        std::string err = get_last_error();
        if (!captured_exception.empty()) {
            if (!err.empty()) err += " | Exception: ";
            err += captured_exception;
        }
        if (err.empty()) {
            std::string recent = get_recent_logs();
            if (!recent.empty()) {
                err = "Engine returned null. Recent logs: " + recent;
            }
        }
        if (err.empty()) {
            if (stat_res != 0) {
                std::ostringstream ss;
                ss << "Model file not accessible: '" << model_path << "' (stat errno=" << stat_errno << ": " << strerror(stat_errno) << ")";
                err = ss.str();
            } else if (st.st_size == 0) {
                std::ostringstream ss;
                ss << "Model file is empty (0 bytes): '" << model_path << "'";
                err = ss.str();
            } else {
                int final_errno = errno;
                std::ostringstream ss;
                ss << "Native llama.cpp failed to initialize context from GGUF weights for '" << model_path
                   << "' (size: " << st.st_size << " bytes, errno=" << final_errno << ": " << strerror(final_errno) << ")";
                err = ss.str();
            }
        }
        LOGE("Failed to load model: %s", err.c_str());

        if (errorHolder && env->GetArrayLength(errorHolder) > 0) {
            jstring jErr = env->NewStringUTF(err.c_str());
            env->SetObjectArrayElement(errorHolder, 0, jErr);
            env->DeleteLocalRef(jErr);
        }
        return 0;
    }

    LOGI("Model loaded successfully into native memory. Handle: %p", model);
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jlong JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeCreateContext(
    JNIEnv *env, jclass /* clazz */,
    jlong modelHandle, jint nCtx, jint nBatch, jint nThreads, jobjectArray errorHolder) {

    struct llama_model * model = reinterpret_cast<struct llama_model *>(modelHandle);
    if (!model) {
        LOGE("Cannot create context: Invalid model handle");
        return 0;
    }

    clear_last_error();
    register_all_log_callbacks();

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (nCtx > 0) ? static_cast<uint32_t>(nCtx) : 2048;
    ctx_params.n_batch = (nBatch > 0) ? static_cast<uint32_t>(nBatch) : 512;
    ctx_params.n_ubatch = ctx_params.n_batch;
    ctx_params.n_threads = (nThreads > 0) ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;
    ctx_params.no_perf = false;

    LOGI("Creating llama context: n_ctx=%u, n_batch=%u, n_threads=%d",
         ctx_params.n_ctx, ctx_params.n_batch, ctx_params.n_threads);

    struct llama_context * ctx = nullptr;
    std::string captured_exception;

    try {
        ctx = llama_init_from_model(model, ctx_params);
    } catch (const std::exception & e) {
        captured_exception = e.what();
        LOGE("llama_init_from_model threw C++ exception: %s", e.what());
    } catch (...) {
        captured_exception = "Unknown C++ exception thrown during llama_init_from_model";
        LOGE("llama_init_from_model threw unknown C++ exception");
    }

    if (!ctx) {
        std::string err = get_last_error();
        if (!captured_exception.empty()) {
            if (!err.empty()) err += " | Exception: ";
            err += captured_exception;
        }
        if (err.empty()) {
            std::string recent = get_recent_logs();
            if (!recent.empty()) {
                err = "Context creation failed. Recent logs: " + recent;
            } else {
                std::ostringstream ss;
                ss << "llama_init_from_model failed to allocate context memory (n_ctx=" << ctx_params.n_ctx
                   << ", n_batch=" << ctx_params.n_batch << ", n_threads=" << ctx_params.n_threads << ")";
                err = ss.str();
            }
        }
        LOGE("Failed to create context: %s", err.c_str());

        if (errorHolder && env->GetArrayLength(errorHolder) > 0) {
            jstring jErr = env->NewStringUTF(err.c_str());
            env->SetObjectArrayElement(errorHolder, 0, jErr);
            env->DeleteLocalRef(jErr);
        }
        return 0;
    }

    LOGI("Llama context created successfully. Handle: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeFreeContext(
    JNIEnv * /* env */, jclass /* clazz */, jlong contextHandle) {
    if (contextHandle != 0) {
        struct llama_context * ctx = reinterpret_cast<struct llama_context *>(contextHandle);
        LOGI("Freeing native llama context: %p", ctx);
        llama_free(ctx);
    }
}

JNIEXPORT void JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeFreeModel(
    JNIEnv * /* env */, jclass /* clazz */, jlong modelHandle) {
    if (modelHandle != 0) {
        struct llama_model * model = reinterpret_cast<struct llama_model *>(modelHandle);
        LOGI("Freeing native llama model: %p", model);
        llama_model_free(model);
    }
}

JNIEXPORT jstring JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeGetModelInfo(
    JNIEnv *env, jclass /* clazz */, jlong modelHandle) {

    struct llama_model * model = reinterpret_cast<struct llama_model *>(modelHandle);
    if (!model) return nullptr;

    char desc_buf[512] = {0};
    llama_model_desc(model, desc_buf, sizeof(desc_buf));

    char arch_buf[128] = {0};
    llama_model_meta_val_str(model, "general.architecture", arch_buf, sizeof(arch_buf));

    char ftype_buf[64] = {0};
    llama_model_meta_val_str(model, "general.file_type", ftype_buf, sizeof(ftype_buf));

    uint64_t n_params = llama_model_n_params(model);

    std::ostringstream oss;
    oss << "Description: " << desc_buf << "\n"
        << "Architecture: " << (arch_buf[0] ? arch_buf : "transformer") << "\n"
        << "Parameters: " << n_params << "\n"
        << "FileType: " << ftype_buf;

    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT void JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeStopGeneration(
    JNIEnv * /* env */, jclass /* clazz */) {
    LOGI("Stop generation requested natively.");
    g_stop_requested.store(true);
}

JNIEXPORT jboolean JNICALL
Java_com_pocketai_local_engines_NativeLlamaJni_nativeGenerateStream(
    JNIEnv *env, jclass /* clazz */,
    jlong contextHandle, jlong modelHandle,
    jstring jPrompt, jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty,
    jint maxTokens, jobjectArray jStopTokens, jobject callback) {

    struct llama_context * ctx = reinterpret_cast<struct llama_context *>(contextHandle);
    struct llama_model * model = reinterpret_cast<struct llama_model *>(modelHandle);

    if (!ctx || !model || !jPrompt || !callback) {
        LOGE("nativeGenerateStream: Invalid null arguments");
        return JNI_FALSE;
    }

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    if (!onTokenMethod) {
        LOGE("nativeGenerateStream: Failed to find onToken(String) method");
        return JNI_FALSE;
    }

    // Collect stop tokens
    std::vector<std::string> stop_tokens;
    if (jStopTokens) {
        jsize len = env->GetArrayLength(jStopTokens);
        for (jsize i = 0; i < len; i++) {
            jstring str = (jstring) env->GetObjectArrayElement(jStopTokens, i);
            if (str) {
                const char * cstr = env->GetStringUTFChars(str, nullptr);
                stop_tokens.push_back(std::string(cstr));
                env->ReleaseStringUTFChars(str, cstr);
                env->DeleteLocalRef(str);
            }
        }
    }

    const char * prompt_c = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt_str(prompt_c);
    env->ReleaseStringUTFChars(jPrompt, prompt_c);

    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("Failed to get model vocab");
        return JNI_FALSE;
    }

    // Clear KV cache / memory before starting new sequence
    llama_memory_t mem = llama_get_memory(ctx);
    if (mem) {
        llama_memory_clear(mem, true);
    }

    // Tokenize prompt
    try {
        const int n_prompt = -llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(), nullptr, 0, true, true);
        if (n_prompt <= 0) {
            LOGE("Failed to determine prompt token count (result: %d)", n_prompt);
            return JNI_FALSE;
        }

        std::vector<llama_token> prompt_tokens(n_prompt);
        if (llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
            LOGE("Failed to tokenize prompt");
            return JNI_FALSE;
        }

        uint32_t n_ctx = llama_n_ctx(ctx);
        if (prompt_tokens.size() >= n_ctx) {
            LOGE("Prompt too long for context size: %zu tokens vs n_ctx=%u", prompt_tokens.size(), n_ctx);
            return JNI_FALSE;
        }

        // Initialize sampler chain
        struct llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        sparams.no_perf = false;
        struct llama_sampler * smpl = llama_sampler_chain_init(sparams);

        if (repeatPenalty > 1.0f) {
            llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
                llama_vocab_n_tokens(vocab), 64, repeatPenalty, 0.0f, 0.0f));
        }
        if (topK > 0) {
            llama_sampler_chain_add(smpl, llama_sampler_init_top_k(topK));
        }
        if (topP > 0.0f && topP < 1.0f) {
            llama_sampler_chain_add(smpl, llama_sampler_init_top_p(topP, 1));
        }
        if (temperature > 0.0f) {
            llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(smpl, llama_sampler_init_dist(1337));
        } else {
            llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
        }

        // Ingest prompt in batches
        g_stop_requested.store(false);
        uint32_t batch_size = llama_n_batch(ctx);
        if (batch_size <= 0) batch_size = 512;

        for (size_t i = 0; i < prompt_tokens.size(); i += batch_size) {
            if (g_stop_requested.load()) {
                LOGI("Generation cancelled during prompt processing.");
                llama_sampler_free(smpl);
                return JNI_TRUE;
            }
            size_t cur_tokens = std::min((size_t)batch_size, prompt_tokens.size() - i);
            struct llama_batch batch = llama_batch_get_one(prompt_tokens.data() + i, cur_tokens);
            if (llama_decode(ctx, batch) != 0) {
                LOGE("llama_decode failed on prompt ingestion at offset %zu", i);
                llama_sampler_free(smpl);
                return JNI_FALSE;
            }
        }

        // Generation loop
        int max_gen = (maxTokens > 0) ? maxTokens : 1024;
        uint32_t current_pos = static_cast<uint32_t>(prompt_tokens.size());
        std::string stream_acc;

        for (int step = 0; step < max_gen && current_pos < n_ctx; step++) {
            if (g_stop_requested.load()) {
                LOGI("Generation stopped by user request at step %d", step);
                break;
            }

            llama_token new_token_id = llama_sampler_sample(smpl, ctx, -1);
            llama_sampler_accept(smpl, new_token_id);

            if (llama_vocab_is_eog(vocab, new_token_id)) {
                LOGI("EOG token reached at step %d", step);
                break;
            }

            char piece_buf[256] = {0};
            int piece_len = llama_token_to_piece(vocab, new_token_id, piece_buf, sizeof(piece_buf), 0, false);
            if (piece_len > 0) {
                std::string piece_str(piece_buf, piece_len);
                stream_acc += piece_str;

                // Check if any custom stop token is encountered
                bool matched_stop = false;
                for (const auto & st : stop_tokens) {
                    if (!st.empty() && stream_acc.find(st) != std::string::npos) {
                        matched_stop = true;
                        break;
                    }
                }
                if (matched_stop) {
                    LOGI("Matched stop token, ending generation.");
                    break;
                }

                jstring jPiece = env->NewStringUTF(piece_str.c_str());
                env->CallVoidMethod(callback, onTokenMethod, jPiece);
                env->DeleteLocalRef(jPiece);

                if (env->ExceptionCheck()) {
                    LOGW("Exception in Kotlin token callback; aborting generation loop.");
                    env->ExceptionClear();
                    break;
                }
            }

            struct llama_batch next_batch = llama_batch_get_one(&new_token_id, 1);
            if (llama_decode(ctx, next_batch) != 0) {
                LOGE("llama_decode failed on token generation at step %d", step);
                break;
            }
            current_pos++;
        }

        llama_sampler_free(smpl);
        LOGI("Generation completed successfully.");
        return JNI_TRUE;
    } catch (const std::exception & e) {
        LOGE("nativeGenerateStream caught exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("nativeGenerateStream caught unknown exception");
        return JNI_FALSE;
    }
}

} // extern "C"
