# Coin Wallet — Design Spec

Date: 2026-11-07 · Owner: Ilan · Status: approved design, spec under review

## 1. Purpose

Android app for travelers. Each trip holds the coins and notes of one foreign
currency. User counts pieces per denomination; app shows the trip total in the
trip currency and converted to the user's home currency, plus a grand total of
all trips. Redesign of the old "Koruna Wallet" (old code lost, nothing reused).

Non-goals (v1): cloud sync, accounts, custom currencies, multiple currencies
per trip, change history, widgets, charts, BGN (euro since 2026).

## 2. Platform and stack

- Kotlin, Jetpack Compose Material 3, single module `app`, Gradle Kotlin DSL.
- minSdk 30, targetSdk 35, compileSdk 35. Package `com.ilankog.coinwallet`.
- Single Activity, navigation-compose, one ViewModel per screen exposing `StateFlow`.
- kotlinx.serialization (JSON) for files. `HttpURLConnection` for network. No DI framework.
- Permissions: `INTERNET` only.
- Release: R8 minify + resource shrink, signed with the debug keystore.

## 3. Currencies

Exactly the 30 currencies served by `https://api.frankfurter.dev/v1/currencies`:
AUD BRL CAD CHF CNY CZK DKK EUR GBP HKD HUF IDR ILS INR ISK JPY KRW MXN MYR NOK
NZD PHP PLN RON SEK SGD THB TRY USD ZAR.

Each `CurrencyInfo` has: `code`, `name` (English, from the list above),
`symbol`, `decimals`, `coins: List<Long>`, `notes: List<Long>` (values in minor units).

Zero-decimal currencies (minor unit = major unit): HUF, IDR, ISK, JPY, KRW.
All others: 2 decimals.

Denomination tables (minor units; coins | notes):

| Code | Coins | Notes |
|---|---|---|
| AUD | 5 10 20 50 100 200 | 500 1000 2000 5000 10000 |
| BRL | 5 10 25 50 100 | 200 500 1000 2000 5000 10000 20000 |
| CAD | 5 10 25 100 200 | 500 1000 2000 5000 10000 |
| CHF | 5 10 20 50 100 200 500 | 1000 2000 5000 10000 20000 100000 |
| CNY | 10 50 100 | 100 500 1000 2000 5000 10000 |
| CZK | 100 200 500 1000 2000 5000 | 10000 20000 50000 100000 200000 500000 |
| DKK | 50 100 200 500 1000 2000 | 5000 10000 20000 50000 100000 |
| EUR | 1 2 5 10 20 50 100 200 | 500 1000 2000 5000 10000 20000 |
| GBP | 1 2 5 10 20 50 100 200 | 500 1000 2000 5000 |
| HKD | 10 20 50 100 200 500 1000 | 1000 2000 5000 10000 50000 100000 |
| HUF | 5 10 20 50 100 200 | 500 1000 2000 5000 10000 20000 |
| IDR | 100 200 500 1000 | 1000 2000 5000 10000 20000 50000 100000 |
| ILS | 10 50 100 200 500 1000 | 2000 5000 10000 20000 |
| INR | 100 200 500 1000 2000 | 1000 2000 5000 10000 20000 50000 |
| ISK | 1 5 10 50 100 | 500 1000 2000 5000 10000 |
| JPY | 1 5 10 50 100 500 | 1000 2000 5000 10000 |
| KRW | 10 50 100 500 | 1000 5000 10000 50000 |
| MXN | 50 100 200 500 1000 2000 | 2000 5000 10000 20000 50000 100000 |
| MYR | 5 10 20 50 | 100 500 1000 2000 5000 10000 |
| NOK | 100 500 1000 2000 | 5000 10000 20000 50000 100000 |
| NZD | 10 20 50 100 200 | 500 1000 2000 5000 10000 |
| PHP | 25 100 500 1000 2000 | 2000 5000 10000 20000 50000 100000 |
| PLN | 1 2 5 10 20 50 100 200 500 | 1000 2000 5000 10000 20000 50000 |
| RON | 1 5 10 50 | 100 500 1000 5000 10000 20000 50000 |
| SEK | 100 200 500 1000 | 2000 5000 10000 20000 50000 100000 |
| SGD | 5 10 20 50 100 | 200 500 1000 5000 10000 100000 |
| THB | 25 50 100 200 500 1000 | 2000 5000 10000 50000 100000 |
| TRY | 1 5 10 25 50 100 | 500 1000 2000 5000 10000 20000 |
| USD | 1 5 10 25 50 100 | 100 200 500 1000 2000 5000 10000 |
| ZAR | 10 20 50 100 200 500 | 1000 2000 5000 10000 20000 |

