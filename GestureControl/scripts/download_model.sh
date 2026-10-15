#!/usr/bin/env bash
# 下载 MediaPipe 手部关键点模型到 assets 目录
# 用法：bash scripts/download_model.sh
set -euo pipefail

DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"
MODEL_URL="https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"

mkdir -p "$DEST"
echo "下载模型 → $DEST/hand_landmarker.task"
if command -v curl >/dev/null 2>&1; then
  curl -fL -o "$DEST/hand_landmarker.task" "$MODEL_URL"
elif command -v wget >/dev/null 2>&1; then
  wget -O "$DEST/hand_landmarker.task" "$MODEL_URL"
else
  echo "需要 curl 或 wget" >&2
  exit 1
fi

SIZE=$(wc -c < "$DEST/hand_landmarker.task" | tr -d ' ')
echo "完成：${SIZE} bytes"
if [ "$SIZE" -lt 1000000 ]; then
  echo "警告：文件过小，可能下载失败，请手动从 $MODEL_URL 下载" >&2
  exit 1
fi
