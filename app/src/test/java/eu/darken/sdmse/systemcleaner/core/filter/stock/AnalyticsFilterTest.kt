package eu.darken.sdmse.systemcleaner.core.filter.stock

import eu.darken.sdmse.common.areas.DataArea.Type.*
import eu.darken.sdmse.common.areas.currentAreas
import eu.darken.sdmse.systemcleaner.core.filter.SystemCleanerFilterTest
import eu.darken.sdmse.systemcleaner.core.sieve.BaseSieve
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AnalyticsFilterTest : SystemCleanerFilterTest() {

    @BeforeEach
    override fun setup() {
        super.setup()
    }

    @AfterEach
    override fun teardown() {
        super.teardown()
    }

    private fun create() = AnalyticsFilter(
        baseSieveFactory = object : BaseSieve.Factory {
            override fun create(config: BaseSieve.Config): BaseSieve = BaseSieve(config, fileForensics)
        },
        gatewaySwitch = gatewaySwitch,
    )

    @Test fun testFilter() = runTest {
        mockDefaults()
        val areas = setOf(SDCARD, PUBLIC_DATA)
        areaManager.currentAreas()
            .filter { areas.contains(it.type) }
            .distinctBy { it.type }
            .onEach {
                neg(it.type, "bugsense", Flag.Dir)
                neg(it.type, ".bugsense", Flag.Dir)
                pos(it.type, ".bugsense", Flag.File)
            }

        neg(SDCARD, "tlocalcookieid", Flag.File)
        pos(SDCARD, ".tlocalcookieid", Flag.File)
        neg(SDCARD, "INSTALLATION", Flag.File)
        pos(SDCARD, ".INSTALLATION", Flag.File)
        neg(SDCARD, "wps_preloaded_2.txt", Flag.File)
        pos(SDCARD, ".wps_preloaded_2.txt", Flag.File)

        // https://www.usenix.org/conference/usenixsecurity24/presentation/dong-zikan
        neg(SDCARD, ".UTSystemConfig", Flag.Dir)
        neg(SDCARD, ".UTSystemConfig/Global", Flag.Dir)
        pos(SDCARD, ".UTSystemConfig/Global/Alvin2.xml", Flag.File)

        neg(SDCARD, ".DataStorage", Flag.Dir)
        pos(SDCARD, ".DataStorage/ContextData.xml", Flag.File)

        neg(PUBLIC_DATA, "com.snssdk.api.embed", Flag.Dir)
        neg(PUBLIC_DATA, "com.snssdk.api.embed/cache", Flag.Dir)
        pos(PUBLIC_DATA, "com.snssdk.api.embed/cache/clientudid.dat", Flag.File)

        neg(SDCARD, "Tencent", Flag.Dir)
        neg(SDCARD, "Tencent/ams", Flag.Dir)
        neg(SDCARD, "Tencent/ams/cache", Flag.Dir)
        pos(SDCARD, "Tencent/ams/cache/meta.dat", Flag.File)

        neg(PUBLIC_DATA, "com.tencent.ams", Flag.Dir)
        neg(PUBLIC_DATA, "com.tencent.ams/cache", Flag.Dir)
        pos(PUBLIC_DATA, "com.tencent.ams/cache/meta.dat", Flag.File)

        neg(SDCARD, "backups", Flag.Dir)
        neg(SDCARD, "backups/.SystemConfig", Flag.Dir)
        pos(SDCARD, "backups/.SystemConfig/.cuid", Flag.File)
        pos(SDCARD, "backups/.SystemConfig/.cuid2", Flag.File)

        pos(SDCARD, "backups/.adiu", Flag.File)

        neg(SDCARD, "Mob", Flag.Dir)
        neg(SDCARD, "Mob/comm", Flag.Dir)
        neg(SDCARD, "Mob/comm/dbs", Flag.Dir)
        pos(SDCARD, "Mob/comm/dbs/.duid", Flag.File)

        neg(PUBLIC_DATA, ".mn", Flag.Dir)
        pos(PUBLIC_DATA, ".mn_1006862472", Flag.File)

        neg(SDCARD, "imei.txt", Flag.File)
        pos(SDCARD, ".imei.txt", Flag.File)

        neg(SDCARD, "DC4278477faeb9.txt", Flag.File)
        pos(SDCARD, ".DC4278477faeb9.txt", Flag.File)

        neg(SDCARD, "Android/obj", Flag.Dir)
        neg(SDCARD, "Android/obj/.um", Flag.Dir)
        pos(SDCARD, "Android/obj/.um/sysid.dat", Flag.File)

        neg(PUBLIC_DATA, ".um", Flag.Dir)
        neg(PUBLIC_DATA, ".um/sysid", Flag.Dir)
        pos(PUBLIC_DATA, ".um/sysid.dat", Flag.File)

        neg(SDCARD, ".pns", Flag.Dir)
        neg(SDCARD, ".pns/.uniqueId", Flag.Dir)
        pos(SDCARD, ".pns/.uniqueId/file", Flag.File)

        neg(SDCARD, "oukdtft", Flag.Dir)
        pos(SDCARD, ".oukdtft", Flag.Dir)

        neg(SDCARD, "libs", Flag.Dir)
        pos(SDCARD, "libs/com.igexin.sdk.deviceId.db", Flag.Dir)

        neg(SDCARD, "data", Flag.Dir)
        pos(SDCARD, "data/.push_deviceid", Flag.File)

        neg(SDCARD, "msc", Flag.Dir)
        pos(SDCARD, "msc/.2F6E2C5B63F0F83B", Flag.File)

        neg(SDCARD, ".lm_device", Flag.Dir)
        pos(SDCARD, ".lm_device/.lm_device_id", Flag.File)

        neg(SDCARD, "LMDevice", Flag.Dir)
        pos(SDCARD, "LMDevice/lm_device_id", Flag.File)

        confirm(create())
    }

}