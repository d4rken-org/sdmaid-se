package eu.darken.sdmse.appcleaner.core.forensics.filter

import androidx.core.util.Pair
import eu.darken.sdmse.appcleaner.core.forensics.*
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppBackupsFilterTest : BaseFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = WhatsAppBackupsFilter()

    // TODO refactor to non-legacy test methods
    @Test fun testWhatsAppBackupsFilter() = runTest {
        addDefaultNegatives()
        for (p in PKGS) {
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree(p.second))
            addCandidate(neg().pkgs(p.first).locs(SDCARD).prefixFree("${p.second}/Media"))
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt12")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt13")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt14")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2021-11-10.1.db.crypt15")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt16")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt17")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt18")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt19")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt20")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                neg().pkgs(p.first).locs(SDCARD)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt21")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree(p.second))
            addCandidate(neg().pkgs(p.first).locs(PUBLIC_MEDIA).prefixFree("${p.second}/Media"))
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt12")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt13")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt14")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2021-11-12.1.db.crypt15")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt16")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt17")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt18")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt19")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                pos().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt20")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
            addCandidate(
                neg().pkgs(p.first).locs(PUBLIC_MEDIA)
                    .prefixFree("${p.second}/Databases/msgstore-2017-10-21.1.db.crypt21")
                    .lastModified(System.currentTimeMillis() - 86400000L - 1000)
            )
        }
        confirm(create())
    }

    companion object {
        private val PKGS = listOf(
            Pair.create("com.whatsapp", "WhatsApp"),
            Pair.create("com.whatsapp.w4b", "WhatsApp Business")
        )
    }
}