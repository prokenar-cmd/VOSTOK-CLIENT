#!/usr/bin/env python3
from pathlib import Path

# Candidate 023: bridge the server-authoritative Interaction Core to the
# already-existing Android VOSTOK interaction overlay. The server sends two
# reserved ClientMessage control records. They are consumed here before chat
# rendering, so normal chat/dialog/launcher/loading UI is untouched.
chat = Path('client/Jni source/jni/chatwindow.cpp')
s = chat.read_text()

old = '''void CChatWindow::AddClientMessage(uint32_t dwColor, char* szStr)
{
\tFilterInvalidChars(szStr);
\tAddToChatWindowBuffer(CHAT_TYPE_INFO, szStr, nullptr, dwColor, 0);
}'''

new = '''void CChatWindow::AddClientMessage(uint32_t dwColor, char* szStr)
{
    static const char kVostokInteractionShow[] = "~VOSTOK_UI~INTERACT:SHOW:";
    static const char kVostokInteractionHide[] = "~VOSTOK_UI~INTERACT:HIDE";

    if (szStr && strncmp(szStr, kVostokInteractionShow, sizeof(kVostokInteractionShow) - 1) == 0)
    {
        float distanceMeters = 0.0f;
        const char* value = szStr + sizeof(kVostokInteractionShow) - 1;
        if (sscanf(value, "%f", &distanceMeters) == 1)
        {
            if (distanceMeters < 0.0f) distanceMeters = 0.0f;
            if (distanceMeters > 100.0f) distanceMeters = 100.0f;
            if (g_pJavaWrapper)
                g_pJavaWrapper->ShowVostokInteraction(distanceMeters);
        }
        return;
    }

    if (szStr && strcmp(szStr, kVostokInteractionHide) == 0)
    {
        if (g_pJavaWrapper)
            g_pJavaWrapper->HideVostokInteraction();
        return;
    }

    FilterInvalidChars(szStr);
    AddToChatWindowBuffer(CHAT_TYPE_INFO, szStr, nullptr, dwColor, 0);
}'''

count = s.count(old)
if count != 1:
    raise SystemExit(f'Candidate 023 AddClientMessage anchor mismatch: {count}')
s = s.replace(old, new, 1)

required = [
    '~VOSTOK_UI~INTERACT:SHOW:',
    '~VOSTOK_UI~INTERACT:HIDE',
    'g_pJavaWrapper->ShowVostokInteraction(distanceMeters);',
    'g_pJavaWrapper->HideVostokInteraction();',
]
for needle in required:
    if needle not in s:
        raise SystemExit(f'Candidate 023 interaction bridge missing: {needle}')

chat.write_text(s)
print('Applied VOSTOK Candidate 023 server-authoritative interaction button transport')
