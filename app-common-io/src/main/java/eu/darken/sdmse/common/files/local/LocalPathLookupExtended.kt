package eu.darken.sdmse.common.files.local

import android.os.Parcelable
import eu.darken.sdmse.common.files.APathLookup
import eu.darken.sdmse.common.files.APathLookupExtended
import eu.darken.sdmse.common.files.Ownership
import eu.darken.sdmse.common.files.Permissions
import kotlinx.parcelize.Parcelize

@Parcelize
data class LocalPathLookupExtended(
    val lookup: LocalPathLookup,
    override val ownership: Ownership?,
    override val permissions: Permissions?,
) : APathLookupExtended<LocalPath>, APathLookup<LocalPath> by lookup, Parcelable