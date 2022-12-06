//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnit;
//import org.mockito.junit.MockitoRule;
//
//import eu.thedarken.sdm.tools.apps.IPCFunnel;
//import eu.thedarken.sdm.tools.forensics.LocationInfo;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//
//import static eu.thedarken.sdm.tools.OwnerMatcher.ownerPkgEq;
//import static org.hamcrest.MatcherAssert.assertThat;
//import static org.hamcrest.core.Is.is;
//import static org.mockito.Mockito.mock;
//import static org.mockito.Mockito.never;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//public class DirNameCheckTest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    @Mock CSIProcessor csiProcessor;
//    @Mock IPCFunnel ipcFunnel;
//    DirNameCheck check;
//
//    @Before
//    public void setup() {
//        check = new DirNameCheck(csiProcessor);
//    }
//
//    @Test
//    public void testNotInstalled() {
//        OwnerInfo ownerInfo = mock(OwnerInfo.class);
//        LocationInfo locationInfo = mock(LocationInfo.class);
//        when(locationInfo.getPrefixFreePath()).thenReturn("com.mxtech.ffmpeg.x86");
//        when(ownerInfo.getLocationInfo()).thenReturn(locationInfo);
//
//        when(csiProcessor.isInstalled("com.mxtech.ffmpeg.x86")).thenReturn(false);
//
//        assertThat(check.check(ownerInfo), is(false));
//        verify(ownerInfo, never()).addOwner(ownerPkgEq("com.mxtech.ffmpeg.x86"));
//    }
//
//    @Test
//    public void testBaseMatch() {
//        OwnerInfo ownerInfo = mock(OwnerInfo.class);
//        LocationInfo locationInfo = mock(LocationInfo.class);
//        when(locationInfo.getPrefixFreePath()).thenReturn("com.mxtech.ffmpeg.x86");
//        when(ownerInfo.getLocationInfo()).thenReturn(locationInfo);
//
//
//        when(csiProcessor.isInstalled("com.mxtech.ffmpeg.x86")).thenReturn(true);
//
//        assertThat(check.check(ownerInfo), is(true));
//        verify(ownerInfo).addOwner(ownerPkgEq("com.mxtech.ffmpeg.x86"));
//    }
//}