Some values exist as both coin and note (e.g. CNY 1 yuan, USD $1). A
denomination is identified by `DenomKey(kind: COIN|NOTE, value: Long)`, string
form `"c100"` / `"n100"`. Counts are keyed by that string.

## 4. Domain rules (pure Kotlin, package `core`, no Android imports)

- Money is `Long` minor units. Never `Double` for stored amounts.
- Count per denomination: integer 0..9999. `+` at 9999 and `−` at 0 are no-ops.
  Typed entry: digits only; empty → 0; >9999 or non-digit → rejected with error text, count unchanged.
- Trip total (minor) = Σ value × count. Max 9999 × 500000 × ~13 denominations fits in Long.
- Trip name: trimmed, 1..40 chars, required. Duplicate names allowed.
- Format: `Money.format(minor, currency, locale)` using `java.text.NumberFormat`
  with currency decimals, symbol from `CurrencyInfo`.
- Conversion: `RateTable` holds EUR-based rates (EUR = 1). Rate from A to B =
  rate[B] / rate[A] as `BigDecimal` (scale 10, HALF_EVEN). Home amount =
  minor(A) / 10^dec(A) × rate × 10^dec(B), rounded HALF_EVEN to home minor units.
- Rate priority for a trip currency A against home H:
  1. Manual override for A (stored as "1 A = X H", X > 0, H at time of saving).
     A manual override is valid only while H equals its saved home; else ignored.
  2. Cached ECB cross-rate.
  3. None → trip home amount is `null`.
- Same currency (A == H): rate 1, no override allowed (menu item hidden).
- Grand total = Σ home amounts of trips with a rate. If any trip lacks a rate,
  UI shows hint "N trips without rate not included".
- Staleness: refresh needed when no cache, or cache fetched > 12h ago (wall clock;
  a fetch time in the future counts as stale).

## 5. Persistence (package `data`, thin Android glue)

- `trips.json` in `filesDir`: `{ "version": 1, "trips": [Trip...] }`.
  `Trip(id: String UUID, name, currency, counts: Map<String, Int>, createdAt: Long epoch ms)`.
- Write: serialize → write `trips.json.tmp` → `fsync` → atomic rename over `trips.json`.
- Read: missing file → empty list. Unparseable / wrong version / unknown currency
  code / count outside 0..9999 → whole file treated as corrupt: renamed to
  `trips.corrupt.json` (overwriting older), return empty list + `corrupt = true`;
  UI shows snackbar "Saved trips were damaged and were reset".
  Unknown count keys (denomination not in table) are dropped on load with a
  `Log.w` entry (not corrupt, no user message).
- `rates.json` in `filesDir`: `{ "fetchedAt": Long, "date": "YYYY-MM-DD", "rates": {code: Double} }`.
  Same atomic write. Corrupt → treat as no cache (delete file, log).
- SharedPreferences `settings`: `home_currency` (String), `manual_rate_<CODE>` (String decimal),
  `manual_home_<CODE>` (String). Live updates via an
  `OnSharedPreferenceChangeListener` held as a field of `SettingsStore`
  (prefs only keep weak refs), exposed as `StateFlow<Settings>`.
- Home currency default: currency of `Locale.getDefault()` if among the 30, else EUR.
- All file I/O on `Dispatchers.IO`. Trip mutations serialized through a single
  `Mutex` in `TripRepository`; in-memory `StateFlow<List<Trip>>` is the source of
  truth, file written after each change. Write failure → state kept in memory,
  snackbar "Couldn't save. Changes may be lost." (logged).

## 6. Rates network

- `GET https://api.frankfurter.dev/v1/latest` (base EUR). Connect + read timeout 10s.
  Follow redirects. Response must be HTTP 200 and parse to `{base: "EUR", date, rates}`.
- `RatesParser` (pure Kotlin) parses the body; adds EUR = 1; ignores unknown codes;
  rejects non-positive / non-finite rates (drops that code). Parse failure → error.
- `RateRepository.refresh()` returns `RefreshResult`: `Success | Offline | HttpError(code) | BadData`.
  Busy flag set at start, cleared in `finally`. `CancellationException` rethrown, never swallowed.
  Concurrent `refresh()` calls: second call joins the in-flight one (single-flight).
- Auto-refresh: on app start (Activity `onStart` via ViewModel) if stale. Manual: pull-to-refresh
  on Trips screen and "Refresh rates now" in Settings.

## 7. Screens (package `ui`)

