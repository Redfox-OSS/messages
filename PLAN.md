# Messages App — Plan v1.0

> Status: **Final. Decisions locked (§14). M0 done; M2 read path done (2026-10-17): existing SMS/MMS load from the system store and run on the S23 (Samsung Messages stays default). Threads are aggregated from the `sms`/`mms` tables because Samsung rewrites `mms-sms/conversations`. Pagination (2026-10-17): the inbox pages from a Room index kept in sync in the background (1,298 threads on the S23, about 1.3–1.7 s per pass, off the UI path); threads keyset-page straight from the store. The nav bar compacts on scroll (`CompactFloatingNavBar` in `:core:ui`, following FLOATING_NAV_BAR_COMPACT_ON_SCROLL.md). RCS chatbot cards (2026-10-17): Samsung stores RBM business messages as MMS (`application/vnd.gsma.botmessage` parts, or text parts holding the same JSON). They are parsed (`RichMessageParser`) and rendered as cards; images always load, and reply buttons are hidden (no RCS). FTS lands with search.** Where §14 differs from earlier sections (for example the tab count), §14 wins.
> Design source of truth: `/Users/debz/debz/Alarmzy/obsidian/README.md` + `dev.redfox.obsidian` source.

---

## 1. Goal & Scope

A personal, **SMS + MMS only** (no RCS) Android messaging app that fully replaces Google Messages / Samsung Messages as the **default SMS app** on:

| Device | OS (expected) | Notes |
|---|---|---|
| Samsung Galaxy S23 | One UI 7/8 (Android 15/16) | Dual SIM / eSIM, aggressive battery mgmt |
| OnePlus 13 | OxygenOS 15/16 (Android 15/16) | Dual SIM, very aggressive background killing. Obsidian's nav bar is OxygenOS-styled — feels native here |

**Non-goals:** RCS, cloud sync server, light theme (Obsidian is always dark), Material You dynamic colour.

---

## 2. Tech Stack (aligned with Obsidian / Alarmzy toolchain)

| Concern | Choice |
|---|---|
| Kotlin | **2.2.10** (KSP `2.2.10-2.0.2`) |
| AGP | 9.2.1 |
| Compose | **BOM 2026.02.01** (Compose 1.10) |
| Design system | **`dev.redfox:obsidian:1.0.0`** from `mavenLocal()` (already published to `~/.m2`). Brings M3 + **Haze 1.7.2** as `api` — do **not** upgrade Haze to 2.x (needs Compose 1.12 / Kotlin 2.4) |
| SDK | **minSdk 33** (≥ 31 required by Obsidian), target/compile 36 |
| DI | Hilt 2.59.2 |
| Async | Coroutines + Flow |
| DB | Room 2.7.x + FTS4 |
| Settings | DataStore (Preferences behind a typed `SettingsDataSource`; Proto deferred until settings outgrow it) |
| Navigation | Navigation 3 (we own the back stack → we know push vs pop, which drives `RevealScope(enabled)`) |
| Background | WorkManager + exact alarms |
| MMS PDU | Vendored AOSP `com.google.android.mms.pdu` (Apache 2.0) |
| Images / media | Coil 3.3, Media3 ExoPlayer |
| On-device ML | ML Kit (Entity Extraction, Smart Reply, Language ID, Translation) |
| Collections | kotlinx.collections.immutable |
| Tests | JUnit4 (Robolectric needs it), Turbine, MockK, Robolectric, Compose UI test, Roborazzi |

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); mavenLocal() }
}
// core/ui/build.gradle.kts
dependencies { api("dev.redfox:obsidian:1.0.0") }   // via version catalog alias `obsidian`
```

---

## 3. Architecture: Clean Architecture + MVI + MVVM hybrid

### 3.1 Layers

```
presentation  Screen ──Intent──▶ ViewModel ──State/Effect──▶ Screen
                                    │ calls
domain        UseCases ─▶ Repository interfaces, Domain models   (pure Kotlin)
                                    │ implemented by
