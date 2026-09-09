# PocketAI System Architecture Documentation

## Overview
PocketAI is a local-first, model-agnostic personal AI assistant and future robot control platform.

### Core Tenets
1. **Model-Agnostic Core**: The Android app is never bound to one LLM.
2. **Zero Bundled Models**: Starts with 0MB bundled weights; user brings their own GGUF/model files via Android SAF.
3. **Model-Independent Memory**: User identity, facts, and conversation history are decoupled from model context and stored in a local Room database and Obsidian Markdown vault.
4. **Verified Cloud Endpoints**: Cloud providers (OpenAI, DeepSeek, Anthropic, Ollama, custom) are tested for connectivity and latency before activation.
5. **Secure Hardware Gateway**: Future ESP32 robotics communicate via the PocketAI Server, never exposing cloud credentials on edge hardware.
