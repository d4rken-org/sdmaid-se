package eu.darken.sdmse.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.liveData
import androidx.test.core.app.launchActivity
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import eu.darken.sdmse.main.ui.MainActivity
import eu.darken.sdmse.main.ui.MainViewModel
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import testhelper.BaseUITest

@HiltAndroidTest
class MainActivityTest : BaseUITest() {

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @BindValue
    val mockViewModel = mockk<MainViewModel>(relaxed = true)

    @Before fun init() {
        hiltRule.inject()

        mockViewModel.apply {
            every { state } returns liveData { }
            every { onGo() } just Runs
        }
    }

    @Test fun happyPath() {
        launchActivity<MainActivity>()

        verify { mockViewModel.onGo() }
    }
}