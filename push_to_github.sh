#!/usr/bin/env bash
# 单词酷 → GitHub 公开仓库 一键推送脚本
#
# 用法（在 WordCool 工程根目录运行）：
#     bash push_to_github.sh
#
# 可覆盖的变量：
#     REPO_NAME=MyWords bash push_to_github.sh        # 改仓库名（默认 WordCool）
#     GIT_NAME / GIT_EMAIL                            # 改提交署名
#
# 前提：已安装 Git；推荐同时装 GitHub CLI（gh），能一条命令建仓+推送。
# 凭据由 gh auth login 或 Git 凭据管理器处理；本脚本不接收、不保存、不回显任何 token。
# 详细图文步骤见 UPLOAD.md。

set -euo pipefail

GITHUB_USER="dotfaker-sys"
REPO_NAME="${REPO_NAME:-WordCool}"
GIT_NAME="${GIT_NAME:-dotfaker-sys}"
GIT_EMAIL="${GIT_EMAIL:-dotfaker-sys@users.noreply.github.com}"

cd "$(dirname "$0")"

echo "=============================================="
echo " 单词酷 → github.com/${GITHUB_USER}/${REPO_NAME}"
echo "=============================================="

# 1) 确认在工程根目录
if [ ! -f "app/build.gradle" ] || [ ! -f "settings.gradle" ]; then
  echo "✗ 请在 WordCool 工程根目录运行本脚本（当前目录找不到 app/build.gradle）"
  exit 1
fi

# 2) 上传前先跑一遍随包静态自检
echo
echo "[1/5] 静态自检…"
PY=python3
command -v "$PY" >/dev/null 2>&1 || PY=python
if command -v "$PY" >/dev/null 2>&1; then
  "$PY" tools/verify.py || { echo "✗ 静态自检没过，先修好再上传"; exit 1; }
else
  echo "  （没找到 Python，跳过静态自检；GitHub Actions 里仍会自动跑）"
fi

# 3) 本地仓库初始化与提交
echo
echo "[2/5] 初始化本地 Git 仓库…"
if [ ! -d .git ]; then git init -q; fi
git config user.name  >/dev/null 2>&1 || git config user.name  "$GIT_NAME"
git config user.email >/dev/null 2>&1 || git config user.email "$GIT_EMAIL"

git add -A
if git diff --cached --quiet; then
  echo "  没有新改动，沿用已有提交"
else
  git commit -qm "单词酷 v7：离线英美音标词典 + 双发音 + Word/Excel/PDF 导入与 Unit/Day 划组"
  echo "  已提交"
fi
git branch -M main

# 4) 建仓 + 推送
echo
echo "[3/5] 连接 GitHub…"
if command -v gh >/dev/null 2>&1; then
  echo "  检测到 GitHub CLI，走最省事的路径"
  gh auth status >/dev/null 2>&1 || gh auth login
  if gh repo view "${GITHUB_USER}/${REPO_NAME}" >/dev/null 2>&1; then
    echo "  仓库已存在，直接推送"
    git remote remove origin 2>/dev/null || true
    git remote add origin "https://github.com/${GITHUB_USER}/${REPO_NAME}.git"
    git push -u origin main
  else
    gh repo create "${GITHUB_USER}/${REPO_NAME}" --public --source=. --remote=origin --push
  fi
else
  echo "  没装 GitHub CLI，走「网页建仓 + PAT」路径"
  echo
  echo "  ① 打开 https://github.com/new"
  echo "     Repository name : ${REPO_NAME}"
  echo "     选 Public；【不要】勾选 Add a README / .gitignore / license"
  echo "  ② 打开 https://github.com/settings/tokens/new 生成 PAT，勾选 repo 权限并复制"
  echo "     （GitHub 早就不允许用账号密码 push；只在 git push 提示密码时粘贴）"
  echo "     PAT 不要发到聊天、截图或写进命令行；泄露了立刻去 settings/tokens 撤销"
  echo
  read -r -p "  建好仓库后按回车继续… " _
  git remote remove origin 2>/dev/null || true
  git remote add origin "https://github.com/${GITHUB_USER}/${REPO_NAME}.git"
  echo
  echo "[4/5] 推送…（若提示输入密码，粘贴 PAT；优先让凭据管理器/浏览器接管）"
  git push -u origin main
fi

echo
echo "[5/5] 完成"
echo "  仓库    : https://github.com/${GITHUB_USER}/${REPO_NAME}"
echo "  Actions : https://github.com/${GITHUB_USER}/${REPO_NAME}/actions"
echo "  push 会自动触发云编译；跑完进那次运行，下载 Artifact「WordCool-APK」"
echo "  （也可在 Actions 页手动点 Run workflow）"
echo
echo "提醒：LICENSES/ 目录必须一起传上去——英音数据上游是 GPL-3.0，公开分发需保留声明与全文。"
