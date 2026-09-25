---
paths:
  - "**/src/test*/**"
  - "**/src/androidTest/**"
  - "**/src/screenshotTest/**"
  - "app-common-test/**"
  - ".github/workflows/emulator.yml"
  - "tools/ci/**"
---

# Testing Guidelines

## Framework Selection

- **JUnit 5**: Use for normal unit tests (must extend `BaseTest`)
- **JUnit 4**: Use for Robolectric tests (Robolectric doesn't support JUnit 5)

## What to Test

- Write tests for web APIs and serialized data
- `androidTest` instrumentation is slow and needs a device: default to the JVM and write one only for the cases described under "Instrumented Tests" below
- JVM-runnable Compose UI tests are acceptable when they catch behavior that JVM unit tests can't (route decoding, sheet/back interaction, selection-mode top bar transitions). **Extend `BaseComposeRobolectricTest`** (don't reinvent the `@RunWith` / `@Config` / `createComposeRule()` preamble). Drive the **internal `Screen` composable** with a mock `MutableStateFlow`, never the Hilt-injected Host — that keeps the test JVM-only and free of `HiltAndroidRule`.

## Base Test Classes

- **`BaseTest`**: JVM unit tests (JUnit 5)
  - Location: `app-common-test/src/main/java/testhelpers/BaseTest.kt`
  - Provides custom logging, test cleanup, and `IO_TEST_BASEDIR` constant
- **`BaseComposeRobolectricTest`**: JVM Compose UI tests (Robolectric + JUnit 4)
  - Location: `app-common-test/src/main/java/testhelpers/compose/BaseComposeRobolectricTest.kt`
  - Provides `composeRule`, `@RunWith(RobolectricTestRunner)`, `@Config(sdk = [33], application = TestApplication)`, `JUnitLogger` setup, and an `@AfterClass` hook that calls `unmockkAll()` + `Logging.clearAll()`
  - Subclasses normally do not redeclare these — JUnit 4 inherits `@RunWith` and `@Rule` from the base class, and Robolectric merges `@Config` from superclasses. Add a local `@Config` on the subclass only for test-specific overrides (e.g. a different SDK level, a qualifier, or a non-default `Application`).
- **`BaseTestInstrumentation`**: Android instrumented tests (JUnit 4)
  - Location: `app/src/androidTest/java/testhelper/BaseTestInstrumentation.kt`
- **`BaseUITest`**: UI instrumentation tests (JUnit 4)
  - Location: `app/src/androidTest/java/testhelper/BaseUITest.kt`
- **`BaseCSITest`**: Forensics/CSI unit tests with extensive MockK setup (JUnit 5)
  - Location: `app-common-data/src/test/java/eu/darken/sdmse/common/forensics/csi/BaseCSITest.kt`

## Testing Patterns

### Normal Unit Tests (JUnit 5)

```kotlin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest
import io.kotest.matchers.shouldBe

class ExampleTest : BaseTest() {
    @BeforeEach
    fun setup() {
        // Test setup
    }

    @Test
    fun `descriptive test name with backticks`() {
        // Arrange
        val input = "test"

        // Act
        val result = functionUnderTest(input)

        // Assert
        result shouldBe "expected"
    }
}
```

### Robolectric Tests (JUnit 4)

For non-Compose tests that need the Android framework, extend `BaseTest` and add Robolectric's annotations on the subclass:

```kotlin
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class AndroidDependentTest : BaseTest() {
    @Test
    fun `test requiring Android framework`() {
        // Test implementation using Android APIs
    }
}
```

Caveat: `BaseTest`'s `@AfterAll` cleanup hook is a JUnit 5 annotation and **does not run under JUnit 4 / Robolectric**. The `init {}` logging setup still works (it's a Kotlin language feature, independent of the test runner). If you rely on `unmockkAll()` between tests, call it explicitly in an `@After`-annotated method.

### Compose UI Tests (Robolectric + JUnit 4)

Extend `BaseComposeRobolectricTest` and write `@Test`-annotated methods that drive composables through `composeRule`. Do not redeclare `@RunWith`, `@Config`, or `createComposeRule()` — the base class provides them.

```kotlin
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest

class MyScreenTest : BaseComposeRobolectricTest() {

    @Test
    fun `tapping the action button invokes the callback`() {
        var invoked = 0
        composeRule.setContent {
            PreviewWrapper {
                MyScreen(
                    stateSource = MutableStateFlow(MyViewModel.State()),
                    onAction = { invoked++ },
                )
            }
        }

        composeRule.onNodeWithText("Run").performClick()
        composeRule.runOnIdle { assertEquals(1, invoked) }
    }
}
```

