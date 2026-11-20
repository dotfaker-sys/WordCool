# Android 背单词应用离线补全英美音标与双发音：产品实证、数据源与工程方案

## 摘要

对 Android 背单词产品而言，“导入只有单词+中文释义的词表”与“显示英美双音标并稳定双发音”是两条不同链路：前者决定用户能否携带自己的学习材料，后者决定查表或发音引擎能否命中。可核实页面中，欧路词典可导入第三方 MDX/Eudic 扩充词库并下载离线发音库；词藤明确提供 14,201 词条的 BrE/AmE 双音标、双预录音频，且首次下载后可离线；List 与 Anki 支持外部词表，Anki 的 `{{tts}}` 则依赖设备 TTS；Quizlet 的官方流程偏云端整理和发布，公开页面未承诺离线音标补全 [1][2][3][8]。ECDICT 基础版约 76 万词，但公开字段描述以“英语音标”为主，不是现成的英美双 IPA 对照表；CMUdict 超过 13.4 万条且是 ARPAbet，只能直接覆盖美音；Kaikki/Wiktionary 可直接给英/美/其他口音 IPA，但原始数据压缩后 2.8 GB、音频约 20.4 GB [4][5][6]。对本项目，最可落地的是“构建 2.5–4 MB 的 `/word → UK IPA / US IPA` 只读查询包，导入时按用户确认策略补全”，保留英音/美音两个独立 TTS voice 作为仅发声层；不得把 IPA 字符串送进 TTS，不能覆盖用户已手工编辑的音标，英美拼写差异做词形别名，超纲词显式留空而非猜读。

## 一、产品实证：导入入口普遍存在，但自定义词表的音标与发音证据明显分层

**主流应用大多把“内置词库质量”与“自定义导入便利”分开运营。** 欧路词典 Android 帮助页明确允许把第三方扩充词库放入任意文件夹，在“工具—软件设置—扩充词库路径”中选择，再由“词典库管理”启用；其“发音设置—下载离线发音库”可下载离线发音库，站点说明又明确单词和例句支持美音、英音切换 [1]。这证明它拥有独立的离线语音资源层，但帮助页没有解释导入的生词会调用哪个词库、是否自动补齐音标，因而不能据此断言“任意导入词都会自动双音标”。词藤公开页面称其聚合多源词典、提供 BrE/AmE 双音标与双发音，并有 8 本词书、14,201 个落库词条；音频与词库首次下载后核心功能可离线 [2]。这里的双发音更像预生成音频资源，而非现场 TTS。

**Anki 的开放架构让“模板规则”和“导入数据”都可自定义，却把设备依赖交给用户。** 官方导入文档表明文本文件可用表头指定分隔符、列名、笔记类型和 deck；首字段重复时默认会更新同类型笔记的其他字段，也可改为忽略重复或作为新笔记 [3]。其卡片模板可写 `{{tts en_US:Front}}` 之类的标签，由 Android 设备已安装的 TTS voice 朗读；AnkiDroid 因而能零音频、离线朗读，但语音质量、方言及“语音包是否真正下载完成”取决于设备。Quizlet 官方支持 PDF、PowerPoint、Word 与分隔符明确的词对导入，AI Smart Assist 只生成草稿且需用户发布，但页面没有承诺内置音标字段或自动英美发音 [8]，因此不能把它视为离线双音标方案。

**百词斩、墨墨、不背单词更像云端词书产品，公开证据只能支持“可学习自有生词”，不足以证明本地补全。** 百词斩官网展示大量校内教材和校外词书，包括音节拆分功能，却没有公开可核验的“导入任意 CSV/纯词表并自动给音标”说明 [9]。二手产品页称百词斩有自定义生词本、支持手动录入、拍照 OCR 和 TXT 批量导入，同时内置美音、可设置英式发音；这足以说明产品存在生词收集与口音切换，但 OCR/手动录入路径并不等于导入 CSV 后自动查询内部词典。墨墨公开资料称可自建词库、导入生词并切换美音，不过“6 级以上解锁美音切换”属于账号/等级门槛，不是数据许可；它同样没有公开自定义词如何查音标。不背单词可上传每行一个词的 TXT 并同步到 APP，公开体验说明其自动匹配中英文发音、可做随身听，但没有公布内部数据源、是否离线、是否预录。

**小众产品的公开披露差异最大，应以可验证功能而非口碑下结论。** List 背单词在 Microsoft Store 页面明确列出外部文件导入、在线词典、英式/美式发音+音标、PDF 导出等能力 [12]；在另一官方 App Store 索引页中，相关说明未抓到导入或音标字段，因此本报告只确认“产品层面存在该功能”，不确认 Android 版每个版本完全一致。PitterPatter 的商店页称美音/英音各有男女发音人、下载一次后可离线，并且按音标生成发音；但没有公开自定义词表导入入口或数据来源，不能将其标为“已证实可导入并自动双音标”。WordVine/词藤的官网证据已足以证明双音标和双预录音频，却不足以判断用户上传任意词表时能否同样补全。

