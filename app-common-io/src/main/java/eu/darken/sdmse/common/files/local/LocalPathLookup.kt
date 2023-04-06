package eu.darken.sdmse.common.files.local

import android.os.Parcelable
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.FileType
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class LocalPathLookup(
    override val lookedUp: LocalPath,
    override val fileType: FileType,
    override val size: Long,
    override val modifiedAt: Instant,
    override val ownership: Ownership?,
    override val permissions: Permissions?,
    override val target: LocalPath?
) : APathLookup<LocalPath>, Parcelable