from pathlib import Path

header = Path('client/Jni source/jni/util/CJavaWrapper.h')
source = Path('client/Jni source/jni/util/CJavaWrapper.cpp')

h = header.read_text()
cpp = source.read_text()

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

header.write_text(h)
source.write_text(cpp)
print('Applied VOSTOK native Interaction UI bridge')