Wrap content in `PreviewWrapper` (same as `@Preview2` does) so the test renders against the real theme.

Static / object mocks (`mockkStatic`, `mockkObject`) installed in a test method must be cleaned up by that same test — there is no shared `unmockkAll()` hook.

### ViewModel render-state harness (`safeStateIn`)

`ViewModel4.safeStateIn(...)` is `.stateIn(scope, SharingStarted.WhileSubscribed(5000), initialValue)`. The upstream flow only collects when there's at least one downstream subscriber.

In tests this matters: reading `vm.state.value` returns the **initialValue** (not the upstream-derived value), and `vm.state.first()` races with the upstream chain — both make every state assertion see `State()` defaults regardless of what the mocked upstream emits.

Keep state subscribed for the test scope's lifetime via `TestScope.backgroundScope`, which is auto-cancelled at `runTest` completion without blocking the test body:

```kotlin
// Make harness an extension on TestScope so it can launch the keep-alive.
private fun TestScope.harness(...): Harness {
    // ... mock setup ...
    val vm = SomeViewModel(...)
    if (bind) vm.bindRoute(...)

    backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
        vm.state.collect { /* keep WhileSubscribed alive */ }
    }
    return Harness(vm, ...)
}
```

Don't use `vmScope.launch { state.collect { } }` from inside `runTest2` — it never completes and trips runTest's "uncompleted coroutines" timeout. `backgroundScope` is the supported escape hatch for exactly this case.

### Stubbing `DataStoreValue<T>`

`DataStoreValue.value()` (read) and `.value(T)` (write) are **extension functions** in `DataStoreValue.kt`, not member methods, so MockK can't stub them directly. The read extension is `flow.first()` and the write extension is `update { value }` — stub or verify on those instead:

```kotlin
private fun <T : Any> mockSetting(value: T): DataStoreValue<T> =
    mockk<DataStoreValue<T>>(relaxed = true).apply {
        every { flow } returns flowOf(value)
        // .value() reads via flow.first() — no extra stub needed.
        // .value(T) writes via update {...} — relaxed mock answers; verify with:
        // coVerify { update(any()) }
    }
```

For one-line setting reads where only `.flow` matters, use the existing `mockDataStoreValue(value)` helper from `app-common-test`.

### Stubbing `ExclusionManager.save`

`ExclusionManager.save(exclusion: Exclusion)` is an **extension** that calls the real `save(toSave: Set<Exclusion>): Collection<Exclusion>` on the manager. Mock the real method and capture a `Set<Exclusion>`:

```kotlin
val captured = slot<Set<Exclusion>>()
coEvery { exclusionManager.save(capture(captured)) } returns emptyList()
// After action:
val excl = captured.captured.single()
excl.shouldBeInstanceOf<PathExclusion>()
```

### Stubbing `NavigationController.consumeResults`

Picker results arrive via `navCtrl.consumeResults(PickerResultKey(...))` which subscribers `launchIn` on in their `init`. For tests, return a hot flow you control — a `MutableSharedFlow<PickerResult>(replay = 1)` so emissions don't get dropped if the VM hasn't subscribed yet:

```kotlin
val pickerResults = MutableSharedFlow<PickerResult>(replay = 1, extraBufferCapacity = 1)
val navCtrl = mockk<NavigationController>(relaxed = true).apply {
    every { consumeResults<PickerResult>(any()) } returns pickerResults
}
// Later in test body:
pickerResults.tryEmit(PickerResult(selectedPaths = paths))
advanceUntilIdle()
```

## Instrumented Tests (`androidTest`)

New tests default to `src/test`. The cases below are the ones this repo has found that genuinely need a device.

### The case that needs a device: reflection over hidden framework internals

`app-common-io`'s storage wrappers (`StorageManager2`, `StorageVolumeX`, `VolumeInfoX`, `DiskInfoX`) reach
`StorageManager.getVolumes()`, `StorageVolume.getPath()` and the `VolumeInfo` / `DiskInfo` classes by
reflection, and every accessor swallows a failed lookup and answers null. Whether a member is still
reachable is decided by the platform's hidden-API enforcement, and Robolectric has no such surface: a
shadow returns whatever it was written to return, so a JVM test stays green long after the real member is
denylisted and the wrapper has started answering null everywhere. Only a real runtime answers that
question.

Worked example: `app-common-io/src/androidTest/java/eu/darken/sdmse/common/storage/`. `StorageVolumeXTest`
and `VolumeInfoXTest` probe each member directly via `ReflectionProbe.kt` and assert the wrapper agrees
with the probe, so a member going out of reach surfaces as a named failure instead of a null.

