package eu.darken.sdmse.common.device

import android.content.res.Configuration
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class DeviceDetectiveTest : BaseTest() {

    private lateinit var detective: DeviceDetective

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxed = true)
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `detect Android TV with UI mode`() {
        val context = mockDevice {
            manufacturer = "Generic"
            uiModeType = Configuration.UI_MODE_TYPE_TELEVISION
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ANDROID_TV
    }

    @Test
    fun `detect Android TV with FEATURE_TELEVISION`() {
        val context = mockDevice {
            manufacturer = "Generic"
            hasTelevisionFeature = true
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ANDROID_TV
    }

    @Test
    fun `detect Android TV with FEATURE_LEANBACK`() {
        val context = mockDevice {
            manufacturer = "Generic"
            hasLeanbackFeature = true
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ANDROID_TV
    }

    @Test
    fun `detect UGOOS TV box as AOSP - issue 1826`() {
        val context = mockDevice {
            manufacturer = "UGOOS"
            uiModeType = Configuration.UI_MODE_TYPE_TELEVISION
        }
        detective = DeviceDetective(context)

        // UGOOS is a TV box but runs a phone-style ROM, so it should be detected as AOSP
        detective.getROMType() shouldBe RomType.AOSP
    }

    @Test
    fun `detect LineageOS by display property`() {
        val context = mockDevice {
            manufacturer = "Generic"
            display = "lineage_device-userdebug"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.LINEAGE
    }

    @Test
    fun `detect LineageOS by product property`() {
        val context = mockDevice {
            manufacturer = "Generic"
            product = "lineage_device"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.LINEAGE
    }

    @Test
    fun `detect LineageOS by installed packages`() {
        val context = mockDevice {
            manufacturer = "Generic"
            installedPackages = setOf("org.lineageos.lineagesettings")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.LINEAGE
    }

    @Test
    fun `detect Alcatel by brand`() {
        val context = mockDevice {
            manufacturer = "TCL"
            brand = "alcatel"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ALCATEL
    }

    @Test
    fun `detect ColorOS on Oppo device`() {
        val context = mockDevice {
            manufacturer = "oppo"
            installedPackages = setOf("com.coloros.filemanager")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.COLOROS
    }

    @Test
    fun `detect ColorOS with simsettings package`() {
        val context = mockDevice {
            manufacturer = "oppo"
            installedPackages = setOf("com.coloros.simsettings")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.COLOROS
    }

    @Test
    fun `detect FlymeOS on Meizu device`() {
        val context = mockDevice {
            manufacturer = "meizu"
            installedPackages = setOf("com.meizu.flyme.update")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.FLYMEOS
    }

    @Test
    fun `detect Huawei EMUI`() {
        val context = mockDevice {
            manufacturer = "huawei"
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HUAWEI
    }

    @Test
    fun `detect LG UX`() {
        val context = mockDevice {
            manufacturer = "lge"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.LGUX
    }

    @Test
    fun `detect MIUI V10 - xiaomi cactus Android 9`() {
        // xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.8.0.PCBMIXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V10.0.2.0.PCBMIXM"
            sdkInt = 28
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V11 - xiaomi cactus Android 9`() {
        // xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.8.0.PCBMIXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V11.0.8.0.PCBMIXM"
            sdkInt = 28
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V12 - Xiaomi raphael Android 10`() {
        // Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V12.0.1.0.QFKEUXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V12.0.1.0.QFKEUXM"
            sdkInt = 29
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V13 - Xiaomi venus Android 12`() {
        // Xiaomi/venus_eea/venus:12/SKQ1.211006.001/V13.0.1.0.SKBEUXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V13.0.1.0.SKBEUXM"
            sdkInt = 31
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V14 - Xiaomi plato Android 13`() {
        // Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V14.0.1.0.TLQIDXM"
            sdkInt = 33
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect HyperOS V816 - POCO mondrian Android 14`() {
        // POCO/mondrian_global/mondrian:14/UKQ1.230804.001/V816.0.1.0.UMNMIXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V816.0.1.0.UMNMIXM"
            sdkInt = 34
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `detect HyperOS V816 - Xiaomi aristotle Android 14`() {
        // Xiaomi/aristotle_eea/aristotle:14/UP1A.230905.011/V816.0.17.0.UMFEUXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V816.0.17.0.UMFEUXM"
            sdkInt = 34
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `detect HyperOS OS1 version`() {
        // OS1.0.12.0.ULLMIXM
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "OS1.0.12.0.ULLMIXM"
            sdkInt = 34
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `detect HyperOS OS2 - Xiaomi corot Android 15`() {
        // Xiaomi/corot_global/corot:15/AP3A.240617.008/OS2.0.6.0.VMLMIXM:user/release-keys
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "OS2.0.6.0.VMLMIXM"
            sdkInt = 35
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `HyperOS false positive on API 32 - should be MIUI`() {
        // HyperOS detection requires API 33+, otherwise it's a false positive
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "V816.0.1.0.UMNMIXM"
            sdkInt = 32 // API 32, below the threshold
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect Nubia device`() {
        val context = mockDevice {
            manufacturer = "nubia"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.NUBIA
    }

    @Test
    fun `detect OxygenOS on OnePlus device`() {
        val context = mockDevice {
            manufacturer = "OnePlus"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.OXYGENOS
    }

    @Test
    fun `detect Realme UI`() {
        val context = mockDevice {
            manufacturer = "realme"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.REALMEUI
    }

    @Test
    fun `detect OneUI on Samsung device`() {
        val context = mockDevice {
            manufacturer = "samsung"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ONEUI
    }

    @Test
    fun `detect FuntouchOS on Vivo EEA device`() {
        // vivo/V2413_EEA/V2413:15/AP3A.240905.015.A2_V000L1/compiler03201816:user/release-keys
        val context = mockDevice {
            manufacturer = "vivo"
            product = "V2413_EEA"
            sdkInt = 30
            installedPackages = setOf("com.funtouch.uiengine")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.FUNTOUCHOS
    }

    @Test
    fun `detect FuntouchOS on Vivo with funtouch package`() {
        val context = mockDevice {
            manufacturer = "vivo"
            sdkInt = 30
            installedPackages = setOf("com.funtouch.uiengine")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.FUNTOUCHOS
    }

    @Test
    fun `detect FuntouchOS on Vivo with API 29 - before OriginOS`() {
        val context = mockDevice {
            manufacturer = "vivo"
            sdkInt = 29 // OriginOS first version was API 30
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.FUNTOUCHOS
    }

    @Test
    fun `detect OriginOS on Vivo PD2366 Android 14`() {
        // vivo/PD2366/PD2366:14/UP1A.231005.007_MOD1/compiler07161632:user/release-keys
        val context = mockDevice {
            manufacturer = "vivo"
            product = "PD2366"
            sdkInt = 30 // API 30+
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ORIGINOS
    }

    @Test
    fun `detect OriginOS on Vivo PD2454 Android 15`() {
        // vivo/PD2454/PD2454:15/AP3A.240905.015.A2_V000L1/compiler250517195248:user/release-keys
        val context = mockDevice {
            manufacturer = "vivo"
            product = "PD2454"
            sdkInt = 35
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ORIGINOS
    }

    @Test
    fun `detect Honor device`() {
        val context = mockDevice {
            manufacturer = "HONOR"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HONOR
    }

    @Test
    fun `detect Doogee device`() {
        val context = mockDevice {
            manufacturer = "DOOGEE"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.DOOGEE
    }

    @Test
    fun `detect Oukitel device`() {
        // OUKITEL/OT5_EEA/OT5:13/TP1A.220624.014/20240528:user/release-keys
        val context = mockDevice {
            manufacturer = "OUKITEL"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.OUKITEL
    }

    @Test
    fun `fallback to AOSP for unknown device`() {
        val context = mockDevice {
            manufacturer = "UnknownManufacturer"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.AOSP
    }

    @Test
    fun `fallback to AOSP for Google Pixel`() {
        val context = mockDevice {
            manufacturer = "Google"
            brand = "google"
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.AOSP
    }

    @Test
    fun `Xiaomi without MIUI packages should fallback to AOSP`() {
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "eng.builder.20240101.000000"
            installedPackages = emptySet() // No MIUI packages
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.AOSP
    }

    @Test
    fun `Xiaomi with MIUI package but wrong version pattern should fallback to AOSP`() {
        val context = mockDevice {
            manufacturer = "Xiaomi"
            versionIncremental = "CUSTOM.ROM.VERSION"
            installedPackages = setOf("com.miui.securitycenter")
        }
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.AOSP
    }

    // Tests using real device fingerprints from DeviceDetective comments

    @Test
    fun `detect MIUI V11 from fingerprint - xiaomi cactus Android 9`() {
        // xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.8.0.PCBMIXM:user/release-keys
        val context = deviceFromFingerprint(
            "xiaomi/cactus/cactus:9/PPR1.180610.011/V11.0.8.0.PCBMIXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V12 from fingerprint - Xiaomi raphael Android 10`() {
        // Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V12.0.1.0.QFKEUXM:user/release-keys
        val context = deviceFromFingerprint(
            "Xiaomi/raphael_eea/raphael:10/QKQ1.190825.002/V12.0.1.0.QFKEUXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V13 from fingerprint - Xiaomi venus Android 12`() {
        // Xiaomi/venus_eea/venus:12/SKQ1.211006.001/V13.0.1.0.SKBEUXM:user/release-keys
        val context = deviceFromFingerprint(
            "Xiaomi/venus_eea/venus:12/SKQ1.211006.001/V13.0.1.0.SKBEUXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect MIUI V14 from fingerprint - Xiaomi plato Android 13`() {
        // Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys
        val context = deviceFromFingerprint(
            "Xiaomi/plato_id/plato:13/TP1A.220624.014/V14.0.1.0.TLQIDXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.MIUI
    }

    @Test
    fun `detect HyperOS V816 from fingerprint - POCO mondrian Android 14`() {
        // POCO/mondrian_global/mondrian:14/UKQ1.230804.001/V816.0.1.0.UMNMIXM:user/release-keys
        val context = deviceFromFingerprint(
            "POCO/mondrian_global/mondrian:14/UKQ1.230804.001/V816.0.1.0.UMNMIXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `detect HyperOS V816 from fingerprint - Xiaomi aristotle Android 14`() {
        // Xiaomi/aristotle_eea/aristotle:14/UP1A.230905.011/V816.0.17.0.UMFEUXM:user/release-keys
        val context = deviceFromFingerprint(
            "Xiaomi/aristotle_eea/aristotle:14/UP1A.230905.011/V816.0.17.0.UMFEUXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `detect HyperOS OS2 from fingerprint - Xiaomi corot Android 15`() {
        // Xiaomi/corot_global/corot:15/AP3A.240617.008/OS2.0.6.0.VMLMIXM:user/release-keys
        val context = deviceFromFingerprint(
            "Xiaomi/corot_global/corot:15/AP3A.240617.008/OS2.0.6.0.VMLMIXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }

    @Test
    fun `detect FuntouchOS from fingerprint - vivo V2413 EEA Android 15`() {
        // vivo/V2413_EEA/V2413:15/AP3A.240905.015.A2_V000L1/compiler03201816:user/release-keys
        val context = deviceFromFingerprint(
            "vivo/V2413_EEA/V2413:15/AP3A.240905.015.A2_V000L1/compiler03201816:user/release-keys",
            installedPackages = setOf("com.funtouch.uiengine")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.FUNTOUCHOS
    }

    @Test
    fun `detect OriginOS from fingerprint - vivo PD2366 Android 14`() {
        // vivo/PD2366/PD2366:14/UP1A.231005.007_MOD1/compiler07161632:user/release-keys
        val context = deviceFromFingerprint(
            "vivo/PD2366/PD2366:14/UP1A.231005.007_MOD1/compiler07161632:user/release-keys"
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ORIGINOS
    }

    @Test
    fun `detect OriginOS from fingerprint - vivo PD2454 Android 15`() {
        // vivo/PD2454/PD2454:15/AP3A.240905.015.A2_V000L1/compiler250517195248:user/release-keys
        val context = deviceFromFingerprint(
            "vivo/PD2454/PD2454:15/AP3A.240905.015.A2_V000L1/compiler250517195248:user/release-keys"
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.ORIGINOS
    }

    @Test
    fun `detect Oukitel from fingerprint - OT5 EEA Android 13`() {
        // OUKITEL/OT5_EEA/OT5:13/TP1A.220624.014/20240528:user/release-keys
        val context = deviceFromFingerprint(
            "OUKITEL/OT5_EEA/OT5:13/TP1A.220624.014/20240528:user/release-keys"
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.OUKITEL
    }

    @Test
    fun `detect AOSP from fingerprint - Oppo without ColorOS packages`() {
        // OPPO/CPH2247EEA/OP4F7FL1:11/RKQ1.201105.002/1632415665086:user/release-keys
        // This device doesn't have ColorOS packages, so it should fall back to AOSP
        val context = deviceFromFingerprint(
            "OPPO/CPH2247EEA/OP4F7FL1:11/RKQ1.201105.002/1632415665086:user/release-keys",
            installedPackages = emptySet() // No ColorOS packages
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.AOSP
    }

    @Test
    fun `detect HyperOS from fingerprint - POCO device`() {
        val context = deviceFromFingerprint(
            "POCO/miro_eea/miro:16/BP2A.250605.031.A3/OS3.0.3.0.WOMEUXM:user/release-keys",
            installedPackages = setOf("com.miui.securitycenter")
        )
        detective = DeviceDetective(context)

        detective.getROMType() shouldBe RomType.HYPEROS
    }
}
