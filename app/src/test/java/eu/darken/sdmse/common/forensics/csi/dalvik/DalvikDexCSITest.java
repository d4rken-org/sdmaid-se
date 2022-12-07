//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnit;
//import org.mockito.junit.MockitoRule;
//import org.mockito.stubbing.Answer;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.UUID;
//
//import androidx.core.util.Pair;
//import eu.thedarken.sdm.tools.apps.IPCFunnel;
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.BaseCSITest;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//import testhelper.MockFile;
//
//import static junit.framework.Assert.assertFalse;
//import static junit.framework.Assert.assertNotNull;
//import static junit.framework.Assert.assertNull;
//import static junit.framework.Assert.assertTrue;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.core.Is.is;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//public class CSIDalvikDexTest extends BaseCSITest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    Collection<SDMFile> dalvikCachesBases = new ArrayList<>();
//    Collection<SDMFile> dalviks = new ArrayList<>();
//    Collection<SDMFile> dalviksX86 = new ArrayList<>();
//    Collection<SDMFile> dalviksX64 = new ArrayList<>();
//    @Mock Storage storageDalvikProfileX861;
//    @Mock Storage storageDalvikProfileX641;
//    @Mock Storage storageDalvikProfileX862;
//    @Mock Storage storageDalvikProfileX642;
//    @Mock Storage storageData1;
//    @Mock Storage storageData2;
//    @Mock Storage storageSystem;
//    @Mock Storage storageSystemApp;
//    @Mock Storage storageSystemPrivApp;
//    @Mock Storage storageVendor;
//
//    @Before
//    @Override
//    public void setup() {
//        super.setup();
//        SDMFile baseDataPath1 = MockFile.path("/data/app").build();
//        SDMFile baseDataPath2 = MockFile.path("/mnt/expand/uuid/app").build();
//
//        when(storageManager.getStorages(Location.APP_APP, true)).thenReturn(Arrays.asList(storageData1, storageData2));
//        when(storageData1.getFile()).thenReturn(baseDataPath1);
//        when(storageData2.getFile()).thenReturn(baseDataPath2);
//
//        when(storageManager.getStorages(Location.SYSTEM, true)).thenReturn(Collections.singleton(storageSystem));
//        SDMFile systemPath = MockFile.path("/system").build();
//        when(storageSystem.getFile()).thenReturn(systemPath);
//        when(storageManager.getStorages(Location.SYSTEM_APP, true)).thenReturn(Collections.singleton(storageSystemApp));
//        SDMFile systemAppPath = MockFile.path("/system/app").build();
//        when(storageSystemApp.getFile()).thenReturn(systemAppPath);
//        when(storageManager.getStorages(Location.SYSTEM_PRIV_APP, true)).thenReturn(Collections.singleton(storageSystemPrivApp));
//        SDMFile systemPrivAppPath = MockFile.path("/system/priv-app").build();
//        when(storageSystemPrivApp.getFile()).thenReturn(systemPrivAppPath);
//
//        SDMFile vendorPath = MockFile.path("/vendor").build();
//        when(storageVendor.getFile()).thenReturn(vendorPath);
//
//        SDMFile base1 = MockFile.path("/data/dalvik-cache").build();
//        SDMFile base2 = MockFile.path("/cache/dalvik-cache").build();
//        dalvikCachesBases.add(base1);
//        dalvikCachesBases.add(base2);
//        SDMFile dalvik1 = MockFile.path(base1, "x86").build();
//        SDMFile dalvik2 = MockFile.path(base1, "x64").build();
//        SDMFile dalvik3 = MockFile.path(base2, "x86").build();
//        SDMFile dalvik4 = MockFile.path(base2, "x64").build();
//        dalviks.add(dalvik1);
//        dalviks.add(dalvik2);
//        dalviks.add(dalvik3);
//        dalviks.add(dalvik4);
//        dalviksX86.add(dalvik1);
//        dalviksX86.add(dalvik3);
//        dalviksX64.add(dalvik2);
//        dalviksX64.add(dalvik4);
//
//        when(storageManager.getStorages(Location.DALVIK_DEX, true)).thenReturn(Arrays.asList(
//                storageDalvikProfileX861, storageDalvikProfileX641, storageDalvikProfileX862, storageDalvikProfileX642
//        ));
//        when(storageDalvikProfileX861.getFile()).thenReturn(dalvik1);
//        when(storageDalvikProfileX641.getFile()).thenReturn(dalvik2);
//        when(storageDalvikProfileX862.getFile()).thenReturn(dalvik3);
//        when(storageDalvikProfileX642.getFile()).thenReturn(dalvik4);
//
//        when(archHelper.getArchFolderNames()).thenReturn(Arrays.asList("x86", "x64"));
//
//        csiModule = new CSIDalvikDex(fileForensics);
//    }
//
//    @Test
//    @Override
//    public void testJurisdictions() {
//        assertJurisdiction(Location.DALVIK_DEX);
//    }
//
//    @Test
//    public void testDetermineLocation_known() throws Exception {
//        for (SDMFile base : dalviks) {
//            SDMFile testFile1 = MockFile.path(base, UUID.randomUUID().toString()).build();
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            assertNotNull(locationInfo1);
//            assertEquals(Location.DALVIK_DEX, locationInfo1.getLocation());
//            assertEquals(testFile1.getName(), locationInfo1.getPrefixFreePath());
//            assertEquals(base.getPath() + File.separator, locationInfo1.getPrefix());
//            assertTrue(locationInfo1.isBlackListLocation());
//        }
//    }
//
//    @Test
//    public void testDetermineLocation_unknown() throws Exception {
//        for (SDMFile base : dalvikCachesBases) {
//            assertNull(csiModule.matchLocation(MockFile.path(base, UUID.randomUUID().toString()).build()));
//            assertNull(csiModule.matchLocation(MockFile.path(base + "/profiles", UUID.randomUUID().toString()).build()));
//            assertNull(csiModule.matchLocation(MockFile.path(base + "/something64", UUID.randomUUID().toString()).build()));
//        }
//    }
//
//    @Test
//    @Override
//    public void testProcess_hit() {
//        String packageName = "com.test.pkg";
//        for (SDMFile base : dalviks) {
//            Collection<Pair<? extends SDMFile, ? extends SDMFile>> targets = Arrays.asList(
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-1.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-1.apk@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/mnt/expand/uuid/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "mnt@expand@uuid@app@com.test.pkg-12.apk@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk@classes.odex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk@classes.dex.art").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk@classes.oat").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk.odex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk.oat").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/data/app/com.test.pkg-12.apk").build(),
//                            MockFile.path(base, "data@app@com.test.pkg-12.apk.vdex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/com.test.pkg-2.apk").build(),
//                            MockFile.path(base, "system@framework@com.test.pkg-2.jar@classes.odex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/app/com.test.pkg-2.jar").build(),
//                            MockFile.path(base, "system@app@com.test.pkg-2.apk@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/app/Wallet/Wallet.apk").build(),
//                            MockFile.path(base, "system@app@Wallet@Wallet.apk@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/priv-app/NetworkRecommendation/NetworkRecommendation.apk").build(),
//                            MockFile.path(base, "/system@priv-app@NetworkRecommendation@NetworkRecommendation.apk@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/priv-app/NetworkRecommendation/NetworkRecommendation.apk").build(),
//                            MockFile.path(base, "/system@priv-app@NetworkRecommendation@NetworkRecommendation.apk@classes.vdex").build()
//                    )
//            );
//
//            for (Pair<? extends SDMFile, ? extends SDMFile> toHit : targets) {
//                setupApp(packageName, toHit.first);
//                LocationInfo locationInfo = csiModule.matchLocation(toHit.second);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(toHit.second.toString() + " doesn't match " + toHit.first.toString(), ownerInfo.isCurrentlyOwned(), is(true));
//                assertEquals(1, ownerInfo.getOwners().size());
//                assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//            }
//        }
//    }
//
//    @Test
//    public void testProcess_hit_unknown_owner() {
//        String packageName = "com.test.pkg";
//        Collection<Pair<? extends SDMFile, ? extends SDMFile>> targets = new ArrayList<>();
//        for (SDMFile base : dalviksX86) {
//            targets.addAll(Arrays.asList(
//                    new Pair<>(
//                            MockFile.path("/system/framework/x86/boot.art").build(),
//                            MockFile.path(base, "system@framework@boot.art").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x86/boot.oat").build(),
//                            MockFile.path(base, "system@framework@boot.oat").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x86/boot-framework.art").build(),
//                            MockFile.path(base, "system@framework@boot-framework.art").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x86/boot-framework.oat").build(),
//                            MockFile.path(base, "system@framework@boot-framework.oat").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x86/boot-framework.vdex").build(),
//                            MockFile.path(base, "system@framework@boot-framework.vdex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/serviceitems.jar").build(),
//                            MockFile.path(base, "system@framework@serviceitems.jar@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/settings.jar").build(),
//                            MockFile.path(base, "system@framework@settings.jar@classes.dex").build()
//                    )
//            ));
//        }
//        for (SDMFile base : dalviksX64) {
//            targets.addAll(Arrays.asList(
//                    new Pair<>(
//                            MockFile.path("/system/framework/x64/boot.art").build(),
//                            MockFile.path(base, "system@framework@boot.art").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x64/boot.oat").build(),
//                            MockFile.path(base, "system@framework@boot.oat").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x64/boot-framework.art").build(),
//                            MockFile.path(base, "system@framework@boot-framework.art").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x64/boot-framework.oat").build(),
//                            MockFile.path(base, "system@framework@boot-framework.oat").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/x64/boot-framework.vdex").build(),
//                            MockFile.path(base, "system@framework@boot-framework.vdex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/serviceitems.jar").build(),
//                            MockFile.path(base, "system@framework@serviceitems.jar@classes.dex").build()
//                    ),
//                    new Pair<>(
//                            MockFile.path("/system/framework/settings.jar").build(),
//                            MockFile.path(base, "system@framework@settings.jar@classes.dex").build()
//                    )
//            ));
//        }
//        for (Pair<? extends SDMFile, ? extends SDMFile> toHit : targets) {
//            setupApp(packageName, toHit.first);
//            LocationInfo locationInfo = csiModule.matchLocation(toHit.second);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertThat(toHit.second.toString() + " doesn't match " + toHit.first.toString(), ownerInfo.isCurrentlyOwned(), is(true));
//        }
//    }
//
//    @Test
//    @Override
//    public void testProcess_hit_child() {
//
//    }
//
//    @Test
//    public void testProcess_hit_default_unknowns() {
//        String packageName = "com.test.pkg";
//        for (SDMFile base : dalviks) {
//            Collection<Pair<? extends SDMFile, ? extends SDMFile>> targets = Collections.singletonList(
//                    new Pair<>(MockFile.path("").build(), MockFile.path(base, "minimode.dex").build())
//            );
//
//            for (Pair<? extends SDMFile, ? extends SDMFile> toHit : targets) {
//                setupApp(packageName, toHit.first);
//                LocationInfo locationInfo = csiModule.matchLocation(toHit.second);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertTrue(toHit.first.toString(), ownerInfo.isCurrentlyOwned());
//            }
//        }
//    }
//
//    @Test
//    public void testProcess_hit_custom_apk() {
//        SDMPkgInfo packageInfo = mock(SDMPkgInfo.class);
//        when(packageInfo.getPackageName()).thenReturn("eu.thedarken.sdm.test");
//        setupApp(packageInfo.getPackageName(), null);
//
//        for (SDMFile base : dalviks) {
//            SDMFile apk = MockFile.path("/data/app/test-2.apk").build();
//            SDMFile target = MockFile.path(base, "data@app@test-2.apk@classes.dex").build();
//
//            when(ipcFunnel.submit(any(IPCFunnel.ArchiveQuery.class))).thenAnswer((Answer<SDMPkgInfo>) invocation -> {
//                IPCFunnel.ArchiveQuery query = invocation.getArgument(0);
//                if (query.getPath().equals(apk.getPath())) return packageInfo;
//                return null;
//            });
//
//            LocationInfo locationInfo = csiModule.matchLocation(target);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertThat(ownerInfo.toString(), ownerInfo.isCurrentlyOwned(), is(true));
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageInfo.getPackageName(), ownerInfo.getOwners().get(0).getPackageName());
//        }
//    }
//
//    @Test
//    @Override
//    public void testProcess_clutter_hit() {
//        String packageName = "com.test.pkg";
//        setupApp(packageName, null);
//        String prefixFree = UUID.randomUUID().toString();
//        addMarker(packageName, Location.DALVIK_DEX, prefixFree);
//        for (SDMFile base : dalviks) {
//            SDMFile toHit = MockFile.path(base, prefixFree).build();
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            assertNotNull(locationInfo);
//            assertEquals(base.getPath() + File.separator, locationInfo.getPrefix());
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//            assertTrue(ownerInfo.isCurrentlyOwned());
//        }
//    }
//
//    @Test
//    public void testProcess_nothing() {
//        for (SDMFile base : dalviks) {
//            SDMFile testFile1 = MockFile.path(base, UUID.randomUUID().toString()).build();
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            OwnerInfo ownerInfo1 = new OwnerInfo(locationInfo1);
//            csiModule.process(ownerInfo1);
//            ownerInfo1.checkOwnerState(fileForensics);
//            assertTrue(ownerInfo1.isCorpse());
//            assertFalse(ownerInfo1.isCurrentlyOwned());
//        }
//    }
//
//}
