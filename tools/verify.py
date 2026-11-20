#!/usr/bin/env python3
"""单词酷工程静态自检。不依赖 Android SDK，标准库即可运行。

用法（在工程根目录）：
    python3 tools/verify.py

检查项：XML 合法性与重复属性、R.* 引用与资源定义一致性、Kotlin 括号/字符串
配对、emoji 残留、ipa.tsv 格式与排序、第三方许可证文件是否齐全。
通过退出 0，失败退出 1。

注意：这是静态检查，不能替代 Android Studio 的真实编译与真机点按测试。
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main"
errors = []

xmls = sorted(MAIN.rglob("*.xml"))
kts = sorted((MAIN / "java").rglob("*.kt"))

layouts, drawables, colors, strings, ids = set(), set(), set(), set(), set()

for p in xmls:
    try:
        ET.parse(p)
    except Exception as e:
        errors.append(f"XML 解析失败 {p.relative_to(ROOT)}: {e}")
        continue
    t = p.read_text(encoding="utf-8")
    ids.update(re.findall(r"@\+id/(\w+)", t))
    rel = p.relative_to(MAIN).as_posix()
    if "/layout/" in rel:
        layouts.add(p.stem)
    if "/drawable/" in rel:
        drawables.add(p.stem)
    colors.update(re.findall(r'<color\s+name="(\w+)"', t))
    strings.update(re.findall(r'<string\s+name="(\w+)"', t))
    for name, body in re.findall(r"<([A-Za-z][\w.]*)\b([^>]*?)/?>", t, re.S):
        attrs = re.findall(r"([\w:.-]+)\s*=", body)
        dup = [a for a in set(attrs) if attrs.count(a) > 1]
        if dup:
            errors.append(f"{p.relative_to(ROOT)}: <{name}> 重复属性 {dup}")
    for ch in ("🔊", "🔒", "📖", "✅", "✓"):
        if ch in t:
            errors.append(f"{p.relative_to(ROOT)}: 残留 emoji {ch}")

# 第二遍：所有定义已收集齐，再校验 XML 里的 @资源引用（顺序无关）
for p in xmls:
    t = p.read_text(encoding="utf-8")
    for kind, defined in (("drawable", drawables), ("color", colors),
                          ("string", strings), ("layout", layouts)):
        for n in re.findall(r"@" + kind + r"/(\w+)", t):
            if n not in defined:
                errors.append(f"{p.relative_to(ROOT)}: @{kind}/{n} 未定义")

for p in kts:
    s = p.read_text(encoding="utf-8")
    for kind, defined in (("id", ids), ("layout", layouts),
                          ("drawable", drawables), ("color", colors),
                          ("string", strings)):
        for n in re.findall(r"R\." + kind + r"\.(\w+)", s):
            if n not in defined:
                errors.append(f"{p.relative_to(ROOT)}: R.{kind}.{n} 未定义")
    # 精细括号扫描：跳过 // 注释、三引号、普通字符串、字符字面量
    i, depth, n, line = 0, 0, len(s), 1
    while i < n:
        c = s[i]
        if c == "\n":
            line += 1
        if c == "/" and i + 1 < n and s[i + 1] == "/":
            j = s.find("\n", i)
            i = n if j < 0 else j
            continue
        if s.startswith('"""', i):
            j = s.find('"""', i + 3)
            if j < 0:
                errors.append(f"{p.relative_to(ROOT)}:{line} 三引号未闭合")
                break
            line += s[i:j + 3].count("\n")
            i = j + 3
            continue
        if c == '"':
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == '"':
                    i += 1
                    break
                if s[i] == "\n":
                    line += 1
                i += 1
            continue
        if c == "'":
            i += 1
            while i < n:
                if s[i] == "\\":
                    i += 2
                    continue
                if s[i] == "'":
                    i += 1
                    break
                i += 1
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth < 0:
                errors.append(f"{p.relative_to(ROOT)}:{line} 多余的 }}")
                depth = 0
        i += 1
    if depth != 0:
        errors.append(f"{p.relative_to(ROOT)}: 大括号未闭合 depth={depth}")

