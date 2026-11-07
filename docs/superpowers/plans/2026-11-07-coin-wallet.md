# Coin Wallet v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Coin Wallet Android app: trips (one currency each) whose coins/notes are counted per denomination and converted to a home currency with cached ECB rates.

**Architecture:** Single `app` module. `core` = pure Kotlin domain (no Android imports), fully JUnit-tested. `data` = thin glue (files, HTTP, SharedPreferences). `ui` = Compose screens that are stateless (`XxxScreen(state, callbacks)`) plus thin `XxxRoute` wrappers that own a ViewModel. Paparazzi snapshots render the stateless screens.

**Tech Stack:** Kotlin 2.1.21, AGP 8.10.1, Gradle 8.14.3, Compose BOM 2025.05.01 (material3), navigation-compose 2.9.0, lifecycle 2.9.0, kotlinx.serialization 1.8.1, coroutines 1.10.2, JUnit 4.13.2, Paparazzi 1.3.5.

Spec: `docs/superpowers/specs/2026-11-07-coin-wallet-design.md`

## Global Constraints

- Package / applicationId: `com.ilankog.coinwallet`. App name: "Coin Wallet". versionName `1.0.0`, versionCode 1.
- minSdk 30, targetSdk 35, compileSdk 35, JVM target 17, Gradle Kotlin DSL, single module `:app`.
- Only permission: `android.permission.INTERNET`.
- `core` package files must not import `android.*` or `androidx.*`.
- Money is `Long` minor units; conversions use `BigDecimal` with `RoundingMode.HALF_EVEN`. Never `Double` for amounts.
- Count per denomination: 0..9999. Trip name: trimmed, 1..40 chars.
- Manual rate: > 0 and ≤ 1,000,000 (spec addendum: lowered from 1e9 to avoid Long overflow on IDR totals).
- Rates URL: `https://api.frankfurter.dev/v1/latest`, connect/read timeout 10 000 ms. Stale after 12 h.
- Files in `filesDir`: `trips.json`, `trips.corrupt.json`, `rates.json`. SharedPreferences name `settings`.
- No silent catches: every `catch` logs (`android.util.Log` in `data`) or returns an explicit error value (in `core`). Never catch `CancellationException` (only catch `IOException` / `IllegalArgumentException`).
- No `!!` in main source sets.
- Every gradle command runs from repo root `/home/user/coin-wallet` with `export ANDROID_HOME=/root/android-sdk` and uses `gradle --no-daemon -q`. Builds take minutes: use timeout ≥ 600000 ms.
- Repositories: `pluginManagement` first in `settings.gradle.kts`; `maven("https://maven-central.storage-download.googleapis.com/maven2/")` before `mavenCentral()`.
- Git: branch `feat/v1`, identity already configured as `Ilan <ilanamir100.100@gmail.com>`. `git add` explicit paths only. Every commit message ends with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX
  ```
- UI copy is hard-coded English in Kotlin (single-language app; `strings.xml` holds only `app_name`).
- Theme colors exactly as in Task 10. Edge-to-edge; every `Scaffold` uses `contentWindowInsets = WindowInsets.safeDrawing`.

## File Map

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew + gradle/wrapper/*
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/{strings,colors,themes}.xml, values-night/colors.xml
app/src/main/res/drawable/ic_launcher_foreground.xml, mipmap-anydpi-v26/ic_launcher.xml
app/src/main/java/com/ilankog/coinwallet/
  MainActivity.kt                 entry, edge-to-edge, sets nav host
  CoinWalletApp.kt                Application holding AppContainer
  core/Currency.kt                DenomKind, Denomination, CurrencyInfo, Currencies, CurrencySearch, DenomTier
  core/Money.kt                   Money (format/label/parseRate/formatRate), RelativeTime
  core/Trip.kt                    Trip, TripRules
  core/Rates.kt                   RateSnapshot, ManualRate, RateSource, ResolvedRate, ParseResult, RatesParser, Conversion, Staleness
  core/TripsCodec.kt              DecodeResult, TripsCodec
  core/Totals.kt                  TripSummary, WalletTotals, Totals
  core/Settings.kt                Settings, SettingsCodec
  data/AtomicFiles.kt             atomic write / read / move-aside
  data/TripRepository.kt          RepoNotice, TripRepository
  data/Http.kt                    HttpResponse, HttpFetcher, UrlConnectionFetcher
  data/RateRepository.kt          RefreshResult, RateRepository
  data/SettingsStore.kt           SettingsSource, SettingsStore
  data/AppContainer.kt            manual DI
  ui/theme/{Color,Theme}.kt
  ui/common/Messages.kt           UiMessage + copy for results/notices
  ui/common/RateStatus.kt         RateStatus + rateStatus()
  ui/components/{DenomIcon,CoinStackArt,DialogCard,CurrencyPicker,RateChip}.kt
  ui/trips/{TripsViewModel,TripsScreen,TripSheet,TripsRoute}.kt
  ui/wallet/{WalletViewModel,WalletScreen,WalletDialogs,WalletRoute}.kt
  ui/settings/{SettingsViewModel,SettingsScreen,SettingsRoute}.kt
  ui/AppNav.kt                    routes + NavHost + app-level snackbar
app/src/test/resources/frankfurter_latest.json (exists), trips_corrupt.json
app/src/test/java/com/ilankog/coinwallet/ Fixtures.kt, MainDispatcherRule.kt, core/*Test, data/*Test + FakeFetcher + FakeSettings, ui/*Test + SnapshotHelpers + SampleData
```

## Progress Ledger

Kept in `docs/superpowers/plans/2026-11-07-coin-wallet-ledger.md` (task, status, commit, review result).

---

### Task 1: Project scaffold

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties` (git-ignored), `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/colors.xml`, `app/src/main/res/values-night/colors.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `app/src/main/java/com/ilankog/coinwallet/MainActivity.kt`, gradle wrapper files.

**Interfaces:**
- Consumes: nothing.
- Produces: buildable app; `BuildConfig.VERSION_NAME`; theme `@style/Theme.CoinWallet`; launcher icon `@mipmap/ic_launcher`.

- [ ] **Step 1: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "coin-wallet"
include(":app")
```

- [ ] **Step 2: Write `build.gradle.kts`, `gradle.properties`, `local.properties`**

`build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21" apply false
    id("app.cash.paparazzi") version "1.3.5" apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`local.properties`:
```properties
sdk.dir=/root/android-sdk
```

- [ ] **Step 3: Write `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.ilankog.coinwallet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilankog.coinwallet"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

`app/proguard-rules.pro`:
```proguard
# kotlinx.serialization: keep generated serializers of our @Serializable classes (files + nav routes).
-keepclassmembers @kotlinx.serialization.Serializable class com.ilankog.coinwallet.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ilankog.coinwallet.**$$serializer { *; }
```

- [ ] **Step 4: Write manifest and resources**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.CoinWallet">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Coin Wallet</string>
</resources>
```

`app/src/main/res/values/colors.xml`:
```xml
<resources>
    <color name="window_bg">#FBF6EE</color>
    <color name="ic_launcher_background">#B4632F</color>
</resources>
```

`app/src/main/res/values-night/colors.xml`:
```xml
<resources>
    <color name="window_bg">#1A1410</color>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.CoinWallet" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/window_bg</item>
    </style>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml` (original stacked-coins art, inside the 66dp safe zone):
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- bottom coin -->
    <path android:fillColor="#7A3E1A" android:pathData="M34,66 L74,66 L74,74 A20,6 0 0,1 34,74 Z" />
    <path android:fillColor="#E9A97C" android:pathData="M34,66 A20,6 0 1,0 74,66 A20,6 0 1,0 34,66 Z" />
    <!-- middle coin -->
    <path android:fillColor="#9C7A26" android:pathData="M34,56 L74,56 L74,64 A20,6 0 0,1 34,64 Z" />
    <path android:fillColor="#F3DC95" android:pathData="M34,56 A20,6 0 1,0 74,56 A20,6 0 1,0 34,56 Z" />
    <!-- top coin -->
    <path android:fillColor="#8D9198" android:pathData="M34,46 L74,46 L74,54 A20,6 0 0,1 34,54 Z" />
    <path android:fillColor="#F1F2F4" android:pathData="M34,46 A20,6 0 1,0 74,46 A20,6 0 1,0 34,46 Z" />
    <path android:strokeColor="#8D9198" android:strokeWidth="1.5" android:pathData="M40,46 A14,3.6 0 1,0 68,46 A14,3.6 0 1,0 40,46 Z" />
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 5: Write placeholder `MainActivity.kt`** (replaced in Task 14)

```kotlin
package com.ilankog.coinwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { Text("Coin Wallet") }
    }
}
```

- [ ] **Step 6: Generate wrapper and build**

Run:
```bash
cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk
gradle --no-daemon -q wrapper --gradle-version 8.14.3
gradle --no-daemon -q assembleDebug && ls app/build/outputs/apk/debug/
```
Expected: no errors; prints `app-debug.apk`.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties app/build.gradle.kts app/proguard-rules.pro app/src/main/AndroidManifest.xml app/src/main/res app/src/main/java/com/ilankog/coinwallet/MainActivity.kt
git commit -m "build: scaffold Coin Wallet Android project

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 2: Currency tables

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/core/Currency.kt`
- Create: `app/src/test/java/com/ilankog/coinwallet/Fixtures.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/core/CurrenciesTest.kt`

**Interfaces:**
- Consumes: fixture `app/src/test/resources/frankfurter_latest.json`.
- Produces:
  - `enum class DenomKind(val prefix: Char) { COIN, NOTE }`
  - `data class Denomination(val kind: DenomKind, val value: Long) { val key: String }` (`"c100"`, `"n100"`)
  - `data class CurrencyInfo(code, name, symbol, symbolFirst: Boolean, decimals: Int, coins: List<Long>, notes: List<Long>) { val minorPerMajor: Long; val denominations: List<Denomination> }`
  - `object Currencies { val all: List<CurrencyInfo>; const val FALLBACK = "EUR"; fun get(code: String): CurrencyInfo?; fun orFallback(code: String): CurrencyInfo; fun defaultHome(locale: Locale): String }`
  - `object CurrencySearch { fun filter(query: String): List<CurrencyInfo> }`
  - `object DenomTier { fun of(d: Denomination, info: CurrencyInfo): Int /*0..2*/; fun faceText(d: Denomination, info: CurrencyInfo): String }`
  - test helper `object Fixtures { fun text(name: String): String }`

- [ ] **Step 1: Write the fixture helper and failing test**

`app/src/test/java/com/ilankog/coinwallet/Fixtures.kt`:
```kotlin
package com.ilankog.coinwallet

object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource(name)) { "missing fixture $name" }.readText()
}
```

`app/src/test/java/com/ilankog/coinwallet/core/CurrenciesTest.kt`:
```kotlin
package com.ilankog.coinwallet.core

