### Monica for Android 1.0.311

## 中文

### 简要

- 感谢 [@aiguozhi123456](https://github.com/aiguozhi123456) 贡献 [#133](https://github.com/Monica-Pass/Monica/pull/133)：精简扫码依赖，保留相机与相册识别，支持反色、多码识别并完善长时间扫码的恢复能力。
- 感谢 [@tommynok](https://github.com/tommynok) 贡献 [#134](https://github.com/Monica-Pass/Monica/pull/134)：修复导入页面底部操作栏被系统导航栏遮挡的问题，CSV 导入失败提示现已适配全部八种应用语言。
- 感谢 [@tommynok](https://github.com/tommynok) 贡献 [#131](https://github.com/Monica-Pass/Monica/pull/131)：补全俄语翻译、安全问题本地化，并改善较长文字的布局。
- 修复 [#128](https://github.com/Monica-Pass/Monica/issues/128)：分组样式选项和 MDBX 管理器随应用语言显示，补全英文、中文和俄语文案，切换语言后及时刷新。
- 新增卡包卡叠，支持组合收纳与上下翻阅。
- 卡包多选保留卡叠顺序，以分组底色、选中数量和独立卡片区域区分成员。
- 优化 Monica 键盘列表，新增网站地址填充。
- 新增填充服务保护，支持后台运行检查与可选的无障碍增强恢复。
- 统一下拉搜索与返回行为，减少误触。
- 修复验证器、卡包和笔记多选时按返回误触发退出应用提示的问题。
- 修复解锁页键盘 Enter／完成键无法提交密码的问题。
- 初始化时开启指纹解锁须先通过系统身份验证。
- 感谢 [@tommynok](https://github.com/tommynok) 贡献 [#127](https://github.com/Monica-Pass/Monica/pull/127)：修复初始化向导底部按钮被系统导航栏遮挡的问题（[#129](https://github.com/Monica-Pass/Monica/issues/129)），并将“跳过”按钮与长标题分行显示。
- 初始化页语言选择改为弹出菜单，展开时不再推动页面内容。
- WebDAV 同步设置新增备份数量上限，永久备份不受影响。
- 修复卡面裁剪预览底部漏图的问题。

### 详细

- Steam 与验证器共用 CameraX 相机预览和 ZXing 识别引擎，保留原有 13 种码制、反色二维码和小码识别；同一画面或图片中的多个码交由页面筛选。解码在后台完成，释放相机帧后在主线程处理结果，扫码会话支持中断恢复与前后台切换。
- [@tommynok](https://github.com/tommynok) 的 [#131](https://github.com/Monica-Pass/Monica/pull/131) 补充俄语界面翻译并统一术语；预设安全问题现在随应用语言显示，保留原有问题编号和自定义问题。权限卡片、预设字段对话框及分段按钮为较长文字预留空间，减少文字挤压、异常换行和按钮高度不一致。
- 分组方式的标题、说明和预览标签改用语言资源。MDBX 本地与远程管理、创建与打开、迁移、历史、快照、健康诊断、修复及确认提示移除中文硬编码，其他尚无译文的语言使用英文回退。历史日期遵循当前应用语言；切换语言会刷新缓存的展示文案，保留原有分组标识、用户内容和数据库操作逻辑。
- 多选卡片即可创建或加入卡叠，无需命名；卡叠置顶，普通卡片保留原有排序。
- 展开后可上下翻阅，支持阻尼动效与一键收起，并以当前卡片作为封面。
- 卡面提供数量和管理入口，可调整顺序、移出卡片或解散卡叠。
- 多选时按卡叠显示顺序展开成员，组内沿用已保存的排列。每个卡叠使用连续底色和独立分组头，显示“已选 / 总数”，支持整组选择及进入卡叠管理；独立卡片保留原有排序并单独分区。筛选时整组选中仅作用于当前显示的成员；取消全部选择后仍可继续选其他卡片，按返回退出多选。
- Monica 键盘的密码、验证器和卡包统一列表样式，筛选和搜索与卡片对齐，为滑动条留出独立空间。
- 密码条目新增“网站”按钮，一键填入已保存的网址。
- 填充服务保护支持开机解锁后恢复、无障碍连接检查和自启动设置指引；可在了解风险后授权 Shizuku ADB 增强恢复。
- 修正授权确认复选框的间距，避免选中反馈遮挡说明文字。
- 下拉到位后停留 1.5 秒开启搜索，从列表下方滑回顶部时不触发；返回一次即可收起键盘和搜索框，恢复顶部操作按钮。
- 验证器、卡包和笔记多选时，系统返回键与退出多选按钮共用清理逻辑，先取消选择并恢复普通列表。
- 解锁时可直接按键盘 Enter／完成键提交密码，与确认按钮共用校验逻辑；首次设置密码也可用键盘完成下一步和确认。
- 初始化页开启生物识别解锁前先验证身份，取消或验证失败时保持关闭；未录入指纹或面容时显示设置提示。
- 包含 [@tommynok](https://github.com/tommynok) 的 [#127](https://github.com/Monica-Pass/Monica/pull/127) 修复：初始化向导底部操作栏避让系统导航栏，避免“开始”“上一步”“下一步”和“完成”按钮被遮挡，覆盖 [#129](https://github.com/Monica-Pass/Monica/issues/129)；“跳过”按钮与后续步骤的长标题分行显示。
- 欢迎页的语言列表改为锚定“更改”按钮的可滚动弹出菜单，标记当前语言；选中语言、点击菜单外部或按返回即可收起，展开时保持页面布局稳定。
- 可在 WebDAV 右上角的同步设置中开启数量限制，设置保留 1–1000 份普通备份；每次上传成功后自动清理超出的旧备份，永久备份单独保留且不占额度。未开启时沿用原有按时间清理规则。
- 卡面裁剪时将图片限制在预览区域内，竖图、放大和拖动时均不会溢出到顶部工具栏或底部提示区。

## English

### Summary

- Thanks to [@aiguozhi123456](https://github.com/aiguozhi123456) for [#133](https://github.com/Monica-Pass/Monica/pull/133): reduced scanner dependencies while retaining camera and gallery scanning, inverted and multiple-code recognition, and recovery during extended scanning sessions.
- Thanks to [@tommynok](https://github.com/tommynok) for [#134](https://github.com/Monica-Pass/Monica/pull/134): the import action bar now respects system navigation insets, and CSV import failure messages support all eight app languages.
- Thanks to [@tommynok](https://github.com/tommynok) for [#131](https://github.com/Monica-Pass/Monica/pull/131): expanded Russian translations, localized security questions, and improved layouts for longer labels.
- Fixed [#128](https://github.com/Monica-Pass/Monica/issues/128): grouping options and the MDBX manager follow the app language, with English, Chinese, and Russian text that refreshes after language changes.
- Added wallet card stacks for grouped storage and vertical browsing.
- Wallet selection preserves stack order, with distinct group backgrounds, selection counts, and a separate section for individual cards.
- Refined Monica Keyboard lists and added website address filling.
- Added fill service protection with background checks and optional accessibility recovery.
- Unified pull-to-search and Back behavior to prevent accidental activation.
- Fixed Back showing the app-exit prompt while selecting authenticators, wallet items, or notes.
- Fixed password submission with the keyboard Enter/Done key on the unlock screen.
- Initial setup now requires system authentication before enabling biometric unlock.
- Thanks to [@tommynok](https://github.com/tommynok) for [#127](https://github.com/Monica-Pass/Monica/pull/127): fixed setup buttons overlapping the system navigation bar ([#129](https://github.com/Monica-Pass/Monica/issues/129)) and placed Skip on its own row above longer step titles.
- Language selection during setup now opens a popup menu without shifting page content.
- Added a WebDAV backup count limit in Sync settings, with permanent backups exempt.
- Fixed image overflow below the card-face crop preview.

### Details

- Steam and the authenticator share CameraX preview and ZXing decoding, retaining all 13 barcode formats, inverted QR codes, and small-code recognition. Multiple candidates from the camera or gallery are filtered by the calling screen. Decoding stays in the background; frames are released before main-thread result delivery, with camera recovery and support for background/resume transitions.
- [@tommynok](https://github.com/tommynok)'s [#131](https://github.com/Monica-Pass/Monica/pull/131) fills gaps in Russian translations and makes terminology consistent. Preset security questions now follow the app language while retaining existing question IDs and custom questions. Permission cards, preset-field dialogs, and segmented buttons give longer text enough room, reducing cramped labels, awkward wrapping, and mismatched button heights.
- Grouping titles, descriptions, and preview labels now use language resources. Local and remote MDBX management, create/open flows, migration, history, snapshots, health diagnostics, repairs, and confirmation dialogs no longer use hardcoded Chinese. Languages without a translation fall back to English. History dates follow the app locale, and cached presentation text refreshes when the language changes while grouping identifiers, user content, and database operations retain their existing behavior.
- Select cards to create or join a stack without naming it. Stacks appear first; individual cards retain their sort order.
- Browse vertically with damped motion and collapse with one tap. The current card becomes the cover.
- View the card count and manage members directly from the cover: reorder, remove, or dissolve the stack.
- Selection expands stacks in their browsing order and preserves each stack’s saved member order. A shared background and group header identify each stack, show selected/total counts, and provide group selection and stack management. Individual cards retain their ordering in a separate section. Group selection under a filter affects only visible members; clearing all selections keeps selection mode open, and Back exits it.
- Passwords, authenticators, and wallet items share a consistent keyboard layout, with filters and search aligned to the cards and dedicated space for fast scrolling.
- Fill a saved website address using the new Website action on password entries.
- Fill service protection supports resuming after reboot and unlock, accessibility connection checks, and auto-start guidance. Optional Shizuku ADB recovery requires reviewing the access risks.
- Adjusted the authorization acknowledgement checkbox spacing so its selection feedback stays clear of the label.
- Hold a pull for 1.5 seconds to open search. Scrolling back to the top does not trigger it, and one Back action closes both the keyboard and search bar and restores toolbar actions.
- System Back in authenticator, wallet, and note selection mode uses the same cleanup as the selection toolbar: clear the selection and return to the normal list.
- Submit the unlock password with the keyboard Enter/Done key using the same validation as the confirmation button. Initial password setup also supports keyboard Next and Done actions.
- Enabling biometric unlock during initial setup now requires identity verification. Cancelling or failing authentication leaves it off, and devices without enrolled biometrics show setup guidance.
- Includes [@tommynok](https://github.com/tommynok)'s [#127](https://github.com/Monica-Pass/Monica/pull/127): the setup action bar respects system navigation insets so Start, Previous, Next, and Finish remain accessible, covering [#129](https://github.com/Monica-Pass/Monica/issues/129). Skip also appears on its own row above longer step titles.
- The welcome page shows languages in a scrollable popup anchored to Change, with the current language marked. Selecting a language, tapping outside, or pressing Back dismisses the menu without changing the underlying page layout.
- Enable a limit of 1–1000 regular backups in the WebDAV page’s top-right Sync settings. Older excess backups are removed after each successful upload; permanent backups are retained separately and do not count toward the limit. When disabled, the existing age-based cleanup rules apply.
- Card-face images stay within the crop preview when using portrait images, zooming, or panning, keeping the toolbar and footer clear.
