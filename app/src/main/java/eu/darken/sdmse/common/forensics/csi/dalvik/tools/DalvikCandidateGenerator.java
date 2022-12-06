//package eu.darken.sdmse.common.forensics.csi.dalvik;
//
//import java.io.File;
//import java.util.Collection;
//import java.util.HashSet;
//import java.util.LinkedHashSet;
//
//import eu.thedarken.sdm.App;
//import eu.thedarken.sdm.BuildConfig;
//import eu.thedarken.sdm.tools.forensics.Location;
//import eu.thedarken.sdm.tools.forensics.csi.CSIHelper;
//import eu.thedarken.sdm.tools.forensics.csi.CSIProcessor;
//import eu.thedarken.sdm.tools.io.JavaFile;
//import eu.thedarken.sdm.tools.io.SDMFile;
//import eu.thedarken.sdm.tools.storage.Storage;
//import timber.log.Timber;
//
//public class DalvikCandidateGenerator extends CSIHelper {
//    private static final String TAG = App.logTag("CSIDalvikDex", "CandidateGenerator");
//    private static final String[] POSTFIX_EXTENSIONS = new String[]{
//            "@classes.dex",
//            "@classes.odex",
//            "@classes.dex.art",
//            "@classes.oat",
//            "@classes.vdex"
//    };
//    private static final String[] DEX_EXTENSIONS = new String[]{
//            ".dex",
//            ".odex",
//            ".oat",
//            ".art",
//            ".vdex"
//    };
//
//    private static final String[] SOURCE_EXTENSIONS = new String[]{
//            ".apk",
//            ".jar",
//            ".zip"
//    };
//
//    private volatile Collection<SDMFile> sourceStorages = null;
//    private final Object sourceStoragesLock = new Object();
//
//    /**
//     * Hints
//     * https://android.googlesource.com/platform/frameworks/base/+/a029ea1/services/java/com/android/server/pm/PackageManagerService.java#1256
//     * https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java
//     * https://android.googlesource.com/platform/dalvik/+/39e8b7e/dexopt/OptMain.cpp
//     */
//    public DalvikCandidateGenerator(CSIProcessor csiProcessor) {
//        super(csiProcessor);
//    }
//
//    private synchronized Collection<SDMFile> getSourceStorages() {
//        if (sourceStorages == null) {
//            synchronized (sourceStoragesLock) {
//                if (sourceStorages == null) {
//                    sourceStorages = new HashSet<>();
//                    for (Storage storage : getStorageManager().getStorages(Location.APP_APP, true)) {
//                        sourceStorages.add(storage.getFile());
//                    }
//                    for (Storage storage : getStorageManager().getStorages(Location.SYSTEM_APP, true)) {
//                        sourceStorages.add(storage.getFile());
//                    }
//                    for (Storage storage : getStorageManager().getStorages(Location.SYSTEM, true)) {
//                        sourceStorages.add(JavaFile.build(storage.getFile(), "framework"));
//                    }
//                    for (Storage storage : getStorageManager().getStorages(Location.VENDOR, true)) {
//                        sourceStorages.add(JavaFile.build(storage.getFile(), "app"));
//                    }
//                }
//            }
//        }
//        return sourceStorages;
//    }
//
//    private String removePostFix(String fileName, boolean removeExtension) {
//        int postFixCutoff = -1;
//        for (String ext : POSTFIX_EXTENSIONS) {
//            if (fileName.endsWith(ext)) {
//                postFixCutoff = fileName.lastIndexOf(ext);
//                break;
//            }
//        }
//        if (postFixCutoff == -1) {
//            for (String post : DEX_EXTENSIONS) {
//                final int extCutIndex = fileName.lastIndexOf(post);
//                if (extCutIndex == -1) continue;
//                String removedExt = fileName.substring(0, extCutIndex);
//                for (String ext : SOURCE_EXTENSIONS) {
//                    if (removedExt.endsWith(ext)) {
//                        postFixCutoff = extCutIndex;
//                        break;
//                    }
//                }
//                if (postFixCutoff != -1) break;
//            }
//        }
//        String result = fileName;
//        if (postFixCutoff != -1) {
//            // something.jar@classes.dex -> // something.jar
//            result = fileName.substring(0, postFixCutoff);
//            if (removeExtension) {
//                // something.jar -> something
//                int extraExtension = result.lastIndexOf(".");
//                if (extraExtension != -1) result = result.substring(0, extraExtension);
//            }
//        }
//        return result;
//    }
//
//    private SDMFile fileNameToPath(String fileName) {
//        String pathFromName = fileName.replace("@", File.separator);
//        if (!pathFromName.startsWith("/")) pathFromName = File.separator + pathFromName;
//        return JavaFile.absolute(pathFromName);
//    }
//
//    public Collection<SDMFile> getCandidates(SDMFile dexFile) {
//        long start = System.currentTimeMillis();
//        Collection<SDMFile> candidates = new LinkedHashSet<>();
//
//        // Dex file contains the direct path
//        // system@framework@boot.oat -> /system/framework/boot.oat
//        SDMFile nameAsPath = fileNameToPath(dexFile.getName());
//        candidates.add(nameAsPath);
//
//        // data@app@com.test.apk@classes.dex -> /data/app/com.test.apk
//        SDMFile pathWithoutPostFix = fileNameToPath(removePostFix(dexFile.getName(), false));
//        candidates.add(pathWithoutPostFix);
//
//        // data@app@com.test.apk@classes.dex -> /data/app/com.test
//        final SDMFile pathWithoutExtension = fileNameToPath(removePostFix(dexFile.getName(), true));
//        for (String ext : SOURCE_EXTENSIONS) {
//            candidates.add(JavaFile.absolute(pathWithoutExtension.getParent(), pathWithoutExtension.getName() + ext));
//        }
//
//        // Account for architecture in direct and indirect matches
//        // /data/dalvik-cache/x86/system@framework@boot.oat -> /system/framework/x86/boot.oat
//        for (String folder : getArchHelper().getArchFolderNames()) {
//            String argFolder = File.separator + folder + File.separator;
//            if (dexFile.getPath().contains(argFolder)) {
//                candidates.add(JavaFile.absolute(JavaFile.absolute(pathWithoutPostFix.getParent(), argFolder), pathWithoutPostFix.getName()));
//                // Do this for all storages
//                for (SDMFile parent : getSourceStorages()) {
//                    candidates.add(JavaFile.absolute(JavaFile.absolute(parent, argFolder), pathWithoutPostFix.getName()));
//                }
//            }
//        }
//
//        // Source has different extension
//        for (SDMFile parent : getSourceStorages()) {
//            // Target has a direct name match on a different location
//            candidates.add(JavaFile.absolute(parent, dexFile.getName()));
//            // We have something like test.apk@classes.dex and a possible direct match, just different storage
//            candidates.add(JavaFile.absolute(parent, pathWithoutPostFix.getName()));
//            // Webview.apk@classes.dex -> Webview/base.apk
//            candidates.add(JavaFile.absolute(parent, pathWithoutExtension.getName() + File.separator + "base.apk"));
//            // Webview.dex -> Webview/Webview.apk
//            candidates.add(JavaFile.absolute(parent, pathWithoutExtension.getName() + File.separator + pathWithoutPostFix.getName()));
//            for (String extension : SOURCE_EXTENSIONS) {
//                candidates.add(JavaFile.absolute(parent, pathWithoutExtension.getName() + extension));
//            }
//        }
//
//        long stop = System.currentTimeMillis();
//        if (BuildConfig.DEBUG) Timber.tag(TAG).d("Generation time: %d", (stop - start));
//        for (SDMFile p : candidates) Timber.tag(TAG).v("Potential parent: %s", p);
//        return candidates;
//    }
//}
