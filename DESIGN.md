# 单词酷 · 设计系统与架构规范 v1.11

> 版本沿革：v2 页面重设计 → v3 加功能 → v4 修 15 项缺陷 → v5 英音 / 美音双发音与双音标 → v6 内置离线开源音标词典 → v7 对照同行产品与开源数据源做实证调研 → v8 接入微信 / QQ 文件分享并做成 CI 真编译 + 自动发版 → v9 应用内检查更新（首次、也是唯一一次新增 `INTERNET` 权限）→ **v9.1 按用户要求移除 GitHub Actions 工作流** → **v10 拼写测试（v1.3）** → **v1.4 内置考研词库（versionCode 5）** → **v1.5 独立设置页（versionCode 6）** → **v1.6 多选范围与顺序 / 打乱（versionCode 7）** → **v1.7 词库折叠头与行内发音（versionCode 8）** → **v1.8 Unit → Day 分组折叠（versionCode 9）** → **v1.9 词库只读 / 英音单列 / 编辑移到导入（versionCode 10）** → **v1.10 词库组别多选（versionCode 11）** → **v1.11 内置离线语音（versionCode 13）**。
>
> **v9.1 一句话**：`.github/workflows/build-apk.yml` 已删除，push 到 `main` 不再自动编译、也不再自动发新版 Release。已发布的三条 Release 与 APK 全部保留，应用内「检查更新」仍能读到现有的 latest（v1.2.0）；只是以后不会自动冒出新版本。源码、数据、文档、自检脚本一行没删，只少了这一个自动化文件。
>
> **v10 一句话**：学习页加「拼写测试 · 看释义写单词」。从当前组里取当天到期的词，看中文释义输入英文；提示首字母、听发音、不会看答案、结束后再来一轮都齐。判分直接复用 `recordGrade`（拼对按「认识」、拼错按「不认识」），不新增数据库字段、不新增权限、不新增依赖；所以拼写结果和认词结果共用同一套 1/2/4/7/15/30 天复习节奏。
>
> **v1.4 一句话**：把用户提供的 Unit1–5 考研词表整理成 `assets/kaoyan.tsv`（711 个唯一词 / 23 组），导入页点一次「载入考研词库」就进入原有预览流程：自动补离线音标、显示未命中词、确认后写入。同形异读合并为一条，英式拼写写在 note 里；不新增权限、不改数据库结构。
>
> **v1.5 一句话**：导入页太挤，把「设置」拆成第 5 个 tab。导入页只留文档 / 粘贴 / 划组 / 内置词库 / 微信；统计页只留统计；每天解锁数、备份、重置、清空、发音、音标、版本更新全部搬到设置页。只是搬家，数据库、备份格式、权限与既有操作逻辑不变。
>
> **v1.6 一句话**：学习页顶部把「组别」升级成「范围」，可以多选 Unit / Day；旁边加「顺序」在按顺序与打乱之间切换。解锁、今日复习、小测、拼写全部走同一组筛选条件；顺序在每轮开始时打乱一次，不改变已有进度和复习间隔。
>
> **v1.7 一句话**：词库页的搜索 / 筛选 / 组别 / 计数 / 进度搬进 ListView 的 header，往下滑自然折叠；每个单词行右侧加「发音」按钮；学习页主按钮换成「开始学习」在左、「解锁今日新词」在右。只动视觉与入口，数据库、复习规则、权限不变。
>
> **v1.8 一句话**：词库页进一步按 Unit → Day 两层分组：Unit 大标题 + Day 小标题，点标题可折叠；搜索时临时全部展开，避免结果被折叠状态藏住。行仍复用同一套 `getWords` 查询，不新增数据库字段。
>
> **v1.9 一句话**：词库列表做减法：去掉每行的「未解锁 / 待解锁」，只显示英音，长单词完整换行；词库页改为只读，编辑与删除统一走导入页的「编辑单词」搜索框，避免在列表里误点误改。
>
> **v1.10 一句话**：词库页的组别筛选从单选升级为多选，复用学习页的「清空 / 全选 / 勾选」弹窗；列表查询与「解锁新词」共用同一组组名，按钮显示「组别：2 个组」。
>
> **v1.11 一句话**：把系统 TTS 从「唯一发音来源」降为兜底，APK 内置 sherpa-onnx + Piper Amy int8 美音模型；安装后不装语音包、不联网也能直接读。代价是 APK 从约 2.5MB 增到约 73MB，正式包 arm64 + armeabi-v7a。
>
> **v9 一句话**：应用内检查更新放在「统计 → 版本与更新」，点按钮才向 GitHub 发一次只读 GET 比对公开 Release 版本号；发现新版弹窗、点「去下载」才用浏览器打开下载页，绝不自动下载或安装。**这是全应用唯一联网点，也是唯一新增权限**：词库、导入、离线音标词典、双发音、复习、备份全部仍离线可用。用户明确确认「允许加 INTERNET 权限」后才做，见第 11 节。
>
> **v8 一句话**：微信里点开词表 →「用其他应用打开」/「分享」→ 选「单词酷」，文件直接进导入预览，不用先存到手机。做法是给 `MainActivity` 加三组 `intent-filter` 接住 `ACTION_VIEW` / `ACTION_SEND` / `ACTION_SEND_MULTIPLE`，统一走原有预览流程；**不读微信私有目录，导入链路不联网**。详见第 10 节。
>
> **v7 一句话**：先查别人怎么做，再改代码。结论是主流产品把「词表数据 / 音标显示 / TTS 发声」三层分开，且导入补全都走「只补空 + 未命中显式告知」；据此把音标补全从「三列全空才补」改成**逐字段只补空**、补上**英美拼写别名**、**保留同形异读全部候选**、**列出并可复制未命中词**、以及**数据 SHA-256 溯源**。数据层全部离线、零第三方依赖不变（权限口径变化见第 11 节）。
>
> **v5 一句话**：学习卡片上并列「英音 UK」「美音 US」两个按钮，点哪边用哪边口音读；有 UK / US 两套音标时并排显示，导入文档里写 `UK /…/ US /…/` 会自动拆成两套。发音仍走系统 TTS 的**本地离线语音包**，不引第三方库，发音链路不联网。
>
> **v6 修正上一版的结论**：那时说「离线应用没有内置词典，音标不会凭空生成」——现在**改为随包内置一份离线开源音标词典**（v6 为 147,488 条，v7 归一并保留多读音后重建为 146,647 条），导入时自动补全。结论变化的原因是把数据合法打包进 APK，而不是改成了联网查词：仍然不联网查词、不装别的东西，只是 APK 多约 4.4 MB。**查不到的词依旧留空，不会编造。**
>
> **v3 三件事**：① 学习卡片页一次只显示一个单词，自评后由用户点「下一个」才换词；② 导入支持 Word / Excel / 文字型 PDF，文档里的 `Unit 1` / `Day 2` / `第 3 单元` 标题行自动划组；③ 导入后可手动改组名、按顺序自动划组，词库页与学习页都能按组筛选。视觉沿用 v2「墨卡 / INK CARD」设计系统，未改动色板与组件规则。

## 1. 规范来源与落地方式

你列出的 `frontend-design`、`web-design-guidelines`、`ux-ui-agent-skills`、`ui-ux-pro-max`、`android-development`、`architecture`、`claude-android-skill` 等属于外部编码 Agent 的 Skill 生态，需要在对应工具里安装，本会话无法安装或加载它们。本平台技能市场里与需求最接近的只有 `archify`（架构可视化），已安装的 `image_generation` 可出界面视觉稿。

因此本次的做法是：**把这些规范的理念直接落成工程里的具体约束**，而不是依赖某个 Skill 运行时：

| 规范理念 | 本次落地 |
| --- | --- |
| 避开「AI 通用审美」 | 停用紫蓝渐变、玻璃拟态、发光边框、emoji 图标、满屏圆角白卡 |
| Web/界面一致性与无障碍 | 统一设计 token、1px 细线、微圆角；按 WCAG 2.2 AA 校验对比度 |
| 设计系统 | 固定色板、字阶、8dp 间距网格、组件规则，集中在一处维护 |
| 安卓整洁架构 / UDF / MVI | 文档中给出分层对照与演进路线，**运行时代码不改** |
| 架构可视化 | 用 `archify` 对照真实代码出一张带冻结边界的架构图 |

---

## 2. 设计系统「墨卡 / INK CARD」

编辑式、报刊式排版：纸感米白底 + 墨黑文字 + **唯一强调色朱红**；英文单词用衬线体、中文用黑体，靠字阶与细线建立层级，而不是靠阴影和色块。

### 2.1 色板（`res/values/colors.xml` 与 `Ui.kt` 的 `Pal` 双处同源）

| Token | 值 | 用途 | 对比度（对纸底） |
| --- | --- | --- | --- |
| `bg` | `#F7F5F0` | 页面纸底 | — |
| `card` | `#FDFCF9` | 卡片 / 行底 | — |
| `text` | `#16181D` | 墨黑主文字 | 16.4:1 |
| `text_sub` | `#6B6F76` | 辅助字 / 音标 | 4.6:1 |
| `primary` | `#C2402F` | 朱红：按钮、强调、进度 | 4.7:1 |
| `line` / `track` | `#D8D3C9` / `#DDD8CF` | 1px 细线 / 进度轨道 | 装饰，不承载文字 |
| `locked` | `#5C6067` | 未解锁状态底 | 白字 6.3:1 |

白字在朱红底 5.2:1、在墨黑底 17.8:1，均过 WCAG 2.2 AA（正文 4.5:1）。所有状态标签都是**深底 + 白字**，不靠颜色单独区分状态，同时保留文字标签。

### 2.2 字阶与排版

| 场景 | 字体 | 字号 |
| --- | --- | --- |
| 页面标题 | 黑体 bold | 20sp |
| 列表英文词 | **衬线 serif** bold | 22sp |
| 学习卡片主词 | **衬线 serif** bold | 38sp |
| 测验主词 | **衬线 serif** bold | 34sp |
| 统计数字 | **衬线 serif** bold | 30sp |
| 释义 / 正文 | 黑体 | 13–14sp |
| 辅助 / 音标 / 标签 | 黑体 | 11–12sp |

