package eu.darken.sdmse.appcleaner.core.forensics.filter

import androidx.core.util.Pair
import eu.darken.sdmse.appcleaner.core.forensics.BaseFilterTest
import eu.darken.sdmse.appcleaner.core.forensics.neg
import eu.darken.sdmse.appcleaner.core.forensics.pos
import eu.darken.sdmse.common.areas.DataArea.Type.PRIVATE_DATA
import eu.darken.sdmse.common.areas.DataArea.Type.PUBLIC_DATA
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

    private fun create() = WhatsAppBackupsFilter(
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun `delete WhatsApp msgstore`() = runTest {
        addDefaultNegatives()
        val lastModified = Instant.ofEpochMilli(System.currentTimeMillis() - 86400000L - 1000)

        for (p in PKGS) {
            neg(p.first, SDCARD, p.second)
            neg(p.first, SDCARD, "${p.second}/Media")
            pos(p.first, SDCARD, lastModified, "${p.second}/Databases/msgstore-2017-10-21.1.db.crypt12")
            pos(p.first, SDCARD, lastModified, "${p.second}/Databases/msgstore-2017-10-21.1.db.crypt16")
            pos(p.first, SDCARD, lastModified, "${p.second}/Databases/msgstore-increment-1-2026-10-13.1.db.crypt14")
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

    @Test fun `delete WhatsApp backups in Android media layout`() = runTest {
        addDefaultNegatives()
        val lastModified = Instant.ofEpochMilli(System.currentTimeMillis() - 86400000L - 1000)

        for (p in PKGS) {
            val base = "${p.first}/${p.second}"
            neg(p.first, PUBLIC_MEDIA, base)
            neg(p.first, PUBLIC_MEDIA, "$base/Databases")
            pos(p.first, PUBLIC_MEDIA, lastModified, "$base/Databases/msgstore-2021-11-12.1.db.crypt15")
            pos(p.first, PUBLIC_MEDIA, lastModified, "$base/Databases/msgstore-increment-1-2026-10-13.1.db.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "$base/Databases/msgstore-increment-5-2026-10-13.1.db.crypt14")
            pos(p.first, PUBLIC_MEDIA, lastModified, "$base/Backups/wa-2021-11-12.1.db.crypt15")
        }
        confirm(create())
    }

    @Test fun `ignore other areas`() = runTest {
        addDefaultNegatives()
        val lastModified = Instant.ofEpochMilli(System.currentTimeMillis() - 86400000L - 1000)

        for (p in PKGS) {
            for (area in setOf(PUBLIC_DATA, PRIVATE_DATA)) {
                neg(p.first, area, lastModified, "${p.second}/Databases/msgstore-2021-11-12.1.db.crypt15")
                neg(p.first, area, lastModified, "${p.first}/${p.second}/Databases/msgstore-2021-11-12.1.db.crypt15")
                neg(p.first, area, lastModified, "${p.second}/Backups/wa-2021-11-12.1.db.crypt15")
            }
        }
        confirm(create())
    }

    @Test fun `keep current backups and recent copies`() = runTest {
        addDefaultNegatives()
        val lastModified = Instant.ofEpochMilli(System.currentTimeMillis() - 86400000L - 1000)
        val recentlyModified = Instant.ofEpochMilli(System.currentTimeMillis() - 3600000L)

        for (p in PKGS) {
            for (area in setOf(SDCARD, PUBLIC_MEDIA)) {
                neg(p.first, area, lastModified, "${p.second}/Databases/msgstore.db.crypt14")
                neg(p.first, area, lastModified, "${p.second}/Databases/msgstore-increment-1.db.crypt14")
                neg(p.first, area, recentlyModified, "${p.second}/Databases/msgstore-2021-11-12.1.db.crypt15")
            }
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