# <img src="metadata/en-US/icon.png" width="42"/> ComfyMobile

An unofficial, native Android UI for [ComfyUI](https://github.com/comfyanonymous/ComfyUI).

**Current version**: v0.8.17

[<img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/refs/heads/main/get-it-on-github.png"
    alt="Get it on GitHub"
    height="80">](https://github.com/Glitch9Inc/comfymobile/releases/latest)

## Overview

ComfyMobile provides a streamlined mobile interface for interacting with ComfyUI servers, allowing you to generate and manage AI images and videos directly from your Android device. The app communicates with your ComfyUI server via its API, bringing the power of node-based AI generation to your mobile workflow.

**Note**: This is an independent, community-developed project and is not officially affiliated with or endorsed by the ComfyUI team.

ComfyMobile is a fork of [ComfyChair](https://github.com/legal-hkr/comfychair) by Legal HKr.

## Screenshots

<img src="metadata/en-US/images/phoneScreenshots/1.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/2.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/3.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/4.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/5.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/6.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/7.png" width="200"/> <img src="metadata/en-US/images/phoneScreenshots/8.png" width="200"/>

## Features

### General

- **Multi-server support**: Save and manage multiple ComfyUI server connections with per-server settings and optional auto-login
- **Server connection**: Connect to remote or local ComfyUI servers with automatic HTTP/HTTPS detection and self-signed certificate support
- **Authentication**: HTTP Basic Auth and API key/Bearer token authentication for servers behind reverse proxies
- **Offline mode**: Browse cached data without network connection, with automatic offline prompt when server is unreachable
- **Queue management**: Submit multiple jobs without waiting, view queue count, cancel current job, add to front of queue, or clear queue entirely
- **Dual workflow support**:
  - **Checkpoint mode**: Traditional CheckpointLoaderSimple workflows
  - **UNET mode**: Modern diffusion workflows (Flux, Z-Image, etc.) with separate UNET, VAE, and CLIP model selection
- **LoRA chain support**:
  - Add up to 5 LoRAs per chain with individual strength control (0.0-2.0)
  - Text to Image and Image to Image: Separate LoRA chains for Checkpoint and UNET modes
  - Text to Video and Image to Video: Separate High Noise and Low Noise LoRA chains
  - LoRAs are dynamically injected into workflows at generation time
- **Prompt Library**:
  - Save prompts as reusable presets with custom names and tags
  - Organize presets with favorites and tag-based filtering
  - Quick access via dropdown or full library dialog
  - Search presets by name, prompt text, or tags
- **Localization**: Available in English (default), German, French, Polish, Spanish, and Chinese with per-app language selection
- **Native Android experience**: Built with Kotlin and Jetpack Compose with Material Design 3

### Generation Modes

- **Text to Image**:
  - Mobile-optimized interface with live progress updates
  - Live preview images during generation (when supported by server)
- **Image to Image**:
  - Two modes: **Editing** (transformation) and **Inpainting** (mask-based)
  - **Editing mode**: Transform images with optional reference images (up to 2)
  - **Inpainting mode**: Mask editor with adjustable brush size and feathered edges
  - Live progress updates and preview images during generation
- **Text to Video**:
  - Generate AI videos with High Noise / Low Noise UNET and LoRA model selection
  - Live preview during generation
- **Image to Video**:
  - Animate still images into videos with adjustable length and frame rate
  - High Noise / Low Noise UNET and LoRA model selection
  - Live preview during generation

- **Reference to Image (REF2I)** and **Reference to Video (REF2V)**:
  - Top-right media selection uses a large-thumbnail gallery, not a file list. REF2I accepts multiple images; REF2V accepts images and videos together. Numbered badges preserve tap order, and each media kind is appended to its own reference list in that order
  - Home screens offer four equal-height, two-line resolution choices: **Original ratio** (default), **2:3**, **1:1**, and **3:2**. Every option shows width × height calculated from the current megapixel budget, which defaults to **1** and is edited only in the configuration sheet. Changing megapixels updates the selected ratio's dimensions; editing width calculates height from the same pixel budget, and editing height calculates width. Dimensions align to the workflow's grid, so area and aspect ratio are approximate
  - **Source / Preview** switches between the original reference and generated result. REF2I uses its first reference image; REF2V prefers its first reference video, falling back to its first image when no videos remain. Changing the first reference updates the original-ratio canvas without changing selected models
  - REF2V also offers 5s / 10s / 15s duration shortcuts. Megapixels, custom dimensions, duration, FPS, models and reference-list management remain in the configuration sheet
  - REF2V duration is entered and saved in whole seconds. Existing frame-count preferences are converted using their saved FPS (rounded to the nearest second); changing FPS keeps the chosen seconds unchanged. Submission converts seconds × FPS to the workflow's frame-count input. H3 rounds frames upward to its native temporal grid (at 24 fps: 5s → 124 frames, 10s → 243, 15s → 362), so actual duration can be slightly longer
  - Capacity comes from the workflow's mapped loaders, not a fixed model-specific limit
  - Built-in **Qwen Image 2.1 Reference** provides 16 image slots and an independent latent canvas
  - Qwen REF2I follows the reference graph's QwenImage21Cache and Euler/simple sampling (25 steps, CFG 1). Choose **Qwen3-VL 8B** for conditioning; `pe_i2i` / `pe_t2i` models are prompt enhancers and must not be used as its CLIP encoder
  - Reference-tested Qwen configuration: `qwen_image_2.1_int8_convrot.safetensors` diffusion, `qwen3vl_8b_int8_convrot.safetensors` conditioning encoder (`qwen_image`), and `qwen_image_2.1_vae_bf16.safetensors`. The reference encoder uses resolution 1024; its reference-sized latent is unused, so the output canvas can be landscape even when all references are portrait
  - Updating a bundled workflow does not overwrite saved per-workflow sampling/model choices. When moving from the earlier Qwen template, explicitly set 25 steps / CFG 1 and select the conditioning encoder above
  - Reference-mode model preferences survive an empty/loading server model cache; automatic resolution updates do not save empty model selections over existing per-workflow choices
  - Built-in **MiniMax H3 Reference Video** provides 9 image slots and 3 native video slots; generated audio is retained, reference audio is not connected
  - H3 prompts identify image references with `<Picture 1>` and video references with `<Video 1>` (one-based numbering per media kind). Image-only, video-only, and mixed selections are supported; unfilled loaders are pruned before submission. Its 512×320 / 5-second / 24-fps / 8-step defaults are a smoke configuration, not a final-quality preset
  - Select compatible diffusion, text encoder and video/image VAE models in the parameter sheet. H3's audio VAE remains a separate loader (`MiniMax/minimax_h3_audio_vae_fp32.safetensors`); duplicate the workflow to change that filename for another server
  - Enable **Show built-in workflows** in app settings if bundled workflows are hidden
  - Reference video files are streamed to ComfyUI's `/upload/image` endpoint with their original media MIME type
  - Per-server, per-workflow settings retain media URIs, original-ratio preference and megapixel budget, not uploaded filenames or media bytes. Gallery access is requested through Android's photo/video permissions; unreadable or revoked media must be reselected or removed

### Media

- **Media viewer**:
  - Unified fullscreen viewer for images and videos
  - Material Design 3 Expressive UI with floating toolbar and FAB
  - Swipe navigation between gallery items with tonal navigation buttons
  - Pinch-to-zoom and double-tap to toggle between fit and crop zoom
  - Single-tap to toggle UI visibility (with system bars)
  - Quick actions: delete, save to gallery, share, view metadata
  - Metadata viewer showing generation parameters (prompt, model, seed, steps, etc.)
  - REF2I previews retain the generated server file identity so the image viewer can retrieve PNG generation metadata, including after restoring a completed result
  - Video preview thumbnails and dimensions follow the current video URI, so replacing a result does not retain the previous video's frame or aspect ratio
  - Active generations also check completed server history, so a missed WebSocket completion notification does not leave the result preview waiting for the app to resume
  - Optimized media caching for smooth transitions between items
- **Gallery**:
  - View all generated images and videos with 2-column grid layout
  - Video indicator on thumbnails
  - Pull-to-refresh to update gallery
  - Multi-select mode with dedicated button or long press to select items
  - Batch operations: save, share, or delete multiple items at once
- **Media management**: Save to device gallery (Pictures/ComfyMobile or Movies/ComfyMobile) or share

### Workflows

- **Full node graph visualization**: Colored nodes and connections matching ComfyUI's visual style with smooth Bezier curves or Hermite splines
- **Node management**:
  - Add nodes via searchable Node Browser with category filter chips
  - Human-readable display names shown where available (with class type as subtitle)
  - Deprecated and experimental nodes clearly marked with warning/science icons
  - Search by display name, class type, category, or description
  - Long-press output slots to open Node Browser filtered by compatible types
  - Delete, duplicate, bypass, and rename nodes
  - Cleanup tool to remove disconnected nodes
- **Node groups**:
  - Organize nodes into collapsible groups with grid layout
  - Rename groups via context menu
  - Groups persist across saves and theme changes
- **Connection management**:
  - Create connections by tapping an output socket and then an input socket (or vice versa)
  - Target sockets glow when compatible during connection creation
  - Delete connections via node context menu or by selecting and deleting
  - Selected nodes highlight connections with animated flowing segments
  - Connected slot circles enlarge and color-code when highlighted
- **Node attribute editing**:
  - Side sheet editor for node parameters (tap nodes to edit)
  - All editable inputs shown, including those using default values
  - Full support for both legacy and new ComfyUI COMBO input formats
  - Numeric steppers with floating labels, range hints, and increment/decrement buttons
  - Boolean values displayed as on/off toggles
  - Image previews for image selector fields
  - Template values highlighted with "UI:" prefix
  - Connected inputs styled with wire colors
  - Edited values highlighted for easy identification
- **Workflow notes**:
  - Add notes to workflows with Markdown support
  - Unified title and text editor
  - Notes displayed in the workflow graph
- **Navigation and zoom**:
  - Material Design 3 Expressive floating toolbar
  - Zoom controls with percentage display
  - Fit all / Fit width zoom options
  - Initial drawing animation when opening workflows
- **Workflow management**:
  - Create workflows from scratch or import existing ones from ComfyUI
  - Supported formats:
    - ComfyUI LiteGraph (import only) - native ComfyUI workflow format with subgraph support
    - ComfyUI API JSON (import/export) - workflow format used by the ComfyUI API
    - ComfyMobile JSON (import/export) - preserves field mappings and app settings
  - Dynamic workflow thumbnails showing mini graph previews
  - Context menus for edit, rename, duplicate, export, and delete
  - Flexible field mapping: Positive Prompt required; source-image modes also require a source image, and reference modes require independent output dimensions and compatible media bindings
  - Dynamic parameter constraints based on workflow node definitions
  - Per-workflow generation settings persistence
  - Reference workflows require Positive Prompt plus independent output Width and Height mappings and at least one compatible reference loader
  - Numbered reference fields use `{{reference_image_1}}`, `{{reference_image_2}}`, etc., and `{{reference_video_1}}`, etc.; each kind is numbered consecutively from 1
  - Supported reference loaders: `LoadImage.image`, `VHS_LoadVideo.video`, and native `LoadVideo.file` followed by `GetVideoComponents`
  - Add and connect loaders in the graph editor before mapping more references. Unfilled reference slots are pruned for submission; pruning a shared required chain is rejected
  - For REF2I, connect an independent output latent rather than the reference encoder's image-sized latent. REF2V output dimensions map to the generation consumer, not reference preprocessing
  - Import supports named/object widgets, exposed subgraph widgets, and flat dotted dynamic input keys. The app does not create or repair model-specific generation chains
  - Import and graph editing share prompt-candidate tracing, including conditioning consumers with a `prompt` input instead of a text-encoder class name
  - Configuration backups retain reference URI lists but do not include source media or URI permission grants; restoring on another device may require selecting the files again
  - Workflow Editor returns to view mode after saving for continued editing

### Configuration

- **Server configuration**:
  - View detailed server information (ComfyUI version, OS, Python, PyTorch versions)
  - Monitor hardware resources (RAM and GPU VRAM usage with free/total display)
  - Server management actions (clear queue, clear history)
- **App management**:
  - Clear local cache (generated images, videos, source images, masks)
  - Restore default settings
  - Backup configuration to JSON file (connection settings, workflow values, custom workflows)
  - Restore configuration from backup
- **Workflow management**:
  - View and manage custom workflows with visual thumbnails
  - Upload custom ComfyUI workflows with automatic validation
  - Edit workflow name and description
  - Default generation settings extracted during workflow import
  - Per-workflow generation settings (each workflow remembers its own configuration)
- **Configuration persistence**: Automatically saves and restores all settings including prompts, models, workflow selections, and generation parameters on a per-workflow basis

## Requirements

- Android 13 (API level 33) or higher
- Access to a running ComfyUI server instance
- Network connectivity to reach your ComfyUI server

## Development setup

### Prerequisites

1. **Android Studio** (latest stable version recommended)
2. **JDK 11** or higher
3. **Android SDK** with API level 36

### Building the project

1. Clone the repository:
   ```bash
   git clone https://github.com/Glitch9Inc/comfymobile.git
   cd comfymobile
   ```

2. Set up your local environment:
   - Ensure `JAVA_HOME` is set to your JDK installation
   - Configure Android SDK path in `local.properties`

3. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```

4. Run on device/emulator:
   ```bash
   ./gradlew installDebug
   ```

The **Build APK** GitHub Actions workflow automatically builds a dev APK on pushes to any branch (not tags), and can also be run manually via **Actions → Build APK → Run workflow**. Successful builds upload `comfymobile-dev.apk` as a workflow artifact and publish it to the `dev-latest` prerelease.

### Running tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

## Configuration

To connect to your ComfyUI server, you'll need:
- ComfyUI server URL (e.g., `http(s)://192.168.1.100:8188`)
- Network access between your Android device and the ComfyUI server

## Tech stack

- **Language**: Kotlin 2.0.21
- **Min SDK**: Android 13 (API 33)
- **Target SDK**: Android 15 (API 36)
- **UI Framework**: Jetpack Compose with Material Design 3 (including Expressive components)
- **Video Playback**: Media3 ExoPlayer
- **Architecture**: MVVM with ViewModels and StateFlow
- **Navigation**: Jetpack Compose Navigation
- **Build system**: Gradle with Kotlin DSL

## Contributing

This project follows standard Android development practices:
- Kotlin coding conventions
- Jetpack Compose best practices
- Material Design 3 guidelines
- MVVM architecture pattern

## License

[GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.en.html)

## Acknowledgments

- [ComfyChair](https://github.com/legal-hkr/comfychair) - The original project ComfyMobile is forked from
- [ComfyUI](https://github.com/comfyanonymous/ComfyUI) - The powerful node-based UI this app interfaces with
- [Claude](https://claude.ai) by Anthropic - AI assistant that provided valuable support during the development process, helping with code implementation, architecture decisions, and documentation
