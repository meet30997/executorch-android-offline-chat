# ExecuTorch Offline Chat for Android

A small Kotlin and Jetpack Compose app that runs **Llama 3.2 1B Instruct** locally through **ExecuTorch**. Replies stream into a chat UI, conversation history is included in follow-up prompts, and generation can be stopped.

Companion article: [ExecuTorch on Android: Offline Llama 3.2 Chat and Benchmarks](https://meetprajapati.com/blogs/executorch-android-offline-llama-chat-benchmarks/).

## Stack

- ExecuTorch Android **1.5.1**, CPU model with XNNPACK.
- Llama 3.2 1B Instruct, SpinQuant INT4 EO8, **2,048-token context**.
- Kotlin **2.0.21**, Jetpack Compose, coroutines, and `StateFlow`.
- Android Gradle Plugin **8.7.2** and Gradle **8.14.3**.
- Minimum Android **10 / API 29**; compile and target SDK **35**.

The verified device/model combination is **ARM64 on a Pixel 8a running Android 17 / API 37**. The build also packages x86_64 libraries, but this model was not validated on an emulator.

## Requirements

- JDK **17**.
- Android SDK **35** and Platform Tools (`adb`).
- An ARM64 Android phone with space for the roughly **1.14 GB** model and **2.18 MB** tokenizer, plus the app and working memory.

The Gradle wrapper is included. Configure your SDK path through Android Studio or set `ANDROID_HOME` for command-line builds. Use your own JDK installation; the project does not pin a developer-machine path.

## Build and install

```sh
git clone https://github.com/meet30997/executorch-android-offline-chat.git
cd executorch-android-offline-chat
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.example.executorchdemo/.MainActivity
```

Enable USB debugging and authorize the computer on the phone. Open the app once before provisioning the model so its storage directory exists.

## Set up the model

Download these two files from the [tested Hugging Face revision](https://huggingface.co/executorch-community/Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8-ET/tree/911221e0f49d3354cbd4697a9c09feee25cadec7):

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte` | 1,135,951,488 | `8715cdba9e91f6bede00cc5f2d6b12397b95225da5630c4972a8da03001cda3b` |
| `tokenizer.model` | 2,183,982 | `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` |

Copy both files into the installed app's folder:

```sh
adb push Llama-3.2-1B-Instruct-SpinQuant_INT4_EO8.pte /sdcard/Android/data/com.example.executorchdemo/files/
adb push tokenizer.model /sdcard/Android/data/com.example.executorchdemo/files/
```

Tap **Check again** in the app and wait for the **Llama 3.2 1B** ready status. Model weights are downloaded separately and are not included in this repository. This app expects the tested `.pte` program and tokenizer; it does not load GGUF files.

## Use the app

Send a short message or choose a suggested prompt. Ask a follow-up to use the retained chat history, press **Stop** to interrupt generation, or use **Clear Chat** to start again. Long messages remain accessible by scrolling. Performance metrics appear after a reply finishes, and the metrics row scrolls horizontally.

The APK has no `INTERNET` permission. Inference was also verified with Android reporting no active default network. There is no in-app model downloader.

## Measured results

On the Pixel 8a, with the debug build, temperature 0.7, and five model-warm runs per prompt:

| Prompt | Median native first-token time | Median native total inference |
| --- | ---: | ---: |
| Two-sentence explanation | 308 ms | 3,041 ms |
| Three-bullet notes summary | 604 ms | 3,279 ms |
| Kotlin expression explanation | 429 ms | 5,042 ms |

These exclude model loading, ADB input, and UI rendering. Sampled app PSS reached about **1.35 GiB** in a separate profiled long-reply check; that is not a guaranteed peak. See the article for inputs, raw results, screenshots, and metric definitions.

## Current limitations

- The small model can change facts, produce incorrect code results, and confuse conversational roles. Fast output does not establish correctness.
- Context budgeting uses character estimates and does not establish safety for arbitrary oversized or Unicode input.
- Generation has a 512-callback ceiling; a reply can end before completing the requested task. Native token counts and callback counts need not match exactly.
- Auto-scroll can leave the tail of a long reply below the viewport. Scroll manually to read it.
- A normal Stop/retry was checked; rapid stop/reuse and native teardown still need stress testing.
- Measurements cover one phone/model and the debug build. Code comments about low-memory behavior are not tested device guarantees.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

The tests cover prompt formatting, the current budgeting behavior, metric formatting, and ViewModel state. They do not establish native lifecycle or long-input safety.
