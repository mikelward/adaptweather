# Testing

This document describes the test stack used in ClothesCast, what each library
contributes, when to reach for it, and what's deliberately not in scope.

## Constraints

All tests must run **headlessly on the JVM**, with no Android device or
emulator. The CI runner is a mobile-friendly sandbox: anything that needs a
display, a real `Context`, or a `Looper` is out of scope unless we add the
infrastructure to fake it (see [Not in the project](#not-in-the-project)).

Determinism is non-negotiable. Tests use suspending barriers (Turbine's
`awaitItem()`, kotlinx-coroutines-test schedulers) rather than sleeps or
polling. A flaky test is worse than no test.

## What's in the project

### JUnit 5 (Jupiter) — the test runner

The framework that discovers and runs tests. Tests use:

- `@Test` (`org.junit.jupiter.api.Test`)
- `@BeforeEach` / `@AfterEach`
- `@TempDir` for filesystem-backed tests (DataStore, etc.)
- `@ParameterizedTest` for table-driven cases

JUnit 5 is the successor to JUnit 4: cleaner extension model
(`@ExtendWith`), better Kotlin ergonomics. It doesn't dictate how you write
assertions — that's Kotest's job.

Wired in `app/build.gradle.kts` via `unitTests.all { it.useJUnitPlatform() }`.
Deps: `junit-jupiter-api`, `junit-jupiter-engine` (runtime).

### Kotest (assertions only)

Kotlin-native assertion DSL:

```kotlin
value shouldBe expected
list.shouldContainExactly(a, b, c)
string.shouldContain("substring")
```

Kotest *can* be a full test framework with its own runner (StringSpec,
BehaviorSpec). This project pulls in **only** `kotest-assertions-core` and
runs everything via JUnit 5. The assertions read more naturally than JUnit's
`assertEquals(expected, actual)` and the failure messages are richer.

### MockK — mocking

For dependencies that are too heavy or stateful to construct directly:

```kotlin
val workManager = mockk<WorkManager>()
every { workManager.getWorkInfosForUniqueWorkFlow(any()) } returns flowOf(emptyList())
verify { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
```

Pure Kotlin (vs Mockito which is Java-first). Works on `final` classes by
default — important because Kotlin makes everything `final` by default.

**Prefer hand-written fakes when the surface is small.**
`GenerateDailyInsightTest` uses `FakeWeatherRepository` and
`FakeInsightGenerator` rather than MockK. Reach for MockK when the
real type is awkward to construct (Android-framework classes,
production-wired clients).

### kotlinx-coroutines-test — deterministic coroutines

Coroutines are async by default. Tests want determinism:

```kotlin
@Test
fun example() = runTest {
    // launches inside a TestScope with a TestDispatcher
}
```

Two main test dispatchers:

| Dispatcher | Behaviour |
|---|---|
| `StandardTestDispatcher` | Coroutines queue; you advance time explicitly with `advanceUntilIdle()`. Use when testing scheduling. |
| `UnconfinedTestDispatcher` | Coroutines execute eagerly the moment they're launched. Closest thing to "synchronous" coroutines; what you usually want for ViewModel/Flow tests. |

`Dispatchers.setMain(testDispatcher)` swaps `Dispatchers.Main` so anything
that hits the main dispatcher (lifecycle, `viewModelScope`) runs on your test
dispatcher. This is what unblocks ViewModel testing on the JVM.

> **Why is `kotlinx-coroutines-android` excluded from the test classpath?**
> See `app/build.gradle.kts` — its `AndroidDispatcherFactory` calls
> `Looper.getMainLooper()` at static init time, which throws "not mocked"
> against the AGP stub Android jar and poisons `MainDispatcherLoader` for
> the rest of the JVM, breaking every coroutine test that touches
> `Dispatchers.setMain`. Excluding it lets `TestMainDispatcherFactory` win
> the service-loader race cleanly.

### Turbine — Flow assertion DSL

Cold/hot Flows emit over time. Without Turbine you write `flow.toList()`
(loses timing) or `launch { flow.collect { ... } }` (races assertions).

```kotlin
flow.test {
    awaitItem() shouldBe TodayState(temperatureUnit = CELSIUS)
    repository.setTemperatureUnit(FAHRENHEIT)
    awaitItem().temperatureUnit shouldBe FAHRENHEIT
    cancelAndIgnoreRemainingEvents()
}
```

`awaitItem()` is a **suspending barrier**. It doesn't poll, doesn't sleep,
just suspends the coroutine until the next emission arrives. That's how the
"no sleeps, use conditions" rule is satisfied for Flow-based code.

### Ktor MockEngine — fake HTTP

Replaces the real OkHttp engine with one that returns canned responses for
specific URLs:

```kotlin
val engine = MockEngine { request ->
    respond(content = """{"results":[]}""", status = HttpStatusCode.OK)
}
```

Used in `:core:data` and `SettingsViewModelTest` (geocoding). Lets you test
HTTP-using code without a network or a server. Same library production uses
— so the request shape, JSON deserialisation, and error mapping are all
exercised end-to-end.

### In-memory DataStore (technique, not a library)

```kotlin
@TempDir lateinit var tempDir: Path
private lateinit var dataStore: DataStore<Preferences>

@BeforeEach
fun setUp() {
    dataStore = PreferenceDataStoreFactory.create(
        produceFile = { File(tempDir.toFile(), "test.preferences_pb") }
    )
}
```

`androidx.datastore:datastore-preferences-core` is the JVM-friendly subset
that doesn't require Android. Combined with JUnit's `@TempDir`, you get a
real DataStore backed by a real (throwaway) file. No mocks, no test doubles
for the storage layer — you exercise the actual code path that ships.

`InsightCacheTest` and `SettingsRepositoryTest` use this pattern.

### Robolectric — Android runtime on the JVM

Provides JVM-side implementations of `Context`, `Resources`, `Bitmap`,
`View`, `Looper`, etc. so code that touches the Android framework can be
tested headlessly. Adds 1–2 seconds to test startup, which is why the
JUnit 5 / Kotest path above is preferred for anything that doesn't need it.

In this project Robolectric exists to support **`PreviewSnapshots`** (see
below). `@RunWith(AndroidJUnit4::class) @GraphicsMode(NATIVE) @Config(sdk = …)`
switches to the real Skia pipeline so Compose can rasterise to a real
`Bitmap`, with the SDK and screen qualifiers pinned for cross-host
reproducibility.

JUnit 5 doesn't run JUnit 4 tests on its own — the bridge is
`junit-vintage-engine` as `testRuntimeOnly`, wired in `app/build.gradle.kts`.

### Roborazzi + Compose UI test — `@Preview` snapshot capture

Renders Composables to PNG on the JVM via Robolectric's Skia pipeline and
writes the result to a tracked path. The test harness is the standard
Compose UI test API (`createAndroidComposeRule`, `composeRule.setContent`,
`composeRule.onRoot()`) — same library as instrumented tests, just
running against Robolectric instead of a device.

```kotlin
@Test fun today_insight_card() = capture { InsightCardPreview() }
```

Pipeline lives in
`app/src/test/kotlin/app/clothescast/ui/today/PreviewSnapshots.kt`; PNGs
land in `app/snapshots/` (tracked in Git, so GitHub renders pixel diffs
inline in the PR's "Files changed" view). The build script sets
`roborazzi.test.record = true`, so each run re-records and the "gate" is
just "did the PNGs change?" — no separate verify step. CI commits
any new/updated PNGs back to the branch (see `.github/workflows/ci.yml`).

To add a captured state: add an `internal @Preview` wrapper in
`TodayPreviews.kt` (Preview wrappers are deliberately `internal` rather
than `public` so screen-internal composables don't leak through their
visibility), then a one-line `capture { … }` in `PreviewSnapshots`.

## Test-data conventions

- Fake / stub classes live alongside the test that owns them (private
  classes inside the test class, or top-level in the test source set).
- Sample / fixture data is constructed inline. There's no shared "test
  data" module — keeps each test legible without cross-file lookups.
- Real wire-format JSON (e.g. an actual Open-Meteo response) lives in
  `core/data/src/test/resources/` and is read with `javaClass.getResource`.

## What to use when

| Thing to test | Stack |
|---|---|
| Pure functions (mappers, conversion, prompt builders) | JUnit 5 + Kotest |
| ViewModel + Flow combine | JUnit 5 + Kotest + Turbine + kotlinx-coroutines-test (`runTest` + `UnconfinedTestDispatcher`) |
| DataStore-backed cache or repository | JUnit 5 + Kotest + `@TempDir` + real `PreferenceDataStoreFactory` |
| HTTP client logic (Ktor) | JUnit 5 + Ktor `MockEngine` |
| Heavy Android interfaces (`WorkManager`) | MockK with `every { … } returns flowOf(…)` |
| `@Preview` pixel snapshots | Roborazzi + Robolectric + Compose UI test (`createAndroidComposeRule`) |
| Composable behaviour assertions (`onNodeWithText` etc.) | Compose UI test + Robolectric (same harness as snapshots; not yet used in any test) |

## Not in the project

These are deliberate omissions, not oversights. Each adds tooling cost and
should only land when there's a clear reason.

### Instrumented Android tests (real device / emulator)

The instrumented flavour of Compose UI test (`androidTest` source set,
needs a device running), AndroidX Test, Espresso, and any on-device
screenshot tooling. Out of scope for the headless sandbox CI. Note
that the **Robolectric-backed flavour** of Compose UI test _is_ wired —
that's what `PreviewSnapshots` runs against — so behaviour assertions
(`onNodeWithText(...).assertIsDisplayed()`) are also possible headlessly
the day a test wants them, no extra infra needed.

### Paparazzi

Same role as Roborazzi (Composable → PNG on the JVM), different
rendering backend: Paparazzi uses Studio's LayoutLib, Roborazzi uses
Robolectric + real Skia. They're substitutable; we picked Roborazzi
because it shares its Android runtime with the rest of the test stack.

## Practical implications for chart-style features

`ForecastChart` is a Composable that calls Vico, which renders to a Compose
Canvas. The Roborazzi pipeline above can pixel-snapshot it, but pixel diffs
are noisy for chart-style content (axis ticks, label placement, anti-alias
fuzz) and don't tell you _why_ a render changed. The default strategy is
still:

1. **Test the data layer** — verify the right list of values flows into the
   chart's producer (round-trips through the cache, conversions are correct).
2. **Refactor pure-data shaping out of the Composable.** A function like

   ```kotlin
   fun toChartSeries(hourly: List<HourlyForecast>, unit: TemperatureUnit)
       : Pair<List<Double>, List<Double>>
   ```

   is trivially headless-testable, and the Composable becomes a thin
   wrapper.

That keeps coverage on the parts that can break without needing emulator
infrastructure.