- 间距走 8dp 网格（4 / 8 / 12 / 16 / 24dp）
- 圆角统一收敛到 ≤4dp；`Ui.kt` 的 `rounded()` 把旧调用点的 10/12/18dp 自动收敛为微圆角，**调用方一行未改**
- 分隔一律 1px 细线，不用阴影、不用卡片堆叠

### 2.3 组件规则

| 组件 | 规则 |
| --- | --- |
| 主按钮 | 朱红实心、3dp 圆角、白字，最小高度 42dp |
| 次按钮 / 描边按钮 | 浅米或纸白底 + 1px 细线 + 墨黑字 |
| 输入框 | 下划线式（只保留底边细线），无填充无大圆角 |
| 卡片 | 纸白底 + 细线描边 + 4dp 圆角 |
| 词库列表行 | 纸白底 + 仅底边细线，无卡片感 |
| 筛选标签 / 状态标签 | 细线微圆角或深底白字，不用彩色药丸堆砌 |
| 进度条 | 极细朱红实线 + 浅米轨道 |

### 2.4 明确避免的 AI 俗套

紫蓝渐变、玻璃拟态、霓虹发光、emoji 充当图标、大面积圆角白卡叠阴影、渐变按钮、装饰性插画、无意义的多彩状态色。界面里的 `🔊 / 🔒 / 📖 / ✅ / ✓` 已全部改成纯文字。

---

## 3. 安卓架构：现状与演进（不改代码）

当前工程按职责已经自然分层，架构图见 `docs/architecture.html`（由 `archify` 对照真实代码生成，带「表现层」与「冻结契约」两个边界）。

| 层 | 文件 | 职责 |
| --- | --- | --- |
| 表现层 | `MainActivity.kt` + 四个 `*View.kt` | 渲染、事件分发、页面切换 |
| 设计 token | `Ui.kt` | 色板、圆角、状态色（本次只换值） |
| 规则层 | `WordDb.kt` / `DocxParser.kt` | 解锁、间隔复习、统计、备份、词表解析 |
| 数据层 | SQLite `words` / `checkin` + SharedPreferences | 持久化 |
| 系统能力 | SAF 文件选择、系统 TTS、XmlPullParser、`HttpURLConnection` + `org.json` | 零第三方依赖；v1.2 起仅 `INTERNET` 一个权限 |

演进建议（**仅建议，本次未改任何运行时代码**）：

1. **UDF**：四个 View 只读 State，用户事件统一上行到容器，不在 View 里直接改数据。
2. **Repository / UseCase**：把 `WordDb` 的解锁与复习规则抽成接口，表现层只依赖抽象，便于单测。
3. **MVI**：需要时引入 Intent / State / Effect，并以现有公开方法作为适配层，保证旧调用不破。
4. **多模块化**：按 feature 拆分时，先抽 `data` / `domain`，保持 `applicationId` 与数据库表名不变。

---

## 4. 接口冻结清单（重点）

以下原有内容**保持原样**（v2 未改一行；v3 只在其上追加，见第 5 节）：

1. **`WordDb.kt` 全部公开方法签名**：`getWords` / `getWord` / `insertWord` / `updateWord` / `deleteWord` / `setState` / `resetWord` / `unlockNew` / `dueWords` / `recordGrade` / `randomMeanings` / `stats` / `quizWords` / `importWords` / `loadDemo` / `clearAll` / `resetProgress` / `exportJson` / `importBackup` / `dailyGoal` / `today`，以及数据类 `Word` / `Imported` / `Stats` 的字段。
2. **SQLite 表结构**：`words` 与 `checkin` 的旧列名、类型、唯一约束、`wordcool.db` 库名全部保留。数据库版本 v1=1、v3=2、**v5=3**，升级依次 `ALTER TABLE` 加 `group_name` / `phonetic_uk` / `phonetic_us` 三列，**不删表、不清数据**。
3. **`DocxParser.kt`**：`parseBytes` / `parseDocx` / `parseText` / `parseLines` / `isZip` 的行为，以及 Word 导入格式（普通段落、两列表格、Tab / 冒号 / 分号 / 音标 / 行首序号）。
4. **备份 JSON 字段**：`app` / `version` / `exported_at` / `words[]` 及词条字段名，换机恢复逻辑不变。
5. **`MainActivity.kt` 与四个 View 的构造、调用契约**：`showWordDialog` / `showImportPreview` / `pickDocument` / `exportBackup` / `switchTo` / `dataChanged` / `speak` 等；四个 View 仍以 `(MainActivity, View)` 构造。
6. **v2 的全部 `R.id` 与布局文件名**：8 个布局、90 个旧 id 一个不改名、不删除（v3 新增 10 个、v4 新增 `dialog_input.xml`、v5 新增 9 个、v6 新增 8 个、v8 新增 2 个、v9 新增 3 个、v10 新增 13 个、v1.4 新增 1 个、v1.5 新增 1 个、v1.6 新增 1 个、v1.7 新增 1 个并新增 `view_library_header.xml`，v1.8 新增 6 个并新增 `item_library_unit.xml` / `item_library_day.xml`，v1.9 删 1 个 `tvState`、加 4 个编辑入口 id 并新增 `dialog_pick_word.xml`，现共 14 布局 / 148 个 id）。
7. **`Ui.kt` 的函数签名**：`Pal` 字段名、`dp` / `rounded` / `toast` / `show` / `stateName` / `stateColor` 全部保持；仅替换色值与圆角收敛系数。
8. **Gradle 与清单**：`applicationId`、minSdk 23 / targetSdk 34、**v1.11 起依赖本地 `app/libs/sherpa-onnx-2.0.2.aar`（Apache-2.0），不再是空 dependencies；除此之外不引 AndroidX / 网络库**。**v1.0/v1.1 为零权限；v1.2 起按用户明确确认新增唯一一个 `<uses-permission android:name="android.permission.INTERNET" />`**，只给「检查更新」用，仍无存储 / 定位 / 通讯录等任何其他权限，`tools/verify.py` 用白名单强制只允许这一个。v8 前 `AndroidManifest.xml` 一直没有改过；v8 起**只增不改**：加 `launchMode="singleTask"` 与 3 组接收文件的 `intent-filter`，原 MAIN / LAUNCHER 过滤器与 `exported="true"` 不变。Kotlin 源文件 8 → 9（v6 `IpaDictionary.kt`）→ 10（v9 `UpdateChecker.kt`）→ 11（v1.5 `SettingsView.kt`）→ **12（v1.11 `BundledTts.kt`）**；v6 另新增 `assets/ipa.tsv`、`LICENSES/`、`tools/`，均为数据与脚本，不是代码依赖。
9. **编译与安装方式**：v9.1 移除工作流后剩下 Android Studio / 命令行两条路径，外加直接下载已有 Release（见 `README.md`）。

本次实际改动范围：`res/values`（色板、主题）、`res/drawable`（卡片 / 按钮 / 输入 / 进度 / 行 / 页眉）、`res/mipmap-*`（启动图标）、`Ui.kt`（数值与圆角实现）、以及布局里的纯视觉属性（字体族、字号、背景引用、去掉 emoji 的文案）。

---

## 5. v3 扩展清单：只增不减

### 5.1 新增能力

| 能力 | 落点 |
| --- | --- |
| 一次一张卡 + 手动翻页 | `LearnView.grade()` 不再自动跳到下一词；新增 `btnStudyNext` 与 `tvGradeResult`，答完停在当前词，点「下一个」才换 |
| 多格式导入 | `DocxParser.parseBytes(bytes, fileName)` 按内容路由：docx / xlsx / 文字型 PDF / 纯文本；旧 `parseBytes(bytes)` 保留 |
| 自动划组 | `normalizeGroup()` 识别 Unit / Day / Lesson / 第 N 单元 等标题行，后续词条继承组名；标题行本身不入词库 |
| 手动划组 | `ImportView` 增加「划组管理」：点组改名、`renameGroup()`；按序自动划组 `assignSequentialGroups(prefix, size)` |
| 按组筛选与学习 | `showGroupPicker()` 复用于词库页、学习页；`getWords` / `dueWords` / `quizWords` / `unlockNew` 增加可选 `groupFilter` |
| 词条级组别 | 词条对话框新增「组别」字段，可单独改某个词属于哪一组 |

### 5.2 兼容性逐条（旧的东西一个没删）

1. **数据类**：`Word` / `Imported` 只在**末尾追加带默认值的字段**（v3 `groupName` / `group`，v5 `phoneticUk` / `phoneticUs`），旧构造、`copy()`、五 / 六参数 `Imported(...)` 全兼容。
2. **数据库**：v1→v2 加 `group_name`，v2→v3（v5 发布）加 `phonetic_uk` / `phonetic_us`，均只 `ALTER TABLE ADD COLUMN`，**不删表、不重建、不清数据**；旧词库、学习进度、打卡记录、组别完整保留。
3. **方法签名**：`getWords` / `dueWords` / `unlockNew` / `quizWords` 只增加**带默认值的尾部参数**；`speak(text)` 保留并新增 `speak(text, accent)` 重载；其余公开签名不变。
4. **备份 JSON**：新增 `group` / `phonetic_uk` / `phonetic_us`，`version` 仍为 `1`；旧备份缺字段按空值恢复。
5. **`R.id`**：旧 90 个一个不改名、不删除；v3 新增 10 个、v4 新增 `dialog_input.xml`、v5 新增 9 个（双发音按钮、双音标输入、口音设置）。
6. **视觉系统**：色板、字阶、圆角、细线规则、对比度全部沿用 v2，未重新设计。
7. **Gradle / Manifest / 零第三方依赖**：不变（该轮为零权限；v1.2 起仅新增 INTERNET，见第 11 节）。Excel 用 `ZipInputStream + XmlPullParser`，PDF 用 `java.util.zip.Inflater`，都不引库。

### 5.3 格式支持与已知限制

