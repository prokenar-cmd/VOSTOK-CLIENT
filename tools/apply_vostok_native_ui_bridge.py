from pathlib import Path

header = Path('client/Jni source/jni/util/CJavaWrapper.h')
source = Path('client/Jni source/jni/util/CJavaWrapper.cpp')
chat = Path('client/Jni source/jni/chatwindow.cpp')
hooks = Path('client/Jni source/jni/game/hooks.cpp')

h = header.read_text()
cpp = source.read_text()
chat_cpp = chat.read_text()
hooks_cpp = hooks.read_text()

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

# Candidate 021 native fix #1: donor RLEDecompress_hook can copy a repeated
# texture segment past pEndOfDest. Candidate 020 crashed in this exact memcpy
# while TextureDatabaseRuntime::LoadFullTexture was streaming a vehicle texture.
rle_start = 'uint8_t* RLEDecompress_hook(uint8_t* pDest, size_t uiDestSize, uint8_t const* pSrc, size_t uiSegSize, uint32_t uiEscape) {'
rle_end = '\n\n#include "..//crashlytics.h"'
rle_start_pos = hooks_cpp.find(rle_start)
rle_end_pos = hooks_cpp.find(rle_end, rle_start_pos)
if rle_start_pos == -1 or rle_end_pos == -1:
    raise SystemExit('hooks.cpp RLEDecompress_hook anchors not found')

safe_rle = r'''uint8_t* RLEDecompress_hook(uint8_t* pDest, size_t uiDestSize, uint8_t const* pSrc, size_t uiSegSize, uint32_t uiEscape) {
    if (!pDest || !pSrc || uiDestSize == 0 || uiSegSize == 0)
    {
        dwRLEDecompressSourceSize = 0;
        return pDest;
    }

    uint8_t* pTempDest = pDest;
    const uint8_t* pTempSrc = pSrc;
    uint8_t* pEndOfDest = pDest + uiDestSize;
    const uint8_t* pEndOfSrc = pSrc + dwRLEDecompressSourceSize;

    while (pTempDest < pEndOfDest && pTempSrc < pEndOfSrc)
    {
        if (*pTempSrc == uiEscape)
        {
            if ((size_t)(pEndOfSrc - pTempSrc) < 2)
            {
                Log("VOSTOK RLE guard: truncated run header srcRemain=%u", (unsigned)(pEndOfSrc - pTempSrc));
                break;
            }

            uint8_t repeatCount = pTempSrc[1];
            if ((size_t)(pEndOfSrc - pTempSrc) < 2 + uiSegSize)
            {
                Log("VOSTOK RLE guard: truncated run payload srcRemain=%u seg=%u",
                    (unsigned)(pEndOfSrc - pTempSrc), (unsigned)uiSegSize);
                break;
            }

            const uint8_t* pBlock = pTempSrc + 2;
            for (uint8_t repeat = 0; repeat < repeatCount; ++repeat)
            {
                if ((size_t)(pEndOfDest - pTempDest) < uiSegSize)
                {
                    Log("VOSTOK RLE guard: repeat overflow destRemain=%u seg=%u repeat=%u/%u srcSize=%u",
                        (unsigned)(pEndOfDest - pTempDest), (unsigned)uiSegSize,
                        (unsigned)repeat, (unsigned)repeatCount,
                        (unsigned)dwRLEDecompressSourceSize);
                    dwRLEDecompressSourceSize = 0;
                    return pDest;
                }

                pDest = (uint8_t*)memcpy(pTempDest, pBlock, uiSegSize);
                pTempDest += uiSegSize;
            }

            pTempSrc += 2 + uiSegSize;
        }
        else
        {
            if ((size_t)(pEndOfSrc - pTempSrc) < uiSegSize ||
                (size_t)(pEndOfDest - pTempDest) < uiSegSize)
            {
                Log("VOSTOK RLE guard: literal boundary srcRemain=%u destRemain=%u seg=%u",
                    (unsigned)(pEndOfSrc - pTempSrc),
                    (unsigned)(pEndOfDest - pTempDest),
                    (unsigned)uiSegSize);
                break;
            }

            pDest = (uint8_t*)memcpy(pTempDest, pTempSrc, uiSegSize);
            pTempDest += uiSegSize;
            pTempSrc += uiSegSize;
        }
    }

    dwRLEDecompressSourceSize = 0;
    return pDest;
}'''

hooks_cpp = hooks_cpp[:rle_start_pos] + safe_rle + hooks_cpp[rle_end_pos:]

