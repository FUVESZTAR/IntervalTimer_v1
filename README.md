# IntervalTimer_v1

The Android project lives in `IntervalTimer/`.

## Modules

- `IntervalTimer/app` — phone app
- `IntervalTimer/wear` — standalone Wear OS app
- `IntervalTimer/shared` — shared timer/state logic

## Build

From the repository root:

```bash
cd IntervalTimer
gradle :shared:test
gradle :app:assembleDebug
gradle :wear:assembleDebug
```
