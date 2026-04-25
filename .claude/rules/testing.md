# Testing Guidelines

## Framework Selection

- **JUnit 5**: Use for normal unit tests (must extend `BaseTest`)
- **JUnit 4**: Use for Robolectric tests (Robolectric doesn't support JUnit 5)

## What to Test

- Write tests for web APIs and serialized data
- Avoid `androidTest` instrumentation tests (slow, require a device)
- JVM-runnable Compose UI tests (Robolectric + `androidx.compose.ui.test.junit4.createComposeRule()`) are acceptable when they catch behavior that JVM unit tests can't (route decoding, sheet/back interaction, selection-mode top bar transitions). Drive the **internal `Screen` composable** with a mock `MutableStateFlow`, never the Hilt-injected Host — that keeps the test JVM-only and free of `HiltAndroidRule`.

## Base Test Classes

- **`BaseTest`**: JVM unit tests (JUnit 5)
  - Location: `app-common-test/src/main/java/testhelpers/BaseTest.kt`
  - Provides custom logging, test cleanup, and `IO_TEST_BASEDIR` constant
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
