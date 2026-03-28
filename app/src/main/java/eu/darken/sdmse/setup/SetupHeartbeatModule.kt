package eu.darken.sdmse.setup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import eu.darken.sdmse.setup.SetupBinding

@Module
@InstallIn(SingletonComponent::class)
object SetupHeartbeatModule {

    @Provides
    @SetupBinding(SetupModule.Type.INVENTORY)
    fun inventoryHeartbeat(module: InventorySetupModule): SetupHeartbeat = SetupHeartbeat {
        if (!module.isComplete()) {
            throw IncompleteSetupException(SetupModule.Type.INVENTORY)
        }
    }
}
