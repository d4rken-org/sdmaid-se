//package eu.darken.sdmse.appcleaner.core.forensics.generic;
//
//import android.content.Context;
//import android.content.res.AssetManager;
//
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockito.Mock;
//import org.mockito.invocation.InvocationOnMock;
//import org.mockito.junit.MockitoJUnit;
//import org.mockito.junit.MockitoRule;
//import org.mockito.stubbing.Answer;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.UUID;
//
//import eu.darken.sdmse.systemcleaner.core.filter.generic.AdvertisementFilter;
//import eu.thedarken.sdm.SDMEnvironment;
//import eu.thedarken.sdm.appcleaner.core.filter.BaseExpandablesFilterTestHelper;
//import eu.thedarken.sdm.tools.io.SDMFile;
//
//import static eu.thedarken.sdm.appcleaner.core.filter.BaseExpandablesFilterTestHelper.Candidate.neg;
//import static eu.thedarken.sdm.appcleaner.core.filter.BaseExpandablesFilterTestHelper.Candidate.pos;
//import static eu.thedarken.sdm.tools.forensics.Location.PRIVATE_DATA;
//import static eu.thedarken.sdm.tools.forensics.Location.PUBLIC_DATA;
//import static eu.thedarken.sdm.tools.forensics.Location.PUBLIC_OBB;
//import static eu.thedarken.sdm.tools.forensics.Location.SDCARD;
//import static java.util.UUID.randomUUID;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.when;
//
//@SuppressWarnings("SpellCheckingInspection")
//public class AdvertisementFilterTest extends BaseExpandablesFilterTestHelper {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    @Mock SDMEnvironment environment;
//    @Mock SDMFile cacheDir;
//    @Mock Context context;
//    @Mock AssetManager assetManager;
//
//    @Before
//    public void setup() throws IOException {
//        when(sdmContext.getContext()).thenReturn(context);
//        when(context.getAssets()).thenReturn(assetManager);
//
//        when(sdmContext.getEnv()).thenReturn(environment);
//        when(environment.getCacheDir()).thenReturn(cacheDir);
//        when(cacheDir.getName()).thenReturn("cache");
//
//        when(assetManager.open(anyString())).then(new Answer<InputStream>() {
//            @Override
//            public InputStream answer(InvocationOnMock invocation) {
//                return this.getClass().getClassLoader().getResourceAsStream(invocation.getArgument(0));
//            }
//        });
//    }
//
//    @Test
//    public void testAnalyticsFilterMologiq() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.something.mologiq").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.abcedefg.mologiq").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/databases").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/databases/mologiq_").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/item/.b0b0bf57-012d-4b0f-8266-1ca07820a91a.mologiq").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/databases/item/mologiq").build());
//
//        addCandidate(pos().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.b0b0bf57-012d-4b0f-8266-1ca07820a91a.mologiq").build());
//        addCandidate(pos().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.e3883dc0-5bd6-4b93-840a-5d95d788a87e.mologiq").build());
//
//        addCandidate(pos().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/files/.13a5fef7-518e-4a61-b856-5ae5a8701da0.mologiq").build());
//        addCandidate(pos().pkgs(testPkg()).locs(PRIVATE_DATA).prefixFree("eu.thedarken.sdm.test/databases/mologiq").build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testVulge() {
//        addDefaultNegatives();
//        addCandidate(neg().pkgs(testPkg()).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/.vungleabc").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/.vungle").build());
//        addCandidate(neg().pkgs(testPkg()).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/abc.vungleabc").build());
//
//        addCandidate(pos().pkgs(testPkg()).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/.vungle").build());
//        addCandidate(pos().pkgs(testPkg()).locs(PUBLIC_DATA).prefixFree("eu.thedarken.sdm.test/files/.vungle/" + randomUUID().toString()).build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testWeChat() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/sns_ad_landingpages").build());
//        addCandidate(neg().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/sns_ad_landingpages/.nomedia").build());
//        addCandidate(pos().pkgs("com.tencent.mm").locs(SDCARD).prefixFree("tencent/MicroMsg/sns_ad_landingpages/" + UUID.randomUUID().toString()).build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testMidasOversea() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs("com.vng.pubgmobile", "com.tencent.mm").locs(SDCARD).prefixFree("MidasOversea").build());
//        addCandidate(neg().pkgs("com.vng.pubgmobile", "com.tencent.mm").locs(SDCARD).prefixFree("MidasOversea/.nomedia").build());
//        addCandidate(pos().pkgs("com.vng.pubgmobile", "com.tencent.mm").locs(SDCARD).prefixFree("MidasOversea/" + UUID.randomUUID().toString()).build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    // https://github.com/d4rken/sdmaid-public/issues/3124
//    @Test
//    public void testVungleCache() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs("com.sega.sonicboomandroid").locs(PUBLIC_DATA).prefixFree("com.sega.sonicboomandroid/files").build());
//        addCandidate(neg().pkgs("com.sega.sonicboomandroid").locs(PUBLIC_DATA).prefixFree("com.sega.sonicboomandroid/files/vungle_ca").build());
//        addCandidate(pos().pkgs("com.sega.sonicboomandroid").locs(PUBLIC_DATA).prefixFree("com.sega.sonicboomandroid/files/vungle_cache").build());
//        addCandidate(pos().pkgs("com.sega.sonicboomandroid").locs(PUBLIC_DATA).prefixFree("com.sega.sonicboomandroid/files/vungle_cache/" + UUID.randomUUID().toString()).build());
//
//        // https://github.com/d4rken/sdmaid-public/issues/5485
//        addCandidate(neg().pkgs("com.sega.sonicboomandroid").locs(PUBLIC_DATA).prefixFree("com.sega.sonicboomandroid/no_backup").build());
//        addCandidate(pos().pkgs("com.sega.sonicboomandroid").locs(PUBLIC_DATA).prefixFree("com.sega.sonicboomandroid/no_backup/vungle_cache/" + UUID.randomUUID().toString()).build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testMeizuMedia() {
//        addCandidate(neg().pkgs("com.meizu.media.video").locs(PUBLIC_DATA).prefixFree("com.meizu.media.video/MzAdLog").build());
//        addCandidate(pos().pkgs("com.meizu.media.video").locs(PUBLIC_DATA).prefixFree("com.meizu.media.video/MzAdLog/something").build());
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testZCamera() {
//        addCandidate(neg().pkgs("com.jb.zcamera").locs(PUBLIC_OBB).prefixFree("com.jb.zcamera/GoAdSdk/advert").build());
//        addCandidate(pos().pkgs("com.jb.zcamera").locs(PUBLIC_OBB).prefixFree("com.jb.zcamera/GoAdSdk/advert/cacheFile").build());
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void faceEditor() {
//        addCandidate(neg().pkgs("com.scoompa.faceeditor").locs(PUBLIC_DATA).prefixFree("com.scoompa.faceeditor/files/ads").build());
//        addCandidate(pos().pkgs("com.scoompa.faceeditor").locs(PUBLIC_DATA).prefixFree("com.scoompa.faceeditor/files/ads/adfile").build());
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testTouchPalAdCache() {
//        String[] pkgs = new String[]{
//                "com.cootek.smartinputv5",
//                "com.cootek.smartinputv5.oem",
//                "com.emoji.keyboard.touchpal",
//                "com.keyboard.cb.oem"
//        };
//        addCandidate(neg().pkgs(pkgs).locs(SDCARD).prefixFree("TouchPal2015/plugin_cache").build());
//        addCandidate(pos().pkgs(pkgs).locs(SDCARD).prefixFree("TouchPal2015/plugin_cache/theCakeIsALie").build());
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testMeizuFileManager() {
//        addCandidate(neg().pkgs("com.meizu.filemanager").locs(PUBLIC_DATA).prefixFree("com.meizu.filemanager/update_component").build());
//        addCandidate(pos().pkgs("com.meizu.filemanager").locs(PUBLIC_DATA).prefixFree("com.meizu.filemanager/update_component_log").build());
//        addCandidate(pos().pkgs("com.meizu.filemanager").locs(PUBLIC_DATA).prefixFree("com.meizu.filemanager/update_component_log123").build());
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    // https://github.com/d4rken/sdmaid-public/issues/4230
//    @Test
//    public void testVideoLike() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs("video.like").locs(SDCARD).prefixFree("._sdk_ruui").build());
//        addCandidate(neg().pkgs("video.like").locs(SDCARD).prefixFree("_sdk_ruuid").build());
//        addCandidate(pos().pkgs("video.like").locs(SDCARD).prefixFree("._sdk_ruuid").build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void appoDeal() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs("com.ludashi.dualspace").locs(SDCARD).prefixFree(".appodea").build());
//        addCandidate(neg().pkgs("com.ludashi.dualspace").locs(SDCARD).prefixFree(".appodeall").build());
//        addCandidate(pos().pkgs("com.ludashi.dualspace").locs(SDCARD).prefixFree(".appodeal").build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//
//    @Test
//    public void testQueVideo() {
//        addDefaultNegatives();
//
//        addCandidate(neg().pkgs("com.quvideo.xiaoying").locs(SDCARD).prefixFree("data/.push").build());
//        addCandidate(pos().pkgs("com.quvideo.xiaoying").locs(SDCARD).prefixFree("data/.push_deviceid").build());
//
//        checkCanidates(new AdvertisementFilter(getSDMContext()));
//    }
//}
