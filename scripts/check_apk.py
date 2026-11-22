"""Verify offline-camera privacy contract and bundled model integrity in a built APK."""
import hashlib
from pathlib import Path
import subprocess
import sys
import zipfile

aapt, apk = sys.argv[1:]
permissions = subprocess.check_output([aapt, "dump", "permissions", apk], text=True)
for forbidden in (
    "android.permission.INTERNET",
    "android.permission.RECORD_AUDIO",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
):
    assert forbidden not in permissions, f"Unexpected permission: {forbidden}"
assert "android.permission.CAMERA" in permissions
with zipfile.ZipFile(apk) as archive:
    model = archive.read("assets/pose_landmarker_lite.task")
    assert hashlib.sha256(model).hexdigest() == "59929e1d1ee95287735ddd833b19cf4ac46d29bc7afddbbf6753c459690d574a"
    assert any(name.startswith("lib/arm64-v8a/") for name in archive.namelist()), "ARM64 native libraries missing"
print(f"Verified camera/offline permissions, bundled model and ARM64 libraries: {Path(apk).name}")
