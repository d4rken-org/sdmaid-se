package eu.darken.sdmse.common.room

import androidx.room.TypeConverter
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.toPkgId

class PkgIdTypeConverter {
    @TypeConverter
    fun from(value: Pkg.Id): String = value.name

    @TypeConverter
    fun to(value: String): Pkg.Id = value.toPkgId()
}