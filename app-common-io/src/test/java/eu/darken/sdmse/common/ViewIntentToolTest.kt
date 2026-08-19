package eu.darken.sdmse.common

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.common.files.local.LocalPath
import eu.darken.sdmse.common.files.saf.SAFPath
import eu.darken.sdmse.common.storage.PathMapper
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import testhelpers.BaseTest
import testhelpers.TestApplication

private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"

private fun Context.registerFolderHandler(documentUri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(documentUri, DocumentsContract.Document.MIME_TYPE_DIR)
    }
    val resolveInfo = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = "com.example.filemanager"
            name = "com.example.filemanager.BrowseActivity"
            applicationInfo = ApplicationInfo().apply {
                packageName = "com.example.filemanager"
            }
        }
    }
    shadowOf(packageManager).addResolveInfoForIntent(intent, resolveInfo)
}

@Suppress("DEPRECATION")
private fun Intent.targetIntent(): Intent = getParcelableExtra<Intent>(Intent.EXTRA_INTENT).shouldNotBeNull()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
class ViewIntentToolTest : BaseTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pathMapper = mockk<PathMapper>()
    private val subject = ViewIntentTool(
        context = context,
        mimeTypeTool = MimeTypeTool(),
        pathMapper = pathMapper,
    )

    @Test
    fun `nested folder maps to a document uri`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "DCIM", "Camera")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
                "DCIM",
                "Camera",
            )
            val expected = "content://$STORAGE_AUTHORITY/document/primary%3ADCIM%2FCamera".toUri()
            context.registerFolderHandler(expected)

            val chooser = subject.createForFolder(localPath).shouldNotBeNull()
            chooser.getStringExtra(Intent.EXTRA_TITLE) shouldBe "/storage/emulated/0/DCIM/Camera"

            val target = chooser.targetIntent()
            target.action shouldBe Intent.ACTION_VIEW
            target.data shouldBe expected
            target.type shouldBe DocumentsContract.Document.MIME_TYPE_DIR
            (target.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) shouldBe 0

            subject.canOpenFolder(localPath) shouldBe true
        }
    }

    @Test
    fun `tree root with a trailing colon yields the same document uri`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "DCIM", "Camera")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary%3A",
                "DCIM",
                "Camera",
            )
            val expected = "content://$STORAGE_AUTHORITY/document/primary%3ADCIM%2FCamera".toUri()
            context.registerFolderHandler(expected)

            val target = subject.createForFolder(localPath).shouldNotBeNull().targetIntent()
            target.data shouldBe expected
        }
    }

    @Test
    fun `secondary volumes use their volume id as document root`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "1A2B-3C4D", "DCIM")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/1A2B-3C4D",
                "DCIM",
            )
            val expected = "content://$STORAGE_AUTHORITY/document/1A2B-3C4D%3ADCIM".toUri()
            context.registerFolderHandler(expected)

            val target = subject.createForFolder(localPath).shouldNotBeNull().targetIntent()
            target.data shouldBe expected
        }
    }

    @Test
    fun `the volume root maps to a document uri without segments`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
            )
            val expected = "content://$STORAGE_AUTHORITY/document/primary%3A".toUri()
            context.registerFolderHandler(expected)

            val chooser = subject.createForFolder(localPath).shouldNotBeNull()
            chooser.getStringExtra(Intent.EXTRA_TITLE) shouldBe "/storage/emulated/0"

            chooser.targetIntent().data shouldBe expected

            subject.canOpenFolder(localPath) shouldBe true
        }
    }

    @Test
    fun `Android data is provider restricted on API 30 and up`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "Android", "data", "com.example.app")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
                "Android",
                "data",
                "com.example.app",
            )
            context.registerFolderHandler(
                "content://$STORAGE_AUTHORITY/document/primary%3AAndroid%2Fdata%2Fcom.example.app".toUri(),
            )

            subject.createForFolder(localPath) shouldBe null
            subject.canOpenFolder(localPath) shouldBe false
        }
    }

    @Test
    fun `Android obb is provider restricted on API 30 and up`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "Android", "obb", "com.example.app")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
                "Android",
                "obb",
                "com.example.app",
            )
            context.registerFolderHandler(
                "content://$STORAGE_AUTHORITY/document/primary%3AAndroid%2Fobb%2Fcom.example.app".toUri(),
            )

            subject.createForFolder(localPath) shouldBe null
            subject.canOpenFolder(localPath) shouldBe false
        }
    }

    @Test
    fun `non local paths are not supported`() {
        runBlocking {
            val safPath = SAFPath.build("content://$STORAGE_AUTHORITY/tree/primary", "DCIM")

            subject.createForFolder(safPath) shouldBe null
            subject.canOpenFolder(safPath) shouldBe false
        }
    }

    @Test
    fun `unmappable paths are not supported`() {
        runBlocking {
            val localPath = LocalPath.build("data", "data", "com.example.app")
            coEvery { pathMapper.toSAFPath(localPath) } returns null

            subject.createForFolder(localPath) shouldBe null
            subject.canOpenFolder(localPath) shouldBe false
        }
    }

    @Test
    fun `without a handling activity there is no intent`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "Documents")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
                "Documents",
            )

            subject.createForFolder(localPath) shouldBe null
            subject.canOpenFolder(localPath) shouldBe false
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = TestApplication::class)
class ViewIntentToolLegacyApiTest : BaseTest() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pathMapper = mockk<PathMapper>()
    private val subject = ViewIntentTool(
        context = context,
        mimeTypeTool = MimeTypeTool(),
        pathMapper = pathMapper,
    )

    @Test
    fun `Android data is not restricted below API 30`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "Android", "data", "com.example.app")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
                "Android",
                "data",
                "com.example.app",
            )
            val expected = "content://$STORAGE_AUTHORITY/document/primary%3AAndroid%2Fdata%2Fcom.example.app".toUri()
            context.registerFolderHandler(expected)

            val target = subject.createForFolder(localPath).shouldNotBeNull().targetIntent()
            target.data shouldBe expected

            subject.canOpenFolder(localPath) shouldBe true
        }
    }

    @Test
    fun `Android obb is not restricted below API 30`() {
        runBlocking {
            val localPath = LocalPath.build("storage", "emulated", "0", "Android", "obb", "com.example.app")
            coEvery { pathMapper.toSAFPath(localPath) } returns SAFPath.build(
                "content://$STORAGE_AUTHORITY/tree/primary",
                "Android",
                "obb",
                "com.example.app",
            )
            val expected = "content://$STORAGE_AUTHORITY/document/primary%3AAndroid%2Fobb%2Fcom.example.app".toUri()
            context.registerFolderHandler(expected)

            val target = subject.createForFolder(localPath).shouldNotBeNull().targetIntent()
            target.data shouldBe expected

            subject.canOpenFolder(localPath) shouldBe true
        }
    }
}
