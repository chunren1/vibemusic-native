#!/usr/bin/env bash
# 分层红线守卫（2026-11-02）。AGENTS.md 写了「分层 data → business → presentation，
# 禁止跨层（UI 不得直连网络/存储）」，但此前**没有任何机器检查** —— 又一条
# 「靠人记住的约定」，与全局规范「靠人记住的约定必须变成机器断言」冲突。
#
# 这条比「单文件 ≤N 行」更接近真正要防的东西：耦合。行数只是烟感器
# （见 scripts/check-file-size.sh 顶部注记），而「界面层自己开线程读盘、
# 自己拼请求」才是会让测试无法覆盖、让改动牵连一片的结构问题。
#
# 检查范围：`app/src/main/java/com/cyk666/vibemusic/` 下的
#   *Screen.kt / *Ui.kt / UiAtoms.kt
# 只在这些文件里禁止（`MainActivity.kt` 是 composition root，允许做 IO 与调 API；
# `*Store.kt` / `VibeApi.kt` / `MediaCache.kt` 是 data 层，本来就该碰）。
#
# 禁止项（跳过 import / package 行，避免把类型引用当调用）：
#   - Dispatchers.IO / Default / Unconfined  —— 界面层自己切线程
#   - File(...) / filesDir / cacheDir         —— 界面层自己摸磁盘
#   - VibeApi.xxx(...)                        —— 界面层直接发请求（VibeApi.Xxx 类型引用允许）
#   - OfflineStore.dir/audioFile/delete/...   —— 绕过 Store 直接操作文件
#   - OkHttpClient(...) / Retrofit.Builder    —— 界面层自建网络栈
#
# 用法：scripts/check-layering.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/java/com/cyk666/vibemusic"

python3 - "$SRC" <<'PY'
import pathlib, re, sys

src = pathlib.Path(sys.argv[1])
targets = sorted(
    p for p in src.glob("*.kt")
    if p.name.endswith("Screen.kt") or p.name.endswith("Ui.kt") or p.name == "UiAtoms.kt"
)

rules = [
    (re.compile(r"Dispatchers\.(IO|Default|Unconfined)"), "界面层自己切线程（应由调用方在 IO 上算好传进来）"),
    (re.compile(r"\bFile\s*\("), "界面层自己构造 File（拿 Context 读盘请走 Store）"),
    (re.compile(r"\b(filesDir|cacheDir|externalCacheDir)\b"), "界面层直接摸应用目录"),
    (re.compile(r"VibeApi\.[a-z]\w*\s*\("), "界面层直接调 API（应通过回调/状态传入）"),
    (re.compile(r"OfflineStore\.(dir|audioFile|delete|listDownloads|save)\b"), "界面层绕过 OfflineStore 的封装"),
    (re.compile(r"\bOkHttpClient\s*\(|Retrofit\.Builder"), "界面层自建网络栈"),
]

violations = []
for p in targets:
    for no, raw in enumerate(p.read_text(encoding="utf-8").splitlines(), start=1):
        s = raw.strip()
        if not s or s.startswith("//") or s.startswith("import ") or s.startswith("package "):
            continue
        for pattern, why in rules:
            if pattern.search(s):
                violations.append(f"{p.name}:{no}  {why}\n      {s[:120]}")

print(f"检查 {len(targets)} 个界面层文件（*Screen.kt / *Ui.kt / UiAtoms.kt）")
if violations:
    print("\n".join("  ✗ " + v for v in violations))
    print(f"\n共 {len(violations)} 处跨层。修法：把 IO/请求移到 MainActivity 或 Store，界面只收结果。")
    sys.exit(1)
print("✓ 分层守卫通过")
PY
