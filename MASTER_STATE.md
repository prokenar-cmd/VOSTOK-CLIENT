# VOSTOK RP — CLIENT MASTER STATE

Дата фиксации: 2026-09-15

## AUTHORITATIVE MASTER

**VOSTOK-CLIENT-FULL-SOURCE-031K.zip**

Статус: **MASTER / единственная разрешённая исходная точка для дальнейшей клиентской разработки**.

Решение о promotion в MASTER принято владельцем проекта после завершения 031K Codex hardening pass.

## ОБЯЗАТЕЛЬНОЕ ПРАВИЛО ДАЛЬНЕЙШЕЙ РАЗРАБОТКИ

1. Любой новый Client Candidate создаётся **только от полного исходника 031K**.
2. 031J, 031I и все более ранние Candidate остаются только историей/референсом и **не могут использоваться как build base**.
3. Запрещено снова реконструировать рабочий клиент от `Parad1st/Black-Russia-Source` с последующим наложением старого overlay/cumulative patch chain.
4. Запрещено возвращать старый донорский launcher, donor Activity/layout/resources, old server selector и старые launcher backgrounds.
5. Подтверждённые рабочие системы 031K нельзя откатывать при следующих изменениях.
6. MASTER и будущие Candidate вести отдельно. Новый Candidate повышается в MASTER только после compile/runtime/device smoke gate.

## СИСТЕМЫ, КОТОРЫЕ СЧИТАЮТСЯ БАЗОВЫМИ В 031K

- VOSTOK launcher как единственная пользовательская launcher-система.
- Отдельный canonical launcher background и отдельные UI/иконки.
- Фиксированное launcher design-space `1891 x 831`.
- Character selector / dropdown / выбранный персонаж / auth-session flow.
- VOSTOK loading / entry-spawn flow.
- HUD + HUD editor.
- Radial menu.
- Inventory.
- Interaction system.
- Vehicle UI.
- Native auto-connect.
- Сохранённые native/game-runtime boundary-компоненты, которые нельзя удалять без доказательства отсутствия JNI/runtime зависимостей.

## LAUNCHER HARDENING POLICY

Главный фон лаунчера больше не должен зависеть от preview/low-resolution/fallback ресурса. В следующих версиях canonical background и launcher lock/validation 031K должны сохраняться. Любая смена background должна быть осознанным изменением, а не побочным эффектом старого pipeline.

## SOURCE OF TRUTH

Для фактической разработки использовать содержимое архива:

`VOSTOK-CLIENT-FULL-SOURCE-031K.zip`

Если GitHub-ветка/старый workflow/старый STATUS противоречат этому файлу, **приоритет у 031K MASTER**.

## DEPRECATED BASELINES

- 031J Clean Launcher — superseded by 031K MASTER.
- 031I Launcher Decouple — historical only.
- 031H1 и более ранние — historical only.

Никакой новый код не начинать с deprecated baseline.
