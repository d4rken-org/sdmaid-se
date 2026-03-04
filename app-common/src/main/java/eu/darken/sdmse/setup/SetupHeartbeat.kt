package eu.darken.sdmse.setup

fun interface SetupHeartbeat {
    /**
     * @throws Exception if setup is incomplete
     */
    suspend fun checkOrThrow()
}
