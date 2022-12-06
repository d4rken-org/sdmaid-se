//package eu.darken.sdmse.common.forensics.csi.privs;
//
//import android.content.pm.ApplicationInfo;
//
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnit;
//import org.mockito.junit.MockitoRule;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.UUID;
//
//import eu.darken.sdmse.common.forensics.csi.BaseCSITest;
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//import testhelper.MockFile;
//
//import static junit.framework.Assert.assertFalse;
//import static junit.framework.Assert.assertNotNull;
//import static junit.framework.Assert.assertNull;
//import static junit.framework.Assert.assertTrue;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//public class CSIAppLibTest extends BaseCSITest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    Collection<SDMFile> bases = new ArrayList<>();
//    @Mock Storage storageAppLib1;
//
//    @Before
//    @Override
//    public void setup() {
//        super.setup();
//        SDMFile appLib1 = MockFile.path("/data/app-lib").build();
//        bases.add(appLib1);
//
//        when(storageManager.getStorages(Location.APP_LIB, true)).thenReturn(Collections.singletonList(
//                storageAppLib1
//        ));
//        when(storageAppLib1.getFile()).thenReturn(appLib1);
//
//        csiModule = new CSIAppLib(fileForensics);
//    }
//
//    @Test
//    @Override
//    public void testJurisdictions() {
//        assertJurisdiction(Location.APP_LIB);
//    }
//
//    @Test
//    public void testDetermineLocation_known() throws Exception {
//        for (SDMFile base : bases) {
//            SDMFile testFile1 = MockFile.path(base, UUID.randomUUID().toString()).build();
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            assertNotNull(locationInfo1);
//            assertEquals(Location.APP_LIB, locationInfo1.getLocation());
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
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-1").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-12").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-123").build());
//        }
//        for (SDMFile toHit : targets) {
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(ownerInfo.isCurrentlyOwned());
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
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-1/something.so").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-1/abc/def").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-12/abc/def").build());
//            targets.add(MockFile.path(base, "eu.thedarken.sdm.test-123/abc/def").build());
//        }
//        for (SDMFile toHit : targets) {
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(ownerInfo.isCurrentlyOwned());
//            assertEquals(1, ownerInfo.getOwners().size());
//            assertEquals(packageName, ownerInfo.getOwners().get(0).getPackageName());
//        }
//    }
//
//    @Test
//    @Override
//    public void testProcess_clutter_hit() {
//        String packageName = "com.test.pkg";
//        setupApp(packageName, null);
//        String prefixFree = UUID.randomUUID().toString();
//        addMarker(packageName, Location.APP_LIB, prefixFree);
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
//    public void testProcess_hit_nativeLibraryDir() {
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(MockFile.path(base, "blabla").build());
//            targets.add(MockFile.path(base, "blabla/something.so").build());
//
//            SDMPkgInfo packageInfo = mock(SDMPkgInfo.class);
//            when(packageInfo.getPackageName()).thenReturn("test");
//            ApplicationInfo applicationInfo = new ApplicationInfo();
//            when(packageInfo.getApplicationInfo()).thenReturn(applicationInfo);
//            applicationInfo.nativeLibraryDir = base.getPath() + File.separator + "blabla";
//            appMap.put(packageInfo.getPackageName(), packageInfo);
//            for (SDMFile toHit : targets) {
//                LocationInfo locationInfo = csiModule.matchLocation(toHit);
//                OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//                csiModule.process(ownerInfo);
//                ownerInfo.checkOwnerState(fileForensics);
//
//                assertTrue(ownerInfo.isCurrentlyOwned());
//                assertEquals(1, ownerInfo.getOwners().size());
//                assertEquals(packageInfo.getPackageName(), ownerInfo.getOwners().get(0).getPackageName());
//            }
//        }
//    }
//
//}