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
//import java.util.Collections;
//import java.util.UUID;
//
//import eu.darken.sdmse.common.forensics.csi.BaseCSITest;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
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
//public class CSIDataSDExt2Test extends BaseCSITest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    @Mock Storage storageData;
//    @Mock Storage storageDataSystem;
//    @Mock Storage storageDataSystemCE;
//    @Mock Storage storageDataSystemDE;
//    @Mock Storage storageDataSDExt2;
//    SDMFile basePath = JavaFile.absolute("/data/sdext2");
//
//    @Before
//    @Override
//    public void setup() {
//        super.setup();
//        when(storageManager.getStorages(Location.DATA, true)).thenReturn(Collections.singleton(storageData));
//        when(storageData.getFile()).thenReturn(JavaFile.absolute("/data"));
//        when(storageManager.getStorages(Location.DATA_SYSTEM, true)).thenReturn(Collections.singleton(storageDataSystem));
//        when(storageDataSystem.getFile()).thenReturn(JavaFile.absolute("/data/system"));
//        when(storageManager.getStorages(Location.DATA_SYSTEM_CE, true)).thenReturn(Collections.singleton(storageDataSystemCE));
//        when(storageDataSystemCE.getFile()).thenReturn(JavaFile.absolute("/data/system_ce"));
//        when(storageManager.getStorages(Location.DATA_SYSTEM_DE, true)).thenReturn(Collections.singleton(storageDataSystemDE));
//        when(storageDataSystemDE.getFile()).thenReturn(JavaFile.absolute("/data/system_de"));
//        when(storageManager.getStorages(Location.DATA_SDEXT2, true)).thenReturn(Collections.singleton(storageDataSDExt2));
//        when(storageDataSDExt2.getFile()).thenReturn(JavaFile.absolute("/data/sdext2"));
//        csiModule = new CSIDataSDExt2(fileForensics);
//    }
//
//    @Test
//    @Override
//    public void testJurisdictions() {
//        assertJurisdiction(Location.DATA_SDEXT2);
//    }
//
//    @Test
//    public void testDetermineLocation_known() throws Exception {
//        SDMFile testFile1 = JavaFile.build(basePath, UUID.randomUUID().toString());
//        LocationInfo locationInfo = csiModule.matchLocation(testFile1);
//        assertNotNull(locationInfo);
//        assertEquals(Location.DATA_SDEXT2, locationInfo.getLocation());
//        assertEquals(testFile1.getName(), locationInfo.getPrefixFreePath());
//        assertEquals(basePath.getPath() + File.separator, locationInfo.getPrefix());
//        assertFalse(locationInfo.isBlackListLocation());
//    }
//
//    @Test
//    public void testDetermineLocation_unknown() throws Exception {
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data/system_ce", UUID.randomUUID().toString())));
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data/system_de", UUID.randomUUID().toString())));
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data/systems", UUID.randomUUID().toString())));
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data/system ", UUID.randomUUID().toString())));
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data/sdext2 ", UUID.randomUUID().toString())));
//        assertNull(csiModule.matchLocation(JavaFile.absolute("/data", UUID.randomUUID().toString())));
//    }
//
//    @Test
//    public void testProcess_hit() {
//
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
//
//    }
//
//    @Test
//    public void testProcess_nothing() {
//        SDMFile testFile1 = JavaFile.build(basePath, UUID.randomUUID().toString());
//        LocationInfo locationInfo = csiModule.matchLocation(testFile1);
//
//        OwnerInfo ownerInfo = new OwnerInfo(locationInfo);
//        csiModule.process(ownerInfo);
//        ownerInfo.checkOwnerState(fileForensics);
//
//        assertFalse(ownerInfo.isCorpse());
//        assertTrue(ownerInfo.isCurrentlyOwned());
//    }
//
//}
