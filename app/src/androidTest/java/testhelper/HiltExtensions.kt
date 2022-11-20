package testhelper


import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import eu.darken.sdmse.HiltTestActivity

/**
 * https://developer.android.com/training/dependency-injection/hilt-testing#launchfragment
 */
inline fun <reified T : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: Bundle? = null,
    crossinline action: Fragment.() -> Unit = {}
) {
    val startActivityIntent = Intent.makeMainActivity(
        ComponentName(
            ApplicationProvider.getApplicationContext(),
            HiltTestActivity::class.java
        )
    )

    ActivityScenario.launch<HiltTestActivity>(startActivityIntent).onActivity { activity ->
        activity.supportFragmentManager.fragmentFactory.instantiate(T::class.java.classLoader!!, T::class.java.name)
            .apply {
                arguments = fragmentArgs

                activity.supportFragmentManager
                    .beginTransaction()
                    .add(android.R.id.content, this, "")
                    .commitNow()

                action()
            }
    }
}