package eu.darken.sdmse.common.compose

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.airbnb.lottie.model.KeyPath
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

// Lottie ignores dynamic properties whose key path matches nothing, so a renamed layer would silently drop a costume.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class SdmMascotCostumeLayersTest : BaseTest() {

    private val drawable by lazy {
        val composition = LottieCompositionFactory.fromAssetSync(
            ApplicationProvider.getApplicationContext(),
            MASCOT_ANIMATION.assetName,
        ).value!!
        LottieDrawable().apply { setComposition(composition) }
    }

    @Test
    fun `a missing layer resolves to nothing`() {
        drawable.resolveKeyPath(KeyPath(MASCOT_ROOT_LAYER, "cup_missing")).shouldBeEmpty()
    }

    @Test
    fun `every cup costume layer exists in the animation`() {
        CupCostume.entries.forEach {
            drawable.resolveKeyPath(KeyPath(MASCOT_ROOT_LAYER, it.layerName)).shouldNotBeEmpty()
        }
    }

    @Test
    fun `the cup handle layer exists in the animation`() {
        drawable.resolveKeyPath(KeyPath(MASCOT_ROOT_LAYER, CUP_HANDLE_LAYER, "**")).shouldNotBeEmpty()
    }

    // A costume only ever turns its own layer on, so a visible default would stack every cup all year.
    @Test
    fun `every cup costume layer starts hidden`() {
        val animation = ApplicationProvider.getApplicationContext<Context>().assets
            .open(MASCOT_ANIMATION.assetName)
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        val rootRef = animation.getJSONArray("layers").objects()
            .single { it.getString("nm") == MASCOT_ROOT_LAYER }
            .getString("refId")
        val rootLayers = animation.getJSONArray("assets").objects()
            .single { it.optString("id") == rootRef }
            .getJSONArray("layers").objects()
        CupCostume.entries.forEach { costume ->
            val opacity = rootLayers.single { it.getString("nm") == costume.layerName }
                .getJSONObject("ks")
                .getJSONObject("o")
            withClue(costume) {
                opacity.getInt("a") shouldBe 0
                opacity.getDouble("k") shouldBe 0.0
            }
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