![主流产品功能确认强度](/data/workspace/dr:mobile_word_ipa/fig_product_capabilities.png)
*图 1：主流背单词产品都能导入词表，但双音标与音频来源披露不均。数据来源：各产品官网、应用商店官方页面 [1][2][3][8][9][12]*

[下载：产品功能确认数据](/data/workspace/dr:mobile_word_ipa/data_product_capabilities.csv)

| 产品 | 自定义导入可验证性 | 内置/导入音标来源 | 双发音证据 | 证据边界 |
|---|---|---|---|---|
| 欧路/Eudic | 高：可放第三方词库 | 扩充词典；导入生词是否自动补未披露 | 单词、例句英音美音切换；离线语音库 | 不确认每个自定义词自动双音标 |
| 词藤 WordVine | 未见明确自定义文件导入 | 多源词典聚合、BrE/AmE 双音标 | 明确双发音，首次下载后离线 | 自建词表路径未公开 |
| List背单词 | 官方商店页明确外部导入 | 英/美音标可显示 | 英式/美式发音+音标 | Android 版本细节未逐版披露 |
| Anki/AnkiDroid | 官方支持文本/牌组导入 | 模板、笔记字段由用户决定 | `{{tts}}` 用设备 voice | 不是预录音频，依赖设备数据 |
| Quizlet | 官方支持文件与分隔符导入 | 页面未确认自动音标 | 未确认 | 偏云端整理/发布，存在 TTS 功能不说明离线 |
| 百词斩 | 二手资料称 TXT 批量导入 | 内置词书为主 | 设置可切英式发音 | 官方页无完整自定义补全说明 |
| 墨墨背单词 | 资料称自建词库/导入生词 | 内置词书数据 | 可切美音，但等级门槛 | 导入词补全机制未披露 |
| 不背单词 | 官方网页上传每行一词 TXT | 自动匹配释义/发音 | 随身听 | 数据来源与离线性未披露 |
| PitterPatter | 未见文件导入证据 | 按音标生成 | 英美各两人声，离线 | 是否为自定义导入未证实 |

## 二、核心原则：音标负责“显示与纠错”，TTS 负责“发声”，两套数据不能互相冒充

**用 TTS 只能解决发声，不能自动得到准确 IPA。** Android `TextToSpeech.speak()` 的合法输入是自然语言文本，返回状态只表示合成任务是否进入队列或完成；它不返回音标，也不会因语音正确而反推出 IPA。把 `/ˈæp.l̩/` 或 `/kɒlə/` 送进去，引擎可能逐字母读 ASCII 斜线、标点，也可能用自然语言把“slash”“r colour”念出来。要显示音标，项目必须持有一份“规范拼写→音标”映射，或在导入时在线取得；只有朗读英文单词本身时，才调用用户已确认的 en-GB/en-US voice。Android 官方语音查询接口可以枚举 voice 并检查语言可用性，但是否需要网络、是否安装仍是逐 voice 属性 [11]。

**真正成熟的实现会把三层分开：规范词形、英美音标、英美发音资源。** 第一层是大小写和变体归一后的规范 headword，例如 `colour`、`organization`；第二层是两列独立 IPA，允许一列为空；第三层是发音执行器，优先本地预录音频，其次离线设备 TTS，再次联网 fallback（本项目已排除网络，故只有前两项）。显示 UI 永远读第二层，而发音按钮读第三层。若把 TTS 实时结果当作权威音标缓存，后续升级引擎或换设备就可能让显示结果漂移；若把规则猜测结果和真人词典结果混在同一字段，用户也无法判断哪些值得信赖。

**“零第三方依赖、零权限、全程离线”会自然淘汰大多数云端捷径。** 不需要 `INTERNET`、`WRITE_EXTERNAL_STORAGE`，也不依赖 Google Play Services 时，联网词典 API、Firebase Remote Config、动态下载音频、Google/Microsoft 云端神经语音都不能作为默认路径。Android 8.0+ 的 `TextToSpeech` 仍属于系统 API，不必然增加权限，但语音包是否随系统预装不是应用可控制项；因此它适合做“无数据时兜底”，不适合承担“音标展示”的唯一来源。若必须 100% 离线且无语音数据也能发声，只能在构建期为每个目标词预生成音频，这会显著提高 APK/应用体积与许可审查成本。

## 三、离线数据源：直接 IPA 与 ARPAbet 转写是两回事

**ECDICT 是最实用的英汉释义与候选音标来源，但不是可靠的“英美双 IPA”来源。** 其仓库说明 `word` 之外有 `phonetic`、`definition`、`translation`、`pos`、`exchange` 等字段；一个派生项目读取 770,611 行 ECDICT，记录基础 CSV 为 62.9 MB，另有项目明确警告原始 `pos` 全为空 [4]。字段表写“phonetic：音标，以英语英标为主”，因此正确做法是把 `phonetic` 视为“英音优先、来源混合、质量待校验”的候选，而不是机械拆成英美两列。仓库搜索结果没有显示明确许可文件；部分下游工程自行采用 MIT 说明，但下游声明不能替代上游许可证。故本项目可以离线构建检索原型、检查字段质量，正式打包前应向作者取得书面许可，或在许可澄清前不用其音标字段。