| 格式 | 支持 | 限制 |
| --- | --- | --- |
| `.docx` | 段落、两列表格、Tab 分隔、音标、行首序号 | 旧版二进制 `.doc` 不支持，请另存为 `.docx` |
| `.xlsx` | 自动找第一张工作表 + `sharedStrings.xml`，按列聚合、缺列补空 | 多工作表只读第一张；老 `.xls` 请另存为 `.xlsx` |
| `.pdf` | 文字层里的 `Tj / TJ` 文本，按 `/Length` 精确截流、FlateDecode 解压 | **扫描图片版无文字层，导不出**；CID 字体的中文释义可能乱码/缺失，建议用 Word/Excel 或粘贴导入 |
| `.txt` / `.csv` / 粘贴 | 全部支持，含带引号的 CSV | — |

### 5.4 组别命名规则

`Unit 1` / `Day 2` / `Lesson 10` / `第 3 单元` / `第十二课` 统一归一为 `Key 数字`，标题行请**独占一行**。手动划组的前缀可自由填，例如填 `Unit 1 Day` 会得到 `Unit 1 Day 1`、`Unit 1 Day 2`。空组名显示为「未分组」，也可整组重命名。组别列表按「前缀 + 数字」自然排序，不会出现 `Unit 1, Unit 10, Unit 2`。

---

## 6. v4 代码审查修复清单

这一轮对全部 8 个 Kotlin 源文件、25 个 XML 做了逐行走查，修掉以下**可确证**的问题：

| # | 问题 | 修法 |
| --- | --- | --- |
| 1 | 词库页按组筛选后点「解锁新词」，仍解锁全库 | `unlockNew` 传入当前 `groupFilter` |
| 2 | 学习页解锁新词后队列有缓存，新词要等旧队列排完 | 解锁后清空 `queue` |
| 3 | 顶部「开始学习」在卡片/测验显示时仍可点，会跳过当前词或当前小测 | `startOrNext()` 加守卫，未自评 / 未答题不允许跳 |
| 4 | 词库释义不足出不了小测时，`studiedSinceQuiz` 一直 ≥3，每翻一页都白查一次 | `tryQuiz()` 失败即清零 |
| 5 | 小测可能抽到刚学完的那个词 | 从候选池排除 `current.id` |
| 6 | `abandon v. 放弃`（单空格 + 词性）会把中文吞进单词字段 | `word` 含中文时回退取首个英文词，剩余并回释义 |
| 7 | CSV 带引号 `"abandon","放弃"` 释义带垃圾引号 | 先去双引号（保留单引号以兼容 `don't`），`clean()` 也去引号 |
| 8 | 表头 `No.` / `ID` 被当成词条 | 扩充表头黑名单 + 过滤纯大写短词 |
| 9 | `Unit 1 abandon 放弃` 这类行被误判成组名，整行词条被吞 | 收紧 `GROUP_EN` 后缀，只允许常见标题词 |
| 10 | 删过工作表后 `.xlsx` 里是 `sheet2.xml`，原代码只找 `sheet1.xml` 直接报错 | 自动枚举 `xl/worksheets/sheetN.xml` 取编号最小的一张 |
| 11 | PDF 用非贪婪正则抓流，二进制里出现 `endstream` 会截断；`/Filter` 只回看 400 字符会漏判 | 按 `/Length` 精确截字节，字典范围限定在最近 `obj` 与 `stream` 之间 |
| 12 | 二进制 docx/xlsx/pdf 理论上可能被误判成 JSON 备份 | 只有非 zip、非 PDF 的纯文本才按内容猜备份 |
| 13 | 组别按字典序排成 `Unit 1, Unit 10, Unit 2` | `groups()` 改自然排序，未分组置顶 |
| 14 | 组名输入复用了 220dp 多行预览框 | 新增 `dialog_input.xml` 单行布局 |
| 15 | 文档里数据库版本、布局数量与代码不一致 | 同步 `DESIGN.md` / `README.md` |

**仍未消除的边界**：扫描版 PDF、CID 字体中文释义、老 `.doc`/`.xls`、Excel 多工作表只读第一张——这些是格式本身的能力边界，已在界面与文档里明确提示，不是崩溃性缺陷。

---

## 7. v5 英音 / 美音双发音与双音标

### 7.1 效仿的交互（主流词典 / 背词 App 的做法）

卡片上并列 **UK / US 两个发音按钮**，两套音标并排显示，点哪一边就用哪边口音读 [citation:2][citation:3]；这也是 List 背单词「英式/美式发音+音标」、词藤「🔊 UK 🔊 US」、PitterPatter「美式和英式音标并排列出」的通用形态 [citation:1][citation:2][citation:3]。

### 7.2 落地的实现

| 项 | 做法 |
| --- | --- |
| 双发音按钮 | 学习卡片 `btnSpeakUk` / `btnSpeak`（美音），词条对话框再加 `btnDialogSpeakUk` / `btnDialogSpeakUs` / 默认朗读 |
| 口音选择 | `speak(text, accent)` 新增重载；旧 `speak(text)` 保留并按默认口音朗读。默认口音存 SharedPreferences，导入页可切换 |
| 精确口音 | `applyAccent()` 优先 `setVoice()` 选 `en_US` / `en_GB` 的本地 voice，失败退回 `setLanguage(Locale.US / Locale.UK)` |
| 可用性检查 | 依次过 `isLanguageAvailable()` → `voices` 里必须存在「地区匹配 + 不需联网 + 未标记 notInstalled」的 voice。**列在 voices 里不等于能用**，缺语音数据时系统会静默回退默认口音 [citation:4] |
| 离线保证 | 硬性过滤 `isNetworkConnectionRequired`，只选本地语音包；发音链路完全不联网，检查更新是独立的一条只读请求 [citation:7] |
| 缺包降级 | 点英音但没装英音包 → 自动用美音读并明确提示；两种都没有 → 引导「系统设置 → 语言和输入 → 文字转语音」安装（`ACTION_INSTALL_TTS_DATA`），返回后 `onResume` 重新探测 [citation:9] |
| 双音标解析 | 文档写 `UK /ˈɑːftə/ US /ˈæftər/`、`BrE[…] AmE[…]`、`英式/美式` 都会拆成 `phonetic_uk` / `phonetic_us`；只有一个音标则英美共用 |
| 音标编辑 | 词条对话框加「英式音标 UK」「美式音标 US」两个输入，留空沿用通用音标 |
| 音标显示 | `phoneticText()`（卡片双行）/ `phoneticShort()`（列表紧凑版）全站复用 |

### 7.3 兼容性（继续只增不减）

- 数据库 2 → 3 只 `ALTER TABLE` 加两列；`Word` / `Imported` 只在末尾追加带默认值字段，DEMO 六参数构造不变
- `exportJson` 多两个字段、`version` 仍为 1；旧备份缺字段按空值恢复，旧版 App 忽略多出的字段
- 旧 100 个 `R.id` 一个没改名 / 删除，v5 新增 9 个；`speak(text)` 旧签名保留
- 视觉 token、分组、导入、复习算法、零依赖全部不变；权限口径变化见第 11 节

### 7.4 必须说清的边界（这不是 bug，是离线应用的物理限制）

1. **音标不能凭空生成**（v5 状态，**v6 起已被第 8 节取代**）：v5 时包里没有词典数据，音标只能来自文档或手动填写，文档没写卡片就是空的。**v6 起改为随包内置离线开源词典自动补全**，但依然不是「凭空生成」——数据来自真实词典，查不到的词仍留空。详见第 8 节。
2. **音色由手机的 TTS 引擎决定**：App 只能请求 `en-US` / `en-GB` 的本地 voice，装没装、音质好不好、男女声，都是系统语音包的事。国产机若只预装了中文 TTS，需要去系统设置装英语语音数据；发音资源不联网下载，也不打包音频文件（那会让 APK 变成几十 MB 且涉及词典授权）。
3. **两个口音都可能没装时**：不会崩溃，会给出安装引导，但确实没法发声。
4. 扫描版 PDF、CID 字体中文释义、老 `.doc`/`.xls` 的限制不变。

### 7.5 本轮顺手修掉的一个解析 bug

等价回归里发现 `focus /ˈfəʊkəs/ 焦点` 会被拆成单词 `foc` + 把音标误判成 `US` 音标——原因是口音标签正则没加左边界，把 focus 末尾的 `us` 当成了 `US` 标签。已给标签分支加 `(?<![A-Za-z])`，回归用例覆盖 `focus` / `bonus` / 单词 `us` / 音标紧贴单词，全部通过。

---

## 8. v6 把「只有单词 + 汉语」的 Word 也补完整

### 8.1 这一轮解决的问题

前面几轮一直是挤牙膏：你提一个补一个，还反复强调「离线没有词典、音标只能自己填」。v6 一次性把底子做实：

| 之前 | 现在 |
| --- | --- |
| 文档没写音标 → 卡片空一行 | 导入时自动查**随包内置的离线开源音标词典**，补出 UK / US 两套 |
| 发音没声音只能干瞪眼 | 挂 `UtteranceProgressListener`，把系统错误码翻成人话（合成失败 / 输出被静音 / 语音包没装完 / 需联网等） |
| 语速固定 | 慢 0.7× / 正常 1.0× / 快 1.3× 三档，切口音后不会被引擎重置 |
| 不知道这台手机到底出不出声 | 导入页「试听一句英文」一键验证 |
| 只靠我这边临时写的静态检查 | `tools/verify.py` 随包交付，你在自己电脑上一键跑 |
| 上一轮说「最后一版还没打包」 | 本轮重新打包，交付与代码一致 |

### 8.2 数据来源与许可（重要）

`assets/ipa.tsv` 由两份开源数据合并生成，**不是凭空生成的**：

- 美音 `en_US.txt`：ipa-dict → 上游 cmudict-ipa（CMUdict），**MIT** [citation:1][citation:5]
- 英音 `en_UK.txt`：ipa-dict → 上游 **ipacards by @leoboiko，GPL-3.0** [citation:1]
- ipa-dict 仓库自身 LICENSE 写 MIT，但项目明确声明第三方数据集保留各自原始许可；因此**不能把英音那份当 MIT 用** [citation:1][citation:2][citation:4]

随包已附 `LICENSES/MIT.txt`、`LICENSES/GPL-3.0-only.txt` 全文与 `LICENSES/THIRD_PARTY_NOTICES.md`（含来源、修改清单、对应源码获取方式）。数据作为独立 assets 文件运行时加载，属于聚合使用，代码许可不受影响；**若以后要公开发布这个 APK，请保留这些声明**。