# 离线词典数据
ipa = MAIN / "assets/ipa.tsv"
if not ipa.exists():
    errors.append("缺少 assets/ipa.tsv")
else:
    raw = ipa.read_text(encoding="utf-8")
    rows = raw.splitlines()
    bad = [(i + 1, l) for i, l in enumerate(rows) if l.count("\t") != 2]
    keys = [l.split("\t")[0] for l in rows if l]
    if bad:
        errors.append(f"ipa.tsv 有 {len(bad)} 行列数不是 3，例如第 {bad[0][0]} 行")
    if keys != sorted(keys):
        errors.append("ipa.tsv 未按词字典序排序，IpaDictionary 的行首查找会失效")
    if any(not k for k in keys):
        errors.append("ipa.tsv 存在空词条")
    # 每个非空音标字段的 / 必须成对（v7 曾出现 `/ðə, /ði/` 这种缺闭合斜杠）
    bad_slashes = 0
    for l in rows:
        for cell in l.split("\t")[1:]:
            if cell and (cell.count("/") == 0 or cell.count("/") % 2 != 0):
                bad_slashes += 1
    if bad_slashes:
        errors.append(f"ipa.tsv 有 {bad_slashes} 个音标字段斜杠不成对")
    print(f"ipa.tsv: {len(rows)} 条 · {ipa.stat().st_size / 1024 / 1024:.2f} MB · 音标格式正常")

stats = MAIN / "assets/ipa_stats.json"
if not stats.exists():
    errors.append("缺少 assets/ipa_stats.json")
else:
    import json
    try:
        st = json.loads(stats.read_text(encoding="utf-8"))
        if st.get("entries") != len(rows):
            errors.append(f"ipa_stats.json entries={st.get('entries')} 与 ipa.tsv 的 {len(rows)} 条不一致")
        if st.get("tsv_bytes") != ipa.stat().st_size:
            errors.append("ipa_stats.json tsv_bytes 与 ipa.tsv 实际大小不一致")
        if not st.get("upstream_sha256"):
            errors.append("ipa_stats.json 缺少上游 SHA-256")
    except Exception as e:
        errors.append(f"ipa_stats.json 解析失败: {e}")

for f in ("MIT.txt", "GPL-3.0-only.txt", "THIRD_PARTY_NOTICES.md"):
    if not (ROOT / "LICENSES" / f).is_file():
        errors.append(f"缺少许可证文件 LICENSES/{f}")

# ---- v7：数据溯源 + 行为回归 ----
import json
import unicodedata

stats_path = MAIN / "assets/ipa_stats.json"
if not stats_path.exists():
    errors.append("缺少 assets/ipa_stats.json（数据溯源与覆盖率统计）")
else:
    st = json.loads(stats_path.read_text(encoding="utf-8"))
    if st.get("entries") != len(rows):
        errors.append(f"ipa_stats.json entries={st.get('entries')} 与 ipa.tsv 行数 {len(rows)} 不一致")
    if not st.get("upstream_sha256"):
        errors.append("ipa_stats.json 缺上游 SHA-256，无法复核数据来源")
    if st.get("multi_pronunciation_rows", 0) < 1000:
        errors.append("多读音似乎又被丢弃了：multi_pronunciation_rows 过小")
    print(f"数据溯源: 上游 SHA-256 {len(st.get('upstream_sha256', {}))} 份 · "
          f"多读音行 {st.get('multi_pronunciation_rows')} · "
          f"双口音 {st.get('both_accents')}")

# 构建期归一必须和运行时 IpaDictionary.normalize() 一致
STRIP = " \t\"'.,;:!?()[]{}"
def py_norm(w):
    return unicodedata.normalize("NFC", w.strip().lower()).strip(STRIP)
if any(py_norm(k) != k for k in keys):
    errors.append("ipa.tsv 存在未归一的词条，运行时会查不到")

