#!/usr/bin/env python3
"""从 ipa-dict 的两份原始 txt 重建 app/src/main/assets/ipa.tsv。

用法（在工程根目录）：
    python3 tools/build_ipa.py en_US.txt en_UK.txt

原始数据：
    https://raw.githubusercontent.com/open-dict-data/ipa-dict/master/data/en_US.txt
    https://raw.githubusercontent.com/open-dict-data/ipa-dict/master/data/en_UK.txt

许可与修改说明见 LICENSES/THIRD_PARTY_NOTICES.md。

v7 变更（对照同行做法后的修正）：
  1. 多读音不再只取第一条后丢弃 —— 同一个词有多个 IPA 时全部保留，
     用 ", " 连接，避免用词典顺序冒充「唯一正确读音」；
  2. 词形归一与 IpaDictionary.lookup() 完全一致（NFC / 小写 / 去首尾标点），
     避免构建期与运行时归一不一致导致查不到；
  3. 输出上游 SHA-256 与覆盖率统计，便于复核数据来源。
"""
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/ipa.tsv"
STATS = ROOT / "app/src/main/assets/ipa_stats.json"

# 与 IpaDictionary.normalize() 保持一致
STRIP = ' \t"\'.,;:!?()[]{}'


def normalize(word: str) -> str:
    return unicodedata.normalize("NFC", word.strip().lower()).strip(STRIP)


def wrap_ipa(ipa: str) -> str:
    """多读音行如 `/ˈskɛdʒuɫ/, /ˈskɛdʒʊɫ/`：逐个补 /…/，保留全部候选。"""
    parts = [p.strip() for p in re.split(r"\s*,\s*", ipa.strip()) if p.strip()]
    out = []
    for p in parts:
        # 源数据里既有 `/ˈeɪ/, /ə/`，也有 `/ðə, ði/`（一个斜杠内塞多个读音）。
        # 只有统一「去两端斜杠 → 再包回一对」才能保证每个候选都是完整 /…/，
        # 否则会产出 `/ðə, /ði/` 这种缺闭合斜杠的坏音标。
        p = p.strip().strip("/").strip()
        if not p:
            continue
        p = "/" + p + "/"
        p = p.replace("\n", " ").replace("\t", " ").strip()
        if p not in out:
            out.append(p)
    return ", ".join(out)


def load(path):
    table = {}
    variants = 0
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if "\t" not in line:
            continue
        word, ipa = line.split("\t", 1)
        word = normalize(word)
        ipa = wrap_ipa(ipa)
        if not word or not ipa or "\t" in word or "\n" in word:
            continue
        if word not in table:
            table[word] = ipa
        else:
            # 同一个词在源数据里出现多行：合并候选，不覆盖已有
            merged = [p.strip() for p in (table[word] + ", " + ipa).split(", ") if p.strip()]
            seen = []
            for p in merged:
                if p not in seen:
                    seen.append(p)
            table[word] = ", ".join(seen)
            variants += 1
    return table, variants


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 1
    us, us_var = load(sys.argv[1])
    uk, uk_var = load(sys.argv[2])
    keys = sorted(set(us) | set(uk))
    lines = [f"{k}\t{uk.get(k, '')}\t{us.get(k, '')}" for k in keys if k]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    stats = {
        "source": "open-dict-data/ipa-dict data/en_UK.txt (RP) + data/en_US.txt (General American)",
        "license": "see LICENSES/THIRD_PARTY_NOTICES.md",
        "entries": len(lines),
        "both_accents": sum(1 for k in keys if k in uk and k in us),
        "uk_only": sum(1 for k in keys if k in uk and k not in us),
        "us_only": sum(1 for k in keys if k in us and k not in uk),
        "multi_pronunciation_rows": sum(
            1 for k in keys if ", " in (uk.get(k, "") + us.get(k, ""))
        ),
        "merged_duplicate_source_rows": us_var + uk_var,
        "tsv_bytes": OUT.stat().st_size,
        "upstream_sha256": {
            Path(sys.argv[1]).name: sha256(sys.argv[1]),
            Path(sys.argv[2]).name: sha256(sys.argv[2]),
        },
        "note": "多读音全部保留（v6 曾只取第一条）；词形归一与运行时一致",
    }
    STATS.write_text(json.dumps(stats, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(stats, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
