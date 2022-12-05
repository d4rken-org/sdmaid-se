package eu.darken.sdmse.common.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.files.core.local.LocalPath
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import testhelpers.BaseTest


class LocationInfoTest : BaseTest() {

    @Test fun `test equals`() {
        val area = DataArea(
            path = LocalPath.build("/area"),
            type = DataArea.Type.PUBLIC_DATA,
            label = "Public data",
            userHandle = UserHandle2(1),
        )
        val a = AreaInfo(area, LocalPath.build("/area/test"), "", false)
        val b = AreaInfo(area, LocalPath.build("/area/test"), "", false)
        val c = AreaInfo(area, LocalPath.build("/area/test2"), "", false)
        a shouldBe b
        a shouldNotBe c
    }
}