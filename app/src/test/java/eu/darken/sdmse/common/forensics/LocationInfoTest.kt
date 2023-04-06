package eu.darken.sdmse.common.forensics

import eu.darken.sdmse.common.areas.DataArea
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.files.local.LocalPath
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
            label = "Public data".toCaString(),
            userHandle = UserHandle2(1),
        )
        val a = AreaInfo(LocalPath.build("/area/test"), LocalPath.build(), area, false)
        val b = AreaInfo(LocalPath.build("/area/test"), LocalPath.build(), area, false)
        val c = AreaInfo(LocalPath.build("/area/test2"), LocalPath.build(), area, false)
        a shouldBe b
        a shouldNotBe c
    }
}