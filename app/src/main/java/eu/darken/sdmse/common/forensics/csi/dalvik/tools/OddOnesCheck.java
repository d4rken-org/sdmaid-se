//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.tools.RunTimeChecker;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSICheck;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import timber.log.Timber;
//
//public class OddOnesCheck extends CSICheck {
//    private static final String TAG = App.logTag("CSIDalvikDex", "OddOnesCheck");
//    private volatile Runtime runtimeInfo = null;
//    private final Object runtimeInfoLock = new Object();
//
//    protected OddOnesCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    private synchronized Runtime getRuntimeInfo() {
//        if (runtimeInfo == null) {
//            synchronized (runtimeInfoLock) {
//                if (runtimeInfo == null) runtimeInfo = RunTimeChecker.buildRuntimeInfo();
//            }
//        }
//        return runtimeInfo;
//    }
//
//    public boolean check(OwnerInfo ownerInfo) {
//        String fileName = ownerInfo.getItem().getName();
//        boolean unknownOwner = false;
//        if (fileName.equals("minimode.dex")) {
//            unknownOwner = true;
//        } else if (getRuntimeInfo().getType() == Runtime.Type.ART) {
//            if (fileName.contains("boot.art") || fileName.contains("boot.oat")) {
//                unknownOwner = true;
//            }
//        }
//        ownerInfo.setUnknownOwner(unknownOwner);
//        if (unknownOwner) Timber.tag(TAG).d("Default unknown owner: %s", ownerInfo.getItem());
//
//        return unknownOwner;
//    }
//}
