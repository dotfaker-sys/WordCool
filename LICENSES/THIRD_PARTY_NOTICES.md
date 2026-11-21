# 第三方数据声明 / Third-party notices

本工程随 APK 内置了一份离线英语音标索引：`app/src/main/assets/ipa.tsv`。
它**不是本工程原创数据**，由以下两份开源数据合并生成。代码与数据是两个独立部分：数据以单独 assets 文件在运行时加载，属于聚合使用，不改变本工程自身代码的许可。

## 1. 美音数据（en_US）

- 来源仓库：<https://github.com/open-dict-data/ipa-dict> ，文件 `data/en_US.txt`
- 上游：cmudict-ipa by Lingliang Zhang（其数据源于 CMU Pronouncing Dictionary, Carnegie Mellon University）
- 许可：**MIT**
- 本工程内对应全文：`LICENSES/MIT.txt`

## 2. 英音数据（en_UK）

- 来源仓库：<https://github.com/open-dict-data/ipa-dict> ，文件 `data/en_UK.txt`
- 上游：**ipacards by @leoboiko**，ipa-dict 项目明确说明第三方数据集保留其原始许可
- 许可：**GPL-3.0-only**
- 本工程内对应全文：`LICENSES/GPL-3.0-only.txt`

> 注意：ipa-dict 仓库根目录的 LICENSE 写的是 MIT，但其英音数据来自 GPL-3.0 的 ipacards，因此**不能**把整份数据当作 MIT 使用。分发包含 `ipa.tsv` 的 APK 时，必须随附本声明、两份许可证全文，并提供下列获取对应源码的方式。

## 3. 本工程对数据做的修改

生成脚本见 `tools/build_ipa.py`，处理是确定性、可重放的：

1. 逐行读取 `word<TAB>/ipa/`；
2. 词条统一 NFC、小写、去首尾标点（与运行时 `IpaDictionary.normalize()` 逐字符一致）；
3. **多读音全部保留**：源数据里 `, ` 分隔的多个 IPA 逐条补 `/…/` 后并列保留，不再只取第一条；
4. 同一个词在源文件里出现多行时合并候选、去重；
5. 以词为键合并 en_UK / en_US 两表，缺失的一边留空串；
6. 按词字典序排序，输出为单行三列 TSV：`word<TAB>uk<TAB>us`；
7. 不含换行 / 制表符的异常词条跳过。

**没有改写任何音标内容本身**，只做了格式归一与合并。v7 另在**代码层**加了英美拼写别名表（`IpaDictionary.ALIAS_PAIRS`，如 color↔colour、center↔centre），只用于查表，不改写数据文件、不改写用户词形。

## 4. 对应源码与完整性校验

```
https://raw.githubusercontent.com/open-dict-data/ipa-dict/master/data/en_US.txt
https://raw.githubusercontent.com/open-dict-data/ipa-dict/master/data/en_UK.txt
```

在本工程根目录执行 `python3 tools/build_ipa.py en_US.txt en_UK.txt` 即可重建 `ipa.tsv` 与 `ipa_stats.json`。

本次构建所用上游文件的 SHA-256（写入 `app/src/main/assets/ipa_stats.json`，可用 `tools/verify.py` 复核）：

```
en_US.txt  2af6f154a5c363275f052d1f85acedef38ed185ca9745aa4314be77f6b70de67
en_UK.txt  221394caef0cf723b4f2df81a98ac33191293257b88aed5b1fb89466d3a0dc77
```

## 5. 覆盖与质量边界（实测，非估算）

- 合并后共 **146,647 条**：英美两套都有 43,560 条，仅美音 81,528 条，仅英音 21,559 条；8,451 条含多个读音候选。
- 用公开高频词表 `google-10000-english`（9,894 词）实测：**9,387 词命中音标（94.9%）**，其中 7,676 词（77.6%）英美双套齐全，1,711 词只有一套，**507 词（5.1%）查不到**——主要是 `rss/faq/gmt/msn` 这类缩写、专名与极生僻词。
- 数据是**开源音素转写**，含 `ɫ` / `ɝ` / `ɐ` 等严式 IPA 符号，与课本常见的宽式 IPA 写法不完全一致（例如 `after` 美音标作 `/ˈæftɝ/` 而非 `/ˈæftər/`）。本工程保留原样，不做自动改写，以免引入系统性错误；用户可在词条对话框里手动改成自己习惯的写法。
- 词组（如 `take off`）、自造词、极生僻词通常不在表内，查不到就留空——**不会凭空生成音标**；导入预览会列出未命中词并支持一键复制。
- 发音与音标是两条独立链路：朗读由系统 TTS 完成，与这份数据无关，**绝不把 IPA 字符串送进 TTS**。

## 6. 考研词库（用户提供）

`app/src/main/assets/kaoyan.tsv`（711 个唯一词 / 23 组）由用户提供的 Unit1–5 Word 词表整理，**不是本工程原创数据，也不属于上面的 MIT / GPL-3.0 开源数据**。本工程只做格式整理（去重、合并同形义项、拆英式拼写、补组名），没有声称拥有词表版权。

- 当前仅用于用户本地学习；**公开分发 APK 或仓库前，请先确认词表来源与版权授权**。
- 文件首尾的 `#` 注释说明了来源与这一限制；`tools/verify.py` 只校验行数、组数、列格式与唯一性，不校验版权。
- 如果不想随包分发这份词表，删除 `app/src/main/assets/kaoyan.tsv` 并移除导入页的「载入考研词库」入口即可，其余功能不受影响。

## 7. 内置语音引擎（v1.11 起）

`app/libs/sherpa-onnx-2.0.2.aar` 与 `app/src/main/assets/tts/amy/` 是内置离线语音（sherpa-onnx + Piper Amy int8）。sherpa-onnx 使用 **Apache-2.0**（全文见 `LICENSES/Apache-2.0.txt`）；Piper 模型的上游数据集许可见 `LICENSES/SHERPA_ONNX_AND_PIPER_NOTICE.md`。公开分发时请一并保留这两份声明。
