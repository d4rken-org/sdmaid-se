package eu.darken.sdmse.common.dataarea.modules

import eu.darken.sdmse.common.dataarea.DataArea

interface DataAreaModule {

    suspend fun firstPass(): Collection<DataArea>

    suspend fun secondPass(firstPass: Collection<DataArea>): Collection<DataArea>
}