### The case that needs a device: app flows through the real app

The Robolectric screen tests drive each internal `Screen` composable with mock state, so nothing on the JVM
starts the real Hilt graph, navigates from `MainActivity`, or sees real permission state. `app`'s
`androidTest` covers exactly that: launch `MainActivity` with `ActivityScenario` and drive it through
`createEmptyComposeRule()`. Extend `testhelper/BaseAppFlowTest`, which carries the compose rule, `walkOnboarding()`,
the list helpers and `shell()`. Worked examples: `app/src/androidTest/java/eu/darken/sdmse/main/ui/onboarding/OnboardingFlowTest.kt`
and, for real permission state, `app/src/androidTest/java/eu/darken/sdmse/setup/SetupDetectionTest.kt`.

- These run against the production `App`, not `HiltTestApplication`: the manifest removes WorkManager's
  initializer, so the graph cannot be built without `App` acting as the `Configuration.Provider`. There is
  no `@HiltAndroidTest` / `@TestInstallIn` in this module.
- The Test Orchestrator runs each test in its own process, and `clearPackageData` clears the app's data
  after each test. Every test after the first starts as a fresh install; the first inherits whatever
  `eu.darken.sdmse` data is already on the device. Files a test writes to shared storage survive the clear;
  clean them up in the test.
- Only `:app:connectedFossDebugAndroidTest` runs in CI. Anything flavor-specific in a flow (GPlay has no
  update check, for example) has to be branched on `BuildConfigWrap.FLAVOR`.
- Match screens by their string resources, and wait with `composeRule.waitUntil` rather than assuming a
  screen is already there: first-launch work and navigation are asynchronous.
- Grant access from the test with `shell("pm grant …")` / `shell("appops set …")`; it runs as the shell uid.
  The test runs inside the app's process, so revoking a runtime permission or `MANAGE_EXTERNAL_STORAGE`
  kills it. Granting doesn't.
- The orchestrator's clear resets runtime permissions but not app-ops, `WRITE_SECURE_SETTINGS` or secure
  settings: a `GET_USAGE_STATS` / `MANAGE_EXTERNAL_STORAGE` app-op or an enabled accessibility service carries
  into later tests of the same run. Reset what can be reset in `@Before` (`appops set <pkg> GET_USAGE_STATS
  default` and `pm revoke <pkg> android.permission.WRITE_SECURE_SETTINGS` are safe).
- `MANAGE_EXTERNAL_STORAGE` cannot be reset, so storage tests form an ordered group: on API 30+
  `SetupDetectionTest.grantingStorageAccessHidesTheStorageCard` needs it missing, and every other test that grants
  it must run after that class. Observed runs order classes by fully qualified name, which nothing guarantees;
  `SystemCleanerDashboardFlowTest` sits in `eu.darken.sdmse.systemcleaner.dashboard` to come after
  `eu.darken.sdmse.setup`. If the order flips, the setup test fails on its storage precondition.
- SD Maid's accessibility service stops itself and clears its secure-settings entry while in-app consent is
  missing, so enable it from the shell only after the consent click.
- Setup re-reads permission state in `ON_RESUME`, not continuously. After a shell grant, call
  `scenario.cycleResume()`; without it the card never changes.
- Off-screen lazy-list items are not in the semantics tree, so "no node found" proves nothing. Use
  `awaitNoListItem()`, then an `awaitListItem()` for something that must still be there: absence also
  passes on any other screen with a list. For presence of a Setup card, match its body text: a loading card
  shows the same title.

### Room migrations do not need a device

`MigrationTestHelper` runs fine under Robolectric here. Two modules already do it:
`app-tool-swiper/src/test/java/eu/darken/sdmse/swiper/core/db/SwiperDatabaseMigrationTest.kt` and
`app-tool-squeezer/src/test/java/eu/darken/sdmse/squeezer/core/history/CompressionHistoryMigrationTest.kt`.
Copy either. A migration test under `androidTest` is a mistake in this repo.

### Hidden-API results depend on the caller's `targetSdk`

Enforcement is keyed on the *calling* app's `targetSdk`, and a library's test APK otherwise inherits
`compileSdk` - its observations would then describe a future app rather than the shipped one.
`app-common-io/build.gradle.kts` pins `testOptions { targetSdk = projectConfig.targetSdk }`. Any other
module adding instrumented tests that touch hidden APIs needs the same pin.
`app-common-io/src/androidTest/java/eu/darken/sdmse/common/io/InstrumentationEnvironmentTest.kt` asserts the
running APK's target at runtime, so a lost pin fails there instead of quietly changing what the tests mean.