data          RepositoryImpl ─▶ Telephony provider, Room, Contacts, SmsManager, DataStore, ML Kit
```

### 3.2 The hybrid rule

Every ViewModel is **MVVM outside** (`StateFlow<UiState>`, lifecycle-scoped) and **MVI inside** (single `dispatch`, reducer, effect channel):

```kotlin
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEffect>(initial: S) : ViewModel() {
    val state: StateFlow<S>
    val effects: Flow<E>               // navigation, snackbars, haptics, clipboard
    fun dispatch(intent: I)
    protected abstract suspend fun handle(intent: I)
    protected fun reduce(block: S.() -> S)
    protected fun emit(effect: E)
}
```

| Screen type | Flavour | Screens |
|---|---|---|
| Interaction-heavy (selection, optimistic updates, undo) | **Full MVI**: sealed Intent, pure reducer, Effects | Inbox, Thread + Composer, New conversation, Search |
| Mostly read + toggles | **Lightweight MVVM**: state = `combine(repo flows)`, thin intents | Settings, Conversation info, Starred, Scheduled, Blocking, Archive, Backup |

Rules:
1. Composables only **dispatch** intents. They make no use-case or repository calls.
2. `UiState` is an `@Immutable` data class that uses `ImmutableList`.
3. One-off events go through `Effect` and never live in state.
4. ViewModels call **use cases only**.
5. Every screen is split into `XxxRoute` (VM + effects) and `XxxScreen(state, onIntent)` (stateless, previewable, screenshot-tested).

---

## 4. Module Structure

```
:app                     Application, MainActivity, root theme + Haze, Nav3 host, FloatingNavBar, manifest components
:core:common             Result, dispatchers, logging
:core:model              domain models
:core:domain             use cases + repository interfaces
:core:data               repository impls
:core:telephony          SMS/MMS provider, send/receive, PDU, SIM subscriptions, sync engine
:core:database           Room, DAOs, FTS
:core:datastore          Proto settings
:core:contacts           contacts + avatars
:core:notifications      channels, MessagingStyle, inline reply, bubbles
:core:ml                 ML Kit wrappers (OTP/entities, smart reply, translate, categoriser)
:core:ui                 ⭐ api(obsidian) + MessagesTheme adapter + messaging composites (§7.3) + icon drawables
:core:testing            fakes, rules, Roborazzi config

:feature:onboarding  :feature:conversations  :feature:thread  :feature:compose
:feature:search      :feature:conversationinfo  :feature:starred  :feature:scheduled
:feature:blocking    :feature:archive  :feature:settings  :feature:backup
```

`:core:designsystem` from v0.1 is **dropped**. Obsidian is the design system, and `:core:ui` only *extends* it. Feature modules depend on `:core:ui`, never on each other.

---

## 5. Data Strategy

- **The system Telephony provider is the source of truth** for messages. Nothing is lost when switching apps in either direction.
- **Room holds app-owned metadata and the index:**
  - `conversation_meta`: pin and order, archived, muted-until, per-chat SIM, locked, category override, draft, notification channel
  - `message_meta`: starred, reminder, extracted OTP, entities
  - `scheduled_message`
  - `block_rule`
  - `quick_reply_template`
  - `message_fts`
  - `sync_state`
- **Sync:** a `ContentObserver` on `content://mms-sms/` produces incremental diffs. The first launch does a paged full reindex.
- **Data flows one way:** the app writes to the provider, and the observer refreshes the index and UI.

---

## 6. Default-SMS-App & OEM Requirements

- **Manifest:**
  - `SMS_DELIVER` receiver
  - `WAP_PUSH_DELIVER` receiver (MMS)
  - `RESPOND_VIA_MESSAGE` service
  - `SENDTO`/`SEND` activity for `sms:`/`smsto:`/`mms:`/`mmsto:`