**CMUdict 的许可证相对清楚，却只直接覆盖北美英语 ARPAbet。** 官方页面称它包含超过 134,000 个词的发音，使用 39 个 ARPAbet 音素、元音带 0/1/2 重音；未收录词、拼写错误、数字都不会返回结果 [5]。派生数据集 `CMUdict eSpeak-NG Aligned IPA Lexicon` 有 135,166 条，同时给 ARPAbet 和 eSpeak 风格 IPA，字段包括 `word`、`raw_word`、`ipa`、`stress_pattern` 等 [17]。这为“字典查找 + ARPAbet→IPA 映射”提供路径，但仍有四类损失：英美差异（如 `bath`、`tomato`、`schedule`）只能给美音；同形异读（如 `read`、 `lead`、`content`）只以不同变体存在，须结合词性消歧；专有名词、缩写、最新词和复合词大量缺失；声门塞音、联接音、成音节辅音及口音细节依赖映射表设计。若映射表不做词性消歧，输入 `read` 时随机选一个读音，会把规则词典的“覆盖率”伪装成准确率。

**Kaikki/Wiktionary 是最完整的一手候选，但代价是数据规模、权利审查与后处理。** 截至 2026-09-02 的英语原始 dump 为 JSONL，未压缩 23.9 GB、gzip 压缩 2.8 GB；页面还提供约 20.4 GB 的音频批量 tar，含约 942,000 个文件，但说明约 0.5% 因 Wikimedia Commons 重定向缺失 [6]。Wiktextract 论文确认每个 JSON 对象包含 `word`、`lang`、`pos`、`senses`，并可有 `sounds`、`forms`、`translations` 等；发音字段包含 IPA 和音频文件，且程序化生成音标会带口音标注 [7]。Kaikki 页面建议学术引用论文，却未在下载页给出一份明确、统一的开放许可证。Wiktionary 正文通常遵循 CC BY-SA 3.0，音频在 Commons 上可能为 CC BY、CC0 或其他开放许可，因此批量打包必须逐记录保留 `license`、`attribution`、`source`、`author` 字段，不能只写一个总许可；来源不明者宁可排除。

![核心数据集条目规模](/data/workspace/dr:mobile_word_ipa/fig_source_entries.png)
*图 2：完整数据集覆盖规模远超普通词表，但也包含大量低频词、专名和变体。数据来源：ECDICT、CMUdict、CMUdict→IPA 派生数据集、Kaikki.org [4][5][6][17]*

[下载：数据集条目规模数据](/data/workspace/dr:mobile_word_ipa/data_source_entries.csv)

**FreeDict 和 Open English WordNet 适合释义/词网，不适合作为本项目的核心音标包。** FreeDict 是 TEI P5 XML 双语词典，官方仓库强调可离线使用，音标可由 `espeak-ng` 生成；其元数据页显示英文词表只有 8 本，单个词表规模有限 [13][14]。TEI 可表达发音，不等于每个词都有人工校对 IPA；由 eSpeak 生成的音标同样有 TTS 发音学误差。官方资料和 Debian 版权页显示 FreeDict 多为 GPL-2+，个别词表 GPL-3+，符合“清晰许可证”，但 GPL 词表数据是否构成 APK 中必须开源的“数据”存在争议；商业发布最好只在独立资源文件中保留原许可与版权，并把该资源文件视为用户可替换数据，而不是把数据合并进私有核心代码。Open English WordNet 2025+ 有 WNDB、LMF XML、Turtle 三种格式，压缩体积分别为 10.5、12.3、19.4 MB，并将专有名词迁移到 Open English Namenet [10]；公开下载页和字段摘要未给出发音字段，故本报告不把它列为音标源。

**eng-to-ipa、Epitran、g2p-en 是算法 fallback，不是权威双音标事实库。** eng-to-ipa 以 CMUdict 为基础并把 ARPAbet 映射成 IPA；它能在词命中时避免手工映射，但覆盖仍受 CMUdict 限制，口音转换只是规则近似。Epitran 是 MIT 许可的正字法转 IPA 工具，英语需要 Flite 的词典查找；没有词典时走规则，规则无法可靠处理 `colonel`、`yacht`、`climb`、`Wednesday` 及同形异读。g2p-en 的 Apache-2.0 资料明确它是 CMUdict 词典加神经网络 OOV 处理；OOV 模型会降低未知词的随机失配风险，却不能保证英音或学习词典标准标音。故算法路径只适合给未命中词“发声候选”，不应默认覆盖 `phonetic_uk/phonetic_us`。

![数据集下载体积](/data/workspace/dr:mobile_word_ipa/fig_source_size.png)
*图 3：音标文本通常为数 MB 至数十 MB，预渲染音频则是 GB 级负担。数据来源：ECDICT、CMUdict、Kaikki.org、Open English WordNet [4][5][6][10][17]*

[下载：数据集体积数据](/data/workspace/dr:mobile_word_ipa/data_source_size.csv)

