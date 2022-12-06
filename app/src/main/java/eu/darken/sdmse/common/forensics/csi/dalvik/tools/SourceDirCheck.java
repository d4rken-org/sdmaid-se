//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//
//import java.util.Collection;
//
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.forensics.Owner;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSIHelper;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.SDMFile;
//
//public class SourceDirCheck extends CSIHelper {
//
//    protected SourceDirCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    public boolean check(OwnerInfo ownerInfo, Collection<SDMFile> candidates) {
//        for (SDMPkgInfo packageInfo : getApps()) {
//            if (packageInfo.getApplicationInfo() == null) continue;
//            for (SDMFile candidate : candidates) {
//                // Don't reverse the EQUALS because sourceDir can be null in some cases
//                if (candidate.getPath().equals(packageInfo.getSourceDir())) {
//                    ownerInfo.addOwner(new Owner(packageInfo.getPackageName()));
//                    return true;
//                }
//            }
//        }
//        return false;
//    }
//}
