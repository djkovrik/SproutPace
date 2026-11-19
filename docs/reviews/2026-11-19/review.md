# Ревью основных флоу SproutPace от 19 ноября 2026

Вердикт: **GAPS**. Основные сценарии представлены в production-коде, но их полную корректность подтвердить нельзя: найдены пять функциональных дефектов. Это ревью текущего проекта, а не формальный закрывающий audit receipt и не подтверждение release readiness.

Исходный HEAD: `7c6eb4d1614effebe95dc838b74578856063531e`; рабочее дерево перед проверкой было чистым. Production-код, тесты, спецификация и delivery state не изменялись. Проверены app-spec, 12 flow-файлов, 16 screen-файлов и supporting product/domain/data/design/quality contracts. Два независимых bounded review покрыли основной цикл выращивания и платформенные уведомления/privacy.

## Подтверждённые замечания

### R1 P1 — Коррекция партии подменяет снимок лотка

Обязательства: FLOW-002 AC-016, FLOW-004 AC-032; снимки существующих партий должны сохраняться при изменении пресета.

`shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/ProductionScreenInteractionBuilder.kt:233–242` ищет текущий пресет по сохранённым имени и форме. Если пресет переименован, изменён или удалён, редактор подставляет первый доступный пресет. `RecipeBatchCommandHandler.kt:179–182` затем сохраняет его снимок и пересчитывает рекомендованную массу семян.

Воспроизведение: создать партию на лотке 10×20 см; изменить пресет на 20×30; открыть коррекцию партии, поменять только поставщика и сохранить. Сохранённая площадь партии меняется с 200 на 600 см² без явного выбора нового лотка.

Исправление: редактор существующей партии должен сохранять исходный TraySnapshot до явного выбора другого лотка. Поддержать удалённый и переименованный пресет. Регрессия через публичный component/Store/editor: unrelated edit после update/delete preset, затем рестарт; отдельный тест явной замены.

### R2 P1 — Пересоздание Activity во время запроса разрешения блокирует планировщик

Обязательства: FLOW-006 AC-064/067, QG-002; восстановление после lifecycle-событий и разрешений.

`shared/platform/src/androidMain/kotlin/com/sedsoftware/sproutpace/platform/AndroidNotificationPermissionService.kt:70–74` обнуляет pendingRequest при detach, не завершив continuation из `request():42–49`. `androidApp/src/main/kotlin/com/sedsoftware/sproutpace/AppActivity.kt:110` вызывает detach при уничтожении Activity. При этом `shared/compose/src/commonMain/kotlin/com/sedsoftware/sproutpace/compose/ProductionAppGraph.kt:232–238` удерживает mutex ReminderSchedulingMonitor в ожидании результата.

Воспроизведение: Android 13+, ещё не выданное разрешение; открыть запрос, пересоздать Activity в том же процессе (например, recreate или смена системного uiMode), завершить запрос. Callback уже потерян; ожидающая coroutine и mutex остаются заняты, дальнейшая синхронизация уведомлений ждёт до перезапуска процесса. Обычный поворот экрана не является надёжным триггером: manifest обрабатывает orientation самостоятельно.

Исправление: определить lifecycle-владение запросом, сохранять continuation при смене host либо корректно завершать/отменять её; не удерживать глобальную блокировку планировщика на время системного диалога. Тестировать настоящий AndroidNotificationPermissionService с detach/attach/result, а не только FakeNotificationPermissionService; после этого сверить foreground reconciliation и delivered reminders.

### R3 P2 — Пустое значение редактора не очищает сохранённое поле

Обязательства: FLOW-003 AC-023, FLOW-004 AC-032.

`shared/component/src/commonMain/kotlin/com/sedsoftware/sproutpace/component/ScreenInteractionContracts.kt:154–157` удаляет пустые значения при подготовке команды. `shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/RecipeBatchCommandHandler.kt:54–60,185–198,249–250` трактует отсутствие supplier/cultivar/seedLot/germinationPercentage как «оставить прежнее». Аналогично отсутствие всех fixed-date аргументов сохраняет прежние даты.

Воспроизведение: сохранить поставщика либо germinationPercentage; очистить поле в редакторе и сохранить — после повторного открытия значение возвращается. Удаление последней fixedHarvestDate также не снимает фиксацию.