- **Default role:** requested through `RoleManager.ROLE_SMS`.
- **Receivers:** use `goAsync()` and hand work to an expedited WorkManager job.
- **Exact alarms:** `USE_EXACT_ALARM`, which messaging apps are allowed.
- **OEM battery settings:** onboarding includes deep links to Samsung "Never sleeping apps" and OnePlus "Auto-launch".
- **Per-SIM sending:** `SmsManager.getSmsManagerForSubscriptionId` + `CarrierConfigManager`.

---

## 7. Design System Integration (Obsidian)

### 7.1 Root setup (`MainActivity`)

```kotlin
setContent {
    val appearance by settingsViewModel.appearance.collectAsStateWithLifecycle()
    MessagesTheme(accent = appearance.accent) {                     // thin adapter → ObsidianTheme
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            Box(Modifier.fillMaxSize().background(ObsidianTheme.colors.background)) {
                MessagesNavHost(backStack)                           // every entry wrapped in RevealScope
                AnimatedVisibility(visible = backStack.top is TabRoute, ...) {
                    FloatingNavBar(tabs, selectedTab, onSelect, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
fun MessagesTheme(accent: AppAccent = AppAccent.Electric, content: @Composable () -> Unit) =
    ObsidianTheme(accent = accent.toObsidian(), neutrals = ObsidianNeutrals.Dark, content = content)
```

- **Accent:** `ObsidianAccent.Electric` (`#3D9BFF`). Blue is the conventional messaging colour, reads clearly as "outgoing", and has strong contrast on `Dark`. The domain `AppAccent` maps to presets (the same adapter pattern as Alarmzy), so an in-app accent picker costs nothing (see Q2).
- **Neutrals:** `ObsidianNeutrals.Dark`, fixed as specified.
- **Edge-to-edge:** `enableEdgeToEdge()`. Headers handle status-bar insets, and the nav bar handles navigation-bar insets.
- **System notifications:** `setColor(accent)` so the notification shade matches.

### 7.2 Conventions (enforced in review and by a Gradle check)

| Rule | Detail |
|---|---|
| Tokens | Only `ObsidianTheme.colors` / `ObsidianTheme.typography`, `ObShapes`, `ObSpacing`, `ScreenGutter`. **Never `MaterialTheme.*`** |
| Components | Use the Obsidian component first. Stock M3 only where Obsidian has no equivalent (`ModalBottomSheet`, `SwipeToDismissBox`, `BasicTextField`), always wrapped in `:core:ui` |
| Clicks | `Modifier.pressable {}` with the README scales: rows 0.985, cards 0.97, buttons 0.96, action pills 0.93, header icons 0.9, chips/FAB 0.88. **No `clickable` and no ripples** |
| Glows | Only `ObGlows` presets, applied **before `clip`**. Only for on/primary elements, one strong glow per region, and no glow when disabled |
| Motion | `ObMotion` springs for anything interactive. Each nav entry gets `RevealScope(enabled = !poppedBack)`. Lists use `revealOnEnter(index)`, forms and settings use `RevealColumn` |
| Glass | Scrolling content gets `Modifier.glassSource()`. Glass surfaces are *siblings* of the source, clipped before `glassEffect` |
| Insets | Tab screens pad by `floatingNavClearance()`. Content top padding = status bar + `LargeHeaderHeight` / `DetailHeaderHeight` |
| Haptics | Follow README §9. Send = `Confirm`; swipe threshold, filter and segment changes = `SegmentTick`; copy OTP, FAB, re-tap tab = `ContextClick` |
| Icons | Material Symbols **Rounded** 24dp vector drawables in `:core:ui` (`ic_*`), tinted `textSecondary` at rest, `textPrimary` when selected, `accentLight` on containers |

**Gradle check `verifyDesignRules`:** grep-based and fails the build on `MaterialTheme.`, `Modifier.clickable`, `indication = ripple`, or a hard-coded `Color(0x` in `:feature:*` sources.

### 7.3 Messaging components to build in `:core:ui`

Obsidian ships no text input, bubble, avatar, badge, sheet, snackbar or swipe row. These are built **only from Obsidian tokens, effects and motion**:

