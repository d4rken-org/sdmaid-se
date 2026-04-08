package eu.darken.sdmse.common.room

import eu.darken.sdmse.common.pkgs.toPkgId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PkgIdTypeConverterTest : BaseTest() {

    private val converter = PkgIdTypeConverter()

    @Test
    fun `typical package id round-trip`() {
        val pkgId = "eu.darken.sdmse".toPkgId()
        converter.to(converter.from(pkgId)) shouldBe pkgId
    }

    @Test
    fun `single-segment package id round-trip`() {
        val pkgId = "android".toPkgId()
        converter.to(converter.from(pkgId)) shouldBe pkgId
    }

    @Test
    fun `numeric-containing package id round-trip`() {
        val pkgId = "com.foo123.bar_baz".toPkgId()
        converter.to(converter.from(pkgId)) shouldBe pkgId
    }

    @Test
    fun `stored format is raw package name`() {
        converter.from("eu.darken.sdmse".toPkgId()) shouldBe "eu.darken.sdmse"
    }
}
