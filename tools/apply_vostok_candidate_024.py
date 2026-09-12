#!/usr/bin/env python3
from pathlib import Path

# Candidate 024 is deliberately a narrow presentation/localization pass.
# It does not touch the speedometer, vehicle controls, account transport,
# or the frozen VOSTOK launcher main screen.


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if text.count(old) != 1:
        raise SystemExit(f"Candidate 024 {label} anchor mismatch in {path}: {text.count(old)}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


interaction = Path("client/app/src/main/java/com/blackrussia/game/vostok/ui/InteractionUiManager.java")
replacements = [
    ("new FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER)",
     "new FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER)", "interaction action size"),
    ("new FrameLayout.LayoutParams(dp(43), dp(43), Gravity.CENTER)",
     "new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER)", "interaction hand size"),
    ("panel.addView(halo, linearParams(dp(86), dp(86), 0, dp(6)))",
     "panel.addView(halo, linearParams(dp(78), dp(78), 0, dp(6)))", "interaction halo size"),
    ("labelView.setTextSize(13.0f);", "labelView.setTextSize(12.0f);", "interaction label text size"),
    ("labelView.setPadding(dp(11), 0, dp(11), 0);",
     "labelView.setPadding(dp(9), 0, dp(9), 0);", "interaction label padding"),
    ("linearParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(31), 0, 0)",
     "linearParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(29), 0, 0)", "interaction label height"),
    ("dp(148), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM",
     "dp(138), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM", "interaction panel width"),
]
for old, new, label in replacements:
    replace_once(interaction, old, new, label)

main = Path("client/app/src/main/java/com/blackrussia/launcher/activity/MainActivity.java")
for old, new, label in [
    ('"Backend вернул неполную сессию"', '"Сервер учётных записей вернул неполную сессию"', "account session message"),
    ('"Проверяем VOSTOK Account..."', '"Проверяем учётную запись VOSTOK..."', "account check message"),
    ('"Получаем игровой ticket..."', '"Получаем разрешение на вход в игру..."', "game ticket message"),
]:
    replace_once(main, old, new, label)

text = interaction.read_text(encoding="utf-8")
required = [
    "dp(70), dp(70)",
    "dp(38), dp(38)",
    "linearParams(dp(78), dp(78)",
    "labelView.setTextSize(12.0f)",
    "dp(138), ViewGroup.LayoutParams.WRAP_CONTENT",
    'DEFAULT_LABEL = "Взаимодействие"',
]
for needle in required:
    if needle not in text:
        raise SystemExit(f"Candidate 024 interaction UI verification failed: {needle}")

print("Applied VOSTOK Candidate 024 Russian UI / compact interaction patch (launcher untouched)")
