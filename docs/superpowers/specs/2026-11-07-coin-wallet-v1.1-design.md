# Coin Wallet v1.1: design addendum

Approved by Ilan on 2026-11-07. This extends `2026-11-07-coin-wallet-design.md`. Everything there still applies unless this file changes it.

## 1. Trip list actions

- **Swipe to delete.** Swiping a trip card end-to-start (left in LTR) deletes it at once. The existing app-wide snackbar then says "Trip deleted" with Undo. Swiping the other way does nothing.
- **Multi-select.**
  - Long-pressing a card enters select mode with that trip selected. While in select mode:
    - Tapping a card toggles it.
    - The top bar shows "N selected" with **Select all**, **Delete** and **✕**.
    - The FAB and pull-to-refresh are hidden.
    - System back exits select mode.
    - Swiping is disabled.
  - Delete removes all selected trips in one write. The snackbar says "N trips deleted" (or "Trip deleted" for 1), and one Undo restores all of them.
  - Selection is held as trip ids in `rememberSaveable`, so it survives rotation. Ids that no longer exist are dropped.
- **Repository.**
  - `delete(ids: Set<String>)` replaces `delete(id)`.
  - `RepoNotice.Deleted(trips: List<Trip>)` replaces `Deleted(trip)`.
  - `restore(trips: List<Trip>)` re-adds the trips that are missing, in one write.
  - A failed write posts `SaveFailed`, never `Deleted` (same rule as v1).

## 2. Hop currency

- **Settings.**
  - New card "Hop currency" with a switch and a currency row that opens the existing picker.
  - Stored in SharedPreferences as `hop_enabled` (Boolean, default false) and `hop_currency` (String, default `EUR`).
  - Invalid stored codes fall back to `EUR`.
  - `Settings` gains `hopEnabled: Boolean` and `hopCurrency: String`. The effective hop is `hopCurrency` when enabled, otherwise none.
- **Display.** The hop appears only in the wallet header and the Pay sheet, never on the trip list. It shows when it is enabled and differs from the trip's currency, including when it equals the home currency (the user turned it on).
- **Conversion.** Hop uses ECB cross rates only, because manual rates are bound to a home currency. With no ECB rate, the line reads "Hop: no rate".

## 3. Pay

- **Entry point.** A **Pay** button in the wallet header, enabled when the trip holds anything.
- **Sheet layout.** Opening Pay shows a sheet with:
  - A **price** field in the trip's currency. It accepts digits with an optional `.` or `,` and up to `decimals` fraction digits, and must be greater than 0.
  - The suggestion, which updates live as the price is typed.
- **Validation.** If the price exceeds the wallet total, the sheet shows "You only have {total}" and **Paid** is disabled. An invalid price shows "Enter a price above 0" and **Paid** is disabled.
- **Algorithm** (core, pure). The user chose "smallest first, then drop extras":
  1. Walk the denominations from smallest to largest value. Take `min(held, ceil(remaining / value))` of each until the price is covered.
  2. Walk the taken denominations from largest to smallest. Drop `min(taken, floor((paid − price) / value))` of each.
  3. The result is the pieces, `paidMinor`, and `changeMinor = paidMinor − price`.
  4. Return null when the total held is less than the price.
- **Suggestion display.** The suggestion is not editable (the user chose "take it or cancel"). It is a read-only list of rows like "3 × 1 Kč" with icons, then "You hand over X", "Change Y" (shown only when Y > 0), and the home and hop equivalents of the price.
- **Paid.**
  - Applies the payment inside `TripRepository.update`. The transform re-checks that every piece is still held. If not, the trip is left unchanged and the sheet shows "Wallet changed. Check the suggestion again." with the suggestion recomputed.
  - Pieces are subtracted.
  - Change is never added to counts (the user's choice). If `changeMinor > 0`, a change reminder is added to the trip.
- **Cancel** or dismissing the sheet changes nothing.

## 4. Change reminders

- **Model.** `Trip` gains `changeDue: List<ChangeNote> = emptyList()`, where `ChangeNote(id: String, amountMinor: Long, at: Long)`.
- **File format.** The format stays version 1. The field has a default, so v1 files load unchanged. The codec rejects a note with `amountMinor <= 0` as corrupt.
- **Wallet screen.**
  - Each note shows as a card at the top: "Change to add: 8.00 Kč", the exact local date and time it was created (`DateTimeFormatter.ofLocalizedDateTime(MEDIUM, SHORT)`, device zone), and a **Done** button.
  - Done removes only that note.
  - Notes never change or disappear for any other reason. Adding coins does not touch them (Ilan's rule).
- **Trip list.** A trip card with notes shows a small badge, for example "2 change", with a TalkBack description such as "2 change reminders".
- **Currency lock.** The edit-trip currency is also locked while notes exist. The hint reads "Reset counts and clear change reminders to change currency". **Reset counts** does not clear notes.

## 5. Currency names in the wallet header

- Each header amount is followed by the full currency name in smaller text: "12,500 Ft · Hungarian Forint", "≈ ₪131.20 · Israeli New Shekel", and "≈ €32.10 · Euro (hop)".
- Long names wrap to their own line instead of being cut off.

## 6. Testing

- Core unit tests cover:
  - the Pay algorithm: exact, overshoot then drop, insufficient, zero-decimal JPY, single large note;
  - price parsing;
  - codec round-trip with notes, plus rejecting a negative note;
  - settings hop decoding.
- ViewModel tests cover:
  - bulk delete and Undo;
  - the Pay apply path, including a stale-suggestion rejection;
  - note Done;
  - hop conversion.
- Paparazzi snapshots in light and dark cover:
  - select mode;
  - a card with a reminder badge;
  - the wallet with notes and hop names;
  - the Pay sheet (normal, insufficient, with change);
  - the Settings hop card.
