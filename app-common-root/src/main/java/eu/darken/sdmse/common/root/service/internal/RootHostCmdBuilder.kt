package eu.darken.sdmse.common.root.service.internal

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Base64
import androidx.annotation.RequiresApi
import eu.darken.flowshell.core.cmd.FlowCmd
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.parcel.forceParcel
import eu.darken.sdmse.common.parcel.marshall
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Based on https://github.com/Chainfire/librootjava
 * Also see
 * https://github.com/Mygod/VPNHotspot/blob/master/mobile/src/main/java/be/mygod/librootkotlinx/AppProcess.kt
 * https://github.com/Chainfire/librootjava/blob/master/librootjava/src/main/java/eu/chainfire/librootjava/AppProcess.java
 */

@SuppressLint("PrivateApi")
class RootHostCmdBuilder<Host : BaseRootHost>(
    private val context: Context,
    private val rootHost: KClass<Host>,
) {

    private val currentInstructionSet by lazy {
        val classVMRuntime = Class.forName("dalvik.system.VMRuntime")
        val runtime = classVMRuntime.getDeclaredMethod("getRuntime").invoke(null)
        classVMRuntime.getDeclaredMethod("getCurrentInstructionSet").invoke(runtime) as String
    }

    private val classSystemProperties by lazy {
        Class.forName("android.os.SystemProperties")
    }

    private val isVndkLite by lazy {
        classSystemProperties
            .getDeclaredMethod("getBoolean", String::class.java, Boolean::class.java)
            .invoke(null, "ro.vndk.lite", false) as Boolean
    }

    private val vndkVersion by lazy {
        classSystemProperties
            .getDeclaredMethod("get", String::class.java, String::class.java)
            .invoke(null, "ro.vndk.version", "") as String
    }

    /**
     * Based on: https://android.googlesource.com/platform/bionic/+/aff9a34/linker/linker.cpp#3397
     */
    @get:RequiresApi(28)
    private val genericLdConfigFilePath: String
        get() {
            try {
                val path = "/system/etc/ld.config.$currentInstructionSet.txt"
                if (File(path).isFile) return path
            } catch (e: Throwable) {
                log(TAG, WARN) { "Failed to currentInstructionSet path: ${e.asLog()}" }
            }

            if (Build.VERSION.SDK_INT >= 30) {
                val path = "/linkerconfig/ld.config.txt"
                if (File(path).isFile) return path
                log(TAG, WARN) { "Failed to find generated linker configuration from \"$path\"" }
            }

            if (isVndkLite) {
                val path = "/system/etc/ld.config.vndk_lite.txt"
                if (File(path).isFile) return path
            }

            if (listOf("", "current").none { vndkVersion == it }) {
                val path = "/system/etc/ld.config.$vndkVersion.txt"
                if (File(path).isFile) return path
            }

            return "/system/etc/ld.config.txt"
        }

    /**
     * Based on: https://android.googlesource.com/platform/bionic/+/30f2f05/linker/linker_config.cpp#182
     */
    @RequiresApi(26)
    fun findLinkerSection(lines: Sequence<String>, binaryRealPath: String): String {
        for (untrimmed in lines) {
            val line = untrimmed.substringBefore('#').trim()
            if (line.isEmpty()) continue
            if (line[0] == '[' && line.last() == ']') break
            if (line.contains("+=")) continue

            val chunks = line.split('=', limit = 2)
            if (chunks.size < 2) {
                log(TAG, WARN) { "Couldn't parse invalid format: $line (ignoring line)" }
                continue
            }

            var (name, value) = chunks.map { it.trim() }
            if (!name.startsWith("dir.")) {
                log(TAG, WARN) { "Unexpected property name \"$name\", expected 'dir.<section_name>' (ignoring line)" }
                continue
            }
            if (value.endsWith('/')) value = value.dropLast(1)
            if (value.isEmpty()) {
                log(TAG, WARN) { "Property value is empty (ignoring line)" }
                continue
            }
            try {
                value = File(value).canonicalPath
            } catch (e: IOException) {
                log(TAG, WARN) { "Path \"$value\" couldn't be resolved: ${e.asLog()}" }
            }
            if (binaryRealPath.startsWith(value) && binaryRealPath[value.length] == '/') return name.substring(4)
        }
        throw IllegalArgumentException("No valid linker section found")
    }

    private val myExe get() = "/proc/${Process.myPid()}/exe"
    private val myExeCanonical
        get() = try {
            File("/proc/self/exe").canonicalPath
        } catch (e: IOException) {
            log(TAG, WARN) { "Couldn't resolve self exe: ${e.asLog()}" }
            "/system/bin/app_process"
        }

    /**
     * To workaround Samsung's stupid kernel patch that prevents exec, we need to relocate exe outside of /data.
     * See also: https://github.com/Chainfire/librootjava/issues/19
     */
    private fun relocateScript(): Pair<String, String> {
        val persistence = File(context.codeCacheDir, ".libroot-relocation-uuid")
        val token = context.packageName + '@' + try {
            persistence.readText()
        } catch (_: FileNotFoundException) {
            log(TAG) { "No relocation id found, creating." }
            UUID.randomUUID().toString().also { persistence.writeText(it) }
        }

        val script = StringBuilder()

        val (baseDir, relocated) = if (Build.VERSION.SDK_INT < 29) {
            "/dev" to "/dev/app_process_$token"
        } else {
            val apexPath = "/apex/$token"
            // we need to mount a new tmpfs to override noexec flag
            script.appendLine(
                "[ -d $apexPath ] || mkdir $apexPath && mount -t tmpfs -o size=1M tmpfs $apexPath || exit 1"
            )

            // unfortunately native ld.config.txt only recognizes /data,/system,/system_ext as system directories;
            // to link correctly, we need to add our path to the linker config too
            val ldConfig = "$apexPath/etc/ld.config.txt"
            val masterLdConfig = genericLdConfigFilePath
            val section = try {
                File(masterLdConfig).useLines { findLinkerSection(it, myExeCanonical) }
            } catch (e: Exception) {
                log(TAG, WARN) { "Failed to locate system section: ${e.asLog()}" }
                "system"
            }
            script.appendLine(
                "[ -f $ldConfig ] || mkdir -p $apexPath/etc && echo dir.$section = $apexPath >$ldConfig && cat $masterLdConfig >>$ldConfig || exit 1"
            )
            "$apexPath/bin" to "$apexPath/bin/app_process"
        }

        script.appendLine(
            "[ -f $relocated ] || mkdir -p $baseDir && cp $myExe $relocated && chmod 700 $relocated || exit 1"
        )

        return script.toString() to relocated
    }

    fun build(
        withRelocation: Boolean,
        initialOptions: RootHostInitArgs,
    ): FlowCmd {
        log { "build(relocate=$withRelocation, ${initialOptions})" }
        val cmds = mutableListOf<String>()

        val processPath: String = if (withRelocation) {
            val (relocScript, relocPath) = relocateScript()
            log(TAG) { "Relocation script: $relocScript" }
            log(TAG) { "Relocation path: $relocPath" }
            cmds.add(relocScript)
            relocPath
        } else {
            myExe
        }

        var launchCmd = buildLaunchCmd(
            isDebug = initialOptions.isDebug,
            processPath = processPath
        )
        log(TAG) { "Launch command: $launchCmd" }
        launchCmd += " ${BaseRootHost.OPTIONS_KEY}=${initialOptions.toLaunchCmdFormat()}"
        log(TAG) { "Launch command with options: $launchCmd" }

        cmds.add(launchCmd)

        return FlowCmd(cmds)
    }

    private fun buildLaunchCmd(isDebug: Boolean, processPath: String): String {
        val packageCodePath = context.packageCodePath

        var debugParams = ""
        if (isDebug) {
            // https://developer.android.com/ndk/guides/wrap-script#debugging_when_using_wrapsh
            debugParams += when (Build.VERSION.SDK_INT) {
                in 29..Int.MAX_VALUE -> "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y"
                28 -> "-XjdwpProvider:adbconnection -XjdwpOptions:suspend=n,server=y -Xcompiler-option --debuggable"
                else -> "-Xrunjdwp:transport=dt_android_adb,suspend=n,server=y -Xcompiler-option --debuggable"
            }
        }

        val niceName = "${context.packageName}:root:${rootHost.qualifiedName ?: rootHost.java.name}"
        val extraParams = " --nice-name=$niceName"

        val hostClass = rootHost.qualifiedName ?: rootHost.java.name

        return "CLASSPATH=$packageCodePath exec $processPath $debugParams /system/bin$extraParams $hostClass"
    }

    private fun RootHostInitArgs.toLaunchCmdFormat(): String {
        try {
            this.forceParcel()
        } catch (e: Throwable) {
            log(TAG, ERROR) { "forceParcel() check failed: ${e.asLog()}" }
            throw RuntimeException("RootHostInitArgs parcelation failed", e)
        }

        return Base64.encodeToString(this.marshall(), Base64.NO_WRAP)
    }

    companion object {
        private val TAG = logTag("Root", "Service", "Host", "CmdBuilder")
    }
}