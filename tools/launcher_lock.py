#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "launcher" / "APPROVED_LAUNCHER_LOCK.json"
APPROVED_ART = ROOT / "launcher" / "approved" / "VOSTOK_LAUNCHER_BACKGROUND_CLEAN_019.jpg"
OVERLAY_VIEW = ROOT / "overlay" / "app" / "src" / "main" / "java" / "com" / "blackrussia" / "launcher" / "ui" / "VostokLauncherView.java"
CLIENT_VIEW = ROOT / "client" / "app" / "src" / "main" / "java" / "com" / "blackrussia" / "launcher" / "ui" / "VostokLauncherView.java"
CLIENT_RES_DIR = ROOT / "client" / "app" / "src" / "main" / "res" / "drawable-nodpi"
CLIENT_JPG = CLIENT_RES_DIR / "vostok_launcher_reference.jpg"
CLIENT_WEBP = CLIENT_RES_DIR / "vostok_launcher_reference.webp"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def git_blob(path: Path) -> str:
    return subprocess.check_output(["git", "hash-object", str(path)], cwd=ROOT, text=True).strip()


def fail(message: str) -> None:
    raise SystemExit("LAUNCHER LOCK: " + message)


def load_lock():
    if not LOCK_PATH.is_file():
        fail(f"missing lock manifest: {LOCK_PATH}")
    return json.loads(LOCK_PATH.read_text(encoding="utf-8"))


def verify_overlay(lock):
    if not OVERLAY_VIEW.is_file():
        fail("missing canonical VostokLauncherView.java")
    actual = git_blob(OVERLAY_VIEW)
    expected = lock["launcher_view_overlay_git_blob"]
    if actual != expected:
        fail(f"VostokLauncherView.java changed outside launcher-only flow: {actual} != {expected}")


def verify_art(lock):
    if not APPROVED_ART.is_file():
        fail(
            "approved launcher JPEG is not committed. Refusing to build with donor/broken art. "
            "Expected launcher/approved/VOSTOK_LAUNCHER_BACKGROUND_CLEAN_019.jpg"
        )
    actual = sha256(APPROVED_ART)
    expected = lock["approved_background_sha256"]
    if actual != expected:
        fail(f"approved launcher JPEG checksum mismatch: {actual} != {expected}")


def install(lock):
    verify_overlay(lock)
    verify_art(lock)
    CLIENT_RES_DIR.mkdir(parents=True, exist_ok=True)
    CLIENT_JPG.write_bytes(APPROVED_ART.read_bytes())
    if CLIENT_WEBP.exists():
        CLIENT_WEBP.unlink()
    if not CLIENT_VIEW.is_file():
        fail("generated client VostokLauncherView.java missing")
    text = CLIENT_VIEW.read_text(encoding="utf-8")
    old = '"ID: "+c.id+"   •   LVL "+c.level'
    new = '"ID: "+c.id+"   •   УР. "+c.level'
    if old in text:
        if text.count(old) != 1:
            fail("unexpected LVL anchor count")
        text = text.replace(old, new, 1)
        CLIENT_VIEW.write_text(text, encoding="utf-8")
    elif new not in text:
        fail("character level label anchor missing")
    if "BLACK RUSSIA" in text or "mylogo" in text.lower():
        fail("donor launcher token detected in VostokLauncherView.java")
    print("LAUNCHER LOCK: approved Candidate 020/022 launcher installed")
    print("LAUNCHER LOCK: background sha256", sha256(CLIENT_JPG))
    print("LAUNCHER LOCK: launcher view sha256", sha256(CLIENT_VIEW))


def snapshot():
    lock = load_lock()
    verify_overlay(lock)
    verify_art(lock)
    if not CLIENT_JPG.is_file() or not CLIENT_VIEW.is_file():
        fail("client launcher files missing before snapshot")
    data = {
        "jpg": sha256(CLIENT_JPG),
        "view": sha256(CLIENT_VIEW),
    }
    Path("/tmp/vostok_launcher_snapshot.json").write_text(json.dumps(data, sort_keys=True), encoding="utf-8")
    print("LAUNCHER LOCK: snapshot", json.dumps(data, sort_keys=True))


def verify_snapshot():
    snap_path = Path("/tmp/vostok_launcher_snapshot.json")
    if not snap_path.is_file():
        fail("snapshot missing")
    expected = json.loads(snap_path.read_text(encoding="utf-8"))
    current = {
        "jpg": sha256(CLIENT_JPG),
        "view": sha256(CLIENT_VIEW),
    }
    if current != expected:
        fail(f"launcher mutated after freeze: {current} != {expected}")
    print("LAUNCHER LOCK: unchanged after non-launcher patches")


def verify_apk(apk: Path):
    lock = load_lock()
    if not apk.is_file():
        fail(f"APK missing: {apk}")
    import zipfile
    with zipfile.ZipFile(apk, "r") as zf:
        matches = [n for n in zf.namelist() if n.endswith("/vostok_launcher_reference.jpg") or n == "res/drawable-nodpi/vostok_launcher_reference.jpg"]
        if len(matches) != 1:
            fail(f"APK must contain exactly one launcher JPEG, found: {matches}")
        raw = zf.read(matches[0])
        actual = hashlib.sha256(raw).hexdigest()
        expected = lock["approved_background_sha256"]
        if actual != expected:
            fail(f"APK launcher JPEG mismatch: {actual} != {expected}")
        if any(n.endswith("/vostok_launcher_reference.webp") for n in zf.namelist()):
            fail("APK contains forbidden launcher WebP")
    print("LAUNCHER LOCK: final APK contains exact approved launcher JPEG")


def main():
    if len(sys.argv) < 2:
        fail("usage: launcher_lock.py install|snapshot|verify-snapshot|verify-apk <apk>")
    cmd = sys.argv[1]
    lock = load_lock()
    if cmd == "install":
        install(lock)
    elif cmd == "snapshot":
        snapshot()
    elif cmd == "verify-snapshot":
        verify_snapshot()
    elif cmd == "verify-apk":
        if len(sys.argv) != 3:
            fail("verify-apk requires APK path")
        verify_apk(Path(sys.argv[2]))
    else:
        fail(f"unknown command: {cmd}")


if __name__ == "__main__":
    main()
