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

print(f"XML {len(xmls)} · Kotlin {len(kts)} · 布局 {len(layouts)} · id {len(ids)} · drawable {len(drawables)}")
if errors:
    print("\n发现问题：")
    for e in errors:
        print("  -", e)
    print(f"\n共 {len(errors)} 项，静态自检未通过。")
    return_code = 1
else:
    print("静态自检全部通过。仍需用 Android Studio 真实编译并在真机上点按验证。")
    return_code = 0
sys.exit(return_code)
