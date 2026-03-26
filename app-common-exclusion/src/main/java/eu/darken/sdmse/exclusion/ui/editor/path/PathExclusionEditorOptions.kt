@file:UseSerializers(APathSerializer::class)

package eu.darken.sdmse.exclusion.ui.editor.path

import android.os.Parcelable
import eu.darken.sdmse.common.files.APath
import eu.darken.sdmse.common.serialization.APathSerializer
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

@Serializable
@Parcelize
data class PathExclusionEditorOptions(
    val targetPath: APath,
) : Parcelable