# Coin Wallet v1.1 Implementation Plan

> **For agentic workers:** Implement task by task with TDD. A separate reviewer reviews each task. Ilan asked the lead to implement inline so progress is visible.

**Goal:** Add swipe-to-delete and multi-select on the trip list, a hop currency, a Pay flow with change reminders, and full currency names in the wallet header.

**Architecture:** Same layering as v1.
- Pure-Kotlin `core`: Payment, ChangeNote, amount parsing, hop settings.
- Thin `data`: bulk delete and restore, hop prefs.
- Stateless Compose screens plus Route wrappers.

**Spec:** `docs/superpowers/specs/2026-11-07-coin-wallet-v1.1-design.md`

## Global Constraints

All v1 constraints apply (`docs/superpowers/plans/2026-11-07-coin-wallet.md`). Additionally:
- Logging in `data/` uses `java.util.logging`, not `android.util.Log`, because Paparazzi breaks the latter in unit tests.
- Paparazzi renders at API 34 via `coinPaparazzi()`.
- The trips file stays `version: 1`. New fields have defaults.
- Change never changes counts. A ChangeNote is removed only by its own Done.
- Hop conversion uses ECB cross rates only.
- Snapshots of time-dependent text use `ZoneId.of("UTC")`.

---

### Task 1: Core: change notes, payment, amount parsing, hop settings

**Files:**
- Modify: `core/Trip.kt`: add `ChangeNote` and `Trip.changeDue`, plus `TripRules.canChangeCurrency` and `TripRules.dismissNote`.
- Modify: `core/TripsCodec.kt`: reject `amountMinor <= 0` notes.
- Create: `core/Payment.kt`.
- Modify: `core/Money.kt`: add `parseAmount`.
- Modify: `core/Settings.kt`: add hop fields and keys.
- Test: `core/PaymentTest.kt`, `core/ChangeNoteTest.kt`, plus additions to `MoneyTest`, `SettingsCodecTest` and `TripsCodecTest`.

**Interfaces produced:**
```kotlin
@Serializable data class ChangeNote(val id: String, val amountMinor: Long, val at: Long)
// Trip(..., val changeDue: List<ChangeNote> = emptyList())
TripRules.canChangeCurrency(trip: Trip): Boolean
TripRules.dismissNote(trip: Trip, id: String): Trip
data class PaySuggestion(val pieces: List<Pair<Denomination, Int>>, val priceMinor: Long, val paidMinor: Long) { val changeMinor: Long }
Payment.suggest(trip: Trip, info: CurrencyInfo, priceMinor: Long): PaySuggestion?
Payment.apply(trip: Trip, s: PaySuggestion, noteId: String, now: Long): Trip?
Money.parseAmount(text: String, c: CurrencyInfo): Long?
// Settings(homeCurrency, manualRates, hopEnabled = false, hopCurrency = "EUR") { val hop: String? }
SettingsCodec.KEY_HOP_ENABLED = "hop_enabled"; SettingsCodec.KEY_HOP = "hop_currency"
```

**Payment algorithm** (spec §3):
- Ascending pass: take `min(held, ceil(remaining / v))` of each denomination until the price is covered.
- Descending pass: drop `min(taken, (paid - price) / v)` of each.
- Return null if the total held is below the price or the price is ≤ 0.

**Tests (write first, RED):**
- `suggest`:
  - Exact pay with small coins (CZK 1×3, 2×1 for 5: takes 1×3 and 2×1, change 0).
  - Overshoot then drop (CZK 1×3 and 10×1 for 5: 10×1, change 5).
  - Insufficient returns null; price 0 returns null.
  - JPY zero-decimal (¥1×7, ¥100×4 for ¥250: 100×3, then the drop pass removes nothing, so check by computing: ascending takes 1×7 = 7, then 100×3 = 307; drop at 100: floor(57/100) = 0; drop at 1: min(7, 57) = 7, giving 300, change 50).
  - A single large note (100 Kč note for 30: change 70).
- `apply`:
  - Subtracts pieces and adds a note when change > 0.
  - Adds no note when change = 0.
  - Returns null when a piece is no longer held.
- `parseAmount`:
  - Valid: "12", "12.5", "12,50" and ".5" for CZK.
  - Rejects "12.555" for CZK, "1.5" for JPY, and "0", "", "abc", "1.2.3" and "-1".
- Codec:
  - A round trip keeps notes.
  - A file without `changeDue` loads.
  - A note with amount 0 is Corrupt.
- `SettingsCodec`: hop defaults (false, EUR), reads true with CZK, and an invalid code falls back to EUR. `hop` is null when disabled.
- `canChangeCurrency` is false with notes and no counts. `dismissNote` removes only the matching id.

The code is in the commit. The lead implements it directly from this section. Exact sources: see `app/src/main/java/com/ilankog/coinwallet/core/Payment.kt` after the Task 1 commit.

### Task 2: Data: bulk delete and restore, hop prefs

**Files:**
- `data/TripRepository.kt`:
  - `delete(ids: Set<String>): List<Trip>`
  - `restore(trips: List<Trip>)`
  - `RepoNotice.Deleted(trips: List<Trip>)`
- `data/SettingsStore.kt`: `SettingsSource.setHopEnabled(Boolean)` and `setHopCurrency(String)`.
- Test fake: `FakeSettings`.
- Callers:
  - `WalletViewModel.delete()` calls `trips.delete(setOf(tripId))`.
  - `AppNav` restores `deleted.trips`.
  - `Messages.text()` shows "Trip deleted" or "N trips deleted".