### The API matrix is a maintenance obligation

`.github/workflows/emulator.yml` runs `:app-common-io:connectedDebugAndroidTest` and `:app:connectedFossDebugAndroidTest` on API 28 and 36, and can
only catch a regression on a level it actually runs. When `compileSdk` / `targetSdk` moves
(`buildSrc/src/main/java/ProjectConfigPlugin.kt`), add the new level to that matrix. The storage assertions
are keyed on SDK *ranges* and call `unpinnedSdk(...)` for anything outside them, so a level nobody has
observed fails loudly rather than skipping - the new level announces itself on first run.

### Running them locally

```bash
ANDROID_SERIAL=<serial> ./gradlew :app-common-io:connectedDebugAndroidTest
ANDROID_SERIAL=<serial> ./gradlew :app:connectedFossDebugAndroidTest
```

Scope it to one device. Unscoped, `connectedDebugAndroidTest` runs against every attached device, and
contributors here usually have several. The AVD also needs an SD card (`sdcard-path-or-size: 512M` in the
workflow) - the storage tests assert that a disk-backed volume exists and fail without one. Use a system image
at a level the matrix runs: on any other level the range-keyed storage tests fail through `unpinnedSdk(...)`
by design.

Run the `:app` tests on a dedicated emulator. Debug builds use the release package name, so a run clears
the data of an existing install on that device.

## Pitfalls

### Robolectric renders with stub font metrics

`BaseComposeRobolectricTest` does not measure text realistically, so assertions that depend on text
measurement prove nothing there:

- Width is a flat ~1.0dp per character, identical across text styles and unchanged by font scale. A
  13-character headline measures 13.0dp. **Text therefore never wraps**, at any width or font scale.
- Line heights are wrong in both directions: `bodyMedium` measures ~36dp at font scale 1.0 against a
  real M3 line height of ~20dp, and grows only to ~38dp at scale 2.0. `labelSmall` measures ~12dp at
  scale 1.0 and ~36dp at scale 2.0.

So a rendered `assertIsDisplayed()` check can fail on content that ships fine (phantom height overflows
a fixed-height card), and a bounds assertion cannot guard a wrap-driven regression. Existing font-scale
bounds tests pass largely because the card grows while the stub text does not; treat them as
gross-overflow guards, not as evidence about real layout.

Layout that depends on text metrics needs the `screenshotTest` source set
(`android.experimental.enableScreenshotTest=true` is on) or on-device inspection. Before claiming any
layout test guards something, reintroduce the defect and confirm the test goes red.

### A green run can be testing stale bytecode

Gradle in this repo has run tests against compiled classes that did not match the source on disk, in
both directions, most often in a worktree with the `built_in_kotlinc` compiler. A newly added `@Test`
was absent from the compiled class and the run reported success without it; after a fix was reverted,
the class still contained the fix and the test still passed.

Never accept a pass/fail on a test just added or a source just reverted without checking the binary.
Cheapest check: compare `grep -c '@Test'` in the source against `tests="N"` in
`<module>/build/test-results/<task>/TEST-<fqcn>.xml`. A mismatch means a stale compile, not a flaky
test. To force a rebuild, delete the module's `build/intermediates/built_in_kotlinc/<variant>` and
`build/intermediates/classes/<variant>`, and confirm with
`javap -p <Class>.class | grep <method-or-token>`.

Reading that XML: a passing testcase is self-closing, but the `/` follows the `time="…"` attribute
(`<testcase name="…" classname="…" time="0.067"/>`), so a regex anchored right after `name="…"`
misreports passes as failures. Cross-check `failures="0"` on the `<testsuite>` element.

## Testing Libraries

- **Assertions**: Use Kotest matchers (`io.kotest.matchers.shouldBe`, `shouldThrow`, etc.)
- **Mocking**: Use MockK (`mockk<Class>()`, `every { ... } returns ...`)
- **Flow Testing**: Use provided `FlowTest` utilities from `app-common-test`
- **Coroutine Testing**: Use enhanced `runTest2` from test extensions
- **JSON Testing**: Use `toComparableJson()` for JSON comparisons
- **DataStore Testing**: Use `mockDataStoreValue()` helper

## Common Test Utilities

```kotlin
// Flow testing
flow.test("testTag", scope).apply {
    await { values, latest -> condition }
    assertNoErrors()
    cancelAndJoin()
}

// Enhanced coroutine testing
runTest2(expectedError = IllegalArgumentException::class) {
    // Test code that should throw
}

// MockK with cleanup (handled by BaseTest)
@MockK lateinit var dependency: SomeDependency
```