Исправление: различать unchanged, set и clear в контракте команд. Сохранить семантику частичных команд, условно применимых полей и authored whitespace. Тесты должны проходить через подготовку команды из draft и реальный Store, проверять повторное открытие и рестарт. Отдельно проверить очистку последней и одной из нескольких фиксированных дат.

### R4 P2 — После перерыва план предлагает только прошлые даты

Обязательства: FLOW-005 AC-051/054/057 и цель генерации будущих предложений.

`shared/data/src/commonMain/kotlin/com/sedsoftware/sproutpace/data/PlannedSuggestion.kt:45–60` строит первые `3 + batches.size` слотов от anchorDate, отбрасывая лишь уже принятые. Параметр today используется для capacity, но не для продвижения горизонта предложений. `shared/domain/src/commonMain/kotlin/com/sedsoftware/sproutpace/domain/engine/PlannerEngine.kt:26` начинает отсчёт от исходного anchorDate. Continuous queue без принятых слотов также использует старый anchor.

Воспроизведение: cadence-план с anchor 1 октября и интервалом пять дней, ни одной принятой партии; открыть 19 ноября. Предлагаются сборы 1, 6 и 11 октября, посевы ещё раньше. Рестарт или pause/resume не продвигают очередь.

Исправление: продвигать горизонт неподтверждённых предложений относительно локального today и длительности выращивания, сохраняя cadence/rotation и стабильные идентичности. Подтверждённые и фиксированные даты партий не сдвигать. Проверить длительный перерыв, pause/resume, continuous queue, смену даты/часового пояса, смешанные принятые/непринятые слоты и capacity.

### R5 P2 — Неудачная замена фото теряет уже выбранный файл черновика

Обязательства: FLOW-007 branch «Import/file failure preserves the prior observation», SCREEN-009 retained input/retry, QG-001.

`shared/platform/src/androidMain/kotlin/com/sedsoftware/sproutpace/platform/AndroidSystemPickers.kt:110–113` удаляет все pending photos перед открытием и копированием нового файла. Аналогично `iosApp/iosApp/iosApp.swift:621–631` удаляет прежние файлы до copyItem. `RootUiAdapter.kt:199–205` обновляет draft только после успешного результата picker.

Воспроизведение: выбрать фото A, не сохранять наблюдение; Replace photo → выбрать B, чтение/копирование которого завершается ошибкой (например, storage failure). Поле черновика сохраняет ссылку A, но файл A уже удалён; повторное сохранение черновика не может прочитать фото. Сохранённое ранее фото наблюдения этим конкретным дефектом не удаляется — проблема именно в pending replacement.

Исправление: сначала полностью скопировать B во временный уникальный файл, затем передать новый token и только после принятия заменить/очистить A. На ошибке/отмене сохранить A; удалить частичный B. Проверить Android и iOS, успешную замену, failure, cancellation, remove, save/reopen и cleanup. Существующий DraftGuidanceAndPhotoProductionTest проверяет успешные операции через fixture, но не этот native copy failure.

## Матрица флоу

«Связи прослежены» означает чтение production-пути и соответствующих assertions; это не ручной end-to-end PASS на устройстве.

| Флоу | Проверенный путь и результат |
| --- | --- |
| 001 Первый запуск | Setup, browse-before-setup, создание зоны/пресета/плана и переходы связаны; горизонт первого/повторного плана затронут R4. |
| 002 Зоны и лотки | CRUD, occupancy, unassigned и historical snapshots связаны; R1 нарушает снимок при последующей коррекции партии. |
| 003 Каталог и рецепты | Локальный каталог, personal CRUD, duplicate/use и snapshots связаны; R3 препятствует очистке optional metadata. |
| 004 Партия | Create/correct, stage confirmation/skip/undo, overflow и delete связаны; R1/R3. |
| 005 План | Cadence, continuous, capacity, confirm-to-editor и pause/delete связаны; R4. |
| 006 Сегодня и уведомления | Stable IDs, completion/snooze, environment reconciliation и permission recovery связаны; R2. |
| 007 Наблюдения | Датированный текст, фото, edit/delete и persistence связаны; R5. |
| 008 Завершение и повтор | Terminal outcomes, освобождение мест, отмена pending actions, actual mass и повтор из snapshots прослежены; нового доказанного дефекта не найдено. |
| 009 Backup/restore/reset | Preview/confirm, journal recovery, compensation, photos, CSV и reset navigation прослежены; NativePublicFileOperationsTest и DataLifecycleRegressionProductionTest содержат roundtrip/rollback/restart assertions. Нового доказанного дефекта не найдено. |
| 010 Реклама и consent | Eligibility, разрешённые placements, withdrawal/policy change, safe-disabled iOS прослежены; новых доказанных дефектов нет. Native SDK execution в этой сессии не проверен. |
| 011 Crashlytics | Настройка и effective-state wiring прослежены; новых доказанных дефектов нет. Configured release crash/symbolication не проверены. |
| 012 Адаптация и доступность | Ресурсы/темы, адаптивные компоненты и тесты существуют; выборочно просмотрены goldens Plan, Today permission denied и Observation pending photo. Полный новый визуальный/assistive runtime PASS не заявляется. |

