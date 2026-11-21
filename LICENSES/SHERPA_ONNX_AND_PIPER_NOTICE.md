# 内置语音引擎与模型声明 / Bundled TTS notices

v1.11 起 APK 内置一份离线英语语音（美音），不再依赖手机系统语音包。

## 1. sherpa-onnx

- 文件：`app/libs/sherpa-onnx-2.0.2.aar`
- 项目：<https://github.com/k2-fsa/sherpa-onnx>
- 版本：2.0.2
- 许可：**Apache-2.0**，全文见 `LICENSES/Apache-2.0.txt`
- 用途：Android 端 VITS/Piper 推理 + PCM 播放前的音频生成
- 说明：AAR 内含 ONNX Runtime 与 sherpa-onnx 的 arm64 / armeabi-v7a / x86_64 原生库；本项目只打包 arm64 + armeabi-v7a 到正式包，调试包另含 x86_64。

## 2. Piper 语音模型

- 文件：`app/src/main/assets/tts/amy/`
  - `en_US-amy-low.onnx`（int8，约 18.6 MB）
  - `tokens.txt`
  - `lexicon-en-us.txt`（来自 sherpa-onnx 的 tts-models release）
  - `MODEL_CARD`
- 模型：`vits-piper-en_US-amy-low-int8`
- 下载来源：<https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models>
- 上游数据集：<https://github.com/MycroftAI/mimic3-voices>
- 上游数据集许可：**CC-BY-SA-4.0**，全文见 `LICENSES/CC-BY-SA-4.0.txt`
- 说明：模型卡写明 `License: See URL`，本项目按上游数据集许可保留署名与相同方式共享要求；公开分发前请再次核对模型与数据集的许可。

## 3. 发音词典

`lexicon-en-us.txt` 由 sherpa-onnx 的 `build_lexicon.py` 生成，来源注明 CMUDict / en_list / compounds；下载地址：

<https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/lexicon-en-us.txt>

## 4. 行为边界

- 当前只内置**美音 Amy**；界面上的 UK 按钮也会用这个声音，并在第一次切换时提示。
- 系统 TTS 仍保留为兜底：内置引擎加载失败、或用户设备不支持 arm64/armeabi 时才会走系统语音包。
- 所有推理在本机 CPU 完成，不需要联网；模型与引擎会占 APK 约 50–73 MB。
