<div align="center">

# Agent Studio

**An Android workspace where AI can do more than answer.**

A provider-neutral AI agent for Android. Connect a model, give it a goal, and let it execute multi-step workflows using native device tools.

[![Platform](https://img.shields.io/badge/Platform-Android_7.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Kotlin-Native-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)

[![Latest Release](https://img.shields.io/github/v/release/smashsus/agent-studio?style=flat-square)](https://github.com/smashsus/agent-studio/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/smashsus/agent-studio/total?style=flat-square)](https://github.com/smashsus/agent-studio/releases)
[![Stars](https://img.shields.io/github/stars/smashsus/agent-studio?style=flat-square)](https://github.com/smashsus/agent-studio/stargazers)
[![Issues](https://img.shields.io/github/issues/smashsus/agent-studio?style=flat-square)](https://github.com/smashsus/agent-studio/issues)

<br/>

[![Download APK](https://img.shields.io/badge/Download_for_Android-Latest_APK-2ea44f?style=for-the-badge&logo=android&logoColor=white)](https://github.com/smashsus/agent-studio/releases/latest)

<sub>No source build required. Download and install the APK directly from GitHub Releases.</sub>

</div>

---

## Overview

Most mobile AI apps stop at a chat interface. **Agent Studio** connects an LLM directly to Android-side tools, allowing it to plan work, chain tool calls, inspect results, and recover from errors across multi-step tasks.

You bring your own model endpoint and define the execution boundaries; the app provides the runtime, tools, and local workspace.

---

## Core Capabilities

<table>
<tr>
<td width="50%" valign="top">

### Autonomous Workflows
Give the agent an objective instead of micromanaging prompts. It decomposes tasks, chains tool calls, tracks state, and handles execution retries within configured limits.

</td>
<td width="50%" valign="top">

### Bring Your Own Model
Connect any **OpenAI-compatible HTTPS endpoint** using a Base URL, Model ID, and API Key. Includes built-in provider presets, custom gateway support, and an embedded web-session bridge.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Native Device Tools
Execute shell commands, read/write workspace files, analyze PDFs, run JavaScript, preview HTML apps, search the web, trigger intents, and interact via Android Accessibility.

</td>
<td width="50%" valign="top">

### Three-Layer Local Memory
Retains active conversation context, task execution plans/summaries, and user-approved long-term facts inside a local **Room** database.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### Granular Execution Control
Switch between **Autonomous**, **Balanced**, and **Strict** execution policies to control when the agent acts freely and when it pauses for explicit user approval.

</td>
<td width="50%" valign="top">

### Focused Compose Workspace
Built natively with Material 3 across **Home**, **Activity**, **Memory**, and **Files** tabs, complete with chat search, pinning, renaming, and session export.

</td>
</tr>
</table>

---

## Architecture & Internals

### 18 Integrated Tools

| Category | Available Tools |
| :--- | :--- |
| **Shell & System** | `run_shell` |
| **Web & Research** | `web_search`, `fetch_webpage`, `generate_report` |
| **File System** | `read_document`, `analyze_pdf`, `create_file`, `list_workspace_files` |
| **Code & Preview** | `run_javascript`, `generate_html_app` |
| **Android OS** | `open_url`, `open_app`, `share_content`, `clipboard_access`, `create_notification`, `accessibility_interact` |
| **Productivity** | `manage_task_list`, `save_memory` |

> *Note: Tool availability depends on granted Android permissions, device OEM restrictions, and whether your chosen model supports tool/function calling.*

### Agent Execution Loop

1. **Understand:** Interprets the user's goal and identifies required outcomes.
2. **Plan:** Breaks complex objectives into sequential or dependent subtasks.
3. **Act:** Invokes Android-side tools and captures structured outputs.
4. **Evaluate:** Inspects tool responses to determine next steps or error recovery.
5. **Checkpoint / Finish:** Continues within step/timeout limits, pauses for user approval (based on policy), or delivers the final result.

The controller enforces configurable retry ceilings, per-step timeouts, approval gates, and graceful exception handling.

### Memory Architecture

| Layer | Scope & Persistence |
| :--- | :--- |
| **Short-Term Memory** | Active conversation window and immediate tool outputs. |
| **Task Memory** | Multi-step plans, subtask states, progress logs, and execution summaries. |
| **Long-Term Memory** | User-approved facts and preferences stored persistently via Room. |

### Provider Configuration

Agent Studio works with any standard OpenAI-compatible `/chat/completions` HTTPS API:

- **Base URL:** Your provider or local/self-hosted gateway URL (e.g., OpenRouter, Groq, Ollama via tunnel, OpenAI, Anthropic-compatible proxies).
- **Model ID:** Target model identifier.
- **API Key:** Stored encrypted on-device.

*An embedded HTTPS WebView session bridge is also included for supported web flows (note: tool-calling capabilities via web sessions may differ from direct API endpoints).*

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI & Design** | Jetpack Compose + Material 3 |
| **Networking** | OkHttp |
| **Serialization** | `kotlinx.serialization` |
| **Local Storage** | Room Database |
| **Background Execution** | WorkManager |
| **Security** | Android Keystore (`AES-GCM`) |
| **Device Automation** | Android Accessibility Service & WebView |

---

## Getting Started

### 1. Requirements
- **OS:** Android 7.0 (API 24) or higher.
- **Network:** Internet access for remote LLM endpoints and web tools.
- **Credentials:** An OpenAI-compatible API endpoint and key (or supported web-session login).

### 2. Installation
1. Go to [**Latest Releases**](https://github.com/smashsus/agent-studio/releases/latest) and download the `.apk` asset.
2. Open the APK on your device and approve *"Install unknown apps"* for your browser or file manager if prompted.

### 3. Quick Setup
1. Open **Agent Studio** and navigate to provider settings.
2. Select a preset or enter your **Base URL**, **Model ID**, and **API Key**.
3. Choose an execution policy (**Strict**, **Balanced**, or **Autonomous**).
4. Grant optional permissions (Notifications, Accessibility Service) only if you intend to use those specific tools.

---

## Security, Privacy & Permissions

Because Agent Studio can execute commands and interact with device surfaces, safety controls are built into the runtime:

- **Encrypted Credentials:** API keys are encrypted at rest using **Android Keystore (AES-GCM)** and are excluded from model prompts and shell environments.
- **Sandboxed Shell:** `run_shell` executes strictly under the app's unprivileged Android UID within its scoped workspace, not with root privileges.
- **Opt-In Accessibility:** UI interaction requires explicit activation in Android's Accessibility settings. Password-masked input fields are automatically excluded from inspection and interaction.
- **Transport Security:** All external model API calls require HTTPS.

> **Best Practice:** Autonomous agents can modify files and trigger external actions. Start with **Balanced** or **Strict** approval policies when testing unfamiliar models or prompts.

---

<div align="center">

**Agent Studio** | *Give your model tools.*

[Latest APK](https://github.com/smashsus/agent-studio/releases/latest) · [All Releases](https://github.com/smashsus/agent-studio/releases) · [Report an Issue](https://github.com/smashsus/agent-studio/issues)

</div>
