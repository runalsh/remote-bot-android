# Specification: Complete Android App UI Localization (i18n)

## 1. Goal and Core Rules
You must complete full English (EN) and Russian (RU) localization for all remaining UI screens in the Android application.

### Strict Project Mandates:
1. **Zero Cyrillic Comments**: All code comments, docstrings, commit messages, and log messages in Kotlin files MUST be in **English only**.
2. **Default Fallback is English**:
   - `app/src/main/res/values/strings.xml` contains the default English (`EN`) strings. Non-Russian system locales (e.g. Chinese `zh`, German `de`, French `fr`) automatically fallback to this file.
   - `app/src/main/res/values-ru/strings.xml` contains Russian (`RU`) translations with the exact same resource keys.
3. **No Hardcoded UI Strings in Kotlin**: All user-visible titles, card labels, placeholders, tooltips, dialogs, button texts, and toast messages must use `stringResource(R.string.<key>)` or `context.getString(R.string.<key>)`.
4. **Formatting with Placeholders**: Use `%1$s`, `%2$d`, etc., in XML for variable interpolation and pass arguments into `stringResource(R.string.key, arg1, arg2)`.
5. **No Breaking Changes**: Keep state logic, callbacks, flow collectors, and coroutine launches unchanged. Only replace raw string literals with string resources.
6. **Verification Command**: After editing, verify compilation with `make check-compile` (or `./gradlew :app:compileDebugKotlin`).

---

## 2. Status of Localization Across Tabs

| Tab / Screen | File Path | Current Status | Action Required |
|---|---|---|---|
| **Start** | `app/.../ui/screens/StartTab.kt` | ✅ Completed | None |
| **About** | `app/.../ui/screens/AboutTab.kt` | ✅ Completed | None |
| **Tasks** | `app/.../ui/screens/ActiveTasksTab.kt` | ✅ Completed | None |
| **Logs** | `app/.../ui/screens/LogsTab.kt` | ⚠️ Partially translated | Translate log level filter chips, clear button, auto-scroll switch |
| **SMS** | `app/.../ui/screens/SmsSettingsTab.kt` | ✅ Completed | None |
| **Proxy** | `app/.../ui/screens/ProxySettingsTab.kt` | ✅ Completed | None |
| **Telegram** | `app/.../ui/screens/TelegramSettingsTab.kt` | ⚠️ Partially translated | Translate user subscriptions, API mirrors/Failover, storage folder, push webhook test |
| **Notifications** | `app/.../ui/screens/NotificationsSettingsTab.kt` + `notifications/AppPickerDialog.kt` | ❌ Not translated | Full extraction to XML |
| **Permissions** | `app/.../ui/screens/PermissionsTab.kt` + `permissions/*` | ❌ Not translated | Full extraction to XML |
| **Push** | `app/.../ui/screens/FcmSettingsTab.kt` | ❌ Not translated | Full extraction to XML |
| **Backup** | `app/.../ui/screens/BackupTab.kt` | ❌ Not translated | Full extraction to XML |
| **Debug** | `app/.../ui/screens/debug/DebugTab.kt` | ❌ Not translated | Full extraction to XML |

---

## 3. Detailed Screen Breakdown & Target Keys

### 3.1. `TelegramSettingsTab.kt` (Finish remaining sections)
**Lines ~255 to 830**:
1. **Whitelist & Subscriptions**:
   - `"Команды выполняются ТОЛЬКО для указанных ID..."` ➔ `R.string.tg_whitelist_desc`
     - EN: `"Commands are executed ONLY for specified IDs (others get rejected):"`
     - RU: `"Команды выполняются ТОЛЬКО для указанных ID (чужие получают отказ):"`
   - Subscription chips:
     - `"🔔 Push"` ➔ `R.string.tg_sub_push`
     - `"💬 SMS"` ➔ `R.string.tg_sub_sms`
     - `"📞 Звонки"` ➔ `R.string.tg_sub_calls` (EN: `"📞 Calls"`, RU: `"📞 Звонки"`)
     - `"⚙️ Сист"` ➔ `R.string.tg_sub_system` (EN: `"⚙️ Sys"`, RU: `"⚙️ Сист"`)
     - `"🕹 Режим: только управление (события не пересылаются)"` ➔ `R.string.tg_sub_control_only`
2. **Connection Mode & Endpoints**:
   - `"3. Режим приема команд"` ➔ `R.string.tg_connection_mode_title` (EN: `"3. Command Reception Mode"`, RU: `"3. Режим приема команд"`)
   - Push Wakeup params card labels:
     - `"Параметры Push Wakeup:"` ➔ `R.string.tg_push_params_title`
     - `"• Шлюз (Gateway): %1$s"` ➔ `R.string.tg_push_gateway_label`
     - `"• Секретный ключ: %1$s"` ➔ `R.string.tg_push_secret_label`
     - `"• Firebase Project: %1$s"` ➔ `R.string.tg_push_project_label`
   - Mirror / Failover inputs:
     - `"Основной API Root / Зеркало (по умолч. пусто)"` ➔ `R.string.tg_api_root_label`
     - `"Резервные API эндпоинты (Failover)"` ➔ `R.string.tg_failover_title`
     - `"Стратегия выбора эндпоинта:"` ➔ `R.string.tg_strategy_title`
     - Strategies: `"Failover (Sequential)"` / `"По порядку"`, `"Round-Robin"`, `"Lowest Latency"` / `"Мин. пинг"`
