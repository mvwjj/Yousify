#!/usr/bin/env bash
# Stress test: 200 random tracks playback on emulator API 26
set -e
for i in {1..200}; do
  TRACK_ID=$RANDOM
  adb shell am start -n com.veshikov.Yousify/.ui.TracksActivity --es playlist_id "stress_$TRACK_ID"
  sleep 2
  adb shell input tap 400 600 # simulate track click
  sleep 10
  adb shell am force-stop com.veshikov.Yousify
  sleep 1
done
