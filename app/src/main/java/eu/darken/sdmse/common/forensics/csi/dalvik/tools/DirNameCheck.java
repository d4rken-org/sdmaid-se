//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//import eu.thedarken.sdm.tools.forensics.Owner;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSICheck;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.FileOpsHelper;
//
//
//public class DirNameCheck extends CSICheck {
//
//    public DirNameCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    @Override
//    public boolean check(OwnerInfo ownerInfo) {
//        String potPkg = FileOpsHelper.getFirstDirName(ownerInfo.getLocationInfo().getPrefixFreePath());
//        if (isInstalled(potPkg)) {
//            ownerInfo.addOwner(new Owner(potPkg));
//            return true;
//        }
//        return false;
//    }
//}
