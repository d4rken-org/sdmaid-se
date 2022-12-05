//package eu.darken.sdmse.common.forensics.csi;
//
//import java.util.Collection;
//
//import eu.thedarken.sdm.tools.clutter.Marker;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSICheck;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.FileOpsHelper;
//
//public class ClutterCheck extends CSICheck {
//
//    public ClutterCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    @Override
//    public boolean check(OwnerInfo ownerInfo) {
//        String dirName = FileOpsHelper.getFirstDirName(ownerInfo.getLocationInfo().getPrefixFreePath());
//        Collection<Marker.Match> matches = getClutterRepository().match(ownerInfo.getLocationInfo().getLocation(), dirName);
//        ownerInfo.addOwners(matches);
//        return matches.size() > 0;
//    }
//}
