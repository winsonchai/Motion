# Motion — Android yoga preview

An English-language Android app for **Mountain** and **Warrior II** practice. Version 0.1.0 targets ARM / ARM64 phones running Android 8.0+ (API 26), with Android 15 / API 35 build tools. x86 emulators are not included in this phone preview.

## What works in this version

- Live front/back camera, with a mirrored front preview and aligned skeleton overlay.
- MediaPipe Pose Landmarker Lite runs on the phone. The model is bundled into the APK.
- Broad 2D checks for framing, visibility, front-facing torso, shoulder level, torso alignment, knee bends and arm position.
- One text cue at a time. Amber means adjustment / uncertain tracking; green means within the experimental practice range.
- A 10-second hold goal, with a 0.6-second settling window. A nonmatching frame or a gap over 0.5 seconds resets the current hold. A separate watchdog clears stale feedback. Best hold is kept in memory for the current pose/session.
- Pause, camera permission recovery, camera switching and automatic camera shutdown in the background.
- **No accounts, runtime network permission, video recording, image storage or upload.**

These are experimental camera estimates, **not confirmation of correct or safe technique**. The rules have not been validated by a yoga instructor. Single-camera projection can hide alignment errors and cannot measure joint loading. Choose a comfortable pose and stop if uncomfortable.

## Try it

1. Install the debug APK and grant camera permission when you tap **Start practice**.
2. Fix the phone upright on a stable support, with even lighting. Keep head, hands and feet visible with margin, and only one person in view.
3. Choose **Mountain** or **Warrior II**. Face the chest toward the camera; the on-screen instructions explain the stance.
4. Step back until tracking is clear. The timer starts after stable matching, and resets when the pose changes or tracking is unclear.
5. Tap the privacy / estimate note for the limitations. **Pause practice** releases the camera.

For Warrior II, either leg may be the bent front leg. Do not force a knee angle to satisfy the app. The first version does not evaluate foot rotation, knee-over-toe alignment, spinal curvature, balance, injury risk or breathing.

Basketball, recorded-video import, voice coaching, session history and custom/calibrated thresholds are outside this first version.

## Build

Use JDK 17 and Android SDK Platform 35 / Build Tools 35.0.0. Open this directory in Android Studio or set `ANDROID_HOME` and run:

```sh
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

The pinned Gradle 8.11.1 wrapper verifies its distribution checksum. The first Android build downloads the versioned Google Pose Landmarker Lite model, verifies SHA-256 `59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a`, then packages it. Subsequent builds reuse the verified local model. This is build-time networking only.

APK: `app/build/outputs/apk/debug/app-debug.apk`. This is a development build signed using the builder's Android debug key. Updates must use that same key; a different builder's debug APK may require uninstalling first. Release signing and Play Store publication are not configured.

To reproduce the distributed preview's signature, set `MOTION_PREVIEW_KEYSTORE` to its privately retained preview keystore before building (standard test alias/password only). Never commit signing keys. This is not a production signing configuration.

GitHub Actions builds a debug APK and uploads the APK and check reports for each push / pull request. Artifact availability depends on the workflow completing successfully.

## Structure and checks

- `core/`: Android-independent geometry, framing/visibility gates, pose rules and hold state; JUnit tests cover occlusion, cropping, nonfinite landmarks, camera aspect scaling, view rejection, both pose patterns, stale frames, settling and goal timing.
- `app/`: CameraX lifecycle, frame conversion/rotation, on-device inference, display, camera permission handling and stale-result rejection.
- `docs/device-checks.md`: required phone checks and known limits. Passing unit tests and compiling do not establish real-world accuracy.

## Third-party components

- [MediaPipe](https://github.com/google-ai-edge/mediapipe) and [Pose Landmarker documentation](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker).
- [Google-hosted model, version 1](https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task).
- [CameraX](https://developer.android.com/media/camera/camerax) and AndroidX.
- [Gradle wrapper](https://github.com/gradle/gradle), distributed under Apache License 2.0; see `gradle/wrapper/LICENSE`.

Review upstream SDK and model terms before distributing a production product. No license for the app's own source is granted by this preview.
