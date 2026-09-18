from pathlib import Path
import sys

root = Path(sys.argv[1])
gtasa = root / "app/src/main/java/com/samp/mobile/game/GTASA.java"
samp = root / "app/src/main/java/com/samp/mobile/game/SAMP.java"

def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing patch anchor: {label}")
    return text.replace(old, new, 1)

g = gtasa.read_text(encoding="utf-8")
g = replace_once(
    g,
    "import com.samp.mobile.launcher.util.SignatureChecker;\n",
    "import com.samp.mobile.launcher.util.SignatureChecker;\nimport com.samp.mobile.diag.StartupMarker;\n",
    "GTASA import"
)
g = replace_once(
    g,
    "    public void onCreate(Bundle bundle)\n    {\n",
    "    public void onCreate(Bundle bundle)\n    {\n        StartupMarker.mark(this, \"GTASA_ONCREATE_ENTER\");\n",
    "GTASA onCreate enter"
)
g = replace_once(
    g,
    "        super.onCreate(bundle);\n\n        if (new SharedPreferenceCore().getBoolean(this, \"MLOADER\")) {\n",
    "        StartupMarker.mark(this, \"GTASA_BEFORE_WARMEDIA_SUPER\");\n        super.onCreate(bundle);\n        StartupMarker.mark(this, \"GTASA_AFTER_WARMEDIA_SUPER\");\n\n        StartupMarker.mark(this, \"GTASA_BEFORE_MLOADER_PREF\");\n        if (new SharedPreferenceCore().getBoolean(this, \"MLOADER\")) {\n            StartupMarker.mark(this, \"GTASA_MLOADER_ENABLED\");\n",
    "GTASA super/MLOADER"
)
g = replace_once(
    g,
    "                System.loadLibrary(\"monetloader\");\n",
    "                StartupMarker.mark(this, \"GTASA_BEFORE_MONETLOADER\");\n                System.loadLibrary(\"monetloader\");\n                StartupMarker.mark(this, \"GTASA_AFTER_MONETLOADER\");\n",
    "GTASA monetloader"
)
g = replace_once(
    g,
    "        }\n    }\n\n    public void onDestroy()\n",
    "        }\n        StartupMarker.mark(this, \"GTASA_ONCREATE_DONE\");\n    }\n\n    public void onDestroy()\n",
    "GTASA done"
)
gtasa.write_text(g, encoding="utf-8")

s = samp.read_text(encoding="utf-8")
s = replace_once(
    s,
    "import com.samp.mobile.launcher.util.SignatureChecker;\n",
    "import com.samp.mobile.launcher.util.SignatureChecker;\nimport com.samp.mobile.diag.StartupMarker;\n",
    "SAMP import"
)
s = replace_once(
    s,
    "    public void onCreate(Bundle savedInstanceState) {\n        Log.i(TAG, \"**** onCreate\");\n        super.onCreate(savedInstanceState);\n",
    "    public void onCreate(Bundle savedInstanceState) {\n        StartupMarker.mark(this, \"SAMP_ONCREATE_ENTER\");\n        Log.i(TAG, \"**** onCreate\");\n        StartupMarker.mark(this, \"SAMP_BEFORE_SUPER\");\n        super.onCreate(savedInstanceState);\n        StartupMarker.mark(this, \"SAMP_AFTER_SUPER\");\n",
    "SAMP super"
)
s = replace_once(
    s,
    "        mKeyboard = new CustomKeyboard(this);\n\n        mDialog = new DialogManager(this);\n\n        mAttachEdit = new AttachEdit(this);\n\n        mLoadingScreen = new LoadingScreen(this);\n\n        instance = this;\n\n        try {\n            initializeSAMP();\n",
    "        StartupMarker.mark(this, \"SAMP_BEFORE_KEYBOARD\");\n        mKeyboard = new CustomKeyboard(this);\n        StartupMarker.mark(this, \"SAMP_AFTER_KEYBOARD\");\n\n        mDialog = new DialogManager(this);\n        StartupMarker.mark(this, \"SAMP_AFTER_DIALOG\");\n\n        mAttachEdit = new AttachEdit(this);\n        StartupMarker.mark(this, \"SAMP_AFTER_ATTACH_EDIT\");\n\n        mLoadingScreen = new LoadingScreen(this);\n        StartupMarker.mark(this, \"SAMP_AFTER_LOADING_SCREEN\");\n\n        instance = this;\n        StartupMarker.mark(this, \"SAMP_BEFORE_INITIALIZE_NATIVE\");\n\n        try {\n            initializeSAMP();\n            StartupMarker.mark(this, \"SAMP_AFTER_INITIALIZE_NATIVE\");\n",
    "SAMP components/native init"
)
s = replace_once(
    s,
    "        }\n\n    }\n\n    private native void initializeSAMP();\n",
    "        }\n        StartupMarker.mark(this, \"SAMP_ONCREATE_DONE\");\n\n    }\n\n    private native void initializeSAMP();\n",
    "SAMP done"
)
samp.write_text(s, encoding="utf-8")

print("Lifecycle instrumentation applied")
