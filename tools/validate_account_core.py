#!/usr/bin/env python3
from pathlib import Path
import runpy
import sys

# Candidate 022 build hook. The workflow already invokes this validator after
# apply_vostok_native_ui_bridge.py, so apply the focused native control patch
# here without duplicating the large Android workflow. Keep this explicit until
# the 022 device smoke test passes, then fold it into the canonical native patch.
control_patch = Path(__file__).with_name("apply_vostok_control_fix_022.py")
if not control_patch.is_file():
    raise SystemExit("missing Candidate 022 control patch")
runpy.run_path(str(control_patch), run_name="__main__")

# Candidate 023: add only the narrow server->native Interaction button transport.
# This deliberately runs after the 022 control fix and does not touch launcher,
# splash/loading screens, HUD layout, auth UI, or gameplay controls.
interaction_patch = Path(__file__).with_name("apply_vostok_interaction_button_023.py")
if not interaction_patch.is_file():
    raise SystemExit("missing Candidate 023 interaction button patch")
runpy.run_path(str(interaction_patch), run_name="__main__")

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("client")
java_root = root / "app/src/main/java/com/blackrussia/game/vostok/account"
required = [
    "AuthProvider.java", "AccountState.java", "AccountModels.java", "AccountGateway.java",
    "SecureSessionStore.java", "HttpAccountGateway.java", "AccountCore.java", "AccountCoreBootstrap.java",
]
errors = []
for name in required:
    if not (java_root / name).is_file():
        errors.append(f"missing {name}")
all_text = "\n".join((java_root / name).read_text(errors="replace") for name in required if (java_root / name).is_file())
checks = {
    "google provider": 'GOOGLE("google")', "vk provider": 'VK("vk")', "yandex provider": 'YANDEX("yandex")',
    "email provider": 'EMAIL("email")', "email OTP request": "/v1/auth/email/request-code",
    "email OTP verify": "/v1/auth/email/verify-code", "provider exchange": "/v1/auth/provider/exchange",
    "session refresh": "/v1/auth/session/refresh", "characters": "/v1/characters", "game ticket": "/v1/game-ticket",
    "character create state": "CHARACTER_CREATION_REQUIRED", "character select state": "CHARACTER_SELECTION_REQUIRED",
    "ready state": "READY_TO_PLAY", "Android Keystore": '"AndroidKeyStore"',
}
for label, needle in checks.items():
    if needle not in all_text:
        errors.append(f"missing contract: {label}")
for forbidden in ["password_hash", "game_password", "setPassword(", "getPassword("]:
    if forbidden in all_text:
        errors.append(f"password flow leaked into Account Core: {forbidden}")
application = root / "app/src/main/java/com/blackrussia/game/vostok/VostokApplication.java"
if not application.is_file():
    errors.append("missing VostokApplication bootstrap")
manifest = root / "app/src/main/AndroidManifest.xml"
if manifest.is_file() and "com.blackrussia.game.vostok.VostokApplication" not in manifest.read_text(errors="replace"):
    errors.append("manifest does not register VostokApplication")
if errors:
    print("VOSTOK Account Core validation: FAIL")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("VOSTOK Account Core validation: PASS")
print(" - passwordless provider model present")
print(" - email OTP contract present")
print(" - silent session recovery foundation present")
print(" - character selection/creation states present")
print(" - one-time game-ticket contract present")
