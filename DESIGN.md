# 单词酷 · 设计系统与架构规范 v9

> 版本沿革：v2 页面重设计 → v3 加功能 → v4 修 15 项缺陷 → v5 英音 / 美音双发音与双音标 → v6 内置离线开源音标词典 → v7 对照同行产品与开源数据源做实证调研 → v8 接入微信 / QQ 文件分享并做成 CI 真编译 + 自动发版 → v9 应用内检查更新（首次、也是唯一一次新增 `INTERNET` 权限）→ **v9.1 按用户要求移除 GitHub Actions 工作流**。
>
> **v9.1 一句话**：`.github/workflows/build-apk.yml` 已删除，push 到 `main` 不再自动编译、也不再自动发新版 Release。已发布的三条 Release 与 APK 全部保留，应用内「检查更新」仍能读到现有的 latest（v1.2.0）；只是以后不会自动冒出新版本。源码、数据、文档、自检脚本一行没删，只少了这一个自动化文件。
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
6. **v2 的全部 `R.id` 与布局文件名**：8 个布局、90 个旧 id 一个不改名、不删除（v3 新增 10 个、v4 新增 `dialog_input.xml`、v5 新增 9 个、v6 新增 8 个，现共 9 布局 / 117 个 id）。
7. **`Ui.kt` 的函数签名**：`Pal` 字段名、`dp` / `rounded` / `toast` / `show` / `stateName` / `stateColor` 全部保持；仅替换色值与圆角收敛系数。
8. **Gradle 与清单**：`applicationId`、minSdk 26 / targetSdk 34、**零第三方依赖（Gradle dependencies 仍为空）**。**v1.0/v1.1 为零权限；v1.2 起按用户明确确认新增唯一一个 `<uses-permission android:name="android.permission.INTERNET" />`**，只给「检查更新」用，仍无存储 / 定位 / 通讯录等任何其他权限，`tools/verify.py` 用白名单强制只允许这一个。v8 前 `AndroidManifest.xml` 一直没有改过；v8 起**只增不改**：加 `launchMode="singleTask"` 与 3 组接收文件的 `intent-filter`，原 MAIN / LAUNCHER 过滤器与 `exported="true"` 不变。Kotlin 源文件 8 → 9（v6 `IpaDictionary.kt`）→ **10（v9 `UpdateChecker.kt`）**；v6 另新增 `assets/ipa.tsv`、`LICENSES/`、`tools/`，均为数据与脚本，不是代码依赖。
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

## 12. 验收与诚实边界

- 已做：`python3 tools/verify.py` 一键静态自检通过（25 个 XML 合法性与重复属性、122 个 id / 资源引用一致性、10 个 Kotlin 精细括号扫描、emoji 残留、`ipa.tsv` 三列格式与排序、许可证文件齐全、上游 SHA-256 与 `ipa_stats.json` 行数一致性、**Manifest 三组接收过滤器与「仅 INTERNET 一个权限」白名单校验**）
- 已做：解析算法等价模拟与 **v7 的 8 项行为回归**（逐字段只补空、文档自带双音标不覆盖、别名补缺列、多读音保留、词组与自造词留空），断言全部通过；v8 另加了外部 Intent 的等价模拟（三种 action、Uri 去重、多文件合并、二进制拦截）；**v9 加了 9 项检查更新回归**（版本三段比较含 1.10>1.9、预发布后缀、assets 挑 APK 直链、无 assets 降级、权限白名单）
- 已做：用真实公开高频词表 `google-10000-english-no-swears`（9,894 词）跑离线查表，得到 94.9% / 77.6% / 5.1% 三组覆盖率数字，非估算
- 曾做（**该工作流现已按用户要求移除**）：GitHub Actions（ubuntu-22.04 + JDK 17 + SDK 34 + Gradle 8.2）真实编译通过，`assembleDebug` 产出 `WordCool.apk`，并自动发布 Release；此前两轮 CI 失败（setup-android action、SDK 34 不存在的 TTS 常量）都已按日志定位并修掉
- 架构图通过 `archify` showcase 校验（9/9）与 4 个桌面视口浏览器检查
- **仍未做**：真机安装与点按测试。尤其 TTS 的 `setVoice` / `isLanguageAvailable` / `UtteranceProgressListener`、v8 的微信分享入口是否出现在系统列表里、以及 v9 的实际网络请求与浏览器跳转，静态检查与 CI 都验证不了——必须装到手机上试一次才能确认
- 静态检查与曾经的 CI 都不能证明运行时零缺陷，遇到具体报错请把截图或错误文字发来，按现象定位
- 现成 APK 在 <https://github.com/dotfaker-sys/WordCool/releases>；自行编译见 `README.md`，编译前先跑 `python3 tools/verify.py`
