package eu.darken.sdmse.appcleaner.core.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.automation.specs.SpecProvider
import eu.darken.sdmse.automation.core.AutomationModule
import eu.darken.sdmse.automation.core.AutomationTask
import eu.darken.sdmse.automation.core.SpecSource
import eu.darken.sdmse.automation.core.crawler.ACCrawler
import eu.darken.sdmse.automation.core.crawler.AutomationHost
import eu.darken.sdmse.automation.core.crawler.CrawlerCommon
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.debug.logging.Logging.Priority.*
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import eu.darken.sdmse.common.debug.logging.logTag
import eu.darken.sdmse.common.funnel.IPCFunnel
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.PkgRepo
import eu.darken.sdmse.common.pkgs.features.Installed
import eu.darken.sdmse.common.progress.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException

class ClearCacheModule @AssistedInject constructor(
    @Assisted automationHost: AutomationHost,
    @Assisted private val moduleScope: CoroutineScope,
    val ipcFunnel: IPCFunnel,
    private val specProvider: SpecProvider,
    private val pkgRepo: PkgRepo,
    private val specACCrawlerFactory: ACCrawler.Factory,
) : AutomationModule(automationHost) {

    override suspend fun process(task: AutomationTask): AutomationTask.Result {
        task as ClearCacheTask
        updateProgressPrimary(R.string.general_progress_loading)

        host.changeOptions { old ->
            old.copy(
                showOverlay = true,
                accessibilityServiceInfo = AccessibilityServiceInfo().apply {
                    flags = (
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                                    or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                                    or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                            )
                    eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                    feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                },
                controlPanelTitle = R.string.appcleaner_automation_title.toCaString(),
                controlPanelSubtitle = R.string.appcleaner_automation_subtitle_default_caches.toCaString(),
            )
        }

        val crawler = specACCrawlerFactory.create(host)

        val successful = mutableSetOf<Pkg.Id>()
        val failed = mutableSetOf<Pkg.Id>()
        updateProgressCount(Progress.Count.Percent(0, task.targets.size))

        for (target in task.targets) {
            val installed = pkgRepo.getPkg(target)

            if (installed == null) {
                log(TAG, WARN) { "$target is not in package repo" }
                failed.add(target)
                continue
            }

            log(TAG) { "Clearing cache for $installed" }

            updateProgressPrimary(installed.label ?: target.name.toCaString())
            try {
                clearCache(crawler, installed)
                log(TAG, INFO) { "Successfully cleared cache for for $target" }
                successful.add(target)
            } catch (e: TimeoutCancellationException) {
                log(TAG, WARN) { "Timeout while processing $installed" }
                failed.add(target)
            } catch (e: CancellationException) {
                log(TAG, WARN) { "We were cancelled" }
                throw e
            } catch (e: Exception) {
                log(TAG, WARN) { "Failure for $target: ${e.asLog()}" }
                failed.add(target)
            } finally {
                increaseProgress()
            }
        }

        val backAction1 = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        log(TAG, VERBOSE) { "Was back1 successful=$backAction1" }
        val backAction2 = host.service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        log(TAG, VERBOSE) { "Was back2 successful=$backAction2" }

        return ClearCacheTask.Result(
            successful = successful,
            failed = failed,
        )
    }

    private suspend fun clearCache(crawler: ACCrawler, pkg: Installed) {
        log(TAG) { "Clearing default primary caches for $pkg" }
        val start = System.currentTimeMillis()
        val specs = getCrawlerSpecs(pkg).toMutableList()

        val iterator = specs.listIterator()
        while (iterator.hasNext()) {
            val spec = iterator.next()
            updateProgressSecondary(spec.label)
            updateProgressIcon(pkg.icon)

            log(TAG) { "Crawler (${pkg.id}) is starting for $spec" }
            try {
                crawler.crawl(spec)
                log(TAG) { "Crawler (${pkg.id}) finished for $spec" }
            } catch (e: Exception) {
                log(TAG) { "Crawler failed checking for branch exception for $spec" }
                val branchException = CrawlerCommon.getBranchException(e)
                if (branchException != null) {
                    var deletionSteps = branchException.invalidSteps

                    val originalPosition = specs.indexOf(spec)

                    // Add in new steps in front of us
                    branchException.altRoute.forEach { iterator.add(it) }
                    // Remove invalid steps
                    while (iterator.hasNext() && deletionSteps != 0) {
                        iterator.next()
                        iterator.remove()
                        deletionSteps--
                    }
                    // Return to original position
                    while (iterator.previousIndex() != originalPosition) {
                        iterator.previous()
                    }
                } else if (spec.isHailMary) {
                    throw spec.createHailMaryException(cause = e)
                } else {
                    throw e
                }
            }
        }
        val stop = System.currentTimeMillis()
        log(TAG) { "Cleared default primary cache in ${(stop - start)}ms for $pkg " }
    }

    private suspend fun getCrawlerSpecs(pkg: Installed): List<ACCrawler.Step> {
        val source = specProvider.allSpecs.firstOrNull {
            it.isResponsible(pkg)
        } ?: throw SpecSource.createUnsupportedError("DeviceSpec")
        log(TAG) { "Using specSource: ${source.label}" }
        return source.getSpecs(pkg)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class DIM {
        @Binds @IntoSet abstract fun mod(mod: Factory): AutomationModule.Factory
    }

    @AssistedFactory
    interface Factory : AutomationModule.Factory {
        override fun isResponsible(task: AutomationTask): Boolean = task is ClearCacheTask

        override fun create(host: AutomationHost, moduleScope: CoroutineScope): ClearCacheModule
    }

    companion object {
        val TAG: String = logTag("Automation", "AppCleaner", "ClearCacheModule")
    }
}