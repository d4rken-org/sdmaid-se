package eu.darken.sdmse.appcleaner.core.forensics.filter

import dagger.Binds
import dagger.Module
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.appcleaner.core.AppCleanerSettings
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.datastore.value
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.*
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider


@Reusable
class WhatsAppBackupsFilter @Inject constructor() : ExpendablesFilter {

    override suspend fun initialize() {
        log(TAG) { "initialize()" }
    }

    override suspend fun isExpendable(
        pkgId: Pkg.Id,
        target: APathLookup<APath>,
        areaType: DataArea.Type,
        segments: Segments
    ): Boolean {
        if (segments.isNotEmpty() && IGNORED_FILES.contains(segments[segments.size - 1])) return false

        if (!VALID_LOCS.contains(DataArea.Type.SDCARD)) return false
        if (!VALID_PKGS.contains(pkgId)) return false
        if (VALID_PREFIXES.none { segments.startsWith(it) }) return false
        if (VALID_POSTFIXES.none { segments.last().endsWith(it) }) return false

        return if (target.modifiedAt == Instant.EPOCH) {
            false
        } else {
            Duration.between(target.modifiedAt, Instant.now()) > Duration.ofDays(1)
        }
    }

    @Reusable
    class Factory @Inject constructor(
        private val settings: AppCleanerSettings,
        private val filterProvider: Provider<WhatsAppBackupsFilter>
    ) : ExpendablesFilter.Factory {
        override suspend fun isEnabled(): Boolean = settings.filterWhatsAppBackupsEnabled.value()
        override suspend fun create(): ExpendablesFilter = filterProvider.get()
    }

    @InstallIn(SingletonComponent::class)
    @Module
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): ExpendablesFilter.Factory
    }

    companion object {
        private val IGNORED_FILES: Collection<String> = listOf(
            ".nomedia"
        )
        private val VALID_LOCS = setOf(
            DataArea.Type.SDCARD,
            DataArea.Type.PUBLIC_MEDIA
        )
        private val VALID_PKGS = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
        ).map { it.toPkgId() }
        private val VALID_PREFIXES = setOf(
            "WhatsApp",
            "com.whatsapp",
            "com.whatsapp/WhatsApp",

            "WhatsApp Business",
            "com.whatsapp.w4b",
            "com.whatsapp.w4b/WhatsApp Business",
        ).map { name -> "$name/Databases/msgstore-".toSegs() }
        private val VALID_POSTFIXES = setOf(
            ".db.crypt12",
            ".db.crypt13",
            ".db.crypt14",
            ".db.crypt15",
            ".db.crypt16",
            ".db.crypt17",
            ".db.crypt18",
            ".db.crypt19",
            ".db.crypt20",
        )

        private val TAG = logTag("AppCleaner", "Scanner", "Filter", "WhatsApp", "Backups")
    }
}