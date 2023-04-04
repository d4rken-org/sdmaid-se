package eu.darken.sdmse.common.files.core.local

import android.os.Parcelable
import eu.darken.sdmse.common.files.core.APathLookup
import eu.darken.sdmse.common.files.core.FileType
import eu.darken.sdmse.common.files.core.Ownership
import eu.darken.sdmse.common.files.core.Permissions
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