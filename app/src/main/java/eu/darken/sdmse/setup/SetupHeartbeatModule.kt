package eu.darken.sdmse.setup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.sdmse.setup.inventory.InventorySetupModule
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object SetupHeartbeatModule {

    @Provides
    @Named("inventory")
    fun inventoryHeartbeat(module: InventorySetupModule): SetupHeartbeat = SetupHeartbeat {
        if (!module.isComplete()) {
            throw IncompleteSetupException(SetupModule.Type.INVENTORY)
        }
    }
}
