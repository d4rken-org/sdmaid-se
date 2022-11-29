package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;//package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;
//
//
//import android.annotation.TargetApi;
//import android.content.UriPermission;
//import android.os.Build;
//import android.provider.DocumentsContract;
//import android.text.TextUtils;
//
//import java.io.File;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import eu.thedarken.sdmse.App;
//import eu.thedarken.sdmse.tools.BugTrack;
//import eu.thedarken.sdmse.tools.MultiUser;
//import eu.thedarken.sdmse.tools.storage.oswrapper.SAFUriHelper;
//import eu.thedarken.sdmse.tools.storage.oswrapper.manager.StorageManagerX;
//import eu.thedarken.sdmse.tools.storage.oswrapper.manager.VolumeInfoX;
//import timber.log.Timber;
//
//@TargetApi(Build.VERSION_CODES.M)
//public class MarshmallowRepository implements VolumeRootRepository {
//    static final String TAG = App.logTag("MarshmallowRepository");
//    final StorageManagerX storageManagerX;
//    private final MultiUser multiUser;
//
//    public MarshmallowRepository(MultiUser multiUser, StorageManagerX storageManagerX) {
//        this.multiUser = multiUser;
//        this.storageManagerX = storageManagerX;
//    }
//
//    @Nullable
//    @Override
//    public VolumeRoot getVolumeRoot(@NonNull UriPermission uriPermission) {
//        Timber.tag(TAG).d("Attempting getVolumeRoot(%s) via getVolumes() (API23+)", uriPermission);
//        try {
//            String volumeId = SAFUriHelper.getVolumeIdFromTreeUri(uriPermission.getUri());
//            if (TextUtils.isEmpty(volumeId)) {
//                throw new StorageAccessFrameworkException("Can't get volumeId from:" + uriPermission.getUri().toString());
//            }
//            VolumeInfoX targetVolume = null;
//            for (VolumeInfoX volumeInfo : storageManagerX.getVolumes()) {
//                if (isMatch(volumeInfo, volumeId)) {
//                    targetVolume = volumeInfo;
//                    break;
//                }
//            }
//            if (targetVolume == null) {
//                throw new StorageAccessFrameworkException("No matching StorageVolume for:" + uriPermission.toString());
//            }
//
//            int currentUserId = multiUser.getCurrentUserHandle();
//
//            String documentPath = SAFUriHelper.getDocumentPathFromTreeUri(uriPermission.getUri());
//            if (documentPath.endsWith(File.separator)) {
//                documentPath = documentPath.substring(0, documentPath.length() - 1);
//            }
//            File path = new File(targetVolume.getPathForUser(currentUserId), documentPath);
//            String title = targetVolume.getDescription();
//            String documentId = DocumentsContract.getTreeDocumentId(uriPermission.getUri());
//            VolumeRoot volumeRoot = new VolumeRoot(uriPermission, volumeId, documentId, path, title);
//            Timber.tag(TAG).d("Found mapping %s -> %s", targetVolume, volumeRoot);
//            return volumeRoot;
//        } catch (ReflectiveOperationException e) {
//            Timber.tag(TAG).e(e, "VolumeInfoX reflection issue");
//            BugTrack.notify(TAG, e);
//        } catch (StorageAccessFrameworkException e) {
//            Timber.tag(TAG).w(e, "Failed to build VolumeRoot via VolumeInfo.");
//        } catch (Exception e) {
//            Timber.tag(TAG).w(e, "Unknown issue while trying to create VolumeRoot via VolumeInfo");
//        }
//        Timber.tag(TAG).e("getVolumeRoot(%s) via getVolumes() (API23+) failed.", uriPermission);
//        return null;
//    }
//
//    private boolean isMatch(VolumeInfoX volumeInfo, String volumeId) throws ReflectiveOperationException {
//        if (volumeInfo.isPrimary() && volumeId.equals("primary")) {
//            return true;
//        } else if (volumeId.equals(volumeInfo.getFsUuid())) {
//            return true;
//        }
//        if (volumeInfo.getFsUuid() == null) Timber.tag(TAG).w("Missing UUID for %s", volumeInfo);
//
//        return volumeInfo.getPath().getName().equals(volumeId);
//    }
//
//}
