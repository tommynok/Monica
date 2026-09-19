# Контекст сессии — форк tommynok/Monica

Рабочая копия форка `Monica-Pass/Monica` (апстрим, автор JoyinJoester, очень активен,
коммитит в любое время суток). Ремоуты: `origin` = tommynok/Monica (наш форк),
`upstream` = Monica-Pass/Monica (оригинал). Всё описанное ниже актуально на
2026-09-19, `upstream/main` на коммите `6e10340d`.

## Общий воркфлоу (сложился за сессию)

- **Никогда не работать от старой ветки** — перед новыми правками всегда
  `git fetch upstream main`, ветки создавать/ребейзить от `upstream/main`.
- **Одна тема = одна ветка**, PR не создаются без явной просьбы пользователя
  (у него нет доступа к API upstream-репо через GitHub MCP — только `origin`
  attach'нут; PR открывает сам через compare-ссылку
  `https://github.com/Monica-Pass/Monica/compare/main...tommynok:Monica:BRANCH?expand=1`).
- **Хардкод (не missing translation, а буквально зашитый в код текст) —
  НЕ трогаем бездумно.** Точечные однострочные фиксы — ок (пример: CSV error
  message, "ALL" title). Целые экраны на китайском (Bitwarden Settings,
  ~50 строк) — не лезем, просто фиксируем находку и ждём решения автора.
  Автор уже сам чинит подобное (Dedup Engine, "ALL" title, autofill RU-перевод —
  всё это он закрыл сам, пока мы работали параллельно).
- **Каждый апстрим-скачок — целиком перепроверять все открытые ветки**:
  автор мог: (а) сам починить то же самое другим способом → наша ветка
  мертва, выкидываем; (б) переписать экран рядом → наш diff не накладывается
  чисто, надо переналожить руками, проверяя, не решилась ли проблема
  архитектурно сама (пример: WebDAV test-connection button — в
  WebDavBackupScreen.kt автор сделал новый full-width компонент
  `CloudBackupPrimaryButton`, баг с переносом текста пропал сам, фикс убрали;
  в LocalKeePassWebDavBrowser.kt баг остался — фикс перенесли).
- Тестовые сборки — только `workflow_dispatch` на `Android-Preview.yml`
  (`mcp__github__actions_run_trigger`, `owner: tommynok, repo: Monica`).
  Это debug-подписанный preview-канал (плавающий тег `preview`), НЕ
  трогает `Android.yml` (тот — стабильный релиз, триггерится только тегом
  `v*.*.*`, автор им не пользуется вручную).
- Для тестирования пользователем **не гонять билд на каждую ветку отдельно** —
  собирать сводную ветку `test/combined-all-fixes` (rebuild от upstream/main +
  merge всех живых fix-веток) и билдить один APK. Пересобирать при каждой
  правке/ресинке.

## Статус веток (на 2026-09-19)

### Смёржено в апстрим (не трогать, история)
- `fix/quick-setup-nav-overlap` → PR #127 merged
- `fix/ru-locale-and-visual-polish` → PR #131 merged
- `fix/import-dedup-nav-and-csv-error` / `claude/serene-einstein-z9dr8v` → PR #134 merged

### Мертво — автор сам решил иначе, НЕ отправлять
- `fix/password-list-all-title-hardcode` — автор завёл `legacy_ui_strings.xml`,
  перевёл на 11 языков (`legacy_ui_all_title`), RU = "ВСЁ".
- `i18n/ru-autofill-protection-strings` — автор сам добавил RU-перевод
  `autofill_protection_strings.xml`, качество хорошее (проверяли: естественный
  язык, терминология единообразна, сверять не с чем — единственное место
  в приложении с этими терминами).

### Живые, актуальные, ждут PR от пользователя
Все влиты в `test/combined-all-fixes`, ребейзнуты на `upstream/main` @ 6e10340d:
- `fix/card-density-totp-password-group` — плотность карточек TOTP-списка и
  сгруппированных по приложению паролей (padding/spacing trim, тач-таргеты
  кнопок оставлены 48dp — accessibility-минимум Material).
- `fix/password-detail-field-padding` — padding в блоке "Пароль" на экране
  деталей + перевод `password_detail_storage_info` → "Хранилище".
- `fix/ru-time-info-translation` — `password_detail_time_info`:
  "Временные данные" → "Даты" (было "temporary data", должно быть про
  даты создания/изменения).
- `fix/ru-timeline-cardface-wording` — `timeline_title`/`timeline_and_trash_title`:
  "Хронология" → "История"; `card_face_customize`: "Оформление карты" →
  "Дизайн карточки" (было двузначно — путалось с "оформлением документов").
- `fix/webdav-test-connection-button-centering` — `textAlign = Center` на
  Text двухстрочной кнопки (актуально только для
  `LocalKeePassWebDavBrowser.kt`, в `WebDavBackupScreen.kt` баг исчез сам
  после редизайна автора).
- `ci/cache-rust-and-gradle-restore-keys` — кеш Rust toolchain/registry +
  `rust-jni/target`, restore-keys для Gradle-кеша, в обоих workflow-файлах.

### Не смёржено, требует решения пользователя (не мы)
- Bitwarden Settings screen — ~50 строк хардкода на китайском
  (`BitwardenSettingsScreen.kt`), целый экран, не тронуто. Пользователь
  написал автору в Telegram-канал (топик "问题反馈"/Issue Feedback), ждём
  ответа. **Не начинать чинить самостоятельно без явной отмашки.**

## Скриншот-находки, уже разобранные

- Экран деталей пароля: диспропорция из-за 3× `IconButton` (48dp default
  touch target) в одной строке с коротким лейблом — не трогали сами кнопки
  (accessibility), подрезали только внешние отступы; после правки нашлась
  асимметрия (свой `padding(vertical=4dp)` на тексте с точками пароля
  дублировался с внешним `spacedBy(4dp)`) — убрали дублирующий padding.
- "Оформление карты" — двусмысленный перевод, компонент `CardFaceCustomizer`
  переиспользуется для банковских карт/документов/платёжных адресов
  осознанно (все три — визуальные "карточки" в кошельке), не баг архитектуры.
- Десктопная версия: `Monica for Windows` **архивирован**
  (`github.com/JoyinJoester/Monica-for-Windows`, 2 коммита, только скелет
  WinUI3/.NET8). Автор пишет в README: соло-мейнтейнер, фокус на Android,
  desktop заброшен в пользу нового browser extension (в разработке).
  Оценили: "доделать" Windows-версию нереалистично — там нет переиспользуемой
  логики с Android (Kotlin vs C#), и это противоречит явному решению автора.

## Технические привычки, которые прижились

- Переводы всегда проверяются программно: сверка ключ-в-ключ EN↔RU
  (`python3 -c "import xml.etree..."`), плейсхолдеры, XML well-formed —
  до коммита.
- При конфликте `git rebase` на строках перевода — решать вручную,
  сверяя оба варианта по смыслу (не брать "чужую" версию не глядя).
- Автор пишет коммиты и на английском, и с китайскими комментариями в коде
  (`// 中文 комментарий`) — это нормально для служебных `//`-комментариев,
  не путать с хардкодом видимого пользователю текста.
