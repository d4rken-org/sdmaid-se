//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//
//import java.util.Collection;
//
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.tools.apps.IPCFunnel;
//import eu.thedarken.sdm.tools.apps.SDMPkgInfo;
//import eu.thedarken.sdm.tools.forensics.Owner;
//import eu.thedarken.sdm.tools.forensics.OwnerInfo;
//import eu.thedarken.sdm.tools.forensics.csi.CSIHelper;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import timber.log.Timber;
//
//public class ApkCheck extends CSIHelper {
//    private static final String TAG = App.logTag("CSIDalvikDex", "ApkCheck");
//
//    protected ApkCheck(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    public boolean check(OwnerInfo ownerInfo, Collection<SDMFile> candidates) {
//        for (SDMFile can : candidates) {
//            // We don't want to check .jar files for their installed state.
//            if (can.getName().endsWith(".apk")) {
//                SDMPkgInfo pkgInfo = getIPCFunnel().submit(new IPCFunnel.ArchiveQuery(can, 0));
//                if (pkgInfo != null) {
//                    Timber.tag(TAG).d("Archive packagename: %s", pkgInfo.getPackageName());
//                    ownerInfo.addOwner(new Owner(pkgInfo.getPackageName()));
//                    if (pkgInfo.getPackageName().startsWith("com.google.android.gms.")) {
//                        /**
//                         * /data/dalvik-cache/arm64/system@product@priv-app@PrebuiltGmsCore@app_chimera@m@PrebuiltGmsCoreRvc_DynamiteModulesC.apk@classes.vdex
//                         * to
//                         * /system/product/priv-app/PrebuiltGmsCore/app_chimera/m/PrebuiltGmsCoreRvc_DynamiteModulesC.apk
//                         *
//                         * /data/dalvik-cache/arm64/system@product@priv-app@PrebuiltGmsCore@m@independent@AndroidPlatformServices.apk@classes.dex
//                         * to
//                         * /system/product/priv-app/PrebuiltGmsCore/m/independent/AndroidPlatformServices.apk
//                         */
//                        ownerInfo.addOwner(new Owner("com.google.android.gms"));
//                    }
//                    return true;
//                }
//            }
//        }
//        return false;
//    }
//}
