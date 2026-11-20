# 上传到 GitHub：dotfaker-sys / WordCool

> 我这边没有你 GitHub 账号的写入权限，**没法直接替你 push**。下面给的是照着粘贴就能完成的一套命令；最省事的是直接跑仓库根目录的 `push_to_github.sh`。
>
> 你的主页 `github.com/dotfaker-sys` 目前是 0 个公开仓库，所以按「**新建公开仓库**」来：仓库名用 `WordCool`（想改就改下面命令里的 `REPO_NAME`）。

---

## 0. 前提（两分钟）

| 要什么 | 说明 |
| --- | --- |
| Git | 电脑装好 Git；Windows 用 **Git Bash** 跑下面的命令，PowerShell 也能跑大部分 |
| GitHub 账号 | 已登录 `dotfaker-sys` |
| 认证 | **GitHub 从 2021 年起不允许用账号密码 push**，二选一：装 [GitHub CLI](https://cli.github.com/)（`gh auth login`），或生成 PAT |

PAT 生成：<https://github.com/settings/tokens/new> → 勾 `repo` → Generate → **立刻复制**（只显示一次）。

---

## 1. 最省事：装了 `gh` 的话，两条命令

在 `WordCool` 目录里：

```bash
gh auth login                 # 首次用，浏览器授权一次
bash push_to_github.sh        # 自动自检 → 提交 → 建仓 → push
```

脚本会先跑 `python3 tools/verify.py`，自检过了才提交；仓库已存在时不会重复创建，也不会 force push。

---

## 2. 没装 `gh`：网页建仓 + 命令行推送

**第 1 步**：打开 <https://github.com/new>
- Repository name：`WordCool`
- 选 **Public**
- **不要**勾 `Add a README file` / `.gitignore` / `Choose a license`（空仓库才不会冲突）

**第 2 步**：在 `WordCool` 目录里依次粘贴：

```bash
cd WordCool                                   # 已经在目录里就跳过

git init
git config user.name  "dotfaker-sys"
git config user.email "dotfaker-sys@users.noreply.github.com"

python3 tools/verify.py                       # 静态自检；Windows 若提示找不到就换成 python
git add -A                                    # -A 会把 .github 隐藏目录一起带上
git commit -m "单词酷 v7：离线英美音标词典 + 双发音 + Word/Excel/PDF 导入"
git branch -M main

git remote add origin https://github.com/dotfaker-sys/WordCool.git
git push -u origin main
```

`git push` 时若要求输入密码，**粘贴 PAT，不是账号密码**（用户名填 `dotfaker-sys`）。

**推荐**：直接 `git push -u origin main`，让 Git 弹浏览器授权或交给凭据管理器（Git Credential Manager / macOS Keychain），PAT 不落进命令历史。

**不推荐**把 token 明文拼进 remote URL：它会留在 `.git/config` 和 shell 历史里，哪怕事后 `git remote set-url` 抹掉也清不干净。确需临时免交互时，用环境变量 + 凭据助手，别把字符串写进命令行。

> **凭据安全**：PAT 等同于账号密码，**只在你自己电脑的终端里粘贴**。不要发到聊天、截图、文档、Issue 或代码里；一旦贴出去，立刻到 <https://github.com/settings/tokens> 点 **Revoke** 作废并重新生成（GitHub 有时会检测泄露自动撤销，但别等它）。新 token 不用发给任何人，包括我。

---

## 3. 纯手写最简版（三行）

仓库已在网页建好、且 PAT 已备好时：

```bash
git init && git add -A && git commit -m "init: 单词酷 Android 工程"
git branch -M main && git remote add origin https://github.com/dotfaker-sys/WordCool.git
git push -u origin main
```

---

## 4. 推完检查这三件事

1. **文件齐不齐**：仓库页应能看到
   - `.github/workflows/build-apk.yml` ← 云编译靠它，**最容易漏**
   - `app/src/main/assets/ipa.tsv` + `ipa_stats.json` ← 离线音标数据
   - `LICENSES/` ← MIT + GPL-3.0 全文与署名声明，**公开发布必须保留**
   - `tools/verify.py`、`tools/build_ipa.py`、`DESIGN.md`、`QUICKSTART.md`
2. **Actions 有没有自动跑**：<https://github.com/dotfaker-sys/WordCool/actions>
   push 到 `main` 会自动触发；没跑就在该页手动点 **Run workflow**。
3. **下载 APK**：进那次运行 → 底部 Artifacts → **WordCool-APK** → 解压得到 `WordCool.apk` → 传手机安装。

---

## 5. 常见报错

| 现象 | 原因 / 处理 |
| --- | --- |
| `remote origin already exists` | `git remote remove origin` 再 `git remote add origin ...` |
| `rejected / fetch first` | 建仓时勾了 README。执行 `git pull origin main --rebase` 后再 push；或删掉仓库重建空仓库 |
| `Authentication failed` | 用了账号密码。改 PAT 或 `gh auth login` |
| `.github` 没上去，Actions 不触发 | 多半是网页拖拽上传漏了隐藏目录。命令行 `git add -A` 不会漏；也可用 `git add -f .github/workflows/build-apk.yml` |
| Actions 里 `sdkmanager` 报 licenses | 配置里已加 `yes \| sdkmanager --licenses`，若仍失败看运行日志具体步骤 |
| 想换仓库名 | 脚本用 `REPO_NAME=MyWords bash push_to_github.sh`；手写命令把两处 `WordCool` 一起改掉 |

---

## 6. 一句话边界

源码仓库里**不放预编译 APK**，也**没有 gradlew**（CI 会自动 `gradle wrapper` 生成）；`UPLOAD.md` 只负责把源码推上去，出包交给 Actions。CI 编译成功后还会自动发布 / 更新 Release（默认 tag `v1.1.0`，可在 Actions 页手动填别的 tag），现成安装包在 <https://github.com/dotfaker-sys/WordCool/releases>。Android Studio 本地打开不依赖 gradlew。英音数据上游是 GPL-3.0，公开分发请保留 `LICENSES/`。