| 数据源 | 规模与 IPA 形态 | 字段/格式 | 许可证与打包判断 | 已知质量缺陷 |
|---|---|---|---|---|
| ECDICT | 基础版约 76 万词；单 `phonetic`，主要英语音标 | CSV：`word/phonetic/definition/translation/pos/exchange` | 页面无清晰许可；必须向作者确认；可作离线检索评估 | 并非可靠双 IPA；拼写、繁体/英文标点、释义噪声需清洗；`pos` 可能全空 |
| CMUdict | 超过 13.4 万；ARPAbet，元音 0/1/2 重音 | 词+空格分隔音素；可直接查 | BSD 风格宽松许可，随 APK 保留版权提示可行 | 仅北美英语；同形异读、专名、缩写、新词大量缺失 |
| CMUdict→IPA 派生表 | 135,166 条；已有 IPA | `word/raw_word/ipa/espeak_ascii/arpabet/stress/syllables/variant` | 继承 CMU BSD-2-Clause；确认上游 SHA/版本后可用 | 仍继承 CMUdict 覆盖与口音限制 |
| Kaikki/Wiktionary | 英语 dump；可含英/美/其他口音 IPA；约 94.2 万音频 | JSONL：`word/lang/pos/senses/sounds/forms` | 逐记录权利混杂，必须携带来源/许可/署名；不适合无审查打包 | 条目质量不一、自动生成音标、口音标签不一致、同形异读需消歧 |
| eng-to-ipa | 命中 CMU 时给 IPA，约 13 万美音基础 | Python dict/函数 | 依其仓库 LICENSE 打包；本项目不需要运行 Python | OOV 无声标；英音是规则模拟；同形异读不消歧 |
| Epitran | 规则/词典混合，英语用 Flite lex_lookup | `transliterate()` 输出 Unicode IPA | MIT；适合构建期，不宜完整塞入 Android 运行时 | 无词典时规则会误读不透明拼写和专名 |
| g2p-en | CMU 词典 + 神经网络 OOV | G2P 模型/词典 | Apache-2.0；模型体积与依赖需评估 | OOV 会猜，不是真人词典；默认偏向美音 |
| FreeDict | 多语种双语小词表；音标可有可无 | TEI P5 XML | 多为 GPL-2+，个别 GPL-3+；商业项目保留完整许可文件 | 英汉规模有限；部分 IPA 来自 eSpeak |
| Open English WordNet | 2025+ Turtle 19.4 MB；无公开音标字段 | WNDB/XML/Turtle | 页面未给出清晰许可文本，不应默认打包 | 适合语义关系，不适合音标/音频 |
| Anki 共享牌组音频 | 社区规模不可统计 | 音频文件嵌入 `.apkg`/collection.media | 共享牌组仅个人学习、不可再分发；排除 | 上传者是否拥有录音/数据授权无人审核；质量不一 |

## 四、五类工程方案：覆盖率是数据问题，稳定双发音是 voice 状态问题

**方案①只用系统 TTS，体积最小但不能显示音标。** 它实现最快，也不需要随包分发词典，只需持有 `en-GB` 和 `en-US` voice。优点是零数据体积、零网络、可朗读任意词形；缺点是无法显示 IPA，口音与发音质量依赖厂商，语音包缺失、网络 voice 和设备默认 voice 都会造成静默回退。它适合作为离线兜底，而不是用户所要求的核心补全方案。

**方案②内置精简“词→英美 IPA”表，是本项目的黄金平衡。** 以词频、考试标签、用户词表规模为目标，把 2–5 万常用词连同人工或权威来源 IPA 构建为只读数据库。导入时先规范大小写、标点、Unicode，查表后仅在两列都空或用户选择“覆盖未编辑”时写入；未命中留空并显示“无离线音标”。优点是覆盖常见词高、可离线、无网络、体积可控、可维护；缺点是生僻词、专名、最新词和新释义无法显示。对于 3 万词的高质量包，文本 CSV 约 2.5–4 MB，按 4 字节哈希前缀、规范化 headword 与两列 IPA 建立 FTS5/Room 索引后，运行时 SQLite 可能约 6–12 MB；这是工程估算，实际取决于去重、索引页大小和 `PRAGMA encoding`（UTF-8 不可变）。

**方案③完整数据加索引适合研究型查询，不适合当前产品约束。** 完整 ECDICT 基础 CSV 已 62.9 MB；CMUdict 本身约 5.3 MB 但只有 ARPAbet；完整 Kaikki JSONL gzip 为 2.8 GB，解压后 23.9 GB，不能装进常规 APK，只适合桌面校验、服务器或用户自行下载的扩展包 [4][5][6]。若只取英语 IPA 并压缩，可能在几十 MB 级，但仍远大于方案②。其覆盖率和多口音丰富度最好，维护成本也最高：上游每周更新，必须做 schema、许可证、口音标签和同形异读迁移。

