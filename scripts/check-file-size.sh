#!/usr/bin/env bash
# 单文件体量守卫（CODE-REVIEW-2026-10-28 §5.2 / 第 4 批；2026-11-02 调整口径）。
#
# 为什么存在：CI 里没有 detekt / ktlint / 覆盖率插件，体量这条规范此前只靠人记，
# 与 AGENTS.md「靠人记住的约定必须变成机器断言」冲突。
#
# 口径（2026-11-02 改，理由见 docs/REFACTOR-mainactivity.md 顶部注记）：
#   * 纯 LOC 是**烟感器**，不是设计原则。真正要防的是「一个文件多个变更理由」，
#     所以只用它挡「下一个 MainActivity」，不用它逼人拆小文件。
#   * 硬上限 400：新文件超过就失败（400 行的 Compose 屏 + 状态 + 纯函数基本到顶了）。
#   * 警告线 250：250–400 只打印提醒，不失败 —— 拆不拆看是否搬走了完整概念。
#   * 存量白名单只降不升：白名单里的文件不得超过记录值（防止债务增长）；
#     顺手拆小了**只警告**（记录值留高一点无害，闸门是上界）。
#
# 纯 LOC 口径：排除空行、`//` 行、`/* … */` 块（与 docs/REFACTOR-mainactivity.md 一致）。
# 注意：`/*` 出现在字符串里会被误判为块注释开头 —— Kotlin 里块注释可嵌套，这里
# 用"见到 /* 进块、见到 */ 出块"的简化状态机，够用且可预测。
#
# 用法：
#   scripts/check-file-size.sh            # 校验（CI 跑这个）
#   scripts/check-file-size.sh --update   # 重写白名单记录值（拆小了想同步时跑）
set -euo pipefail

MAX_PURE_LOC=400
WARN_PURE_LOC=250
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/java"
BASELINE="$ROOT/scripts/file-size-baseline.txt"

python3 - "$SRC" "$BASELINE" "$MAX_PURE_LOC" "$WARN_PURE_LOC" "${1:-}" <<'PY'
import pathlib, sys

src, baseline_path = sys.argv[1], sys.argv[2]
max_loc, warn_loc = int(sys.argv[3]), int(sys.argv[4])
update = len(sys.argv) > 5 and sys.argv[5] == "--update"


def pure_loc(path: pathlib.Path) -> int:
    n = 0
    in_block = False
    for raw in path.read_text(encoding="utf-8").splitlines():
        s = raw.strip()
        if in_block:
            if "*/" in s:
                in_block = False
            continue
        if s.startswith("/*"):
            if "*/" not in s:
                in_block = True
            continue
        if not s or s.startswith("//"):
            continue
        n += 1
    return n


files = sorted(
    ((pure_loc(p), str(p.relative_to(src))) for p in pathlib.Path(src).rglob("*.kt")),
    reverse=True,
)
measured = {rel: n for n, rel in files}

baseline = {}
if pathlib.Path(baseline_path).exists():
    for line in pathlib.Path(baseline_path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        n, rel = line.split(None, 1)
        baseline[rel.strip()] = int(n)

if update:
    tracked = {rel: n for rel, n in measured.items() if n > warn_loc}
    lines = [
        "# 纯 LOC 白名单（scripts/check-file-size.sh 维护，勿手改）。",
        "# 格式：<纯 LOC> <相对 app/src/main/java 的路径>",
        "# 规则：不得超过记录值（只降不升）；硬上限见脚本 MAX_PURE_LOC。",
    ]
    lines += [f"{n} {rel}" for rel, n in sorted(tracked.items(), key=lambda kv: -kv[1])]
    pathlib.Path(baseline_path).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"已重写 {baseline_path}（{len(tracked)} 个 >{warn_loc} 纯 LOC 的文件，硬上限 {max_loc}）")
    sys.exit(0)

failures = []
warnings = []
for rel, n in measured.items():
    recorded = baseline.get(rel)
    if recorded is None:
        if n > max_loc:
            failures.append(f"新增超标文件：{rel} = {n} 纯 LOC（硬上限 {max_loc}），请拆分")
        elif n > warn_loc:
            warnings.append(f"{rel} = {n} 纯 LOC（>{warn_loc}）：确认是一个概念；再长就拆")
    elif n > recorded:
        failures.append(f"债务增长：{rel} = {n} 纯 LOC（记录值 {recorded}），请拆分或显式跑 --update")
    elif n < recorded:
        warnings.append(f"{rel} 已缩小到 {n}（记录值 {recorded}），想同步记录值可跑 --update")

for rel in baseline:
    if rel not in measured:
        warnings.append(f"白名单条目已不存在：{rel}，可跑 --update 清理")

over_count = sum(1 for n in measured.values() if n > max_loc)
legacy_over = sum(1 for rel, n in measured.items() if n > max_loc and rel in baseline)
tracked_count = sum(1 for n in measured.values() if n > warn_loc)
print(
    f"检查 {len(measured)} 个 Kotlin 文件：{tracked_count} 个 >{warn_loc} 纯 LOC，"
    f"{over_count} 个 >{max_loc}（其中 {legacy_over} 个是白名单存量，豁免；"
    f"新增文件不得超）"
)

if warnings:
    print("\n".join("  · " + w for w in warnings))

if failures:
    print("\n".join("  ✗ " + f for f in failures))
    sys.exit(1)
print("✓ 单文件体量守卫通过")
PY
