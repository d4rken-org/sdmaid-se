package eu.darken.sdmse.common.user

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.adb.AdbManager
import eu.darken.sdmse.common.adb.canUseAdbNow
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.root.RootManager
import eu.darken.sdmse.common.root.canUseRootNow
import eu.darken.sdmse.common.shell.ShellOps
import eu.darken.sdmse.common.shell.ipc.ShellOpsCmd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager2 @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userManager: UserManager,
    private val rootManager: RootManager,
    private val adbManager: AdbManager,
    private val shellOps: ShellOps,
) {

    suspend fun currentUser(): UserProfile2 = UserProfile2(
        handle = if (!hasMultiUserSupport) UserHandle2(handleId = 0) else Process.myUserHandle().toUserHandle2(),
    )

    suspend fun systemUser(): UserProfile2 = UserProfile2(
        handle = UserHandle2(handleId = -1),
        isRunning = true,
    )

    private val userListRegex by lazy {
        Regex("^\\s+UserInfo.+?(\\d+):(.+):([a-z0-9]+).+?(\\w+)*\$")
    }

    suspend fun allUsers(): Set<UserProfile2> {
        val profiles = mutableSetOf<UserProfile2>()

        val shellMode = when {
            rootManager.canUseRootNow() -> ShellOps.Mode.ROOT
            adbManager.canUseAdbNow() -> ShellOps.Mode.ADB
            else -> null
        }

        log(TAG) { "allUsers(): shellMode=$shellMode" }

        if (shellMode != null) {
            try {
                val shellResult = shellOps.execute(ShellOpsCmd("pm list users"), shellMode)
                log(TAG) { "allUser() result: $shellResult" }
                if (!shellResult.isSuccess) throw IllegalStateException("allUser() failed: ")

                shellResult.output
                    .mapNotNull { userListRegex.matchEntire(it) }
                    .mapNotNull { match ->
                        try {
                            UserProfile2(
                                handle = UserHandle2(match.groupValues[1].toInt()),
                                label = match.groupValues[2]?.takeIf { it != "null" },
                                code = match.groupValues[3],
                                isRunning = match.groupValues[4] == "running",
                            )
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "UserProfile parsing failed for $shellResult: ${e.asLog()}" }
                            null
                        }
                    }
                    .run { profiles.addAll(this) }
            } catch (e: Exception) {
                log(TAG, ERROR) { "allUsers(): Lookup failed ${e.asLog()}" }
            }
        }

        if (profiles.isEmpty()) {
            userManager.userProfiles
                .map { UserProfile2(handle = it.toUserHandle2()) }
                .run { profiles.addAll(this) }
        }

        if (profiles.none { it.handle == currentUser().handle }) {
            profiles.add(currentUser())
        }

        return profiles
    }

    suspend fun otherUsers() = allUsers() - currentUser()

    suspend fun isAdminUser(userHandle: UserHandle2): Boolean = !hasMultiUserSupport || userHandle.handleId == 0

    val hasMultiUserSupport: Boolean by lazy {
        try {
            UserManager::class.java.getDeclaredMethod("supportsMultipleUsers").invoke(null) as Boolean
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to determine multi-user support state: ${e.asLog()}" }
            false
        }
    }

    private fun UserHandle.toUserHandle2(): UserHandle2 {
        var id: Int? = getIdentifier()

        if (id == null) id = userManager.getSerialNumberForUser(this).toInt()

        if (id == -1) id = this.hashCode()
        return UserHandle2(handleId = id)
    }

    suspend fun getHandleForId(rawId: Int) = UserHandle2(handleId = rawId)

    companion object {

        internal val TAG = logTag("UserManager2")
    }

}