数据重建脚本 `tools/build_ipa.py` 是确定性、可重放的。

### 8.3 实现要点

- `IpaDictionary.kt`：不为 14 万条建 HashMap（省内存），整份 TSV 常驻一个 String，用 `"\n词\t"` 做 `indexOf` 精确命中行首；文件按词字典序排序，由 `verify.py` 强制校验排序（乱序会让查找失效）。**只在需要补音标时才加载**，平时学习页不占这份内存。
- 导入链路：`showImportPreview()` 先跑 `autofillPhonetics()`；v6 是「三个音标字段全空才补」，**v7 已改为逐字段只补空**（见第 9 节）。文档里写了 `UK /…/ US /…/` 的、用户手填的，一律不覆盖；词典没收录的（词组、生造词、极生僻词）留空并显式列出。
- 预览里直接显示「自动补全 N / M 个」，导入页「⑥ 音标自动补全」可一键关闭、也可给**已导入的老词**批量补一次。
- 词条对话框加「查内置词典补全音标」，仍只填空框、保存才生效。

### 8.4 端到端等价验证

用一份只有「单词 + 汉语」的 Word 内容做回归：8 条词识别出 8 条，自动补全 5 条，`take off`（词组）与自造词留空，文档里自带双音标的 `after` 未被覆盖。断言全部通过。

### 8.5 发音侧的补强

`installTtsListener()` 在 TTS 初始化成功后挂监听并统一设语速；`ttsErrorMessage()` 把 `ERROR_SYNTHESIS / ERROR_SERVICE / ERROR_OUTPUT / ERROR_NETWORK / ERROR_NOT_INSTALLED_YET` 等翻成可操作提示。没有引入「自动换引擎重试」这类未经真机验证的复杂逻辑——宁可少一个花活，也不埋一个不确定行为。

### 8.6 兼容性

- 数据库版本仍为 3，**表结构一列没加**；`autoIpa` / `speechRate` 只存 SharedPreferences
- `speak(text)` 旧签名保留；新增 `speak(text, accent)` 重载不变
- 旧 109 个 `R.id` 一个没改名 / 删除，v6 新增 8 个；备份 JSON 字段不变
- 分组、导入解析、复习算法、视觉 token、零依赖 / 离线全部不变（权限口径见第 11 节）

### 8.7 必须说清的边界

1. **音标是严式音素转写**：含 `ɫ` / `ɝ` / `ɐ` 等符号，跟课本宽式 IPA 不完全一样（如 `after` 美音标作 `/ˈæftɝ/`，`deliberate` 标作 `/dɪˈɫɪbɝˌeɪt/`）。这是开源数据的原始写法，本工程**不做自动改写**以免引入系统性错误，可在词条里手动改成习惯写法。
2. **覆盖不全**：v7 重建后合并 146,647 条，英美两套都有 43,560 条，仅美音 81,528 条，仅英音 21,559 条（v6 的 147,488 是「多读音只取一条 + 未归一」的旧口径）。一边查不到就只显示另一边，两边都没有就留空。9,894 个高频词实测命中率 94.9%，见第 9 节。
3. **发音仍取决于手机的 TTS 语音包**，与这份音标数据无关；音标补上了不代表声音一定响。
4. **APK 体积增加约 4 MB**（v7 的 `ipa.tsv` 未压缩 4.35 MB，另附 `ipa_stats.json`；打包时 aapt 会压缩 assets 文本）。
5. **许可**：英音数据 GPL-3.0，公开发布需随附声明与全文。

---

## 9. v7：先调研别人怎么做，再改代码

### 9.1 调研范围与可核实的结论

这一轮不再靠推演，实际查了产品形态、开源数据源许可证和 Android TTS 行为，报告全文见 `docs/research_competitors_ipa.md`。关键事实：

| 对象 | 可核实的做法 | 对本项目的启示 |
| --- | --- | --- |
| 欧路 / Eudic | 可装第三方 MDX 扩充词库、下载**离线发音库**，英/美音可切 | 音标数据与发音资源是**独立两层**，不互相冒充 |
| 词藤 WordVine | 聚合多源词典，14,201 词条 BrE/AmE **双音标 + 双预录音频**，首次下载后离线 | 双音标是数据问题，不是 TTS 副产品 |
| List 背单词 / PitterPatter | 英式/美式发音+音标；美英音标并排列出、点哪边用哪边读 | 卡片「UK / US 并列」是成熟交互，v5 已对齐 |
| Anki / AnkiDroid | 文本导入首字段重复时**默认更新同类型笔记其他字段**；`{{tts en_US:Front}}` 用设备 voice | 导入必须做**字段级策略**，不能「有数据就覆盖」；TTS 只负责发声 |
| Quizlet / 百词斩 / 墨墨 / 不背单词 | 支持文件/TXT 导入，但公开页均**未披露**自定义词表如何自动补音标 | 不要把「内置词书质量」当成「任意导入词都能补」 |
| 数据源 | ECDICT 约 76 万词但**许可不清晰**、单 phonetic；CMUdict 13.4 万条 BSD-2-Clause 但只有美音 ARPAbet；Kaikki/Wiktionary 压缩 2.8 GB、逐条授权混杂；ipa-dict 的 en_UK/en_US 分列 IPA、MIT（英音上游 GPL-3.0） | 选**许可可核实、体积可控、英美分列**的 ipa-dict，并把许可证全文与 SHA-256 随包 |

核心结论（报告第 2、5、6 节）：**显示层只读音标数据，发声层只用系统 TTS，永不把 IPA 字符串送进 `speak()`；导入是「预览 → 确认 → 只补空」的事务；未命中是「已知未知」，必须可见、可导出，不能填空占位符或规则猜读。**

### 9.2 对照后修掉的 5 个短板

| # | v6 的问题 | v7 改法 |
| --- | --- | --- |
| 1 | 只有「三个音标字段全空」才补：文档给了通用音标但缺 UK/US 两列时，白白丢掉双音标 | `autofillPhonetics()` 与 `WordDb.fillMissingPhonetics()` 都改成**逐字段只补空**；已有值一律不覆盖 |
| 2 | 英美拼写差异（color/colour、center/centre）查不到就直接留空 | `IpaDictionary` 加 36 组双向别名，**只用于查表**，不改用户词形；主词缺一列时用别名补该列 |
| 3 | 构建脚本把同形异读「只取第一条，其余丢弃」，等于用词典顺序冒充唯一正确读音 | 8,451 条多读音**全部候选并列保留**（如 `schedule` 美音 `/ˈskɛdʒuɫ/, /ˈskɛdʒʊɫ/`），不做词频硬选 |
| 4 | 预览只说「补全 N 个」，查不到哪些词不知道 | 预览列出**未命中词清单**（前 20 + 总数），点提示区可一键复制到剪贴板去别处查 |
| 5 | 数据说「来自 ipa-dict」但无法复核具体快照 | 生成脚本记录**上游 SHA-256** 与完整统计写入 `ipa_stats.json`，`verify.py` 强制校验行数、排序、归一一致性与多读音未被丢弃 |

### 9.3 实测数字（不是估算）

- 离线词表：**146,647 条**，4.35 MB 未压缩；英美双套 43,560 / 仅美音 81,528 / 仅英音 21,559；含多读音 8,451 条
- 用 `google-10000-english-no-swears`（9,894 词）跑真实查表：**9,387 命中 = 94.9%**，7,676 词（77.6%）英美双套齐全，1,711 词只有一套，**507 词（5.1%）未命中**（多为 `rss/faq/gmt/ny` 等缩写、专名）
- `tools/verify.py`：25 个 XML / 9 个 Kotlin / 117 个 id / 许可证齐全 / ipa.tsv 三列与排序 / 数据溯源 / **8 项行为回归全部通过**（含「文档自带双音标不被覆盖」「单音标保留并补两列」「别名补缺列」「词组与自造词留空」）

### 9.4 明确没有采纳的方案

- **联网词典 API**：能补齐任意词，但要 `INTERNET` 权限与后端（v7 当时的否决理由；v9 虽加了 INTERNET，但仍**未**把查词搬到线上，词表数据依旧随包离线）
- **完整 Kaikki / Wiktionary 数据**：2.8 GB 压缩、逐条授权混杂，不适合塞进 APK
- **ECDICT 的 phonetic 字段**：仓库无清晰许可，且只有一个混合音标，不能拆成英美两列
- **规则 G2P / 神经网络猜未命中词**：`colonel`、`yacht`、`read` 这类必然出错，宁可留空
- **预渲染真人双发音音频**：3 万词英美两套常达数百 MB～GB，且录音权利链复杂；设备 TTS 已能读任意词形

### 9.5 仍然存在的边界

1. **英音数据上游是 GPL-3.0**：`LICENSES/` 已附全文、声明与重建方式；公开发布 APK 时必须保留。这是许可事实，不是可以「优化掉」的 bug。
2. **严式 IPA 与课本宽式写法不同**（`ɫ` / `ɝ` / `ɐ`），保留原样不改，可在词条里手动订正。
3. 5.1% 高频词、词组、自造词查不到就留空 —— 这是可信产品的「已知未知」，不是缺陷。
4. 发音是否真出英音/美音仍取决于手机装的 TTS 语音包，**静态检查无法替代真机试听**。

---

## 10. v8：从微信 / QQ 导入文件

### 10.1 为什么是「接住分享」而不是「读微信目录」

Android 沙箱下，微信收到的文件在 `com.tencent.mm` 私有目录里，第三方应用既没有权限也不该去读。查过微信的实际行为后确认是两条系统级入口 [citation:3][citation:6]：

| 用户在微信里的动作 | 系统发出的 Intent | 文件在哪 |
| --- | --- | --- |
| 点文件 →「…」→「用其他应用打开」 | `ACTION_VIEW` + `data`（`content://` Uri）+ `FLAG_GRANT_READ_URI_PERMISSION` | `intent.data` |
| 长按文件 →「分享 / 发送给朋友」→ 更多应用 | `ACTION_SEND` | `EXTRA_STREAM`（Uri） |
| 多选文件分享 | `ACTION_SEND_MULTIPLE` | `EXTRA_STREAM`（Uri 列表） |
| 部分版本 / 文件管理器 | 同上 | `clipData.itemAt(i).uri` |

