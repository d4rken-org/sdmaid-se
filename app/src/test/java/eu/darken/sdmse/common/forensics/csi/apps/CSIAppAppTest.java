//package eu.darken.sdmse.common.forensics.csi.apps;
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
//import java.util.List;
//import java.util.UUID;
//
//import eu.thedarken.sdm.tools.apps.IPCFunnel;
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.clutter.Marker;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.BaseCSITest;
//import eu.thedarken.sdm.tools.io.JavaFile;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//import testhelper.MockFile;
//
//import static junit.framework.Assert.assertFalse;
//import static junit.framework.Assert.assertNotNull;
//import static junit.framework.Assert.assertNull;
//import static junit.framework.Assert.assertTrue;
//import static org.hamcrest.core.Is.is;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//public class CSIAppAppTest extends BaseCSITest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    Collection<SDMFile> bases = new ArrayList<>();
//    @Mock Storage storageAppApp;
//
//    @Before
//    @Override
//    public void setup() {
//        super.setup();
//        SDMFile appApp1 = MockFile.path("/data/app").build();
//        bases.add(appApp1);
//
//        when(storageManager.getStorages(Location.APP_APP, true)).thenReturn(Collections.singletonList(
//                storageAppApp
//        ));
//        when(storageAppApp.getFile()).thenReturn(appApp1);
//
//        csiModule = new CSIAppApp(fileForensics);
//    }
//
//    @Test
//    @Override
//    public void testJurisdictions() {
//        assertJurisdiction(Location.APP_APP);
//    }
//
//    @Test
//    public void testDetermineLocation_known() throws Exception {
//        for (SDMFile base : bases) {
//            SDMFile testFile1 = MockFile.path(base, UUID.randomUUID().toString()).build();
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            assertNotNull(locationInfo1);
//            assertEquals(Location.APP_APP, locationInfo1.getLocation());
//            assertEquals(testFile1.getName(), locationInfo1.getPrefixFreePath());
//            assertEquals(base.getPath() + File.separator, locationInfo1.getPrefix());
//            assertTrue(locationInfo1.isBlackListLocation());
//        }
//    }
//
//    @Test
//    public void testDetermineLocation_unknown() throws Exception {
//        assertNull(csiModule.matchLocation(MockFile.path("/data", UUID.randomUUID().toString()).build()));
//        assertNull(csiModule.matchLocation(MockFile.path("/data/lib", UUID.randomUUID().toString()).build()));
//    }
//
//    @Test
//    public void testProcess_hit() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, null);
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-1.apk").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-12.apk").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-123.apk").build());
//
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-1").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-12").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-123").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-RLEuLDrRIaICTBfF4FhaFg==/base.apk").build());
//        }
//        for (SDMFile toHit : targets) {
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(toHit.toString(), ownerInfo.isCurrentlyOwned());
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//        }
//    }
//
//    @Test
//    public void testProcess_hit_child() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, null);
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-123/abc").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-123/abc/def").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-RLEuLDrRIaICTBfF4FhaFg==/abc/def").build());
//        }
//        for (SDMFile toHit : targets) {
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(toHit.toString(), ownerInfo.isCurrentlyOwned());
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//        }
//    }
//
//    @Test
//    public void testProcess_hit_archiveinfo() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, null);
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(MockFile.path(base, "test.apk").build());
//        }
//        for (SDMFile toHit : targets) {
//            SDMPkgInfo packageInfo = mock(SDMPkgInfo.class);
//            when(packageInfo.getPackageName()).thenReturn(packageName);
//
//            IPCFunnel.ArchiveQuery query = new IPCFunnel.ArchiveQuery(toHit, 0);
//            when(ipcFunnel.submit(query)).thenReturn(packageInfo);
//
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            verify(ipcFunnel).submit(query);
//
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(ownerInfo.isCurrentlyOwned());
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//        }
//    }
//
//    @Test
//    public void testProcess_hit_nested_archiveinfo() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, null);
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(MockFile.path(base, "ApiDemos").build());
//        }
//        List<String> validApkNames = Arrays.asList(
//                "ApiDemos.apk",
//                "base.apk"
//        );
//        for (SDMFile target : targets) {
//            for (String apkName : validApkNames) {
//                SDMPkgInfo packageInfo = mock(SDMPkgInfo.class);
//                when(packageInfo.getPackageName()).thenReturn(packageName);
//
//                final SDMFile apkFile = MockFile.path(target, apkName).build();
//
//                when(ipcFunnel.submit(any(IPCFunnel.ArchiveQuery.class))).thenAnswer((Answer<SDMPkgInfo>) invocation -> {
//                    IPCFunnel.ArchiveQuery query = invocation.getArgument(0);
//                    if (query.getPath().equals(apkFile.getPath()) && query.getFlags() == 0) return packageInfo;
//                    else return null;
//                });
//
//                LocationInfo locationInfo = csiModule.matchLocation(apkFile);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertTrue(apkFile.toString(), ownerInfo.isCurrentlyOwned());
//                assertEquals(1, ownerInfo.getOwners().size());
//                assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//            }
//        }
//    }
//
//    @Test
//    @Override
//    public void testProcess_clutter_hit() {
//        String packageName = "com.test.pkg";
//        setupApp(packageName, null);
//        String prefixFree = UUID.randomUUID().toString();
//        addMarker(packageName, Location.APP_APP, prefixFree);
//        for (SDMFile base : bases) {
//            SDMFile toHit = MockFile.path(base, prefixFree).build();
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            assertNotNull(locationInfo);
//            assertEquals(base.getPath() + File.separator, locationInfo.getPrefix());
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            verify(clutterRepository).match(locationInfo.getLocation(), locationInfo.getPrefixFreePath());
//
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//            assertTrue(ownerInfo.isCurrentlyOwned());
//        }
//    }
//
//    @Test
//    public void testProcess_oddones() {
//        String packageName = "com.forpda.lp";
//        setupApp(packageName, null);
//        String prefixFree = "something-123.odex";
//        for (SDMFile base : bases) {
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
//            assertTrue(ownerInfo.getOwners().get(0).hasFlag(Marker.Flag.CUSTODIAN));
//        }
//    }
//
//    @Test
//    public void testProcess_nothing() {
//        for (SDMFile base : bases) {
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
//    @Test
//    public void testStrictMatching_no_false_positive_dir() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, JavaFile.absolute("/data/app", "eu.thedarken.sdm.test-2/base.apk"));
//
//        for (SDMFile base : bases) {
//            {
//                // Stale and shouldn't have an owner
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-1").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(false));
//                assertThat(ownerInfo.getOwners().size(), is(0));
//            }
//            {
//                // Stale and shouldn't have an owner
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-1/base.apk").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(false));
//                assertThat(ownerInfo.getOwners().size(), is(0));
//            }
//
//            {
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-2").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(true));
//                assertThat(ownerInfo.getOwners().size(), is(1));
//                assertThat(ownerInfo.getOwners().get(0).getPackageName(), is(packageName));
//            }
//            {
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-2/base.apk").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(true));
//                assertThat(ownerInfo.getOwners().size(), is(1));
//                assertThat(ownerInfo.getOwners().get(0).getPackageName(), is(packageName));
//            }
//        }
//    }
//
//    @Test
//    public void testStrictMatching_no_false_positive_file() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, JavaFile.absolute("/data/app", "eu.thedarken.sdm.test-2.apk"));
//
//        for (SDMFile base : bases) {
//            {
//                // Stale and shouldn't have an owner
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-1").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(false));
//                assertThat(ownerInfo.getOwners().size(), is(0));
//            }
//            {
//                // Stale and shouldn't have an owner
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-1/base.apk").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(false));
//                assertThat(ownerInfo.getOwners().size(), is(0));
//            }
//
//            {
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-2").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(true));
//                assertThat(ownerInfo.getOwners().size(), is(1));
//                assertThat(ownerInfo.getOwners().get(0).getPackageName(), is(packageName));
//            }
//            {
//                SDMFile toHit = MockFile.path(base, "eu.thedarken.sdm.test-2/base.apk").build();
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertThat(ownerInfo.isCurrentlyOwned(), is(true));
//                assertThat(ownerInfo.getOwners().size(), is(1));
//                assertThat(ownerInfo.getOwners().get(0).getPackageName(), is(packageName));
//            }
//        }
//    }
//
//}