| Component | Composition |
|---|---|
| `Avatar(contact, size)` | Coil photo, or initials on a `tile` circle in `labelStrong`/`textSecondary`. Groups show 2 overlapping avatars with a `background` ring. The selection-mode check pops in with `ObMotion.bouncy()` |
| `UnreadBadge(count)` | `accent` pill with an `onAccent` `RollingText` in `caption` (odometer when count changes). No glow, since badges are neutral density |
| `ConversationRow` | `pressable(0.985, onLongClick)`, Avatar, name (`bodyLarge`, `textPrimary`; SemiBold when unread), snippet (`body`, `textSecondary`), time (`label`, `textTertiary`; `accentLight` when unread), mute/pin glyphs, `HorizontalReveal { ObCheckbox }` in selection mode |
| `SwipeableRow` | Wraps `SwipeToDismissBox`. Backgrounds: archive = `accentContainer` + `accentLight` icon; delete = `error` @20% + `error` icon. The icon `popOnSelect`s and ticks a `SegmentTick` haptic at the threshold |
| `MessageBubble` | **Incoming:** `card`, `textPrimary`. **Outgoing:** `accentContainerStrong` fill, `accentBorder` 1dp, `textPrimary` (the README "selected" recipe; no glow, since bubbles are repeated content). 18dp radius, 6dp on the tail corner of grouped runs. Links and OTP in `accentLight`. Failed sends get an `error` border and a retry glyph |
| `MessageStatus` | `caption` text in `RollSwap`: Sending → Sent → Delivered. `ob_ic_check` pops in bouncy on delivery |
| `DateDivider` | Centred `caption` uppercase, `textTertiary` |
| `SimBadge` | Tiny `caption` pill on `tile` ("1" / "2"); `accentContainer` when it differs from the default SIM |
| `OtpChip` | `ChoicePill` styling plus a copy glyph. On tap: `popOnSelect`, `ContextClick`, and a "Copied" snackbar |
| `ObTextField` | `BasicTextField` on a `cardInner` pill with a `border` → `accentBorder` on focus (snappy colour), `body` text, `textTertiary` hint, `accent` cursor |
| `SearchField` | `ObTextField` with a search glyph and a clear `HeaderIconButton` |
| `ComposerBar` | Floating glass pill (like the nav bar): `glow(Black, Elevation)`, `glassEffect(navScrim)`, `borderStrong`. Contains attach `HeaderIconButton`, `SimBadge` toggle, `ObTextField`, and a send/mic button. **Send** is a 44dp `accent` circle with `ObGlows.Action` only while sendable; mic ↔ send swap uses `HeaderActionsSwap`. A segment counter `RollingText` ("142 / 1") shows above the field once past 120 chars |
| `ObBottomSheet` | `ModalBottomSheet`: `card` container, `ObShapes.CardLarge` top, `tileHigh` drag handle, content in `RevealColumn` |
| `ObSnackbar` | Floating glass pill above `floatingNavClearance()`, `popIn()` entrance, `body` text plus an `accentLight` action ("Undo"). Used for undo-send, undo-archive and copied |
| `ScrollToBottomPill` | `PillButton` styling with ↓ glyph and an unread count in `RollingText`; `popIn` in, scale out |
| `MediaThumb` | `ObShapes.Small`, `tile` placeholder; video and audio glyph overlays |
| `ContactChip` | Recipient chip in New conversation (`accentContainerStrong` + `accentBorder`), `popOnSelect` on add |

All of these get Roborazzi screenshot tests and are added to an in-app **Debug → Design showcase** screen that hosts `ObsidianShowcase()` plus the messaging components, for quick visual checks.

---

## 8. Navigation & Screen Specs

### 8.1 Shell

The `FloatingNavBar` has 4 icon-only tabs. It is hidden (fades and slides) on pushed screens such as Thread.

