#!/usr/bin/env bash
# 单文件「≤250 纯 LOC」机器守卫（CODE-REVIEW-2026-10-28 §5.2 / 第 4 批）。
#
# 为什么需要它：全仓 18 个 Kotlin 文件已超 250 纯 LOC，而 CI 里没有 detekt /
# ktlint / 覆盖率插件 —— 这条规范此前只靠人记，与 AGENTS.md「靠人记住的约定
# 必须变成机器断言」直接冲突。
#
# 规则（棘轮，只降不升）：
#   1. 不在白名单里的文件 > MAX_PURE_LOC → 失败（不许新增超标文件）；
#   2. 白名单里的文件 > 记录值 → 失败（想加行就得先显式改白名单，让"加债"出现在 diff 里）；
#   3. 白名单里的文件 ≤ 记录值 → 通过；顺手拆小了请跑 `--update` 把记录值降下来。
#
# 纯 LOC 口径：排除空行、`//` 行、`/* … */` 块（与 docs/REFACTOR-mainactivity.md 一致）。
# 注意：`/*` 出现在字符串里会被误判为块注释开头 —— Kotlin 里块注释可嵌套，这里
# 用"见到 /* 进块、见到 */ 出块"的简化状态机，够用且可预测。
#
# 用法：
#   scripts/check-file-size.sh            # 校验（CI 跑这个）
#   scripts/check-file-size.sh --update   # 重写白名单记录值（拆小了才该跑）
set -euo pipefail

MAX_PURE_LOC=250
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/app/src/main/java"
BASELINE="$ROOT/scripts/file-size-baseline.txt"

python3 - "$SRC" "$BASELINE" "$MAX_PURE_LOC" "${1:-}" <<'PY'
import pathlib, sys

src, baseline_path, max_loc = sys.argv[1], sys.argv[2], int(sys.argv[3])
update = len(sys.argv) > 4 and sys.argv[4] == "--update"


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
    over = {rel: n for rel, n in measured.items() if n > max_loc}
    lines = [
        "# 纯 LOC 白名单（scripts/check-file-size.sh 维护，勿手改）。",
        "# 格式：<纯 LOC> <相对 app/src/main/java 的路径>",
        "# 规则：文件不得超过记录值；拆小了请跑 scripts/check-file-size.sh --update。",
    ]
    lines += [f"{n} {rel}" for rel, n in sorted(over.items(), key=lambda kv: -kv[1])]
    pathlib.Path(baseline_path).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"已重写 {baseline_path}（{len(over)} 个超标文件，上限 {max_loc}）")
    sys.exit(0)

failures = []
for rel, n in measured.items():
    recorded = baseline.get(rel)
    if recorded is None:
        if n > max_loc:
            failures.append(f"新增超标文件：{rel} = {n} 纯 LOC（上限 {max_loc}），请拆分")
    elif n > recorded:
        failures.append(f"债务增长：{rel} = {n} 纯 LOC（记录值 {recorded}），请拆分或显式跑 --update")
    elif n < recorded:
        failures.append(f"已缩小但未记录：{rel} = {n}（记录值 {recorded}），请跑 --update 降下来")

for rel in baseline:
    if rel not in measured:
        failures.append(f"白名单条目已不存在：{rel}，请跑 --update")

over_count = sum(1 for n in measured.values() if n > max_loc)
print(f"检查 {len(measured)} 个 Kotlin 文件，{over_count} 个超过 {max_loc} 纯 LOC（白名单 {len(baseline)} 条）")

if failures:
    print("\n".join("  ✗ " + f for f in failures))
    sys.exit(1)
print("✓ 单文件体积守卫通过")
PY
