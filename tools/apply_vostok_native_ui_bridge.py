from pathlib import Path

header = Path('client/Jni source/jni/util/CJavaWrapper.h')
source = Path('client/Jni source/jni/util/CJavaWrapper.cpp')
chat = Path('client/Jni source/jni/chatwindow.cpp')

h = header.read_text()
cpp = source.read_text()
chat_cpp = chat.read_text()

header_replacements = [
    (
        '\tjmethodID s_showNotification;\n\tjmethodID s_showMenu;',
        '\tjmethodID s_showNotification;\n\tjmethodID s_showVostokInteraction;\n\tjmethodID s_hideVostokInteraction;\n\tjmethodID s_setVostokInteractionContext;\n\tjmethodID s_showMenu;'
    ),
    (
        '\tvoid ShowNotification(int type, char* text, int duration, char* actionforBtn, char* textBtn);\n\tvoid ShowMenu();',
        '\tvoid ShowNotification(int type, char* text, int duration, char* actionforBtn, char* textBtn);\n\tvoid ShowVostokInteraction(float distanceMeters);\n\tvoid HideVostokInteraction();\n\tvoid SetVostokInteractionContext(int flags, int reservedActionSlots);\n\tvoid ShowMenu();'
    ),
]

for old, new in header_replacements:
    count = h.count(old)
    if count != 1:
        raise SystemExit(f'CJavaWrapper.h expected exactly one match, found {count}: {old!r}')
    h = h.replace(old, new, 1)

constructor_anchor = '\ts_showNotification = env->GetMethodID(nvEventClass, "showNotification","(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V");\n\ts_showMenu = env->GetMethodID(nvEventClass, "showMenu", "()V");'
constructor_replacement = '''\ts_showNotification = env->GetMethodID(nvEventClass, "showNotification","(ILjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V");
\ts_showVostokInteraction = env->GetMethodID(nvEventClass, "showVostokInteraction", "(F)V");
\ts_hideVostokInteraction = env->GetMethodID(nvEventClass, "hideVostokInteraction", "()V");
\ts_setVostokInteractionContext = env->GetMethodID(nvEventClass, "setVostokInteractionContext", "(II)V");
\ts_showMenu = env->GetMethodID(nvEventClass, "showMenu", "()V");'''

count = cpp.count(constructor_anchor)
if count != 1:
    raise SystemExit(f'CJavaWrapper.cpp constructor anchor expected once, found {count}')
cpp = cpp.replace(constructor_anchor, constructor_replacement, 1)

implementation_anchor = 'CJavaWrapper::CJavaWrapper(JNIEnv* env, jobject activity)\n{'
implementation = r'''void CJavaWrapper::ShowVostokInteraction(float distanceMeters)
{
    JNIEnv* env = GetEnv();
    if (!env)
    {
        Log("No env");
        return;
    }

    env->CallVoidMethod(this->activity, this->s_showVostokInteraction, (jfloat)distanceMeters);
    EXCEPTION_CHECK(env);
}

void CJavaWrapper::HideVostokInteraction()
{
    JNIEnv* env = GetEnv();
    if (!env)
    {
        Log("No env");
        return;
    }

    env->CallVoidMethod(this->activity, this->s_hideVostokInteraction);
    EXCEPTION_CHECK(env);
}

void CJavaWrapper::SetVostokInteractionContext(int flags, int reservedActionSlots)
{
    JNIEnv* env = GetEnv();
    if (!env)
    {
        Log("No env");
        return;
    }

    env->CallVoidMethod(
        this->activity,
        this->s_setVostokInteractionContext,
        (jint)flags,
        (jint)reservedActionSlots
    );
    EXCEPTION_CHECK(env);
}

'''

count = cpp.count(implementation_anchor)
if count != 1:
    raise SystemExit(f'CJavaWrapper.cpp implementation anchor expected once, found {count}')
cpp = cpp.replace(implementation_anchor, implementation + implementation_anchor, 1)

# Candidate 020 runtime fix: the donor client hard-codes the old Black Russia
# application data directory when loading libbass.so. VOSTOK uses the package
# com.vostok.roleplay, so resolve the packaged native library by soname instead.
legacy_bass_path = 'handle = dlopen("/data/data/com.blackrussia.game/lib/libbass.so", 3);'
legacy_bass_count = chat_cpp.count(legacy_bass_path)
if legacy_bass_count != 2:
    raise SystemExit(f'chatwindow.cpp expected two legacy BASS paths, found {legacy_bass_count}')
chat_cpp = chat_cpp.replace(legacy_bass_path, 'handle = dlopen("libbass.so", RTLD_NOW);')

# Never call through a null dlsym result. This is the exact failure mode seen in
# the Candidate 019 device tombstone (PC=0x0 from libsamp during initSAMP).
bass_init_call = '\treturn BASS_Init_func(device, freq, flags, win, dsguid);'
if chat_cpp.count(bass_init_call) != 1:
    raise SystemExit('chatwindow.cpp BASS_Init wrapper anchor mismatch')
chat_cpp = chat_cpp.replace(
    bass_init_call,
    '\tif (!BASS_Init_func)\n'
    '\t{\n'
    '\t\tLog("BASS_Init unavailable");\n'
    '\t\treturn FALSE;\n'
    '\t}\n'
    '\treturn BASS_Init_func(device, freq, flags, win, dsguid);',
    1
)

if '/data/data/com.blackrussia.game/lib/libbass.so' in chat_cpp:
    raise SystemExit('legacy Black Russia BASS package path still present')
if 'dlopen("libbass.so", RTLD_NOW)' not in chat_cpp:
    raise SystemExit('package-independent BASS dlopen missing')
if 'if (!BASS_Init_func)' not in chat_cpp:
    raise SystemExit('BASS_Init null guard missing')

header.write_text(h)
source.write_text(cpp)
chat.write_text(chat_cpp)
print('Applied VOSTOK native Interaction UI bridge + package-safe BASS loader')
