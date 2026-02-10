package eu.darken.sdmse.automation.core.animation

import com.squareup.moshi.Moshi
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class AnimationStateTest : BaseTest() {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(AnimationState::class.java)

    @Test
    fun `serialize full state`() {
        val state = AnimationState(
            windowAnimationScale = 1.0f,
            globalTransitionAnimationScale = 1.0f,
            globalAnimatorDurationscale = 1.0f,
        )

        val json = adapter.toJson(state)
        json.toComparableJson() shouldBe """
            {
                "windowAnimationScale": 1.0,
                "globalTransitionAnimationScale": 1.0,
                "globalAnimatorDurationScale": 1.0
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe state
    }

    @Test
    fun `serialize disabled state`() {
        val json = adapter.toJson(AnimationState.DISABLED)
        json.toComparableJson() shouldBe """
            {
                "windowAnimationScale": 0.0,
                "globalTransitionAnimationScale": 0.0,
                "globalAnimatorDurationScale": 0.0
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe AnimationState.DISABLED
    }

    @Test
    fun `serialize state with null values`() {
        val state = AnimationState(
            windowAnimationScale = null,
            globalTransitionAnimationScale = 1.5f,
            globalAnimatorDurationscale = null,
        )

        val json = adapter.toJson(state)
        json.toComparableJson() shouldBe """
            {
                "globalTransitionAnimationScale": 1.5
            }
        """.toComparableJson()

        adapter.fromJson(json) shouldBe state
    }

    @Test
    fun `deserialize from fixed json`() {
        val json = """
            {
                "windowAnimationScale": 2.0,
                "globalTransitionAnimationScale": 0.5,
                "globalAnimatorDurationScale": 1.0
            }
        """

        val state = adapter.fromJson(json)!!
        state.windowAnimationScale shouldBe 2.0f
        state.globalTransitionAnimationScale shouldBe 0.5f
        state.globalAnimatorDurationscale shouldBe 1.0f
    }

    @Test
    fun `deserialize with missing fields defaults to null`() {
        val json = """{"windowAnimationScale": 1.0}"""

        val state = adapter.fromJson(json)!!
        state.windowAnimationScale shouldBe 1.0f
        state.globalTransitionAnimationScale shouldBe null
        state.globalAnimatorDurationscale shouldBe null
    }
}