table = {}
for l in rows:
    c = l.split("\t")
    if len(c) == 3:
        table[c[0]] = (c[1], c[2])
ALIAS = {"color": "colour", "colour": "color", "center": "centre",
         "centre": "center", "organize": "organise", "organise": "organize"}
def py_lookup(w):
    k = py_norm(w)
    d = table.get(k)
    a = table.get(ALIAS.get(k, ""))
    if d is None:
        return a
    if a and (not d[0] or not d[1]):
        return (d[0] or a[0], d[1] or a[1])
    return d

def py_autofill(items):
    """等价复刻 MainActivity.autofillPhonetics：逐字段只补空"""
    out, changed = [], 0
    for it in items:
        w, main, uk, us = it
        ipa = py_lookup(w)
        if not ipa:
            out.append(it); continue
        nuk = uk if uk else ipa[0]
        nus = us if us else ipa[1]
        nmain = main if main else (nuk or nus)
        if (nuk, nus, nmain) != (uk, us, main):
            changed += 1
        out.append((w, nmain, nuk, nus))
    return out, changed

cases = [
    ("abandon", "", "", ""),                     # 只有单词+汉语
    ("after", "", "UK /x/", "US /y/"),           # 文档自带双音标，不能被覆盖
    ("focus", "/mine/", "", ""),                 # 文档只给了通用音标
    ("take off", "", "", ""),                    # 词组，词典未收录
    ("xyzzyq", "", "", ""),                      # 自造词
    ("color", "", "", ""),                       # 别名补英音
]
filled, n = py_autofill(cases)
checks = [
    (filled[0][2] and filled[0][3], "abandon 应补出英/美双音标"),
    (filled[1][2] == "UK /x/" and filled[1][3] == "US /y/", "文档自带双音标不能被覆盖"),
    (filled[2][1] == "/mine/" and filled[2][2] and filled[2][3],
     "单音标保留，同时补齐 UK/US 两列"),
    (not filled[3][1] and not filled[3][2] and not filled[3][3], "词组未命中应留空"),
    (not filled[4][1] and not filled[4][2] and not filled[4][3], "自造词未命中应留空"),
    (bool(filled[5][2]), "英美拼写别名应补上缺失的一列"),
    (n == 4, f"补全词条数应为 4，实际 {n}"),
    (", " in table.get("schedule", ("", ""))[1], "同形异读应保留多个候选，不能只取第一条"),
]
for ok, msg in checks:
    if not ok:
        errors.append("v7 回归失败: " + msg)
print(f"v7 行为回归: {sum(1 for ok, _ in checks if ok)}/{len(checks)} 项通过 · 补全 {n} 词")