Theme: original warm copper/brass palette. Light: cream background `#FBF6EE`,
copper primary `#B4632F`, brass secondary `#B8902F`, dark text `#2B1D12`. Dark:
espresso background `#1A1410`, copper primary `#E89A63`, brass `#D9B45A`, text `#F3E9DC`.
Coin icons: original vector drawables (round coin with rim and value text,
rectangle note), tinted copper/silver/brass by value tier. App icon: original
stacked-coins adaptive icon. Edge-to-edge, `WindowInsets.safeDrawing` padding,
follows system dark mode.

### 7.1 Trips (start destination)
- Top: app title, Settings icon.
- Header card: grand total in home currency; rate chip:
  "Rates updated 2h ago" / "Updating…" / "Offline · rates from 3d ago" / "No rates yet".
  Hint line when trips lack a rate.
- List of trip cards: name, currency code badge, trip total (trip currency),
  home total or "No rate", small "manual rate" tag when override used.
- FAB "New trip" → New trip sheet. Pull-to-refresh → rates refresh; result snackbar
  on failure ("You're offline. Using saved rates." / "Rate server error (503)" /
  "Rate data was invalid"). Success: chip updates, no snackbar.
- Empty state: coin stack illustration, "No trips yet", "Tap New trip to start counting".
- Tap card → Wallet.

### 7.2 Trip sheet (create / edit) — ModalBottomSheet
- Name field (error under field when invalid, shown after first edit).
- Currency field → searchable list (code + name), filter by code or name, case-insensitive.
  Default selection: none on create (Save disabled until chosen).
- Edit mode (from Wallet menu "Edit trip"): name editable; currency change allowed only
  when all counts are 0 (else field disabled with note "Reset counts to change currency").
- Save → persists, closes. Cancel / swipe down → discards, closes.

### 7.3 Wallet
- Top bar: back, trip name, overflow menu: Edit trip, Set manual rate, Clear manual rate
  (only if set), Reset counts, Delete trip.
- Header: trip total (trip currency, large), home total or "No rate", rate line
  "1 CZK = 0.1626 ILS · ECB 2026-11-06" or "· manual".
- Sections "Coins" and "Notes", each row: coin/note icon, formatted value, `−` button,
  count (tappable), `+` button, row subtotal. Buttons ≥48dp. Haptic tick on change.
- Tap count → dialog with numeric field; OK validates per §4; Cancel closes unchanged.
- Set manual rate dialog: "1 {A} = [ ] {H}"; decimal input, >0, ≤ 1,000,000 (keeps IDR totals inside Long), `.` or `,` separator;
  invalid → error text, OK disabled.
- Reset counts → confirm dialog → all counts 0. Delete trip → confirm dialog →
  delete, navigate back, snackbar "Trip deleted" with Undo (restores the same trip, same id).
- Trip id not found (e.g. deleted elsewhere) → navigate back.

### 7.4 Settings
- Home currency (searchable picker, same component as 7.2).
- "Refresh rates now" button with inline progress and result text.
- Rates info: source "European Central Bank via Frankfurter", ECB date, fetched time.
- About: app version.

## 8. Error handling summary

Every action resolves to success, failure (message shown) or cancel (no change).
No silent catches: every catch logs with `Log.w/e` and surfaces a user message or
an explicit documented fallback. No `!!` on external data.

## 9. Testing

- JUnit 4 for all `core` classes. Fixtures in `app/src/test/resources`:
  `frankfurter_latest.json` (real response captured 2026-11-07), corrupt trips JSON,
  bad-rate JSON.
- `data` tested with temp dirs (`TemporaryFolder`) and a fake `HttpFetcher` interface
  (`fun get(url): HttpResponse`); `HttpURLConnection` impl is the only untested glue.
- ViewModels tested with fake repositories + `kotlinx-coroutines-test`.
- Paparazzi snapshots, light and dark, for: Trips empty / filled / offline / updating / trips without rate;
  Trip sheet create / invalid / currency picker / edit locked currency; Wallet filled /
  zero-decimal (JPY) / no rate / manual rate / count dialog / manual-rate dialog / delete confirm;
  Settings. PNGs reviewed by eye.
- Gate: `gradle clean assembleRelease testDebugUnitTest` passes; `apksigner verify` passes.

## 10. Repo and process

- GitHub repo `IlanKog99/coin-wallet` (created by Ilan), work on branch `feat/v1`, PR to `main`.
- Commits as `Ilan <ilanamir100.100@gmail.com>`, explicit `git add` paths.
- `.gitignore`: build/, .gradle/, .kotlin/, local.properties, .superpowers/.
