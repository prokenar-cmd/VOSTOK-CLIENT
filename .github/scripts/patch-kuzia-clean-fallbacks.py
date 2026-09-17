from pathlib import Path
import re
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "kuzia")
hooks = root / "app/src/main/cpp/samp/game/hooks.cpp"
wrapper = root / "app/src/main/cpp/samp/gui/imguiwrapper.cpp"

text = hooks.read_text(encoding="utf-8")
required = [
    "void InstallUrezHooks()",
    "g_libGTASA + 0x24E4C4",
    "g_libGTASA + 0x246D17",
    "void InstallSpecialHooks()",
    "InjectHooks();",
    'CHook::InlineHook("_ZN5CGame20InitialiseRenderWareEv"',
    'CHook::InlineHook("_Z10NvUtilInitv"',
]
for item in required:
    if item not in text:
        raise SystemExit(f"Expected Kuzia baseline fragment not found: {item}")

# Confirmed clean-cache fix: do not apply the old CRMP DXT rewrite.
pattern = r"(?m)^\s*InstallUrezHooks\(\);\s*$"
if len(re.findall(pattern, text)) != 1:
    raise SystemExit("Expected exactly one InstallUrezHooks invocation")
text = re.sub(
    pattern,
    '    Log("AB KUZIA NO_UREZ_ONLY: InstallUrezHooks disabled");',
    text,
    count=1,
)

# The clean GTA cache has no Kuzia SAMP texture database. Skip it when absent.
old_rw = '''bool CGame__InitialiseRenderWare_hook() {
    Log("Loading SAMP texture database..");
    CGame__InitialiseRenderWare();

    TextureDatabaseRuntime::Load("samp", false, TextureDatabaseFormat::DF_Default);

    InitGui();
    return true;
}'''
new_rw = '''bool CGame__InitialiseRenderWare_hook() {
    Log("Loading SAMP texture database..");
    bool rwOk = CGame__InitialiseRenderWare();
    Log("VOSTOK CLEAN: original CGame::InitialiseRenderWare returned %d", rwOk ? 1 : 0);

    char sampTxt[255]{};
    snprintf(sampTxt, sizeof(sampTxt), "%stexdb/samp/samp.txt", g_pszStorage);
    FILE* sampProbe = fopen(sampTxt, "rb");
    if (sampProbe) {
        fclose(sampProbe);
        Log("VOSTOK CLEAN: loading optional samp texture DB");
        TextureDatabaseRuntime::Load("samp", false, TextureDatabaseFormat::DF_Default);
    } else {
        Log("VOSTOK CLEAN: samp texture DB absent, skip: %s", sampTxt);
    }

    InitGui();
    return rwOk;
}'''
if old_rw not in text:
    raise SystemExit("Expected CGame__InitialiseRenderWare_hook block not found")
text = text.replace(old_rw, new_rw, 1)

# Kuzia has optional SAMP/* replacements for stock GTA files. If the replacement
# is absent, use the original clean /GTA/<path> instead of returning nullptr.
old_open_fail = '''    else
    {
        Log("NVFOpen hook | Error: file not found (%s)", path);
        free(st);
        return nullptr;
    }
}'''
new_open_fail = '''    else
    {
        Log("NVFOpen hook | Error: file not found (%s)", path);

        char originalPath[255]{};
        if (strstr(r1, "emulated") != NULL) {
            snprintf(originalPath, sizeof(originalPath), "%s", r1);
        } else {
            snprintf(originalPath, sizeof(originalPath), "%s%s", g_pszStorage, r1);
        }

        if (strcmp(path, originalPath) != 0) {
            FILE* originalFile = fopen(originalPath, "rb");
            if (originalFile) {
                Log("VOSTOK CLEAN: SAMP override missing, fallback to %s", originalPath);
                st->isFileExist = true;
                st->f = originalFile;
                return st;
            }
        }

        free(st);
        return nullptr;
    }
}'''
if old_open_fail not in text:
    raise SystemExit("Expected NvFOpen failure block not found")
text = text.replace(old_open_fail, new_open_fail, 1)
hooks.write_text(text, encoding="utf-8")

# Avoid ImGui's fatal AddFontFromFileTTF assertion when SAMP/fonts is absent.
w = wrapper.read_text(encoding="utf-8")
old_font = '''    ImFont* font = io.Fonts->AddFontFromFileTTF(m_fontPath.c_str(),
                                                UISettings::fontSize(), &fontCfg, ranges);

    if (font == nullptr)
    {
        Log::addParameter("font", font);
        return false;
    }'''
new_font = '''    ImFont* font = nullptr;
    FILE* fontProbe = fopen(m_fontPath.c_str(), "rb");
    if (fontProbe)
    {
        fclose(fontProbe);
        font = io.Fonts->AddFontFromFileTTF(m_fontPath.c_str(),
                                            UISettings::fontSize(), &fontCfg, ranges);
    }
    else
    {
        Log::addParameter("VOSTOK CLEAN: external font missing: %s", m_fontPath.c_str());
        const char* systemFont = "/system/fonts/Roboto-Regular.ttf";
        fontProbe = fopen(systemFont, "rb");
        if (fontProbe)
        {
            fclose(fontProbe);
            Log::addParameter("VOSTOK CLEAN: font fallback: %s", systemFont);
            font = io.Fonts->AddFontFromFileTTF(systemFont,
                                                UISettings::fontSize(), &fontCfg, ranges);
        }
        else
        {
            Log::addParameter("VOSTOK CLEAN: system Roboto absent, using ImGui default font");
            fontCfg.SizePixels = UISettings::fontSize();
            font = io.Fonts->AddFontDefault(&fontCfg);
        }
    }

    if (font == nullptr)
    {
        Log::addParameter("font", font);
        return false;
    }'''
if old_font not in w:
    raise SystemExit("Expected ImGui font loading block not found")
wrapper.write_text(w.replace(old_font, new_font, 1), encoding="utf-8")

# Internal guards.
final_hooks = hooks.read_text(encoding="utf-8")
final_wrapper = wrapper.read_text(encoding="utf-8")
checks = [
    ("AB KUZIA NO_UREZ_ONLY: InstallUrezHooks disabled", final_hooks),
    ("VOSTOK CLEAN: samp texture DB absent, skip", final_hooks),
    ("VOSTOK CLEAN: SAMP override missing, fallback to", final_hooks),
    ("VOSTOK CLEAN: external font missing", final_wrapper),
    ("/system/fonts/Roboto-Regular.ttf", final_wrapper),
]
for needle, haystack in checks:
    if needle not in haystack:
        raise SystemExit(f"Patch guard missing: {needle}")
if re.search(r"(?m)^\s*InstallUrezHooks\(\);\s*$", final_hooks):
    raise SystemExit("InstallUrezHooks invocation survived patch")

print("Kuzia clean-cache fallbacks patched successfully")