# Candidate 021 native fix #2: the donor renders the entire custom widget
# manager only from CWidgetButtonEnterCar_Draw_hook. That makes VOSTOK/SAMP
# buttons depend on GTA deciding to draw the enter-car control (usually after a
# vehicle is streamed nearby). Move the custom widget update/draw to the regular
# HUD pass so controls are present immediately after spawn.
hud_anchor = 'void CHud__DrawScriptText_hook(uintptr_t thiz, uint8_t unk)\n{'
if hooks_cpp.count(hud_anchor) != 1:
    raise SystemExit('hooks.cpp CHud__DrawScriptText_hook anchor mismatch')
hooks_cpp = hooks_cpp.replace(
    hud_anchor,
    'void DrawVostokWidgetsEveryFrame();\n\n' + hud_anchor,
    1
)

hud_tail = '''\t\t}\n\t}\n}\n\n#include "..//keyboard.h"'''
if hooks_cpp.count(hud_tail) != 1:
    raise SystemExit('hooks.cpp CHud__DrawScriptText_hook tail mismatch')
hooks_cpp = hooks_cpp.replace(
    hud_tail,
    '\t\t}\n\t}\n\n\tDrawVostokWidgetsEveryFrame();\n}\n\n#include "..//keyboard.h"',
    1
)

enter_start = 'int CWidgetButtonEnterCar_Draw_hook(uintptr_t thiz)\n{'
enter_end = '\n}\n\nuint64_t(*CWorld_ProcessPedsAfterPreRender)();'
enter_start_pos = hooks_cpp.find(enter_start)
enter_end_pos = hooks_cpp.find(enter_end, enter_start_pos)
if enter_start_pos == -1 or enter_end_pos == -1:
    raise SystemExit('hooks.cpp enter-car widget hook anchors not found')

widget_impl = r'''void DrawVostokWidgetsEveryFrame()
{
    if (!g_pWidgetManager || !pGame)
    {
        return;
    }

    CWidget* pWidget = g_pWidgetManager->GetWidget(WIDGET_CHATHISTORY_UP);
    if (pWidget)
    {
        pWidget->SetDrawState(false);
    }

    pWidget = g_pWidgetManager->GetWidget(WIDGET_CHATHISTORY_DOWN);
    if (pWidget)
    {
        pWidget->SetDrawState(false);
    }

    pWidget = g_pWidgetManager->GetWidget(WIDGET_CAMERA_CYCLE);
    if (pWidget)
    {
        pWidget->SetDrawState(true);
    }

    pWidget = g_pWidgetManager->GetWidget(WIDGET_MICROPHONE);
    if (pWidget)
    {
        if (pVoice)
        {
            pWidget->SetDrawState(true);
            static uint32_t lastTick = GetTickCount();
            if (pWidget->GetState() == 2 && GetTickCount() - lastTick >= 250)
            {
                pVoice->TurnRecording();
                if (pVoice->IsRecording())
                {
                    g_uiLastTickVoice = GetTickCount();
                    if (pVoice->IsDisconnected())
                    {
                        pChatWindow->AddDebugMessage("Voice server disconnected");
                        pVoice->DisableInput();
                    }
                }
                lastTick = GetTickCount();
            }

            if (pVoice->IsRecording() && GetTickCount() - g_uiLastTickVoice >= 30000)
            {
                pVoice->DisableInput();
            }

            if (pVoice->IsRecording())
            {
                if (pVoice->IsDisconnected())
                {
                    pChatWindow->AddDebugMessage("Voice server disconnected");
                    pVoice->DisableInput();
                }
                pWidget->SetColor(255, 0x9C, 0xCF, 0x9C);
            }
            else
            {
                pWidget->ResetColor();
            }
        }
    }

    if (!pGame->IsToggledHUDElement(HUD_ELEMENT_BUTTONS))
    {
        for (int i = 0; i < MAX_WIDGETS; i++)
        {
            CWidget* pAnyWidget = g_pWidgetManager->GetWidget(i);
            if (pAnyWidget)
            {
                pAnyWidget->SetDrawState(false);
            }
        }
    }

    g_pWidgetManager->Draw();
}

int CWidgetButtonEnterCar_Draw_hook(uintptr_t thiz)
{
    return CWidgetButtonEnterCar_Draw(thiz);
}'''

hooks_cpp = hooks_cpp[:enter_start_pos] + widget_impl + hooks_cpp[enter_end_pos + 2:]

for required in [
    'VOSTOK RLE guard: repeat overflow',
    'void DrawVostokWidgetsEveryFrame()',
    '\tDrawVostokWidgetsEveryFrame();',
    'return CWidgetButtonEnterCar_Draw(thiz);',
]:
    if required not in hooks_cpp:
        raise SystemExit(f'Candidate 021 native patch validation failed: {required}')

header.write_text(h)
source.write_text(cpp)
chat.write_text(chat_cpp)
hooks.write_text(hooks_cpp)
print('Applied VOSTOK native Interaction UI bridge + BASS loader + RLE bounds + HUD widget decoupling')
