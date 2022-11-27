package eu.darken.sdmse.common.files.ui.picker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.navArgs
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.ensureContentView
import eu.darken.sdmse.common.files.ui.picker.local.LocalPickerFragmentArgs
import eu.darken.sdmse.common.files.ui.picker.types.TypesPickerFragmentArgs
import eu.darken.sdmse.common.navigation.isGraphSet
import eu.darken.sdmse.common.observe2
import eu.darken.sdmse.common.smart.SmartActivity
import eu.darken.sdmse.databinding.PathPickerActivityBinding

@AndroidEntryPoint
class PathPickerActivity : SmartActivity() {
    val navArgs by navArgs<PathPickerActivityArgs>()

    private val vdc: PathPickerActivityVDC by viewModels()

    private val navController by lazy { findNavController(R.id.nav_host) }
    private val graph by lazy { navController.navInflater.inflate(R.navigation.path_picker) }
    private val appBarConf by lazy {
        AppBarConfiguration.Builder()
            .setFallbackOnNavigateUpListener {
                finish()
                true
            }
            .build()
    }
    private lateinit var ui: PathPickerActivityBinding

    private val sharedVM by lazy { ViewModelProvider(this)[SharedPathPickerVM::class.java] }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = PathPickerActivityBinding.inflate(layoutInflater)
        setContentView(ui.root)

        sharedVM.typeEvent.observe2(this) { vdc.onTypePicked(it) }
        sharedVM.resultEvent.observe2(this) { vdc.onResult(it) }

        vdc.launchSAFEvents.observe2(this) { intent ->
            startActivityForResult(intent, 18)
        }

        vdc.launchLocalEvents.observe2(this) { options ->
            ensureContentView(R.layout.path_picker_activity)
            val args = LocalPickerFragmentArgs(options = options)
            if (!navController.isGraphSet()) {
                if (options.allowedTypes.size > 1) {
                    graph.setStartDestination(R.id.typesPickerFragment)
                } else {
                    graph.setStartDestination(R.id.localPickerFragment)
                }
                navController.setGraph(graph, args.toBundle())
                setupActionBarWithNavController(navController, appBarConf)
                if (options.allowedTypes.size > 1) {
                    navController.navigate(R.id.localPickerFragment, args.toBundle())
                }
            } else {
                navController.navigate(R.id.action_typesPickerFragment_to_localPickerFragment, args.toBundle())
            }
        }

        vdc.launchTypesEvents.observe2(this) {
            ensureContentView(R.layout.path_picker_activity)
            val args = TypesPickerFragmentArgs(options = it)
            if (!navController.isGraphSet()) {
                graph.setStartDestination(R.id.typesPickerFragment)
                navController.setGraph(graph, args.toBundle())
                setupActionBarWithNavController(navController, appBarConf)
            } else {
                navController.popBackStack()
            }
        }

        vdc.resultEvents.observe2(this) { (result, finish) ->
            val resultCode = when {
                result.isSuccess -> Activity.RESULT_OK
                else -> Activity.RESULT_CANCELED
            }
            val data = Intent().apply {
                putExtra(PathPickerActivityContract.ARG_PICKER_RESULT, result)
            }
            setResult(resultCode, data)
            if (finish) finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            18 -> vdc.onSAFPickerResult(data?.data)
            else -> throw NotImplementedError()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        else -> NavigationUI.onNavDestinationSelected(item, navController) || super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConf) || super.onSupportNavigateUp()
    }
}