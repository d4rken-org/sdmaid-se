//package eu.darken.sdmse.common.forensics.csi.privs;
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
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.io.JavaFile;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Mount;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//import static junit.framework.Assert.assertFalse;
//import static junit.framework.Assert.assertNotNull;
//import static junit.framework.Assert.assertNull;
//import static junit.framework.Assert.assertTrue;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.when;
//
//public class CSIAppAsecTest extends BaseCSITest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    Collection<SDMFile> bases = new ArrayList<>();
//    @Mock Storage storageAppAsec;
//
//    @Before
//    @Override
//    public void setup() {
//        super.setup();
//        SDMFile appLib1 = JavaFile.absolute("/data/app-asec");
//        bases.add(appLib1);
//
//        when(storageManager.getStorages(Location.APP_ASEC, true)).thenReturn(Collections.singletonList(
//                storageAppAsec
//        ));
//        when(storageAppAsec.getFile()).thenReturn(appLib1);
//
//        csiModule = new CSIAppAsec(fileForensics);
//    }
//
//    @Test
//    @Override
//    public void testJurisdictions() {
//        assertJurisdiction(Location.APP_ASEC);
//    }
//
//    @Test
//    public void testDetermineLocation_known() throws Exception {
//        for (SDMFile base : bases) {
//            SDMFile testFile1 = JavaFile.build(base, UUID.randomUUID().toString());
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            assertNotNull(locationInfo1);
//            assertEquals(Location.APP_ASEC, locationInfo1.getLocation());
//            assertEquals(testFile1.getName(), locationInfo1.getPrefixFreePath());
//            assertEquals(base.getPath() + File.separator, locationInfo1.getPrefix());
//            assertTrue(locationInfo1.isBlackListLocation());
//        }
//    }
//
//    @Test
//    public void testDetermineLocation_unknown() throws Exception {
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data", UUID.randomUUID().toString())));
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data/lib", UUID.randomUUID().toString())));
//    }
//
//    @Test
//    public void testProcess_hit() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, null);
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-1.asec"));
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-12.asec"));
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-123.asec"));
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
//    public void testProcess_hit_mount() {
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-1.asec"));
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-12.asec"));
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-123.asec"));
//        }
//        for (SDMFile toHit : targets) {
//            Mount mount = mock(Mount.class);
//            when(mount.getMountpoint()).thenReturn(toHit);
//            mountCollection.add(mount);
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(ownerInfo.isCurrentlyOwned());
//            assertTrue(ownerInfo.hasUnknownOwner());
//            assertEquals(0, ownerInfo.getOwners().size());
//        }
//    }
//
//    @Test
//    public void testProcess_hit_mount_2() {
//        Collection<SDMFile> targets = new ArrayList<>();
//        for (SDMFile base : bases) {
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-1.asec"));
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-12.asec"));
//            targets.add(JavaFile.build(base, "eu.thedarken.sdm.test-123.asec"));
//        }
//        for (SDMFile toHit : targets) {
//            Mount mount = mock(Mount.class);
//            when(mount.getMountpoint()).thenReturn(JavaFile.absolute(toHit.getPath().replace(".asec", "")));
//            mountCollection.add(mount);
//            LocationInfo locationInfo = csiModule.matchLocation(toHit);
//            OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//
//            csiModule.process(ownerInfo);
//            ownerInfo.checkOwnerState(fileForensics);
//
//            assertTrue(ownerInfo.isCurrentlyOwned());
//            assertTrue(ownerInfo.hasUnknownOwner());
//            assertEquals(0, ownerInfo.getOwners().size());
//        }
//    }
//
//    @Test
//    public void testProcess_hit_child() {
//
//    }
//
//    @Test
//    @Override
//    public void testProcess_clutter_hit() {
//        String packageName = "com.test.pkg";
//        setupApp(packageName, null);
//        String prefixFree = UUID.randomUUID().toString();
//        addMarker(packageName, Location.APP_ASEC, prefixFree);
//        for (SDMFile base : bases) {
//            SDMFile toHit = JavaFile.build(base, prefixFree);
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
//            SDMFile testFile1 = JavaFile.build(base, UUID.randomUUID().toString());
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            OwnerInfo ownerInfo1 = new OwnerInfo(locationInfo1);
//            csiModule.process(ownerInfo1);
//            ownerInfo1.checkOwnerState(fileForensics);
//            assertTrue(ownerInfo1.isCorpse());
//            assertFalse(ownerInfo1.isCurrentlyOwned());
//        }
//    }
//}