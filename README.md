# DeepSeek Agent — Native Android AI Harness

DeepSeek Agent is a native Android (Kotlin + Jetpack Compose + Material 3) autonomous AI agent harness designed around account-first authentication, local-first three-layer memory, modular tool execution, and transparent Android system integration.

---

## 1. Architecture Overview

The codebase is organized into focused, modular packages under `com.example`:

- **`authentication/`**:
  - `AuthState` sealed state machine (`NotAuthenticated`, `Authenticating`, `Authenticated`, `SessionExpired`, `AuthenticationFailed`).
  - `KeystoreVault` using `AndroidKeyStore` (`AES/GCM/NoPadding`, 256-bit) to encrypt optional credentials and metadata locally. Credentials are never logged, never injected into prompts, and never exposed to tools.
  - `AuthManager` supporting **Priority 1: Official DeepSeek Web Session** (`https://chat.deepseek.com` inside an isolated embedded `WebView`) and **Priority 2: Official DeepSeek API / Compatible Endpoint** (optional fallback).
- **`model/`**:
  - `ModelProvider` abstraction separating model transport from the agent loop.
  - `DeepSeekWebBridgeProvider`: Honest Web-Bridge orchestration for Priority 1 web sessions plus deterministic local tool-chain support.
  - `OpenAiCompatibleModelProvider`: Full `OkHttp` + `kotlinx.serialization` client for `https://api.deepseek.com` or compatible endpoints with automated `tool_calls` chaining and cancellation.
- **`agent/` & `execution/`**:
  - `AgentController`: Loop-based autonomous engine supporting multi-step task decomposition, tool chaining, `Autonomous` / `Balanced` / `Strict` approval checkpoints, retry limits, per-step timeouts, and immediate user cancellation (`Stop`).
  - `TaskPlanner`, `ExecutionMonitor`, `ErrorRecoveryManager`, `ConversationManager`.
  - `AgentWorkManager`: `ReminderWorker` and `BackgroundAgentWorker` powered by `WorkManager`.
- **`tools/`**:
  - **Research**: `web_search` (DuckDuckGo HTML / Wikipedia OpenSearch), `fetch_webpage` (HTTPS-only clean extraction), `generate_report` (`.md` / `.pdf`).
  - **File Management**: `read_document` (SAF `content://` & workspace), `analyze_pdf` (`PdfRenderer` + pure-Kotlin stream extractor), `create_file` (TXT, Markdown, HTML, native `PdfDocument`), `list_workspace_files`.
  - **Coding**: `run_javascript` (hardened offscreen `WebView` sandbox with network/file access disabled), `generate_html_app` (live sandboxed HTML5 preview).
  - **Android Integration**: `open_url`, `open_app`, `share_content` (`FileProvider` Share Sheet), `clipboard_access`, `create_notification` (`POST_NOTIFICATIONS` + `WorkManager`), `accessibility_interact` (opt-in `AgentAccessibilityService`).
  - **Productivity**: `manage_task_list`, `save_memory`.
- **`memory/` & `database/`**:
  - Local `Room` database (`conversations`, `messages`, `task_plans`, `subtasks`, `memory_entries`, `execution_logs`).
  - Three memory layers: **Short-Term** (active conversation), **Task Memory** (plan, subtasks, task summaries), and **Long-Term** (user-approved facts & preferences with inspect, edit, approve, and delete UI).
- **`permissions/` & `settings/`**:
  - `PermissionManager`: Enforces tool toggles, `AutonomyLevel`, HTTPS/SSRF URL validation, optional domain allowlisting, runtime OS permissions, and opt-in Accessibility consent.
  - `SettingsStore`: Jetpack DataStore Preferences.

---

## 2. Authentication Limitations & Transparency

- DeepSeek does **not** currently offer a public third-party OAuth flow that converts a consumer `chat.deepseek.com` login into programmatic REST API access.
- Accordingly, **Priority 1 (Official DeepSeek Web Session)** embeds the official `chat.deepseek.com` experience inside an isolated WebView without requesting/storing your password, without reading cookies from other apps, and without reverse-engineering private endpoints.
- In Web Session mode, the agent never pretends web login grants API access; instead, it offers a transparent **Web-Bridge** workflow (prepare structured prompts for your signed-in WebView and parse tool JSON replies back into the local loop) alongside direct on-device tool execution.
- Configuring **Priority 2 (Official DeepSeek API or compatible endpoint)** in the Account tab enables fully automated end-to-end LLM `tool_calls` execution.

---

## 3. Build & Installation Instructions

### Requirements
- Android Studio (Ladybug / Meerkat or newer) with Android SDK 36
- JDK 11+
- Target device or emulator running **Android 8.0 (API 26)+** (minimum SDK 24 supported, ARM64-v8a compatible)

### Build APK via Gradle
```bash
gradle :app:assembleDebug
```
The debug APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install on Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or open the project in Android Studio and click **Run**.

---

## 4. Security & Privacy Notes
- Zero hardcoded API keys.
- Optional API keys are encrypted at rest via `AndroidKeyStore` (`AES/GCM/NoPadding`).
- No unrestricted shell (`Runtime.exec`) execution; JavaScript runs inside an isolated `WebView` sandbox.
- File access uses zero-permission Storage Access Framework (`OpenDocument` / `CreateDocument`) and app-private `AgentWorkspace`.
- `AgentAccessibilityService` is disabled by default, requires explicit in-app user consent plus Android Accessibility Settings activation, and strictly skips password fields.
