package eu.darken.sdmse.common.user

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.debug.logging.Logging.Priority.ERROR
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager2 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userManager: UserManager,
) {

    val currentUser: UserHandle2
        get() = if (!hasMultiUserSupport) UserHandle2(userId = 0) else Process.myUserHandle().toUserHandle2()

    val systemUser: UserHandle2
        get() = UserHandle2(userId = -1)

    val allUsers: List<UserHandle2>
        get() = userManager.userProfiles.map { it.toUserHandle2() }

    fun isAdminUser(userHandle: UserHandle2): Boolean = !hasMultiUserSupport || userHandle.userId == 0

    val hasMultiUserSupport: Boolean by lazy {
        try {
            UserManager::class.java.getDeclaredMethod("supportsMultipleUsers").invoke(null) as Boolean
        } catch (e: Exception) {
            log(TAG, ERROR) { "Failed to determine multi-user support state: ${e.asLog()}" }
            false
        }
    }

    private fun UserHandle.toUserHandle2(): UserHandle2 {
        var id: Int? = try {
            val getIdentifier = this.javaClass.getMethod("getIdentifier")
            getIdentifier.invoke(this) as Int
        } catch (e: Exception) {
            log(TAG, WARN) { "toUserHandle2(): Failed to use reflective access on getIdentifier: ${e.asLog()} " }
            null
        }

        if (id == null) id = userManager.getSerialNumberForUser(this).toInt()

        if (id == -1) id = this.hashCode()
        return UserHandle2(userId = id)
    }

    suspend fun getHandleForId(rawId: Int) = UserHandle2(userId = rawId)

    companion object {
        private val TAG = logTag("UserManager2")
    }

}