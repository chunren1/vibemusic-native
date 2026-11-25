#!/usr/bin/env bash
# 禁止在 UI 层直接写颜色字面量：所有颜色必须来自主题 token（UiTheme.kt）
# 或语义色（UiAtoms.kt 的 UiFavRed 等）。
#
# 为什么要有它：2026-11 加主题时才发现同一份色板被复制了 5 份、
# 29 处字面量里一半是主题色副本，切主题时这些地方不会变。
set -u
ROOT="${1:-app/src/main/java/com/cyk666/vibemusic}"
ALLOW=("UiTheme.kt" "UiAtoms.kt")
bad=0

while IFS= read -r line; do
  file=${line%%:*}
  base=$(basename "$file")
  for a in "${ALLOW[@]}"; do
    [ "$base" = "$a" ] && continue 2
  done
  echo "  ✗ $line"
  bad=$((bad + 1))
done < <(grep -rn "Color(0x" "$ROOT" --include="*.kt" 2>/dev/null)

if [ "$bad" -gt 0 ]; then
  echo "发现 $bad 处裸色值。请改用主题 token（ObsidianBg/UiAccent/InkOnDark…）"
  exit 1
fi
echo "✓ 颜色来源守卫通过（无裸色值）"
