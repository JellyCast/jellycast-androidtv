#!/bin/zsh
# Measures janky frames during 30 s of real key repeat on the Chromecast (release build).
#
# Usage: scripts/perf/measure-scroll.sh [left-right|rows]
#   left-right  alternately holds → and ← (bookcase, rows)
#   rows        alternately holds ↓, ↑, → and ← (Series)
#
# Put the focus where you want to measure first. The script creates a virtual keyboard via
# `uinput`, so Android generates the key repeat itself (after 400 ms, every 50 ms), like the
# remote. `adb shell input keyevent` is unsuitable: it sends keys without pauses, so several
# steps land in one frame.
set -e
mode=${1:-left-right}
here=${0:A:h}
adb push "$here/keyrepeat-$mode.jsonl" /data/local/tmp/keyrepeat.jsonl >/dev/null
adb shell dumpsys gfxinfo nl.jellycast.tv reset >/dev/null
adb shell 'uinput - < /data/local/tmp/keyrepeat.jsonl' >/dev/null 2>&1
adb shell dumpsys gfxinfo nl.jellycast.tv |
    grep -E "Total frames|Janky frames:|50th percentile|90th percentile|99th percentile|50th gpu|90th gpu"
