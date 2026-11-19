# Промпт для исправления замечаний ревью

Работай в `D:\Sources\Android\SproutPace`. Исправь все пять функциональных замечаний R1–R5 из `docs/reviews/2026-11-19/review.md` и доведи их до проверенного результата. Прочти AGENTS.md, утверждённый `spec/app-spec.json`, соответствующие FLOW/SCREEN и текущий `.vibe/work.json`. Используй vibe-developer lean delivery. Сохраняй пользовательские изменения и pinned tooling.

Сначала перепроверь каждый контрпример на текущем HEAD. Воспроизведи дефект регрессионным тестом через максимально близкий к production вход; затем внеси ограниченное исправление. Если код уже изменён и замечание больше не воспроизводится, покажи конкретный актуальный тест/доказательство вместо повторной правки.

Порядок работ и критерии:

1. **R1 — TraySnapshot.** Изменение/переименование/удаление пресета не меняет снимок существующей партии при редактировании другого поля. Явная замена лотка работает и пересчитывает только ожидаемые поля. Проверить persistence/restart и сохранение recipe snapshot/history.
2. **R2 — Android permission lifecycle.** Pending permission request корректно переживает detach/attach либо завершается отменой; reminder mutex не блокируется системным диалогом. Тест настоящего AndroidNotificationPermissionService с пересозданием Activity, поздним callback и subsequent reconciliation. Обычный rotate недостаточен: manifest обрабатывает orientation.
3. **R3 — Clear semantics.** Различай отсутствие изменения, новое значение и явную очистку. Через публичный editor/Store проверить supplier/cultivar/seedLot/germinationPercentage рецепта/партии и снятие последней/одной из нескольких fixed dates. Сохранить совместимость частичных команд и authored whitespace.
4. **R4 — Будущие предложения.** После длительного перерыва, рестарта и pause/resume предложения cadence/continuous queue актуальны относительно локального today и периода выращивания. Сохрани cadence/rotation, capacity validation, стабильные ID и даты уже подтверждённых партий. Используй deterministic clock; проверь переход дня и часового пояса.
5. **R5 — Pending photo replacement.** При неудаче/отмене копирования B ранее выбранное A и черновик остаются пригодны для save. Новая копия принимается до удаления A; partial B очищается. Реализуй и проверь Android/iOS native adapters, успешную замену, remove, save/reopen и cleanup. Не ограничивай доказательство фейковым picker callback.

Веди одну recovery note и следующий шаг в lean state. Применяй relevant skills напрямую. Для материального UI-изменения сначала Lazyweb evidence; сохранить EN/RU, accessibility, adaptive layout и previews. Спеку не менять ради оправдания дефекта; при настоящем противоречии зафиксировать его явно.

Во время работы запускай affected tests. На стабильном integration snapshot один раз выполни применимую финальную матрицу из AGENTS.md через `lean-delivery.py <repo> check --runner gradle -- <tasks>`: Detekt, Android host tests, Compose compile, SQLDelight migration, Paparazzi verify, Android debug/release, Kover и применимые iOS compilation checks. Не использовать legacy `.vibe/scripts/run-final-local-gates.ps1`. Инспектировать intentional golden changes перед verify.

Затем запроси независимый acceptance/delta review изменённых сценариев и их потребителей. Отдельно собери текущие Android native/TalkBack и macOS Xcode/Simulator/VoiceOver результаты, если соответствующие среды доступны. Windows compile/host tests не заменяют native iOS evidence. Если среда недоступна, завершить все доступные исправления и явно оставить конкретные gates unverified.

Итог: таблица R1–R5 с изменениями и доказательствами, результаты реальных проверок и их логами, оставшиеся gaps, отдельные статусы locally verified / implementation complete / release ready. Не публикуй приложение, не создавай Firebase/ads/store projects, не добавляй реальные секреты и не ослабляй quality gates.