拿到的是 `content://` 临时授权 Uri（Android 7+ 禁止跨应用暴露 `file://`），必须走 `ContentResolver.openInputStream`，**不能拼文件路径**，也不需要任何存储权限 [citation:6][citation:11]。所以本应用只注册接收端，不扫描任何目录。

### 10.2 落地清单

- `AndroidManifest.xml`：加 3 组 `intent-filter`。VIEW 限定 `content` / `file` scheme 与具体 MIME（含微信常给的 `application/octet-stream`、`text/*`），避免网页链接也把本应用列进去；SEND / SEND_MULTIPLE 用 `*/*` 兜住各版本微信不设或乱设 MIME 的情况，顺带让「分享一段词表文字」也能进来 [citation:7]。`launchMode="singleTask"` 避免微信唤起时堆多个 Activity 实例 [citation:3]。
- `MainActivity.handleIncomingIntent()`：同时读 `data` / `EXTRA_STREAM` / `clipData`，按 Uri 字符串去重；冷启动走 `onCreate`、已运行走 `onNewIntent`；`lastIncomingKey` 防止同一 Intent 重发时弹两遍预览；`ACTION_MAIN` 直接跳过。
- 解析链路复用：抽出 `readIncoming()` 给单文件与多文件共用，`importFromUri(uri)` 旧行为不变（只加带默认值的参数），新增 `importFromUris()` 把多选文件合并成**一次**预览；备份 JSON 仍单独恢复。
- 微信的 FileProvider 常不返回 `DISPLAY_NAME`：`displayName()` 加异常保护，`guessName()` 用 MIME 补扩展名。格式判断本来靠魔数（`%PDF-` / `PK` / zip 内条目），文件名只用于提示，拿不到名字也能解析 [citation:6]。
- 误发图片 / 音频时不再把二进制当 UTF-8 文本生成垃圾词条：`looksLikeText()` 用替换字符与控制字符比例拦截，给出明确提示。
- 纯文本 `EXTRA_TEXT` 分享直接当「粘贴导入」；导入页新增「⑦ 从微信 / QQ 导入」说明卡与步骤弹窗。

### 10.3 接口与兼容性

- 数据库版本仍为 3，表结构与备份 JSON 字段一列未加
- `importFromUri(uri)` 旧签名保留；`showImportPreview` / `db.importWords` / `DocxParser.parseBytes` 全部未改
- 视觉 token 不变，`R.id` 只增（`btnWechatHelp` / `btnPickDoc2`），旧 117 个一个没改名
- **v1.1 为零权限**；v1.2 起按用户确认新增唯一的 `INTERNET`（只给检查更新）。仍没有 `READ_EXTERNAL_STORAGE`、没有 FileProvider、没有第三方库
- `exported="true"` 是原来就有的（LAUNCHER 必需）；新增过滤器只接收文件 Uri，不暴露 Service / Provider / 数据写入接口
- 版本：v1.1（versionCode 2）→ **v1.2（versionCode 3）**；当时 CI 默认 Release tag `v1.1.0` → **`v1.2.0`**（工作流现已移除）

### 10.4 诚实边界

1. 微信各版本给出的 MIME 不统一，**不能保证「用其他应用打开」列表里一定出现「单词酷」**。找不到时先在微信里「保存到手机」，再用本应用「选择文档并导入」；导入页 ⑦ 写了这套退路。
2. 分享的是图片、语音、链接或已被微信清理的文件时，会给出具体失败原因，不会静默失败。
3. **未做真机点按验证**：Intent 匹配、`onNewIntent`、微信实际弹出的应用列表都属于系统行为，只能在装了微信的手机上确认。

---

## 11. v9：应用内检查更新

### 11.1 口径变化（必须写清楚）

前几版一路坚持「零权限、全程离线」。用户明确选择「应用内检查更新」并**确认允许加 `INTERNET` 权限**后，v1.2 才加。这是**唯一**一次破例，且收在最窄的范围里：

| 维度 | v1.0 / v1.1 | v1.2 |
| --- | --- | --- |
| 权限 | 0 个 | **仅 `INTERNET` 1 个** |
| 网络请求 | 无 | 仅 `GET https://api.github.com/repos/dotfaker-sys/WordCool/releases/latest` |
| 触发条件 | — | 用户点「检查更新」，或自己打开「启动时自动检查」 |
| 上传内容 | — | **无**。不传词库、账号、token、设备信息；只读公开 JSON |
| 自动下载 / 安装 | — | **没有**。发现新版只弹窗，点「去下载」才用系统浏览器打开页面 |
| 其他功能 | 离线 | **仍全部离线**：词库、导入、IPA 词典、TTS 发音、复习、备份 |

`tools/verify.py` 现在用白名单强制校验：Manifest 里**只能**出现 `android.permission.INTERNET`，多一个就报错。

### 11.2 实现

- 新增 `UpdateChecker.kt`（第 10 个 Kotlin 文件），系统自带 `HttpURLConnection` + `org.json`，**零第三方依赖**；设 `User-Agent`（GitHub API 不设会 403）、8 秒连接 / 读取超时、`finally { disconnect() }`
- 后台 `Thread` 发请求，`runOnUiThread` 回主线程弹窗；弹前判断 `isFinishing / isDestroyed`，避免 Activity 已销毁时崩
- `compare(local, remote)` 把 `1.2` 与 `v1.2.0`、`v1.2.1`、`v1.2.0-beta` 归一成数字三段逐段比较（**1.10 必须大于 1.9**），返回 >0 才判为有新版本
- 版本号读 `PackageManager.getPackageInfo().versionName`，不写死在布局里，以后升级不会忘记改；`buildconfig=false` 时也不依赖 `BuildConfig`
- 失败分级：超时 / 无网络 / HTTP 403 限流 / JSON 格式不对，都给**具体原因**和「打开下载页」退路；静默自动检查失败时**不弹窗**，不打扰
- 「启动时自动检查」开关存 `SharedPreferences`，**默认关**；开启后只有真有新版才弹，已是最新时静默。从微信分享文件进来时不触发自动检查，避免打断导入预览
- 入口在「统计」页底部：`tvVersion` / `btnCheckUpdate` / `btnAutoUpdate`，沿用既有卡片与按钮样式，视觉 token 一行未改

### 11.3 接口与兼容性

- 数据库仍 v3、备份 JSON 字段未动；`R.id` 只增 3 个，旧 119 个一个没改名
- `MainActivity` 只加 `isIncomingIntent()` 与一行 `UpdateChecker.autoCheck(this)`；`StatsView` 只加 init 绑定，原有统计逻辑未改
- `AndroidManifest.xml` 只加 1 条 `uses-permission`，v8 的三组文件接收过滤器、MAIN / LAUNCHER、`exported` 全部不变
- 当时 CI 默认 tag 升 `v1.2.0`，发布说明同步写明「唯一联网点」与「不自动下载安装」（该工作流现已移除）
- **零第三方依赖不变**：`app/build.gradle` 的 `dependencies {}` 仍为空

### 11.4 诚实边界

1. **GitHub 未登录接口每小时限 60 次 / IP**，触发限流会明确提示并可手动打开下载页；这是公开接口限制，不是应用崩溃。
2. 检查更新**只比对版本号、不校验 APK 签名或完整性**；下载与安装由用户在浏览器里自行完成。
3. 中国大陆网络环境下 `api.github.com` 可能连不上，此时给「连不上 GitHub」并保留手动打开下载页的退路；**不影响任何背单词功能**。
4. **仍未做真机点按验证**：实际请求、弹窗、浏览器跳转属于系统行为，需在装了应用的手机上点一次确认。
5. 不想要联网就**不要打开「启动时自动检查」、也不要点「检查更新」**；这两个都不触发，应用行为与 v1.1 完全一致。

---

## 12. v10：拼写测试

### 12.1 为什么加这一步

认词卡片测的是「看到英文能不能想起中文」，4 选 1 也只测再认；这两条链路都允许「看着眼熟就蒙对」。真正要会用一个词，至少还要能**从中文反推英文拼写**。v10 不改原有认词 / 小测流程，只在学习页加一条独立的拼写会话：**看释义 → 输入英文 → 立即判分**。

### 12.2 行为

- **取词范围**：当前组别里 `state>0` 且 `next_review<=today` 的词，且释义非空；没有可拼写的词时明确提示，不静默失败
- **判分**：拼对调用 `recordGrade(id, 2)`，拼错 / 看答案调用 `recordGrade(id, 0)`。复用原有间隔复习与打卡，不新开一套「拼写等级」
- **宽松规则**：只忽略大小写、首尾空格、连续空格和弯撇号（`’`→`'`）；多写 / 少写字母、把 `take off` 写成 `takeoff` 都判错
- **三种求助**：
  - 「提示首字母」：显示首字母 + 其余字母下划线，空格 / 连字符保留；同一条答案只提示一次
  - 「听发音（有提示）」：调用系统 TTS 读这个词；会提示本轮用过提示
  - 「不会，看答案」：直接显示正确拼写，并按「不认识」记一次
- **节奏**：作答后停在当前卡，由用户点「下一个」；全部过完显示「拼对 X / Y」与正确率，可「再来一轮」（只包含今天仍到期的错词）或「返回学习」
- **不打断**：拼写会话有独立队列；开始拼写会清空认词缓存队列，避免学完拼写后又被旧队列带回已拼过的词。正在看认词答案 / 未作答小测时，点拼写按钮会先提示完成当前卡

### 12.3 接口与兼容性

