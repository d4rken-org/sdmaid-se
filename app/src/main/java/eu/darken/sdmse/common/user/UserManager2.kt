package eu.darken.sdmse.common.user

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager2 @Inject constructor() {

    val currentUser: UserHandle2
        get() {
            return UserHandle2(userId = 0)
        }

}