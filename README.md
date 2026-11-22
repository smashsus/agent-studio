# Agent Studio — Provider-Neutral Autonomous Android AI Workspace

**Agent Studio** is a native Android (Kotlin + Jetpack Compose + Material 3) autonomous AI workspace built around a **Swiss Minimalism** developer interface. It connects to **any OpenAI-compatible HTTPS API endpoint** (such as Fasfas AI, OpenAI, OpenRouter, Groq, or custom gateways) or any user-chosen embedded HTTPS web session, and equips the active model with **18 real on-device tools** including sandboxed shell access, local file management, web research, JavaScript/HTML5 sandboxes, WorkManager background execution, and process-isolated Accessibility automation.

---

## Purpose & Core Philosophy

- **Provider-Neutral & Model-Adaptive**: Bring any OpenAI-compatible HTTPS Base URL, Model ID (e.g. `glm-5.3-flash`, `gpt-4o-mini`, `llama-3.3-70b-versatile`), and Bearer token—or open any HTTPS web portal in the embedded browser. The workspace UI, system prompt, message headers, and splash screen adapt directly to your configured model.
- **Autonomous On-Device Tool Execution**: Multi-step task decomposition and automated `tool_calls` chaining with configurable **Autonomous**, **Balanced**, and **Strict** human-in-the-loop safety policies.
- **Sandboxed Android Shell Access**: Native `/system/bin/sh` command execution anchored in `AgentWorkspace` with stdout/stderr capture, exit codes, SELinux guidance, timeout controls, and an interactive **Shell Console** in the **Files** tab.
- **Local-First Three-Layer Memory**: Persistent Room database organizing **Short-Term** conversation context, **Task Memory** (plans, subtasks, summaries), and **Long-Term** user-approved facts.
- **Hardware-Backed Credential Security**: API keys are encrypted locally using `AndroidKeyStore` (`AES/GCM/NoPadding`) and are never logged, injected into prompts, or exposed to tools or the shell environment.
- **Stable Isolated Accessibility Automation**: `AgentAccessibilityService` runs in a dedicated `:a11y` process with on-demand reads only (no passive event flooding), so heavy tasks, WebViews, or background sweeps do not stall or trigger `"This service is malfunctioning"` errors.

---

## Architecture Overview

Packages under `com.example`:

- **`authentication/`**:
  - `AuthState` sealed state machine (`NotAuthenticated`, `Authenticating`, `Authenticated`, `SessionExpired`, `AuthenticationFailed`).
  - `KeystoreVault`: 256-bit `AndroidKeyStore` AES-GCM encryption for API keys.
  - `AuthManager`: Quick presets (`Fasfas AI`, `Fasfas RP`, `OpenAI`, `OpenRouter`, `Groq`, `Custom`) plus arbitrary HTTPS embedded `WebView` session support.
- **`model/`**:
  - `OpenAiCompatibleModelProvider`: `OkHttp` + `kotlinx.serialization` client supporting arbitrary HTTPS base URLs, `tool_calls` chaining, reasoning traces, and resilient JSON parsing.
  - `WebBridgeProvider`: Provider-neutral web-session bridge prompt builder + deterministic local tool execution fallback.
- **`agent/` & `execution/`**:
  - `AgentController`: Multi-step execution loop with retry limits, per-step timeouts, approval checkpoints, and exception isolation.
  - `TaskPlanner`, `ExecutionMonitor`, `ErrorRecoveryManager`, `ConversationManager`, and `AgentWorkManager` (`ReminderWorker`, `BackgroundAgentWorker`).
- **`tools/`** (18 registered tools):
  - **Shell & System**: `run_shell` (`/system/bin/sh -c` inside `AgentWorkspace`, 1–30s timeout, protected against `shared_prefs`/Keystore reads and dynamic binary loading).
  - **Research**: `web_search` (DuckDuckGo HTML / Wikipedia OpenSearch), `fetch_webpage` (HTTPS-only extraction), `generate_report` (`.md` / `.pdf`).
  - **File Management**: `read_document` (SAF `content://` & workspace), `analyze_pdf` (`PdfRenderer` + pure-Kotlin stream extractor), `create_file` (TXT, Markdown, HTML, native `PdfDocument`), `list_workspace_files`.
  - **Coding**: `run_javascript` (hardened offscreen `WebView` sandbox), `generate_html_app` (live full-screen HTML5 preview).
  - **Android Integration**: `open_url`, `open_app`, `share_content` (`FileProvider`), `clipboard_access`, `create_notification` (`POST_NOTIFICATIONS` + `WorkManager`), `accessibility_interact`.
  - **Productivity**: `manage_task_list`, `save_memory`.
- **`accessibility/`**:
  - `AgentAccessibilityService` + private `AccessibilityBridgeProvider` isolated in `android:process=":a11y"` for reliable, on-demand screen reading and UI interaction.
- **`ui/`**:
  - Swiss Minimalism light/dark theme tokens (`Space Grotesk`, `Inter`, `JetBrains Mono`), custom cold-start + animated Compose splash screen, 4-tab navigation (`Home`, `Activity`, `Memory`, `Files`), search/pin/rename/export chat history management, Markdown + code blocks, and full-height isolated `WebView` modals.

---

## Build & Installation

### Requirements
- Android Studio (Ladybug / Meerkat or newer) with Android SDK 36
- JDK 11+
- Target device running **Android 7.0 (API 24)+** (recommended Android 8.0+ / ARM64-v8a)

### Build APK via Gradle
```bash
gradle :app:assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Install on Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Security & Device Notes
- Zero hardcoded API keys; bearer tokens live exclusively in `AndroidKeyStore` and are attached only to HTTPS model transport requests.
- `run_shell` runs strictly under the unprivileged app UID inside `AgentWorkspace`, blocks access to `shared_prefs` / Keystore XMLs, and blocks external `.dex`/`.jar`/`.so` dynamic loading.
- `AgentAccessibilityService` is opt-in (disabled by default), strictly skips password-masked fields, and only activates on explicit consent + Android Accessibility Settings toggle. On OEM skins with aggressive background killing (such as HyperOS/MIUI), enabling **Autostart** and setting Battery Saver to **No restrictions** for Agent Studio helps prevent the OS from terminating background Accessibility bindings.