- 数据库仍为 v3，`words` / `checkin` 表结构、备份 JSON 字段**一列未加**；`recordGrade` 仍是唯一判分入口
- 新增 13 个 `R.id`（`btnSpellStart` / `cardSpell` / `tvSpellLabel` / `tvSpellMeta` / `tvSpellMeaning` / `tvSpellHint` / `etSpell` / `btnSpellSpeak` / `btnSpellHint` / `btnSpellCheck` / `btnSpellGiveUp` / `tvSpellResult` / `btnSpellNext`），旧 id 一个没改名
- `LearnView` 新增内部状态 `SPELL=3`，没有改 `MainActivity` / `WordDb` 的公开方法签名
- 权限仍只有 `INTERNET` 一个；不新增存储、麦克风、通知等权限，`tools/verify.py` 的白名单继续强制这一条
- 版本：v1.2（versionCode 3）→ **v1.3（versionCode 4）**；v1.4 起再升 versionCode 5 并内置考研词库（见第 13 节）。GitHub 上现有 Release 仍是 v1.2.0，v1.4 需自行编译

### 12.4 诚实边界

1. 「听发音」和「提示首字母」都会泄露答案，只是降低难度；它们的正确率不能当作独立拼写能力。
2. 一个词拼对一次就按「认识」推进复习间隔，和认词卡共享进度；**没有单独的拼写熟练度**，也不区分「会拼但不会用」。
3. 词组仍按原样比对空格；`take off` / `takeoff`、连字符写法、英美拼写差异（`color` / `colour`）都按不同答案处理，不会自动判等。
4. 释义为空或只有词性的词无法用来提示中文，已从拼写队列里过滤；这类词要先去词库补释义。
5. **仍未做全量真机回归**：v1.3 已在 Android 37 模拟器（API 37、1080×2400）上真实安装并点按验证「正确 / 拼错 / 提示 / 看答案 / 完成 / 再来一轮」主流程；系统 TTS 真实语音包、不同厂商输入法与键盘、微信分享入口仍需在真机确认。

---

## 13. v1.4：内置考研词库

### 13.1 数据来源与整理

用户提供了 `Unit1`–`Unit5` 共 46 个 Word 文件：每天一份单词表、一份测试卷。处理时只取单词表，跳过文件名含「测试卷 / 测试版」的测试文件；Unit3–4 是旧版 `.doc`，先在电脑上用 Word 转成 `.docx` 再抽取表格，之后随包的词表与 `.doc` 无关。

- 原始表格行：722 行；去重后 **711 个唯一词**
- 分组：Unit 1 Day 1 … Unit 5 Day 3，共 **23 组**（源文件只到 Unit 5 Day 3）
- 同形词：11 个词在源表里按不同义项出现两次，合并成一条并保留全部义项；不合并会被 SQLite 的 `word UNIQUE` 吞掉
- 拼写变体：`visualize/-ise`、`analyze/yse` 等 10 条拆成标准词形，英式拼写写进 `note`
- `Pre-` 是前缀不是单词，改成 `prefix`；`hold-up` 等带连字符的词保留原形
- 音标仍由 `IpaDictionary` 在预览阶段补；711 个词中 707 个命中，`preposition` / `hold-up` / `inspirit` / `dispirit` 四个留空，不编造

### 13.2 落点与接口

- 新增 `app/src/main/assets/kaoyan.tsv`：`word<TAB>meaning<TAB>group<TAB>note`，`#` 开头为注释；711 行正文 + 2 行来源说明
- 导入页新增「载入考研词库（Unit 1–5 · 711 词）」按钮；点击先弹确认，再调用 `MainActivity.loadKaoyanPack()`
- `loadKaoyanPack()` 只读取 assets、拼成 `Imported` 列表，然后复用 `showImportPreview()`；预览里仍可「统一改组」、看未命中词、确认后由 `db.importWords()` 入库
- 不新增数据库字段、不新增权限、不新增第三方依赖；`R.id` 新增 1 个（`btnLoadKaoyan`），v1.4 当时 136 个，v1.5 再加 `tabSettings` 后为 137 个
- 版本：v1.3（versionCode 4）→ **v1.4（versionCode 5）**

### 13.3 诚实边界

1. 词表来自用户提供的文件，**没有独立核对是不是某一本考研书的官方词表**；公开分发前请先确认词表版权与来源。
2. 同形词合并后，一个词只保留一条记录，无法在词库里分别按「count 动词」「count 名词」两个义项独立复习；合并是当前 `word UNIQUE` 结构下的折中。
3. 源文件只到 Unit 5 Day 3，所以没有 Unit 5 Day 4–5；组数 23 是如实反映，不是漏导。
4. 4 个未命中音标的词在预览里明确列出，可以手动补；不编造音标。
5. 仍未做真机点按验证：v1.4 在 Android 37 模拟器上验证了「载入预览 → 导入 711 → 词库显示 23 组」，真实手机的文件选择、TTS 与不同 ROM 仍需确认。

---

## 14. v1.5：独立设置页

### 14.1 页面结构

底部从 4 个 tab 变成 5 个：`词库 / 学习 / 统计 / 导入 / 设置`。`MainActivity` 的 `pages` 与 `tabs` 仍一一对应，导入页仍是索引 3，所以 v8 微信分享进来时 `switchTo(3)` 不需要改；设置页是索引 4。

| 页面 | 现在只放什么 | 搬走了什么 |
| --- | --- | --- |
| 导入 | ① 文档导入、② 粘贴导入、③ 划组管理、④ 内置考研 / 示例词库、⑤ 微信 / QQ 导入 | 每日解锁数、JSON 备份、清空、发音、音标、版本更新 |
| 统计 | 四个数字卡、解锁进度、正确率、连续打卡、近 7 天柱状图 | 重置学习进度、版本与更新 |
| 设置 | 学习、数据管理、发音、音标、版本与更新、关于 | —（新页面） |

### 14.2 实现与接口

- 新增 `res/layout/page_settings.xml` 与第 11 个 Kotlin 文件 `SettingsView.kt`
- 新增 `R.id.tabSettings`；设置项沿用原 id（`etDaily` / `btnExport` / `btnReset` / `btnClear` / `tvTtsStatus` / `btnAccentUs` / `btnAccentUk` / `btnRateSlow` / `btnRateNormal` / `btnRateFast` / `btnSpeakSample` / `btnTtsSettings` / `tvIpaStatus` / `btnAutoIpa` / `btnFillIpa` / `tvVersion` / `btnCheckUpdate` / `btnAutoUpdate`），一个没改名
- `ImportView` 删掉已搬走的 findViewById 与监听，避免在精简后的布局上取到 null；`StatsView` 删掉版本与重置逻辑
- 数据库仍 v3、备份 JSON 字段不变、权限仍只有 `INTERNET`；`UpdateChecker.autoCheck(this)` 仍在 `MainActivity.onCreate` 的正常启动分支里
- 版本：v1.4（versionCode 5）→ **v1.5（versionCode 6）**；v1.5 当时 10 个布局 / 137 个 id / 11 个 Kotlin 文件，v1.6 再加 `btnStudyOrder` 后为 138 个 id

### 14.3 诚实边界

1. 5 个 tab 在 1080px 宽、420dpi 的模拟器上两个字标签能放下；更窄的手机或超大系统字体需要真机确认，必要时把 tab 字号降到 12sp 或改成图标。
2. 设置页仍是单个 `ScrollView`，内容比导入页长，但分类更清楚；没有做二级设置页。
3. 设置只是搬家：每日目标、备份、清空、发音、音标与更新的行为与旧版一致，旧词库和偏好不需要迁移。
4. 仍未做真机点按验证；v1.5 只在 Android 37 模拟器上验证了 5 tab 切换、设置页滚动与导入页精简。

---

## 15. v1.6：多选学习范围与顺序 / 打乱

### 15.1 范围

- 学习页顶部原来的「组别」按钮改成「范围」：弹窗里用 checkbox 多选 `Unit 1 Day 1` … `Unit 5 Day 3`，另有「清空 / 全选 / 确定」
- 语义：`null` = 全部词组；空选也归一为全部（避免进入一个永远没词的死范围）；选了部分组则用 `Set<String>` 保存，空串代表「未分组」
- 范围存 SharedPreferences `study_groups`，重启应用后仍生效；加载时会过滤掉已改名 / 已删除的组，全部失效则回到「全部词组」
- 「范围：全部词组 / Unit 1 Day 2 / 2 个组」直接显示在按钮上
- `WordDb` 新增：
  - `dueWordsForGroups(limit, groups)`：今日复习
  - `unlockNewForGroups(limit, groups)`：解锁新词
  - `quizWordsForGroups(groups)`：小测词池
  - `appendGroupsWhere()`：生成 `group_name IN (?,?)`；含未分组时补 `group_name IS NULL OR group_name=''`
- 认词队列、拼写队列、解锁、小测全部使用同一个范围；切换范围会清空当前队列并回到空闲态，避免旧队列混入新范围

### 15.2 顺序

- 学习页新增「顺序：顺序 / 打乱」按钮，偏好存 `WordDb.shuffleOrder`（SharedPreferences `shuffle_order`，默认关）
- 每次 `startOrNext()` 重新取队列、或 `startSpelling()` 新建拼写队列时，按偏好 `shuffle()` 一次；已经开始的这一轮不会被中途打乱
- 打乱只改变本轮取出顺序，不改变 `next_review`、连对、正确率和任何复习间隔

### 15.3 接口与兼容性

- 数据库仍 v3、备份 JSON 字段不变、权限仍只有 `INTERNET`、第三方依赖仍为空
- 新增 `R.id.btnStudyOrder`；`R.id` 从 137 增至 138，旧 id 一个没改名
- 旧 `dueWords` / `unlockNew` / `quizWords` 保留，新方法只增不改；词库页仍用单选 `showGroupPicker`，只有学习页用多选 `showMultiGroupPicker`
- 版本：v1.5（versionCode 6）→ **v1.6（versionCode 7）**

### 15.4 诚实边界

1. 「打乱」是每轮一次 `shuffle()`，不是严格均衡抽样；同一轮内每个词仍只出现一次，但不同轮可能连续抽到同一批。
2. 范围多选只在学习页生效；词库页的组筛选仍是单选，这是刻意的，避免两个页面各有一套多选状态。
3. 没有做「排除某组」「按来源 / 词频排序」；当前只有按到期日 / id 顺序与随机打乱两种。
4. 选中很多组时，范围弹窗是一个可滚动列表；组特别多时仍需真机检查滚动与按钮位置。
5. 仍未做真机点按验证；v1.6 在 Android 37 模拟器上验证了清空 → 勾选 Unit 1 Day 1 + Unit 1 Day 2 → 范围显示「2 个组」→ 待学数变为该范围 → 顺序切到「打乱」→ 开始学习。