import com.ilankog.coinwallet.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrenciesTest {

    @Test
    fun `supports exactly the Frankfurter currencies`() {
        val api = Regex("\"([A-Z]{3})\":")
            .findAll(Fixtures.text("frankfurter_latest.json"))
            .map { it.groupValues[1] }
            .toSet() + "EUR"
        assertEquals(api, Currencies.all.map { it.code }.toSet())
        assertEquals(30, Currencies.all.size)
    }

    @Test
    fun `denominations are positive ascending and unique`() {
        for (c in Currencies.all) {
            assertTrue(c.code, c.coins.isNotEmpty() && c.notes.isNotEmpty())
            assertEquals(c.code, c.coins.sorted(), c.coins)
            assertEquals(c.code, c.notes.sorted(), c.notes)
            assertTrue(c.code, (c.coins + c.notes).all { it > 0 })
            assertEquals(c.code, c.denominations.size, c.denominations.map { it.key }.toSet().size)
        }
    }

    @Test
    fun `zero decimal currencies`() {
        assertEquals(
            setOf("HUF", "IDR", "ISK", "JPY", "KRW"),
            Currencies.all.filter { it.decimals == 0 }.map { it.code }.toSet(),
        )
        assertEquals(1L, Currencies.orFallback("JPY").minorPerMajor)
        assertEquals(100L, Currencies.orFallback("EUR").minorPerMajor)
    }

    @Test
    fun `coin and note of same value have distinct keys`() {
        val keys = Currencies.orFallback("CNY").denominations.map { it.key }
        assertTrue("c100" in keys)
        assertTrue("n100" in keys)
    }

    @Test
    fun `default home follows locale country`() {
        assertEquals("CZK", Currencies.defaultHome(Locale("cs", "CZ")))
        assertEquals("USD", Currencies.defaultHome(Locale.US))
        assertEquals("ILS", Currencies.defaultHome(Locale("he", "IL")))
    }

    @Test
    fun `default home falls back to EUR`() {
        assertEquals("EUR", Currencies.defaultHome(Locale("ru", "RU")))
        assertEquals("EUR", Currencies.defaultHome(Locale.ENGLISH))
    }

    @Test
    fun `get unknown is null and orFallback gives EUR`() {
        assertNull(Currencies.get("XAU"))
        assertEquals("EUR", Currencies.orFallback("XAU").code)
    }

    @Test
    fun `search matches code or name case-insensitively`() {
        assertEquals(listOf("CZK"), CurrencySearch.filter("czk").map { it.code })
        assertEquals(listOf("CZK"), CurrencySearch.filter(" koruna ").map { it.code })
        assertEquals(30, CurrencySearch.filter("").size)
        assertTrue(CurrencySearch.filter("zzz").isEmpty())
    }

    @Test
    fun `tiers spread over copper brass silver`() {
        val eur = Currencies.orFallback("EUR")
        assertEquals(0, DenomTier.of(Denomination(DenomKind.COIN, 1), eur))
        assertEquals(1, DenomTier.of(Denomination(DenomKind.COIN, 20), eur))
        assertEquals(2, DenomTier.of(Denomination(DenomKind.COIN, 200), eur))
        assertEquals(2, DenomTier.of(Denomination(DenomKind.NOTE, 20000), eur))
    }

    @Test
    fun `face text uses minor for fractions, major for whole, K for large`() {
        val eur = Currencies.orFallback("EUR")
        assertEquals("50", DenomTier.faceText(Denomination(DenomKind.COIN, 50), eur))
        assertEquals("2", DenomTier.faceText(Denomination(DenomKind.COIN, 200), eur))
        val idr = Currencies.orFallback("IDR")
        assertEquals("100K", DenomTier.faceText(Denomination(DenomKind.NOTE, 100000), idr))
        assertEquals("5000", DenomTier.faceText(Denomination(DenomKind.NOTE, 5000), idr))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.CurrenciesTest'`
Expected: FAIL, compilation error `Unresolved reference 'Currencies'`.

- [ ] **Step 3: Implement `core/Currency.kt`**

```kotlin
package com.ilankog.coinwallet.core

import java.util.Locale

enum class DenomKind(val prefix: Char) { COIN('c'), NOTE('n') }

data class Denomination(val kind: DenomKind, val value: Long) {
    val key: String get() = "${kind.prefix}$value"
}

data class CurrencyInfo(
    val code: String,
    val name: String,
    val symbol: String,
    val symbolFirst: Boolean,
    val decimals: Int,
    val coins: List<Long>,
    val notes: List<Long>,
) {
    val minorPerMajor: Long = (1..decimals).fold(1L) { acc, _ -> acc * 10 }
    val denominations: List<Denomination> =
        coins.map { Denomination(DenomKind.COIN, it) } + notes.map { Denomination(DenomKind.NOTE, it) }
}

object Currencies {
    const val FALLBACK = "EUR"

    private fun c(
        code: String, name: String, symbol: String, symbolFirst: Boolean, decimals: Int,
        coins: String, notes: String,
    ) = CurrencyInfo(
        code, name, symbol, symbolFirst, decimals,
        coins.split(' ').map(String::toLong), notes.split(' ').map(String::toLong),
    )

    // Values in minor units. Source of currency list: api.frankfurter.dev/v1/currencies (2026-11-07).
    val all: List<CurrencyInfo> = listOf(
        c("AUD", "Australian Dollar", "A$", true, 2, "5 10 20 50 100 200", "500 1000 2000 5000 10000"),
        c("BRL", "Brazilian Real", "R$", true, 2, "5 10 25 50 100", "200 500 1000 2000 5000 10000 20000"),
        c("CAD", "Canadian Dollar", "C$", true, 2, "5 10 25 100 200", "500 1000 2000 5000 10000"),
        c("CHF", "Swiss Franc", "CHF ", true, 2, "5 10 20 50 100 200 500", "1000 2000 5000 10000 20000 100000"),
        c("CNY", "Chinese Renminbi Yuan", "CN¥", true, 2, "10 50 100", "100 500 1000 2000 5000 10000"),
        c("CZK", "Czech Koruna", "Kč", false, 2, "100 200 500 1000 2000 5000", "10000 20000 50000 100000 200000 500000"),
        c("DKK", "Danish Krone", "kr", false, 2, "50 100 200 500 1000 2000", "5000 10000 20000 50000 100000"),
        c("EUR", "Euro", "€", true, 2, "1 2 5 10 20 50 100 200", "500 1000 2000 5000 10000 20000"),
        c("GBP", "British Pound", "£", true, 2, "1 2 5 10 20 50 100 200", "500 1000 2000 5000"),
        c("HKD", "Hong Kong Dollar", "HK$", true, 2, "10 20 50 100 200 500 1000", "1000 2000 5000 10000 50000 100000"),
        c("HUF", "Hungarian Forint", "Ft", false, 0, "5 10 20 50 100 200", "500 1000 2000 5000 10000 20000"),
        c("IDR", "Indonesian Rupiah", "Rp", true, 0, "100 200 500 1000", "1000 2000 5000 10000 20000 50000 100000"),
        c("ILS", "Israeli New Shekel", "₪", true, 2, "10 50 100 200 500 1000", "2000 5000 10000 20000"),
        c("INR", "Indian Rupee", "₹", true, 2, "100 200 500 1000 2000", "1000 2000 5000 10000 20000 50000"),
        c("ISK", "Icelandic Króna", "kr", false, 0, "1 5 10 50 100", "500 1000 2000 5000 10000"),
        c("JPY", "Japanese Yen", "¥", true, 0, "1 5 10 50 100 500", "1000 2000 5000 10000"),
        c("KRW", "South Korean Won", "₩", true, 0, "10 50 100 500", "1000 5000 10000 50000"),
        c("MXN", "Mexican Peso", "MX$", true, 2, "50 100 200 500 1000 2000", "2000 5000 10000 20000 50000 100000"),
        c("MYR", "Malaysian Ringgit", "RM", true, 2, "5 10 20 50", "100 500 1000 2000 5000 10000"),
        c("NOK", "Norwegian Krone", "kr", false, 2, "100 500 1000 2000", "5000 10000 20000 50000 100000"),
        c("NZD", "New Zealand Dollar", "NZ$", true, 2, "10 20 50 100 200", "500 1000 2000 5000 10000"),
        c("PHP", "Philippine Peso", "₱", true, 2, "25 100 500 1000 2000", "2000 5000 10000 20000 50000 100000"),
        c("PLN", "Polish Złoty", "zł", false, 2, "1 2 5 10 20 50 100 200 500", "1000 2000 5000 10000 20000 50000"),
        c("RON", "Romanian Leu", "lei", false, 2, "1 5 10 50", "100 500 1000 5000 10000 20000 50000"),
        c("SEK", "Swedish Krona", "kr", false, 2, "100 200 500 1000", "2000 5000 10000 20000 50000 100000"),
        c("SGD", "Singapore Dollar", "S$", true, 2, "5 10 20 50 100", "200 500 1000 5000 10000 100000"),
        c("THB", "Thai Baht", "฿", true, 2, "25 50 100 200 500 1000", "2000 5000 10000 50000 100000"),
        c("TRY", "Turkish Lira", "₺", true, 2, "1 5 10 25 50 100", "500 1000 2000 5000 10000 20000"),
        c("USD", "United States Dollar", "$", true, 2, "1 5 10 25 50 100", "100 200 500 1000 2000 5000 10000"),
        c("ZAR", "South African Rand", "R", true, 2, "10 20 50 100 200 500", "1000 2000 5000 10000 20000"),
    )

    private val byCode: Map<String, CurrencyInfo> = all.associateBy { it.code }

    fun get(code: String): CurrencyInfo? = byCode[code]

    fun orFallback(code: String): CurrencyInfo = byCode[code] ?: byCode.getValue(FALLBACK)

    /** Currency of the locale's country if supported, else EUR (documented fallback, not an error). */
    fun defaultHome(locale: Locale): String {
        if (locale.country.isEmpty()) return FALLBACK
        val code = try {
            java.util.Currency.getInstance(locale)?.currencyCode
        } catch (e: IllegalArgumentException) {
            null // unsupported country code: fall back
        }
        return code?.takeIf { byCode.containsKey(it) } ?: FALLBACK
    }
}

object CurrencySearch {
    fun filter(query: String): List<CurrencyInfo> {
        val q = query.trim()
        if (q.isEmpty()) return Currencies.all
        return Currencies.all.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    }
}

object DenomTier {
    /** 0 = copper/low, 1 = brass/mid, 2 = silver/high, by position within its coin or note list. */
    fun of(d: Denomination, info: CurrencyInfo): Int {
        val list = if (d.kind == DenomKind.COIN) info.coins else info.notes
        val i = list.indexOf(d.value).coerceAtLeast(0)
        return (i * 3 / list.size).coerceIn(0, 2)
    }

    /** Short text printed on the coin/note art. */
    fun faceText(d: Denomination, info: CurrencyInfo): String {
        val v = d.value
        if (v % info.minorPerMajor != 0L) return v.toString()
        val major = v / info.minorPerMajor
        return if (major >= 10_000 && major % 1000 == 0L) "${major / 1000}K" else major.toString()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.CurrenciesTest'`
Expected: PASS (no output, exit 0).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/core/Currency.kt app/src/test/java/com/ilankog/coinwallet/Fixtures.kt app/src/test/java/com/ilankog/coinwallet/core/CurrenciesTest.kt
git commit -m "feat(core): add 30 ECB currencies with coin and note tables

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 3: Money formatting, rate parsing, relative time

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/core/Money.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/core/MoneyTest.kt`

**Interfaces:**
- Consumes: `CurrencyInfo`, `Currencies` (Task 2).
- Produces:
  - `object Money { val MAX_RATE: BigDecimal; fun toMajor(minor: Long, c: CurrencyInfo): BigDecimal; fun format(minor: Long, c: CurrencyInfo, locale: Locale = Locale.getDefault()): String; fun label(value: Long, c: CurrencyInfo, locale: Locale = Locale.getDefault()): String; fun formatRate(rate: BigDecimal): String; fun parseRate(text: String): BigDecimal? }`
  - `object RelativeTime { fun ago(then: Long, now: Long): String }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyTest {
    private val eur = Currencies.orFallback("EUR")
    private val jpy = Currencies.orFallback("JPY")
    private val czk = Currencies.orFallback("CZK")
    private val chf = Currencies.orFallback("CHF")

    @Test
    fun `format uses symbol position and currency decimals`() {
        assertEquals("€1,234.50", Money.format(123450, eur, Locale.US))
        assertEquals("¥1,500", Money.format(1500, jpy, Locale.US))
        assertEquals("123.45 Kč", Money.format(12345, czk, Locale.US))
        assertEquals("CHF 5.00", Money.format(500, chf, Locale.US))
        assertEquals("€0.00", Money.format(0, eur, Locale.US))
    }

    @Test
    fun `format follows locale separators`() {
        assertEquals("€1.234,50", Money.format(123450, eur, Locale.GERMANY))
    }

    @Test
    fun `label drops decimals for whole values`() {
        assertEquals("€0.50", Money.label(50, eur, Locale.US))
        assertEquals("€2", Money.label(200, eur, Locale.US))
        assertEquals("¥500", Money.label(500, jpy, Locale.US))
        assertEquals("5,000 Kč", Money.label(500000, czk, Locale.US))
    }

    @Test
    fun `parseRate accepts dot or comma decimals`() {
        assertEquals(BigDecimal("0.25"), Money.parseRate("0,25"))
        assertEquals(BigDecimal("3.6"), Money.parseRate(" 3.6 "))
        assertEquals(BigDecimal("0.5"), Money.parseRate(".5"))
        assertEquals(BigDecimal("1000000"), Money.parseRate("1000000"))
    }

    @Test
    fun `parseRate rejects invalid input`() {
        for (bad in listOf("", "abc", "0", "0.000", "-1", "1e3", "1000000.01", "1.2.3", ".", "1,2.3")) {
            assertNull(bad, Money.parseRate(bad))
        }
    }

    @Test
    fun `formatRate keeps five significant digits`() {
        assertEquals("0.14668", Money.formatRate(BigDecimal("0.1466817248")))
        assertEquals("176.77", Money.formatRate(BigDecimal("176.77")))
        assertEquals("3.6", Money.formatRate(BigDecimal("3.6000")))
        assertEquals("0.000049483", Money.formatRate(BigDecimal("0.0000494830")))
    }

    @Test
    fun `relative time buckets`() {
        val now = 10_000_000_000L
        assertEquals("just now", RelativeTime.ago(now - 30_000, now))
        assertEquals("5 min ago", RelativeTime.ago(now - 5 * 60_000, now))
        assertEquals("2h ago", RelativeTime.ago(now - 2 * 3_600_000 - 1, now))
        assertEquals("3d ago", RelativeTime.ago(now - 3 * 86_400_000L, now))
        assertEquals("just now", RelativeTime.ago(now + 60_000, now))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.MoneyTest'`
Expected: FAIL, `Unresolved reference 'Money'`.

- [ ] **Step 3: Implement `core/Money.kt`**

```kotlin
package com.ilankog.coinwallet.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

object Money {
    val MAX_RATE: BigDecimal = BigDecimal("1000000")

    fun toMajor(minor: Long, c: CurrencyInfo): BigDecimal = BigDecimal.valueOf(minor, c.decimals)

    fun format(minor: Long, c: CurrencyInfo, locale: Locale = Locale.getDefault()): String =
        withSymbol(number(toMajor(minor, c), c.decimals, locale), c)

    /** Denomination label: whole values without decimals ("€2"), fractions with ("€0.50"). */
    fun label(value: Long, c: CurrencyInfo, locale: Locale = Locale.getDefault()): String {
        val digits = if (value % c.minorPerMajor == 0L) 0 else c.decimals
        return withSymbol(number(toMajor(value, c), digits, locale), c)
    }

    fun formatRate(rate: BigDecimal): String =
        rate.round(MathContext(5, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()

    /** Positive decimal ≤ MAX_RATE, "." or "," separator, digits only. Null when invalid. */
    fun parseRate(text: String): BigDecimal? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty() || t.count { it == '.' } > 1 || !t.all { it in '0'..'9' || it == '.' }) return null
        val v = t.toBigDecimalOrNull() ?: return null
        return v.takeIf { it > BigDecimal.ZERO && it <= MAX_RATE }
    }

    private fun number(v: BigDecimal, digits: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            isGroupingUsed = true
            roundingMode = RoundingMode.HALF_EVEN
        }.format(v)

    private fun withSymbol(num: String, c: CurrencyInfo): String =
        if (c.symbolFirst) "${c.symbol}$num" else "$num ${c.symbol}"
}

object RelativeTime {
    fun ago(then: Long, now: Long): String {
        val mins = (now - then).coerceAtLeast(0) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 24 * 60 -> "${mins / 60}h ago"
            else -> "${mins / (24 * 60)}d ago"
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/core/Money.kt app/src/test/java/com/ilankog/coinwallet/core/MoneyTest.kt
git commit -m "feat(core): add money formatting, rate parsing and relative time

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 4: Trip model and counting rules

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/core/Trip.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/core/TripRulesTest.kt`

**Interfaces:**
- Consumes: `Denomination`, `CurrencyInfo`, `Currencies` (Task 2).
- Produces:
  - `@Serializable data class Trip(val id: String, val name: String, val currency: String, val counts: Map<String, Int> = emptyMap(), val createdAt: Long)`
  - `object TripRules { const val MAX_COUNT = 9999; const val MAX_NAME = 40; fun cleanName(raw: String): String?; fun count(trip: Trip, d: Denomination): Int; fun setCount(trip: Trip, d: Denomination, value: Int): Trip; fun increment(trip: Trip, d: Denomination): Trip; fun decrement(trip: Trip, d: Denomination): Trip; fun reset(trip: Trip): Trip; fun hasCounts(trip: Trip): Boolean; fun totalMinor(trip: Trip, info: CurrencyInfo): Long; fun parseCount(text: String): Int? }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRulesTest {
    private val eur = Currencies.orFallback("EUR")
    private val two = Denomination(DenomKind.COIN, 200)
    private val fiveCent = Denomination(DenomKind.COIN, 5)
    private val twenty = Denomination(DenomKind.NOTE, 2000)
    private val trip = Trip(id = "t", name = "Rome", currency = "EUR", createdAt = 1)

    @Test
    fun `increment and decrement change one denomination`() {
        val t = TripRules.increment(TripRules.increment(trip, two), two)
        assertEquals(2, TripRules.count(t, two))
        assertEquals(1, TripRules.count(TripRules.decrement(t, two), two))
        assertEquals(0, TripRules.count(t, fiveCent))
    }

    @Test
    fun `decrement at zero is a no-op and zero counts are removed`() {
        assertEquals(trip, TripRules.decrement(trip, two))
        val back = TripRules.decrement(TripRules.increment(trip, two), two)
        assertTrue(back.counts.isEmpty())
    }

    @Test
    fun `increment at max is a no-op`() {
        val full = TripRules.setCount(trip, two, 9999)
        assertEquals(full, TripRules.increment(full, two))
    }

    @Test
    fun `setCount clamps to range`() {
        assertEquals(9999, TripRules.count(TripRules.setCount(trip, two, 20000), two))
        assertEquals(0, TripRules.count(TripRules.setCount(trip, two, -3), two))
    }

    @Test
    fun `total sums value times count`() {
        var t = TripRules.setCount(trip, two, 2)
        t = TripRules.setCount(t, fiveCent, 3)
        t = TripRules.setCount(t, twenty, 1)
        assertEquals(400L + 15L + 2000L, TripRules.totalMinor(t, eur))
        assertTrue(TripRules.hasCounts(t))
        assertFalse(TripRules.hasCounts(TripRules.reset(t)))
    }

    @Test
    fun `coin and note of same value are counted separately`() {
        val cny = Currencies.orFallback("CNY")
        val coin = Denomination(DenomKind.COIN, 100)
        val note = Denomination(DenomKind.NOTE, 100)
        val t = TripRules.setCount(TripRules.setCount(trip.copy(currency = "CNY"), coin, 2), note, 3)
        assertEquals(2, TripRules.count(t, coin))
        assertEquals(3, TripRules.count(t, note))
        assertEquals(500L, TripRules.totalMinor(t, cny))
    }

    @Test
    fun `parseCount accepts digits 0 to 9999`() {
        assertEquals(0, TripRules.parseCount(""))
        assertEquals(0, TripRules.parseCount("0"))
        assertEquals(12, TripRules.parseCount(" 0012 "))
        assertEquals(9999, TripRules.parseCount("9999"))
    }

    @Test
    fun `parseCount rejects bad input`() {
        for (bad in listOf("10000", "-1", "1.5", "abc", "1 2", "99999999999")) assertNull(bad, TripRules.parseCount(bad))
    }

    @Test
    fun `cleanName trims and enforces length`() {
        assertEquals("Prague", TripRules.cleanName("  Prague "))
        assertNull(TripRules.cleanName("   "))
        assertEquals(40, TripRules.cleanName("x".repeat(40))?.length)
        assertNull(TripRules.cleanName("x".repeat(41)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.TripRulesTest'`
Expected: FAIL, `Unresolved reference 'Trip'`.

- [ ] **Step 3: Implement `core/Trip.kt`**

```kotlin
package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable

@Serializable
data class Trip(
    val id: String,
    val name: String,
    val currency: String,
    val counts: Map<String, Int> = emptyMap(),
    val createdAt: Long,
)

object TripRules {
    const val MAX_COUNT = 9999
    const val MAX_NAME = 40

    fun cleanName(raw: String): String? = raw.trim().takeIf { it.length in 1..MAX_NAME }

    fun count(trip: Trip, d: Denomination): Int = trip.counts[d.key] ?: 0

    fun setCount(trip: Trip, d: Denomination, value: Int): Trip {
        val v = value.coerceIn(0, MAX_COUNT)
        val counts = if (v == 0) trip.counts - d.key else trip.counts + (d.key to v)
        return if (counts == trip.counts) trip else trip.copy(counts = counts)
    }

    fun increment(trip: Trip, d: Denomination): Trip = setCount(trip, d, count(trip, d) + 1)

    fun decrement(trip: Trip, d: Denomination): Trip = setCount(trip, d, count(trip, d) - 1)

    fun reset(trip: Trip): Trip = trip.copy(counts = emptyMap())

    fun hasCounts(trip: Trip): Boolean = trip.counts.values.any { it > 0 }

    fun totalMinor(trip: Trip, info: CurrencyInfo): Long =
        info.denominations.sumOf { it.value * count(trip, it) }

    /** Typed count: digits only, empty = 0, max 9999. Null when invalid. */
    fun parseCount(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty()) return 0
        if (!t.all { it in '0'..'9' }) return null
        val n = t.trimStart('0')
        if (n.isEmpty()) return 0
        if (n.length > 4) return null
        return n.toInt().takeIf { it <= MAX_COUNT }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same command as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/core/Trip.kt app/src/test/java/com/ilankog/coinwallet/core/TripRulesTest.kt
git commit -m "feat(core): add trip model and counting rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---
### Task 5: Rates: parsing, conversion, staleness

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/core/Rates.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/core/RatesTest.kt`

**Interfaces:**
- Consumes: `Currencies`, `CurrencyInfo` (Task 2), fixture `frankfurter_latest.json`.
- Produces:
  - `@Serializable data class RateSnapshot(val fetchedAt: Long, val date: String, val rates: Map<String, Double>)` (EUR-based, includes `"EUR" to 1.0`)
  - `data class ManualRate(val rate: BigDecimal, val home: String)` ("1 trip currency = rate home")
  - `enum class RateSource { SAME, MANUAL, ECB }`, `data class ResolvedRate(val rate: BigDecimal, val source: RateSource)`
  - `sealed interface ParseResult { data class Ok(val date: String, val rates: Map<String, Double>); data class Error(val reason: String) }`
  - `object RatesParser { fun parse(body: String): ParseResult }`
  - `object Conversion { fun crossRate(snapshot: RateSnapshot?, from: String, to: String): BigDecimal?; fun resolve(from: String, home: String, manual: ManualRate?, snapshot: RateSnapshot?): ResolvedRate?; fun convert(minor: Long, from: CurrencyInfo, to: CurrencyInfo, rate: BigDecimal): Long }`
  - `object Staleness { const val MAX_AGE_MS: Long; fun isStale(snapshot: RateSnapshot?, now: Long): Boolean }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilankog.coinwallet.core

import com.ilankog.coinwallet.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class RatesTest {
    private val snapshot = RateSnapshot(
        fetchedAt = 0,
        date = "2026-11-06",
        rates = mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77),
    )

    @Test
    fun `parses real Frankfurter response`() {
        val r = RatesParser.parse(Fixtures.text("frankfurter_latest.json")) as ParseResult.Ok
        assertEquals("2026-11-06", r.date)
        assertEquals(30, r.rates.size)
        assertEquals(24.35, r.rates.getValue("CZK"), 0.0)
        assertEquals(1.0, r.rates.getValue("EUR"), 0.0)
    }

    @Test
    fun `rejects malformed or foreign-base bodies`() {
        assertTrue(RatesParser.parse("<html>busy</html>") is ParseResult.Error)
        assertTrue(RatesParser.parse("""{"base":"USD","date":"2026-11-06","rates":{"EUR":0.9}}""") is ParseResult.Error)
        assertTrue(RatesParser.parse("""{"base":"EUR","date":"2026-11-06","rates":{}}""") is ParseResult.Error)
        assertTrue(RatesParser.parse("""{"base":"EUR","date":"2026-11-06"}""") is ParseResult.Error)
    }

    @Test
    fun `drops unknown codes and non-positive rates`() {
        val body = """{"base":"EUR","date":"2026-11-06","rates":{"CZK":24.35,"XAU":0.0004,"USD":0,"GBP":-1.0}}"""
        val r = RatesParser.parse(body) as ParseResult.Ok
        assertEquals(setOf("EUR", "CZK"), r.rates.keys)
    }

    @Test
    fun `cross rate divides EUR based rates`() {
        assertEquals(BigDecimal("0.1466817248"), Conversion.crossRate(snapshot, "CZK", "ILS"))
        assertEquals(BigDecimal.ONE, Conversion.crossRate(null, "CZK", "CZK"))
        assertNull(Conversion.crossRate(snapshot, "THB", "ILS"))
        assertNull(Conversion.crossRate(null, "CZK", "ILS"))
    }

    @Test
    fun `convert rounds half even into target minor units`() {
        val czk = Currencies.orFallback("CZK")
        val ils = Currencies.orFallback("ILS")
        val jpy = Currencies.orFallback("JPY")
        val eur = Currencies.orFallback("EUR")
        assertEquals(1467L, Conversion.convert(10000, czk, ils, BigDecimal("0.1466817248")))
        assertEquals(566L, Conversion.convert(1000, jpy, eur, BigDecimal("0.0056570685")))
        assertEquals(1768L, Conversion.convert(1000, eur, jpy, BigDecimal("176.77")))
        assertEquals(2L, Conversion.convert(25, eur, eur, BigDecimal("0.1"))) // 0.025 -> 0.02 (half even)
    }

    @Test
    fun `resolve prefers same then manual then ECB`() {
        assertEquals(RateSource.SAME, Conversion.resolve("ILS", "ILS", null, null)?.source)
        val manual = ManualRate(BigDecimal("0.15"), "ILS")
        assertEquals(ResolvedRate(BigDecimal("0.15"), RateSource.MANUAL), Conversion.resolve("CZK", "ILS", manual, snapshot))
        assertEquals(RateSource.ECB, Conversion.resolve("CZK", "ILS", null, snapshot)?.source)
        assertNull(Conversion.resolve("CZK", "ILS", null, null))
    }

    @Test
    fun `manual rate saved for another home is ignored`() {
        val manual = ManualRate(BigDecimal("0.04"), "EUR")
        assertEquals(RateSource.ECB, Conversion.resolve("CZK", "ILS", manual, snapshot)?.source)
        assertNull(Conversion.resolve("CZK", "ILS", manual, null))
    }

    @Test
    fun `stale when missing, older than 12h, or from the future`() {
        val now = 100_000_000L
        assertTrue(Staleness.isStale(null, now))
        assertFalse(Staleness.isStale(snapshot.copy(fetchedAt = now - Staleness.MAX_AGE_MS), now))
        assertTrue(Staleness.isStale(snapshot.copy(fetchedAt = now - Staleness.MAX_AGE_MS - 1), now))
        assertTrue(Staleness.isStale(snapshot.copy(fetchedAt = now + 1), now))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.RatesTest'`
Expected: FAIL, `Unresolved reference 'RateSnapshot'`.

- [ ] **Step 3: Implement `core/Rates.kt`**

```kotlin
package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode

/** EUR-based ECB rates; always contains "EUR" to 1.0. */
@Serializable
data class RateSnapshot(val fetchedAt: Long, val date: String, val rates: Map<String, Double>)

/** "1 trip currency = [rate] [home]", valid only while the home currency equals [home]. */
data class ManualRate(val rate: BigDecimal, val home: String)

enum class RateSource { SAME, MANUAL, ECB }

data class ResolvedRate(val rate: BigDecimal, val source: RateSource)

sealed interface ParseResult {
    data class Ok(val date: String, val rates: Map<String, Double>) : ParseResult
    data class Error(val reason: String) : ParseResult
}

object RatesParser {
    @Serializable
    private data class Body(val base: String, val date: String, val rates: Map<String, Double>)

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): ParseResult {
        val parsed = try {
            json.decodeFromString(Body.serializer(), body)
        } catch (e: IllegalArgumentException) { // SerializationException is an IllegalArgumentException
            return ParseResult.Error("malformed body: ${e.message}")
        }
        if (parsed.base != "EUR") return ParseResult.Error("unexpected base ${parsed.base}")
        val rates = parsed.rates.filter { (code, r) -> Currencies.get(code) != null && r.isFinite() && r > 0.0 } +
            ("EUR" to 1.0)
        if (rates.size < 2) return ParseResult.Error("no usable rates")
        return ParseResult.Ok(parsed.date, rates)
    }
}

object Conversion {
    private const val SCALE = 10

    fun crossRate(snapshot: RateSnapshot?, from: String, to: String): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        val rates = snapshot?.rates ?: return null
        val f = rates[from] ?: return null
        val t = rates[to] ?: return null
        return BigDecimal.valueOf(t).divide(BigDecimal.valueOf(f), SCALE, RoundingMode.HALF_EVEN)
    }

    fun resolve(from: String, home: String, manual: ManualRate?, snapshot: RateSnapshot?): ResolvedRate? {
        if (from == home) return ResolvedRate(BigDecimal.ONE, RateSource.SAME)
        if (manual != null && manual.home == home) return ResolvedRate(manual.rate, RateSource.MANUAL)
        return crossRate(snapshot, from, home)?.let { ResolvedRate(it, RateSource.ECB) }
    }

    fun convert(minor: Long, from: CurrencyInfo, to: CurrencyInfo, rate: BigDecimal): Long =
        BigDecimal.valueOf(minor, from.decimals)
            .multiply(rate)
            .movePointRight(to.decimals)
            .setScale(0, RoundingMode.HALF_EVEN)
            .longValueExact()
}

object Staleness {
    const val MAX_AGE_MS: Long = 12L * 60 * 60 * 1000

    fun isStale(snapshot: RateSnapshot?, now: Long): Boolean =
        snapshot == null || snapshot.fetchedAt > now || now - snapshot.fetchedAt > MAX_AGE_MS
}
```

Note: `longValueExact` cannot overflow within limits: max trip ≈ 1.9e9 IDR × MAX_RATE 1e6 × 100 ≈ 1.9e17 < Long.MAX.

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/core/Rates.kt app/src/test/java/com/ilankog/coinwallet/core/RatesTest.kt
git commit -m "feat(core): parse ECB rates and convert with rate priority

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 6: Trips file codec and totals

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/core/TripsCodec.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/core/Totals.kt`
- Create: `app/src/test/resources/trips_corrupt.json`
- Test: `app/src/test/java/com/ilankog/coinwallet/core/TripsCodecTest.kt`, `app/src/test/java/com/ilankog/coinwallet/core/TotalsTest.kt`

**Interfaces:**
- Consumes: `Trip`, `TripRules` (Task 4), `Currencies` (Task 2), `RateSnapshot`, `ManualRate`, `ResolvedRate`, `Conversion` (Task 5).
- Produces:
  - `sealed interface DecodeResult { data class Ok(val trips: List<Trip>, val droppedKeys: Int); data class Corrupt(val reason: String) }`
  - `object TripsCodec { const val VERSION = 1; fun encode(trips: List<Trip>): String; fun decode(text: String): DecodeResult }`
  - `data class TripSummary(val trip: Trip, val info: CurrencyInfo, val totalMinor: Long, val homeMinor: Long?, val rate: ResolvedRate?)`
  - `data class WalletTotals(val trips: List<TripSummary>, val grandHomeMinor: Long, val missingRateCount: Int)`
  - `object Totals { fun summarize(trip: Trip, info: CurrencyInfo, home: CurrencyInfo, manual: ManualRate?, snapshot: RateSnapshot?): TripSummary; fun all(trips: List<Trip>, home: CurrencyInfo, manual: Map<String, ManualRate>, snapshot: RateSnapshot?): WalletTotals }` (`all` sorts newest `createdAt` first)

- [ ] **Step 1: Create the corrupt fixture**

```bash
cd /home/user/coin-wallet && printf '%s' '{"version":1,"trips":[{"id":"a","name":"Prague","currency":"CZK","counts":{"c100":' > app/src/test/resources/trips_corrupt.json
```

- [ ] **Step 2: Write the failing tests**

`TripsCodecTest.kt`:
```kotlin
package com.ilankog.coinwallet.core

import com.ilankog.coinwallet.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsCodecTest {
    private val trips = listOf(
        Trip("a", "Prague", "CZK", mapOf("c100" to 3, "n20000" to 1), createdAt = 10),
        Trip("b", "Tokyo", "JPY", emptyMap(), createdAt = 20),
    )

    @Test
    fun `round trip`() {
        assertEquals(DecodeResult.Ok(trips, 0), TripsCodec.decode(TripsCodec.encode(trips)))
    }

    @Test
    fun `truncated file is corrupt`() {
        assertTrue(TripsCodec.decode(Fixtures.text("trips_corrupt.json")) is DecodeResult.Corrupt)
        assertTrue(TripsCodec.decode("not json") is DecodeResult.Corrupt)
    }

    @Test
    fun `wrong version is corrupt`() {
        assertTrue(TripsCodec.decode("""{"version":2,"trips":[]}""") is DecodeResult.Corrupt)
    }

    @Test
    fun `unknown currency is corrupt`() {
        val text = """{"version":1,"trips":[{"id":"a","name":"X","currency":"XAU","createdAt":1}]}"""
        assertTrue(TripsCodec.decode(text) is DecodeResult.Corrupt)
    }

    @Test
    fun `count outside range is corrupt`() {
        for (n in listOf(10000, -1)) {
            val text = """{"version":1,"trips":[{"id":"a","name":"X","currency":"EUR","counts":{"c1":$n},"createdAt":1}]}"""
            assertTrue("$n", TripsCodec.decode(text) is DecodeResult.Corrupt)
        }
    }

    @Test
    fun `unknown denomination keys are dropped`() {
        val text = """{"version":1,"trips":[{"id":"a","name":"X","currency":"EUR","counts":{"c1":2,"c3":4},"createdAt":1}]}"""
        val r = TripsCodec.decode(text) as DecodeResult.Ok
        assertEquals(mapOf("c1" to 2), r.trips.single().counts)
        assertEquals(1, r.droppedKeys)
    }
}
```

`TotalsTest.kt`:
```kotlin
package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class TotalsTest {
    private val ils = Currencies.orFallback("ILS")
    private val snapshot = RateSnapshot(0, "2026-11-06", mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77))
    private val prague = Trip("p", "Prague", "CZK", mapOf("n10000" to 1), createdAt = 1)    // 100.00 Kč
    private val tokyo = Trip("t", "Tokyo", "JPY", mapOf("n1000" to 1), createdAt = 2)       // ¥1000
    private val bangkok = Trip("b", "Bangkok", "THB", mapOf("n2000" to 1), createdAt = 0)   // no THB rate

    @Test
    fun `sums trips with rates and counts the rest`() {
        val totals = Totals.all(listOf(prague, tokyo, bangkok), ils, emptyMap(), snapshot)
        assertEquals(listOf("t", "p", "b"), totals.trips.map { it.trip.id })
        assertEquals(2021L, totals.trips[0].homeMinor)
        assertEquals(1467L, totals.trips[1].homeMinor)
        assertNull(totals.trips[2].homeMinor)
        assertEquals(3488L, totals.grandHomeMinor)
        assertEquals(1, totals.missingRateCount)
    }

    @Test
    fun `manual rate fills a missing ECB rate`() {
        val manual = mapOf("THB" to ManualRate(BigDecimal("0.1"), "ILS"))
        val totals = Totals.all(listOf(bangkok), ils, manual, snapshot)
        assertEquals(200L, totals.grandHomeMinor) // 20.00 THB * 0.1 = 2.00 ILS
        assertEquals(RateSource.MANUAL, totals.trips.single().rate?.source)
        assertEquals(0, totals.missingRateCount)
    }

    @Test
    fun `empty list`() {
        val totals = Totals.all(emptyList(), ils, emptyMap(), null)
        assertEquals(WalletTotals(emptyList(), 0L, 0), totals)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.TripsCodecTest' --tests 'com.ilankog.coinwallet.core.TotalsTest'`
Expected: FAIL, `Unresolved reference 'TripsCodec'` / `'Totals'`.

- [ ] **Step 4: Implement `core/TripsCodec.kt`**

```kotlin
package com.ilankog.coinwallet.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

sealed interface DecodeResult {
    data class Ok(val trips: List<Trip>, val droppedKeys: Int) : DecodeResult
    data class Corrupt(val reason: String) : DecodeResult
}

object TripsCodec {
    const val VERSION = 1

    @Serializable
    private data class FileBody(val version: Int, val trips: List<Trip>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(trips: List<Trip>): String = json.encodeToString(FileBody.serializer(), FileBody(VERSION, trips))

    fun decode(text: String): DecodeResult {
        val body = try {
            json.decodeFromString(FileBody.serializer(), text)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Corrupt("parse: ${e.message}")
        }
        if (body.version != VERSION) return DecodeResult.Corrupt("version ${body.version}")
        var dropped = 0
        val trips = body.trips.map { t ->
            val info = Currencies.get(t.currency) ?: return DecodeResult.Corrupt("unknown currency ${t.currency}")
            if (t.counts.values.any { it !in 0..TripRules.MAX_COUNT }) return DecodeResult.Corrupt("count out of range in ${t.id}")
            val valid = info.denominations.map { it.key }.toSet()
            val kept = t.counts.filterKeys { it in valid }
            dropped += t.counts.size - kept.size
            t.copy(counts = kept)
        }
        return DecodeResult.Ok(trips, dropped)
    }
}
```

- [ ] **Step 5: Implement `core/Totals.kt`**

```kotlin
package com.ilankog.coinwallet.core

data class TripSummary(
    val trip: Trip,
    val info: CurrencyInfo,
    val totalMinor: Long,
    val homeMinor: Long?,
    val rate: ResolvedRate?,
)

data class WalletTotals(val trips: List<TripSummary>, val grandHomeMinor: Long, val missingRateCount: Int)

object Totals {
    fun summarize(trip: Trip, info: CurrencyInfo, home: CurrencyInfo, manual: ManualRate?, snapshot: RateSnapshot?): TripSummary {
        val total = TripRules.totalMinor(trip, info)
        val rate = Conversion.resolve(info.code, home.code, manual, snapshot)
        return TripSummary(trip, info, total, rate?.let { Conversion.convert(total, info, home, it.rate) }, rate)
    }

    fun all(trips: List<Trip>, home: CurrencyInfo, manual: Map<String, ManualRate>, snapshot: RateSnapshot?): WalletTotals {
        val summaries = trips.sortedByDescending { it.createdAt }.mapNotNull { t ->
            Currencies.get(t.currency)?.let { summarize(t, it, home, manual[t.currency], snapshot) }
        }
        return WalletTotals(
            trips = summaries,
            grandHomeMinor = summaries.sumOf { it.homeMinor ?: 0L },
            missingRateCount = summaries.count { it.homeMinor == null },
        )
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: same as Step 3. Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/core/TripsCodec.kt app/src/main/java/com/ilankog/coinwallet/core/Totals.kt app/src/test/resources/trips_corrupt.json app/src/test/java/com/ilankog/coinwallet/core/TripsCodecTest.kt app/src/test/java/com/ilankog/coinwallet/core/TotalsTest.kt
git commit -m "feat(core): add validated trips codec and wallet totals

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 7: Atomic files and trip repository

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/data/AtomicFiles.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/data/TripRepository.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/data/TripRepositoryTest.kt`

**Interfaces:**
- Consumes: `Trip`, `TripRules` (Task 4), `TripsCodec`, `DecodeResult` (Task 6), fixture `trips_corrupt.json`.
- Produces:
  - `object AtomicFiles { fun write(target: File, text: String); fun readOrNull(file: File): String?; fun moveAside(file: File, to: File) }` (all `@Throws(IOException::class)`)
  - `sealed interface RepoNotice { data object TripsReset; data object SaveFailed; data class Deleted(val trip: Trip) }`
  - `class TripRepository(dir: File, io: CoroutineDispatcher = Dispatchers.IO, newId: () -> String = UUID, clock: () -> Long = System::currentTimeMillis)` with `val trips: StateFlow<List<Trip>>`, `val isLoaded: StateFlow<Boolean>`, `val notice: StateFlow<RepoNotice?>`, `fun clearNotice(n: RepoNotice)`, `suspend fun load()`, `suspend fun create(name: String, currency: String): Trip`, `suspend fun update(id: String, transform: (Trip) -> Trip): Trip?`, `suspend fun delete(id: String): Trip?`, `suspend fun restore(trip: Trip)`. Callers pass already-validated name/currency.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var ids = 0
    private val two = Denomination(DenomKind.COIN, 200)

    private fun TestScope.repo(dir: File = folder.root) =
        TripRepository(dir, io = UnconfinedTestDispatcher(testScheduler), newId = { "id${++ids}" }, clock = { 1000L })

    @Test
    fun `missing file loads empty without notice`() = runTest {
        val r = repo()
        r.load()
        assertEquals(emptyList<Trip>(), r.trips.value)
        assertTrue(r.isLoaded.value)
        assertNull(r.notice.value)
    }

    @Test
    fun `create persists across instances`() = runTest {
        val t = repo().create("Prague", "CZK")
        assertEquals(Trip("id1", "Prague", "CZK", emptyMap(), 1000L), t)
        val again = repo()
        again.load()
        assertEquals(listOf(t), again.trips.value)
    }

    @Test
    fun `update persists and unknown id returns null`() = runTest {
        val r = repo()
        val t = r.create("Prague", "CZK")
        val updated = r.update(t.id) { TripRules.increment(it, two) }
        assertEquals(1, updated?.let { TripRules.count(it, two) })
        assertNull(r.update("nope") { it })
        val again = repo()
        again.load()
        assertEquals(1, TripRules.count(again.trips.value.single(), two))
    }

    @Test
    fun `delete posts notice and restore brings the same trip back`() = runTest {
        val r = repo()
        val t = r.create("Prague", "CZK")
        assertEquals(t, r.delete(t.id))
        assertTrue(r.trips.value.isEmpty())
        assertEquals(RepoNotice.Deleted(t), r.notice.value)
        r.clearNotice(RepoNotice.SaveFailed)
        assertEquals(RepoNotice.Deleted(t), r.notice.value) // other notice not cleared
        r.clearNotice(RepoNotice.Deleted(t))
        assertNull(r.notice.value)
        r.restore(t)
        r.restore(t) // idempotent
        assertEquals(listOf(t), r.trips.value)
        val again = repo()
        again.load()
        assertEquals(listOf(t), again.trips.value)
    }

    @Test
    fun `corrupt file is moved aside and reported`() = runTest {
        val text = Fixtures.text("trips_corrupt.json")
        File(folder.root, "trips.json").writeText(text)
        val r = repo()
        r.load()
        assertTrue(r.trips.value.isEmpty())
        assertEquals(RepoNotice.TripsReset, r.notice.value)
        assertEquals(text, File(folder.root, "trips.corrupt.json").readText())
        assertFalse(File(folder.root, "trips.json").exists())
    }

    @Test
    fun `save failure is reported and memory state kept`() = runTest {
        val r = repo(File(folder.root, "missing-dir"))
        val t = r.create("Oslo", "NOK")
        assertEquals(listOf(t), r.trips.value)
        assertEquals(RepoNotice.SaveFailed, r.notice.value)
    }

    @Test
    fun `parallel increments are all applied`() = runTest {
        val r = repo()
        val t = r.create("Prague", "CZK")
        (1..50).map { launch { r.update(t.id) { TripRules.increment(it, two) } } }.joinAll()
        assertEquals(50, TripRules.count(r.trips.value.single(), two))
        val again = repo()
        again.load()
        assertEquals(50, TripRules.count(again.trips.value.single(), two))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.data.TripRepositoryTest'`
Expected: FAIL, `Unresolved reference 'TripRepository'`.

- [ ] **Step 3: Implement `data/AtomicFiles.kt`**

```kotlin
package com.ilankog.coinwallet.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFiles {
    /** Write to "<name>.tmp", fsync, then atomically rename over [target]. */
    @Throws(IOException::class)
    fun write(target: File, text: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    @Throws(IOException::class)
    fun readOrNull(file: File): String? = if (file.exists()) file.readText(Charsets.UTF_8) else null

    @Throws(IOException::class)
    fun moveAside(file: File, to: File) {
        Files.move(file.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
```

- [ ] **Step 4: Implement `data/TripRepository.kt`**

```kotlin
package com.ilankog.coinwallet.data

import android.util.Log
import com.ilankog.coinwallet.core.DecodeResult
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripsCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface RepoNotice {
    data object TripsReset : RepoNotice
    data object SaveFailed : RepoNotice
    data class Deleted(val trip: Trip) : RepoNotice
}

/**
 * In-memory list is the source of truth; every change is written to trips.json.
 * All mutations go through one Mutex so rapid taps apply in order.
 */
class TripRepository(
    dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(dir, FILE)
    private val corruptFile = File(dir, CORRUPT_FILE)
    private val mutex = Mutex()
    private var loaded = false

    private val _trips = MutableStateFlow<List<Trip>>(emptyList())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _notice = MutableStateFlow<RepoNotice?>(null)
    val notice: StateFlow<RepoNotice?> = _notice.asStateFlow()

    /** Clears [n] only if it is still the current notice. */
    fun clearNotice(n: RepoNotice) {
        _notice.compareAndSet(n, null)
    }

    suspend fun load() = mutex.withLock { ensureLoaded() }

    suspend fun create(name: String, currency: String): Trip = mutex.withLock {
        ensureLoaded()
        val trip = Trip(id = newId(), name = name, currency = currency, createdAt = clock())
        commit(_trips.value + trip)
        trip
    }

    suspend fun update(id: String, transform: (Trip) -> Trip): Trip? = mutex.withLock {
        ensureLoaded()
        val current = _trips.value.firstOrNull { it.id == id } ?: return@withLock null
        val next = transform(current)
        if (next != current) commit(_trips.value.map { if (it.id == id) next else it })
        next
    }

    suspend fun delete(id: String): Trip? = mutex.withLock {
        ensureLoaded()
        val trip = _trips.value.firstOrNull { it.id == id } ?: return@withLock null
        commit(_trips.value.filterNot { it.id == id })
        _notice.value = RepoNotice.Deleted(trip)
        trip
    }

    suspend fun restore(trip: Trip) = mutex.withLock {
        ensureLoaded()
        if (_trips.value.none { it.id == trip.id }) commit(_trips.value + trip)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _trips.value = withContext(io) { readFile() }
        _isLoaded.value = true
    }

    private fun readFile(): List<Trip> {
        val text = try {
            AtomicFiles.readOrNull(file)
        } catch (e: IOException) {
            Log.e(TAG, "trips read failed", e)
            return resetCorrupt("read failed: ${e.message}")
        }
        if (text == null) return emptyList()
        return when (val r = TripsCodec.decode(text)) {
            is DecodeResult.Ok -> {
                if (r.droppedKeys > 0) Log.w(TAG, "dropped ${r.droppedKeys} unknown count keys")
                r.trips
            }
            is DecodeResult.Corrupt -> resetCorrupt(r.reason)
        }
    }

    private fun resetCorrupt(reason: String): List<Trip> {
        Log.e(TAG, "trips file corrupt: $reason")
        try {
            AtomicFiles.moveAside(file, corruptFile)
        } catch (e: IOException) {
            Log.e(TAG, "could not move corrupt trips file aside", e)
        }
        _notice.value = RepoNotice.TripsReset
        return emptyList()
    }

    private suspend fun commit(next: List<Trip>) {
        _trips.value = next
        try {
            withContext(io) { AtomicFiles.write(file, TripsCodec.encode(next)) }
        } catch (e: IOException) {
            Log.e(TAG, "trips save failed", e)
            _notice.value = RepoNotice.SaveFailed
        }
    }

    companion object {
        const val FILE = "trips.json"
        const val CORRUPT_FILE = "trips.corrupt.json"
        private const val TAG = "TripRepository"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/data/AtomicFiles.kt app/src/main/java/com/ilankog/coinwallet/data/TripRepository.kt app/src/test/java/com/ilankog/coinwallet/data/TripRepositoryTest.kt
git commit -m "feat(data): add atomic trip repository with corrupt-file recovery

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 8: HTTP fetcher and rate repository

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/data/Http.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/data/RateRepository.kt`
- Create: `app/src/test/java/com/ilankog/coinwallet/data/FakeFetcher.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/data/RateRepositoryTest.kt`

**Interfaces:**
- Consumes: `AtomicFiles` (Task 7), `RateSnapshot`, `RatesParser`, `ParseResult` (Task 5).
- Produces:
  - `data class HttpResponse(val code: Int, val body: String)`, `fun interface HttpFetcher { fun get(url: String): HttpResponse }` (throws `IOException`), `class UrlConnectionFetcher(timeoutMs: Int = 10_000) : HttpFetcher`
  - `sealed interface RefreshResult { data object Success; data object Offline; data class HttpError(val code: Int); data object BadData }`
  - `class RateRepository(dir: File, fetcher: HttpFetcher, scope: CoroutineScope, io: CoroutineDispatcher = Dispatchers.IO, clock: () -> Long = System::currentTimeMillis)` with `val snapshot: StateFlow<RateSnapshot?>`, `val busy: StateFlow<Boolean>`, `val lastResult: StateFlow<RefreshResult?>`, `suspend fun load()`, `suspend fun refresh(): RefreshResult`, `companion const val URL`.
  - test helper `class FakeFetcher(var response: () -> HttpResponse) : HttpFetcher { calls: Int; lastUrl: String? }`

- [ ] **Step 1: Write the fake and the failing test**

`FakeFetcher.kt`:
```kotlin
package com.ilankog.coinwallet.data

class FakeFetcher(var response: () -> HttpResponse) : HttpFetcher {
    @Volatile var calls = 0
    @Volatile var lastUrl: String? = null

    override fun get(url: String): HttpResponse {
        calls++
        lastUrl = url
        return response()
    }
}
```

`RateRepositoryTest.kt`:
```kotlin
package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.Fixtures
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class RateRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val body = Fixtures.text("frankfurter_latest.json")

    private fun TestScope.repo(fetcher: HttpFetcher, io: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler)) =
        RateRepository(folder.root, fetcher, backgroundScope, io, clock = { 5000L })

    @Test
    fun `success stores snapshot and cache`() = runTest {
        val f = FakeFetcher { HttpResponse(200, body) }
        val r = repo(f)
        assertEquals(RefreshResult.Success, r.refresh())
        val s = requireNotNull(r.snapshot.value)
        assertEquals("2026-11-06", s.date)
        assertEquals(5000L, s.fetchedAt)
        assertEquals(24.35, s.rates.getValue("CZK"), 0.0)
        assertFalse(r.busy.value)
        assertEquals(RefreshResult.Success, r.lastResult.value)
        assertEquals(RateRepository.URL, f.lastUrl)
        val again = repo(f)
        again.load()
        assertEquals(s, again.snapshot.value)
    }

    @Test
    fun `offline keeps previous snapshot`() = runTest {
        val f = FakeFetcher { HttpResponse(200, body) }
        val r = repo(f)
        r.refresh()
        val before = r.snapshot.value
        var busyDuringFetch = false
        f.response = { busyDuringFetch = r.busy.value; throw IOException("offline") }
        assertEquals(RefreshResult.Offline, r.refresh())
        assertTrue(busyDuringFetch)
        assertFalse(r.busy.value)
        assertEquals(before, r.snapshot.value)
        assertEquals(RefreshResult.Offline, r.lastResult.value)
    }

    @Test
    fun `http error and bad data are reported`() = runTest {
        val f = FakeFetcher { HttpResponse(503, "busy") }
        val r = repo(f)
        assertEquals(RefreshResult.HttpError(503), r.refresh())
        f.response = { HttpResponse(200, "<html>") }
        assertEquals(RefreshResult.BadData, r.refresh())
        assertNull(r.snapshot.value)
    }

    @Test
    fun `corrupt cache is deleted on load`() = runTest {
        val cache = File(folder.root, RateRepository.FILE)
        cache.writeText("{oops")
        val r = repo(FakeFetcher { HttpResponse(200, body) })
        r.load()
        assertNull(r.snapshot.value)
        assertFalse(cache.exists())
    }

    @Test
    fun `concurrent refreshes share one request`() = runTest {
        val f = FakeFetcher { HttpResponse(200, body) }
        val r = repo(f, StandardTestDispatcher(testScheduler))
        val a = async { r.refresh() }
        val b = async { r.refresh() }
        assertEquals(RefreshResult.Success, a.await())
        assertEquals(RefreshResult.Success, b.await())
        assertEquals(1, f.calls)
    }

    @Test
    fun `cancelled caller does not leave busy stuck`() = runTest {
        val gate = CountDownLatch(1)
        val f = FakeFetcher { gate.await(5, TimeUnit.SECONDS); HttpResponse(200, body) }
        val r = RateRepository(folder.root, f, backgroundScope, Dispatchers.IO, clock = { 5000L })
        val caller = launch { r.refresh() }
        withContext(Dispatchers.Default) { withTimeout(5000) { r.busy.first { it } } }
        caller.cancelAndJoin()
        gate.countDown()
        withContext(Dispatchers.Default) { withTimeout(5000) { r.busy.first { !it } } }
        assertNotNull(r.snapshot.value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.data.RateRepositoryTest'`
Expected: FAIL, `Unresolved reference 'HttpFetcher'`.

- [ ] **Step 3: Implement `data/Http.kt`**

```kotlin
package com.ilankog.coinwallet.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val code: Int, val body: String)

fun interface HttpFetcher {
    @Throws(IOException::class)
    fun get(url: String): HttpResponse
}

/** Blocking GET; call from Dispatchers.IO. Only untested glue in the data layer. */
class UrlConnectionFetcher(private val timeoutMs: Int = 10_000) : HttpFetcher {
    override fun get(url: String): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }
}
```

- [ ] **Step 4: Implement `data/RateRepository.kt`**

```kotlin
package com.ilankog.coinwallet.data

import android.util.Log
import com.ilankog.coinwallet.core.ParseResult
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.RatesParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

sealed interface RefreshResult {
    data object Success : RefreshResult
    data object Offline : RefreshResult
    data class HttpError(val code: Int) : RefreshResult
    data object BadData : RefreshResult
}

/**
 * Cached ECB rates. refresh() is single-flight: the request runs in [scope] (app-wide),
 * so a caller leaving the screen never cancels it and busy is always cleared in finally.
 */
class RateRepository(
    dir: File,
    private val fetcher: HttpFetcher,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val file = File(dir, FILE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private var loaded = false
    private var inFlight: Deferred<RefreshResult>? = null

    private val _snapshot = MutableStateFlow<RateSnapshot?>(null)
    val snapshot: StateFlow<RateSnapshot?> = _snapshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _lastResult = MutableStateFlow<RefreshResult?>(null)
    val lastResult: StateFlow<RefreshResult?> = _lastResult.asStateFlow()

    suspend fun load() {
        val first = lock.withLock {
            if (loaded) false else { loaded = true; true }
        }
        if (!first) return
        val cached = withContext(io) { readCache() }
        if (_snapshot.value == null) _snapshot.value = cached
    }

    suspend fun refresh(): RefreshResult {
        val job = lock.withLock {
            inFlight?.takeIf { it.isActive } ?: scope.async { doRefresh() }.also { inFlight = it }
        }
        return job.await()
    }

    private suspend fun doRefresh(): RefreshResult {
        _busy.value = true
        try {
            val result = fetchAndStore()
            _lastResult.value = result
            return result
        } finally {
            _busy.value = false
        }
    }

    private suspend fun fetchAndStore(): RefreshResult {
        val response = try {
            withContext(io) { fetcher.get(URL) }
        } catch (e: IOException) {
            Log.w(TAG, "rates fetch failed: ${e.message}")
            return RefreshResult.Offline
        }
        if (response.code != 200) {
            Log.w(TAG, "rates fetch HTTP ${response.code}")
            return RefreshResult.HttpError(response.code)
        }
        return when (val parsed = RatesParser.parse(response.body)) {
            is ParseResult.Error -> {
                Log.w(TAG, "rates parse failed: ${parsed.reason}")
                RefreshResult.BadData
            }
            is ParseResult.Ok -> {
                val snap = RateSnapshot(clock(), parsed.date, parsed.rates)
                _snapshot.value = snap
                try {
                    withContext(io) { AtomicFiles.write(file, json.encodeToString(RateSnapshot.serializer(), snap)) }
                } catch (e: IOException) {
                    Log.w(TAG, "rates cache write failed", e)
                }
                RefreshResult.Success
            }
        }
    }

    private fun readCache(): RateSnapshot? {
        val text = try {
            AtomicFiles.readOrNull(file)
        } catch (e: IOException) {
            Log.w(TAG, "rates cache read failed", e)
            return null
        }
        if (text == null) return null
        return try {
            json.decodeFromString(RateSnapshot.serializer(), text)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "rates cache corrupt, deleting", e)
            if (!file.delete()) Log.w(TAG, "could not delete corrupt rates cache")
            null
        }
    }

    companion object {
        const val URL = "https://api.frankfurter.dev/v1/latest"
        const val FILE = "rates.json"
        private const val TAG = "RateRepository"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/data/Http.kt app/src/main/java/com/ilankog/coinwallet/data/RateRepository.kt app/src/test/java/com/ilankog/coinwallet/data/FakeFetcher.kt app/src/test/java/com/ilankog/coinwallet/data/RateRepositoryTest.kt
git commit -m "feat(data): add single-flight cached rate repository

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 9: Settings, app container, Application

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/core/Settings.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/data/SettingsStore.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/data/AppContainer.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/CoinWalletApp.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `android:name=".CoinWalletApp"` to `<application>`)
- Create: `app/src/test/java/com/ilankog/coinwallet/data/FakeSettings.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/core/SettingsCodecTest.kt`

**Interfaces:**
- Consumes: `Currencies` (Task 2), `Money.parseRate` (Task 3), `ManualRate` (Task 5), `TripRepository` (Task 7), `RateRepository`, `UrlConnectionFetcher` (Task 8).
- Produces:
  - `data class Settings(val homeCurrency: String, val manualRates: Map<String, ManualRate>)`
  - `object SettingsCodec { const val KEY_HOME = "home_currency"; fun rateKey(code: String): String; fun homeKey(code: String): String; fun decode(values: Map<String, *>, defaultHome: String): Settings }`
  - `interface SettingsSource { val settings: StateFlow<Settings>; fun setHomeCurrency(code: String); fun setManualRate(currency: String, rate: BigDecimal, home: String); fun clearManualRate(currency: String) }`
  - `class SettingsStore(prefs: SharedPreferences, defaultHome: String) : SettingsSource`
  - `class AppContainer(context: Context) { val appScope; val trips: TripRepository; val rates: RateRepository; val settings: SettingsSource }`
  - `class CoinWalletApp : Application() { val container: AppContainer }`
  - test helper `class FakeSettings(home: String = "ILS") : SettingsSource`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ilankog.coinwallet.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class SettingsCodecTest {
    @Test
    fun `defaults when empty`() {
        assertEquals(Settings("ILS", emptyMap()), SettingsCodec.decode(emptyMap<String, Any>(), "ILS"))
    }

    @Test
    fun `reads home and manual rates`() {
        val s = SettingsCodec.decode(
            mapOf(SettingsCodec.KEY_HOME to "CZK", SettingsCodec.rateKey("USD") to "3.6", SettingsCodec.homeKey("USD") to "ILS"),
            "EUR",
        )
        assertEquals("CZK", s.homeCurrency)
        assertEquals(mapOf("USD" to ManualRate(BigDecimal("3.6"), "ILS")), s.manualRates)
    }

    @Test
    fun `ignores invalid entries`() {
        val s = SettingsCodec.decode(
            mapOf(
                SettingsCodec.KEY_HOME to "XXX",
                "manual_rate_USD" to "abc", "manual_home_USD" to "ILS",
                "manual_rate_GBP" to "1.2",
                "manual_rate_XAU" to "2", "manual_home_XAU" to "ILS",
                "manual_rate_JPY" to 5, "manual_home_JPY" to "ILS",
                "manual_rate_THB" to "0.1", "manual_home_THB" to "ZZZ",
            ),
            "EUR",
        )
        assertEquals(Settings("EUR", emptyMap()), s)
    }

    @Test
    fun `key names`() {
        assertEquals("manual_rate_USD", SettingsCodec.rateKey("USD"))
        assertEquals("manual_home_USD", SettingsCodec.homeKey("USD"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.core.SettingsCodecTest'`
Expected: FAIL, `Unresolved reference 'Settings'`.

- [ ] **Step 3: Implement `core/Settings.kt`**

```kotlin
package com.ilankog.coinwallet.core

data class Settings(val homeCurrency: String, val manualRates: Map<String, ManualRate>)

/** Maps raw SharedPreferences values to [Settings]; invalid entries are ignored. */
object SettingsCodec {
    const val KEY_HOME = "home_currency"
    private const val PREFIX_RATE = "manual_rate_"
    private const val PREFIX_HOME = "manual_home_"

    fun rateKey(code: String): String = PREFIX_RATE + code
    fun homeKey(code: String): String = PREFIX_HOME + code

    fun decode(values: Map<String, *>, defaultHome: String): Settings {
        val home = (values[KEY_HOME] as? String)?.takeIf { Currencies.get(it) != null } ?: defaultHome
        val manual = values.keys
            .filter { it.startsWith(PREFIX_RATE) }
            .mapNotNull { key ->
                val code = key.removePrefix(PREFIX_RATE)
                val rate = (values[key] as? String)?.let(Money::parseRate)
                val rateHome = (values[homeKey(code)] as? String)?.takeIf { Currencies.get(it) != null }
                if (Currencies.get(code) != null && rate != null && rateHome != null) code to ManualRate(rate, rateHome) else null
            }
            .toMap()
        return Settings(home, manual)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Implement glue: `data/SettingsStore.kt`, `data/AppContainer.kt`, `CoinWalletApp.kt`, manifest, `FakeSettings.kt`**

`data/SettingsStore.kt`:
```kotlin
package com.ilankog.coinwallet.data

import android.content.SharedPreferences
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.core.SettingsCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

interface SettingsSource {
    val settings: StateFlow<Settings>
    fun setHomeCurrency(code: String)
    fun setManualRate(currency: String, rate: BigDecimal, home: String)
    fun clearManualRate(currency: String)
}

class SettingsStore(private val prefs: SharedPreferences, private val defaultHome: String) : SettingsSource {
    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<Settings> = _settings.asStateFlow()

    // Held as a field on purpose: SharedPreferences keeps listeners only weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _settings.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(): Settings = SettingsCodec.decode(prefs.all, defaultHome)

    override fun setHomeCurrency(code: String) {
        prefs.edit().putString(SettingsCodec.KEY_HOME, code).apply()
    }

    override fun setManualRate(currency: String, rate: BigDecimal, home: String) {
        prefs.edit()
            .putString(SettingsCodec.rateKey(currency), rate.toPlainString())
            .putString(SettingsCodec.homeKey(currency), home)
            .apply()
    }

    override fun clearManualRate(currency: String) {
        prefs.edit().remove(SettingsCodec.rateKey(currency)).remove(SettingsCodec.homeKey(currency)).apply()
    }
}
```

`data/AppContainer.kt`:
```kotlin
package com.ilankog.coinwallet.data

import android.content.Context
import com.ilankog.coinwallet.core.Currencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale

/** Manual DI: one instance per process, owned by CoinWalletApp. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val trips = TripRepository(context.filesDir)
    val rates = RateRepository(context.filesDir, UrlConnectionFetcher(), appScope)
    val settings: SettingsSource = SettingsStore(
        context.getSharedPreferences("settings", Context.MODE_PRIVATE),
        Currencies.defaultHome(Locale.getDefault()),
    )
}
```

`CoinWalletApp.kt`:
```kotlin
package com.ilankog.coinwallet

import android.app.Application
import com.ilankog.coinwallet.data.AppContainer

class CoinWalletApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
```

Manifest: change the `<application` opening tag to:
```xml
    <application
        android:name=".CoinWalletApp"
        android:allowBackup="true"
```
(keep all other attributes unchanged).

`app/src/test/java/com/ilankog/coinwallet/data/FakeSettings.kt`:
```kotlin
package com.ilankog.coinwallet.data

import com.ilankog.coinwallet.core.ManualRate
import com.ilankog.coinwallet.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.math.BigDecimal

class FakeSettings(home: String = "ILS") : SettingsSource {
    private val state = MutableStateFlow(Settings(home, emptyMap()))
    override val settings: StateFlow<Settings> = state

    override fun setHomeCurrency(code: String) {
        state.value = state.value.copy(homeCurrency = code)
    }

    override fun setManualRate(currency: String, rate: BigDecimal, home: String) {
        state.value = state.value.copy(manualRates = state.value.manualRates + (currency to ManualRate(rate, home)))
    }

    override fun clearManualRate(currency: String) {
        state.value = state.value.copy(manualRates = state.value.manualRates - currency)
    }
}
```

- [ ] **Step 6: Verify build and all tests**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q assembleDebug testDebugUnitTest`
Expected: exit 0, no failures.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/core/Settings.kt app/src/main/java/com/ilankog/coinwallet/data/SettingsStore.kt app/src/main/java/com/ilankog/coinwallet/data/AppContainer.kt app/src/main/java/com/ilankog/coinwallet/CoinWalletApp.kt app/src/main/AndroidManifest.xml app/src/test/java/com/ilankog/coinwallet/data/FakeSettings.kt app/src/test/java/com/ilankog/coinwallet/core/SettingsCodecTest.kt
git commit -m "feat(data): add settings store with live listener and app container

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---
### Task 10: Theme, shared UI pieces, snapshot harness

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/theme/Color.kt`, `ui/theme/Theme.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/common/Messages.kt`, `ui/common/RateStatus.kt`
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/components/DenomIcon.kt`, `CoinStackArt.kt`, `DialogCard.kt`, `CurrencyPicker.kt`, `RateChip.kt`
- Create: `app/src/test/java/com/ilankog/coinwallet/ui/SnapshotHelpers.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/ui/common/RateStatusTest.kt`, `app/src/test/java/com/ilankog/coinwallet/ui/components/ComponentsSnapshotTest.kt`

(All main paths below are under `app/src/main/java/com/ilankog/coinwallet/`.)

**Interfaces:**
- Consumes: `Currencies`, `CurrencySearch`, `DenomTier`, `Denomination`, `DenomKind`, `CurrencyInfo` (Task 2), `RelativeTime` (Task 3), `RateSnapshot` (Task 5), `RepoNotice` (Task 7), `RefreshResult` (Task 8).
- Produces:
  - `@Composable fun CoinWalletTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`; `data class Metal(face, shine, rim, ink)`; `Copper`, `Brass`, `Silver`, `NotePaper: List<Color>`, `NoteInk`
  - `sealed interface UiMessage { data object Offline; data class ServerError(val code: Int); data object BadData }`; `fun RefreshResult.toMessage(): UiMessage?`; `fun UiMessage.text(): String`; `fun RefreshResult.describe(): String`; `fun RepoNotice.text(): String`
  - `sealed interface RateStatus { data object Updating; data object None; data class Fresh(val fetchedAt: Long); data class Offline(val fetchedAt: Long) }`; `fun rateStatus(snapshot: RateSnapshot?, busy: Boolean, last: RefreshResult?): RateStatus`; `fun RateStatus.label(now: Long): String`
  - `@Composable fun DenomIcon(denom: Denomination, info: CurrencyInfo, modifier: Modifier = Modifier)`
  - `@Composable fun CoinStackArt(modifier: Modifier = Modifier)`
  - `@Composable fun DialogCard(title: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, confirmEnabled: Boolean = true, destructive: Boolean = false, dismissLabel: String = "Cancel", content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun AppDialog(onDismiss: () -> Unit, content: @Composable () -> Unit)`
  - `@Composable fun CodeBadge(code: String, modifier: Modifier = Modifier)`
  - `@Composable fun CurrencyPickerContent(query: String, onQueryChange: (String) -> Unit, selected: String?, onPick: (CurrencyInfo) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun CurrencyPickerDialogContent(title: String, query: String, onQueryChange: (String) -> Unit, selected: String?, onPick: (CurrencyInfo) -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun RateChip(status: RateStatus, now: Long, modifier: Modifier = Modifier)`
  - test helpers: `fun coinPaparazzi(): Paparazzi`, `fun Paparazzi.snapBoth(content: @Composable () -> Unit)` (writes `_light` and `_dark` PNGs), `@Composable fun DialogFrame(content)`, `@Composable fun SheetFrame(content)`

- [ ] **Step 1: Write the failing pure test `ui/common/RateStatusTest.kt`**

```kotlin
package com.ilankog.coinwallet.ui.common

import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.RepoNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateStatusTest {
    private val snap = RateSnapshot(1000L, "2026-11-06", mapOf("EUR" to 1.0))

    @Test
    fun `status derivation`() {
        assertEquals(RateStatus.Updating, rateStatus(snap, busy = true, last = null))
        assertEquals(RateStatus.None, rateStatus(null, busy = false, last = RefreshResult.Offline))
        assertEquals(RateStatus.Fresh(1000L), rateStatus(snap, busy = false, last = null))
        assertEquals(RateStatus.Fresh(1000L), rateStatus(snap, busy = false, last = RefreshResult.Success))
        assertEquals(RateStatus.Offline(1000L), rateStatus(snap, busy = false, last = RefreshResult.HttpError(500)))
    }

    @Test
    fun `labels`() {
        val now = 1000L + 2 * 3_600_000L
        assertEquals("Updating rates…", RateStatus.Updating.label(now))
        assertEquals("No rates yet", RateStatus.None.label(now))
        assertEquals("Rates updated 2h ago", RateStatus.Fresh(1000L).label(now))
        assertEquals("Offline · rates from 2h ago", RateStatus.Offline(1000L).label(now))
    }

    @Test
    fun `messages`() {
        assertNull(RefreshResult.Success.toMessage())
        assertEquals("You're offline. Using saved rates.", RefreshResult.Offline.describe())
        assertEquals("Rate server error (503)", RefreshResult.HttpError(503).describe())
        assertEquals("Rate data was invalid", RefreshResult.BadData.describe())
        assertEquals("Rates updated", RefreshResult.Success.describe())
        assertEquals("Saved trips were damaged and were reset", RepoNotice.TripsReset.text())
        assertEquals("Couldn't save. Changes may be lost.", RepoNotice.SaveFailed.text())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.ui.common.RateStatusTest'`
Expected: FAIL, `Unresolved reference 'RateStatus'`.

- [ ] **Step 3: Implement `ui/common/Messages.kt` and `ui/common/RateStatus.kt`**

`ui/common/Messages.kt`:
```kotlin
package com.ilankog.coinwallet.ui.common

import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.RepoNotice

sealed interface UiMessage {
    data object Offline : UiMessage
    data class ServerError(val code: Int) : UiMessage
    data object BadData : UiMessage
}

fun RefreshResult.toMessage(): UiMessage? = when (this) {
    RefreshResult.Success -> null
    RefreshResult.Offline -> UiMessage.Offline
    is RefreshResult.HttpError -> UiMessage.ServerError(code)
    RefreshResult.BadData -> UiMessage.BadData
}

fun UiMessage.text(): String = when (this) {
    UiMessage.Offline -> "You're offline. Using saved rates."
    is UiMessage.ServerError -> "Rate server error ($code)"
    UiMessage.BadData -> "Rate data was invalid"
}

fun RefreshResult.describe(): String = toMessage()?.text() ?: "Rates updated"

fun RepoNotice.text(): String = when (this) {
    RepoNotice.TripsReset -> "Saved trips were damaged and were reset"
    RepoNotice.SaveFailed -> "Couldn't save. Changes may be lost."
    is RepoNotice.Deleted -> "Trip deleted"
}
```

`ui/common/RateStatus.kt`:
```kotlin
package com.ilankog.coinwallet.ui.common

import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.RelativeTime
import com.ilankog.coinwallet.data.RefreshResult

sealed interface RateStatus {
    data object Updating : RateStatus
    data object None : RateStatus
    data class Fresh(val fetchedAt: Long) : RateStatus
    data class Offline(val fetchedAt: Long) : RateStatus
}

fun rateStatus(snapshot: RateSnapshot?, busy: Boolean, last: RefreshResult?): RateStatus = when {
    busy -> RateStatus.Updating
    snapshot == null -> RateStatus.None
    last != null && last != RefreshResult.Success -> RateStatus.Offline(snapshot.fetchedAt)
    else -> RateStatus.Fresh(snapshot.fetchedAt)
}

fun RateStatus.label(now: Long): String = when (this) {
    RateStatus.Updating -> "Updating rates…"
    RateStatus.None -> "No rates yet"
    is RateStatus.Fresh -> "Rates updated ${RelativeTime.ago(fetchedAt, now)}"
    is RateStatus.Offline -> "Offline · rates from ${RelativeTime.ago(fetchedAt, now)}"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Implement theme**

`ui/theme/Color.kt`:
```kotlin
package com.ilankog.coinwallet.ui.theme

import androidx.compose.ui.graphics.Color

/** Original coin metal palette used by the denomination art. */
data class Metal(val face: Color, val shine: Color, val rim: Color, val ink: Color)

val Copper = Metal(Color(0xFFC27A4A), Color(0xFFE9A97C), Color(0xFF8A4A22), Color(0xFF3D1E0A))
val Brass = Metal(Color(0xFFD9B45A), Color(0xFFF3DC95), Color(0xFF9C7A26), Color(0xFF3A2C05))
val Silver = Metal(Color(0xFFC9CCD1), Color(0xFFF1F2F4), Color(0xFF8D9198), Color(0xFF2A2D31))
val NotePaper = listOf(Color(0xFF9FC3A5), Color(0xFFE3B98A), Color(0xFFA9B8D8))
val NoteInk = Color(0xFF2B1D12)
```

`ui/theme/Theme.kt`:
```kotlin
package com.ilankog.coinwallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4632F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DCC8),
    onPrimaryContainer = Color(0xFF3D1E0A),
    secondary = Color(0xFFB8902F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF3E3B8),
    onSecondaryContainer = Color(0xFF3A2C05),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF2B1D12),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF2B1D12),
    surfaceVariant = Color(0xFFEFE3D3),
    onSurfaceVariant = Color(0xFF5C4A3A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1E7),
    surfaceContainer = Color(0xFFF5EDE1),
    surfaceContainerHigh = Color(0xFFF0E6D8),
    surfaceContainerHighest = Color(0xFFEADFCF),
    outline = Color(0xFF9C8672),
    outlineVariant = Color(0xFFD9C8B4),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE89A63),
    onPrimary = Color(0xFF3D1E0A),
    primaryContainer = Color(0xFF6B3516),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Color(0xFFD9B45A),
    onSecondary = Color(0xFF3A2C05),
    secondaryContainer = Color(0xFF54420F),
    onSecondaryContainer = Color(0xFFF6E3B0),
    background = Color(0xFF1A1410),
    onBackground = Color(0xFFF3E9DC),
    surface = Color(0xFF1A1410),
    onSurface = Color(0xFFF3E9DC),
    surfaceVariant = Color(0xFF3A2F26),
    onSurfaceVariant = Color(0xFFD6C4B0),
    surfaceContainerLowest = Color(0xFF140F0C),
    surfaceContainerLow = Color(0xFF201913),
    surfaceContainer = Color(0xFF241C16),
    surfaceContainerHigh = Color(0xFF2E251E),
    surfaceContainerHighest = Color(0xFF392F27),
    outline = Color(0xFFA08C78),
    outlineVariant = Color(0xFF4F4136),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

private val BaseType = Typography()

private val CoinTypography = BaseType.copy(
    displaySmall = BaseType.displaySmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

private val CoinShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
)

@Composable
fun CoinWalletTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CoinTypography,
        shapes = CoinShapes,
        content = content,
    )
}
```

- [ ] **Step 6: Implement components**

`ui/components/DenomIcon.kt`:
```kotlin
package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.DenomTier
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.ui.theme.Brass
import com.ilankog.coinwallet.ui.theme.Copper
import com.ilankog.coinwallet.ui.theme.NoteInk
import com.ilankog.coinwallet.ui.theme.NotePaper
import com.ilankog.coinwallet.ui.theme.Silver

