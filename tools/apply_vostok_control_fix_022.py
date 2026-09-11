from pathlib import Path

localplayer = Path('client/Jni source/jni/net/localplayer.cpp')
netrpc = Path('client/Jni source/jni/net/netrpc.cpp')
scriptrpc = Path('client/Jni source/jni/net/scriptrpc.cpp')

localplayer_cpp = localplayer.read_text()
netrpc_cpp = netrpc.read_text()
scriptrpc_cpp = scriptrpc.read_text()

# Candidate 022: Candidate 021 logs proved that good and bad relogs receive the
# same normal spawn/position/camera RPC batch. The disappearing controls are
# GTA's native movement/run/look widgets, not the custom g_pWidgetManager layer.
# CLocalPlayer::Spawn normally restores both native widgets and controllability.
# VOSTOK also uses SetCameraBehindPlayer specifically when returning to normal
# player-controlled gameplay, so make that RPC a deterministic restore point.

spawn_anchor = 'bool CLocalPlayer::Spawn()\n{\n\tif(!m_bHasSpawnInfo) return false;'
if localplayer_cpp.count(spawn_anchor) != 1:
    raise SystemExit('localplayer.cpp Spawn anchor mismatch')
localplayer_cpp = localplayer_cpp.replace(
    spawn_anchor,
    'bool CLocalPlayer::Spawn()\n{\n'
    '\tLog("VOSTOK CONTROL: Spawn enter hasInfo=%d active=%d first=%d", (int)m_bHasSpawnInfo, (int)m_bIsActive, (int)bFirstSpawn);\n'
    '\tif(!m_bHasSpawnInfo)\n'
    '\t{\n'
    '\t\tLog("VOSTOK CONTROL: Spawn aborted - no spawn info");\n'
    '\t\treturn false;\n'
    '\t}',
    1,
)

spawn_controls = '\tpGame->DisplayWidgets(true);\n\tpGame->DisplayHUD(true);\n\tm_pPlayerPed->TogglePlayerControllable(true);'
if localplayer_cpp.count(spawn_controls) != 1:
    raise SystemExit('localplayer.cpp Spawn controls anchor mismatch')
localplayer_cpp = localplayer_cpp.replace(
    spawn_controls,
    '\tpGame->ToggleHUDElement(HUD_ELEMENT_BUTTONS, true);\n'
    '\tpGame->DisplayWidgets(true);\n'
    '\tpGame->DisplayHUD(true);\n'
    '\tm_pPlayerPed->TogglePlayerControllable(true);\n'
    '\tLog("VOSTOK CONTROL: Spawn restored native widgets and controllability");',
    1,
)

spawn_return = (
    '\tpNetGame->GetRakClient()->RPC(&RPC_Spawn, &bsSendSpawn, HIGH_PRIORITY, \n'
    '\t\tRELIABLE_SEQUENCED, 0, false, UNASSIGNED_NETWORK_ID, NULL);\n\n'
    '\treturn true;'
)
if localplayer_cpp.count(spawn_return) != 1:
    raise SystemExit('localplayer.cpp Spawn return anchor mismatch')
localplayer_cpp = localplayer_cpp.replace(
    spawn_return,
    '\tpNetGame->GetRakClient()->RPC(&RPC_Spawn, &bsSendSpawn, HIGH_PRIORITY, \n'
    '\t\tRELIABLE_SEQUENCED, 0, false, UNASSIGNED_NETWORK_ID, NULL);\n\n'
    '\tLog("VOSTOK CONTROL: Spawn complete active=%d", (int)m_bIsActive);\n'
    '\treturn true;',
    1,
)

request_spawn_anchor = '''\tif(pLocalPlayer)
\t{
\t\tif(byteRequestOutcome == 2 || (byteRequestOutcome && pLocalPlayer->m_bWaitingForSpawnRequestReply))
\t\t\tpLocalPlayer->Spawn();
\t\telse
\t\t\tpLocalPlayer->m_bWaitingForSpawnRequestReply = false;
\t}'''
if netrpc_cpp.count(request_spawn_anchor) != 1:
    raise SystemExit('netrpc.cpp RequestSpawn anchor mismatch')
