package eu.darken.sdmse.automation.core.animation

import eu.darken.sdmse.common.serialization.SerializationAppModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class AnimationStateTest : BaseTest() {

    private val json: Json = SerializationAppModule().json()

    @Test
    fun `serialize full state`() {
        val state = AnimationState(
            windowAnimationScale = 1.0f,
            globalTransitionAnimationScale = 1.0f,
            globalAnimatorDurationscale = 1.0f,
        )

        val jsonStr = json.encodeToString(AnimationState.serializer(), state)
        jsonStr.toComparableJson() shouldBe """
            {
                "windowAnimationScale": 1.0,
                "globalTransitionAnimationScale": 1.0,
                "globalAnimatorDurationScale": 1.0
            }
        """.toComparableJson()

        json.decodeFromString(AnimationState.serializer(), jsonStr) shouldBe state
    }

    @Test
    fun `serialize disabled state`() {
        val jsonStr = json.encodeToString(AnimationState.serializer(), AnimationState.DISABLED)
        jsonStr.toComparableJson() shouldBe """
            {
                "windowAnimationScale": 0.0,
                "globalTransitionAnimationScale": 0.0,
                "globalAnimatorDurationScale": 0.0
            }
        """.toComparableJson()

        json.decodeFromString(AnimationState.serializer(), jsonStr) shouldBe AnimationState.DISABLED
    }

    @Test
    fun `serialize state with null values`() {
        val state = AnimationState(
            windowAnimationScale = null,
            globalTransitionAnimationScale = 1.5f,
            globalAnimatorDurationscale = null,
        )

        val jsonStr = json.encodeToString(AnimationState.serializer(), state)
        jsonStr.toComparableJson() shouldBe """
            {
                "globalTransitionAnimationScale": 1.5
            }
        """.toComparableJson()

        json.decodeFromString(AnimationState.serializer(), jsonStr) shouldBe state
    }

    @Test
    fun `deserialize from fixed json`() {
        val jsonStr = """
            {
                "windowAnimationScale": 2.0,
                "globalTransitionAnimationScale": 0.5,
                "globalAnimatorDurationScale": 1.0
            }
        """

        val state = json.decodeFromString(AnimationState.serializer(), jsonStr)
        state.windowAnimationScale shouldBe 2.0f
        state.globalTransitionAnimationScale shouldBe 0.5f
        state.globalAnimatorDurationscale shouldBe 1.0f
    }

    @Test
    fun `deserialize with missing fields defaults to null`() {
        val jsonStr = """{"windowAnimationScale": 1.0}"""

        val state = json.decodeFromString(AnimationState.serializer(), jsonStr)
        state.windowAnimationScale shouldBe 1.0f
        state.globalTransitionAnimationScale shouldBe null
        state.globalAnimatorDurationscale shouldBe null
    }
}