/** Original coin (round, metal tier) or note (rectangle, paper tier) art with the face value. */
@Composable
fun DenomIcon(denom: Denomination, info: CurrencyInfo, modifier: Modifier = Modifier) {
    val tier = DenomTier.of(denom, info)
    val text = DenomTier.faceText(denom, info)
    if (denom.kind == DenomKind.COIN) {
        val metal = listOf(Copper, Brass, Silver)[tier]
        Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(metal.shine, metal.face),
                        center = Offset(size.width * 0.35f, size.height * 0.3f),
                        radius = size.minDimension * 0.75f,
                    ),
                )
                drawCircle(metal.rim, radius = size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                drawCircle(metal.rim.copy(alpha = 0.45f), radius = size.minDimension / 2 - 6.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
            }
            Text(text, color = metal.ink, fontWeight = FontWeight.Bold, fontSize = faceSize(text, 15).sp, maxLines = 1)
        }
    } else {
        val paper = NotePaper[tier]
        Box(modifier.size(width = 56.dp, height = 34.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(paper, cornerRadius = CornerRadius(6.dp.toPx()))
                val inset = 3.dp.toPx()
                drawRoundRect(
                    Color.Black.copy(alpha = 0.18f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(Color.White.copy(alpha = 0.35f), radius = size.height * 0.3f, center = Offset(size.width * 0.8f, size.height / 2))
            }
            Text(text, color = NoteInk, fontWeight = FontWeight.Bold, fontSize = faceSize(text, 14).sp, maxLines = 1)
        }
    }
}

private fun faceSize(text: String, base: Int): Int = when {
    text.length <= 2 -> base
    text.length == 3 -> base - 2
    text.length == 4 -> base - 4
    else -> base - 5
}
```

`ui/components/CoinStackArt.kt`:
```kotlin
package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.ui.theme.Brass
import com.ilankog.coinwallet.ui.theme.Copper
import com.ilankog.coinwallet.ui.theme.Silver

/** Empty-state illustration: three stacked coins plus one loose coin. Original art. */
@Composable
fun CoinStackArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val coinW = w * 0.62f
        val coinH = h * 0.2f
        val edge = h * 0.09f
        val left = (w - coinW) / 2
        listOf(Copper to 0.66f, Brass to 0.47f, Silver to 0.28f).forEach { (m, top) ->
            val y = h * top
            drawRect(m.rim, topLeft = Offset(left, y + coinH / 2), size = Size(coinW, edge))
            drawOval(m.rim, topLeft = Offset(left, y + edge), size = Size(coinW, coinH))
            drawOval(
                Brush.linearGradient(listOf(m.shine, m.face), start = Offset(left, y), end = Offset(left + coinW, y + coinH)),
                topLeft = Offset(left, y),
                size = Size(coinW, coinH),
            )
            drawOval(
                m.rim.copy(alpha = 0.5f),
                topLeft = Offset(left + coinW * 0.12f, y + coinH * 0.18f),
                size = Size(coinW * 0.76f, coinH * 0.64f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        val c = Offset(w * 0.84f, h * 0.2f)
        drawCircle(Brush.radialGradient(listOf(Brass.shine, Brass.face), center = c, radius = w * 0.13f), radius = w * 0.11f, center = c)
        drawCircle(Brass.rim, radius = w * 0.11f, center = c, style = Stroke(width = 3.dp.toPx()))
    }
}
```

`ui/components/DialogCard.kt`:
```kotlin
package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Dialog body; rendered inside [AppDialog] in the app and directly in snapshot tests. */
@Composable
fun DialogCard(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissLabel: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            content()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) { Text(confirmLabel) }
            }
        }
    }
}

