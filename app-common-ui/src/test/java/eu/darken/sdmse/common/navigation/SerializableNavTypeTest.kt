package eu.darken.sdmse.common.navigation

import androidx.navigation.NavType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SerializableNavTypeTest : BaseTest() {

    @Serializable
    data class TestArg(val value: String, val count: Int = 0)

    private val nonNullType = serializableNavType(TestArg.serializer())
    private val nullableType = serializableNavType(TestArg.serializer(), isNullableAllowed = true)

    @Test
    fun `non-null type serialize and parse round-trip`() {
        val original = TestArg("hello", 42)

        val serialized = nonNullType.serializeAsValue(original)
        serialized shouldNotBe ""

        val parsed = nonNullType.parseValue(serialized)
        parsed shouldBe original
    }

    @Test
    fun `non-null type isNullableAllowed is false`() {
        nonNullType.isNullableAllowed shouldBe false
    }

    @Test
    fun `nullable type serialize and parse round-trip with non-null value`() {
        val original = TestArg("hello", 42)

        val serialized = nullableType.serializeAsValue(original)
        serialized shouldNotBe ""

        val parsed = nullableType.parseValue(serialized)
        parsed shouldBe original
    }

    @Test
    fun `nullable type isNullableAllowed is true`() {
        nullableType.isNullableAllowed shouldBe true
    }

    @Test
    fun `nullable type serializeAsValue handles null without NPE`() {
        // Regression: Navigation calls serializeAsValue(null) for nullable route fields.
        // The original implementation used NavType<T> where T:Any, causing Kotlin to insert
        // Intrinsics.checkNotNullParameter which threw NPE before our code ran.
        @Suppress("UNCHECKED_CAST")
        val asNullable = nullableType as NavType<TestArg?>

        val result = asNullable.serializeAsValue(null)
        result shouldBe "null"
    }

    @Test
    fun `nullable type handles special characters in values`() {
        val original = TestArg("hello world/path?query=1&foo=bar", 0)

        val serialized = nullableType.serializeAsValue(original)
        val parsed = nullableType.parseValue(serialized)

        parsed shouldBe original
    }
}