| # | Tab | Icon | `NavIconMotion` |
|---|---|---|---|
| 0 | Messages | chat bubbles | `Jelly` |
| 1 | Starred | star | `Pop` |
| 2 | Scheduled | schedule (clock) | `Shake` |
| 3 | Settings | gear | `Turn` |

Pushed destinations use `DetailHeader`. Popping back passes `RevealScope(enabled = false)` so entrances don't replay.

### 8.2 Onboarding (first run)
- `RevealScope` plus a centred hero: an 88dp `accentContainer` badge with chat glyph, `breathingGlow` + `ambientFloat` (the EmptyState recipe), app name in `title`, tagline in `body`.
- A `RevealColumn` of 3 step cards (`ObCard` + `ObListRow`): **Set as default SMS app**, **Contacts & notifications**, **Keep running in background** (OEM-specific deep link). The trailing slot shows `ObCheckbox(checked = granted, null)`, which pops bouncy as each step completes.
- `PrimaryButton("Continue")` at the bottom, enabled when the role is granted (Cta glow appears only then).
- The first-sync progress row uses `RollingText` for "Indexed 12,480 messages".

### 8.3 Inbox (tab 0) — Full MVI
- **Header:** `LargeHeader("Messages")` with actions: search `HeaderIconButton` and overflow `ObDropdownMenu` (Mark all read, Archived, Spam & blocked, Select).
- **Filters:** a horizontally scrolling row of `ChoicePill` under the header (All · Unread · Personal · Transactions · OTPs · Offers). It sits on the glass layer and scrolls away with the list.
- **List:** a `LazyColumn` with `.glassSource()` and padding of top = status bar + 64dp, bottom = `floatingNavClearance()` + 72dp (FAB).
  - **Pinned:** a `SectionLabel("Pinned")` above one `ObCard` holding pinned rows separated by `ObDivider`. Drag to reorder.
  - **Other conversations:** flat `ConversationRow`s on `background`.
  - **Entrances:** `revealOnEnter(index)` on the first screenful; the gate stops replays while scrolling.
- **Swipe:** configurable left and right actions (default: archive / delete) using `SwipeableRow`.
- **Selection mode:** long-press enters it. The header title rolls "Messages" → "3 selected", actions swap via `HeaderActionsSwap` (pin, archive, mute, delete), checkboxes `HorizontalReveal` in, and the FAB scales out.
- **FAB:** `ObFab(ic_edit, "New message")` bottom-end above the nav bar. It is the only strong glow in this region.
- **Empty:** `EmptyState(ic_chat, "No conversations", "Messages you send and receive appear here")`.
- **Destructive actions:** `ObAlertDialog(destructive = true)`.

### 8.4 Thread — Full MVI
- **Header:** `DetailHeader(contactName, onBack)` with actions: call, info. Tapping the title opens Info.
- **Messages:** a `LazyColumn(reverseLayout = true)` with `.glassSource()` over `background`, using `MessageBubble`, `DateDivider`, OTP chips and entity links.
  - **Entrances:** only the newest ~10 bubbles use `revealOnEnter`, indexed from the bottom.
  - **Newly arrived or sent bubble:** `popIn(0.88)` plus a gentle rise.
- **Bubble long-press:** opens an `ObBottomSheet` with message actions (Copy, Copy OTP, Forward, Star, Remind me, Translate, Details, Delete), each an `ObListRow`.
- **Bottom:** `ComposerBar` floats with `imePadding()`; the list pads by the composer height + IME.
- **Smart replies:** a `ChoicePill` row above the composer; it appears with `ExpandEnter`.
- **Attach:** an `ObBottomSheet` grid of `IconTile`s (Camera, Gallery, File, Contact, Location, Audio). The selected media tray sits above the field.
- **Send long-press:** a schedule sheet with quick `ChoicePill`s (Later today, Tonight, Tomorrow morning, Custom…) and a `PrimaryButton("Schedule")`.
- **Undo send:** an `ObSnackbar` with a countdown.
- **Scroll to bottom:** `ScrollToBottomPill` appears when scrolled up.

