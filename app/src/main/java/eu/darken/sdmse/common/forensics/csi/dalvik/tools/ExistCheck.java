//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//
//import java.util.Collection;
//
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSIHelper;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.SDMFile;
//
//public class ExistCheck extends CSIHelper {
//    protected ExistCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    public boolean check(OwnerInfo ownerInfo, Collection<SDMFile> candidates) {
//        for (SDMFile can : candidates) {
//            if (can.getJavaFile().exists()) {
//                ownerInfo.setUnknownOwner(true);
//                return true;
//            }
//        }
//        return false;
//    }
//}
