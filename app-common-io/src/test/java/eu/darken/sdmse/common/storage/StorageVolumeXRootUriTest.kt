package eu.darken.sdmse.common.storage

import android.content.Intent
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import androidx.core.net.toUri
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

/** Below API 29 the SAF root uri is built by hand, from 29 the platform's tree intent supplies it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = TestApplication::class)
class StorageVolumeXRootUriTest : BaseTest() {

    private fun volume(isEmulated: Boolean, uuid: String?, platformRootUri: String? = null) = StorageVolumeX(
        mockk<StorageVolume> {
            every { this@mockk.isEmulated } returns isEmulated
            every { this@mockk.uuid } returns uuid
            if (platformRootUri != null) {
                every { createOpenDocumentTreeIntent() } returns
                    Intent().putExtra(DocumentsContract.EXTRA_INITIAL_URI, platformRootUri.toUri())
            }
        }
    )

    @Test fun `the emulated volume addresses the primary root`() {
        volume(isEmulated = true, uuid = null).apply {
            rootUri.toString() shouldBe "content://com.android.externalstorage.documents/root/primary"
            documentUri.toString() shouldBe "content://com.android.externalstorage.documents/document/primary"
            treeUri.toString() shouldBe "content://com.android.externalstorage.documents/tree/primary"
        }
    }

    @Test fun `a public volume addresses its uuid root`() {
        volume(isEmulated = false, uuid = "1234-5678").apply {
            rootUri.toString() shouldBe "content://com.android.externalstorage.documents/root/1234-5678"
            documentUri.toString() shouldBe "content://com.android.externalstorage.documents/document/1234-5678"
            treeUri.toString() shouldBe "content://com.android.externalstorage.documents/tree/1234-5678"
        }
    }

    @Test fun `a public volume without a uuid has no root`() {
        volume(isEmulated = false, uuid = null).apply {
            rootUri shouldBe null
            documentUri shouldBe null
            treeUri shouldBe null
        }
    }

    @Config(sdk = [33])
    @Test fun `the platform supplies the root from API 29`() {
        volume(
            isEmulated = false,
            uuid = "1234-5678",
            platformRootUri = "content://com.android.externalstorage.documents/root/1234-5678",
        ).apply {
            rootUri.toString() shouldBe "content://com.android.externalstorage.documents/root/1234-5678"
            treeUri.toString() shouldBe "content://com.android.externalstorage.documents/tree/1234-5678"
        }
    }

    /** The platform would build "root/null" here, a root its own provider never publishes. */
    @Config(sdk = [33])
    @Test fun `a public volume without a uuid has no root from API 29`() {
        volume(isEmulated = false, uuid = null).apply {
            rootUri shouldBe null
            documentUri shouldBe null
            treeUri shouldBe null
        }
    }
}