### 8.5 New conversation — Full MVI
- `DetailHeader("New message")`, then a `SearchField` (number or name), then a row of `ContactChip`s once recipients are added.
- Lists: a `SectionLabel("Frequent")` card, then all contacts as `ObListRow`-style rows with Avatar.
- Two or more recipients show a group/individual `SegmentedPill` ("Group MMS" / "Send individually").
- `PrimaryButton("Start chat")` / `"Create group (3)"` with a rolling count; Cta glow when enabled.

### 8.6 Search — Full MVI
- `DetailHeader` holding a `SearchField` (autofocus), then filter `ChoicePill`s: From, Has media, Links, Starred, Date.
- Results are grouped by `SectionLabel` (Conversations, Messages). Matches are highlighted with an `accentLight` + SemiBold `SpanStyle`.
- Empty and no-results screens use `EmptyState`.

### 8.7 Conversation info — MVVM
- Hero: 88dp Avatar, name in `title`, number in `body`, and quick `ActionPillButton`s (Call, Search, Mute). Neutral; only "Call" is `primary`.
- A `RevealColumn` of `ObCard` sections:
  - **Notifications:** `ObSwitch(Medium)`, sound, importance
  - **SIM:** `SegmentedPill(SIM 1 / SIM 2)` with carrier names
  - **Organisation:** Pin, Archive, Lock (switches)
  - **Media & links:** a 3-column `MediaThumb` grid with a "See all" `PillButton`
  - **Danger zone:** Block & report, Delete conversation. Titles in `error`, confirmed through `ObAlertDialog(destructive)`

### 8.8 Starred (tab 1) / Scheduled (tab 2) — MVVM
- `LargeHeader`, then a list of `ObCard`s, each showing a message preview, contact and time.
- **Scheduled cards:** the fire time in `timeSmall` (the numeric style suits a time-first card), and long-press to edit or delete.
- **Empty:** `EmptyState`.

### 8.9 Settings (tab 3) — MVVM
`LargeHeader("Settings")`, then a `RevealColumn` of `SectionLabel` + `ObCard` groups made of `ObListRow`s with `IconTile` and trailing `ObSwitch(SwitchSize.Medium)` or a chevron:
- **Appearance:**
  - Accent swatches (if Q2 = yes): circles in `ObsidianAccent.swatch`; the selected one uses `ObGlows.Action` + `popOnSelect`
  - Text size: `SegmentedPill` S / M / L
  - Bubble density
- **Messages:** default SIM (`SegmentedPill`), delivery reports, auto-convert long SMS to MMS, MMS auto-download (Wi-Fi / Mobile / Never), group messaging mode
- **Notifications:** per-category toggles, private on lock screen, bubbles
- **Organisation:** swipe actions, auto-categorise, OTP auto-delete after 24h, auto-rules (E4)
- **Privacy & security:** blocked numbers and rules, spam folder, locked conversations (E2)
- **Data:** backup and restore (E7), bulk cleanup (E14), storage used
- **About:** version, licences (Inter OFL), and Design showcase (debug builds)

### 8.10 Secondary screens
Blocked & rules, Archive, Spam, Backup, and Locked vault all use `DetailHeader` + MVVM. Blocking rule types (Number / Prefix / Keyword / Regex) use `SegmentedPill`. The Backup screen shows a `PrimaryButton("Back up now")` and progress through `RollingText`.

---

## 9. Motion Choreography (messaging-specific)

