# Phone acceptance checks

Status: not yet tested on a physical Android phone. Do not treat synthetic geometry tests as pose-accuracy validation.

Local verification completed for this preview: 8 JUnit tests passed; Android debug assembly and lint passed (lint has warnings, no errors); the packaged model checksum, offline-camera permissions and ARM64 libraries were verified. Device checks below remain pending.

Before production use, compare independent labelled clips with a qualified instructor across body proportions, clothing, skin tones, lighting, supported phones and movement ability. Record false reassurance and false correction rates separately. Thresholds currently use broad heuristic ranges, not clinical or instructor-approved standards.

## Install and camera lifecycle

- Install on ARM64 phone (Android 8.0 or newer); launch in portrait.
- Deny permission; verify explanation, no crash, and recovery using settings.
- Grant permission; start and pause; confirm Android camera indicator turns off on pause and on switching apps / locking the screen.
- Return to app; it should remain paused and require Start.
- Flip cameras; verify mirroring and that skeleton joints follow the image, including near all four edges. Check front-only / rear-only device fallback.
- Rotate the physical phone, return upright and ensure skeleton orientation remains correct. Verify on devices using gesture navigation and display cutouts.

## Framing, rules and hold timing

- Start with no person, then one person, then two; two detected people must not accrue hold time. The two-person detector cannot guarantee detecting every bystander.
- Cut off head, hand or foot; cover a required joint; dim lights. Unknown tracking should stop/reset timing, and stale overlays should disappear.
- In Mountain, stand upright with relaxed arms, then bend a knee, raise an arm or lean sideways. Check cues without forcing the suggested position.
- In Warrior II, test both sides; extend arms, bend one knee, then lower an arm, bend both knees or narrow stance. Confirm broad-pattern feedback.
- Confirm approximate left/right knee labels refer to the person's anatomical sides even with mirrored front preview.
- Matching needs 0.6 seconds of settling before timing. At 10 counted seconds a completion message appears. Any mismatch resets the current hold; best remains for that pose/session. Changing pose resets the best.
- Keep the camera still; do not infer real 3D posture or safe joint loading from green feedback. Check turn-away / oblique false positives explicitly.

## Performance and privacy

- Use airplane mode after installation; tracking must work without downloads.
- Run 10 minutes on low/mid/high tier phones; record analysis FPS, end-to-end delay, heat, battery drain and missed frames. No universal latency/FPS claim is made.
- Cover or interrupt the camera and confirm feedback never continues timing from stale results.
- Check APK manifest contains camera permission only (apart from any normal SDK-added permissions); no Internet, microphone or storage permission should be present.
- Check no image/video files are created, no cloud requests occur, and best hold is cleared after a fresh app process.

## Known limits

Only text feedback is provided. Holding timers require a sample gap no larger than 0.5 seconds; slow devices can therefore struggle to accumulate time. Pose recognition is based on selected 2D checks; it can accept an incorrect pose or reject a reasonable variation. No fall, injury, balance, pain, breathing or basketball analysis exists. Preview/analysis alignment requires physical-device confirmation. Debug signing is specific to the build environment.
