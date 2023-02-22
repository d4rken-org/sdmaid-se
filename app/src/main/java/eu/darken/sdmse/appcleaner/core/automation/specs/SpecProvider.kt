package eu.darken.sdmse.appcleaner.core.automation.specs

import dagger.Reusable
import eu.darken.sdmse.automation.core.SpecSource
import javax.inject.Inject

@Reusable
class SpecProvider @Inject constructor(
//    customSpecs: CustomSpecs,
//    aosp14to28Specs: AOSP14to28Specs,
//    aosp29PlusSpecs: AOSP29PlusSpecs,
//    androidTVSpecs: AndroidTVSpecs,
//    miui12Specs: MIUI12Specs,
//    alcatelSpecs: AlcatelSpecs,
//    miuiSpecs: MIUI11Specs,
    samsung14To28Specs: Samsung14To28Specs,
    samsung29PlusSpecs: Samsung29PlusSpecs,
//    realmeSpecs: RealmeSpecs,
//    huaweiSpecs: HuaweiSpecs,
//    lgeSpecs: LGESpecs,
//    colorOSLegacySpecs: ColorOSLegacySpecs,
//    colorOS27PlusSpecs: ColorOS27PlusSpecs,
//    flymeSpecs: FlymeSpecs,
//    vivoSpecs: VivoSpecs,
//    vivio29PlusSpecs: VivoAPI29PlusSpecs,
//    nubiaSpecs: NubiaSpecs,
//    onePlus14to28Specs: OnePlus14to28Specs,
//    onePlus29PlusSpecs: OnePlus29PlusSpecs,
//    onePlus31PlusSpecs: OnePlus31PlusSpecs,
) {

    val allSpecs: List<SpecSource> = listOf(
//        customSpecs,
//        miui12Specs,
//        miuiSpecs,
        samsung14To28Specs,
        samsung29PlusSpecs,
//        alcatelSpecs,
//        realmeSpecs,
//        huaweiSpecs,
//        lgeSpecs,
//        colorOSLegacySpecs,
//        colorOS27PlusSpecs,
//        flymeSpecs,
//        vivio29PlusSpecs,
//        vivoSpecs,
//        androidTVSpecs,
//        nubiaSpecs,
//        onePlus31PlusSpecs,
//        onePlus29PlusSpecs,
//        onePlus14to28Specs,
//        // These must always come last, as fallback.
//        aosp14to28Specs,
//        aosp29PlusSpecs
    )

}