---

## 16. v1.7：词库折叠头、行内发音与主按钮换位

### 16.1 折叠头

- 新增 `res/layout/view_library_header.xml`，把搜索框、状态 chips、组别筛选、计数、解锁按钮和进度条整体搬进去
- `LibraryView` 在设置 adapter 之前调用 `list.addHeaderView(header, null, false)`；这些控件不再是页面顶部的固定块，而是列表第 0 项，往下滑会随列表一起滚出屏幕
- 因为 header 占第 0 位，列表点击 / 长按都要用 `pos - list.headerViewsCount` 换算成 `items` 下标，避免点「第一个词」打开「第二个词」的旧 bug
- 页面 `page_library.xml` 只剩 `ListView` 与空态 `TextView`，顶部不再有固定筛选区

### 16.2 行内发音

- `item_word.xml` 单词行改成：单词（占剩余宽度、超长省略）→ 音标 → 「发音」按钮
- `LibraryView.getView()` 给按钮设置 `a.speak(w.word)`，使用设置页里的默认口音；点按钮只朗读，不打开词条编辑框
- 按钮是 28dp 高的描边小按钮，不引入图标资源，延续「纯文字、无 emoji」的设计约束

### 16.3 主按钮换位

- 学习页顶部按钮顺序从「解锁今日新词（朱红主按钮）→ 开始学习（浅色次按钮）」改为「开始学习（朱红主按钮）→ 解锁今日新词（浅色次按钮）」
- id 与监听逻辑完全不变，只交换 XML 顺序与背景；`btnStart` 仍是开始 / 下一个，`btnUnlockNew` 仍是解锁

### 16.4 兼容性与边界

- 数据库仍 v3、备份 JSON 不变、权限仍只有 `INTERNET`；新增文件只涉及布局
- 当前 11 个布局 / 139 个 id / 11 个 Kotlin 文件；版本 v1.6（versionCode 7）→ **v1.7（versionCode 8）**
- 折叠的是词库页自己的顶部块；`activity_main.xml` 里的「我的单词酷 / 已解锁 N / M」是五个页面共用的标题栏，仍然固定
- 行内发音只有一个默认口音按钮，没有每行 UK / US 两按钮；要听另一种口音去设置页切默认口音，或进词条详情
- 超长单词会省略显示，完整拼写在词条详情里；折叠头没有做成吸顶，滑走后要回到顶部才能再搜索 / 筛选
- 仍未做真机点按验证；v1.7 在 Android 37 模拟器上验证了折叠头滚动、行内按钮渲染与学习页按钮顺序

---

## 17. v1.8：词库 Unit → Day 分组折叠

### 17.1 行模型

词库列表不再只有单词行，而是三种类型：

| 类型 | 布局 | 说明 |
| --- | --- | --- |
| `Row.UnitHeader` | `item_library_unit.xml` | Unit 大标题，显示总词数 / 已解锁数，左侧朱红竖线 |
| `Row.DayHeader` | `item_library_day.xml` | Day 小标题，缩进在 Unit 下，显示词数 / 已解锁数 |
| `Row.WordRow` | `item_word.xml` | 原单词行，保留音标、行内发音、状态、复习时间 |

- 新布局只增不改；`item_word.xml` 仍是单词行，v1.7 的「发音」按钮保留
- 分组来自已有 `group_name`：兼容 `Unit 1 Day 2` / `Unit 1` / `Day 1` / `未分组` / 自定义组名
- 自定义组名没有 Unit/Day 数字时归到「未分单元」或按原名作为 Unit 标题；不丢词
- 排序：Unit 按数字升序，Day 按数字升序，未分组 / 未分单元排最后

### 17.2 折叠与搜索

- 点 Unit 标题折叠整个 Unit；点 Day 标题只折叠该 Day；箭头 `▾ / ▸` 表示状态
- 折叠状态只存在当前 `LibraryView` 实例里，切页 / 重启后恢复展开；不写数据库
- 搜索框有内容时强制全部展开，避免搜索结果被折叠状态藏住；清空搜索后恢复之前折叠状态
- 折叠时用 `firstVisiblePosition + getChildAt(0).top` 保持滚动位置，避免跳回顶部
- 列表点击 / 长按仍用 `pos - headerViewsCount` 判断类型；Unit/Day 行点击折叠，单词行长按删除

### 17.3 兼容性

- 数据库仍 v3、备份 JSON 不变、权限仍只有 `INTERNET`；`getWords` / `Word` 字段一行没改
- 当前 13 个布局 / 145 个 id / 11 个 Kotlin 文件；版本 v1.7（versionCode 8）→ **v1.8（versionCode 9）**
- `activity_main.xml` 顶部标题从「我的单词酷」改为「单词酷」，五个页面共用

### 17.4 诚实边界

1. 折叠只看组名里的 `Unit N` / `Day N`；组名写得完全不规则时只会按原名成组，不会猜成 Unit/Day。
2. 折叠状态不持久化；退出词库页再回来会恢复展开，这是为了避免用户忘记自己折叠过什么。
3. Unit/Day 标题目前不能直接改名；改名仍走「导入 → 划组管理」。
4. 搜索时全部展开，词多时列表会变长；这是为了保证搜索结果可见。
5. 仍未做真机点按验证；v1.8 在 Android 37 模拟器上验证了 Unit/Day 标题渲染、折叠后行数变化与折叠头滚动。

---

## 18. v1.9：词库只读、英音单列与编辑入口

### 18.1 词库行做减法

- 删除 `item_word.xml` 右侧的 `tvState` 状态标签；`LibraryView` 不再给 state=0 的词显示「待解锁」，`tvReview` 对未解锁词留空
- 「未解锁 / 学习中 / 已掌握」仍保留在顶部筛选 chip 和顶部「已解锁 N / M」里，只是不再逐行重复
- `Ui.kt` 的 `phoneticShort()` 改为：有英音就只显示英音，没有英音才回退美音 / 通用音标；不再拼「英美双音标」
- 单词从「单词 + 音标 + 按钮」挤在一行改成：
  - 第一行：完整单词（占剩余宽度、可换行、不省略）+ 发音按钮
  - 第二行：英音音标
  - 第三行起：释义 / 学习数据
- `tvWord` 不再设置 `ellipsize` / `maxLines`，长词如 `education`、`conductor` 会完整显示

### 18.2 编辑移到导入页

- 新增导入页「⑤ 编辑单词」卡片和 `btnEditWord`
- 新增 `res/layout/dialog_pick_word.xml`：搜索框 + 词表 + 空态
- `MainActivity.showWordPicker()` 读取当前词库，按单词 / 释义实时过滤，点某一项后关闭选择框并打开原有的 `showWordDialog()`
- `LibraryView` 删掉 `showWordDialog` 点击与长按删除；词库页点击单词不再打开编辑，长按也不再删除；行内「发音」按钮保留
- 编辑框里仍保留「删除」按钮和保存逻辑，行为与旧版一致

### 18.3 兼容性

- 数据库仍 v3、备份 JSON 不变、权限仍只有 `INTERNET`；没有改 `Word` 字段或复习逻辑
- 当前 14 个布局 / 148 个 id / 11 个 Kotlin 文件；版本 v1.8（versionCode 9）→ **v1.9（versionCode 10）**
- `tvState` 从 `item_word.xml` 删除后，`stateName()` / `stateColor()` 不再被词库列表使用，但保留给其他界面与旧调用

### 18.4 诚实边界

1. 列表不再显示锁定状态，想筛出未解锁词仍可用顶部「未解锁」chip；解锁本身没有被删除。
2. 只显示英音是列表的取舍；词条详情仍保留 UK / US 双音标与双试听。
3. 编辑选择框一次性读取当前词库并在内存里过滤；711 词没有压力，几万词时首次打开会慢一些。
4. 长词换行会增加行高，但不会再截断；极长词组仍受词条 60 字符上限约束。
5. 仍未做真机点按验证；v1.9 在 Android 37 模拟器上验证了列表无状态标签、英音单列、长词完整显示与导入页编辑入口。

---

## 19. v1.10：词库组别多选

### 19.1 实现

- `LibraryView.groupFilter` 从 `String?` 改为 `Set<String>?`：`null` = 全部，空串仍代表「未分组」
- 「组别」按钮改用 `MainActivity.showMultiGroupPicker()`，标题为「选择组别（可多选，不选=全部）」，复用「清空 / 全选 / 确定」
- `WordDb` 新增 `getWordsForGroups(stateFilter, keyword, groups)`，与学习页的 `dueWordsForGroups` 共用 `appendGroupsWhere()`
- 「解锁新词」改用 `unlockNewForGroups(dailyGoal, groupFilter)`，只解锁当前所选组
- 按钮文案：`组别：全部词组` / `组别：Unit 1 Day 2` / `组别：2 个组`
- `showMultiGroupPicker()` 增加可选 `title` 参数，学习页默认仍是「选择学习范围（可多选，不选=全部）」，词库页传自己的标题

### 19.2 兼容性

- 数据库仍 v3、备份 JSON 不变、权限仍只有 `INTERNET`
- 当前 14 个布局 / 148 个 id / 11 个 Kotlin 文件；版本 v1.9（versionCode 10）→ **v1.10（versionCode 11）**
- 词库页的多选范围只保存在当前页面实例里，不写 `studyGroups` 偏好；学习页的「范围」仍持久化

### 19.3 诚实边界

1. 词库筛选范围重启后回到「全部词组」；这是为了不把浏览筛选误当成学习计划。
2. 多选范围只影响列表与「解锁新词」；学习页有自己的范围，两者互不覆盖。
3. 选空等于全部，不会出现「空结果」死状态。
4. 组别弹窗列的是真实组名，未分组的空串显示为「未分组」。
5. 仍未做真机点按验证；v1.10 在 Android 37 模拟器上验证了清空 → 勾选 Unit 1 Day 1 + Unit 1 Day 2 → 组别显示「2 个组」、当前显示 78 词。

---

## 20. v1.11：内置离线语音

