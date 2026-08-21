# IntervalTimer_v1

The Android project lives in `/home/runner/work/IntervalTimer_v1/IntervalTimer_v1/IntervalTimer`.

## Modules

- `IntervalTimer/app` — phone app
- `IntervalTimer/wear` — standalone Wear OS app
- `IntervalTimer/shared` — shared timer/state logic

## Build

From the repository root:

```bash
cd /home/runner/work/IntervalTimer_v1/IntervalTimer_v1/IntervalTimer
gradle :shared:test
gradle :app:assembleDebug
gradle :wear:assembleDebug
```