| Moment | Motion |
|---|---|
| Open thread from inbox | Nav3 transition: incoming screen slides 8% + fades with `ObMotion.gentle()`, and the nav bar slides out. Bubbles stagger up from the bottom |
| Send | Send button sinks (`pressable` 0.93) with `Confirm` haptic. The bubble `popIn`s at the bottom, and the field clears with a quick fade |
| Status change | `RollSwap` on the status caption; the delivered check pops in `bouncy()` |
| Incoming while viewing | The bubble `popIn`s. If scrolled up, the scroll-to-bottom pill count rolls |
| Archive or delete swipe | The row collapses with `CollapseExit` and an `ObSnackbar` (Undo) pops in |
| Pin | The row animates to the pinned card with `animateItem(placementSpec = ObMotion.gentle())` |
| Filter pill change | `popOnSelect` + `SegmentTick`; list content crossfades with `gentle()` |
| Selection mode | Header title roll, `HeaderActionsSwap`, checkboxes `HorizontalReveal`, FAB scales out |
| Empty states | `breathingGlow` + `ambientFloat` badge (built into `EmptyState`) |

Per-frame work stays in `graphicsLayer {}`. Thread lists use stable keys (`message.id`) so reveal state isn't recycled incorrectly.

---

## 10. Feature Inventory

### 10.1 Parity with Google Messages + Samsung Messages (non-RCS)
- **Inbox:** pin (reorderable), archive, mute, read/unread, delete, block; swipe + multi-select; categories (Personal / Transactions / OTPs / Offers / Spam); unread filter.
- **Thread:** grouped bubbles, date dividers, per-message SIM, delivery reports with retry, OTP copy chip, entity actions (call / map / calendar), link previews (opt-in), pinch text size, jump to date, group MMS with sender names.
- **Composer:** dual-SIM picker, attachments (camera, gallery, file, vCard, location, voice note), GSM-7/UCS-2-aware counter, auto SMS→MMS, drafts, schedule send, templates, smart reply.
- **Notifications:** `MessagingStyle` with inline reply, mark read and copy OTP; per-conversation channels; bubbles; private lock-screen mode.
- **Blocking & spam:** numbers, unknown senders, alphanumeric senders, prefixes, keywords; on-device spam heuristic.
- **Settings:** see §8.9.

### 10.2 Extras (choose for v1 — see Q1)

| # | Feature |
|---|---|
| E1 | Undo send (3–10 s window) |
| E2 | Locked / private conversations (biometric vault) |
| E3 | Message reminders |
| E4 | Regex / keyword auto-rules (auto-archive, auto-read, auto-categorise) |
| E5 | Transactions dashboard (bank/UPI SMS → amounts, merchants, monthly summary; on-device). Would use `timeLarge` / `RollingText` hero numbers and could become a 5th tab |
| E6 | On-device translation |
| E7 | Backup / restore (local ZIP; also imports *SMS Backup & Restore* XML) |
| E8 | S23 ↔ OnePlus 13 transfer via E7 |
| E9 | Export conversation (PDF / text) |
| E10 | In-app accent picker (Obsidian presets) |
| E11 | Starred messages hub (already a tab) |
| E12 | Home-screen widget (Glance, Obsidian colours) |
| E13 | Quick Settings tile / app shortcuts |
| E14 | Bulk cleanup (old OTPs / promos) |

---

## 11. Key Technical Flows

- **Receive SMS:** `SMS_DELIVER` → `goAsync` → parse → block rules → write to the Inbox provider → categorise and extract OTP → Room meta → notification.
- **Receive MMS:** `WAP_PUSH_DELIVER` → notification-ind → `downloadMultimediaMessage` (per auto-download policy) → persist parts → notify.
- **Send SMS:** Intent.Send → `SendMessageUseCase` → undo window (E1) → outbox row → `sendMultipartTextMessage` with sent/delivered PendingIntents → status receivers → provider → observer → UI.
- **Send MMS:** `SendReq` PDU → cache file → FileProvider → `sendMultimediaMessage` → `MmsSentReceiver`.
- **Scheduled:** Room row → exact alarm → receiver → expedited job → normal send. Rescheduled on boot and time change.

---

## 12. Quality, Security, Performance

- **Privacy:** no analytics or third-party crash reporting. `dataExtractionRules` exclude the DB.
- **Security:**
  - Locked chats are kept out of the FTS index and notifications.
  - Biometric gate via `BiometricPrompt`.