3. **Storage Directory & Verification**:
   - `"4. Папка сохранения медиа и файлов"` ➔ `R.string.tg_storage_title`
   - `"📁 Выбрать папку..."` ➔ `R.string.tg_btn_choose_folder` (EN: `"📁 Choose folder..."`)
   - `"Сброс"` ➔ `R.string.common_reset` (EN: `"Reset"`, RU: `"Сброс"`)
   - `"Проверка связи..."` ➔ `R.string.tg_checking_connection` (EN: `"Checking connection..."`)
   - `"Сохранить настройки бота"` ➔ `R.string.tg_btn_save` (EN: `"Save bot settings"`)

---

### 3.2. `NotificationsSettingsTab.kt` & `AppPickerDialog.kt`
1. **Header & Channels Card**:
   - `"📡 Каналы пересылки уведомлений"` ➔ `R.string.notif_channels_title` (EN: `"📡 Notification Forwarding Channels"`, RU: `"📡 Каналы пересылки уведомлений"`)
   - `"Выберите, куда пересылать каждый тип событий..."` ➔ `R.string.notif_channels_desc`
   - Events:
     - `"📨 Входящие SMS"` ➔ `R.string.notif_event_sms`
     - `"📞 Пропущенные звонки"` ➔ `R.string.notif_event_calls`
     - `"🔔 Уведомления приложений (Push)"` ➔ `R.string.notif_event_apps`
     - `"⚡ Подключение зарядки"` ➔ `R.string.notif_event_charging`
     - `"🪫 Низкий заряд батареи (≤15%)"` ➔ `R.string.notif_event_battery_low`
2. **App Whitelist & Filters**:
   - `"🎯 Фильтр приложений (Whitelist)"` ➔ `R.string.notif_apps_title`
   - `"⛔ Стоп-слова (черный список)"` ➔ `R.string.notif_stopwords_title`
   - `"⏱️ Дедупликация и лимиты"` ➔ `R.string.notif_dedup_title`
   - `"Интервал дедупликации (сек)"` ➔ `R.string.notif_dedup_interval`
   - `"Лимит сообщений в минуту"` ➔ `R.string.notif_rate_limit`
   - `"Сохранить настройки уведомлений"` ➔ `R.string.notif_save_btn`
3. **`AppPickerDialog.kt`**:
   - Dialog title: `"Выбор приложений для пересылки"` ➔ `R.string.app_picker_title`
   - Search placeholder: `"Поиск по названию или пакету..."` ➔ `R.string.app_picker_search`
   - Selection buttons: `"Выбрать все"` ➔ `R.string.common_select_all`, `"Снять выбор"` ➔ `R.string.common_clear_selection`

---

### 3.3. `PermissionsTab.kt` & `permissions/*`
1. **Wizard Header**:
   - `"Разрешения приложения"` ➔ `R.string.perms_title` (EN: `"App Permissions"`, RU: `"Разрешения приложения"`)
   - `"Все разрешения выданы (%1$d из %2$d)"` ➔ `R.string.perms_all_granted`
   - `"Предоставлено %1$d из %2$d разрешений"` ➔ `R.string.perms_count_granted`
   - `"⚡ Выдать все права через Root"` ➔ `R.string.perms_btn_grant_root`
   - `"⚡ Запросить все разрешения вручную"` ➔ `R.string.perms_btn_request_all`
2. **`PermissionItemCard.kt`**:
   - Status badge: `"✅ ВЫДАНО"` ➔ `R.string.perms_status_granted` (EN: `"✅ GRANTED"`, RU: `"✅ ВЫДАНО"`)
   - Status badge: `"❌ НЕТ"` ➔ `R.string.perms_status_missing` (EN: `"❌ MISSING"`, RU: `"❌ НЕТ"`)
   - `"Команды: %1$s"` ➔ `R.string.perms_commands_hint`
3. **Sections**:
   - `CriticalPermissionsSection.kt`:
     - Section title: `"1. Системный доступ (Root и Установка)"` ➔ `R.string.perms_section_critical`
     - Permission items: Root, Install Unknown Apps, Manage All Files, Accessibility, Write System Settings, Display Over Other Apps.
   - `ConfidentialPermissionsSection.kt`:
     - Section title: `"2. Конфиденциальные разрешения"` ➔ `R.string.perms_section_confidential`
     - Items: Notification Listener, SMS Read/Send, Call Phone/Call Log, Do Not Disturb Access.
   - `StandardPermissionsSection.kt`:
     - Section title: `"3. Стандартные разрешения (Runtime)"` ➔ `R.string.perms_section_standard`
     - Items: Camera, Microphone, GPS/Location, Contacts, Battery Optimization, Media Photos/Audio, Bluetooth.
