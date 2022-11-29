package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;//package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;
//
//
//import android.annotation.TargetApi;
//import android.content.Context;
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
//import eu.thedarken.sdmse.tools.storage.oswrapper.SAFUriHelper;
//import eu.thedarken.sdmse.tools.storage.oswrapper.manager.StorageManagerX;
//import eu.thedarken.sdmse.tools.storage.oswrapper.manager.StorageVolumeX;
//import timber.log.Timber;
//
//@TargetApi(Build.VERSION_CODES.KITKAT)
//public class BaseRepository implements VolumeRootRepository {
//    static final String TAG = App.logTag("StorageVolumeRepository");
//    final Context context;
//    final StorageManagerX storageManagerX;
//
//    public BaseRepository(Context context, StorageManagerX storageManagerX) {
//        this.context = context;
//        this.storageManagerX = storageManagerX;
//    }
//
//    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//    @Nullable
//    @Override
//    public VolumeRoot getVolumeRoot(@NonNull UriPermission uriPermission) {
//        Timber.tag(TAG).d("Attempting getVolumeRoot(%s) via getVolumeList().", uriPermission);
//        try {
//            String volumeId = SAFUriHelper.getVolumeIdFromTreeUri(uriPermission.getUri());
//            if (TextUtils.isEmpty(volumeId)) {
//                throw new StorageAccessFrameworkException("Can't get volumeId from:" + uriPermission.getUri().toString());
//            }
//            StorageVolumeX targetVolume = null;
//            for (StorageVolumeX storageVolume : storageManagerX.getVolumeList()) {
//                Timber.tag(TAG).d("StorageVolumeX: %s", storageVolume);
//                if (isMatch(storageVolume, volumeId)) {
//                    targetVolume = storageVolume;
//                    break;
//                }
//            }
//            if (targetVolume == null) {
//                throw new StorageAccessFrameworkException("No matching StorageVolume for: " + uriPermission.toString());
//            }
//            String documentPath = SAFUriHelper.getDocumentPathFromTreeUri(uriPermission.getUri());
//            if (documentPath.endsWith(File.separator)) {
//                documentPath = documentPath.substring(0, documentPath.length() - 1);
//            }
//            File path = new File(targetVolume.getPathFile(), documentPath);
//            String title = targetVolume.getDescription(context);
//            String documentId = DocumentsContract.getTreeDocumentId(uriPermission.getUri());
//            VolumeRoot volumeRoot = new VolumeRoot(uriPermission, volumeId, documentId, path, title);
//            Timber.tag(TAG).d("Found mapping %s -> %s", targetVolume, volumeRoot);
//            return volumeRoot;
//        } catch (ReflectiveOperationException e) {
//            Timber.tag(TAG).e(e, "StorageVolumeX reflection issue");
//            BugTrack.notify(TAG, e);
//        } catch (StorageAccessFrameworkException e) {
//            Timber.tag(TAG).w(e, "Failed to build VolumeRoot via StorageVolume.");
//        } catch (Exception e) {
//            Timber.tag(TAG).w(e, "Unknown issue while trying to create VolumeRoot via StorageVolume");
//        }
//        Timber.tag(TAG).e("getVolumeRoot(%s) via getVolumeList() failed.", uriPermission);
//        return null;
//    }
//
//    private boolean isMatch(StorageVolumeX storageVolume, String volumeId) throws ReflectiveOperationException {
//        if (!"mounted".equals(storageVolume.getState())) return false;
//
//        if (storageVolume.isEmulated()) {
//            if (storageVolume.isPrimary() && volumeId.equals("primary")) {
//                return true;
//            } else if (storageVolume.getOwner() != null) {
//                if (storageVolume.getOwner().toString().equals("UserHandle{0}") && volumeId.equals("primary")) {
//                    Timber.tag(TAG).w("MediaTek device workaround (issue #312), faking UUID 'primary' for %s", storageVolume.getPath());
//                    return true;
//                } else if (storageVolume.getOwner().toString().equals("UserHandle{0}") && volumeId.equals("emulated")) {
//                    Timber.tag(TAG).w("Prestigio device workaround (issue #493), faking UUID 'emulated' for %s", storageVolume.getPath());
//                    return true;
//                }
//            }
//        } else if (storageVolume.getUuid() != null && volumeId.equals(storageVolume.getUuid())) {
//            return true;
//        } else if (storageVolume.getUuid() == null) {
//            Timber.tag(TAG).w("Missing UUID for %s", storageVolume);
//        }
//
//        return storageVolume.getPathFile().getName().equals(volumeId);
//    }
//}
