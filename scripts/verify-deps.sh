#!/usr/bin/env bash
# 依赖约束守卫：断言 androidx.media3 的 AAR 元数据要求不超过本项目的编译配置。
#
# 为什么需要这个脚本（2026-10-26 教训）：
#   曾经凭记忆断言「media3 1.9.1+ 要求 compileSdk 36，所以工具链卡住」，实际解包 AAR 发现
#   1.9.0/1.9.1/1.9.2/1.9.3 全是 minCompileSdk=35，只有 1.10.0 起才是 36。
#   一个假前提让「工具链冻结」这个决定成立了好几周。教训：依赖的版本约束必须由机器去读制品，
#   不能靠人回忆。本脚本就是那条机器断言 —— 升 media3 时如果要求变了，CI 会直接红并说清要改什么。
#
# 用法：bash scripts/verify-deps.sh
# 退出码：0 通过；1 违反约束；2 环境/网络问题（不静默通过）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSIONS="$ROOT/gradle/libs.versions.toml"
BUILD="$ROOT/app/build.gradle.kts"
CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/vibemusic-native/deps-guard"

die() { echo "  !! $*" >&2; exit 2; }

# ---------- 1. 读项目侧配置（唯一来源） ----------
[ -f "$VERSIONS" ] || die "找不到 $VERSIONS"
[ -f "$BUILD" ] || die "找不到 $BUILD"

MEDIA3_VERSION="$(sed -n 's/^media3 *= *"\([^"]*\)".*/\1/p' "$VERSIONS" | head -1)"
COMPILE_SDK="$(sed -n 's/^ *compileSdk *= *\([0-9]\+\).*/\1/p' "$BUILD" | head -1)"
MIN_SDK="$(sed -n 's/^ *minSdk *= *\([0-9]\+\).*/\1/p' "$BUILD" | head -1)"

[ -n "$MEDIA3_VERSION" ] || die "无法从 libs.versions.toml 解析 media3 版本"
[ -n "$COMPILE_SDK" ] || die "无法从 app/build.gradle.kts 解析 compileSdk"
[ -n "$MIN_SDK" ] || die "无法从 app/build.gradle.kts 解析 minSdk"

echo "项目配置：media3=$MEDIA3_VERSION  compileSdk=$COMPILE_SDK  minSdk=$MIN_SDK"

# ---------- 2. 拉取该版本 AAR 并读元数据 ----------
# media3-exoplayer 与 media3-session 的 minCompileSdk 一致，取 exoplayer 作代表；
# 若两者不一致，下面会分别校验。
mkdir -p "$CACHE"
FAILED=0

read_meta() {  # $1=artifact  -> 打印 minCompileSdk=NN
  local art="$1"
  local aar="$CACHE/${art}-${MEDIA3_VERSION}.aar"
  local url="https://dl.google.com/dl/android/maven2/androidx/media3/${art}/${MEDIA3_VERSION}/${art}-${MEDIA3_VERSION}.aar"

  if [ ! -f "$aar" ]; then
    echo "  下载 $art:$MEDIA3_VERSION ..." >&2
    curl -fsSL --retry 3 --connect-timeout 15 -o "$aar.part" "$url" \
      || die "下载失败：$url（网络问题，不是配置问题；CI 需能访问 dl.google.com）"
    mv "$aar.part" "$aar"
  fi

  python3 - "$aar" <<'PY'
import sys, zipfile
path = sys.argv[1]
with zipfile.ZipFile(path) as z:
    name = "META-INF/com/android/build/gradle/aar-metadata.properties"
    try:
        raw = z.read(name).decode("utf-8")
    except KeyError:
        sys.exit(f"  !! {path} 里没有 {name}（元数据格式变了？）")
props = {}
for line in raw.splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
print(f"minCompileSdk={props.get('minCompileSdk', '')}")
PY
}

for ART in media3-exoplayer media3-session; do
  META="$(read_meta "$ART")" || exit 2
  REQ_COMPILE="$(echo "$META" | sed -n 's/^minCompileSdk=\([0-9]\+\)$/\1/p')"
  [ -n "$REQ_COMPILE" ] || die "$ART 的 aar-metadata.properties 里没有可解析的 minCompileSdk（原始：$META）"

  printf "  %-18s minCompileSdk=%s  (项目 compileSdk=%s) " "$ART" "$REQ_COMPILE" "$COMPILE_SDK"
  if [ "$REQ_COMPILE" -le "$COMPILE_SDK" ]; then
    echo "OK"
  else
    echo "违反"
    FAILED=1
  fi
done

if [ "$FAILED" -ne 0 ]; then
  cat >&2 <<EOF

  ============================================================
  media3 $MEDIA3_VERSION 要求 compileSdk ≥ $REQ_COMPILE，本项目是 $COMPILE_SDK。
  升 compileSdk 不是改一个数字，必须同时满足：
    · AGP 最低版本（API 36 需 AGP ≥ 8.9.1，但 8.9 官方只声明支持到 35，稳妥用 8.11.1+）
      https://developer.android.com/build/releases/about-agp
    · 对应 AGP 要求的 Gradle 版本与 JDK（AGP 8.11/8.13 都是 Gradle 8.13 + JDK 17）
    · Kotlin 版本要在 KGP 兼容表内（2.0.20 官方上限是 AGP 8.5 / Gradle 8.8）
      https://kotlinlang.org/docs/gradle-configure-project.html
  另注意 targetSdk 与 compileSdk 是两件事：targetSdk 36 会强制 edge-to-edge、
  默认开启 predictive back、大屏忽略方向限制，属于独立工程，别混在一次改动里。
  ============================================================
EOF
  exit 1
fi

echo "依赖约束校验通过（media3 $MEDIA3_VERSION 与 compileSdk $COMPILE_SDK 兼容）"
