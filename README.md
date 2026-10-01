# Milo

**Your little sous chef.** A friendly nudge when the stove's still on.

Milo is an open-source Android safety guardrail that combines local room recognition with a stove-state tracker. It can remind you when the stove appears to be on after you have moved somewhere else in your home.

This is an early proof of concept. It is intended as an extra reminder, not as a replacement for attentive cooking or certified safety equipment.

## Implemented

- Sensor Explorer with live phone sensor readings, orientation, sampling frequency, movement state, timestamp, and a selectable 10-second chart.
- Arbitrary zones, 30-second repeatable training visits, separate raw sessions, and overlapping 2-second windows at 500 ms steps.
- Local feature extraction summarizes sensor readings, motion, and phone orientation for room recognition.
- Replaceable normalized weighted k-nearest-neighbor classification with a distance-derived Unknown probability.
- Raw, exponentially smoothed, and active predictions with configurable confidence, dwell, and release hysteresis.
- Independent timed validation with accuracy, confidence, bad transitions, recognition latency, Unknown time, and repeated history.
- Raw distributions, a 2D feature plot, validation confusion matrix, and raw/window CSV export.
- Warm dark home screen with persistent reminder cards, a focused reminder composer, and stove-specific starter ideas. Locations, recognition, sensors, experiments, and diagnostics live under Settings.
- Focused stove-reminder prompts with optional names, editing, pause controls, and deletion. New installs include an enabled Couch stove reminder; deleting it does not recreate it. Existing reminders and training data survive the upgrade.

All Milo data stays in the app's private local storage. The app uses local-network access to read stove state from Camera Tracker; it does not send Milo data to a cloud service.

In the prompt editor, type `@` (or tap its `@` button) to choose a saved room, Current room, or Stove state. Keep typing to filter the dropdown. Selected mentions are highlighted and persist with their room/value IDs; editing inside a mention unlinks it, and a removed room is flagged in the editor. These references help define a rule but do not activate execution.

Reminders containing one linked room and `@Stove state` with an ON condition run locally in a foreground monitor. Milo polls Camera Tracker at `192.168.50.207:8000`, recognizes the linked room from trained sensor data, and posts one Android alert when both conditions become true. It will retry an unavailable tracker and suppress repeat alerts for 30 minutes. Other prompts are saved as drafts.

Open Milo once after installing or restarting the phone, allow notifications, and leave the enabled rule on. Android displays a low-priority persistent notification while location rules are being monitored. The tracker URL is restricted to the current home-LAN address; update `STOVE_TRACKER_URL` and the matching network-security domain if that computer's DHCP address changes.

## Architecture

```text
AndroidSensorCollector : SensorCollector
             ↓
       RawSensorSample
             ↓
      FeatureExtractor
             ↓
 LocationClassifier (NormalizedKnnClassifier)
             ↓
      TemporalSmoother
             ↓
     SmoothedDetection
```

`ZoneRepository` isolates persistence. Training and experiment sessions are different stored types, so validation data never becomes training data. The classifier can be replaced without touching sensors, storage, or UI.

Key files:

- `sensor/AndroidSensorCollector.kt` — real `SensorManager` integration; no production mocks.
- `processing/FeatureExtractor.kt` — raw-to-window boundary.
- `classification/LocationClassifier.kt` — k-NN and Unknown probability.
- `classification/TemporalSmoother.kt` — EMA, dwell, and release hysteresis.
- `experiment/ExperimentAnalytics.kt` — metrics and confusion matrix.
- `data/FileZoneRepository.kt` — atomic local JSON persistence.
- `export/CsvExporter.kt` — raw and feature-window datasets.

## Build and install

The app targets Android 16 / API 36 and supports API 26 and newer.

```powershell
./gradlew.bat test
./gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The built APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Pixel hardware smoke test

1. Enable Developer options and USB debugging, connect the Pixel, approve the computer, and run `adb devices -l`.
2. Install the debug APK and open **Milo**.
3. Open **Settings > Sensors** and confirm the four sensors show `available` (rotation vector may be absent on unusual hardware).
4. Confirm the live sensor readings respond when you move and rotate the phone. Do not validate against one expected reading.
5. Leave the phone on a stable surface until the UI says `STATIONARY`; pick it up and rotate it to confirm `MOVING`.
6. Confirm the chart covers roughly ten seconds and its selectable sensor channels show different traces.
7. Background and reopen the app. Sampling should pause while backgrounded and resume when visible.

## First home dataset protocol

Keep the phone case and accessories consistent throughout the first dataset.

1. Add Changing Table, Feeding Chair, Bottle Station, and Bassinet.
2. Training pass 1: visit each zone once. Use a natural grip and small portrait/landscape/face-up rotations.
3. Wait at least 15 minutes and take training pass 2 in a different order. Across the passes include held and surface-resting visits.
4. Do not train again yet. Run three independent 30-second tests per zone, interleaved rather than grouped.
5. Test the closest adjacent pair and one neutral, untrained area. The neutral area should mostly become Unknown.
6. Repeat at a different time and once with nearby appliances in their normal alternate state.
7. Inspect the confusion matrix and scatter plot, then export both CSV files.

Treat the hypothesis as supported only if independent tests show at least 90% correct active-zone time, low false switching, recognition near 3 seconds, and robustness across normal orientations. Preserve failed tests; overlap is the useful diagnostic result.

## Evidence boundary

Compilation, lint, APK assembly, and non-hardware unit tests run locally. Real sensor behavior cannot be verified without a connected physical Pixel; an emulator is not a substitute for testing room recognition in the actual home.