## Проверки и ограничения

Android/quality Gradle-прогон завершён: **BUILD SUCCESSFUL**, 4 мин 46 сек, 311 задач (24 executed, 287 up-to-date). Включены Detekt, host tests всех семи shared-модулей, Compose Android compilation, SQLDelight migration verification, Paparazzi verify, Android debug/release assemble и Kover verify. Существующие XML test results содержат 852 теста, 0 failures/errors/skipped. Все семь testAndroidHostTest и Paparazzi были UP-TO-DATE: это успешная инкрементальная проверка текущих inputs, а не 852 заново выполненных теста.

Лог: `.vibe/receipts/RECEIPT-e628e0a3-4b90-4a57-a9e7-61a7dce11d78.log`. JSON по тому же имени содержит exitCode 0, но executionStatus `workspace-changed`: во время сборки добавлены ровно два Markdown-файла этого ревью. Сравнение start/end workingFiles подтверждает отсутствие изменения product/test/build inputs. Этот receipt не объявляется формальным frozen acceptance PASS.

Отдельный прогон 14 compileKotlinIosArm64/compileKotlinIosSimulatorArm64 задач: **BUILD SUCCESSFUL**, 20 сек, все 14 compilation tasks UP-TO-DATE. Лог: `.vibe/receipts/RECEIPT-7159d891-96da-45eb-9bf5-a151592dfddd.log`. Это локальная проверка Gradle inputs, не native link/runtime proof.

Предупреждения: SDK XML version mismatch, несовместимость configuration cache с Paparazzi tasks и iOS Skiko dependency version mismatch (0.144.6 → 0.150.1). Последнее следует проверить на macOS при проверке linkage/runtime; в этой сессии конкретная runtime-ошибка от него не доказана. Зависимости автоматически не обновлялись. Исторические статусы `.vibe/work.json` не приняты за текущий PASS.

Android minSdk в проверенных build-файлах — 24. На Windows нельзя подтвердить iOS Simulator, xcodebuild, native picker/notifications и VoiceOver. Текущего ручного TalkBack-прогона в этой сессии нет. Предыдущие незакрытые native evidence gaps нельзя считать закрытыми чтением кода или goldens.

Lazyweb capabilities и поиск реальных форм/confirmation были выполнены до визуальной оценки. Получены примеры PlugShare, Life360 и Asana; они использованы только как контекст. Дефекты R1–R5 обоснованы спецификацией и production-кодом, не внешними эстетическими рекомендациями. Нового редизайна или полного first-release Lazyweb approval эта сессия не проводит.

## План исправлений

1. R1: защитить сохранённые снимки лотков и добавить production editor regression.
2. R2: устранить зависание permission/lifecycle и проверить возобновление scheduler.
3. R3: ввести однозначную семантику очистки полей и покрыть весь путь draft → command → persistence.
4. R4: исправить продвижение планового горизонта с детерминированным clock и capacity regression; проверять актуальность начала посева/замачивания, а не только даты сбора.
5. R5: сделать замену pending photo отказоустойчивой на обеих платформах.
6. На стабильном снимке выполнить применимую матрицу согласно AGENTS.md через lean runner; intentional UI changes требуют Lazyweb, EN/RU, previews, инспекции и verify goldens. Не обновлять goldens ради скрытия регрессий.
7. Провести независимое delta acceptance по R1–R5, затем закрыть отдельными актуальными доказательствами TalkBack и macOS/iOS проверки. Публикация и создание внешних проектов не входят в задачу.

До выполнения этих пунктов: implementation complete — **нет**, release ready — **нет**. Успешная существующая тестовая матрица сама по себе не опровергает найденные непокрытые сценарии.
