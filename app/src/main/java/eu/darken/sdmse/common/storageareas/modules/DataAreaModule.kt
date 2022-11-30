package eu.darken.sdmse.common.storageareas.modules

import eu.darken.sdmse.common.storageareas.StorageArea

interface DataAreaModule {

    suspend fun firstPass(): Collection<StorageArea>

    suspend fun secondPass(firstPass: Collection<StorageArea>): Collection<StorageArea>
}