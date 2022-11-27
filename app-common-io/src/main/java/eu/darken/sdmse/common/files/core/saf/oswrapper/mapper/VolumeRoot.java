package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;//package eu.darken.sdmse.common.files.core.saf.oswrapper.mapper;
//
//import android.annotation.TargetApi;
//import android.content.UriPermission;
//import android.os.Build;
//
//import java.io.File;
//
//@TargetApi(Build.VERSION_CODES.KITKAT)
//public class VolumeRoot {
//    public final UriPermission uriPermission;
//    public final String volumeId;
//    public final String documentId;
//    public final File storagePath;
//    public final String label;
//
//    VolumeRoot(UriPermission uriPermission, String volumeId, String documentId, File storagePath, String label) {
//        this.uriPermission = uriPermission;
//        this.volumeId = volumeId;
//        this.documentId = documentId;
//        this.storagePath = storagePath;
//        this.label = label;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o)
//            return true;
//        if (!(o instanceof VolumeRoot))
//            return false;
//
//        VolumeRoot foo = (VolumeRoot) o;
//        return uriPermission.equals(foo.uriPermission);
//    }
//
//    @Override
//    public String toString() {
//        return "VolumeRoot(treeUri=" + uriPermission.getUri().getPath() + ", rootId=" + volumeId + ", title=" + label + ", documentId=" + documentId + ", storagePath=" + storagePath.getPath() + ")";
//    }
//}