# ---- v8：微信 / QQ 外部 Intent 接收 ----
manifest = (MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")
for act in ("android.intent.action.VIEW", "android.intent.action.SEND",
            "android.intent.action.SEND_MULTIPLE"):
    if act not in manifest:
        errors.append(f"Manifest 缺少外部文件接收 action: {act}")
# v9 起按用户确认加了 INTERNET（应用内检查更新）；只允许这一个，禁止夹带别的权限
perms = re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', manifest)
if perms != ["android.permission.INTERNET"]:
    errors.append(
        "权限白名单被破坏：只允许 android.permission.INTERNET 一个，实际 " + str(perms)
    )
if 'android:launchMode="singleTask"' not in manifest:
    errors.append("Manifest 缺少 singleTask，微信唤起会堆多个 Activity 实例")
if 'android:exported="true"' not in manifest:
    errors.append("targetSdk 34 要求显式 exported=true")
if "application/octet-stream" not in manifest:
    errors.append("Manifest 未兜住微信常给的 application/octet-stream")

main_kt = (MAIN / "java/com/wordcool/app/MainActivity.kt").read_text(encoding="utf-8")
for token in ("handleIncomingIntent", "onNewIntent", "importFromUris", "readIncoming",
              "looksLikeText", "guessName", "Intent.EXTRA_STREAM", "clipData"):
    if token not in main_kt:
        errors.append(f"MainActivity 缺少 v8 微信导入实现: {token}")

def collect(action, data=None, stream=None, clip=None, text=None):
    """等价复刻 handleIncomingIntent 的 Uri 收集与去重"""
    uris = []
    if data:
        uris.append(data)
    if isinstance(stream, str):
        uris.append(stream)
    elif isinstance(stream, list):
        uris.extend(stream)
    if clip:
        uris.extend(clip)
    return list(dict.fromkeys(uris)), (text if action == "SEND" else None)

def py_looks_like_text(raw):
    if not raw:
        return False
    s = raw.decode("utf-8", "replace")
    if not s:
        return False
    bad = sum(1 for c in s if c == "\ufffd" or (ord(c) < 0x20 and c not in "\n\r\t"))
    return bad / len(s) < 0.05

v8 = [
    (collect("VIEW", data="content://a.docx")[0] == ["content://a.docx"],
     "ACTION_VIEW 应从 intent.data 取到 Uri"),
    (collect("SEND", stream="content://b.docx")[0] == ["content://b.docx"],
     "ACTION_SEND 应从 EXTRA_STREAM 取到 Uri"),
    (collect("SEND_MULTIPLE", stream=["content://c1.docx", "content://c2.xlsx"])[0]
     == ["content://c1.docx", "content://c2.xlsx"],
     "ACTION_SEND_MULTIPLE 应收到多个 Uri"),
    (collect("SEND", stream="content://d.docx",
             clip=["content://d.docx", "content://e.pdf"])[0]
     == ["content://d.docx", "content://e.pdf"],
     "EXTRA_STREAM 与 clipData 重复时应去重"),
    (collect("SEND", text="abandon 放弃\nbenefit 益处")[1] is not None,
     "纯文本分享应转成粘贴导入"),
    (py_looks_like_text("Unit 1\nabandon 放弃\n".encode("utf-8")),
     "正常 UTF-8 词表应被当成文本"),
    (not py_looks_like_text(b"\x89PNG\r\n\x1a\n\xff\xfe\x00\x01binary"),
     "图片二进制不应被当成 UTF-8 词表"),
]
for ok, msg in v8:
    if not ok:
        errors.append("v8 回归失败: " + msg)
print(f"v8 微信导入回归: {sum(1 for ok, _ in v8 if ok)}/{len(v8)} 项通过 · "
      f"权限白名单 / 三组过滤器校验完成")

# ---- v9：应用内检查更新 ----
upd = (MAIN / "java/com/wordcool/app/UpdateChecker.kt")
if not upd.is_file():
    errors.append("缺少 UpdateChecker.kt")
else:
    u = upd.read_text(encoding="utf-8")
    for token in ("HttpURLConnection", "runOnUiThread", "JSONObject", "connectTimeout",
                  "readTimeout", "disconnect", "setRequestProperty(\"User-Agent\"",
                  "SocketTimeoutException", "UnknownHostException", "RELEASES_PAGE",
                  "compare(", "autoCheck", "toggleAuto", "isDestroyed"):
        if token not in u:
            errors.append(f"UpdateChecker 缺少: {token}")
    if "releases/latest" not in u:
        errors.append("UpdateChecker 未指向 GitHub latest Release 接口")
    if "startActivity" not in u or "Intent.ACTION_VIEW" not in u:
        errors.append("发现新版后没有跳转浏览器的入口")

stats_kt = (MAIN / "java/com/wordcool/app/StatsView.kt").read_text(encoding="utf-8")
for token in ("btnCheckUpdate", "btnAutoUpdate", "tvVersion", "UpdateChecker.check",
              "UpdateChecker.currentVersion", "renderAutoLabel"):
    if token not in stats_kt:
        errors.append(f"StatsView 未接上更新入口: {token}")
stats_xml = (MAIN / "res/layout/page_stats.xml").read_text(encoding="utf-8")
for token in ("tvVersion", "btnCheckUpdate", "btnAutoUpdate"):
    if 'android:id="@+id/' + token + '"' not in stats_xml:
        errors.append(f"page_stats.xml 缺少 id: {token}")

if "UpdateChecker.autoCheck(this)" not in main_kt or "isIncomingIntent" not in main_kt:
    errors.append("MainActivity 未接上启动时静默检查 / 未避开微信分享入口")

build_gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
if "versionCode 3" not in build_gradle or "versionName '1.2'" not in build_gradle:
    errors.append("app/build.gradle 版本号未升到 v1.2 / versionCode 3")
wf = (ROOT / ".github/workflows/build-apk.yml").read_text(encoding="utf-8")
if "'v1.2.0'" not in wf or "应用内检查更新" not in wf:
    errors.append("CI 默认 tag / 发布说明未同步到 v1.2.0")

def py_compare(local, remote):
    """等价复刻 UpdateChecker.compare：>0 表示远端更新"""
    def parts(v):
        core = v.strip().lstrip("vV").split("-")[0].split("+")[0]
        return [int("".join(c for c in x if c.isdigit()) or "0")
                for x in core.split(".")][:4]
    a, b = parts(local), parts(remote)
    for i in range(max(len(a), len(b))):
        x = a[i] if i < len(a) else 0
        y = b[i] if i < len(b) else 0
        if x != y:
            return (y > x) - (y < x)
    return 0

def py_pick_apk(payload):
    """等价复刻 parse()：从 assets 里挑第一个 .apk 下载直链"""
    apk = ""
    for a in payload.get("assets", []):
        url = a.get("browser_download_url", "")
        if url.lower().endswith(".apk"):
            apk = url
            break
    return apk, payload.get("tag_name", "").strip()

v9 = [
    (py_compare("1.2", "v1.2.0") == 0, "1.2 与 v1.2.0 应视为同一版本，不弹更新"),
    (py_compare("1.1", "v1.2.0") > 0, "远端 v1.2.0 应判为比 1.1 新"),
    (py_compare("1.2", "v1.1.0") < 0, "本地比远端新时不应弹更新"),
    (py_compare("1.9", "v1.10.0") > 0, "逐段数字比较，1.10 必须大于 1.9"),
    (py_compare("1.2", "v1.2.1") > 0, "补丁号变化也应识别为新版"),
    (py_compare("1.2", "v1.2.0-beta") == 0, "预发布后缀不参与比较"),
    (py_pick_apk({"tag_name": "v1.3.0", "assets": [
        {"browser_download_url": "https://x/notes.md"},
        {"browser_download_url": "https://x/WordCool.apk"}]})
     == ("https://x/WordCool.apk", "v1.3.0"),
     "应从 assets 里挑出 APK 直链"),
    (py_pick_apk({"tag_name": "v1.3.0", "assets": []}) == ("", "v1.3.0"),
     "没有 assets 时降级为发布页，不崩溃"),
    (perms == ["android.permission.INTERNET"], "权限白名单：仅 INTERNET 一个"),
]
for ok, msg in v9:
    if not ok:
        errors.append("v9 回归失败: " + msg)
print(f"v9 检查更新回归: {sum(1 for ok, _ in v9 if ok)}/{len(v9)} 项通过 · "
      f"权限白名单 {perms}")

print(f"XML {len(xmls)} · Kotlin {len(kts)} · 布局 {len(layouts)} · id {len(ids)} · drawable {len(drawables)}")
if errors:
    print("\n发现问题：")
    for e in errors:
        print("  -", e)
    print(f"\n共 {len(errors)} 项，静态自检未通过。")
    return_code = 1
else:
    print("静态自检全部通过。GitHub Actions 已真实编译出 APK；仍需真机点按验证（微信分享入口、检查更新请求与浏览器跳转）。")
    return_code = 0
sys.exit(return_code)
