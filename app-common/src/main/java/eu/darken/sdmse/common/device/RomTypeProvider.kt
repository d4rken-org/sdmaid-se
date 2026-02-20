package eu.darken.sdmse.common.device

fun interface RomTypeProvider {
    suspend fun getRomType(): RomType
}
