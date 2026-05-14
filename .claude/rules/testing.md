# Testing Guidelines

## Framework Selection

- **JUnit 5**: Use for normal unit tests (must extend `BaseTest`)
- **JUnit 4**: Use for Robolectric tests (Robolectric doesn't support JUnit 5)

## What to Test

- Write tests for web APIs and serialized data
- Avoid `androidTest` instrumentation tests (slow, require a device)
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

## Testing Libraries

- **Assertions**: Use Kotest matchers (`io.kotest.matchers.shouldBe`, `shouldThrow`, etc.)
- **Mocking**: Use MockK (`mockk<Class>()`, `every { ... } returns ...`)
- **Flow Testing**: Use provided `FlowTest` utilities from `app-common-test`
- **Coroutine Testing**: Use enhanced `runTest2` from test extensions
- **JSON Testing**: Use `toComparableJson()` for JSON comparisons
- **DataStore Testing**: Use `mockDataStoreValue()` helper

## Test Organization

- **Package by feature**: Tests mirror the main source structure
- **Extend base classes**: Always extend appropriate base test classes
- **Use test utilities**: Leverage shared test helpers from `app-common-test`
- **Descriptive names**: Use backtick syntax for readable test names

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
