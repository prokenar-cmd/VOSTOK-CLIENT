# STATUS 031K — MASTER PROMOTION

Дата: 2026-09-15

## RESULT

031K повышен в **MASTER** по прямому решению владельца проекта.

Authoritative full-source archive:

`VOSTOK-CLIENT-FULL-SOURCE-031K.zip`

## BASELINE RULE

С этого момента дальнейшая клиентская разработка VOSTOK RP ведётся **только от 031K full source**.

Не использовать как исходную точку:
- 031J
- 031I
- 031H1 и более ранние Candidate
- donor reconstruction pipeline от Black-Russia-Source

## PRESERVE

Следующие подтверждённые системы должны сохраняться при всех последующих Candidate:
- VOSTOK launcher
- canonical launcher background / launcher hardening
- design-space 1891x831
- character selector
- auth/session
- entry/spawn flow
- VOSTOK loading
- HUD / HUD editor
- radial menu
- inventory
- interaction
- vehicle UI
- native auto-connect

## NEXT CANDIDATE POLICY

Следующая версия должна быть отдельным Candidate поверх 031K MASTER. MASTER не изменять до прохождения promotion gate новой версии.

Любой старый файл/скрипт/workflow, который способен вернуть donor launcher или старый low-resolution/background fallback, считается legacy и не может становиться authoritative build base.
