//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//
//import java.io.File;
//import java.util.Collection;
//
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.forensics.Owner;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSIHelper;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.FileOpsHelper;
//import eu.thedarken.sdm.tools.io.JavaFile;
//import eu.thedarken.sdm.tools.io.SDMFile;
//
//public class CustomDexOptCheck extends CSIHelper {
//    protected CustomDexOptCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    public boolean check(OwnerInfo ownerInfo, Collection<SDMFile> candidates) {
//        // Custom apk/jar subfile that has been optimized manually
//        // https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexFile.java
//        if (ownerInfo.getItem().getName().contains("@")) {
//            String trunk = File.separator + ownerInfo.getItem().getName().replace("@", File.separator);
//            String firstSlice = null;
//            while (trunk != null) {
//                int slicePOI = trunk.lastIndexOf(File.separator);
//                if (slicePOI != -1 && slicePOI < trunk.length()) {
//                    String poi = trunk.substring(slicePOI + 1, trunk.length());
//                    if (poi.length() > 0) {
//                        for (SDMPkgInfo p : getApps()) {
//                            if (p.getPackageName().equals(poi)) {
//                                ownerInfo.addOwner(new Owner(p.getPackageName()));
//                                return true;
//                            }
//                        }
//                    }
//                }
//                trunk = FileOpsHelper.chopOffLast(trunk);
//                if (firstSlice == null) {
//                    // We expect this to be a packagename
//                    // e.g. data@app@ ^ eu.thedarken.sdm-1.apk ^ @classes.dex
//                    firstSlice = trunk;
//                }
//            }
//            if (ownerInfo.getOwners().isEmpty() && firstSlice != null) {
//                candidates.add(JavaFile.absolute(firstSlice));
//            }
//        }
//        return false;
//    }
//}
