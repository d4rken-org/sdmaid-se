package eu.darken.sdmse.common.picker

import android.os.Parcel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.RawPath
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/**
 * A PickerRoute on the Navigation3 back stack is persisted through the androidx savedstate codec.
 * This is the shape that crashed on process death restore in v2.0.2.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class PickerRouteSavedStateTest : BaseTest() {

    private val route = PickerRoute(
        request = PickerRequest(
            requestKey = "test-key",
            mode = PickerRequest.PickMode.DIRS,
            allowedAreas = setOf(DataArea.Type.SDCARD, DataArea.Type.PUBLIC_DATA),
            selectedPaths = listOf(
                LocalPath.build("/storage/emulated/0/DCIM"),
                SAFPath(
                    "content://com.android.externalstorage.documents/tree/primary%3A",
                    listOf("Pictures", "Wallpaper"),
                ),
                RawPath.build("/some/raw/path"),
            ),
        ),
    )

    @Test
    fun `PickerRoute saved state round-trip`() {
        val savedState = encodeToSavedState(PickerRoute.serializer(), route)
        decodeFromSavedState(PickerRoute.serializer(), savedState) shouldBe route
    }

    @Test
    fun `PickerRoute survives a full nav back stack save and restore`() {
        val serializer = NavBackStackSerializer(NavKeySerializer<NavKey>())
        val original = NavBackStack<NavKey>(route)

        val savedState = encodeToSavedState(serializer, original)

        val parcel = Parcel.obtain()
        val restored = try {
            parcel.writeBundle(savedState)
            parcel.setDataPosition(0)
            val marshalled = parcel.readBundle(javaClass.classLoader)!!
            decodeFromSavedState(serializer, marshalled)
        } finally {
            parcel.recycle()
        }

        restored.size shouldBe 1
        restored.first() shouldBe route
    }
}