netrpc_cpp = netrpc_cpp.replace(
    request_spawn_anchor,
    '''\tif(pLocalPlayer)
\t{
\t\tLog("VOSTOK CONTROL: RequestSpawn outcome=%u waiting=%d", (unsigned)byteRequestOutcome, (int)pLocalPlayer->m_bWaitingForSpawnRequestReply);
\t\tif(byteRequestOutcome == 2 || (byteRequestOutcome && pLocalPlayer->m_bWaitingForSpawnRequestReply))
\t\t{
\t\t\tbool spawned = pLocalPlayer->Spawn();
\t\t\tLog("VOSTOK CONTROL: RequestSpawn Spawn()=%d", (int)spawned);
\t\t}
\t\telse
\t\t{
\t\t\tLog("VOSTOK CONTROL: RequestSpawn did not call Spawn()");
\t\t\tpLocalPlayer->m_bWaitingForSpawnRequestReply = false;
\t\t}
\t}''',
    1,
)

camera_anchor = '''void ScrSetCameraBehindPlayer(RPCParameters *rpcParams)
{
\tLog("RPC: ScrSetCameraBehindPlayer");

\tpGame->GetCamera()->SetBehindPlayer();\t
}'''
if scriptrpc_cpp.count(camera_anchor) != 1:
    raise SystemExit('scriptrpc.cpp camera-behind anchor mismatch')
scriptrpc_cpp = scriptrpc_cpp.replace(
    camera_anchor,
    '''void ScrSetCameraBehindPlayer(RPCParameters *rpcParams)
{
\tLog("RPC: ScrSetCameraBehindPlayer");
\tpGame->GetCamera()->SetBehindPlayer();

\tuint16_t widgetsBefore = *(uint16_t*)(g_libGTASA + 0x8B82A0 + 0x10C);
\tpGame->ToggleHUDElement(HUD_ELEMENT_BUTTONS, true);
\tpGame->DisplayWidgets(true);
\tCLocalPlayer* pLocalPlayer = pNetGame && pNetGame->GetPlayerPool() ? pNetGame->GetPlayerPool()->GetLocalPlayer() : nullptr;
\tif (pLocalPlayer && pLocalPlayer->GetPlayerPed())
\t{
\t\tpLocalPlayer->GetPlayerPed()->TogglePlayerControllable(true);
\t}
\tuint16_t widgetsAfter = *(uint16_t*)(g_libGTASA + 0x8B82A0 + 0x10C);
\tLog("VOSTOK CONTROL: camera-behind restore widgets=%u->%u local=%d",
\t\t(unsigned)widgetsBefore, (unsigned)widgetsAfter, (int)(pLocalPlayer != nullptr));
}''',
    1,
)

controllable_anchor = '''void ScrTogglePlayerControllable(RPCParameters *rpcParams)
{'''
if scriptrpc_cpp.count(controllable_anchor) != 1:
    raise SystemExit('scriptrpc.cpp controllable RPC anchor mismatch')
scriptrpc_cpp = scriptrpc_cpp.replace(
    controllable_anchor,
    '''void ScrTogglePlayerControllable(RPCParameters *rpcParams)
{
\tLog("VOSTOK CONTROL: ScrTogglePlayerControllable received");''',
    1,
)

for required in (
    'VOSTOK CONTROL: Spawn enter',
    'VOSTOK CONTROL: RequestSpawn outcome=',
    'VOSTOK CONTROL: camera-behind restore widgets=',
    'VOSTOK CONTROL: ScrTogglePlayerControllable received',
):
    if required not in localplayer_cpp + netrpc_cpp + scriptrpc_cpp:
        raise SystemExit(f'Candidate 022 validation failed: {required}')

localplayer.write_text(localplayer_cpp)
netrpc.write_text(netrpc_cpp)
scriptrpc.write_text(scriptrpc_cpp)
print('Applied Candidate 022 deterministic GTA native-control restore + control diagnostics')
