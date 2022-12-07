//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//import android.os.Build;
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
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.UUID;
//
//import eu.thedarken.sdm.tools.ApiHelper;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.BaseCSITest;
//import eu.thedarken.sdm.tools.io.JavaFile;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//
//import static junit.framework.Assert.assertFalse;
//import static junit.framework.Assert.assertNotNull;
//import static junit.framework.Assert.assertNull;
//import static junit.framework.Assert.assertTrue;
//import static org.mockito.Mockito.when;
//
//public class CSIDalvikProfileTest extends BaseCSITest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    Collection<SDMFile> dalvikCachesBases = new ArrayList<>();
//    Collection<SDMFile> profiles = new ArrayList<>();
//    @Mock Storage storageDalvikProfile1;
//    @Mock Storage storageDalvikProfile2;
//
//    @Before
//    @Override
//    public void setup() {
//        super.setup();
//        ApiHelper.setApiLevel(Build.VERSION_CODES.LOLLIPOP);
//        SDMFile base1 = JavaFile.absolute("/data/dalvik-cache");
//        SDMFile base2 = JavaFile.absolute("/cache/dalvik-cache");
//        dalvikCachesBases.add(base1);
//        dalvikCachesBases.add(base2);
//        SDMFile profiles1 = JavaFile.build(base1, "profiles");
//        SDMFile profiles2 = JavaFile.build(base2, "profiles");
//        profiles.add(profiles1);
//        profiles.add(profiles2);
//
//        when(storageManager.getStorages(Location.DALVIK_PROFILE, true)).thenReturn(Arrays.asList(storageDalvikProfile1, storageDalvikProfile2));
//        when(storageDalvikProfile1.getFile()).thenReturn(profiles1);
//        when(storageDalvikProfile2.getFile()).thenReturn(profiles2);
//
//        csiModule = new CSIDalvikProfile(fileForensics);
//    }
//
//    @Test
//    @Override
//    public void testJurisdictions() {
//        assertJurisdiction(Location.DALVIK_PROFILE);
//    }
//
//    @Test
//    public void testDetermineLocation_known() throws Exception {
//        for (SDMFile base : profiles) {
//            SDMFile testFile1 = JavaFile.build(base, UUID.randomUUID().toString());
//            LocationInfo locationInfo1 = csiModule.matchLocation(testFile1);
//            assertNotNull(locationInfo1);
//            assertEquals(Location.DALVIK_PROFILE, locationInfo1.getLocation());
//            assertEquals(testFile1.getName(), locationInfo1.getPrefixFreePath());
//            assertEquals(base.getPath() + File.separator, locationInfo1.getPrefix());
//            assertTrue(locationInfo1.isBlackListLocation());
//        }
//    }
//
//    @Test
//    public void testDetermineLocation_unknown() throws Exception {
//        for (SDMFile base : dalvikCachesBases) {
//            assertNull(csiModule.matchLocation(JavaFile.build(base, UUID.randomUUID().toString())));
//            assertNull(csiModule.matchLocation(JavaFile.build(base + "/arm", UUID.randomUUID().toString())));
//            assertNull(csiModule.matchLocation(JavaFile.build(base + "/arm64", UUID.randomUUID().toString())));
//        }
//    }
//
//    @Test
//    public void testProcess_hit() {
//        String packageName = "eu.thedarken.sdm.test";
//        setupApp(packageName, null);
//        for (SDMFile base : profiles) {
//            SDMFile toHit = JavaFile.build(base, "eu.thedarken.sdm.test");
//
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
//        addMarker(packageName, Location.DALVIK_PROFILE, prefixFree);
//        for (SDMFile base : profiles) {
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
//        for (SDMFile base : profiles) {
//            SDMFile testFile1 = JavaFile.build(base, UUID.randomUUID().toString());
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