4. **Dialog**:
   - Dialog title: `"Ограничения Android"` ➔ `R.string.perms_dialog_restrictions_title`
   - Dialog description: `"Обычные разрешения получены.\n\nИз-за ограничений безопасности Android..."` ➔ `R.string.perms_dialog_restrictions_desc`
   - Button: `"Понятно"` ➔ `R.string.common_got_it` (EN: `"Got it"`, RU: `"Понятно"`)

---

### 3.4. `FcmSettingsTab.kt` (Push Gateway)
1. **Push Master Toggle**:
   - Title: `"🔥 Push-уведомления и шлюз"` ➔ `R.string.push_master_title` (EN: `"🔥 Push Notifications & Gateway"`)
   - Desc: `"Включен: прием push-команд через внешние сервисы"` ➔ `R.string.push_master_desc`
2. **Provider Selection**:
   - Provider options: `"FCM (Firebase Cloud Messaging)"`, `"HMS (Huawei Mobile Services)"`, `"Hybrid (FCM + HMS)"`
3. **Firebase Configuration**:
   - Title: `"Конфигурация Firebase (google-services.json)"` ➔ `R.string.push_firebase_title`
   - Button: `"Загрузить google-services.json"` ➔ `R.string.push_btn_import_json`
   - Fields: App ID, API Key, Project ID, Sender ID.
4. **Gateway Configuration**:
   - Title: `"Внешний шлюз (Push Gateway)"` ➔ `R.string.push_gateway_title`
   - Field: `"URL шлюза"` ➔ `R.string.push_gateway_url_label`
   - Field: `"Секретный ключ (Push Secret)"` ➔ `R.string.push_secret_key_label`
   - Button: `"Проверить связь со шлюзом"` ➔ `R.string.push_btn_test_gateway`
   - Button: `"Сохранить настройки Push"` ➔ `R.string.push_btn_save`

---

### 3.5. `BackupTab.kt`
1. **Export Section**:
   - Title: `"💾 Резервное копирование (Экспорт)"` ➔ `R.string.backup_export_title`
   - Desc: `"Сохранение всех настроек бота, фильтров и конфигурации в файл .json"` ➔ `R.string.backup_export_desc`
   - Button: `"Экспортировать настройки в файл"` ➔ `R.string.backup_btn_export`
2. **Import Section**:
   - Title: `"📥 Восстановление (Импорт)"` ➔ `R.string.backup_import_title`
   - Desc: `"Восстановление конфигурации из ранее сохраненного файла"` ➔ `R.string.backup_import_desc`
   - Button: `"Выбрать файл для импорта..."` ➔ `R.string.backup_btn_import`
3. **Reset Section**:
   - Title: `"⚠️ Сброс настроек"` ➔ `R.string.backup_reset_title`
   - Desc: `"Сброс всех настроек приложения к значениям по умолчанию"` ➔ `R.string.backup_reset_desc`
   - Button: `"Сбросить все настройки"` ➔ `R.string.backup_btn_reset`
   - Confirmation dialog: `"Вы уверены, что хотите сбросить все параметры?"` ➔ `R.string.backup_confirm_reset`

---

### 3.6. `DebugTab.kt` & Subcomponents
1. **Header & Warnings**:
   - Title: `"🛠️ Инженерное меню (Debug)"` ➔ `R.string.debug_title`
   - Subtitle: `"Диагностика датчиков, камер, Doze и компонентов бота"` ➔ `R.string.debug_subtitle`
   - Button: `"✕ Отключить Debug"` ➔ `R.string.debug_btn_disable`
2. **Test Cards**:
   - `"Тестирование камеры и фонарика"` ➔ `R.string.debug_test_camera`
   - `"Тестирование аудиозаписи"` ➔ `R.string.debug_test_audio`
   - `"Тестирование GPS и геолокации"` ➔ `R.string.debug_test_location`
   - `"Тестирование состояния батареи и Doze"` ➔ `R.string.debug_test_battery`
   - `"Тестирование выполнения Root shell"` ➔ `R.string.debug_test_root`
   - `"Проверка сетевых сокетов и HTTP/WSS"` ➔ `R.string.debug_test_network`

---

## 4. Execution Workflow for the Subagent / Model

1. **Step 1**: Add string pairs in `app/src/main/res/values/strings.xml` (EN) and `app/src/main/res/values-ru/strings.xml` (RU). Keep keys identical in both files.
2. **Step 2**: Edit screen Kotlin files (`TelegramSettingsTab.kt`, `NotificationsSettingsTab.kt`, `PermissionsTab.kt`, `FcmSettingsTab.kt`, `BackupTab.kt`, `DebugTab.kt`).
3. **Step 3**: Ensure `import androidx.compose.ui.res.stringResource` and `import com.remotebot.app.R` are present at the top of each edited Kotlin file.
4. **Step 4**: Run verification:
   ```bash
   make check-compile
   ```
   Confirm that all 109 tasks build successfully and there are no unresolved resource reference errors.
5. **Step 5**: Hand over the changes for Antigravity code review.

