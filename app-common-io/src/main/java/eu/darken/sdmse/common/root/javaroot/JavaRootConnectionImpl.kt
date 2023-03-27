package eu.darken.sdmse.common.root.javaroot

import android.content.Context
import androidx.annotation.Keep
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.rxshell.cmd.Cmd
import eu.darken.rxshell.cmd.RxCmdShell
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.files.core.local.root.FileOpsConnection
import eu.darken.sdmse.common.files.core.local.root.FileOpsHost
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsConnection
import eu.darken.sdmse.common.pkgs.pkgops.root.PkgOpsHost
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Keep
class JavaRootConnectionImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileOpsHost: Lazy<FileOpsHost>,
    private val pkgOpsHost: Lazy<PkgOpsHost>
) : JavaRootConnection.Stub() {

    override fun checkBase(): String {
        val sb = StringBuilder()
        sb.append("Our pkg: ${context.packageName}\n")
        val ids = Cmd.builder("id").submit(RxCmdShell.Builder().build()).blockingGet()
        sb.append("Shell ids are: ${ids.merge()}\n")
        val result = sb.toString()
        log(TAG) { "checkBase(): $result" }
        return result
    }

    override fun getFileOps(): FileOpsConnection = fileOpsHost.get()

    override fun getPkgOps(): PkgOpsConnection = pkgOpsHost.get()

    companion object {
        private val TAG = logTag("Root", "Java", "Connection")
    }
}