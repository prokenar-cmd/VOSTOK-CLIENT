# VOSTOK Interaction Core

This document freezes the interaction architecture before most gameplay systems exist.

## Runtime scope now

- Only NPC interaction is enabled.
- The current hand button remains a thin client gateway to the server-authoritative `/interact` path.
- Vehicle/player/resource/object interaction is deliberately disabled until those gameplay systems are ready.

## Planned world interaction model

The interaction button is a reusable world-action gateway, not a vehicle-control panel.

- NPC: current supported target.
- Job resource/node: future direct action. If there is one unambiguous resource target, pressing the button starts collection immediately.
- Vehicle/player/object/etc.: future target types. If more than one valid target is inside the interaction zone, the button opens a radial target selector instead of guessing.
- If a selected target itself has several valid actions, a radial action menu can be opened after target selection.
- Radial menus are a reusable UI primitive and are not tied only to interaction; other systems may use the same radial-menu contract later.

## Vehicle rule

Once the player is seated in a vehicle, engine and other driving controls do not belong to the interaction button. They belong to the speedometer/driving UI. The interaction system must not grow a parallel engine/vehicle-control implementation.

## Authority and future protocol

- Server/native gameplay code remains authoritative for candidate discovery, distance, permissions and available actions.
- Java UI only presents state and forwards the player's choice.
- Current NPC flow uses the legacy `/interact` command as a temporary transport.
- Future native/RPC transport should reuse the same `InteractionCore` and radial contracts rather than replace the UI architecture.

## Selection flow

1. Server/native layer builds valid candidates in range.
2. Disabled target types are filtered by the client runtime policy.
3. Zero targets: hide interaction UI.
4. One target + one action: execute directly.
5. Multiple targets: open radial target selection.
6. One target + multiple actions: open radial action selection.
7. Multiple targets + multiple actions: choose target first, then actions.

The current runtime policy enables only NPC targets, so steps involving other target types are foundation only and cannot trigger yet.