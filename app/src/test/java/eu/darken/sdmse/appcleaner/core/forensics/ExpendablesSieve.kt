package eu.darken.sdmse.appcleaner.core.forensics

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import testhelpers.BaseTest

class ExpendablesSieve : BaseTest() {
    private val context: Context = mockk()
    private val assetManager: AssetManager = mockk()

    @BeforeEach
    fun setup() {
        every { context.assets } returns assetManager
        every { assetManager.open(any()) } answers {
            this.javaClass.classLoader.getResourceAsStream(arg(0))
        }
    }
}