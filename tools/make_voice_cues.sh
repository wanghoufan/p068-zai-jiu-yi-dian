#!/usr/bin/env bash
# P068 离线语音提示资产生成（macOS，全程不联网）
# 产物：app/src/main/res/raw/cue_*.wav
# 依赖：macOS `say`（Tingting 中文语音）、ffmpeg
# 用法：bash tools/make_voice_cues.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
RAW="$HERE/app/src/main/res/raw"
TMP="$(mktemp -d)"
SR=22050
VOICE="Tingting"

mkdir -p "$RAW"

trim_filter="silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,silenceremove=start_periods=1:start_threshold=-45dB:start_silence=0.05,areverse,loudnorm=I=-16:TP=-1.5:LRA=11"

render() { # $1=text $2=out
  local text="$1" out="$2"
  say -v "$VOICE" -o "$TMP/raw.aiff" "$text"
  ffmpeg -hide_banner -loglevel error -y -i "$TMP/raw.aiff" \
    -af "$trim_filter" -ar "$SR" -ac 1 -c:a pcm_s16le "$out"
}

render "开始训练" "$RAW/cue_start.wav"
render "已达到目标，可以继续" "$RAW/cue_target.wav"

for m in $(seq 3 3 99); do
  render "已经训练${m}分钟" "$RAW/cue_interval_${m}.wav"
done
render "已经训练超过一百分钟，继续保持" "$RAW/cue_interval_100.wav"

echo "== 时长核对（秒）=="
for f in "$RAW"/cue_start.wav "$RAW"/cue_target.wav "$RAW"/cue_interval_3.wav "$RAW"/cue_interval_99.wav "$RAW"/cue_interval_100.wav; do
  d=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$f")
  printf "  %-24s %s s\n" "$(basename "$f")" "$d"
done
rm -rf "$TMP"
echo "== 完成：$(ls -1 "$RAW"/cue_*.wav | wc -l | tr -d ' ') 个文件，$(du -sh "$RAW" | cut -f1) =="