**方案④联网查词 API 最快得到双音标，但破坏零权限与离线承诺。** API 可以在导入阶段补齐英美 IPA、音频 URL 和同形异读，后端能轮换多个词典并处理频率限制。它适合桌面导入助手、WiFi 初次增强，却需要 `INTERNET`、后端可用性与隐私合规，网络失败时导入会阻塞。若项目坚持全程离线，API 只能作为可选开关，并在隐私政策与权限说明中披露；不能把它称为“离线方案”。

**方案⑤预渲染音频文件体积大且权利最危险。** 以常见 MP3/OGG 单文件 15–40 KB 估算，5 万词英美双发音约 1.5–4 GB；即便激进压缩，3 万词也常在数百 MB 级。录音通常受表演者邻接权、录音制作者权和词库许可共同约束，CC BY-SA 音频还会带来许可证传播/署名问题。只有能够从许可证明确的单一来源批量取得 CC0/CC-BY 录音、按词条做语音活动切分、压成 OGG 并保留 attribution 时，才适合做可选离线音频扩展；一般词典 TTS 或设备 TTS 更稳妥。

| 路径 | 离线 | 权限 | 第三方依赖 | APK/运行时体积 | 常见词覆盖率 | 口音稳定性 | 维护成本 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 仅 TTS | 是 | 零 | Android TTS | 极小 | 无音标 | 中低，受设备影响 | 低 |
| 精简 IPA 查询表 | 是 | 零 | 无 | 6–12 MB 估计 | 高（目标词） | 显示稳定 | 中 |
| 完整词典+索引 | 是 | 零 | 无 | 数十 MB 至 GB | 最高 | 显示稳定 | 高 |
| 联网 API | 否 | 需网络 | 后端/第三方 | 小 | 高 | 依赖服务 | 中高 |
| 预渲染音频 | 是（下载后） | 可为零 | 无 | 数百 MB 至 GB | 取决于录制范围 | 高 | 高（权利/存储） |

## 五、推荐架构：以“导入补全任务”而非“即时查词”组织数据

**导入应成为带预览的事务，而不是后台静默写库。** 用户选择 TXT/CSV 后，应用先解析、归一、显示“新增/重复/未命中音标/有手工编辑冲突”四类计数；只有在用户确认后才批量写入。冲突不是简单去重：Anki 官方文档已证明，首字段重复时默认会更新已有笔记其他字段，并保留调度；如果不做字段级策略，导入会覆盖手工订正 [3]。本项目已有 `phonetic_uk/phonetic_us`，应进一步保存“用户是否编辑”的状态。

**双 TTS 按钮必须验证 voice 的本地可用性，而不是只验证 locale。** Android 枚举 voice 后，`setVoice()` 可能返回错误；`getVoices()` 可结合语言、网络使用和安装状态过滤，`isLanguageAvailable()` 只能判断语言层面 [11]。因此首次启动建立两个运行时 voice 候选集：`en-GB` 精确 locale 优先，再按 `en_GB`、通用 `en` 降级；`en-US` 同理。每个 voice 先合成一个短词并检查回调成功、错误码、是否需网络。若一个 voice 所需数据未下载，应显示“英音语音包未安装，请在系统 TTS 设置中下载”，不要静默把 `en-US` 当成英音。永远不把 IPA 文本喂给 TTS，也永远不用 TTS 返回结果更新 `phonetic_uk/phonetic_us`。

**离线音频资源采用“词条键→文件”映射，而非 IPA 字符串命名。** 文件名带斜杠、冒号或 Unicode 会触发文件系统兼容问题；以 `sha1(headword_lower + '\u0001' + locale)` 或稳定词形键命名，音频表记录 `word_norm`、`locale`、`format`、`bytes_sha256`、`license_id`、`source_url`、`attribution`。预渲染或下载时按 locale 与词表批量构建，只有全部文件校验通过才启用该语音包。对 GB/US 分别维护已安装版本，避免用户只下载美音时英音按钮误报可用。

## 六、用户导入流程应允许“补齐释义、音标或都不补”

**导入解析应先识别分隔符，再做词级归一。** 先尝试 TAB、连续空白、逗号或分号；若第一列是英文 headword，其余内容不是标准释义，就把整行作为中文释义候选。依次执行 NFC 归一、去 BOM、折叠控制字符、去除首尾标点与中英文引号、合并内部多余空白，但保留内部连字符和英文撇号。把 `colour` 与 `color`、`organise` 与 `organize`、`theatre` 与 `theater` 作为词形别名；查表时同时匹配规范 headword 与别名，显示仍保留用户原始拼写。导入表增加 `source_form` 列，确保输出显示与查询 key 可追溯。

**补全策略应分为三档，避免“有数据就覆盖”。** 第一档是“只补空”：UK、US 两列都为空时才写入；这最符合已有去重逻辑，也不伤害手工订正。第二档是“词典优先但提示冲突”：若新来源与现有内容不同，先展示差异，用户可选择保留、替换或新建；第三档“强制刷新”必须二次确认。官方用户笔记、自定义释义和未知用户列应永不因重新导入被清空。对于中文释义混入音标，解析后用 `/[\/\[\]()]/` 初筛，并检测常见音标 Unicode 范围；命中后把疑似 IPA 移入临时字段供用户确认，不自动删除。