### 20.1 为什么换引擎

系统 TTS 语音包属于 Google / 手机厂商，不能合法打进第三方 APK；`ACTION_INSTALL_TTS_DATA` 在多数新系统上也不提供真正的下载入口。要做到「安装完直接能读」，只能内置开源引擎 + 模型。

- 引擎：**sherpa-onnx 2.0.2 AAR**（Apache-2.0）
- 模型：**Piper `vits-piper-en_US-amy-low-int8`**，16 kHz，单说话人，约 18.6 MB
- 发音词典：`lexicon-en-us.txt`（约 6.3 MB，来自 sherpa-onnx tts-models release）
- 不再需要模型包里的 `espeak-ng-data`（已删除，省约 11 MB）
- 全部在 CPU 推理，不联网、不申请新权限

### 20.2 实现

- `app/libs/sherpa-onnx-2.0.2.aar` 通过 `flatDir` 依赖引入
- `app/src/main/assets/tts/amy/` 放 `en_US-amy-low.onnx`、`tokens.txt`、`lexicon-en-us.txt`
- 新增 `BundledTts.kt`（第 12 个 Kotlin 文件）：
  - `init()` 在后台线程创建 `OfflineTts(assetManager, config)`
  - `speak()` 在单线程 executor 里 `generate()`，再用 `AudioTrack`（`ENCODING_PCM_FLOAT`、16 kHz、单声道、媒体流）播放
  - 状态：加载中 / 已就绪 / 失败 + 错误原因
- `MainActivity.speak()` 顺序：
  1. 检查媒体音量（0 / <30% 给提示）
  2. `BundledTts.speak()` 成功就直接返回
  3. 内置未就绪时才走原系统 TTS 兜底
- 内置只有美音：UK 按钮也会用 Amy，并在首次切换时提示；系统 TTS 仍保留给内置失败 / 不支持的设备

### 20.3 体积与 ABI

- 正式包：`arm64-v8a` + `armeabi-v7a`，约 **73 MB**（含 18.6 MB 模型、6.3 MB 词典、约 31 MB arm64 + 22 MB armeabi 原生库）
- 调试包：额外带 `x86_64`（模拟器），约 **111 MB**
- `app/build.gradle` 的 `ndk.abiFilters` 与 `packaging.jniLibs.excludes` 共同决定打包 ABI；AAR 自带四个 ABI，不能只靠 AAR 默认
- 首次启动后台加载模型约 1–3 秒；加载完成前点发音会走系统 TTS 兜底

### 20.4 许可

- sherpa-onnx：Apache-2.0，全文见 `LICENSES/Apache-2.0.txt`
- Piper 模型：`MODEL_CARD` 注明数据集来自 MycroftAI/mimic3-voices，上游许可 **CC-BY-SA-4.0**；声明见 `LICENSES/SHERPA_ONNX_AND_PIPER_NOTICE.md`
- `lexicon-en-us.txt` 来源与生成方式见同一声明

### 20.5 诚实边界

1. 只内置美音 Amy；UK 按钮不是英音，只是同一美音，第一次会提示。
2. 模型是 low/int8，音质不如 Google 系统语音包，偶尔会有机器味；胜在安装即用、离线、可控。
3. 正式包不支持 x86 / x86_64 真机（只支持 arm64 / armeabi-v7a）；模拟器用 debug 包。
4. APK 73 MB 是明确的体积代价，无法同时做到「2.5 MB」和「自带语音」。
5. 仍未做真机扬声器试听；模拟器只验证了模型加载与 AudioTrack 生成（16 kHz / 12800 samples），实际音质需真机确认。

---

## 21. 验收与诚实边界

- 已做：`python3 tools/verify.py` 一键静态自检通过（34 个 XML 合法性与重复属性、148 个 id / 资源引用一致性、12 个 Kotlin 精细括号扫描、emoji 残留、`ipa.tsv` 三列格式与排序、许可证文件齐全、上游 SHA-256 与 `ipa_stats.json` 行数一致性、**Manifest 三组接收过滤器与「仅 INTERNET 一个权限」白名单校验**、v10 拼写判分等价回归、v1.4 考研词库 711 词 / 23 组 / 无重复词校验、v1.5 设置页 5 tab / 设置项搬离导入与统计页校验、v1.6 多组 SQL IN / 多选范围 / 顺序切换校验、v1.7 折叠头 / 行内发音 / 主按钮顺序校验、v1.8 Unit/Day 分组折叠校验、v1.9 词库只读 / 英音单列 / 完整词形 / 编辑入口校验、v1.10 词库组别多选 / 列表与解锁共用范围校验、**v1.11 sherpa-onnx AAR / Piper 模型 / BundledTts / AudioTrack 校验**）
- 已做（v10）：本机 Android 37 模拟器（API 37、1080×2400、Play Store 镜像）用 `assembleDebug` 真编译、`adb install` 安装并点按验证：加载示例词库 → 解锁 → 拼写测试 → 输入正确 / 错误答案 / 提示 / 看答案 → 完成页「拼对 1 / 10」→ 再来一轮（9 个错词）；同时确认拼写卡在认词 / 小测 / 完成态会正确隐藏
- 已做（v1.4）：同一模拟器上清空词库 → 导入页「载入考研词库」→ 确认 → 预览显示「711 个单词 / 23 组 / 自动补 706 个音标 / 未命中 5 个」→ 导入成功 → 词库顶栏「已解锁 0 / 711」、划组列表显示 23 组（Unit 2 Day 3 为 34 个）；随后把 `Pre-` 修正为 `prefix`，最终资产为 711 词 / 23 组 / 10 条拼写备注。修正后重装并再次导入：预览为 707 个音标、未命中 4 个，词库 0 / 711
- 已做（v1.5）：同一模拟器安装 v1.5，确认底部 5 个 tab（词库 / 学习 / 统计 / 导入 / 设置）都能切换；设置页渲染出学习、数据管理、发音、音标、版本与更新、关于六张卡；导入页精简为 5 张卡（文档 / 粘贴 / 划组 / 内置词库 / 微信），统计页底部不再有重置与版本卡。设置项复用旧监听逻辑，未改数据库与偏好键
- 已做（v1.6）：同一模拟器安装 v1.6 → 学习页「范围」→「清空」→ 勾选 Unit 1 Day 1 + Unit 1 Day 2 → 确定，按钮显示「范围：2 个组」，待学数变为该范围；「顺序」切到「打乱」后开始学习，卡片仍来自所选组；重新打开范围弹窗能保留勾选状态
- 已做（v1.7）：同一模拟器安装 v1.7 → 词库页顶部搜索 / 筛选 / 计数 / 进度正常渲染 → 上滑后这些控件随列表滚出屏幕，单词行上移到 490px 附近 → 每行右侧显示「发音」按钮；学习页按钮顺序显示「开始学习（朱红）→ 解锁今日新词（浅色）」
- 已做（v1.8）：同一模拟器安装 v1.8 → 词库页显示 Unit / Day 二级标题（如 Unit 5 Day 3）→ 点标题折叠后单词行消失、箭头变为 `▸` → 搜索框输入文字后强制展开；顶部 header 仍随列表滚动折叠
- 已做（v1.9）：同一模拟器安装 v1.9 → 词库行不再出现「未解锁」chip 与「待解锁」→ 音标只显示英音 → `education` / `conductor` 等长词完整显示 → 导入页出现「⑤ 编辑单词」，点开是搜索框 + 词表；词库页点击 / 长按不再打开编辑
- 已做（v1.10）：同一模拟器安装 v1.10 → 词库页「组别」→「清空」→ 勾选 Unit 1 Day 1 + Unit 1 Day 2 → 确定，按钮显示「组别：2 个组」，当前显示 78 词；「解锁新词」也按这 2 个组执行
- 已做（v1.11）：Android 37 x86_64 模拟器安装 debug 包（含 x86_64），logcat 显示 `WordCoolTts: ready: sampleRate=16000`；点词库行「发音」后 `AudioTrack` 生成 `samples=12800 rate=16000`，确认不经过系统 TTS。正式 release 包为 arm64 + armeabi-v7a，约 73 MB；模拟器无音频输出，实际音质需真机听
- 已做：解析算法等价模拟与 **v7 的 8 项行为回归**（逐字段只补空、文档自带双音标不覆盖、别名补缺列、多读音保留、词组与自造词留空），断言全部通过；v8 另加了外部 Intent 的等价模拟（三种 action、Uri 去重、多文件合并、二进制拦截）；**v9 加了 9 项检查更新回归**（版本三段比较含 1.10>1.9、预发布后缀、assets 挑 APK 直链、无 assets 降级、权限白名单）
- 已做：用真实公开高频词表 `google-10000-english-no-swears`（9,894 词）跑离线查表，得到 94.9% / 77.6% / 5.1% 三组覆盖率数字，非估算
- 曾做（**该工作流现已按用户要求移除**）：GitHub Actions（ubuntu-22.04 + JDK 17 + SDK 34 + Gradle 8.2）真实编译通过，`assembleDebug` 产出 `WordCool.apk`，并自动发布 Release；此前两轮 CI 失败（setup-android action、SDK 34 不存在的 TTS 常量）都已按日志定位并修掉
- 架构图通过 `archify` showcase 校验（9/9）与 4 个桌面视口浏览器检查
- **仍未做**：真机安装与点按测试。尤其 TTS 的 `setVoice` / `isLanguageAvailable` / `UtteranceProgressListener`、v8 的微信分享入口是否出现在系统列表里、v9 的实际网络请求与浏览器跳转、v1.4 在真实手机上的 711 词导入与滚动性能、v1.5 在窄屏 / 大字号下 5 个 tab 的显示、v1.6 多选范围弹窗在组特别多时的滚动、v1.7 行内「发音」按钮在真实 TTS 下的点击与出声，静态检查与模拟器都验证不了——必须装到手机上试一次才能确认
- 静态检查与曾经的 CI 都不能证明运行时零缺陷，遇到具体报错请把截图或错误文字发来，按现象定位
- 现成 APK 在 <https://github.com/dotfaker-sys/WordCool/releases>；自行编译见 `README.md`，编译前先跑 `python3 tools/verify.py`
