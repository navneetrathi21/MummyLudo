#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/play-store-assets/video"
TMP_DIR="${OUT_DIR}/tmp"
mkdir -p "${TMP_DIR}"

make_slide() {
  local input="$1"
  local seconds="$2"
  local output="$3"
  ffmpeg -y -loop 1 -t "${seconds}" -i "${input}" \
    -vf "scale=1080:2400,crop=1080:1920:0:240" \
    -r 30 -pix_fmt yuv420p -c:v libx264 "${output}"
}

make_clip() {
  local input="$1"
  local seconds="$2"
  local output="$3"
  ffmpeg -y -stream_loop -1 -i "${input}" -t "${seconds}" \
    -vf "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920" \
    -r 30 -pix_fmt yuv420p -c:v libx264 "${output}"
}

make_slide "${ROOT}/play-store-assets/phone-screenshots/01-home.png" 3 "${TMP_DIR}/01-home.mp4"
make_slide "${ROOT}/play-store-assets/phone-screenshots/02-match-setup.png" 3 "${TMP_DIR}/02-setup.mp4"
make_slide "${ROOT}/play-store-assets/phone-screenshots/04-dice-roll.png" 3 "${TMP_DIR}/03-dice.mp4"
make_clip "${ROOT}/artifacts/screenshots/token-movement.mp4" 6 "${TMP_DIR}/04-move.mp4"
make_slide "${ROOT}/play-store-assets/phone-screenshots/06-arrow-move.png" 3 "${TMP_DIR}/05-arrow.mp4"
make_slide "${ROOT}/play-store-assets/phone-screenshots/07-bot-turn.png" 2 "${TMP_DIR}/06-bot.mp4"

cat > "${TMP_DIR}/concat.txt" <<LIST
file '${TMP_DIR}/01-home.mp4'
file '${TMP_DIR}/02-setup.mp4'
file '${TMP_DIR}/03-dice.mp4'
file '${TMP_DIR}/04-move.mp4'
file '${TMP_DIR}/05-arrow.mp4'
file '${TMP_DIR}/06-bot.mp4'
LIST

ffmpeg -y -f concat -safe 0 -i "${TMP_DIR}/concat.txt" \
  -c:v libx264 -pix_fmt yuv420p -movflags +faststart \
  -t 20 "${OUT_DIR}/mummy-ludo-gameplay-20s.mp4"

rm -rf "${TMP_DIR}"
echo "${OUT_DIR}/mummy-ludo-gameplay-20s.mp4"
