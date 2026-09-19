# Session context — tommynok/Monica fork

Working copy of a fork of `Monica-Pass/Monica` (upstream, author JoyinJoester,
very active, commits at all hours). Remotes: `origin` = tommynok/Monica (our
fork), `upstream` = Monica-Pass/Monica (original). Everything below is current
as of 2026-09-19, `upstream/main` at commit `6e10340d`.

User is not a coder — they drive this by screenshots and plain-language
descriptions of visual/translation bugs. Explanations to them should stay in
Russian and non-technical; this file is my own working memory, in English.

## General workflow (established over the session)

- **Never work off a stale branch** — always `git fetch upstream main` before
  starting new work; branches are created/rebased from `upstream/main`.
- **One topic = one branch.** PRs are never opened without the user explicitly
  asking (they have no API access to the upstream repo via GitHub MCP — only
  `origin` is attached; they open PRs themselves via a compare URL:
  `https://github.com/Monica-Pass/Monica/compare/main...tommynok:Monica:BRANCH?expand=1`).
- **Hardcode (literal text baked into code, not a missing translation) — do
  NOT touch reflexively.** Small one-line fixes are fine (CSV error message,
  "ALL" title). Whole screens hardcoded in Chinese (Bitwarden Settings, ~50
  strings) — leave alone, just report the finding and wait for the author's
  call. The author routinely fixes this kind of thing himself (Dedup Engine,
  the "ALL" title, the RU autofill-protection translation — all closed by him
  while we were working in parallel).
- **Every time upstream moves, re-audit every open branch.** The author may
  have: (a) fixed the same thing himself a different way → our branch is dead,
  drop it; (b) rewritten the surrounding screen → our diff won't apply
  cleanly, reapply by hand and check whether the bug even still exists
  (example: WebDAV test-connection button — in WebDavBackupScreen.kt the
  author built a new full-width `CloudBackupPrimaryButton` component and the
  text-wrap bug disappeared on its own, so we dropped our fix there; in
  LocalKeePassWebDavBrowser.kt the bug was untouched, so we reapplied it).
- Test builds only via `workflow_dispatch` on `Android-Preview.yml`
  (`mcp__github__actions_run_trigger`, `owner: tommynok, repo: Monica`). That's
  the debug-signed preview channel (floating `preview` tag), never touches
  `Android.yml` (the stable release workflow, only fires on a `v*.*.*` tag,
  the author doesn't run it manually).
- For user testing, **don't build every branch separately** — maintain one
  combined branch `test/combined-all-fixes` (rebuilt from upstream/main + merge
  of all still-live fix branches) and build a single APK from it. Rebuild it
  whenever anything changes or upstream moves.

## Branch status (as of 2026-09-19)

### Merged upstream (history only, don't touch)
- `fix/quick-setup-nav-overlap` → PR #127 merged
- `fix/ru-locale-and-visual-polish` → PR #131 merged
- `fix/import-dedup-nav-and-csv-error` / `claude/serene-einstein-z9dr8v` → PR #134 merged

### Dead — author solved it a different way, do NOT send
- `fix/password-list-all-title-hardcode` — author added `legacy_ui_strings.xml`,
  translated into 11 languages (`legacy_ui_all_title`), RU = "ВСЁ".
- `i18n/ru-autofill-protection-strings` — author added his own RU translation
  of `autofill_protection_strings.xml`. Quality checked: natural, terminology
  consistent internally — nothing else in the app used those terms, so no
  cross-file drift to worry about.
- **Bitwarden Settings screen hardcode** (was flagged as unresolved earlier in
  the session, and posted about in the author's Telegram — that post is now
  stale). Re-checked on 2026-09-19: the whole screen has since been migrated
  to `stringResource(R.string.legacy_ui_*)`, fully translated into RU (and
  presumably the same other locales as the rest of `legacy_ui_strings.xml`).
  Only remaining Chinese in the file is `//`-comments, not user-visible text.
  **This is fully fixed upstream — nothing to do here anymore.**

### Live, current, waiting on the user to open PRs
All merged into `test/combined-all-fixes`, rebased onto `upstream/main` @ 6e10340d:
- `fix/card-density-totp-password-group` — density trim on TOTP list cards and
  app-grouped password cards (padding/spacing only; kept icon-button touch
  targets at 48dp — Material accessibility minimum).
- `fix/password-detail-field-padding` — padding in the "Password" block on the
  detail screen + `password_detail_storage_info` translation → "Хранилище".
- `fix/ru-time-info-translation` — `password_detail_time_info`:
  "Временные данные" → "Даты" (read like "temporary/volatile data", should be
  about creation/modification dates).
- `fix/ru-timeline-cardface-wording` — `timeline_title`/`timeline_and_trash_title`:
  "Хронология" → "История"; `card_face_customize`: "Оформление карты" →
  "Дизайн карточки" (was ambiguous — read like paperwork/document processing).
- `fix/webdav-test-connection-button-centering` — `textAlign = Center` on the
  two-line button label (only still relevant in
  `LocalKeePassWebDavBrowser.kt` — see note above about WebDavBackupScreen.kt).
- `ci/cache-rust-and-gradle-restore-keys` — cache Rust toolchain/registry +
  `rust-jni/target`, restore-keys for the Gradle cache, in both workflow files.
- `fix/ru-all-passwords-title` — `legacy_ui_all_title`: "ВСЁ" → "Все пароли"
  (only used on the passwords screen, single usage site, so specific wording
  is safe). **Known issue: user tested it, title truncates to just "Все" on
  their device — doesn't fit. Deprioritized, not reverted, left in the
  combined branch as-is for now.**
- `fix/ru-quick-setup-heading` — `qs_welcome_heading`: "Monica по-вашему" →
  "Настройте под себя" (calque of "Make Monica your own"; new wording drops
  the repeated brand name since the big "Monica" title sits right above it).

### Not merged, waiting on the user's/author's decision — not ours to fix
(none currently — the last item here, Bitwarden Settings hardcode, turned out
to be already fixed upstream; see "Dead" section above.)

## Screenshot findings already resolved

- Password detail screen: visual disproportion came from 3× `IconButton`
  (48dp default Material touch target) sharing a row with a short label —
  didn't touch the buttons themselves (accessibility), only trimmed outer
  padding; after that fix, found an asymmetry (the masked-password Text had
  its own `padding(vertical=4dp)` stacking with the outer `spacedBy(4dp)`) —
  removed the redundant padding.
- "Card face customization" wording — the ambiguous translation aside,
  `CardFaceCustomizer` is deliberately reused across bank card / document /
  billing address editors (all three are visual "cards" in the wallet UI) —
  not an architecture bug, just a naming issue we already fixed.
- Desktop: `Monica for Windows` is **archived**
  (`github.com/JoyinJoester/Monica-for-Windows`, 2 commits, just a WinUI3/.NET8
  skeleton). Author's README: solo maintainer, focus is Android, desktop was
  dropped in favor of a new browser extension (in progress). Assessed:
  reviving the Windows app is unrealistic — no shared logic with Android
  (Kotlin vs C#), and it would go directly against the author's own decision.

## Habits that stuck

- Every translation is checked programmatically before commit: key-set parity
  EN↔RU (`python3 -c "import xml.etree..."`), placeholder parity, XML
  well-formedness.
- On a rebase conflict in translated strings, resolve by hand, reading both
  sides for meaning — never blindly take "theirs".
- The author writes commit messages in English but leaves Chinese comments in
  code (`// 中文 comment`) — that's normal for internal `//` comments, not the
  same thing as hardcoded user-visible text.
