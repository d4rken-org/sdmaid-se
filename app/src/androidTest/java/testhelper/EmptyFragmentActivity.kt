package testhelper

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.annotation.Nullable
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider


class EmptyFragmentActivity : FragmentActivity() {
    @SuppressLint("RestrictedApi") override fun onCreate(@Nullable savedInstanceState: Bundle?) {
        FragmentFactoryHolderViewModel.getInstance(this).fragmentFactory?.let {
            supportFragmentManager.fragmentFactory = it
        }

        super.onCreate(savedInstanceState)
    }
}

class FragmentFactoryHolderViewModel : ViewModel() {
    var fragmentFactory: FragmentFactory? = null

    override fun onCleared() {
        super.onCleared()
        fragmentFactory = null
    }

    companion object {
        fun getInstance(activity: FragmentActivity): FragmentFactoryHolderViewModel {
            return ViewModelProvider(activity, FACTORY)[FragmentFactoryHolderViewModel::class.java]
        }

        private val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FragmentFactoryHolderViewModel() as T
            }
        }
    }
}