@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) { content() }
    }
}
```

`ui/components/CurrencyPicker.kt`:
```kotlin
package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.CurrencySearch

@Composable
fun CodeBadge(code: String, modifier: Modifier = Modifier) {
    Surface(modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                code,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Search field + filtered list. Give it a bounded max height via [modifier]. */
@Composable
fun CurrencyPickerContent(
    query: String,
    onQueryChange: (String) -> Unit,
    selected: String?,
    onPick: (CurrencyInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = remember(query) { CurrencySearch.filter(query) }
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search code or name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty()) {
            Text(
                "No currency matches \"${query.trim()}\"",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            items(results, key = { it.code }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    leadingContent = { CodeBadge(c.code) },
                    trailingContent = {
                        if (c.code == selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable { onPick(c) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun CurrencyPickerDialogContent(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    selected: String?,
    onPick: (CurrencyInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            CurrencyPickerContent(query, onQueryChange, selected, onPick, Modifier.heightIn(max = 480.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
```

`ui/components/RateChip.kt`:
```kotlin
package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.common.label

@Composable
fun RateChip(status: RateStatus, now: Long, modifier: Modifier = Modifier) {
    val icon = when (status) {
        RateStatus.Updating -> Icons.Default.Refresh
        RateStatus.None, is RateStatus.Offline -> Icons.Default.CloudOff
        is RateStatus.Fresh -> Icons.Default.Schedule
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(status.label(now), style = MaterialTheme.typography.labelMedium)
        }
    }
}
```

- [ ] **Step 7: Write snapshot harness `app/src/test/java/com/ilankog/coinwallet/ui/SnapshotHelpers.kt`**

```kotlin
package com.ilankog.coinwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.ilankog.coinwallet.ui.theme.CoinWalletTheme

fun coinPaparazzi() = Paparazzi(
    deviceConfig = DeviceConfig.PIXEL_5,
    theme = "android:Theme.Material.Light.NoActionBar",
    maxPercentDifference = 0.0,
)

/** Snapshots [content] in light and dark theme ("light" / "dark" suffix). */
fun Paparazzi.snapBoth(content: @Composable () -> Unit) {
    for (dark in listOf(false, true)) {
        snapshot(if (dark) "dark" else "light") {
            CoinWalletTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

/** Paparazzi cannot render Dialog windows; this mimics the scrim around a DialogCard. */
@Composable
fun DialogFrame(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Mimics a ModalBottomSheet around sheet content. */
@Composable
fun SheetFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(top = 24.dp)) { content() }
        }
    }
}
```

- [ ] **Step 8: Write snapshot test `ui/components/ComponentsSnapshotTest.kt`**

```kotlin
package com.ilankog.coinwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.ui.DialogFrame
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class ComponentsSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private val now = 1_762_430_400_000L

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun denominationIcons() = paparazzi.snapBoth {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (code in listOf("EUR", "CZK", "JPY", "IDR")) {
                val info = Currencies.orFallback(code)
                Text(code, style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    info.denominations.forEach { DenomIcon(it, info) }
                }
            }
        }
    }

    @Test
    fun artAndRateChips() = paparazzi.snapBoth {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CoinStackArt(Modifier.size(120.dp))
            RateChip(RateStatus.Fresh(now - 2 * 3_600_000L), now)
            RateChip(RateStatus.Offline(now - 3 * 86_400_000L), now)
            RateChip(RateStatus.Updating, now)
            RateChip(RateStatus.None, now)
        }
    }

    @Test
    fun confirmDialog() = paparazzi.snapBoth {
        DialogFrame {
            DialogCard(title = "Delete Prague weekend?", confirmLabel = "Delete", onConfirm = {}, onDismiss = {}, destructive = true) {
                Text("You can undo right after.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    @Test
    fun currencyPicker() = paparazzi.snapBoth {
        DialogFrame { CurrencyPickerDialogContent("Home currency", "", {}, "ILS", {}, {}) }
    }

    @Test
    fun currencyPickerNoMatch() = paparazzi.snapBoth {
        DialogFrame { CurrencyPickerDialogContent("Home currency", "zzz", {}, "ILS", {}, {}) }
    }
}
```

- [ ] **Step 9: Record snapshots and run all tests**

Run:
```bash
cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk
gradle --no-daemon -q :app:recordPaparazziDebug
gradle --no-daemon -q :app:testDebugUnitTest
ls app/src/test/snapshots/images/ | grep ComponentsSnapshotTest
```
Expected: exit 0; 10 PNGs listed (`..._light.png` / `..._dark.png` for 5 tests). Open each PNG (Read tool) and check nothing is cut off, overlapping or unreadable in either theme; fix and re-record if so.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/ui/theme app/src/main/java/com/ilankog/coinwallet/ui/common app/src/main/java/com/ilankog/coinwallet/ui/components app/src/test/java/com/ilankog/coinwallet/ui/SnapshotHelpers.kt app/src/test/java/com/ilankog/coinwallet/ui/common/RateStatusTest.kt app/src/test/java/com/ilankog/coinwallet/ui/components/ComponentsSnapshotTest.kt app/src/test/snapshots/images
git commit -m "feat(ui): add copper theme, coin art and shared components

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---
### Task 11: Trips screen, trip sheet, TripsViewModel

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/trips/TripsViewModel.kt`, `TripsScreen.kt`, `TripSheet.kt`, `TripsRoute.kt`
- Create: `app/src/test/java/com/ilankog/coinwallet/MainDispatcherRule.kt`, `app/src/test/java/com/ilankog/coinwallet/ui/SampleData.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/ui/trips/TripsViewModelTest.kt`, `TripsScreenSnapshotTest.kt`, `TripSheetSnapshotTest.kt`

**Interfaces:**
- Consumes: `TripRepository` (7), `RateRepository`, `RefreshResult`, `HttpResponse`, `FakeFetcher` (8), `SettingsSource`, `FakeSettings` (9), `Totals`, `WalletTotals`, `TripSummary` (6), `Staleness` (5), `TripRules` (4), `Currencies`, `CurrencyInfo` (2), all Task 10 UI pieces.
- Produces:
  - `data class TripsUiState(val loaded: Boolean, val home: CurrencyInfo, val totals: WalletTotals, val rateStatus: RateStatus, val now: Long)`
  - `class TripsViewModel(trips: TripRepository, rates: RateRepository, settings: SettingsSource, clock: () -> Long = System::currentTimeMillis) : ViewModel` with `val state: StateFlow<TripsUiState>`, `val message: StateFlow<UiMessage?>`, `fun refreshIfStale()`, `fun refresh()`, `fun messageShown()`, `fun createTrip(name: String, currency: String)`
  - `@Composable fun TripsScreen(state: TripsUiState, snackbarHostState: SnackbarHostState, onRefresh: () -> Unit, onNewTrip: () -> Unit, onOpenTrip: (String) -> Unit, onOpenSettings: () -> Unit)`
  - `fun tripNameError(name: String): String?`
  - `@Composable fun TripSheetContent(title: String, name: String, nameError: String?, currency: CurrencyInfo?, currencyLocked: Boolean, picking: Boolean, query: String, onNameChange: (String) -> Unit, onPickCurrency: () -> Unit, onQueryChange: (String) -> Unit, onCurrencyPicked: (CurrencyInfo) -> Unit, onClosePicker: () -> Unit, onSave: () -> Unit, onCancel: () -> Unit)`
  - `@Composable fun TripSheet(title: String, initialName: String, initialCurrency: String?, currencyLocked: Boolean, onSave: (name: String, currency: String) -> Unit, onDismiss: () -> Unit)` (ModalBottomSheet wrapper, reused by Wallet "Edit trip")
  - `@Composable fun TripsRoute(vm: TripsViewModel, snackbarHostState: SnackbarHostState, onOpenTrip: (String) -> Unit, onOpenSettings: () -> Unit)`
  - test helpers `MainDispatcherRule`, `SampleData` (NOW, ils, snapshot, prague, tokyo, newYork, bangkok, manual, `tripsState(...)`)

- [ ] **Step 1: Write test helpers**

`app/src/test/java/com/ilankog/coinwallet/MainDispatcherRule.kt`:
```kotlin
package com.ilankog.coinwallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

`app/src/test/java/com/ilankog/coinwallet/ui/SampleData.kt`:
```kotlin
package com.ilankog.coinwallet.ui

import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.ManualRate
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.trips.TripsUiState
import java.math.BigDecimal

object SampleData {
    const val NOW = 1_762_430_400_000L
    val ils = Currencies.orFallback("ILS")
    val snapshot = RateSnapshot(
        fetchedAt = NOW - 2 * 3_600_000L,
        date = "2026-11-06",
        rates = mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77, "USD" to 1.1165),
    )
    val prague = Trip("t1", "Prague weekend", "CZK", mapOf("c100" to 3, "c1000" to 2, "c5000" to 1, "n20000" to 2), createdAt = 4)
    val tokyo = Trip("t2", "Tokyo", "JPY", mapOf("c1" to 7, "c100" to 4, "c500" to 2, "n1000" to 3), createdAt = 3)
    val newYork = Trip("t3", "New York", "USD", mapOf("c25" to 6, "n2000" to 1), createdAt = 2)
    val bangkok = Trip("t4", "Bangkok", "THB", mapOf("n10000" to 1), createdAt = 1)
    val allTrips = listOf(prague, tokyo, newYork, bangkok)
    val manual = mapOf("USD" to ManualRate(BigDecimal("3.6"), "ILS"))

    fun tripsState(
        status: RateStatus = RateStatus.Fresh(snapshot.fetchedAt),
        trips: List<Trip> = allTrips,
    ) = TripsUiState(
        loaded = true,
        home = ils,
        totals = Totals.all(trips, ils, manual, snapshot),
        rateStatus = status,
        now = NOW,
    )
}
```

- [ ] **Step 2: Write the failing ViewModel test**

`app/src/test/java/com/ilankog/coinwallet/ui/trips/TripsViewModelTest.kt`:
```kotlin
package com.ilankog.coinwallet.ui.trips

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.MainDispatcherRule
import com.ilankog.coinwallet.core.Staleness
import com.ilankog.coinwallet.data.FakeFetcher
import com.ilankog.coinwallet.data.FakeSettings
import com.ilankog.coinwallet.data.HttpResponse
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.TripRepository
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.common.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TripsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val body = Fixtures.text("frankfurter_latest.json")
    private var now = 1_000_000_000L

    private class Env(val vm: TripsViewModel, val trips: TripRepository, val fetcher: FakeFetcher, val settings: FakeSettings)

    private fun TestScope.env(
        fetcher: FakeFetcher = FakeFetcher { HttpResponse(200, body) },
        settings: FakeSettings = FakeSettings("ILS"),
    ): Env {
        val io = UnconfinedTestDispatcher(testScheduler)
        val trips = TripRepository(folder.root, io, clock = { now })
        val rates = RateRepository(folder.root, fetcher, CoroutineScope(io), io, clock = { now })
        return Env(TripsViewModel(trips, rates, settings, clock = { now }), trips, fetcher, settings)
    }

    @Test
    fun `starts loaded and empty with no rates`() = runTest {
        val e = env(FakeFetcher { throw IOException("offline") })
        val s = e.vm.state.value
        assertTrue(s.loaded)
        assertTrue(s.totals.trips.isEmpty())
        assertEquals(RateStatus.None, s.rateStatus)
        assertEquals("ILS", s.home.code)
    }

    @Test
    fun `refreshIfStale fetches only when stale`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        assertEquals(1, e.fetcher.calls)
        assertEquals(RateStatus.Fresh(now), e.vm.state.value.rateStatus)
        e.vm.refreshIfStale()
        assertEquals(1, e.fetcher.calls)
        now += Staleness.MAX_AGE_MS + 1
        e.vm.refreshIfStale()
        assertEquals(2, e.fetcher.calls)
    }

    @Test
    fun `auto refresh failure shows offline status without a message`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        val fetchedAt = now
        e.fetcher.response = { throw IOException("offline") }
        now += Staleness.MAX_AGE_MS + 1
        e.vm.refreshIfStale()
        assertEquals(RateStatus.Offline(fetchedAt), e.vm.state.value.rateStatus)
        assertNull(e.vm.message.value)
    }

    @Test
    fun `manual refresh failure posts a message`() = runTest {
        val e = env(FakeFetcher { HttpResponse(503, "") })
        e.vm.refresh()
        assertEquals(UiMessage.ServerError(503), e.vm.message.value)
        e.vm.messageShown()
        assertNull(e.vm.message.value)
    }

    @Test
    fun `createTrip validates input and totals convert`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        e.vm.createTrip("   ", "CZK")
        e.vm.createTrip("Prague", "XAU")
        assertTrue(e.vm.state.value.totals.trips.isEmpty())
        e.vm.createTrip(" Prague ", "CZK")
        val t = e.trips.trips.value.single()
        assertEquals("Prague", t.name)
        e.trips.update(t.id) { it.copy(counts = mapOf("n10000" to 1)) }
        assertEquals(1467L, e.vm.state.value.totals.trips.single().homeMinor)
        assertEquals(1467L, e.vm.state.value.totals.grandHomeMinor)
    }

    @Test
    fun `home currency change recomputes live`() = runTest {
        val e = env()
        e.vm.refreshIfStale()
        e.vm.createTrip("Prague", "CZK")
        val t = e.trips.trips.value.single()
        e.trips.update(t.id) { it.copy(counts = mapOf("n10000" to 1)) }
        e.settings.setHomeCurrency("EUR")
        assertEquals("EUR", e.vm.state.value.home.code)
        assertEquals(411L, e.vm.state.value.totals.grandHomeMinor)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.ui.trips.TripsViewModelTest'`
Expected: FAIL, `Unresolved reference 'TripsViewModel'` (SampleData also fails to compile until TripsUiState exists).

- [ ] **Step 4: Implement `ui/trips/TripsViewModel.kt`**

```kotlin
package com.ilankog.coinwallet.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.core.Staleness
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.WalletTotals
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.SettingsSource
import com.ilankog.coinwallet.data.TripRepository
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.common.UiMessage
import com.ilankog.coinwallet.ui.common.rateStatus
import com.ilankog.coinwallet.ui.common.toMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripsUiState(
    val loaded: Boolean,
    val home: CurrencyInfo,
    val totals: WalletTotals,
    val rateStatus: RateStatus,
    val now: Long,
)

class TripsViewModel(
    private val trips: TripRepository,
    private val rates: RateRepository,
    private val settings: SettingsSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val rateFlow = combine(rates.snapshot, rates.busy, rates.lastResult) { s, b, l -> Triple(s, b, l) }

    val state: StateFlow<TripsUiState> =
        combine(trips.trips, trips.isLoaded, rateFlow, settings.settings) { list, loaded, (snap, busy, last), s ->
            build(list, loaded, snap, busy, last, s)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(trips.trips.value, trips.isLoaded.value, rates.snapshot.value, rates.busy.value, rates.lastResult.value, settings.settings.value),
        )

    init {
        viewModelScope.launch { trips.load() }
    }

    /** Called on every screen start; errors only show in the rate chip. */
    fun refreshIfStale() {
        viewModelScope.launch {
            rates.load()
            if (Staleness.isStale(rates.snapshot.value, clock())) rates.refresh()
        }
    }

    /** Pull-to-refresh: failures also show a snackbar. */
    fun refresh() {
        viewModelScope.launch { _message.value = rates.refresh().toMessage() }
    }

    fun messageShown() {
        _message.value = null
    }

    fun createTrip(name: String, currency: String) {
        val clean = TripRules.cleanName(name) ?: return
        if (Currencies.get(currency) == null) return
        viewModelScope.launch { trips.create(clean, currency) }
    }

    private fun build(
        list: List<Trip>, loaded: Boolean, snap: RateSnapshot?, busy: Boolean, last: RefreshResult?, s: Settings,
    ): TripsUiState {
        val home = Currencies.orFallback(s.homeCurrency)
        // ponytail: "now" refreshes on any state change, not on a timer; fine for "2h ago" granularity.
        return TripsUiState(loaded, home, Totals.all(list, home, s.manualRates, snap), rateStatus(snap, busy, last), clock())
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: same as Step 3. Expected: PASS.

- [ ] **Step 6: Implement `ui/trips/TripsScreen.kt`**

```kotlin
package com.ilankog.coinwallet.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.components.CodeBadge
import com.ilankog.coinwallet.ui.components.CoinStackArt
import com.ilankog.coinwallet.ui.components.RateChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    state: TripsUiState,
    snackbarHostState: SnackbarHostState,
    onRefresh: () -> Unit,
    onNewTrip: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coin Wallet") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewTrip,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New trip") },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.rateStatus is RateStatus.Updating,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { TotalHeader(state) }
                if (state.loaded && state.totals.trips.isEmpty()) {
                    item(key = "empty") { EmptyTrips() }
                }
                items(state.totals.trips, key = { it.trip.id }) { s ->
                    TripCard(s, state.home, onClick = { onOpenTrip(s.trip.id) })
                }
            }
        }
    }
}

internal fun missingHint(n: Int): String =
    if (n == 1) "1 trip without rate not included" else "$n trips without rate not included"

@Composable
private fun TotalHeader(state: TripsUiState) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("All trips in ${state.home.code}", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(state.totals.grandHomeMinor, state.home),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.totals.missingRateCount > 0) {
                Text(missingHint(state.totals.missingRateCount), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            RateChip(state.rateStatus, state.now)
        }
    }
}

@Composable
private fun TripCard(s: TripSummary, home: CurrencyInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CodeBadge(s.info.code)
            Column(Modifier.weight(1f)) {
                Text(s.trip.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Money.format(s.totalMinor, s.info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val homeMinor = s.homeMinor
                if (homeMinor != null) {
                    Text(Money.format(homeMinor, home), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                } else {
                    Text("No rate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                }
                if (s.rate?.source == RateSource.MANUAL) {
                    Text("manual rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun EmptyTrips() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CoinStackArt(Modifier.size(120.dp))
        Text("No trips yet", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tap New trip to start counting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 7: Implement `ui/trips/TripSheet.kt`**

```kotlin
package com.ilankog.coinwallet.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.ui.components.CodeBadge
import com.ilankog.coinwallet.ui.components.CurrencyPickerContent

fun tripNameError(name: String): String? {
    val t = name.trim()
    return when {
        t.isEmpty() -> "Name is required"
        t.length > TripRules.MAX_NAME -> "Max ${TripRules.MAX_NAME} characters"
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSheet(
    title: String,
    initialName: String,
    initialCurrency: String?,
    currencyLocked: Boolean,
    onSave: (name: String, currency: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        TripSheetContent(
            title = title,
            name = name,
            nameError = if (touched) tripNameError(name) else null,
            currency = currency?.let(Currencies::get),
            currencyLocked = currencyLocked,
            picking = picking,
            query = query,
            onNameChange = { name = it; touched = true },
            onPickCurrency = { picking = true },
            onQueryChange = { query = it },
            onCurrencyPicked = { currency = it.code; picking = false; query = "" },
            onClosePicker = { picking = false },
            onSave = {
                val clean = TripRules.cleanName(name)
                val code = currency
                if (clean != null && code != null) onSave(clean, code) else touched = true
            },
            onCancel = onDismiss,
        )
    }
}

@Composable
fun TripSheetContent(
    title: String,
    name: String,
    nameError: String?,
    currency: CurrencyInfo?,
    currencyLocked: Boolean,
    picking: Boolean,
    query: String,
    onNameChange: (String) -> Unit,
    onPickCurrency: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCurrencyPicked: (CurrencyInfo) -> Unit,
    onClosePicker: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
        if (picking) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClosePicker) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Choose currency", style = MaterialTheme.typography.titleLarge)
            }
            CurrencyPickerContent(query, onQueryChange, currency?.code, onCurrencyPicked, Modifier.heightIn(max = 520.dp))
        } else {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Trip name") },
                    isError = nameError != null,
                    supportingText = { Text(nameError ?: "${name.trim().length}/${TripRules.MAX_NAME}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CurrencyField(currency, currencyLocked, onPickCurrency)
                if (currencyLocked) {
                    Text(
                        "Reset counts to change currency",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSave, enabled = TripRules.cleanName(name) != null && currency != null) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun CurrencyField(currency: CurrencyInfo?, locked: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (currency != null) {
                CodeBadge(currency.code)
                Column(Modifier.weight(1f)) {
                    Text("Currency", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Text("Choose currency", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            if (!locked) Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
}
```

- [ ] **Step 8: Implement `ui/trips/TripsRoute.kt`**

```kotlin
package com.ilankog.coinwallet.ui.trips

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.ui.common.text

@Composable
fun TripsRoute(
    vm: TripsViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenTrip: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(vm) {
        vm.refreshIfStale()
        onStopOrDispose { }
    }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.messageShown()
        snackbarHostState.showSnackbar(m.text())
    }

    TripsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onRefresh = vm::refresh,
        onNewTrip = { showSheet = true },
        onOpenTrip = onOpenTrip,
        onOpenSettings = onOpenSettings,
    )
    if (showSheet) {
        TripSheet(
            title = "New trip",
            initialName = "",
            initialCurrency = null,
            currencyLocked = false,
            onSave = { name, code -> vm.createTrip(name, code); showSheet = false },
            onDismiss = { showSheet = false },
        )
    }
}
```

- [ ] **Step 9: Write snapshot tests**

`app/src/test/java/com/ilankog/coinwallet/ui/trips/TripsScreenSnapshotTest.kt`:
```kotlin
package com.ilankog.coinwallet.ui.trips

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class TripsScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(state: TripsUiState) = paparazzi.snapBoth {
        TripsScreen(state, remember { SnackbarHostState() }, {}, {}, {}, {})
    }

    @Test
    fun filled() = snap(SampleData.tripsState())

    @Test
    fun empty() = snap(SampleData.tripsState(status = RateStatus.None, trips = emptyList()))

    @Test
    fun updating() = snap(SampleData.tripsState(status = RateStatus.Updating))

    @Test
    fun offline() = snap(SampleData.tripsState(status = RateStatus.Offline(SampleData.NOW - 3 * 86_400_000L)))
}
```

`app/src/test/java/com/ilankog/coinwallet/ui/trips/TripSheetSnapshotTest.kt`:
```kotlin
package com.ilankog.coinwallet.ui.trips

import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.ui.SheetFrame
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class TripSheetSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(
        title: String = "New trip",
        name: String = "",
        nameError: String? = null,
        currency: CurrencyInfo? = null,
        locked: Boolean = false,
        picking: Boolean = false,
        query: String = "",
    ) = paparazzi.snapBoth {
        SheetFrame {
            TripSheetContent(
                title, name, nameError, currency, locked, picking, query,
                onNameChange = {}, onPickCurrency = {}, onQueryChange = {}, onCurrencyPicked = {},
                onClosePicker = {}, onSave = {}, onCancel = {},
            )
        }
    }

    @Test
    fun createEmpty() = snap()

    @Test
    fun invalidName() = snap(name = "   ", nameError = tripNameError("   "))

    @Test
    fun filled() = snap(name = "Prague weekend", currency = Currencies.get("CZK"))

    @Test
    fun editLockedCurrency() = snap(title = "Edit trip", name = "Prague weekend", currency = Currencies.get("CZK"), locked = true)

    @Test
    fun currencyPicker() = snap(picking = true, query = "kr", currency = Currencies.get("SEK"))
}
```

- [ ] **Step 10: Record, run all tests, inspect PNGs**

Run:
```bash
cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk
gradle --no-daemon -q :app:recordPaparazziDebug
gradle --no-daemon -q :app:testDebugUnitTest
ls app/src/test/snapshots/images/ | grep -E 'TripsScreenSnapshotTest|TripSheetSnapshotTest' | wc -l
```
Expected: exit 0; `18`. Open every new PNG (Read tool); fix anything cut off, overlapping, unreadable, or misaligned in either theme, then re-record.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/ui/trips app/src/test/java/com/ilankog/coinwallet/MainDispatcherRule.kt app/src/test/java/com/ilankog/coinwallet/ui/SampleData.kt app/src/test/java/com/ilankog/coinwallet/ui/trips app/src/test/snapshots/images
git commit -m "feat(ui): add trips list, trip sheet and trips view model

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 12: Wallet screen, dialogs, WalletViewModel

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/wallet/WalletViewModel.kt`, `WalletScreen.kt`, `WalletDialogs.kt`, `WalletRoute.kt`
- Modify: `app/src/test/java/com/ilankog/coinwallet/ui/SampleData.kt` (add `walletState` and `noActions`, shown in Step 8)
- Test: `app/src/test/java/com/ilankog/coinwallet/ui/wallet/WalletViewModelTest.kt`, `WalletScreenSnapshotTest.kt`

**Interfaces:**
- Consumes: everything Task 11 consumes, plus `TripSheet` (Task 11), `RepoNotice` (Task 7), `Money.parseRate/formatRate/label` (Task 3), `ResolvedRate`, `RateSource` (Task 5).
- Produces:
  - `data class WalletUiState(val summary: TripSummary, val home: CurrencyInfo, val rateDate: String?, val hasManualRate: Boolean)`
  - `sealed interface WalletState { data object Loading; data object NotFound; data class Ready(val ui: WalletUiState) }`
  - `class WalletViewModel(tripId: String, trips: TripRepository, rates: RateRepository, settings: SettingsSource) : ViewModel` with `val state: StateFlow<WalletState>`, `fun increment(d: Denomination)`, `fun decrement(d: Denomination)`, `fun setCount(d: Denomination, value: Int)`, `fun reset()`, `fun delete()`, `fun setManualRate(rate: BigDecimal)`, `fun clearManualRate()`, `fun editTrip(name: String, currency: String)`
  - `data class WalletActions(onBack, onIncrement: (Denomination) -> Unit, onDecrement: (Denomination) -> Unit, onCountClick: (Denomination) -> Unit, onEdit, onSetRate, onClearRate, onReset, onDelete)` (other fields `() -> Unit`)
  - `@Composable fun WalletScreen(state: WalletUiState, snackbarHostState: SnackbarHostState, actions: WalletActions)`
  - `fun rateLine(state: WalletUiState): String?`
  - `const val COUNT_ERROR`, `const val RATE_ERROR`; `@Composable fun CountDialogContent(label: String, text: String, error: String?, onTextChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit)`; `@Composable fun RateDialogContent(from: String, home: String, text: String, onTextChange: (String) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit)`; `@Composable fun ConfirmDialogContent(title: String, body: String, confirmLabel: String, destructive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun WalletRoute(vm: WalletViewModel, snackbarHostState: SnackbarHostState, onBack: () -> Unit)`

- [ ] **Step 1: Write the failing ViewModel test**

`app/src/test/java/com/ilankog/coinwallet/ui/wallet/WalletViewModelTest.kt`:
```kotlin
package com.ilankog.coinwallet.ui.wallet

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.MainDispatcherRule
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.data.FakeFetcher
import com.ilankog.coinwallet.data.FakeSettings
import com.ilankog.coinwallet.data.HttpResponse
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RepoNotice
import com.ilankog.coinwallet.data.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val body = Fixtures.text("frankfurter_latest.json")
    private val tenKc = Denomination(DenomKind.COIN, 1000)
    private val hundredKc = Denomination(DenomKind.NOTE, 10000)

    private class Env(val vm: WalletViewModel, val trips: TripRepository)

    private suspend fun TestScope.env(settings: FakeSettings = FakeSettings("ILS"), id: String = "trip"): Env {
        val io = UnconfinedTestDispatcher(testScheduler)
        val trips = TripRepository(folder.root, io, newId = { "trip" }, clock = { 1L })
        val rates = RateRepository(folder.root, FakeFetcher { HttpResponse(200, body) }, CoroutineScope(io), io, clock = { 1L })
        rates.refresh()
        trips.create("Prague", "CZK")
        return Env(WalletViewModel(id, trips, rates, settings), trips)
    }

    private fun WalletViewModel.ui() = (state.value as WalletState.Ready).ui

    @Test
    fun `unknown trip is NotFound`() = runTest {
        assertEquals(WalletState.NotFound, env(id = "nope").vm.state.value)
    }

    @Test
    fun `increment decrement set and reset`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        vm.increment(tenKc)
        vm.decrement(tenKc)
        assertEquals(1, TripRules.count(vm.ui().summary.trip, tenKc))
        vm.decrement(hundredKc) // already 0: no-op
        assertEquals(0, TripRules.count(vm.ui().summary.trip, hundredKc))
        vm.setCount(tenKc, 25)
        assertEquals(25000L, vm.ui().summary.totalMinor)
        vm.reset()
        assertEquals(0L, vm.ui().summary.totalMinor)
    }

    @Test
    fun `ECB conversion with rate date`() = runTest {
        val vm = env().vm
        vm.setCount(hundredKc, 1)
        val ui = vm.ui()
        assertEquals(1467L, ui.summary.homeMinor)
        assertEquals(RateSource.ECB, ui.summary.rate?.source)
        assertEquals("2026-11-06", ui.rateDate)
        assertFalse(ui.hasManualRate)
    }

    @Test
    fun `manual rate overrides then clears`() = runTest {
        val vm = env().vm
        vm.setCount(hundredKc, 1)
        vm.setManualRate(BigDecimal("0.2"))
        assertEquals(RateSource.MANUAL, vm.ui().summary.rate?.source)
        assertEquals(2000L, vm.ui().summary.homeMinor)
        assertTrue(vm.ui().hasManualRate)
        vm.clearManualRate()
        assertEquals(RateSource.ECB, vm.ui().summary.rate?.source)
    }

    @Test
    fun `same currency as home uses rate one`() = runTest {
        val vm = env(FakeSettings("CZK")).vm
        vm.setCount(hundredKc, 2)
        assertEquals(RateSource.SAME, vm.ui().summary.rate?.source)
        assertEquals(20000L, vm.ui().summary.homeMinor)
    }

    @Test
    fun `delete leads to NotFound and posts notice`() = runTest {
        val e = env()
        e.vm.delete()
        assertEquals(WalletState.NotFound, e.vm.state.value)
        assertTrue(e.trips.notice.value is RepoNotice.Deleted)
    }

    @Test
    fun `edit renames and blocks currency change while counts exist`() = runTest {
        val vm = env().vm
        vm.increment(tenKc)
        vm.editTrip(" Brno ", "EUR")
        assertEquals("Brno", vm.ui().summary.trip.name)
        assertEquals("CZK", vm.ui().summary.trip.currency)
        vm.reset()
        vm.editTrip("Brno", "EUR")
        assertEquals("EUR", vm.ui().summary.trip.currency)
        vm.editTrip("  ", "EUR")
        vm.editTrip("Brno", "XAU")
        assertEquals("Brno", vm.ui().summary.trip.name)
        assertEquals("EUR", vm.ui().summary.trip.currency)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.ui.wallet.WalletViewModelTest'`
Expected: FAIL, `Unresolved reference 'WalletViewModel'`.

- [ ] **Step 3: Implement `ui/wallet/WalletViewModel.kt`**

```kotlin
package com.ilankog.coinwallet.ui.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.SettingsSource
import com.ilankog.coinwallet.data.TripRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class WalletUiState(
    val summary: TripSummary,
    val home: CurrencyInfo,
    val rateDate: String?,
    val hasManualRate: Boolean,
)

sealed interface WalletState {
    data object Loading : WalletState
    data object NotFound : WalletState
    data class Ready(val ui: WalletUiState) : WalletState
}

class WalletViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val rates: RateRepository,
    private val settings: SettingsSource,
) : ViewModel() {

    val state: StateFlow<WalletState> =
        combine(trips.trips, trips.isLoaded, rates.snapshot, settings.settings) { list, loaded, snap, s ->
            build(list, loaded, snap, s)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(trips.trips.value, trips.isLoaded.value, rates.snapshot.value, settings.settings.value),
        )

    init {
        viewModelScope.launch { trips.load() }
        viewModelScope.launch { rates.load() }
    }

    fun increment(d: Denomination): Unit = edit { TripRules.increment(it, d) }
    fun decrement(d: Denomination): Unit = edit { TripRules.decrement(it, d) }
    fun setCount(d: Denomination, value: Int): Unit = edit { TripRules.setCount(it, d, value) }
    fun reset(): Unit = edit { TripRules.reset(it) }

    fun delete() {
        viewModelScope.launch { trips.delete(tripId) }
    }

    fun setManualRate(rate: BigDecimal) {
        val ui = ready() ?: return
        settings.setManualRate(ui.summary.info.code, rate, ui.home.code)
    }

    fun clearManualRate() {
        val ui = ready() ?: return
        settings.clearManualRate(ui.summary.info.code)
    }

    /** Renames; changes currency only when no pieces are counted (spec 7.2). */
    fun editTrip(name: String, currency: String) {
        val clean = TripRules.cleanName(name) ?: return
        if (Currencies.get(currency) == null) return
        edit { t ->
            if (currency != t.currency && TripRules.hasCounts(t)) t.copy(name = clean) else t.copy(name = clean, currency = currency)
        }
    }

    private fun ready(): WalletUiState? = (state.value as? WalletState.Ready)?.ui

    private fun edit(transform: (Trip) -> Trip) {
        viewModelScope.launch { trips.update(tripId, transform) }
    }

    private fun build(list: List<Trip>, loaded: Boolean, snap: RateSnapshot?, s: Settings): WalletState {
        if (!loaded) return WalletState.Loading
        val trip = list.firstOrNull { it.id == tripId } ?: return WalletState.NotFound
        val info = Currencies.get(trip.currency) ?: return WalletState.NotFound
        val home = Currencies.orFallback(s.homeCurrency)
        val manual = s.manualRates[trip.currency]
        return WalletState.Ready(
            WalletUiState(
                summary = Totals.summarize(trip, info, home, manual, snap),
                home = home,
                rateDate = snap?.date,
                hasManualRate = manual != null && manual.home == home.code,
            ),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Implement `ui/wallet/WalletDialogs.kt`**

```kotlin
package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.ui.components.DialogCard

const val COUNT_ERROR = "Enter a whole number from 0 to 9999"
const val RATE_ERROR = "Enter a rate above 0, up to 1,000,000"

@Composable
fun CountDialogContent(
    label: String,
    text: String,
    error: String?,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard(title = "How many $label?", confirmLabel = "OK", onConfirm = onConfirm, onDismiss = onDismiss) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            isError = error != null,
            supportingText = { Text(error ?: "0 to ${TripRules.MAX_COUNT}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun RateDialogContent(
    from: String,
    home: String,
    text: String,
    onTextChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val valid = Money.parseRate(text) != null
    val showError = text.isNotBlank() && !valid
    DialogCard(title = "Manual rate", confirmLabel = "Save", onConfirm = onConfirm, onDismiss = onDismiss, confirmEnabled = valid) {
        Text(
            "Use the rate you actually got, for example at an exchange booth.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1 $from =", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                isError = showError,
                singleLine = true,
                suffix = { Text(home) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        if (showError) Text(RATE_ERROR, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun ConfirmDialogContent(
    title: String,
    body: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard(title = title, confirmLabel = confirmLabel, onConfirm = onConfirm, onDismiss = onDismiss, destructive = destructive) {
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 6: Implement `ui/wallet/WalletScreen.kt`**

```kotlin
package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.DenomKind
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.core.TripSummary
import com.ilankog.coinwallet.ui.components.DenomIcon

data class WalletActions(
    val onBack: () -> Unit,
    val onIncrement: (Denomination) -> Unit,
    val onDecrement: (Denomination) -> Unit,
    val onCountClick: (Denomination) -> Unit,
    val onEdit: () -> Unit,
    val onSetRate: () -> Unit,
    val onClearRate: () -> Unit,
    val onReset: () -> Unit,
    val onDelete: () -> Unit,
)

fun rateLine(state: WalletUiState): String? {
    val info = state.summary.info
    if (info.code == state.home.code) return null
    val r = state.summary.rate ?: return "Set a manual rate from the menu, or refresh rates."
    val base = "1 ${info.code} = ${Money.formatRate(r.rate)} ${state.home.code}"
    return when (r.source) {
        RateSource.MANUAL -> "$base · manual"
        RateSource.ECB -> listOfNotNull(base, state.rateDate?.let { "ECB $it" }).joinToString(" · ")
        RateSource.SAME -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(state: WalletUiState, snackbarHostState: SnackbarHostState, actions: WalletActions) {
    val s = state.summary
    var menuOpen by remember { mutableStateOf(false) }
    val sameCurrency = s.info.code == state.home.code
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.trip.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Edit trip") }, onClick = { menuOpen = false; actions.onEdit() })
                            if (!sameCurrency) {
                                DropdownMenuItem(text = { Text("Set manual rate") }, onClick = { menuOpen = false; actions.onSetRate() })
                            }
                            if (state.hasManualRate) {
                                DropdownMenuItem(text = { Text("Clear manual rate") }, onClick = { menuOpen = false; actions.onClearRate() })
                            }
                            DropdownMenuItem(text = { Text("Reset counts") }, onClick = { menuOpen = false; actions.onReset() })
                            DropdownMenuItem(
                                text = { Text("Delete trip", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; actions.onDelete() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val coins = s.info.denominations.filter { it.kind == DenomKind.COIN }
        val notes = s.info.denominations.filter { it.kind == DenomKind.NOTE }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { WalletHeader(state) }
            item(key = "coins") { SectionTitle("Coins") }
            items(coins, key = { it.key }) { d -> DenomRow(d, s, actions) }
            item(key = "notes") { SectionTitle("Notes") }
            items(notes, key = { it.key }) { d -> DenomRow(d, s, actions) }
        }
    }
}

@Composable
private fun WalletHeader(state: WalletUiState) {
    val s = state.summary
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("In your pocket", style = MaterialTheme.typography.labelLarge)
            Text(
                Money.format(s.totalMinor, s.info),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (s.info.code != state.home.code) {
                val homeMinor = s.homeMinor
                Text(
                    if (homeMinor != null) "≈ " + Money.format(homeMinor, state.home) else "No rate",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            rateLine(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun DenomRow(d: Denomination, s: TripSummary, actions: WalletActions) {
    val count = TripRules.count(s.trip, d)
    val label = Money.label(d.value, s.info)
    val haptics = LocalHapticFeedback.current
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(60.dp), contentAlignment = Alignment.Center) { DenomIcon(d, s.info) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    if (count == 0) " " else "= " + Money.format(d.value * count, s.info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onDecrement(d) },
                enabled = count > 0,
            ) { Icon(Icons.Default.Remove, contentDescription = "Remove one $label") }
            TextButton(onClick = { actions.onCountClick(d) }, modifier = Modifier.widthIn(min = 64.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            }
            FilledTonalIconButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.onIncrement(d) },
                enabled = count < TripRules.MAX_COUNT,
            ) { Icon(Icons.Default.Add, contentDescription = "Add one $label") }
        }
    }
}
```

- [ ] **Step 7: Implement `ui/wallet/WalletRoute.kt`**

```kotlin
package com.ilankog.coinwallet.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.core.Denomination
import com.ilankog.coinwallet.core.Money
import com.ilankog.coinwallet.core.RateSource
import com.ilankog.coinwallet.core.TripRules
import com.ilankog.coinwallet.ui.components.AppDialog
import com.ilankog.coinwallet.ui.trips.TripSheet

private sealed interface WalletDialog {
    data class Count(val d: Denomination) : WalletDialog
    data object Rate : WalletDialog
    data object Reset : WalletDialog
    data object Delete : WalletDialog
    data object Edit : WalletDialog
}

@Composable
fun WalletRoute(vm: WalletViewModel, snackbarHostState: SnackbarHostState, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<WalletDialog?>(null) }
    var left by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!left) {
            left = true
            onBack()
        }
    }

    when (val st = state) {
        WalletState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        WalletState.NotFound -> LaunchedEffect(Unit) { leave() }
        is WalletState.Ready -> {
            val ui = st.ui
            WalletScreen(
                state = ui,
                snackbarHostState = snackbarHostState,
                actions = WalletActions(
                    onBack = leave,
                    onIncrement = vm::increment,
                    onDecrement = vm::decrement,
                    onCountClick = { dialog = WalletDialog.Count(it) },
                    onEdit = { dialog = WalletDialog.Edit },
                    onSetRate = { dialog = WalletDialog.Rate },
                    onClearRate = vm::clearManualRate,
                    onReset = { dialog = WalletDialog.Reset },
                    onDelete = { dialog = WalletDialog.Delete },
                ),
            )
            val close = { dialog = null }
            when (val d = dialog) {
                null -> Unit
                is WalletDialog.Count -> {
                    var text by remember(d) { mutableStateOf(TripRules.count(ui.summary.trip, d.d).toString()) }
                    var error by remember(d) { mutableStateOf<String?>(null) }
                    AppDialog(onDismiss = close) {
                        CountDialogContent(
                            label = Money.label(d.d.value, ui.summary.info),
                            text = text,
                            error = error,
                            onTextChange = { text = it; error = null },
                            onConfirm = {
                                val n = TripRules.parseCount(text)
                                if (n == null) error = COUNT_ERROR else { vm.setCount(d.d, n); close() }
                            },
                            onDismiss = close,
                        )
                    }
                }
                WalletDialog.Rate -> {
                    val current = ui.summary.rate?.takeIf { it.source == RateSource.MANUAL }?.rate?.toPlainString() ?: ""
                    var text by remember { mutableStateOf(current) }
                    AppDialog(onDismiss = close) {
                        RateDialogContent(
                            from = ui.summary.info.code,
                            home = ui.home.code,
                            text = text,
                            onTextChange = { text = it },
                            onConfirm = { Money.parseRate(text)?.let { vm.setManualRate(it); close() } },
                            onDismiss = close,
                        )
                    }
                }
                WalletDialog.Reset -> AppDialog(onDismiss = close) {
                    ConfirmDialogContent(
                        title = "Reset all counts?",
                        body = "All coins and notes in ${ui.summary.trip.name} go back to 0.",
                        confirmLabel = "Reset",
                        destructive = false,
                        onConfirm = { vm.reset(); close() },
                        onDismiss = close,
                    )
                }
                WalletDialog.Delete -> AppDialog(onDismiss = close) {
                    ConfirmDialogContent(
                        title = "Delete ${ui.summary.trip.name}?",
                        body = "You can undo right after.",
                        confirmLabel = "Delete",
                        destructive = true,
                        onConfirm = { close(); vm.delete() },
                        onDismiss = close,
                    )
                }
                WalletDialog.Edit -> TripSheet(
                    title = "Edit trip",
                    initialName = ui.summary.trip.name,
                    initialCurrency = ui.summary.trip.currency,
                    currencyLocked = TripRules.hasCounts(ui.summary.trip),
                    onSave = { name, code -> vm.editTrip(name, code); close() },
                    onDismiss = close,
                )
            }
        }
    }
}
```

- [ ] **Step 8: Extend `SampleData.kt` and write snapshot test**

Replace `app/src/test/java/com/ilankog/coinwallet/ui/SampleData.kt` with the Task 11 content plus these imports and members (full file):
```kotlin
package com.ilankog.coinwallet.ui

import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.ManualRate
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Totals
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.ui.common.RateStatus
import com.ilankog.coinwallet.ui.trips.TripsUiState
import com.ilankog.coinwallet.ui.wallet.WalletActions
import com.ilankog.coinwallet.ui.wallet.WalletUiState
import java.math.BigDecimal

object SampleData {
    const val NOW = 1_762_430_400_000L
    val ils = Currencies.orFallback("ILS")
    val snapshot = RateSnapshot(
        fetchedAt = NOW - 2 * 3_600_000L,
        date = "2026-11-06",
        rates = mapOf("EUR" to 1.0, "CZK" to 24.35, "ILS" to 3.5717, "JPY" to 176.77, "USD" to 1.1165),
    )
    val prague = Trip("t1", "Prague weekend", "CZK", mapOf("c100" to 3, "c1000" to 2, "c5000" to 1, "n20000" to 2), createdAt = 4)
    val tokyo = Trip("t2", "Tokyo", "JPY", mapOf("c1" to 7, "c100" to 4, "c500" to 2, "n1000" to 3), createdAt = 3)
    val newYork = Trip("t3", "New York", "USD", mapOf("c25" to 6, "n2000" to 1), createdAt = 2)
    val bangkok = Trip("t4", "Bangkok", "THB", mapOf("n10000" to 1), createdAt = 1)
    val allTrips = listOf(prague, tokyo, newYork, bangkok)
    val manual = mapOf("USD" to ManualRate(BigDecimal("3.6"), "ILS"))

    fun tripsState(
        status: RateStatus = RateStatus.Fresh(snapshot.fetchedAt),
        trips: List<Trip> = allTrips,
    ) = TripsUiState(
        loaded = true,
        home = ils,
        totals = Totals.all(trips, ils, manual, snapshot),
        rateStatus = status,
        now = NOW,
    )

    fun walletState(trip: Trip = prague): WalletUiState {
        val info = Currencies.orFallback(trip.currency)
        val m = manual[trip.currency]
        return WalletUiState(
            summary = Totals.summarize(trip, info, ils, m, snapshot),
            home = ils,
            rateDate = snapshot.date,
            hasManualRate = m != null,
        )
    }

    val noActions = WalletActions({}, {}, {}, {}, {}, {}, {}, {}, {})
}
```

`app/src/test/java/com/ilankog/coinwallet/ui/wallet/WalletScreenSnapshotTest.kt`:
```kotlin
package com.ilankog.coinwallet.ui.wallet

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.ilankog.coinwallet.core.Trip
import com.ilankog.coinwallet.ui.DialogFrame
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class WalletScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(trip: Trip) = paparazzi.snapBoth {
        WalletScreen(SampleData.walletState(trip), remember { SnackbarHostState() }, SampleData.noActions)
    }

    @Test
    fun ecbRate() = snap(SampleData.prague)

    @Test
    fun zeroDecimalYen() = snap(SampleData.tokyo)

    @Test
    fun manualRate() = snap(SampleData.newYork)

    @Test
    fun noRate() = snap(SampleData.bangkok)

    @Test
    fun countDialogError() = paparazzi.snapBoth {
        DialogFrame { CountDialogContent("10 Kč", "12345", COUNT_ERROR, {}, {}, {}) }
    }

    @Test
    fun rateDialog() = paparazzi.snapBoth {
        DialogFrame { RateDialogContent("USD", "ILS", "3.6", {}, {}, {}) }
    }

    @Test
    fun rateDialogInvalid() = paparazzi.snapBoth {
        DialogFrame { RateDialogContent("USD", "ILS", "0", {}, {}, {}) }
    }

    @Test
    fun deleteConfirm() = paparazzi.snapBoth {
        DialogFrame { ConfirmDialogContent("Delete Prague weekend?", "You can undo right after.", "Delete", true, {}, {}) }
    }
}
```

- [ ] **Step 9: Record, run all tests, inspect PNGs**

Run:
```bash
cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk
gradle --no-daemon -q :app:recordPaparazziDebug
gradle --no-daemon -q :app:testDebugUnitTest
ls app/src/test/snapshots/images/ | grep WalletScreenSnapshotTest | wc -l
```
Expected: exit 0; `16`. Open every new PNG; fix anything cut off (especially long subtotals and the count button width) in either theme, then re-record.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/ui/wallet app/src/test/java/com/ilankog/coinwallet/ui/SampleData.kt app/src/test/java/com/ilankog/coinwallet/ui/wallet app/src/test/snapshots/images
git commit -m "feat(ui): add wallet counter screen, dialogs and view model

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 13: Settings screen

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `SettingsRoute.kt`
- Test: `app/src/test/java/com/ilankog/coinwallet/ui/settings/SettingsViewModelTest.kt`, `SettingsScreenSnapshotTest.kt`

**Interfaces:**
- Consumes: `RateRepository`, `RefreshResult` (8), `SettingsSource`, `FakeSettings` (9), `RateSnapshot` (5), `RelativeTime` (3), `Currencies`, `CurrencyInfo` (2), `describe()`, `CodeBadge`, `AppDialog`, `CurrencyPickerDialogContent` (10).
- Produces:
  - `data class SettingsUiState(val home: CurrencyInfo, val snapshot: RateSnapshot?, val busy: Boolean, val lastResult: RefreshResult?, val now: Long, val version: String)`
  - `class SettingsViewModel(rates: RateRepository, settings: SettingsSource, version: String, clock: () -> Long = System::currentTimeMillis) : ViewModel` with `val state: StateFlow<SettingsUiState>`, `fun setHome(code: String)`, `fun refresh()`
  - `@Composable fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onPickHome: () -> Unit, onRefresh: () -> Unit)`
  - `@Composable fun SettingsRoute(vm: SettingsViewModel, onBack: () -> Unit)`

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
package com.ilankog.coinwallet.ui.settings

import com.ilankog.coinwallet.Fixtures
import com.ilankog.coinwallet.MainDispatcherRule
import com.ilankog.coinwallet.data.FakeFetcher
import com.ilankog.coinwallet.data.FakeSettings
import com.ilankog.coinwallet.data.HttpResponse
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RefreshResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.vm(fetcher: FakeFetcher): SettingsViewModel {
        val io = UnconfinedTestDispatcher(testScheduler)
        val rates = RateRepository(folder.root, fetcher, CoroutineScope(io), io, clock = { 7L })
        return SettingsViewModel(rates, FakeSettings("ILS"), "1.0.0", clock = { 7L })
    }

    @Test
    fun `setHome accepts only supported codes`() = runTest {
        val vm = vm(FakeFetcher { HttpResponse(200, "") })
        vm.setHome("XAU")
        assertEquals("ILS", vm.state.value.home.code)
        vm.setHome("CZK")
        assertEquals("CZK", vm.state.value.home.code)
        assertEquals("1.0.0", vm.state.value.version)
    }

    @Test
    fun `refresh success and failure`() = runTest {
        val f = FakeFetcher { HttpResponse(200, Fixtures.text("frankfurter_latest.json")) }
        val vm = vm(f)
        vm.refresh()
        assertNotNull(vm.state.value.snapshot)
        assertEquals(RefreshResult.Success, vm.state.value.lastResult)
        assertFalse(vm.state.value.busy)
        f.response = { HttpResponse(500, "") }
        vm.refresh()
        assertEquals(RefreshResult.HttpError(500), vm.state.value.lastResult)
        assertNotNull(vm.state.value.snapshot)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk && gradle --no-daemon -q :app:testDebugUnitTest --tests 'com.ilankog.coinwallet.ui.settings.SettingsViewModelTest'`
Expected: FAIL, `Unresolved reference 'SettingsViewModel'`.

- [ ] **Step 3: Implement `ui/settings/SettingsViewModel.kt`**

```kotlin
package com.ilankog.coinwallet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ilankog.coinwallet.core.Currencies
import com.ilankog.coinwallet.core.CurrencyInfo
import com.ilankog.coinwallet.core.RateSnapshot
import com.ilankog.coinwallet.core.Settings
import com.ilankog.coinwallet.data.RateRepository
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.data.SettingsSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val home: CurrencyInfo,
    val snapshot: RateSnapshot?,
    val busy: Boolean,
    val lastResult: RefreshResult?,
    val now: Long,
    val version: String,
)

class SettingsViewModel(
    private val rates: RateRepository,
    private val settings: SettingsSource,
    private val version: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(rates.snapshot, rates.busy, rates.lastResult, settings.settings) { snap, busy, last, s ->
            build(snap, busy, last, s)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            build(rates.snapshot.value, rates.busy.value, rates.lastResult.value, settings.settings.value),
        )

    init {
        viewModelScope.launch { rates.load() }
    }

    fun setHome(code: String) {
        if (Currencies.get(code) != null) settings.setHomeCurrency(code)
    }

    fun refresh() {
        viewModelScope.launch { rates.refresh() }
    }

    private fun build(snap: RateSnapshot?, busy: Boolean, last: RefreshResult?, s: Settings) =
        SettingsUiState(Currencies.orFallback(s.homeCurrency), snap, busy, last, clock(), version)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Implement `ui/settings/SettingsScreen.kt` and `SettingsRoute.kt`**

`SettingsScreen.kt`:
```kotlin
package com.ilankog.coinwallet.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ilankog.coinwallet.core.RelativeTime
import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.ui.common.describe
import com.ilankog.coinwallet.ui.components.CodeBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onBack: () -> Unit, onPickHome: () -> Unit, onRefresh: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Section("Home currency") {
                Card(
                    onClick = onPickHome,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CodeBadge(state.home.code)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(state.home.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "All totals are converted to this currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Section("Exchange rates") {
                Text("European Central Bank via Frankfurter", style = MaterialTheme.typography.bodyMedium)
                Text(
                    state.snapshot?.let { "Rates of ${it.date} · fetched ${RelativeTime.ago(it.fetchedAt, state.now)}" }
                        ?: "No rates saved yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onRefresh, enabled = !state.busy) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh rates now")
                }
                state.lastResult?.let { r ->
                    Text(
                        r.describe(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r == RefreshResult.Success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
            Section("About") {
                Text("Coin Wallet ${state.version}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Count the coins and notes you carry on each trip and see them in your home currency.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
```

`SettingsRoute.kt`:
```kotlin
package com.ilankog.coinwallet.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilankog.coinwallet.ui.components.AppDialog
import com.ilankog.coinwallet.ui.components.CurrencyPickerDialogContent

@Composable
fun SettingsRoute(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    SettingsScreen(state, onBack = onBack, onPickHome = { picking = true }, onRefresh = vm::refresh)
    if (picking) {
        val close = { picking = false; query = "" }
        AppDialog(onDismiss = close) {
            CurrencyPickerDialogContent(
                title = "Home currency",
                query = query,
                onQueryChange = { query = it },
                selected = state.home.code,
                onPick = { vm.setHome(it.code); close() },
                onDismiss = close,
            )
        }
    }
}
```

- [ ] **Step 6: Write snapshot test**

```kotlin
package com.ilankog.coinwallet.ui.settings

import com.ilankog.coinwallet.data.RefreshResult
import com.ilankog.coinwallet.ui.SampleData
import com.ilankog.coinwallet.ui.coinPaparazzi
import com.ilankog.coinwallet.ui.snapBoth
import org.junit.Rule
import org.junit.Test

class SettingsScreenSnapshotTest {
    @get:Rule
    val paparazzi = coinPaparazzi()

    private fun snap(state: SettingsUiState) = paparazzi.snapBoth { SettingsScreen(state, {}, {}, {}) }

    private val base = SettingsUiState(SampleData.ils, SampleData.snapshot, busy = false, lastResult = RefreshResult.Success, now = SampleData.NOW, version = "1.0.0")

    @Test
    fun withRates() = snap(base)

    @Test
    fun offlineNoRates() = snap(base.copy(snapshot = null, lastResult = RefreshResult.Offline))

    @Test
    fun refreshing() = snap(base.copy(busy = true, lastResult = null))
}
```

- [ ] **Step 7: Record, run all tests, inspect PNGs**

Run:
```bash
cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk
gradle --no-daemon -q :app:recordPaparazziDebug
gradle --no-daemon -q :app:testDebugUnitTest
ls app/src/test/snapshots/images/ | grep SettingsScreenSnapshotTest | wc -l
```
Expected: exit 0; `6`. Open each PNG; fix layout issues; re-record.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/ui/settings app/src/test/java/com/ilankog/coinwallet/ui/settings app/src/test/snapshots/images
git commit -m "feat(ui): add settings screen with home currency and rate refresh

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

### Task 14: Navigation, activity, release verification

**Files:**
- Create: `app/src/main/java/com/ilankog/coinwallet/ui/AppNav.kt`
- Modify: `app/src/main/java/com/ilankog/coinwallet/MainActivity.kt` (full replacement below)

**Interfaces:**
- Consumes: `AppContainer`, `CoinWalletApp` (9), `RepoNotice` (7), `text()` (10), `TripsRoute`, `TripsViewModel` (11), `WalletRoute`, `WalletViewModel` (12), `SettingsRoute`, `SettingsViewModel` (13), `CoinWalletTheme` (10), `BuildConfig.VERSION_NAME` (1).
- Produces: `@Serializable data object TripsDest`, `@Serializable data class WalletDest(val tripId: String)`, `@Serializable data object SettingsDest`, `@Composable fun CoinWalletNavHost(container: AppContainer)`; final `MainActivity`; signed release APK.

- [ ] **Step 1: Implement `ui/AppNav.kt`**

```kotlin
package com.ilankog.coinwallet.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ilankog.coinwallet.BuildConfig
import com.ilankog.coinwallet.data.AppContainer
import com.ilankog.coinwallet.data.RepoNotice
import com.ilankog.coinwallet.ui.common.text
import com.ilankog.coinwallet.ui.settings.SettingsRoute
import com.ilankog.coinwallet.ui.settings.SettingsViewModel
import com.ilankog.coinwallet.ui.trips.TripsRoute
import com.ilankog.coinwallet.ui.trips.TripsViewModel
import com.ilankog.coinwallet.ui.wallet.WalletRoute
import com.ilankog.coinwallet.ui.wallet.WalletViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.serialization.Serializable

@Serializable
data object TripsDest

@Serializable
data class WalletDest(val tripId: String)

@Serializable
data object SettingsDest

@Composable
fun CoinWalletNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // App-wide repository notices (corrupt reset, save failure, delete + Undo) survive navigation.
    LaunchedEffect(container) {
        container.trips.notice.filterNotNull().collect { n ->
            container.trips.clearNotice(n)
            val deleted = n as? RepoNotice.Deleted
            val result = snackbar.showSnackbar(
                message = n.text(),
                actionLabel = if (deleted != null) "Undo" else null,
                withDismissAction = deleted == null,
                duration = if (deleted != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && deleted != null) container.trips.restore(deleted.trip)
        }
    }

    NavHost(navController = nav, startDestination = TripsDest) {
        composable<TripsDest> {
            val vm = viewModel { TripsViewModel(container.trips, container.rates, container.settings) }
            TripsRoute(
                vm = vm,
                snackbarHostState = snackbar,
                onOpenTrip = { nav.navigate(WalletDest(it)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(SettingsDest) { launchSingleTop = true } },
            )
        }
        composable<WalletDest> { entry ->
            val tripId = entry.toRoute<WalletDest>().tripId
            val vm = viewModel { WalletViewModel(tripId, container.trips, container.rates, container.settings) }
            WalletRoute(vm = vm, snackbarHostState = snackbar, onBack = { nav.popBackStack() })
        }
        composable<SettingsDest> {
            val vm = viewModel { SettingsViewModel(container.rates, container.settings, BuildConfig.VERSION_NAME) }
            SettingsRoute(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
```

- [ ] **Step 2: Replace `MainActivity.kt`**

```kotlin
package com.ilankog.coinwallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ilankog.coinwallet.ui.CoinWalletNavHost
import com.ilankog.coinwallet.ui.theme.CoinWalletTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CoinWalletApp).container
        setContent {
            CoinWalletTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CoinWalletNavHost(container)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Full verification gate**

Run:
```bash
cd /home/user/coin-wallet && export ANDROID_HOME=/root/android-sdk
gradle --no-daemon -q clean assembleRelease testDebugUnitTest
/root/android-sdk/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk | head -3
python3 -c "import glob,re;t=f=s=0
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
  x=open(p).read();m=re.search(r'tests=\"(\d+)\" skipped=\"(\d+)\" failures=\"(\d+)\" errors=\"(\d+)\"',x);t+=int(m[1]);s+=int(m[2]);f+=int(m[3])+int(m[4])
print('tests',t,'skipped',s,'failed',f)"
```
Expected: gradle exit 0; apksigner prints `Signer #1 certificate DN: C=US, O=Android, CN=Android Debug`; python prints `tests <N> skipped 0 failed 0`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ilankog/coinwallet/ui/AppNav.kt app/src/main/java/com/ilankog/coinwallet/MainActivity.kt
git commit -m "feat: wire navigation, app-wide notices and main activity

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01WBz1ksx99Lui5iTLpxxUnX"
```

---

## Spec coverage check

| Spec section | Task |
|---|---|
| §3 currencies, tables, DenomKey | 2 |
| §4 money, counts, names, conversion, priority, staleness | 3, 4, 5, 6 |
| §5 trips.json atomic + corrupt, rates.json, SharedPreferences listener, mutex | 7, 8, 9 |
| §6 network, single-flight, busy in finally, results | 8 |
| §7.1 Trips, chip, pull-to-refresh, empty state | 10, 11 |
| §7.2 Trip sheet create/edit, locked currency | 11, 12 |
| §7.3 Wallet rows, dialogs, manual rate, reset, delete + Undo | 12, 14 |
| §7.4 Settings | 13 |
| §8 error handling | all (Global Constraints) |
| §9 tests + Paparazzi light/dark | 2-13 |
| Release build, apksigner | 1, 14 |
