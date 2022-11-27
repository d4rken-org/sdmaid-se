package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;//package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;
//
//import android.content.ContentResolver;
//import android.content.Context;
//import android.content.Intent;
//import android.content.UriPermission;
//import android.net.Uri;
//import android.os.Build;
//import android.provider.DocumentsContract;
//
//import java.io.IOException;
//import java.lang.reflect.Constructor;
//import java.lang.reflect.Field;
//import java.lang.reflect.InvocationTargetException;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.List;
//
//import javax.inject.Inject;
//
//import androidx.annotation.Keep;
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.annotation.RequiresApi;
//import androidx.documentfile.provider.DocumentFile;
//import timber.log.Timber;
//
///**
// * http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.2_r1/com/android/externalstorage/ExternalStorageProvider.java/
// * http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/5.0.0_r1/android/provider/DocumentsProvider.java/
// * <p>
// * https://android.googlesource.com/platform/frameworks/base/+log/cefba58/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java
// */
//@Keep // https://github.com/d4rken/sdmaid-public/issues/2514
//@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
//public class StorageVolumeMapper {
//    static final String TAG = App.logTag("StorageVolumeMapper");
//    public static final int RW_FLAGSINT = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
//    static final String AUTHORITY = "com.android.externalstorage.documents";
//
//    private final Context context;
//    private final MultiUser multiUser;
//    private boolean isInit = false;
//    private Collection<VolumeRoot> roots = new HashSet<>();
//    private String PATH_TREE;
//    private String PATH_DOCUMENT;
//    private Constructor<? extends DocumentFile> treeDocumentFileClassConstructor;
//    private final List<UriPermission> uriPermissions = new ArrayList<>();
//    private StorageManagerX storageManagerX;
//    private final List<VolumeRootRepository> volumeRootRepositories = new ArrayList<>();
//
//    @Inject
//    public StorageVolumeMapper(@ApplicationContext Context context, MultiUser multiUser) {
//        this.context = context;
//        this.multiUser = multiUser;
//        init();
//    }
//
//    private synchronized boolean init() {
//        if (isInit) return true;
//        if (!ApiHelper.hasLolliPop()) return false;
//
//        try {
//            // FIXME rewrite whole class to use DocumentsContract.java
//            if (ApiHelper.hasAndroidP()) {
//                PATH_TREE = "tree";
//                PATH_DOCUMENT = "document";
//            } else {
//                //noinspection JavaReflectionMemberAccess
//                Field path_tree_field = DocumentsContract.class.getDeclaredField("PATH_TREE");
//                path_tree_field.setAccessible(true);
//                PATH_TREE = (String) path_tree_field.get(null);
//
//                //noinspection JavaReflectionMemberAccess
//                Field path_document_field = DocumentsContract.class.getDeclaredField("PATH_DOCUMENT");
//                path_document_field.setAccessible(true);
//                PATH_DOCUMENT = (String) path_document_field.get(null);
//            }
//
//            // noinspection unchecked
//            Class<? extends DocumentFile> treeDocumentFileClass = (Class<? extends DocumentFile>) Class.forName("androidx.documentfile.provider.TreeDocumentFile");
//            treeDocumentFileClassConstructor = treeDocumentFileClass.getDeclaredConstructor(DocumentFile.class, Context.class, Uri.class);
//            treeDocumentFileClassConstructor.setAccessible(true);
//            // Test construction
//            treeDocumentFileClassConstructor.newInstance(null, context, Uri.parse("content://com.android.externalstorage.documents/tree/primary"));
//            storageManagerX = new StorageManagerX(context);
//
//            volumeRootRepositories.add(new BaseRepository(context, storageManagerX));
//            if (ApiHelper.hasMarshmallow()) volumeRootRepositories.add(new MarshmallowRepository(multiUser, storageManagerX));
//            isInit = true;
//        } catch (ReflectiveOperationException e) {
//            BugTrack.notify(TAG, e);
//            isInit = false;
//        }
//        if (isInit) updateMappings();
//        return isInit;
//    }
//
//    public synchronized void updateMappings() {
//        if (!init()) return;
//
//        Timber.tag(TAG).d("Updating mappings.");
//        roots.clear();
//        uriPermissions.clear();
//        uriPermissions.addAll(context.getContentResolver().getPersistedUriPermissions());
//
//        for (UriPermission uriPermission : uriPermissions) {
//            Timber.tag(TAG).d("Trying to map: %s", uriPermission);
//            VolumeRoot volumeRoot = null;
//            for (VolumeRootRepository volumeRootRepository : volumeRootRepositories) {
//                volumeRoot = volumeRootRepository.getVolumeRoot(uriPermission);
//                if (volumeRoot != null) break;
//            }
//
//            if (volumeRoot != null) {
//                roots.add(volumeRoot);
//                Timber.tag(TAG).d("Mapped %s to %s", volumeRoot, uriPermission);
//            } else {
//                Timber.tag(TAG).e(new MissingVolumeRootException(uriPermission));
//            }
//        }
//    }
//
//    @Nullable
//    public String getLabelForStorage(Storage storage) {
//        if (!init()) return null;
//
//        String label = null;
//        try {
//            for (StorageVolumeX volume : storageManagerX.getVolumeList()) {
//                if (storage.getFile().getPath().equals(volume.getPath())) {
//                    label = volume.getDescription(context);
//                    if (label != null) break;
//                }
//            }
//        } catch (ReflectiveOperationException e) {
//            Timber.tag(TAG).w(e);
//        }
//        if (ApiHelper.hasMarshmallow() && label == null) {
//            try {
//                for (VolumeInfoX volume : storageManagerX.getVolumes()) {
//                    if (storage.getFile().getJavaFile().equals(volume.getPath())) {
//                        label = volume.getDescription();
//                        if (label == null && volume.getDisk() != null) {
//                            label = volume.getDisk().getDescription();
//                        }
//                        if (label != null) break;
//                    }
//                }
//            } catch (ReflectiveOperationException e) {
//                Timber.tag(TAG).w(e);
//            }
//        }
//        return label;
//    }
//
//    /*
//    content://com.android.externalstorage.documents/tree/VOLUMEID/document/DOCUMENTID/<Files>
//     */
//    @NonNull
//    public synchronized Uri getSAFUri(SDMFile file) throws IOException {
//        if (!init()) throw new IllegalStateException();
//        String path = file.getPath();
//        VolumeRoot volumeRoot = null;
//        for (VolumeRoot root : roots) {
//            final String rootPath = root.storagePath.getPath();
//            if (root.storagePath.getPath().equals(file.getPath())) {
//                volumeRoot = root;
//                break;
//            }
//            if (path.startsWith(rootPath) && (volumeRoot == null || rootPath.length() > volumeRoot.storagePath.getPath().length())) {
//                volumeRoot = root;
//            }
//        }
//        if (volumeRoot == null) throw new IOException("No matching (UriPermission/VolumeRoot): " + file.getPath());
//
//        boolean directRootMatch = file.getPath().equals(volumeRoot.storagePath.getAbsolutePath());
//
//        Uri returnUri;
//        Uri.Builder uriBuilder = new Uri.Builder();
//        uriBuilder.scheme(ContentResolver.SCHEME_CONTENT);
//        uriBuilder.authority(AUTHORITY);
//        uriBuilder.appendPath(PATH_TREE);
//        uriBuilder.appendPath(volumeRoot.documentId);
//        uriBuilder.appendPath(PATH_DOCUMENT);
//        if (directRootMatch) {
//            uriBuilder.appendPath(volumeRoot.documentId);
//        } else {
//            String subTree = file.getPath().replace(volumeRoot.storagePath.getAbsolutePath() + "/", "");
//            uriBuilder.appendPath(volumeRoot.documentId + subTree);
//        }
//        returnUri = uriBuilder.build();
//
//        Timber.tag(TAG).v("getUri(): " + file.getPath() + " -> " + returnUri);
//        return returnUri;
//    }
//
//    @Nullable
//    public synchronized SDMFile getFile(Uri uri) {
//        if (!init()) return null;
//        if (!uri.getAuthority().equals(AUTHORITY)) return null;
//        if (!uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) return null;
//        if (!uri.getPath().startsWith("/" + PATH_TREE + "/")) return null;
//        int rootIdPos = uri.getPath().indexOf(":");
//        if (rootIdPos == -1) return null;
//
//        String targetRoot = uri.getPath().substring(0, rootIdPos + 1);
//        VolumeRoot targetVolumeRoot = null;
//        for (VolumeRoot volumeRoot : roots) {
//            String rootStart = "/" + PATH_TREE + "/" + volumeRoot.documentId;
//            if (targetRoot.equals(rootStart)) {
//                targetVolumeRoot = volumeRoot;
//                break;
//            }
//        }
//        if (targetVolumeRoot == null) return null;
//
//        String subPath = uri.getPath().replace(targetRoot, "");
//        String possibleIndirectTarget = "/" + PATH_DOCUMENT + "/" + targetVolumeRoot.documentId;
//        int indirectPathIndex = subPath.indexOf(possibleIndirectTarget);
//        if (indirectPathIndex != -1) {
//            subPath = subPath.substring(indirectPathIndex + possibleIndirectTarget.length());
//        }
//        try {
//            return JavaFile.assertAbsolute(JavaFile.absolute(targetVolumeRoot.storagePath, subPath));
//        } catch (IllegalPathException e) {
//            BugTrack.notify(TAG, e);
//            return null;
//        }
//    }
//
//    @NonNull
//    public Collection<VolumeRoot> getVolumeRoots() {
//        if (!init()) return Collections.emptyList();
//        return roots;
//    }
//}