**Tests (RED first, in `TripRepositoryTest`):**
- Deleting 2 of 3 ids posts `Deleted` with both trips and writes once.
- `restore(list)` re-adds only the missing trips.
- Deleting unknown ids returns empty and posts no notice.
- A failed save posts `SaveFailed`, not `Deleted` (update the existing test).
- `Messages`: plural text.

### Task 3: Trip list: swipe, select mode, reminder badge

**Files:** `ui/trips/TripsViewModel.kt` (`deleteTrips(ids)`), `TripsScreen.kt`, `TripsRoute.kt`, plus snapshot tests.

**TripsScreen** gains these defaulted parameters:
- `selected: Set<String> = emptySet()`
- `onLongPressTrip: (String) -> Unit = {}`
- `onToggleTrip: (String) -> Unit = {}`
- `onSelectAll: () -> Unit = {}`
- `onClearSelection: () -> Unit = {}`
- `onDeleteSelected: () -> Unit = {}`
- `onSwipeDelete: (String) -> Unit = {}`

**Select mode** (`selected.isNotEmpty()`):
- The top bar shows "N selected", with ✕ (`Icons.Default.Close`), a "Select all" icon (`Icons.Default.SelectAll`) and Delete (`Icons.Default.Delete`).
- There is no FAB.
- Cards use `combinedClickable`: a tap toggles the card, and a long press also toggles it.
- A selected card uses the `secondaryContainer` color, and a check badge replaces its CodeBadge.
- Swipe is disabled in select mode.

**Outside select mode:**
- A tap opens the trip and a long press selects it.
- `SwipeToDismissBox`, with `enableDismissFromStartToEnd = false`, calls `onSwipeDelete` on EndToStart. Its background is the error container with a delete icon at the end.

**Badge:** a trip with notes shows a small `tertiaryContainer` pill reading "N change", with the content description "N change reminders".

**TripsRoute:**
- `rememberSaveable` holds the list of selected ids, filtered to the ids that still exist.
- `BackHandler` clears the selection.
- Delete calls `vm.deleteTrips(selected)` and then clears the selection.

**Snapshots:** `selectMode` and `reminderBadge`, light and dark.

**VM test:** `deleteTrips` removes them and posts `Deleted` with all of them, and an empty set is a no-op.

### Task 4: Wallet: names, hop line, change notes, Pay sheet

**Files:**
- `ui/wallet/WalletViewModel.kt`:
  - `hop` in state
  - `pay(s, done)`
  - `dismissNote(id)`
  - `editTrip` uses `canChangeCurrency`
  - an injectable `newId` and `clock`
- `ui/wallet/WalletScreen.kt`: header names, hop line, Pay button, note cards. `WalletActions` gets `onPay` and `onDismissNote` with defaults.
- `ui/wallet/PaySheet.kt`: `PayPreview` and `payPreview()` (pure), `PaySheetContent`, `PaySheet`.
- `ui/wallet/WalletRoute.kt`: a "pay" dialog key, the TripSheet lock via `canChangeCurrency`.
- `ui/trips/TripSheet.kt`: the lock hint text.

**State:**
```kotlin
data class HopValue(val info: CurrencyInfo, val minor: Long?, val rate: BigDecimal?)
// WalletUiState(..., val hop: HopValue? = null)
sealed interface PayPreview { data object Empty; data object Invalid; data class Insufficient(val totalMinor: Long); data class Ok(val s: PaySuggestion, val homeMinor: Long?, val hopMinor: Long?) }
fun payPreview(ui: WalletUiState, text: String): PayPreview
fun noteTime(at: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String
```

**VM tests:**
- `pay` subtracts pieces and adds a note with the clock time and id.
- `pay` with a stale suggestion (counts changed after suggest) returns false and leaves the trip unchanged.
- `dismissNote` removes the note.
- Hop: hop CZK→EUR uses the ECB rate. Hop is null when disabled or when it equals the trip currency.
- `editTrip` currency stays locked while notes exist.
- `payPreview`: Empty for blank, Invalid, Insufficient, and Ok with change.

**Snapshots:** `notesAndHop` (prague with 2 notes, hop EUR), `paySheet` (Ok with change), `paySheetInsufficient`, light and dark.

### Task 5: Settings: hop card

**Files:**
- `ui/settings/SettingsViewModel.kt`: state `hopEnabled` and `hop: CurrencyInfo`; `setHopEnabled` and `setHop(code)`.
- `SettingsScreen.kt`: a new section between Home and Exchange rates, with a Switch row "Show hop currency on trip screens", a currency card enabled only when on, and the caption "The currency you exchange from, e.g. EUR → CZK. Shown on trip screens only."
- `SettingsRoute.kt`: a saveable picker target (`"home"` or `"hop"`).

**Tests:** the VM toggles the hop setting and `setHop` rejects unknown codes. Snapshots: `hopOn`, light and dark.

### Task 6: Verify and deliver

- Rebase `feat/v1.1` onto `origin/main` once PR #1 merges. If it hasn't merged yet, open the PR against `feat/v1` and retarget it later.
- Run `gradle clean assembleRelease testDebugUnitTest lintRelease` and `apksigner verify`, and count the tests.
- Final whole-branch review on the most capable model, then fix the findings.
- Deliver `coin-wallet-v1.1.0.apk` (versionCode 2, versionName 1.1.0), the screenshots, and the checklist additions.
