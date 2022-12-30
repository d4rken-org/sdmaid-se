//package eu.darken.sdmse.main.fragment
//
//import android.arch.core.executor.testing.InstantTaskExecutorRule
//import androidx.lifecycle.liveData
//import dagger.hilt.android.testing.BindValue
//import dagger.hilt.android.testing.HiltAndroidRule
//import dagger.hilt.android.testing.HiltAndroidTest
//import eu.darken.sdmse.main.ui.main.MainFragment
//import eu.darken.sdmse.main.ui.main.MainFragmentVM
//import io.mockk.every
//import io.mockk.mockk
//import io.mockk.verify
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import testhelper.BaseUITest
//import testhelper.launchFragmentInHiltContainer
//
//@HiltAndroidTest
//class ExampleFragmentTest : BaseUITest() {
//
//    @get:Rule(order = 0)
//    var hiltRule = HiltAndroidRule(this)
//
//    @get:Rule(order = 1)
//    var instantTaskExecutorRule = InstantTaskExecutorRule()
//
//    @BindValue
//    val mockViewModel = mockk<MainFragmentVM>(relaxed = true)
//
//    @Before fun init() {
//        hiltRule.inject()
//
//        mockViewModel.apply {
//            every { state } returns liveData { }
//        }
//    }
//
//    @Test fun happyPath() {
//        launchFragmentInHiltContainer<MainFragment>()
//
//        verify { mockViewModel.state }
//    }
//}