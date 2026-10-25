#!/bin/zsh
# Measures janky frames in the "steady" state: how the app runs after a day of use.
#
# Usage: scripts/perf/measure-steady.sh <apk> [rows|left-right]
#
# Right after `adb install` the Compose code runs in the interpreter: Android only compiles after
# use (JIT profile) during overnight maintenance, so measuring right after installing reports
# twice the jank. This script installs, uses the app first (builds the profile), compiles the way
# maintenance does (speed-profile) and then measures 3x. Before measuring, put the app where you
# want to measure (e.g. the Series section) with the focus on item 1.
set -e
apk=$1
mode=${2:-rows}
here=${0:A:h}
adb install -r "$apk" | tail -1
adb shell am force-stop nl.jellycast.tv
adb shell am start -W -n nl.jellycast.tv/.MainActivity >/dev/null
sleep 12
for i in 1 2; do "$here/measure-scroll.sh" "$mode" >/dev/null 2>&1; done
# ART writes the JIT profile out on its own; wait for it, then compile like maintenance does.
sleep 20
adb shell cmd package compile -m speed-profile -f nl.jellycast.tv >/dev/null
adb shell am force-stop nl.jellycast.tv
adb shell am start -W -n nl.jellycast.tv/.MainActivity | grep TotalTime
sleep 6
for i in 1 2 3; do
    "$here/measure-scroll.sh" "$mode" 2>/dev/null | grep -E "Janky frames:|50th percentile|50th gpu" |
        tr '\n' ' '
    echo
    sleep 2
done