- **Performance:**
  - Cold start under 500 ms to the inbox.
  - 120 Hz scrolling on 10k+ message threads (Paging 3 over provider cursors).
  - Baseline Profiles.
  - Haze blur is limited to the header, nav bar and composer (3 surfaces max on screen).
- **Testing:**
  - Reducer and use-case unit tests (Turbine).
  - Robolectric provider mapping.
  - Instrumented send/receive tests on both phones.
  - **Roborazzi screenshots for every `XxxScreen` and every §7.3 component**, which locks Obsidian fidelity.
  - The `verifyDesignRules` Gradle check runs in `check`.

---

## 13. Milestones

| Phase | Deliverable |
|---|---|
| **M0 — Skeleton** | Gradle multi-module with `mavenLocal()` + Obsidian, convention plugins, Hilt, Nav3 host with `RevealScope` wrapper, root `MessagesTheme` + Haze + `FloatingNavBar`, `MviViewModel`, `verifyDesignRules`, Debug showcase (`ObsidianShowcase()`), onboarding + SMS role |
| **M1 — Design kit** | All §7.3 components, icon set, screenshot tests |
| **M2 — Read** | Provider sync, Inbox, Thread (read-only), contacts, search index |
| **M3 — SMS send/receive** | Receivers, composer, notifications with inline reply, dual SIM, delivery reports |
| **M4 — MMS** | Download, send with attachments, group MMS, media viewer |
| **M5 — Organisation** | Pin, archive, mute, categories, OTP, blocking, spam, drafts, swipe, selection mode |
| **M6 — Parity polish** | Scheduled send, smart reply, templates, Starred, full Settings, Conversation info |
| **M7 — Extras** | Selected E-items |
| **M8 — Hardening** | OEM battery quirks, Baseline Profiles, perf passes, QA on S23 + OnePlus 13 |

---

## 14. Decisions (locked 2026-10-16)

| # | Decision |
|---|---|
| Package | **`dev.redfox.messages`**, app name **Messages** |
| Accent | **In-app accent picker (E10)** over `ObsidianAccent.Presets`, default **Electric**. Persisted in DataStore and applied reactively at the root through `MessagesTheme` |
| Extras | **All of E1–E14 in v1.** E5 Transactions no longer has a nav tab (see below) |
| Backup | **Local file only**: export to a ZIP via SAF (`CreateDocument`) and import via SAF (`OpenDocument`). The ZIP holds JSON + MMS media; the importer also reads *SMS Backup & Restore* XML. No Drive integration, and no `INTERNET` dependency for backup |
| Distribution | **Sideloaded APK.** Release signing via a local `keystore.properties` (git-ignored). No Play policy constraints, so a direct `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` prompt is fine |
| Network | Link content always loads; there is no setting (2026-10-17, the link-preview toggle was removed). `INTERNET` is used for content linked from messages (chatbot card images, link previews) and the one-time ML Kit model downloads. Message data never leaves the device |
| minSdk | 33 · Material You not used |

### Updated nav shell (4 tabs, 2026-10-17: Transactions tab removed)

| # | Tab | `NavIconMotion` |
|---|---|---|
| 0 | Messages | `Jelly` |
| 1 | Starred | `Pop` |
| 2 | Scheduled | `Shake` |
| 3 | Settings | `Turn` |

The selected-tab indicator is a slot-filling **pill** (54dp tall, 44dp when compact), not Obsidian's 48dp circle. The inbox keeps its Transactions *filter chip*; where the E5 dashboard lives (if anywhere) is undecided.

**Transactions screen (on hold, no tab):** `LargeHeader("Transactions")` with a month `SegmentedPill`/chevrons. The hero `ObCard` shows spent / received in `timeLarge` `RollingText` with currency in `meridiem`. Below it: a category breakdown and a list of parsed transactions (merchant, account ••1234, amount in `timeSmall`; credits in `accentLight`, debits in `textPrimary`). Tapping a transaction jumps to the source SMS.