**未命中必须可观察、可导出、可批量增强。** 导入预览列出每个未命中词并显示原拼写、归一后拼写、是否因标点/大小写/英美拼写造成差异；提供“复制未命中词”“导出为 TXT”和可选的“仅对这些词启用 TTS 发声”。不要填 `N/A`、占位符或自动生成的错误音标，因为空值语义是“没有权威数据”，错误值会被用户当作学习事实。统计覆盖率以成功查到 UK 或 US 的词数÷有效目标词数计算，专名、缩写和拼写错误分开统计，避免单一百分比掩盖问题。

## 七、常见故障都有可验证的规避办法

**TTS 静默回退的核心不是再点一次发音，而是枚举、选择、合成三段分别校验。** 在初始化回调后调用 `voices`，过滤目标 locale、网络要求和可用性；对候选 voice 调用 `setVoice()` 并检查返回值；用 `speak()` 前保存 utteranceId，监听 `onStart/onError/onAudioAvailable`。若错误码是 `ERROR_NOT_INSTALLED_YET`、语言缺失或网络错误，按钮立即置灰并给出系统设置入口。不要写“如果没声音就延时重试”，网络 voice 可能在无网时每次都看似成功却不出声。

**中文释义里的音标应在导入和编辑保存两个入口统一清洗。** 使用一套正则识别成对的 `/.../`、`[...]`、`(...)`，注意括号平衡，避免把释义中的“(adj.)”或例句引号当作音标。再把候选串与项目 IPA 字符白名单比较；白名单可允许 `ˈˌʌæɑɒɔəɜɪiːʊuːeɪaɪɔɪaʊəʊθðʃʒtʃdʒŋɹɾɚɝ` 等，但必须允许后续扩展。高置信字段自动移动，低置信字段保留原值并标黄，用户可撤销。保存时分别记录“原释义 token 位置”，这样回滚不会误删相邻文本。

**英美拼写差异要用别名表，不能把两种拼法当成两个词。** 导入时对别名映射执行双向查找，UI 显示用户原始拼写，查询包同时索引 `colour/color`、`centre/center`、`realise/realize`、`aluminium/aluminum`。别名表只表示“词形变体”，不应自动互相覆盖释义；若英文释义不同，应提示用户。对于 `read`、`lead`、`content`、`wind` 等同形异读，查表结果可能有多条 IPA，UI 提供“选择发音”并保存 `pronunciation_id`；没有上下文时宁可显示全部候选，也不用词频硬选。

**TTS 读错多音节、专有名词、缩写和缩略词时，应优先采用录音或用户录制。** 对品牌、人名、地名和缩写建立禁用自动发音名单；对 TTS 仍可接受的未命中词，按 locale 朗读原始英文文本而非 IPA。缩写 `Mr.`、`etc.`、`US` 需配置逐条读法，避免把 `etc.` 念成“et-c”或把 `US` 念成单词。多音节词可在 TTS 前插入短停顿或 SSML `<break>`（仅当目标引擎确认支持），但不能假定所有 Android TTS 引擎解释 SSML 一致；检测不支持时应退回纯文本。

**规则 G2P 只能在“已确认覆盖”和“明确标记猜测”两条边界内使用。** 构建期对 CMU 命中词直接保存 ARPAbet→IPA 结果，对 OOV 模型结果另存 `is_inferred=1`；UI 用浅色或问号标识。导入时不自动写入 `phonetic_uk/phonetic_us`；仅在用户主动开启“为无数据词生成发音候选”时才把猜测写入独立候选字段。对 `colonel`、`yacht`、`climb`、`island`、`Wednesday`、`recipe` 等高冲突词，加入“拒绝规则/允许列表”，宁可交给 TTS 朗读，也不用错误 IPA 固化错误。

## 八、对本项目最可落地的两项改造：只读离线词典 + 字段级导入策略

**改造一：新增 `phonetic_index` 只读数据库，构建期融合多源 IPA。** 数据来源优先顺序建议是：Wiktionary/Kaikki 中带明确英音、美音标签的 IPA（保留 source/license/attribution，逐条审查权利）；ECDICT 的单音标作为候选，仅在能确认英音或美音语义时分别映射；CMUdict→IPA 或 eng-to-ipa 作为美音补充；Epitran/g2p-en 只生成候选。目标首期 3 万核心词，二期扩展到 5–10 万。生成脚本流程如下：

1. 下载固定快照，记录 commit/sha256/日期；ECDICT 未取得清晰许可前不把音标用于分发。
2. 用同一 Unicode NFC、小写、去标点函数规范 headword；建立别名表。
3. 对每条目标词选择英音 IPA：优先 Wiktionary `BrE`/`UK` 标签，无则人工复核候选；美音同理。
4. 过滤不可见控制字符、重复斜杠、明显 ASCII 错误（如把 `/r/` 与 `/ə/` 拼接错误）、长度异常、括号不平衡。
5. 对同形异读保存 `pronunciation_id`、词性和来源；默认主条目由词频或词典主标签决定。
6. 输出 `headword_norm, source_form, phonetic_uk, phonetic_us, pronunciation_id, source, license, attribution`，再构建 SQLite。
7. 对 10% 样本人工抽检，至少覆盖高频词、英美差异词、多音节词、专名、缩写、同形异读。

