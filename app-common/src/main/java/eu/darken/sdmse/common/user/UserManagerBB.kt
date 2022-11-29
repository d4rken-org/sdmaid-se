package eu.darken.sdmse.common.user

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManagerBB @Inject constructor() {

    val currentUser: UserHandleBB
        get() {
            return UserHandleBB(userId = 0)
        }

}