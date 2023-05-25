package eu.darken.sdmse.appcleaner.core.forensics.filter

import androidx.core.util.Pair
import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_MEDIA
import eu.darken.sdmse.common.areas.DataArea.Type.SDCARD
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

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

    @Test fun `delete WhatsApp msgstore`() = runTest {
        addDefaultNegatives()
        val lastModified = Instant.ofEpochMilli(System.currentTimeMillis() - 86400000L - 1000)

        for (p in PKGS) {
            neg(p.first, SDCARD, p.second)
            neg(p.first, SDCARD, "${p.second}/Media")
            pos(p.first, SDCARD, lastModified, "${p.second}/Databases/msgstore-2017-10-21.1.db.crypt12")
            pos(p.first, SDCARD, lastModified, "${p.second}/Databases/msgstore-2017-10-21.1.db.crypt16")
            neg(p.first, PUBLIC_MEDIA, p.second)
            neg(p.first, PUBLIC_MEDIA, "${p.second}/Media")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Databases/msgstore-2017-10-21.1.db.crypt12")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Databases/msgstore-2021-11-12.1.db.crypt15")
        }
        confirm(create())
    }

    @Test fun `delete WhatsApp backups`() = runTest {
        addDefaultNegatives()
        val lastModified = Instant.ofEpochMilli(System.currentTimeMillis() - 86400000L - 1000)

        for (p in PKGS) {
            neg(p.first, SDCARD, p.second)
            neg(p.first, SDCARD, "${p.second}/Backups")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/backup_settings-2017-10-21.1.json.crypt14")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/chatsettingsbackup-2017-10-21.1.db.crypt14")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/commerce_backup-2017-10-21.1.db.crypt14")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/stickers-2017-10-21.1.db.crypt14")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/wa-2017-10-21.1.db.crypt14")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/wallpapers-2017-10-21.1.backup.crypt14")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/backup_settings-2017-10-21.1.json.crypt15")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/chatsettingsbackup-2017-10-21.1.db.crypt15")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/commerce_backup-2017-10-21.1.db.crypt15")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/stickers-2017-10-21.1.db.crypt15")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/wa-2017-10-21.1.db.crypt15")
            pos(p.first, SDCARD, lastModified, "${p.second}/Backups/wallpapers-2017-10-21.1.backup.crypt15")
            neg(p.first, PUBLIC_MEDIA, p.second)
            neg(p.first, PUBLIC_MEDIA, "${p.second}/Backups")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/backup_settings-2017-10-21.1.json.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/chatsettingsbackup-2017-10-21.1.db.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/commerce_backup-2017-10-21.1.db.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/stickers-2017-10-21.1.db.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/wa-2017-10-21.1.db.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/wallpapers-2017-10-21.1.backup.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/backup_settings-2017-10-21.1.json.crypt15")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/chatsettingsbackup-2017-10-21.1.db.crypt15")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/commerce_backup-2017-10-21.1.db.crypt15")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/stickers-2017-10-21.1.db.crypt15")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/wa-2017-10-21.1.db.crypt15")
            pos(p.first, PUBLIC_MEDIA, lastModified, "${p.second}/Backups/wallpapers-2017-10-21.1.backup.crypt15")
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