建议的构建脚本入口可以是：

```bash
python scripts/build_phonetics.py \
  --target-words assets/wordlists/cefr_30000.txt \
  --wiktionary-extract downloads/kaikki-en-20261030.jsonl.gz \
  --cmudict-ipa downloads/cmudict_ipa_135166.tsv \
  --ecdict downloads/ecdict.csv \
  --alias-map assets/wordlists/en_gb_us_variants.tsv \
  --output app/src/main/assets/phonetics \
  --sample-report build/reports/phonetics_sample_audit.csv
```

只读数据库结构：

```sql
CREATE TABLE phonetic_entry(
  id INTEGER PRIMARY KEY,
  headword_norm TEXT NOT NULL,        -- NFC, lowercased, punctuation-stripped
  phonetic_uk TEXT,
  phonetic_us TEXT,
  source TEXT NOT NULL,
  license_code TEXT,
  attribution TEXT,
  verified INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_phonetic_norm ON phonetic_entry(headword_norm);
CREATE VIRTUAL TABLE phonetic_fts USING fts5(
  headword_norm, phonetic_uk, phonetic_us, content='phonetic_entry'
);
```

3 万词的工程体积估算：两列 UTF-8 IPA 平均各 15–25 字节，行开销与索引后，文本构建中间数据约 2.5–4 MB，最终 SQLite 约 6–12 MB；5 万词约 9–18 MB。这里不把 2.5–4 MB 中间 CSV 与最终 SQLite 相加，因为后者已包含索引数据。相比 Kaikki 完整 dump，这是可承受范围；相比“只依赖 TTS”，则换来了可显示、可审计的音标。

**改造二：把现有两列从“导入时覆盖”升级为“带来源与编辑状态的字段”。** 现有 `words` 表建议做向后兼容迁移：

```sql
ALTER TABLE words ADD COLUMN phonetic_uk_src TEXT NOT NULL DEFAULT 'user';
ALTER TABLE words ADD COLUMN phonetic_us_src TEXT NOT NULL DEFAULT 'user';
ALTER TABLE words ADD COLUMN phonetic_uk_locked INTEGER NOT NULL DEFAULT 0;
ALTER TABLE words ADD COLUMN phonetic_us_locked INTEGER NOT NULL DEFAULT 0;
ALTER TABLE words ADD COLUMN import_batch_id TEXT;
ALTER TABLE words ADD COLUMN needs_review INTEGER NOT NULL DEFAULT 0;
```

其中 `_src` 只取 `user/system/preview/inferred`；`system` 仅来自已审核离线包，`inferred` 只能进入候选面板。导入事务如下：

```sql
INSERT INTO import_batch DEFAULT VALUES RETURNING id; -- 生成 batch id
-- 对每行词：
SELECT id, phonetic_uk, phonetic_us, phonetic_uk_locked, phonetic_us_locked
FROM words WHERE normalized_word = ?;
-- 若新增：直接插入 preview/system 值
-- 若重复且两列空：写入补全
-- 若重复且已锁定：保留用户值，不覆盖
-- 若重复且未锁定但不同：写入 needs_review=1，展示冲突
```

导入预览 SQL 可一次给出统计：

```sql
SELECT
  SUM(CASE WHEN w.id IS NULL THEN 1 ELSE 0 END) AS new_words,
  SUM(CASE WHEN w.id IS NOT NULL
        AND (w.phonetic_uk IS NULL OR w.phonetic_us IS NULL) THEN 1 ELSE 0 END) AS fillable,
  SUM(CASE WHEN w.id IS NOT NULL
        AND COALESCE(w.phonetic_uk_locked,0)=1
        AND COALESCE(w.phonetic_us_locked,0)=1 THEN 1 ELSE 0 END) AS locked_conflicts,
  SUM(CASE WHEN p.headword_norm IS NULL THEN 1 ELSE 0 END) AS no_ipa_hit
FROM import_rows r
LEFT JOIN words w ON w.normalized_word=r.normalized_word
LEFT JOIN phonetic_entry p ON p.headword_norm=r.normalized_word;
```

最终导入前弹出“仅补空/创建未命中/跳过重复”三选项；默认只补空。这样既不破坏已有去重，也能让“用户手工订正优先”。

**超纲词的处理不是降级，而是明确进入无权威数据状态。** 第一优先级是保留用户原始词形并正常背中文释义；第二优先级是允许英音/美音 TTS 按钮各按设备可用 voice 发声；第三优先级是在用户开启猜测时显示“机器猜测，尚未验证”的 IPA 候选；第四优先级是提供未命中导出。若某个词既查不到音标，目标 locale voice 也不可用，按钮明确禁用并解释原因，不调用默认 voice 伪装英音或美音。这样覆盖率即使只有 70%，也仍是一个可信产品：另外 30% 是“已知未知”，而不是 30% 的潜在错误。

