package eu.darken.sdmse.common.files.core.saf.oswrapper;


import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.File;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SAFUriHelper {
    /**
     * @param treeUri content://com.android.externalstorage.documents/tree/VOLUMEID/document/DOCUMENTID/#Files#
     * @return VOLUMEID
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public static String getVolumeIdFromTreeUri(@NonNull final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");

        if (split.length > 0) {
            return split[0];
        } else {
            return null;
        }
    }

    /**
     * @param treeUri content://com.android.externalstorage.documents/tree/VOLUMEID/document/DOCUMENTID/#Files#
     * @return "/" or "/#Files#"
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @NonNull
    public static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) {
            return split[1];
        } else {
            return File.separator;
        }
    }
}