## 九、落地顺序与验收标准

**第一阶段应先验证数据质量，再改 UI。** 选 3,000 个高频学习词作为黄金样本，分别记录来自 Wiktionary、ECDICT、CMUdict 的英美 IPA；组织双语者或词典专业者按 10% 比例抽检，英美各抽 150 词，重点检查 `bath`、`dance`、`schedule`、`tomato`、`garage`、`advertisement`、`lieutenant`、`herb`、`vitamin`、`privacy` 等口音敏感词。只有美音源的 `phonetic_uk` 必须为空，不能靠正则伪造；若准确率未达到项目预设阈值（例如至少 95% 抽样正确，且英美敏感词单独达到 90%），不要扩大打包。

**第二阶段把导入改成预览—确认—写入。** 测试集至少包括：纯 TXT 每行一词；CSV 只有两列；词中带 BOM、中文全角空格、Tab、连续空格；同一词重复出现；已有手工音标且新来源不同；`colour/color`；`read/read` 同形异读；`Dr./etc./NASA`；中文释义夹 `/ˈrɛd/`；数据库未命中词。验收条件是：不会创建重复学习记录，不会覆盖锁定音标，不会把 IPA 文本送进 TTS，不会在缺少语音数据时静默切到默认 voice，也不会因 TTS 回调晚于 UI 更新而崩溃。

**第三阶段再考虑可选音频包。** 在双 IPA 表稳定后，选择 CC0 或 CC-BY 明确许可的录音做 3,000 词试点，分别构建 GB/US OGG 包；校验 SHA-256、采样率、静音首尾、文件名冲突和归因文件。只有试点证明包体积、播放稳定、许可文件完整后，才扩展至目标 3–5 万词。录音无法覆盖的词，回到设备 TTS 兜底，并保持同一双按钮语义。

**结论判断是：最稳健路线不是“找一份万能词表”，而是“高质量核心表 + 设备 TTS + 字段级导入”。** 如果项目要求彻底零依赖且不接受语音包缺失，就应接受双发音功能可能受 Android 厂商限制；如果要求所有词都显示权威双 IPA，就应接受离线查询包至少增加数 MB；如果要求所有词都有真人双发音，就应接受 GB 级音频与严格授权流程。三者不能同时实现为极小、纯离线、无条件、零权利风险。对当前已有 `phonetic_uk/phonetic_us` 与双 TTS 按钮的架构，增加只读 3 万词查询包、导入冲突预览和 voice 安装检查，是改动最小、收益最明确的下一步。

## 引用来源

[1] https://dict.eudic.net/Content/help/dict/help_android.html
> “下载离线发音库 点击【工具】-【发音设置】-【下载离线发音库】。”

[2] https://wordvine.cn/
> “词典多源对照 聚合多套开源词典释义交叉比对，英式(BrE)/美式(AmE)双音标双发音。”

[3] https://docs.ankiweb.net/manual/importing/text-files
> “the existing note’s other fields will be updated based on content of the imported file.”

[4] https://patch-diff.githubusercontent.com/skywind3000/ECDICT
> “phonetic 音标，以英语英标为主”

[5] http://www.speech.cs.cmu.edu/cgi-bin/cmudict/
> “contains over 134,000 words and their pronunciations.”

[6] https://www.kaikki.org/dictionary/rawdata.html
> “Download raw Wiktextract data (JSONL, one object per line) (23.9GB) or compressed .gz (2.8GB)”

[7] https://aclanthology.org/2022.lrec-1.140.pdf
> “This includes IPA pronunciations and sound files where available.”

[8] https://quizlet.com/content/create-flashcards-from-words-and-definitions-with-quizlet-ai
> “Most students already have their material in a Google Doc, a class handout, or a notes app.”

[9] https://www.baicizhan.com/?learning=primary-school-english
> “全国58个校内、校外教材版本同步词书”

[10] https://en-word.net/downloads
> “The major change this year is the transfer of proper nouns to a new resource called Open English Namenet.”

[11] https://developer.tomtom.com/assets/downloads/tomtom-sdks/android/api-reference/2.2.1/tts/tts/com.tomtom.sdk.tts.android/-android-text-to-speech-engine/index.html
> “getVoices(): Result<Set<Voice>, TextToSpeechEngineError> Retrieves a set of available text to speech voices.”

[12] https://apps.microsoft.com/detail/xp89454c2pb5r7?hl=te-IN
> “自定义词库：支持外部文件导入。”

[13] https://www.freedict.eu/
> “FreeDict dictionaries offer you the greatest deal of flexibility possible… All the lookups are offline.”

[14] https://www.openhub.net/p/freedict
> “The dictionaries may include information on the pronunciation, etymology and such.”

[15] https://github.com/freedict/fd-dictionaries
> “All dictionaries are encoded in TEI (version 5).”

[16] https://ankiweb.net/account/terms
> “the deck author grants you… a permanent, non-revocable… license to use the material in your personal studies.”

[17] https://huggingface.co/datasets/cmusphinx/cmudict-espeak-ng-ipa
> “Total Entries 135,166 phonetic entries.”
