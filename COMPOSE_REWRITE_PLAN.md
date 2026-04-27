# Compose Rewrite: SD Maid SE

## Current Status (2026-04-27 — all sections closed)

### What's done
- **Infrastructure**: Navigation3 (stable 1.0.1), ViewModel4, SingleEventFlow, SdmSeTheme, NavigationController (now with typed `setResult`/`consumeResults` + `ResultKey<T>`), ErrorEventHandler, NavigationEventHandler, settings toolkit composables
- **Gradle**: Compose plugin + dependencies on all 10 UI modules (app, app-common-ui, all 9 tool modules) **+ app-common-stats + app-common-picker + app-common-exclusion**. `addNavigation3()` now added to every converted tool module's `build.gradle.kts`.
- **All routes**: Every route implements NavigationDestination
- **ViewModels migrated to ViewModel4** (61 total — `ClusterViewModel` was folded into `DeduplicatorDetailsViewModel`, `FilterContentViewModel` was folded into `FilterContentDetailsViewModel`, and `AppJunkViewModel` was folded into `AppJunkDetailsViewModel`): Main, Dashboard, Setup, Settings, GeneralSettings, Acknowledgements, Support, SupportContactForm, DashboardCardConfig, DebugLogSessions, DataAreas, LogView, Upgrade (FOSS+GPLAY), all onboarding VMs (Welcome, Privacy, Setup, Versus), 9 tool-settings VMs **+ Reports, Picker, ArbiterConfig, CustomFilterList, AffectedPkgs, AffectedPaths, SpaceHistory, ExclusionList, PkgExclusionEditor, SegmentExclusionEditor, PathExclusionEditor, CorpseFinderList, CorpseDetails, SqueezerSetup, SqueezerList, SwiperStatus, SwiperSessions, Preview, SwiperSwipe, DeduplicatorList, DeduplicatorDetails, SystemCleanerList, FilterContentDetails, CustomFilterEditor, AppCleanerList, AppJunkDetails, AppControlList, AppAction, SchedulerManager, ScheduleItem, DeviceStorage, StorageContent, Apps, AppDetails, Content**
- **Navigation3 stable**: Upgraded from alpha08 to 1.0.1, fixed API renames (EntryProviderScope, rememberSaveableStateHolderNavEntryDecorator)
- **62 screens fully converted to Compose**: Onboarding (4), Dashboard, Setup, Settings index, General Settings, Acknowledgements, Support, DashboardCardConfig, SupportContactForm, DebugLogSessions, DataAreas, LogView, Upgrade (FOSS), Upgrade (GPlay), 9 tool-settings screens, Reports (stats), Picker (with SavedStateHandle-backed mid-pick state), ArbiterConfig, CustomFilterList, AffectedPkgs, AffectedPaths, SpaceHistory (chart wrapped via AndroidView), ExclusionList, PathExclusionEditor, PkgExclusionEditor, SegmentExclusionEditor, CorpseFinderList, CorpseDetails (HorizontalPager with inline per-corpse content; CorpseRoute retired), SqueezerSetup, SwiperStatus, SwiperSessions, SqueezerList, Preview, SwiperSwipe (card-stack via `awaitEachGesture` + `VelocityTracker` + `Animatable`; pure `decideSwipe` helper unit-tested; first-launch gesture overlay; entire `app-tool-swiper` Fragment surface retired), DeduplicatorList, DeduplicatorDetails (HorizontalPager with inline `ClusterContent`; ClusterRoute retired; `PreviewDeletionDialog` ported to a stateless Compose dialog reused by the Dashboard `Mode.All` flow), SystemCleanerList, FilterContentDetails (HorizontalPager with inline `FilterContentPage`; `FilterContentRoute` + `FilterContentViewModel` folded into Details), CustomFilterEditor (Material 3 `BottomSheetScaffold` for live-search peek, `TaggedInputField` Compose port of `TaggedInputView` with FlowRow + InputChip + position-preserving mode swap; entire SystemCleaner Fragment surface retired), AppCleanerList, AppJunkDetails (HorizontalPager with inline `AppJunkPage`; `AppJunkRoute` + `AppJunkViewModel` folded into Details; per-junk collapse map; `DeleteSpec` sealed type + `AppCleanerTaskFactory.buildAppCleanerTask` helper that forces `includeInaccessible=false` on Category/SingleFile/SelectedFiles tasks; entire AppCleaner Fragment surface retired), AppControlList, AppAction (Material3 `ModalBottomSheet` route; pure `buildAppActionItems` helper with JUnit5 coverage of the inclusion matrix; entire AppControl Fragment surface retired), SchedulerManager, ScheduleItem (Material3 `ModalBottomSheet` route bound via the Picker entry-arg pattern; Compose Material3 `TimePicker` replaces `MaterialTimePicker`; editable form backed by a separate `MutableStateFlow<FormDraft>` so external `schedulerManager.state` emissions don't wipe input; save-time guards refuse to resurrect deleted schedules and refuse to mutate externally-enabled schedules; entire Scheduler Fragment surface retired) **+ DeviceStorage, StorageContent, Apps, AppDetails, Content (entire `app-tool-analyzer` Fragment surface retired; routes use Picker `bindRoute` pattern with sealed `Loading/Ready/NotFound` state for the 3 screens with required upstream data; trend chart wraps legacy `SpaceHistoryChartView` via `AndroidView`; selection mode mirrors legacy `installListSelection` with a Select All toolbar action; init-time scan submits move to a dedicated `routeFlow.filterNotNull().take(1)` flow so `WhileSubscribed(5000)` restarts don't re-fire them; progress ratios clamped 0f..1f for Material3 strict checks; Media `single()` → `singleOrNull()` with no-op fallback)**
- **Dashboard cards done**: All 18 card types have full Compose composables with XML parity
- **Setup cards done**: All 8 card types (Storage, SAF, Root, UsageStats, Automation, Notification, Shizuku, Inventory) + SetupLoadingCard + SetupCardContainer
- **Support dialogs done**: `RecorderConsentDialog` and `ShortRecordingDialog` are implemented and wired into `SupportScreenHost`
- **Reorderable lib**: Adopted `sh.calvin.reorderable` (APACHE 2.0, acknowledged in `AcknowledgementsScreen`) for drag-reorder in `DashboardCardConfigScreen` + `ArbiterConfigScreen`
- **Navigation3 ModalBottomSheet pattern proven**: `DebugLogSessionsRoute` renders as a `ModalBottomSheet` inside its `entry<>` block in `AppNavigation.kt`; unblocks AppActionDialog + ScheduleItemDialog conversions
- **Shared settings toolkit expanded**: Compose `SizeInputDialog` + `AgeInputDialog` under `common/compose/settings/dialogs/`; `SettingsBadgedSwitchItem` with a write-blocking `SettingGate` (SetupRequired/ProRequired) replaces the legacy `BadgedCheckboxPreference`. `SettingsSwitchItem` and `SettingsBadgedSwitchItem` both accept `ImageVector` or `Painter` for the leading icon.
- **Per-module `NavigationEntry` + Hilt `@Binds @IntoSet`**: All 9 tool modules (+ Stats inside `app-common-stats`, + `app-common-picker`, + `app-common-exclusion`, **+ `app-common-coil` (Preview)**) ship their own `NavigationEntry`.
- **Cross-screen result API**: `NavigationController.setResult(ResultKey<T>, value)` / `consumeResults(ResultKey<T>)` replaces the generic `ResultBus`. Typed via `ResultKey<T>` in `app-common/common/navigation/`. Consumers: `DeduplicatorSettingsViewModel`, `ArbiterConfigViewModel`, `PathExclusionViewModel`, `SqueezerSetupViewModel`, `SwiperSessionsViewModel` all use `PickerResultKey`. **Picker FragmentManager bridge retired** — PickerScreen no longer calls `setFragmentResult`, and `PickerViewModel.Event.Saved` was removed (save() now inlines navUp() after setResult). Every Picker consumer is now Compose-only.
- **Coil ↔ Compose bridge**: `io.coil-kt:coil-compose:2.7.0` added to app-common-stats, app-common-exclusion, **and app-common-coil**. `AsyncImage(model = Pkg)` and `AsyncImage(model = APathLookup)` both resolve through existing Coil mappers (PkgFetcher, PathPreviewFetcher). A Compose `FilePreviewImage(lookup)` helper in `app-common-coil` mirrors the legacy `ImageView.loadFilePreview` with file-type fallback/error icons — used by all tool detail screens.
- **Shared `ProgressOverlay` Compose primitive**: `app-common-ui/.../compose/progress/ProgressOverlay.kt` hides content (alpha 0) and consumes pointer input when `Progress.Data != null`, replacing the XML `ProgressOverlayView` for every converted tool screen.
- **Shared bitmap-comparison primitive**: `app-tool-squeezer/.../comparison/SqueezerComparisonDialog.kt` is a public reusable Compose dialog (fullscreen `Dialog(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)`) with side-by-side `AndroidView { CoilZoomImageView }`, orientation-aware Row/Column, GatewaySwitch-prepared cache file via Hilt EntryPoint, IO bitmap pipeline (`BitmapSampler` + `compress(JPEG/WEBP)` + tempfile + recycle-in-finally + async cleanup-on-dispose). Used by both Squeezer Setup show-example and Squeezer List "View comparison".

### What's broken / incomplete
- **0 Fragments unconverted** — every former tool list/details Fragment has been replaced by a Compose route. **Final-cleanup #3 + RecorderActivity batch shipped in 5 commits (`ee6efe826`, `1e7e61a10`, `168b7b14f`, `4e51209c3`, `2fcfb5e09`)** — ~110 files / ~4,200 deletions removing the Fragment base chain (Fragment2/3, DialogFragment2/3, BottomSheetDialogFragment2, PreferenceFragment2/3, ToolbarHost), orphan uix helpers (Service2, ViewModelLazyKeyed, DetailsPagerAdapter3, FragmentStatePagerAdapter4 + ext), `MainNavGraph`, `LegacyNavigationBridge`, `FragmentExtensions`, `NavDestinationExtensions`, `LogViewerAdapter`, `lists/selection/*` (5 helpers), and now the entire remaining `app-common-ui/.../lists/*` package (17 files: BaseAdapter, BindableVH, DataAdapter, MutableDataAdapter, RecyclerViewExtensions, ViewHolderBasedDivider, AsyncDiffer chain, ModularAdapter, modular/mods/*, ListItem); 8 orphan layouts + 4 menus + 9 dead-only `ids.xml`; legacy custom views with their paired XMLs (`ProgressBarView`, `BreadCrumbBar` + crumb item, `QualityInputDialog`, `BadgedCheckboxPreference`, legacy `common.ui.AgeInputDialog`/`SizeInputDialog`, `CaveatPreferenceView`/`Group`); both `debug_recorder_*.xml` layouts; `BreadCrumbBarStyle`; `viewBinding = true` from 14 modules (kept in `app-common-ui` for MascotView, `app-common-automation` for AutomationControlView); `recyclerview-selection` from 11 modules; `runtime-livedata` from `buildSrc`; `Activity2`, `ViewModel3`, `NavCommand`, `NavEventSource`, `LogFileAdapter`. `MainActivity` rebased on `ComponentActivity` (verbose lifecycle hooks dropped — debug-only). `ErrorDialogSetup.kt` was switched from inline `R.id.nav_host` (deleted with `main_activity.xml`) to `eu.darken.sdmse.common.R.id.nav_host` to match the other two callers. **RecorderActivity is now Compose** (`ComponentActivity` + `setContent { SdmSeTheme(state = themeState) { RecorderScreenHost(...) } }`); `RecorderViewModel` rebased on `ViewModel4` with `themeState` exposed from `GeneralSettings`, filesystem work moved to `dispatcherProvider.IO`, and share-intent failures routed through `errorEvents` via `vm.onShareLaunchFailed(e)`. `Theming.kt` now skips both `MainActivity` and `RecorderActivity` from the XML-era theming pipeline.
- **Priority #1 dead-click rows — CLOSED**. CustomFilterList/Picker/ArbiterConfig/Reports all converted and wired through the new result API.
- **Priority #2 Stats + Exclusion batch — CLOSED**. Three `ReportsViewModel` FIXMEs closed; `PathExclusionEditor` migrated off `setFragmentResultListener` to `navCtrl.consumeResults(PickerResultKey)`; Segment editor bug that ignored `targetSegments` fixed.
- **CorpseFinder tool — CLOSED**. List + Details converted to Compose; `CorpseRoute` retired (its only caller was the ViewPager adapter, now an inline pager page inside `CorpseDetailsScreen`).
- **Picker bridge retirement batch — CLOSED**. SqueezerSetup / SwiperStatus / SwiperSessions converted; Picker's `setFragmentResult` emit path + `Event.Saved` removed.
- **Squeezer + Preview batch — CLOSED**. Squeezer tool fully Compose (List + ComparisonDialog + Setup show-example bitmap restored); legacy `PreviewCompressionDialog` (MaterialAlertDialog wrapper), `ComparisonDialog` (DialogFragment), and orphan `SqueezerOnboardingDialog` deleted; `SqueezerSetupViewModel.startScan` → `SqueezerListRoute` FIXME closed. Preview cross-cutting converted: `PreviewFragment` + `PreviewItemFragment` + `PreviewAdapter` + `PreviewItemViewModel` + `PreviewItem` wrapper + `PreviewItemRoute` + `ZoomAwareViewPager` retired in favor of a single Compose Dialog with `HorizontalPager` and per-page zoom tracking. **Six PreviewRoute callers light up** (SystemCleaner FilterContent, Deduplicator List/Cluster/PreviewDeletionDialog, Squeezer List, Swiper Swipe). Picker pattern (route passed via entry lambda, NOT `SavedStateHandle.toRoute<>()`) confirmed correct for complex Serializable args under Navigation3.
- **Swiper Swipe batch — CLOSED**. Entire `app-tool-swiper` module is now Compose. Card-stack gesture handling reproduced in pure Compose (`awaitEachGesture` + `VelocityTracker` + `Animatable<Offset>`); pure-function `decideSwipe(...)` helper covered by `SwiperSwipeDecisionTest` (distance/velocity commit, swap, undo gate, dominant-axis). The two `// FIXME: Lands on UnknownDestinationScreen until SwiperSwipe converts.` markers in SwiperSessions/SwiperStatus VMs are closed. **`SwiperStatusViewModel` was also migrated off `SavedStateHandle.toRoute<>()`** to the same Picker `bindRoute(route)` pattern after on-device smoke-test reproduced the same `MissingFieldException` for SwiperStatusRoute on Compose-to-Compose entry — the route's `from(handle)` companion remains as dead code (used only by the old crash path) and is a candidate for cleanup.
- **Deduplicator tool — CLOSED**. List + Details + Cluster converted; `ClusterRoute` retired (folded into `DeduplicatorDetailsScreen` via `HorizontalPager` + inline `ClusterContent`, mirroring the CorpseDetails precedent). `PreviewDeletionDialog` is now a stateless Compose dialog (with `Mode.All` reused by the Dashboard `DeduplicatorDeleteConfirmation` flow). `ClusterViewModel` (~280 lines) folded into `DeduplicatorDetailsViewModel`; element-builder extracted as a pure function with JUnit 5 coverage. Selection limited to duplicate rows (matches legacy `itemSelectionKey` semantics) and capped at `duplicateCount - 1` when `allowDeleteAll` is false. The 9 dead `PreviewRoute` emissions queued in Cluster/List code paths now actually navigate. `NavigationIdConsistencyTest` + `DeduplicatorRoutesSerializationTest` updated to drop ClusterRoute references.
- **SystemCleaner tool — CLOSED**. List + Details + FilterContent + CustomFilterEditor converted; `FilterContentRoute` retired (folded into `FilterContentDetailsScreen` via `HorizontalPager` + inline `FilterContentPage`, mirroring CorpseDetails). `FilterContentViewModel` folded into `FilterContentDetailsViewModel`; the per-filter sort + show-date/preview gating extracted as a pure `buildFilterContentElements` helper with JUnit 5 coverage. Action methods (delete-filter, delete-files, exclude-filter, exclude-files) snapshot `systemCleaner.state.first()` and validate the filter still exists before submitting tasks, mirroring the CorpseDetails stale-state guard. CustomFilterEditor uses Material 3 `BottomSheetScaffold` for the persistent live-search panel (peek 64/96/128 dp, expanded sheet capped at 70%, focus-collapse on tagged-input focus). `TaggedInputView` (custom `ConstraintLayout`) replaced by a Compose `TaggedInputField` (`FlowRow` of `InputChip` + `BasicTextField` with `onPreviewKeyEvent` Backspace, NAME `/` filter, blur-clears-uncommitted-text). Long-press chip mode-switcher uses a position-preserving `swapPreservingOrder` helper. Both FIXMEs in `CustomFilterListViewModel.kt:82,92` closed. Compose UI tests added for the editor screen via Robolectric + `createComposeRule`.
- **AppCleaner tool — CLOSED**. List + AppJunkDetails + AppJunk converted; `AppJunkRoute` retired (folded into `AppJunkDetailsScreen` via `HorizontalPager` + inline `AppJunkPage`, mirroring CorpseDetails / FilterContentDetails). `AppJunkViewModel` folded into `AppJunkDetailsViewModel`; per-junk collapse state lives in a `MutableStateFlow<Map<InstallId, Set<KClass>>>` so swiping between apps preserves each app's collapse independently; an empty-junk filter (`!junk.isEmpty()`) drops zero-content junks left by per-path `appCleaner.exclude(installId, paths)` so the pager doesn't render ghost pages. The four element-type list (Header / InaccessibleCache / CategoryHeader / FileRow) is built by a pure `buildAppJunkElements(junk, collapsed)` helper with JUnit 5 coverage. UI delete actions go through a single `requestDelete(spec)` / `confirmDelete(spec)` pair with a sealed `DeleteSpec` (WholeJunk / Inaccessible / Category / SingleFile / SelectedFiles) and a `buildAppCleanerTask(spec, junk)` helper that **forces `includeInaccessible=false` on Category/SingleFile/SelectedFiles** (the param defaults to `true` on `AppCleanerProcessingTask`, which would silently clear the inaccessible cache during file-only deletes). The factory also drops `targetContents` from Category specs to avoid `AppCleaner.kt:226` `single { tc.matches(...) }` collisions. JUnit 5 covers stale-snapshot reconstruction (cross-category SelectedFiles → `targetFilters` derived from live snapshot) and the `includeInaccessible=false` invariant. Both List and Details emit `Event.ExclusionsCreated(count)` and surface a snackbar with a "View" action that navigates to `ExclusionsListRoute`. `coil-compose` was promoted from `implementation` to `api` in `app-common-coil` so consumers can use `AsyncImage` directly without redeclaring the dep.
- **AppControl tool — CLOSED**. List + AppActionDialog converted; `AppActionDialog` (BottomSheetDialogFragment2) replaced by a Material3 `ModalBottomSheet` route registered via `entry<AppActionRoute> { route -> AppActionSheetHost(installId = route.installId) }` (Picker-style entry-arg binding — `SavedStateHandle.toRoute<>()` crashed at runtime with `MissingFieldException` for non-nullable `InstallId`). The 12 RecyclerView VHs collapsed into a sealed `AppActionItem` sealed type (Info.Size, Info.Usage, Action.Launch / ForceStop / SystemSettings / AppStore / Exclude / Toggle / Uninstall / Archive / Restore / Export) materialised by a pure `buildAppActionItems(appInfo, ctx)` helper with 13-case JUnit5 coverage (current-vs-other-user, existing-exclusion add/edit, enable/disable, archive/restore availability, size/usage/export availability). Single-app Uninstall/Archive/Restore confirms re-instated via `Event.Confirm*` (Archive/Restore via root/ADB skip OS confirms otherwise — losing them would have been a destructive regression). Live-id revalidation per click/confirm via `firstOrNull` (replaces the backend's throwing `single { … }`). Bulk Pro-gating re-checks live id-set before gating, and re-gates after SAF returns. Sheet stays open after every action (parity with legacy; resolves the snackbar-vs-dismiss race). The list keeps its search field (debounced 300 ms) + 6 `FilterChip` set (USER / SYSTEM / ENABLED / DISABLED / ACTIVE / NOT_INSTALLED) + sort dropdown + asc/desc toggle, on a `LazyVerticalGrid` with `getSpanCount()` parity. Selection-mode `TopAppBar` promotes Delete / Exclude / SelectAll as icons; rest in a `DropdownMenu` overflow. Tag rendering moved to a Compose `AppInfoTagsRow` (8 states: system / debug / archived / uninstalled / disabled / active / APK base / APK bundle). The list's `appControl.state.data == null` triggers an initial scan instead of `navUp()` — auto-pop would dead-end Dashboard / launcher shortcut / ExclusionsList FAB entries. The dead-click FIXME at `ExclusionListViewModel.openAppControl()` is closed.
- **Analyzer tool — CLOSED**. DeviceStorage + StorageContent + Apps + AppDetails + Content converted; the four data-class routes (`StorageContentRoute`, `AppsRoute`, `AppDetailsRoute`, `ContentRoute`) drop their `from(handle)` + `typeMap` companions and switch to the Picker-style `bindRoute(route)` pattern. Three screens with required upstream data (`StorageContent`, `AppDetails`, `Content`) use sealed `Loading / Ready / NotFound` state instead of nullable defaulted `State()` — the Screen renders distinct paths and `LaunchedEffect(Unit) { vm.navUp() }` once on `NotFound`. Init-time work (StorageScanTask submit, AppDeepScanTask, process-death pop) lives in a separate `routeFlow.filterNotNull().take(1).launchIn(vmScope)` flow, NOT inside the `safeStateIn` chain, so `SharingStarted.WhileSubscribed(5000)` restarts after rotation don't re-fire scan submits. `bindRoute` is idempotent. The trend chart wraps the existing `SpaceHistoryChartView` via `AndroidView { factory = { SpaceHistoryChartView(it).apply { isCompact = true } } }` — `SpaceHistoryChartView.isCompact` was opened up to a public setter so AndroidView can opt-in. The 4 `AppDetails*VH` collapsed into one shared `AppDetailsGroupRow(group, labelRes, descRes, onClick)` composable. Selection mode mirrors legacy `installListSelection`: long-press always enters selection (even on inaccessible items, matching legacy `itemSelectionKey` semantics); selection-mode TopAppBar adds a Select All toggle; Delete / CreateFilter / Swiper actions are hidden when any selected item is inaccessible OR when `isReadOnly` (system content). Compose snackbar text uses `context.getString` / `resources.getQuantityString`, never `@Composable` resource APIs (which won't compile inside `LaunchedEffect`). Material3 `LinearProgressIndicator` ratios are clamped `0f..1f` with explicit divide-by-zero guards on both `storage.spaceUsed` and `parent.size`. Media category navigation switches `single()` to `singleOrNull()` with a no-op fallback (the legacy code would have crashed if the backend returned >1 media group). The `ExclusionListViewModel.openStoragePicker()` FIXME is closed — the previously dead `navTo(DeviceStorageRoute)` call now resolves through the new `AnalyzerNavigation` Compose entry. `MainNavGraph` drops 5 fragment registrations and 4 NavType vals (no remaining consumers).
- **Scheduler tool — CLOSED**. Manager + ScheduleItem converted; `ScheduleItemDialog` (BottomSheetDialogFragment2) replaced by a Material3 `ModalBottomSheet` route registered via `entry<ScheduleItemRoute> { route -> ScheduleItemSheetHost(scheduleId = route.scheduleId) }` (Picker-style entry-arg binding — same fix as AppActionRoute). Both VMs rebased on `ViewModel4`. The three RecyclerView VHs (ScheduleRow, AlarmHint, BatteryHint) became Compose row composables; the inline post-schedule commands editor (`MaterialAlertDialog` + multiline `TextInputEditText`) became a Compose `AlertDialog` with multiline `OutlinedTextField`. The `MaterialTimePicker` (FragmentManager-bound) became Compose Material3 `TimePicker` + `rememberTimePickerState(is24Hour = true)` inside an `AlertDialog`. **`ScheduleItemViewModel` uses a separate `MutableStateFlow<FormDraft>`** initialized once from the live schedule lookup and never re-synced from upstream — the AppAction-style read-only Picker pattern would let an external `schedulerManager.state` emission wipe the user's typing during edit. Two save-time guards: refuse to resurrect a deleted schedule (`!isCreate && live == null`) and refuse to save into an externally-enabled schedule (`live.isEnabled` — `SchedulerManager` only re-checks WorkManager on `scheduledAt` change, so a partial save would diverge displayed time from the actual delay). `canSave` normalizes blank labels to null. `Schedule.calcExecutionEta(...)` is wrapped in `runCatching` at the row level to defuse the infinite loop on `repeatInterval.toDays() == 0`. Battery-intent failures route through `errorEvents.emit(throwable)` rather than a snackbar (parity with the legacy `asErrorDialogBuilder`; ActivityNotFoundException.message is nullable). `updateCommandsAfterSchedule` replaces the legacy `getSchedule(id)!!` NPE-bait with a `firstOrNull` guard that no-ops when the schedule is missing or now enabled. Top bar wires the navigation-up arrow to `navUp` and the help icon to `WebpageTool` (legacy `setupWithNavController` + info menu parity). `JUnit5` covers route serialization (extended for the two `data object` routes) and the two save-time guards.
- **Outstanding FIXMEs** — none. All priority-1 conversion FIXMEs closed.
- **8 NavigationEntry sites in place** — every cross-cutting module + every tool now ships its own `NavigationEntry` (`AnalyzerNavigation` just landed alongside the others).

### Remaining work (priority order)

#### 1. Convert remaining tool list/details Fragments — CLOSED (0 screens left)
- ~~CorpseFinder~~ ✓ — List + Details converted; CorpseRoute retired
- ~~Squeezer~~ ✓ — Setup + List + ComparisonDialog converted; entire tool on Compose
- ~~Swiper~~ ✓ — Sessions + Status + Swipe converted; entire tool on Compose
- ~~Deduplicator~~ ✓ — List + Details + Cluster converted; ClusterRoute retired; PreviewDeletionDialog ported to Compose; entire tool on Compose
- ~~SystemCleaner~~ ✓ — List + Details + FilterContent + CustomFilterEditor converted; FilterContentRoute retired; CustomFilterEditor's two FIXMEs closed; entire tool on Compose
- ~~AppCleaner~~ ✓ — List + AppJunkDetails + AppJunk converted; AppJunkRoute retired; AppJunkViewModel folded into Details; DeleteSpec + AppCleanerTaskFactory locks in includeInaccessible=false for file-only deletes; entire tool on Compose
- ~~AppControl~~ ✓ — List + AppActionDialog converted; AppActionRoute now an `entry<>` route hosting a Material3 `ModalBottomSheet`; pure `buildAppActionItems` helper unit-tested; ExclusionsList AppControl FAB FIXME closed; entire tool on Compose
- ~~Scheduler~~ ✓ — Manager + ScheduleItem converted; `ScheduleItemRoute` bound via Picker entry-arg pattern; Compose Material3 `TimePicker` replaces `MaterialTimePicker`; FormDraft architecture for the editable form; save-time guards prevent resurrecting deleted schedules and mutating externally-enabled schedules; entire tool on Compose
- ~~Analyzer~~ ✓ — DeviceStorage + StorageContent + Apps + AppDetails + Content converted; sealed `Loading/Ready/NotFound` state for the 3 required-data screens; trend chart wraps `SpaceHistoryChartView` via `AndroidView`; selection-mode Select All; ExclusionList storage-picker FIXME closed; entire tool on Compose

#### 2. Cross-cutting Fragments — CLOSED
- ~~Preview: PreviewFragment + PreviewItemFragment~~ ✓ — converted to a single Compose Dialog with HorizontalPager + AndroidView{CoilZoomImageView}; PreviewItemRoute retired

#### 3. Final cleanup — CLOSED (5 commits)
- ~~Delete mainNavGraph.kt~~ ✓
- ~~Delete all base Fragment classes (Fragment2/3, DialogFragment2/3, PreferenceFragment2/3, BottomSheetDialogFragment2)~~ ✓ — plus orphan helpers (ToolbarHost, Service2, ViewModelLazyKeyed, DetailsPagerAdapter3, FragmentStatePagerAdapter4)
- ~~Delete all ViewBinding XML layouts, RecyclerView Adapters/ViewHolders~~ ✓ — orphan layouts + menus + dead-only `ids.xml` + paired XMLs for legacy custom views. After RecorderActivity converted, the entire `app-common-ui/.../lists/*` package + `LogFileAdapter` + both `debug_recorder_*.xml` are also gone (commit `2fcfb5e09`).
- ~~Remove Fragment navigation dependencies~~ ✓ — `navigation-fragment-ktx` + `navigation-ui-ktx` removed from all 16 modules (commit `54843cf43`). `dependencyInsight` on `fossDebugRuntimeClasspath` confirms zero transitive resolution. The 4 nav-using error-dialog callbacks (`ErrorDialogSetup` x2, `AutomationNoConsentException`, `InaccessibleDeletionException`) were rewired to declarative `fixActionRoute` / `infoActionRoute` fields on `LocalizedError`, dispatched through the existing `NavigationController` already wired into `ComposeErrorDialog` via `LocalNavigationController.current` (commit `423e5678b`). `androidx.fragment:fragment-ktx` is still pulled in transitively via `addAndroidUI()` for legacy Fragment-era helpers (LiveDataExtensions, ViewBindingExtensions, ActivityExtensions) — separate batch.
- ~~Remove ViewBinding build feature~~ ✓ — stripped from 14 modules. Kept in `app-common-ui` (MascotView), `app-common-automation` (AutomationControlView). `app/` viewBinding dropped in commit `2fcfb5e09`.
- ~~Remove runtime-livedata dependency~~ ✓
- ~~Remove LegacyNavigationBridge~~ ✓
- ~~RecorderActivity → Compose~~ ✓ — `ComponentActivity` + `setContent` (commit `4e51209c3`); cleanup cascade dropped `Activity2`, `ViewModel3`, `NavCommand`, `NavEventSource`, `LogFileAdapter`, the entire `lists/*` package, both `debug_recorder_*.xml` layouts, and `viewBinding` from `app/` (commit `2fcfb5e09`). `MainActivity` rebased on `ComponentActivity`. `Theming.kt` skips both Compose activities.

#### 4. Three legacy `Navigation.findNavController` callbacks — CLOSED (3 commits)
- ~~`ErrorDialogSetup.kt:28,42`, `AutomationNoConsentException.kt:23`, `InaccessibleDeletionException.kt:32`~~ ✓ — All 4 callsites rewired off `Navigation.findNavController(view, R.id.nav_host)` to declarative `LocalizedError.fixActionRoute` / `infoActionRoute` fields (commit `423e5678b`). `ComposeErrorDialog` dispatches them through the `NavigationController` parameter (already piped in via `LocalNavigationController.current` from `ErrorEventHandler`); the param had been unused. `R.id.nav_host` removed from `app-common/res/values/ids.xml`. Dead helpers also gone: `NavControllerExtensions.kt` (defined `safeNavigate(NavController)`), `SetupModuleSetup.kt` (`showFixSetupHint` / `installShowSetupHint` were never invoked), `SnackbarExtensions.kt` (`enableBigText` only consumed by the dead SetupModuleSetup). Bug fix: `ComposeErrorDialog.infoAction` tap now dismisses the dialog (was previously left open — legacy MaterialAlertDialogBuilder did this implicitly via the neutral button).
- After this batch, `dependencyInsight` confirms zero transitive `navigation-fragment-ktx` / `navigation-ui-ktx` resolution. 16 modules dropped both deps (commit `54843cf43`).

#### 5. Drop `androidx.fragment:fragment-ktx` and remaining Fragment-era helpers — CLOSED (1 commit)
- ~~Dropped `fragment-ktx`~~ ✓ (commit `825dadf2d`) from `addAndroidUI()` in `buildSrc/.../Dependencies.kt`. The three dead Fragment-era helpers are gone: `LiveDataExtensions.kt` (`observe2(Fragment)` overloads), `ViewBindingExtensions.kt` (`Fragment.viewBinding()` delegate), `ActivityExtensions.kt` (`viewParent` / `view` / `isContentViewSet` / `showFragment` / `todoToast`). Also dropped the dead `LoadingOverlayViewStyle` from `app/.../styles.xml` and the now-empty `app/.../res/values/ids.xml`.
- `androidx.fragment.app.Fragment` / `FragmentActivity` / `FragmentFactory` remain available transitively via `androidx.appcompat:appcompat:1.7.1` (in `addAndroidCore()`); the androidTest helpers (`HiltExtensions`, `EmptyFragmentActivity`) compile without an explicit dep.

#### 6. androidTest scaffolding cleanup — CLOSED (1 commit)
- ~~Dropped 4 dead androidTest files~~ ✓ (commit `27a7bfd32`): `MainActivityTest.kt` (mocked `state` as LiveData where the VM exposes Flow + verified a non-existent `vm.onGo()` — broken since the ViewModel4 migration), `ExampleFragmentTest.kt` (fully commented out — referenced the long-gone `MainFragment`/`MainFragmentVM`), `HiltExtensions.kt` (`launchFragmentInHiltContainer<T>()` — only consumed by the commented-out test), `EmptyFragmentActivity.kt` (Hilt host for the helper). No androidTest source references Fragment after this. `:app:compileFossDebugAndroidTestKotlin` + `:app:compileGplayDebugAndroidTestKotlin` both pass.

---

## Context

SD Maid SE currently ships on **XML layouts + Fragments** (62 Fragments, 207
layouts, 15 preference screens, ~50 ModularAdapters, 367 ViewBinding references
across ~16 modules). The `compose-rewrite` branch is the target branch for a
full replacement of the UI layer with Jetpack Compose.

The developer maintains four sibling Android projects — **butler, capod,
bluemusic, octi** — that are all fully on Compose. They share a battle-tested
architecture the SD Maid SE rewrite must match 1:1 so muscle memory, tooling,
and shared review standards carry over. The single most important design
constraint is: **produce a codebase a future `butler`/`capod` contributor can
navigate without surprises**.

### Confirmed decisions (from questions earlier in this planning session)

1. **Strategy**: Big-bang rewrite on `compose-rewrite`. No hybrid Fragment
   islands. Merge to `main` only when the whole app runs on Compose.
2. **Preference screens**: Rewrite all 13 preference XMLs in pure Compose. Drop
   `androidx.preference` dependency. Reuse existing `PreferenceScreenData` /
   `DataStore` backends.
3. **Visual fidelity**: Pixel-parity with current XML as the default. Small
   Material3 improvements allowed where they fall out of the rewrite naturally.
4. **VM event plumbing**: Match sibling projects exactly — `SingleEventFlow`
   plus `ViewModel3`/`ViewModel4` interfaces. Not keeping `SingleLiveEvent`.

---

## Target Architecture (ported verbatim from butler/capod)

Reference paths (butler canonical copy):

- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/ui/ViewModel2.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/ui/ViewModel3.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/ui/ViewModel4.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/flow/SingleEventFlow.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/error/ErrorEventSource.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/error/ErrorEventHandler.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/navigation/NavigationDestination.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/navigation/NavigationController.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/navigation/NavigationEntry.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/navigation/NavigationEventSource.kt`
- `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/navigation/NavigationEventHandler.kt`
- `~/projects/butler/main/app/src/main/java/eu/darken/butler/main/ui/MainActivity.kt`
- `~/projects/butler/main/app/src/main/java/eu/darken/butler/setup/ui/SetupNavigation.kt` (representative `NavigationEntry`)
- `~/projects/butler/main/buildSrc/src/main/java/Dependencies.kt` (`addAndroidUI()`, `addNavigation3()`)
- `~/projects/butler/main/.claude/rules/ui-guidelines.md` (Host/Page pattern)

### 0. Host/Page pattern (canonical convention — applied to every screen)

Reference: `~/projects/butler/main/.claude/rules/ui-guidelines.md`. This is
butler's documented rule and is used without deviation across butler, capod,
bluemusic, octi. **Every Compose screen in the SD Maid SE rewrite must follow
it.** Codex flagged this pattern as load-bearing — skipping it strands
per-screen VMs without event handling.

Each screen splits into two composables:

**`<Feature>ScreenHost`** — ViewModel wiring + side effects. The *only* place
that touches `hiltViewModel()`, collects one-shot events, launches activity
results, and starts intents. Always calls `ErrorEventHandler(vm)` and
`NavigationEventHandler(vm)` with the screen's own VM so its `errorEvents` /
`navEvents` streams actually get consumed.

```kotlin
@Composable
fun CorpseFinderListScreenHost(
    vm: CorpseFinderListViewModel = hiltViewModel(),
) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    // Activity-result / permission launchers live here.
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(vm::exportTo) },
    )
    LaunchedEffect(vm) {
        vm.requestExportEvent.collect { exportLauncher.launch("corpses.json") }
    }

    CorpseFinderListScreen(
        stateSource = vm.state,
        onItemClick = vm::onItemClick,
        onDeleteClick = vm::onDeleteClick,
    )
}
```

**`<Feature>Screen`** — pure presentation. Takes a `Flow<State>` and callbacks.
Does not reference the VM. Preview-friendly via `flowOf(fakeState)`.

```kotlin
@Composable
internal fun CorpseFinderListScreen(
    stateSource: Flow<CorpseFinderListViewModel.State>,
    onItemClick: (CorpseRow) -> Unit,
    onDeleteClick: () -> Unit,
) {
    val state by stateSource.collectAsStateWithLifecycle(initial = null)
    state ?: return
    // render UI
}
```

**Why this matters:** `ErrorEventHandler(vm)` / `NavigationEventHandler(vm)` at
the MainActivity root *only* collect `MainViewModel`'s events — not the ~60
feature VMs. Without per-screen Host composables, every feature VM's
`SingleEventFlow` is never collected and nav calls silently drop. The plan's
Phase 2 must ship this pattern; Phase 3+ must use it for every new screen
without exception.

**State shape** — pick one per screen based on phases:
- Single `data class State(...)` with defaults → updated via `.copy()`,
  used when the screen always has meaningful content.
- `sealed interface State { Initializing; Error; Ready(...) }` → used when
  phases are distinct (loading spinner vs content vs error state).

### 1. Foundation primitives (new code in `app-common-ui`)

| File | Role |
|------|------|
| `common/flow/SingleEventFlow.kt` | `Channel(BUFFERED)`-backed `AbstractFlow<T>` for one-shot events. Exposes `emit`, `tryEmit`, `emitBlocking`. |
| `common/error/ErrorEventSource.kt` | `interface ErrorEventSource { val errorEvents: SingleEventFlow<Throwable> }` |
| `common/error/ErrorEventHandler.kt` | `@Composable fun ErrorEventHandler(source)` — collects errors, shows `ErrorDialog`. Lives at every ScreenHost + MainActivity root. |
| `common/navigation/NavEvent.kt` | `sealed interface NavEvent { GoTo(destination, popUpTo, inclusive); Up; Finish }` |
| `common/navigation/NavigationDestination.kt` | `interface NavigationDestination : NavKey, java.io.Serializable` |
| `common/navigation/NavigationController.kt` | `@Singleton` wrapper around `NavBackStack<NavKey>` with `goTo`, `up`, `replace`, `setup(backStack)`. |
| `common/navigation/NavigationEntry.kt` | `interface NavigationEntry { fun EntryProviderBuilder<NavKey>.setup() }` |
| `common/navigation/NavigationEventSource.kt` | `interface NavigationEventSource { val navEvents: SingleEventFlow<NavEvent> }` |
| `common/navigation/NavigationEventHandler.kt` | `@Composable fun NavigationEventHandler(vararg sources)` — collects and routes events to `NavigationController`. |
| `common/navigation/LocalNavigationController.kt` | `CompositionLocal` that exposes the injected `NavigationController` to every Composable. |

### 2. ViewModel hierarchy (replaces current `ViewModel1`→`ViewModel3`)

The current `app-common-ui/src/main/java/eu/darken/sdmse/common/uix/ViewModel*.kt`
files get rewritten to match butler's shape:

```
ViewModel1  // logging/tag scaffold (keep as-is)
  └─ ViewModel2  // vmScope, launch(), asStateFlow(), launchInViewModel()
       └─ ViewModel3 : ErrorEventSource
            // errorEvents: SingleEventFlow<Throwable>
            // launchErrorHandler → errorEvents.emitBlocking
            └─ ViewModel4 : NavigationEventSource
                 // navEvents: SingleEventFlow<NavEvent>
                 // navTo(destination, popUpTo?, inclusive)
                 // navUp()
```

- Any screen VM that only shows data/errors extends `ViewModel3`.
- Any screen VM that navigates extends `ViewModel4`.
- Delete `SingleLiveEvent.kt`, `NavCommand.kt`, and all LiveData observation in
  the old `Fragment3`. These become dead code once the last Fragment is
  deleted.
- Keep `DynamicStateFlow` as-is — it already integrates cleanly with
  `collectAsStateWithLifecycle`.

### 3. Navigation (Navigation3, not Navigation Compose)

All four sibling projects use `androidx.navigation3` (alpha). The SD Maid SE
rewrite uses the same library. Key mechanics:

- Each feature module (e.g. `app-tool-corpsefinder`) ships a
  `*Navigation.kt` file implementing `NavigationEntry`. It registers every
  destination the module owns via the Navigation3 `entry<RouteType> { ... }`
  DSL and composes the corresponding screen host.
- Every `NavigationEntry` is bound into a Hilt `@IntoSet` multibinding:
  ```kotlin
  @Module @InstallIn(SingletonComponent::class)
  abstract class Mod {
      @Binds @IntoSet abstract fun bind(entry: CorpseFinderNavigation): NavigationEntry
  }
  ```
- `MainActivity` injects `Set<@JvmSuppressWildcards NavigationEntry>` and feeds
  the set into `NavDisplay`'s `entryProvider { navigationEntries.forEach {
  entry -> entry.apply { setup() } } }`.
- `NavigationController` is `@Singleton` and held in a `CompositionLocal`
  (`LocalNavigationController`). Screens navigate via `vm.navTo(destination)` —
  they never touch the controller directly. Root-level
  `NavigationEventHandler(vm)` funnels the events into the controller.

### 4. Routes (`NavigationDestination` replaces `NavCommand`/NavType map)

Current state: every route is already a `@Serializable` data class/object (see
`app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/routes/CommonRoutes.kt`
and the per-tool route files referenced in `MainNavGraph.kt`).

Migration rule — mechanical rename per route:

```kotlin
// before
@Serializable
data class UpgradeRoute(val forced: Boolean = false)

// after
@Serializable
data class UpgradeRoute(val forced: Boolean = false) : NavigationDestination
```

Serializable NavTypes (`StorageIdNavType`, `InstallIdNavType`, …) in the
current `MainNavGraph.kt` go away. Navigation3 passes the whole route object
into the `entry<Route> { destination -> ... }` lambda, so typed args are
already type-safe without an intermediate NavType wrapper.

The existing routes serialization tests
(`app-common-ui/src/test/java/eu/darken/sdmse/common/navigation/routes/CommonRoutesSerializationTest.kt`)
should survive the rename and continue to gate the migration — good canary.

### 5. Activity shell (`MainActivity`)

Replace the current ViewBinding + `NavHostFragment` layout. New shape:

```kotlin
@AndroidEntryPoint
class MainActivity : Activity2() {
    private val vm: MainViewModel by viewModels()
    @Inject lateinit var curriculumVitae: CurriculumVitae
    @Inject lateinit var theming: Theming
    @Inject lateinit var shortcutManager: ShortcutManager
    @Inject lateinit var navCtrl: NavigationController
    @Inject lateinit var navigationEntries: Set<@JvmSuppressWildcards NavigationEntry>

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { showSplashScreen && savedInstanceState == null }

        setContent {
            val themeState by vm.themeState.collectAsStateWithLifecycle()
            SdmSeTheme(state = themeState) {
                CompositionLocalProvider(LocalNavigationController provides navCtrl) {
                    ErrorEventHandler(vm)
                    NavigationEventHandler(vm)

                    val backStack = rememberNavBackStack<NavigationDestination>(DashboardRoute)
                    LaunchedEffect(Unit) { navCtrl.setup(backStack) }

                    NavDisplay(
                        backStack = backStack,
                        onBack = { navCtrl.up() },
                        entryDecorators = listOf(
                            rememberSavedStateNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            navigationEntries.forEach { it.apply { setup() } }
                        },
                    )
                }
            }
        }
    }
}
```

Deep-link handling for `ACTION_OPEN_APPCONTROL` / `ACTION_UPGRADE` moves to
`onNewIntent` → `vm.navTo(AppControlListRoute)` / `vm.navTo(UpgradeRoute())`
(same semantics, routed through the VM).

### 6. Theme — adopt butler's full theming system

The rewrite adopts butler's theming infrastructure byte-for-byte, not just a
one-off "translate colors.xml to `lightColorScheme()`" exercise. This gives
SD Maid SE the same 3-dimensional theme model butler uses
(`Mode × Style × Color`), the same extension points, and the same preview
conventions. Reference:
`~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/theming/`.

**Files to create under `app-common-ui/src/main/java/eu/darken/sdmse/common/theming/`:**

| File | Port from butler | Notes |
|------|------------------|-------|
| `ThemeMode.kt` | `ThemeMode.kt` | `SYSTEM / DARK / LIGHT` enum. SD Maid SE already has the same three modes in its current `Theming`; rename the enum to match butler's exact shape (`@Serializable` + `EnumPreference<ThemeMode>`). |
| `ThemeStyle.kt` | `ThemeStyle.kt` | `DEFAULT / MATERIAL_YOU / MEDIUM_CONTRAST / HIGH_CONTRAST`. The existing SD Maid `ThemeStyle` only has DEFAULT + MATERIAL_YOU; add the two contrast variants now (butler ships them). |
| `ThemeColor.kt` | `ThemeColor.kt` | Enum of palette choices. Start with `GREEN` (SD Maid's brand color, mapped from current `values/colors.xml`). Add additional palettes later if desired. |
| `ThemeState.kt` | `ThemeState.kt` | `data class ThemeState(mode, style, color)` with defaults matching SD Maid (SYSTEM / DEFAULT / GREEN). |
| `SdmSeColorsGreen.kt` | `ButlerColorsGreen.kt` | The current SD Maid green palette lifted from `values/colors.xml` + `values-night/colors.xml` — one object per `light`/`dark` `ColorScheme`. |
| `ThemeColorProvider.kt` | `ThemeColorProvider.kt` | `getLightColorScheme(color, style)` / `getDarkColorScheme(color, style)` switch resolving `ThemeColor × ThemeStyle` → `ColorScheme`. |
| `ColorSchemeExtensions.kt` | `ColorSchemeExtensions.kt` | Extension helpers for semantic colors not on the M3 `ColorScheme` (e.g. success/warning tones, list background stripes). |
| `SdmSeTypography.kt` | `ButlerTypography.kt` | Custom typography if SD Maid wants it; default to `Typography()` initially if butler's is too opinionated. |
| `SdmSeTheme.kt` | `ButlerTheme.kt` | The root Composable: `@Composable fun SdmSeTheme(state: ThemeState = ThemeState(), content: @Composable () -> Unit)`. Handles `dynamicColors` (API 31+ gate), `darkTheme` resolution, status/nav bar `InsetsController` appearance via `SideEffect`, memoized `remember(state, darkTheme, dynamicColors) { ... }` block, `MaterialTheme(colorScheme, typography, content)`. Copy butler's implementation verbatim and only change the brand name + color provider reference. |

**Runtime wiring (`Theming.kt` + `GeneralSettings`):**

- Existing `GeneralSettings.themeMode` / `themeStyle` stay; add
  `GeneralSettings.themeColor` as a new `DataStoreValue<ThemeColor>` (defaults
  to `GREEN`).
- `Theming.kt` exposes `val themeState: StateFlow<ThemeState>` combining the
  three flows. MainActivity collects this with
  `collectAsStateWithLifecycle()` and passes it into `SdmSeTheme(state = ...)`.
- `AppCompatDelegate.setDefaultNightMode` and
  `DynamicColors.applyToActivityIfAvailable` calls **stay** — they still
  affect `RecorderActivity` and the splash screen. Only MainActivity stops
  recreating on theme changes (the Compose theme recomposes).

**XML theme coexistence:**

- `themes.xml` / `colors.xml` are **not deleted**. Still required by:
  - `AndroidManifest.xml` — `android:theme="@style/AppTheme"` on the
    application and `android:theme="@style/AppThemeSplash"` on MainActivity.
  - `RecorderActivity` (debug recorder) — not part of this rewrite's scope.
  - `AppThemeSplash` — `installSplashScreen()` reads it on cold start.
  - `DialogTheme` — window background for any remaining dialog-style
    activities.
- Grep audit at end of Phase 9: any remaining `?attr/colorX` lookups or
  `MaterialColors.getColor()` calls in Kotlin — must be replaced with
  `MaterialTheme.colorScheme.*` or `SdmSeTheme.extras.*` (via
  `ColorSchemeExtensions`) since those call sites are now inside composition.

### 6b. File structure and composable extraction

**Rule**: each logical composable lives in its own file. Large screen files
with dozens of inline composables are not acceptable — extract anything that
could reasonably be previewed on its own into a sibling file.

- A `CorpseFinderListScreen.kt` file contains the `CorpseFinderListScreen`
  composable + its preview. It does **not** contain `CorpseFinderListRow`,
  `CorpseFinderToolbar`, `EmptyCorpsesState`, etc. Each of those lives in
  its own file alongside its preview.
- The `ScreenHost` and `Screen` composables for a single screen may live in
  the same file (they're tightly coupled — Host wires VM, Screen renders).
  That's an exception to the one-per-file rule.
- A screen's file tree typically looks like:
  ```
  corpsefinder/ui/list/
    CorpseFinderListScreen.kt      // Host + Screen
    CorpseFinderListViewModel.kt
    CorpseFinderListState.kt       // data class/sealed interface State
    items/
      CorpseRow.kt                 // row composable + @Preview2 preview
      CorpseListHeader.kt
      CorpseListEmpty.kt
      CorpseListActionBar.kt
  ```
- **Every composable gets a `@Preview2` preview** wrapped in `PreviewWrapper`.
  That's butler's `.claude/rules/ui-guidelines.md` rule copied directly:
  "When creating compose previews, use the `@Preview2` annotation, and wrap
  the UI element in a `PreviewWrapper`."
- The `Preview2` annotation and `PreviewWrapper` composable are ported from
  butler in Phase 2 (see `~/projects/butler/main/app-common/src/main/java/eu/darken/butler/common/compose/Preview2.kt`
  and `.../ComposePreviewHelpers.kt`). Also port `Preview2Tablet` for screens
  where tablet layout matters (Analyzer, Settings master/detail). The
  `SampleContent` helper from butler is useful for theme-only previews.
- Previews must compile and render in Android Studio's preview pane. That's
  the single most important productivity feedback loop during the rewrite —
  treat a broken preview as a blocker, not a warning.

### 7. Settings screens — plain Compose + small row toolkit

Current: 13 `res/xml/preferences_*.xml` + `PreferenceFragmentCompat`-based
fragments. `PreferenceScreenData` is the DataStore-side interface and is
**kept unchanged** — only the UI layer is rewritten.

**Not a preference framework.** Each settings screen is a plain Composable
function (`GeneralSettingsScreen`, `CorpseFinderSettingsScreen`, …) that
composes a `LazyColumn` and drops in rows. A tiny shared toolkit supplies the
most common row shapes, but screens are free to use raw Compose where a
scenario doesn't fit. This deliberately avoids building a
`PreferenceFragmentCompat`-equivalent abstraction that would drown in escape
hatches.

Small shared toolkit in
`app-common-ui/src/main/java/eu/darken/sdmse/common/compose/settings/`:

```
SettingsScaffold.kt     // TopAppBar (title + subtitle) + LazyColumn + insets + scroll behavior
SettingsCategory.kt     // section header
SettingsSwitchRow.kt    // (title, summary, icon, Flow<Boolean>, onToggle)
SettingsListRow.kt      // (title, currentLabel, onClick) — opens a dialog
SettingsSliderRow.kt    // numeric
SettingsClickRow.kt     // (title, summary?, icon?, onClick)
SettingsDialogRow.kt    // row that launches a Compose dialog editor
SettingsDivider.kt
```

Rows are thin — they accept raw `title`/`summary`/`icon` + a click callback.
They do **not** know about `DataStoreValue<T>`. The per-screen Composable
collects its state from the VM (`SettingsViewModel` extending `ViewModel4`)
and calls back into the VM on interaction. That means:

- **Pro-gated clicks** (GeneralSettingsFragment's upgrade-wall rows) —
  handled by the screen: `SettingsClickRow(onClick = { if (state.isPro)
  vm.onEditLanguage() else vm.navTo(UpgradeRoute(forced = true)) })`.
- **API-conditional visibility** — screen-level `if (Build.VERSION.SDK_INT >=
  X) { SettingsSwitchRow(...) }`.
- **Dynamic summaries** — summary is a `String` computed by the VM's state,
  re-rendered on state change.
- **Resume refresh** (SupportFragment, SetupFragment) — VM exposes a
  `refreshOnResume()` the host composable triggers in a
  `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }`.
- **Activity launches** (SupportFragment opens Play Store, debug log
  sharing) — the Host composable owns the `rememberLauncherForActivityResult`
  / `startActivity` calls, driven by a VM `SingleEventFlow` of intent requests.
- **Settings child navigation + title restoration** (SettingsFragment manages
  its own child back stack today) — becomes a normal Navigation3 route graph:
  `SettingsRoute` → `GeneralSettingsRoute` → `AppCleanerSettingsRoute` etc.
  Each is its own destination with its own `SettingsScaffold` title.
  Navigation3's back stack already restores scroll position via
  `rememberSavedStateNavEntryDecorator()`.

`androidx.preference:preference-ktx` is removed at the end of the rewrite.
`MaterialListPreference`, `SizeInputDialog`, `AgeInputDialog`,
`QualityInputDialog` are rewritten as Compose `AlertDialog`s under
`common/compose/settings/dialogs/` and reused across settings screens.

**Parity verification:** during Phase 5, for each tool module run a key-by-key
diff between the old `preferences_<tool>.xml` file and the new Compose
`<Tool>SettingsScreen` — grep every `createValue(...)` call in the matching
`*Settings.kt` and verify each key is surfaced in UI with the same
title/summary text (string resources stay untouched, making this a mechanical
check).

### 8. Lists (LazyColumn replaces ModularAdapter)

The `ModularAdapter` machinery (50 adapters, 19 `*VH` files, `BaseAdapter`, and
the `DataBinderMod`/`TypedVHCreatorMod` system in `app-common-ui/lists/`) is
deleted. Each row becomes a `@Composable` function taking the item and a
click callback:

```kotlin
@Composable
fun CorpseRow(item: CorpseRowData, onClick: () -> Unit, onCheck: (Boolean) -> Unit) { ... }
```

Multi-type lists (e.g. `DeduplicatorListGridVH` vs `DeduplicatorListLinearVH`)
become a single `when` on a sealed `RowState` inside a `LazyColumn` /
`LazyVerticalGrid`. Keys come from existing IDs on the row data.

### 9. Dialogs

- `DialogFragment3` / `BottomSheetDialogFragment2` deletions.
- Modal dialogs (`AppActionDialog`, `DebugLogSessionsDialog`, `ScheduleItemDialog`)
  become Navigation3 destinations rendered as Compose `Dialog`/`ModalBottomSheet`
  inside their `entry<Route> { ... }` blocks. Navigation3 supports a dialog
  presentation mode via the entry provider scope.
- Ad-hoc confirmation dialogs move inline into the hosting screen with
  `remember { mutableStateOf(...) }`.

### 10. Custom Views and non-trivial list interactions

Inventoried here because they're each a design decision, not a mechanical
port. Phase 0 must populate any that this table misses — assume the list is
not exhaustive until proven.

| Current | Replacement / approach |
|---|---|
| `BreadCrumbBar<T>` | `LazyRow` of `AssistChip`s with overflow ellipsis. Generic over `T` via a `@Composable` label slot. |
| `MascotView` | `Box` with `Image` overlay, positioned by `Modifier.offset`. |
| `ProgressOverlayView` | Compose `ProgressOverlay` composable (Box + CircularProgressIndicator + optional label). Used by every tool's scan screen — must be available in Phase 2. |
| `AppInfoTagView` (AppControl) | Compose `AppInfoTag` composable — `Row { Icon + Text }` with the same four severity variants. |
| `SpaceHistoryFragment` chart | Keep existing chart library rendered via `AndroidView { ... }`. Revisit later. |
| `BelowAppBarBehavior` | CoordinatorLayout goes away. Scroll-aware app bars via `TopAppBarScrollBehavior.exitUntilCollapsed`. |
| Size/Age/Quality input dialogs | Compose `AlertDialog` + `OutlinedTextField` under `common/compose/settings/dialogs/`. |

**Non-trivial list interactions — design individually, don't mass-rewrite:**

| Screen | Current behavior | Compose approach |
|---|---|---|
| `DashboardCardConfigFragment` | Drag-reorder via ItemTouchHelper | `reorderable` library (`sh.calvin.reorderable:reorderable`) inside `LazyColumn`. Evaluate whether butler/capod already picked a library — reuse if so. |
| `ArbiterConfigFragment` (Deduplicator) | Drag-reorder priority list | Same as above. |
| `SystemCleanerListFragment` | CAB multi-select with ActionMode menu | Explicit `SelectionState` in VM. Scaffold swaps TopAppBar to a selection bar when `selection.isNotEmpty()`. BackHandler exits selection before popping nav. |
| `ExclusionListFragment` | CAB multi-select | Same pattern as SystemCleaner. |
| `DeduplicatorListFragment` / `SwiperSwipeFragment` | Custom gesture / pager | Case-by-case. Swiper's swipe deck may need `Modifier.pointerInput` + animation state; might be the single screen where a bespoke Compose rewrite takes days rather than hours. |
| Grid/linear layout switching (Deduplicator, Squeezer) | Two adapter types | One `LazyVerticalGrid` with `GridCells.Fixed(1)` vs `Fixed(n)` driven by state. Row Composable is layout-agnostic. |

Phase 0 must produce the complete inventory (see Phase 0 checklist below).

### 11. Results, launchers, and external intents

Fragment-based result delivery disappears with Fragments. The plan must
actively migrate these, not discover them screen by screen. The mechanisms
currently in use:

**A. `setFragmentResult` / `setFragmentResultListener` (cross-screen returns)**

Example: `PickerFragment` returns the chosen path via
`setFragmentResult(resultKey, bundle)`, and callers like
`SwiperSessionsFragment` listen with `setFragmentResultListener(requestKey) {
... }`. Phase 0 must grep every `setFragmentResult*` call and map each request
key to its listener site. Replacement pattern under Navigation3:

- Caller navigates to the picker with `vm.navTo(PickerRoute(request =
  PickerRequest(...)))`.
- Picker VM emits the result through a singleton `PickerResultBus` (new
  class, `@Singleton`, a `SingleEventFlow<PickerResult>` keyed by request
  ID) *before* popping itself with `vm.navUp()`.
- Caller's VM collects from `PickerResultBus` in its init block, filters by
  request ID, and updates state.

A small `ResultBus<Key, Value>` helper in `app-common-ui/common/results/`
generalizes this. One bus per logical result type (`PickerResultBus`,
`ExclusionResultBus`, …). Keyed results survive process death via Navigation3's
saved state because they live in the consuming VM, not in the NavHost.

**B. `registerForActivityResult` (system intents)**

Example: `SetupFragment` has three `ActivityResultLauncher`s (manage-all-files
permission, exact-alarm permission, app-info settings intent). `AppActionDialog`
launches a document picker. Replacement pattern:

- The launcher lives in the **Host composable** for that screen, via
  `rememberLauncherForActivityResult(contract) { result -> vm.onResult(result) }`.
- The VM exposes a `SingleEventFlow<LaunchRequest>` where `LaunchRequest` is a
  screen-local sealed class (`OpenSettings(pkg)`, `CreateDocument(suggestedName)`,
  …).
- The Host's `LaunchedEffect` consumes the flow and calls
  `launcher.launch(...)`.
- Phase 0 must grep every `registerForActivityResult` call and list the
  contract + the VM handler site.

**C. `context.startActivity(intent)` from fragments (no result)**

Example: `SupportFragment` opens Play Store, debug log share, email intents.
Replacement pattern: VM emits `SingleEventFlow<Intent>`, Host composable
collects and calls `context.startActivity(intent)` — mirrors butler's
`SaverWorkspacePageHost` pattern
(`vm.shareIntentEvent.collect { context.startActivity(intent) }`).

**D. System back stack listeners / `OnBackPressedDispatcher`**

Any custom back handling today (e.g. exit-selection-before-popping) becomes
`BackHandler(enabled = ...) { ... }` inside the relevant composable.

### 12. Gradle / Dependencies

Target versions (pinned to butler's reference, which is ~2026-03-24):

```kotlin
// buildSrc/src/main/java/Versions.kt additions
object Compose {
    const val bom = "2025.12.00"
    const val foundationOverride = "1.11.0-alpha01"
}
object Navigation3 {
    const val core = "1.0.0-alpha08"
    const val lifecycleVm = "2.10.0-alpha03"
    const val adaptive = "1.0.0-alpha02"
}
```

New helper in `buildSrc/src/main/java/Dependencies.kt`:

```kotlin
fun DependencyHandlerScope.addCompose() {
    val composeBom = platform("androidx.compose:compose-bom:${Versions.Compose.bom}")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.foundation:foundation:${Versions.Compose.foundationOverride}")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0-alpha01")

    implementation("io.coil-kt:coil-compose:2.7.0") // Coil is already present
}

fun DependencyHandlerScope.addNavigation3() {
    implementation("androidx.navigation3:navigation3-runtime:${Versions.Navigation3.core}")
    implementation("androidx.navigation3:navigation3-ui:${Versions.Navigation3.core}")
    implementation("androidx.navigation3:navigation3-ui-android:${Versions.Navigation3.core}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:${Versions.Navigation3.lifecycleVm}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3-android:${Versions.Navigation3.lifecycleVm}")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:${Versions.Navigation3.adaptive}")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3-android:${Versions.Navigation3.adaptive}")
}
```

Enable in module Gradle files (`app/build.gradle.kts`,
`app-common-ui/build.gradle.kts`, every `app-tool-*/build.gradle.kts`,
`app-common-*/build.gradle.kts` that currently has Fragment UI):

```kotlin
android {
    buildFeatures {
        compose = true
        viewBinding = false  // remove once that module is converted
    }
}
// No composeOptions block needed — Kotlin 2.2.10 uses the Kotlin Compose
// Compiler Plugin (`org.jetbrains.kotlin.plugin.compose`). Add it to the
// root plugins DSL and apply per-module.
```

Dependencies to remove at the end of the rewrite:

- `androidx.preference:preference-ktx`
- `androidx.navigation:navigation-fragment-ktx`
- `androidx.navigation:navigation-ui-ktx`
- `androidx.fragment:fragment-ktx` (last, after every `Fragment` subclass is
  deleted — `@AndroidEntryPoint class MainActivity : Activity2()` is fine
  without it)

Additions to `addTesting()`:

```kotlin
androidTestImplementation("androidx.compose.ui:ui-test-junit4")
debugImplementation("androidx.compose.ui:ui-test-manifest")
// Robolectric-based Compose tests run via testImplementation, not androidTest —
// butler uses the same dependency in both configurations.
testImplementation("androidx.compose.ui:ui-test-junit4")
```

### 13. Compose testing (Robolectric — no instrumentation)

Compose UI tests are in scope **when they can run as regular JVM unit tests**,
i.e. under Robolectric or without any Android context at all. Instrumentation
tests on a real device are still out of scope (CLAUDE.md says "no UI tests
required" — the spirit is no *instrumentation* tests; JVM-executable Compose
tests don't burn CI cycles and give real feedback).

**Reference**:
`~/projects/butler/main/app-common-test/src/main/java/testhelpers/ComposeTest.kt`.
Butler runs Compose UI tests under `RobolectricTestRunner` with
`createComposeRule()` as a JUnit 4 rule, documented limitations and all:

- No native bitmap support (`ImageBitmap()` → NullPointerException).
- No drawing / `captureToImage()` (deadlocks).
- Text measurement is approximate (~20px height, 1px/char).
- Use for: **component behavior, click targets, content assertions, state
  transitions** — *not* pixel-level visual verification.

**Port to SD Maid SE** in Phase 2:

```kotlin
// app-common-test/src/main/java/testhelpers/ComposeTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [34])
abstract class ComposeTest : BaseTest() {
    @get:Rule val composeTestRule = createComposeRule()
}
```

**What to test (non-exhaustive list of patterns worth covering):**

- A settings row composable: clicking the row fires the callback with the
  correct argument. Toggle reflects input state.
- A state-driven composable: given `State.Loading` renders a spinner, given
  `State.Ready(items)` renders each item, given `State.Error` renders error
  text with retry button.
- A selection bar: when `selection.count > 0`, the top bar shows
  "N selected" and delete/exclude actions; when selection is empty, it
  shows the normal toolbar.
- A multi-state `when` inside a screen: each branch renders a visually
  distinct subtree, verified via `onNodeWithTag(...)`.
- Dialog confirmation flows: opening the dialog, confirming/dismissing,
  asserting the callback receives the right value.

**What NOT to test via ComposeTest** (keep these as regular `BaseTest`
JUnit 5 tests):

- Pure business logic in ViewModels (`state.copy(...)` transformations,
  coroutine flows). These use `runTest2` + Kotest matchers per
  `.claude/rules/testing.md` — unchanged by the rewrite.
- Any tests that would require real rendering / screenshots / touch input.

**Pure composables with no Context** (stateless pure renderers like
formatters, wrapper layouts, data mappers) can be unit-tested without
Robolectric at all — just call the composable inside a `composeTestRule`
with no `Config` needed, or call the mapping function directly from a
`BaseTest`.

---

## Phased Delivery (single long-lived branch, phases are internal milestones)

All phases land as commits on `compose-rewrite`. No merges to `main` until
**Phase 10** is clean. Each phase should leave `./gradlew assembleFossDebug`
green, with the debug app runnable on a device.

Phase overview: **0** repo inventory → **0b** screen inventory + baseline
screenshots → **1** Gradle foundation → **2** `app-common-ui` foundation
primitives → **3** Activity shell + end-to-end spike → **4** main module →
**5** tool modules → **6** Analyzer → **7** cross-cutting modules → **8**
route cleanup → **9** final cleanup → **10** visual regression sweep.

### Phase 0 — Preflight inventory (do not skip)

**This is the gating phase for the whole rewrite.** The outputs of Phase 0
become a checklist in `scratch/compose-rewrite-inventory.md` (kept on
the branch) that every later phase ticks off against. Trying to execute the
rewrite without this inventory means discovering landmines screen-by-screen.

Artifacts to produce:

1. **Fragment + dialog + destination inventory** — one row per destination:
   `<Route> | <FragmentClass> | module | state-class complexity (S/M/L) |
   list-kind (none/simple/grid/reorder/CAB/pager) | custom-view deps |
   has-dialog? | extends-VM3-or-VM4?`.
2. **Route arg inventory** — grep every `SavedStateHandle.toRoute<...>()`
   and every `typeMap = mapOf(typeOf<...>() to ...NavType)`. Record
   which routes have non-primitive args, which have nullable args, which
   are currently using `serializableNavType` wrappers.
3. **`setFragmentResult*` inventory** — grep `setFragmentResult`,
   `setFragmentResultListener`, `FragmentResultListener`. Map request keys
   → producer site + consumer site. Planned replacement: which `ResultBus`
   does each pair migrate to?
4. **`registerForActivityResult` inventory** — grep every occurrence. Record
   contract type + the VM method that consumes the result. Planned
   replacement: which `SingleEventFlow<LaunchRequest>` lives on the VM?
5. **`context.startActivity(` from Fragments** — grep. Record the intent
   source (Play Store URL, email, external app) + who triggers it. All move
   to VM `SingleEventFlow<Intent>` → Host collector.
6. **`SingleLiveEvent` usage audit** — grep every `SingleLiveEvent<...>`
   declaration and every `.observe2(` call site. Expect ~54 usages.
   Classify each: (a) pure nav event → `navEvents`, (b) pure error
   event → `errorEvents`, (c) other one-shot (snackbar, dialog open,
   scroll-to-top) → new per-VM `SingleEventFlow<SomeLocalEvent>` consumed
   by a per-screen `LaunchedEffect` in the Host composable.
7. **`.asLiveData()` / `.observe2(` of state streams** — any non-event
   LiveData that today carries screen state. Target replacement:
   `StateFlow<T>` + `collectAsStateWithLifecycle`.
8. **Custom view inventory** — globs for classes extending `View`,
   `FrameLayout`, `LinearLayout`, `ConstraintLayout` under any `ui/`
   package across all modules. Classify each: rewrite in Compose vs wrap
   in `AndroidView`.
9. **Adapter / ViewHolder inventory** — list every `*Adapter.kt` +
   `*VH.kt`. Mark which are trivial (plain list) vs non-trivial (multi-
   type, grid/linear switch, animation, selection state).
10. **Manifest activities** — list every `<activity>` in
    `AndroidManifest.xml`. Anything other than `MainActivity`,
    `ShortcutActivity`, `RecorderActivity` is a finding — some of those may
    need Compose conversion, some keep XML themes.
11. **Hidden GlobalScope / lifecycleScope usage in Fragments** — grep
    `viewLifecycleOwner.lifecycleScope` and `lifecycleScope.launch` inside
    Fragments. Each site needs a conscious migration target (VM-side
    coroutine, Host `LaunchedEffect`, or a side effect composable).
12. **Existing Compose baseline** — `./gradlew assembleFossDebug` runs
    green on the current branch (prove the starting point before touching
    anything).
13. **Kotlin Compose Compiler plugin compat** — Kotlin 2.2.10 + Compose
    Compiler plugin `org.jetbrains.kotlin.plugin.compose` (same plugin
    butler uses; no `composeOptions` block needed).

Phase 0 exits only when the inventory file is complete. The table of
Fragment → VM → state complexity feeds directly into the per-phase ordering
later; it is not optional documentation.

### Phase 0b — Screen inventory + baseline screenshots (visual regression baseline)

Feeds directly into Phase 10. The entire rewrite targets pixel parity as the
default (see Section: Confirmed decisions #3), and the only way to enforce
that across 60+ screens is by capturing the *current* app surface before we
touch anything, then re-capturing after Phase 9 and diffing the two sets.

**Executes in the main agent — do not spawn sub-agents.** Debugbadger's
`sessionToken` and `snapshot_id` are per-agent; a sub-agent cannot hand
them back, and Phase 10's before/after image diff requires the main agent
to read the PNGs directly. Building the APK via `devtools:build-runner` is
still fine — that agent only returns a pass/fail summary and doesn't touch
the device session.

**Device prep**

Target device: **`emulator-5558`** (pinned by the user). All capture and
verification work in Phase 0b and Phase 10 runs against this emulator so
before/after diffs are apples-to-apples.

1. Build the current (pre-rewrite) FOSS debug APK via `devtools:build-runner`
   → `./gradlew :app:assembleFossDebug`.
2. Install on `emulator-5558` via the `debugbadger:*` MCP tools called
   directly from the main agent: `debugbadger:device_list` →
   `debugbadger:device_select(emulator-5558)` → `debugbadger:app_install(<apk>)`.
   Keep the returned `sessionToken` — every later capture in Phase 0b and
   Phase 10 needs the same token from the same agent.
3. Populate representative test data. Prefer `tooling/testdata-generator/`
   (its exact entrypoints are an inventory artifact for Phase 0). If the
   generator can't populate a specific tool's state (e.g. SystemCleaner on
   a clean device has nothing to clean), accept an "empty state"
   screenshot for that surface and note it in the inventory.
4. Fix the device into a consistent visual state for the whole capture run:
   - Theme mode: **Light** (default).
   - Theme style: **DEFAULT** (no Material You).
   - Language: **en** (matches the base `strings.xml`).
   - Display size / font scale: **default**.
   - Screen orientation: **portrait**.
5. Record these settings at the top of the inventory file so Phase 10 can
   match them.

**Screen inventory (grouped per tool/module)**

Produced as a table in `scratch/compose-rewrite-inventory.md`
alongside the Phase 0 artifacts. Each row has: `id | tool | kind (Activity/
Fragment/BottomSheet/Dialog/Overlay) | navigation path | population notes |
screenshot filename`. Start from this scaffolding — Phase 0b must expand it
to cover everything the app exposes:

- **Framework / Activities**
  - `MainActivity` (single-activity host)
  - `RecorderActivity` (debug-only)
  - `ShortcutActivity` (not user-visible; document but don't screenshot)
- **Main module**
  - Onboarding: Welcome, Privacy, Versus, Setup
  - Dashboard (with and without data)
  - Setup (Root / Shizuku / ADB variants)
  - Settings root
  - General settings
  - Dashboard card config
  - Support / contact form
  - Debug log sessions dialog
  - Data Areas
  - Log Viewer
  - Upgrade (FOSS flavor)
  - Upgrade (GPlay flavor) — requires gplay build, capture separately
- **CorpseFinder**
  - List (scan empty / scan with results)
  - AppCorpse details
  - Single corpse details
  - Settings
- **SystemCleaner**
  - List (scan empty / scan with results)
  - Filter content details
  - Filter content list
  - Custom filter list
  - Custom filter editor (new / existing)
  - Settings
- **AppCleaner**
  - List (scan empty / scan with results)
  - App junk details
  - App junk sub-details (per junk type)
  - Settings
- **AppControl**
  - List (filter chips visible)
  - Action dialog (bottom sheet)
  - Settings
- **Deduplicator**
  - List — linear layout
  - List — grid layout
  - Details
  - Cluster view
  - Arbiter config
  - Settings
- **Squeezer**
  - Setup
  - List
  - Settings
- **Swiper**
  - Sessions (empty / populated)
  - Swipe deck (active card)
  - Status
  - Settings
- **Scheduler**
  - Manager (empty / populated)
  - Schedule item dialog
  - Settings
- **Analyzer**
  - Device storage overview
  - Apps view
  - App details
  - Content view
  - Storage content
- **Stats**
  - Reports
  - Space history chart
  - Affected paths
  - Affected pkgs
  - Settings
- **Exclusions**
  - List
  - Path editor
  - Pkg editor
  - Segment editor
- **Shared UI**
  - Picker
  - Preview
  - Preview item
- **Dialogs / overlays (reached from above)**
  - Error dialog (trigger by simulated failure)
  - Delete confirmation dialog
  - Size input dialog
  - Age input dialog
  - Quality input dialog
  - Progress overlay (scan in progress)

Any destinations Phase 0 finds that don't appear on this list get added.
Missing a screen here means missing it in Phase 10, which means shipping a
visual regression.

**Capture procedure**

For each row in the inventory:

1. Drive debugbadger to navigate from a known root (app cold start) to the
   target screen. Record the navigation sequence in the row's "navigation
   path" column.
2. Wait for steady state — no spinners, no progress bars, settled scroll
   position. Use `debugbadger:wait_for_idle` where available.
3. Capture the screen (`debugbadger:observe_screen` or the appropriate
   screenshot-producing tool — loaded on-demand at Phase 0b time).
4. Save the image as
   `scratch/screenshots/before/<tool>/<screen-id>.png`.
5. If the screen has multiple meaningful states (empty vs populated, linear
   vs grid, dark vs light), capture each with a state suffix
   (`..._empty.png`, `..._populated.png`, `..._grid.png`).
6. Append the filename to the inventory row.

**Exit criteria for Phase 0b**

- Every row in the inventory has at least one PNG on disk.
- `scratch/compose-rewrite-inventory.md` has been updated to include
  the device-state prelude (theme, locale, orientation, font scale) and
  every screen's filename.
- A sanity commit is made on `compose-rewrite` with the inventory file and
  the `before/` screenshot tree so the baseline is durably preserved even if
  later phases rewrite the tree around them.

### Phase 1 — Gradle foundation
- Add Compose BOM + Navigation3 helpers in `buildSrc`.
- Apply Kotlin Compose Compiler plugin at root.
- Enable `buildFeatures { compose = true }` in `app` and `app-common-ui`
  (leave `viewBinding = true` alive during the rewrite).
- **Smoke test**: add a throwaway `@Composable fun Placeholder()` with
  `@Preview`, confirm it compiles and tooling works.

### Phase 2 — `app-common-ui` foundation primitives
Create, one commit each, following the butler reference files byte-for-byte
where possible:
1. `SingleEventFlow.kt`.
2. `NavEvent.kt`, `NavigationDestination.kt`.
3. `ErrorEventSource.kt`, `ErrorEventHandler.kt` (+ `ErrorDialog.kt`
   composable that reuses `asErrorDialogBuilder`'s content as a Compose dialog).
4. `NavigationEventSource.kt`, `NavigationEventHandler.kt`,
   `NavigationController.kt`, `NavigationEntry.kt`,
   `LocalNavigationController.kt`.
5. Rewrite `ViewModel2.kt`/`ViewModel3.kt`, add `ViewModel4.kt`.
   The old `SingleLiveEvent<NavCommand?>` path is removed; subclasses don't
   compile until phase 4 refits them — that's fine, this branch is big-bang.
6. **Theming system** (Section 6): `ThemeMode.kt`, `ThemeStyle.kt`,
   `ThemeColor.kt`, `ThemeState.kt`, `SdmSeColorsGreen.kt`,
   `ThemeColorProvider.kt`, `ColorSchemeExtensions.kt`, `SdmSeTypography.kt`,
   `SdmSeTheme.kt`. Wire `themeState: StateFlow<ThemeState>` on `Theming.kt`.
7. **Preview infrastructure** (Section 6b): port `Preview2.kt`,
   `Preview2Tablet` annotations, `ComposePreviewHelpers.kt` with
   `PreviewWrapper` + `SampleContent`. All under `common/compose/preview/`.
8. Settings row toolkit under `common/compose/settings/` (SettingsScaffold,
   SettingsCategory, SettingsSwitchRow, SettingsListRow, SettingsSliderRow,
   SettingsClickRow, SettingsDialogRow, SettingsDivider + size/age/quality
   dialog composables in `common/compose/settings/dialogs/`). Each row
   ships with a `@Preview2` preview using `PreviewWrapper`.
9. Common composables under `common/compose/`: `SdmSeScaffold.kt`,
   `LoadingIndicator.kt`, `EmptyState.kt`, `ConfirmationDialog.kt`,
   `BreadCrumbRow.kt`, `ProgressOverlay.kt`, `AppInfoTag.kt`. Each with
   its own `@Preview2` preview.
10. `ResultBus.kt` helper in `common/results/` for cross-screen result
    delivery (see Section 11).
11. **Compose test base** (Section on testing below): port butler's
    `ComposeTest.kt` base class into
    `app-common-test/src/main/java/testhelpers/ComposeTest.kt`. Add the
    `androidx.compose.ui:ui-test-junit4` dependency. Write one smoke test
    that renders `SampleContent` under `PreviewWrapper` and verifies the
    button click — proves the Robolectric + Compose pipeline works.

### Phase 3 — Activity shell + end-to-end spike

**Step 3a: Preserve every MainActivity behavior that exists today.**
The new MainActivity must carry all of these before the phase can close:

- `installSplashScreen()` + `setKeepOnScreenCondition { showSplashScreen &&
  savedInstanceState == null }` — gated on `vm.readyState` like today.
- `enableEdgeToEdge()` (butler uses this too).
- Early window background color (butler's pre-composition hack in
  `~/projects/butler/main/app/src/main/java/eu/darken/butler/main/ui/MainActivity.kt`)
  to prevent white/black flash before the Compose theme loads. Use
  `values/colors.xml` constants for the two modes.
- Inject `CurriculumVitae`, `Theming`, `ShortcutManager`, `navCtrl`,
  `navigationEntries` — same Hilt bindings as today.
- `curriculumVitae.updateAppOpened()` on create.
- `vm.keepScreenOn.collect { ... }` in a `LaunchedEffect` that adds/removes
  `FLAG_KEEP_SCREEN_ON` on `window`.
- `onResume` equivalent: `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
  vm.checkUpgrades(); vm.checkErrors() }` inside the composition.
- `onNewIntent` / intent handling for shortcut actions
  (`ACTION_OPEN_APPCONTROL`, `ACTION_UPGRADE`) — dispatch through
  `vm.navTo(AppControlListRoute)` / `vm.navTo(UpgradeRoute())`. Butler does
  this via a `savedIntent` field processed in a `LaunchedEffect` tied to
  `vm.startupState` so routing happens after the nav graph is ready.
- `navController.addOnDestinationChangedListener` breadcrumb
  (`Bugs.leaveBreadCrumb("Navigated to ...")`) — replaced by a
  `LaunchedEffect(backStack.size) { Bugs.leaveBreadCrumb("Navigated to
  ${backStack.lastOrNull()}") }`.
- `vm.errorEvents` routed via the root `ErrorEventHandler(vm)` — the old
  `observe2` call.
- `onSaveInstanceState`'s `B_KEY_SPLASH` handling — carried by Compose
  `rememberSaveable` on `showSplashScreen` state.

**Step 3b: The end-to-end spike.** Before converting any real screen, build
*one* throwaway destination that exercises every risky integration point. This
proves the pattern works end-to-end. If any of these fail, stop and fix the
foundation.

The spike screen: `SpikeScreenHost(vm = hiltViewModel())` registered at a
`SpikeRoute(val id: InstallId?, val label: String)` destination (nullable
custom-type arg, matches real routes like `AppJunkDetailsRoute`).

Spike acceptance checklist:
- [ ] `SpikeViewModel : ViewModel4` receives the route instance via
      assisted injection or `SavedStateHandle.toRoute<SpikeRoute>()` — which
      one is dictated by whether Navigation3 + Hilt under
      `rememberViewModelStoreNavEntryDecorator()` populates the saved state
      with the serialized route. **Prove which works before Phase 4.**
      (Butler's `SetupNavigation.kt` calls the ViewModel-factory pattern via
      `entry<DestinationSetup> { destination -> SetupScreenHost(options = ...) }`
      passing the `destination` object *directly* as a Composable param —
      that's the canonical route-to-VM plumbing. Match it.)
- [ ] The `SpikeScreen` renders state collected via
      `collectAsStateWithLifecycle`.
- [ ] Navigating to SpikeRoute from the dashboard via `vm.navTo(SpikeRoute(
      id = null, label = "hello"))` works and the nullable arg survives
      serialization.
- [ ] `vm.errorEvents.tryEmit(IllegalStateException("boom"))` triggers the
      Host's `ErrorEventHandler` and shows the Compose `ErrorDialog`.
      Negative test: triggering the same error from `MainViewModel` must
      show the dialog *via the MainActivity-root handler*, not via the
      SpikeScreenHost — proves the two handlers are independent.
- [ ] `vm.navUp()` pops back to the dashboard.
- [ ] A `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(...))`
      inside `SpikeScreenHost` opens the system picker on button press and
      delivers the URI back to the VM.
- [ ] A `SingleEventFlow<Intent>` on the VM, collected in the host,
      successfully `context.startActivity(intent)` for a dummy VIEW intent.
- [ ] Background the app, kill the process, relaunch — the Spike route
      restores with the original label and the back stack is intact (tests
      `rememberSavedStateNavEntryDecorator()` + Navigation3 saved state).
- [ ] Theme switch Light → Dark → Material You during the spike screen is
      visible, does not recreate the Activity, and the splash theme still
      works on cold start.
- [ ] A hardware back press on SpikeRoute pops the nav stack; a hardware
      back press on the root dashboard shows a toast per butler's
      double-back-to-exit convention (verify whether SD Maid wants this —
      optional).

Only when every checkbox passes does Phase 3 exit. The spike code is then
deleted before Phase 4 begins — its purpose is to prove the foundation, not
to ship.

**Step 3c: MainActivity replacement ships.** Delete `main_activity.xml`, the
`NavHostFragment`, `MainActivityBinding` imports. `MainViewModel` becomes a
`ViewModel4`. Add `AppNavigation.kt` as a throwaway `NavigationEntry` that
registers just `DashboardRoute` → "Dashboard will go here" placeholder
composable so the real dashboard conversion in Phase 4 can swap in.

### Phase 4 — `main` feature (dashboard, settings, onboarding, setup, upgrade)
This is the biggest module. Convert in this order (each a commit):
1. `OnboardingWelcomeFragment`, `OnboardingPrivacyFragment`,
   `OnboardingSetupFragment`, `VersusSetupFragment`.
2. `DashboardFragment` → `DashboardScreen` + `DashboardViewModel` extending
   `ViewModel4`. All dashboard cards become Composables.
3. `SetupFragment` (with its option bag).
4. `SettingsFragment` root index + `DashboardCardConfigFragment` +
   `SupportContactFormFragment` + `DebugLogSessionsDialog` (now an inline
   `ModalBottomSheet`).
5. `UpgradeFragment` (both `foss` and `gplay` flavors — each flavor ships its
   own `UpgradeNavigation.kt` in its flavor source set, bound via
   `@Binds @IntoSet`. Same pattern as butler's
   `foss/...upgrade/ui/UpgradeNavigation.kt`).
6. `DataAreasFragment`, `LogViewFragment`.

At the end of Phase 4 the entire `main` module no longer has Fragments except
maybe stubbed settings pages. Add `MainNavigation.kt` `@IntoSet` binding.

### Phase 5 — Tool modules (parallelizable, one tool per commit range)

For each tool module (`app-tool-corpsefinder`, `app-tool-systemcleaner`,
`app-tool-appcleaner`, `app-tool-deduplicator`, `app-tool-appcontrol`,
`app-tool-scheduler`, `app-tool-squeezer`, `app-tool-swiper`):

1. Add `buildFeatures { compose = true }` + `addCompose()` +
   `addNavigation3()` to the module's build file.
2. Convert each Fragment screen to a `*ScreenHost` + `*Screen` Composable
   pair + `*ViewModelX : ViewModel4` (or `ViewModel3` for terminal screens).
3. Replace `*Adapter` / `*VH` files with row Composables.
4. Rewrite the module's preferences XML as a Compose settings screen using
   the settings row toolkit from Phase 2.
   Preserve any existing filtered setup affordances on gated rows:
   if a disabled/badged preference currently opens `SetupRoute` with a
   specific `typeFilter`, the Compose replacement must keep the same target
   setup-module set and keep `showCompleted = true` for those targeted
   launches.
5. Create `<Tool>Navigation.kt` (Hilt `@IntoSet`-bound) registering every
   destination the module owns via Navigation3 `entry<Route>` calls.
6. Delete the module's `res/layout/*.xml`, `res/xml/preferences_*.xml`,
   and ViewBinding references. `buildFeatures.viewBinding = false`.
7. Use `devtools:build-runner` to keep verbose output isolated.

Recommended execution order (simplest → hardest, so the pattern is proven
before the hardest screens):

1. CorpseFinder (simple list + details, clean forensics path)
2. SystemCleaner (adds custom filter editor — more dialogs)
3. AppCleaner (most tabs, multiple detail layers)
4. AppControl (rich action dialog + filter bar)
5. Scheduler (small but uses picker dialogs)
6. Squeezer (list + setup flow + settings)
7. Deduplicator (grid/linear switch + arbiter config — most adapters)
8. Swiper (custom swipe UI — most interactive)

### Phase 6 — Analyzer (`app/…/analyzer/ui/`)

Analyzer has 5 nested destinations (DeviceStorage → Apps → AppDetails and
Content → StorageContent) and a storage-bar visualization. Convert as a
single unit. Reuse the proportional bar from the recently reverted grid-view
branch as a Composable primitive.

### Phase 7 — Cross-cutting feature modules

- `app-common-stats` (ReportsFragment, SpaceHistoryFragment,
  AffectedPathsFragment, AffectedPkgsFragment)
- `app-common-exclusion` (list + 3 editor screens)
- `app-common-picker` (PickerFragment) — verify Navigation3 can host the
  storage-access-framework handoff
- `common/previews` (PreviewFragment + PreviewItemFragment)
- `common/filter` (CustomFilterEditorFragment etc.)
- `common/debug/logviewer` and `common/upgrade/ui`

Each ships its own `NavigationEntry` multibound to Hilt. Module-level
`viewBinding = false` at the end.

### Phase 8 — Route cleanup (most of this already happened incrementally)

The Phase 3 spike already proved the end-to-end route-to-VM plumbing, and
Phases 4–7 add `: NavigationDestination` to each route file as their
module gets converted. This phase is the final sweep:

- Grep for any remaining `serializableNavType(...)` declarations — delete.
  Navigation3 doesn't need them because the destination object is passed
  directly into the `entry<Route>` lambda.
- Delete the `app-common-ui/common/navigation/SerializableNavType.kt` helper
  itself.
- Delete `MainNavGraph.kt` (replaced by the Set of `NavigationEntry`s).
- Extend `CommonRoutesSerializationTest` to round-trip every route type,
  including those with nullable/custom-typed args. This is the strongest
  single regression guard for the whole rewrite.
- Audit the diff of every `*Route.kt` file: the only change should be
  adding `: NavigationDestination`. Any other drift from the rewrite is a
  smell.

### Phase 9 — Cleanup

1. Delete every `Fragment2`/`Fragment3`/`DialogFragment2`/`DialogFragment3`/
   `PreferenceFragment2`/`PreferenceFragment3`/`BottomSheetDialogFragment2`,
   `SingleLiveEvent`, `NavCommand`, old `Fragment3` nav observation code.
2. Delete all `res/layout/*.xml` except the splash theme reference.
3. Delete all `res/xml/preferences_*.xml` and custom preference attrs.
4. Remove `androidx.preference:preference-ktx`,
   `androidx.navigation:navigation-fragment-ktx`, and
   `androidx.navigation:navigation-ui-ktx` from `addAndroidUI()`.
5. Remove `androidx.fragment:fragment-ktx` if nothing still imports it.
6. Remove `viewBinding = true` from every module — set to `false` or drop.
7. Run `./gradlew lintVitalFossRelease lintVitalGplayRelease assembleFossDebug assembleGplayDebug testFossDebugUnitTest`
   (via `devtools:build-runner`) and fix everything.
8. Crowdin/string audit: ensure no new untranslated strings leaked in. Run
   `/android-translation:strings`.
9. Smoke test the debug APK on a real device using `debugbadger:debug` for the
   happy path of each tool (scan → review → delete) plus onboarding. Grep the
   remaining Kotlin for any leftover `?attr/colorX` or
   `MaterialColors.getColor()` usages — must be replaced with
   `MaterialTheme.colorScheme.*`.

### Phase 10 — Review and confirmation (visual regression sweep)

Final gate before the rewrite can merge to `main`. Matches Phase 0b's
screenshot capture against the new build, screen-for-screen, to catch visual
regressions that slipped through per-phase verification.

**Executes in the main agent — do not spawn sub-agents.** Phase 10 replays
Phase 0b's captures and diffs them against a re-capture of the same
screens. Both the capture and the before/after comparison must run in the
same agent that holds the debugbadger session: sub-agents cannot inspect
the saved images (the diff uses the main agent's multimodal Read
capability) and they cannot receive the session token. Any step that
delegates capture or comparison to a sub-agent breaks the diff.

**Device prep (must match Phase 0b exactly)**

Same device (`emulator-5558`), same theme mode (Light), same theme style
(DEFAULT), same locale (en), same orientation (portrait), same font scale
(default). Use the settings prelude recorded at the top of the inventory
file. If **any** dimension differs from Phase 0b the diff is meaningless.

Install the **new** FOSS debug APK built from the final `compose-rewrite`
tip. Re-populate test data using the same `tooling/testdata-generator/`
invocation (recorded in Phase 0b).

**Recapture procedure**

Iterate every row in `scratch/compose-rewrite-inventory.md`:

1. Drive debugbadger to the screen using the navigation path recorded in
   Phase 0b (adjusted for any Compose-era route renames, which must have
   been noted during Phases 4–7).
2. Wait for steady state.
3. Capture to
   `scratch/screenshots/after/<tool>/<screen-id>.png` using the same
   filename convention as `before/`.
4. Visually compare `before/` and `after/`. Classify each screen as:
   - **Pass** — pixel-identical or within acceptable Material3-driven
     variance (minor spacing, ripple timing, elevation shadows).
   - **Intentional deviation** — a documented design improvement (visual
     fidelity decision #3 allowed small improvements "where they fall out
     of the rewrite naturally"). The deviation must be explicitly
     recorded in a `deviations.md` alongside the screenshots, with a
     one-line justification per screen.
   - **Regression** — unintentional difference. Blocks merge. Must be
     fixed and the screen recaptured.

**Regression report**

Produce `scratch/regression-report.md` with one row per screen:
status (pass/deviation/regression), filename, and a link to the `before/`
and `after/` images. Attach it to the merge-back-to-main PR.

**Interactive smoke test (not just static screenshots)**

Static diffs miss behavioral regressions. For each tool, walk the happy
path end-to-end on device:

1. Open the tool from the dashboard.
2. Run a scan (or trigger equivalent data-gathering).
3. Review results. Scroll the list. Open at least one detail screen.
4. Perform the primary action (delete / exclude / dedupe / etc.).
5. Observe the success/undo snackbar.
6. Return to the dashboard and confirm state refresh.

Also walk these cross-cutting flows:

- Onboarding from a freshly-installed app (wipe + reinstall).
- Upgrade screen from both FOSS and GPlay flavors.
- Settings → each tool's settings subscreen.
- Exclusions: add a path exclusion, verify it's respected on the next scan.
- Theme switch: Light → Dark → Material You, no activity recreation for
  MainActivity.
- Shortcut entry: launch via launcher long-press → AppControl shortcut,
  verify it lands on `AppControlListRoute` directly.
- Process death: kill the app mid-screen (3 levels deep), relaunch, verify
  back-stack restore.
- Picker round-trip: SwiperSessions opens the picker, selects a path, sees
  the result on return.

**Exit criteria for Phase 10**

- All screens in the inventory have an `after/` capture.
- Zero unresolved regressions in the report.
- All intentional deviations are documented.
- Every cross-cutting flow above is signed off in the report.
- `./gradlew lintVitalFossRelease lintVitalGplayRelease` both green.
- `./gradlew test` across all modules green.
- Only then is the branch ready to merge to `main`.

---

## Critical files to create/modify

**Create:**
- `app-common-ui/src/main/java/eu/darken/sdmse/common/flow/SingleEventFlow.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/error/ErrorEventSource.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/error/ErrorEventHandler.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/error/ErrorDialog.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavEvent.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavigationDestination.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavigationController.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavigationEntry.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavigationEventSource.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavigationEventHandler.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/LocalNavigationController.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/uix/ViewModel4.kt`
- **Theming (ported from butler)**:
  - `common/theming/ThemeMode.kt`
  - `common/theming/ThemeStyle.kt`
  - `common/theming/ThemeColor.kt`
  - `common/theming/ThemeState.kt`
  - `common/theming/SdmSeColorsGreen.kt`
  - `common/theming/ThemeColorProvider.kt`
  - `common/theming/ColorSchemeExtensions.kt`
  - `common/theming/SdmSeTypography.kt`
  - `common/theming/SdmSeTheme.kt`
- **Preview infrastructure (ported from butler)**:
  - `common/compose/preview/Preview2.kt` (+ `Preview2Tablet`)
  - `common/compose/preview/PreviewWrapper.kt`
  - `common/compose/preview/SampleContent.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/compose/settings/*.kt` (settings row toolkit)
- `app-common-ui/src/main/java/eu/darken/sdmse/common/compose/settings/dialogs/*.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/compose/*.kt` (Scaffold, Loading, Empty, ConfirmationDialog, BreadCrumbRow, ProgressOverlay, AppInfoTag — each with its own file and `@Preview2` preview)
- `app-common-ui/src/main/java/eu/darken/sdmse/common/results/ResultBus.kt`
- `app-common-test/src/main/java/testhelpers/ComposeTest.kt` (Robolectric-backed base class, ported from butler)
- `scratch/compose-rewrite-inventory.md` (Phase 0 + 0b artifact —
  Fragment/VM/state table, route args, fragment results, activity launchers,
  custom views, adapters, plus the per-screen table feeding Phase 10).
- `scratch/screenshots/before/**/*.png` (Phase 0b artifact — baseline
  captures of every screen, committed to the branch so they survive
  subsequent rewrites).
- `scratch/screenshots/after/**/*.png` (Phase 10 artifact).
- `scratch/screenshots/deviations.md` (Phase 10 artifact — documents
  every intentional visual change with justification).
- `scratch/regression-report.md` (Phase 10 artifact — per-screen
  pass/deviation/regression status, attached to the merge PR).
- One `<Feature>Navigation.kt` file per module (~16 files) with
  `@Binds @IntoSet` Mod, replacing the contents of `MainNavGraph.kt`.
- One `*ScreenHost.kt` / `*Screen.kt` pair per converted screen (~62 × 2),
  plus one file per extracted child composable (rows, headers, empty states,
  action bars — each with its own preview).

**Modify:**
- `app-common-ui/src/main/java/eu/darken/sdmse/common/uix/ViewModel2.kt` —
  adopt butler's shape (abstract `launchErrorHandler`, `asStateFlow`,
  `launchInViewModel`).
- `app-common-ui/src/main/java/eu/darken/sdmse/common/uix/ViewModel3.kt` —
  drop `NavEventSource`, keep only `ErrorEventSource` with `SingleEventFlow`.
- `app/src/main/java/eu/darken/sdmse/main/ui/MainActivity.kt` — full rewrite.
- `buildSrc/src/main/java/Versions.kt` — add Compose/Navigation3 blocks.
- `buildSrc/src/main/java/Dependencies.kt` — add `addCompose()`,
  `addNavigation3()`. Remove `preference-ktx` and nav-fragment from
  `addAndroidUI()` in Phase 9.
- Every `app-tool-*/build.gradle.kts` and `app-common-*/build.gradle.kts` that
  currently has UI — enable Compose, disable ViewBinding.
- Every `*Route.kt` file — add `: NavigationDestination` superinterface.

**Delete (Phase 9):**
- `app-common-ui/src/main/java/eu/darken/sdmse/common/SingleLiveEvent.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/navigation/NavCommand.kt`
- `app-common-ui/src/main/java/eu/darken/sdmse/common/uix/Fragment2.kt`,
  `Fragment3.kt`, `DialogFragment2.kt`, `DialogFragment3.kt`,
  `BottomSheetDialogFragment2.kt`, `PreferenceFragment2.kt`,
  `PreferenceFragment3.kt`.
- All `res/layout/*.xml` across all modules (207 files).
- All `res/xml/preferences_*.xml` across all modules (13 files).
- `app/src/main/java/eu/darken/sdmse/main/ui/navigation/MainNavGraph.kt`.
- The 50 `*Adapter.kt` files and 19 `*VH.kt` files.
- `BaseAdapter.kt`, `ModularAdapter.kt`, `DataBinderMod.kt`,
  `TypedVHCreatorMod.kt` and the rest of `common/lists/`.

---

## Reused code (do not rewrite)

- `DynamicStateFlow` — keep. Works with `collectAsStateWithLifecycle()` via a
  tiny extension on the consumer side.
- `DispatcherProvider`, `DefaultDispatcherProvider` — keep.
- `PreferenceScreenData` interface + `PreferenceStoreMapper` — keep.
  Settings UI layer writes through DataStore directly; `PreferenceStoreMapper`
  is kept only for the preference-to-DataStore bridge that's used outside of
  UI (e.g. settings migrations).
- `GeneralSettings` and every per-tool `*Settings` class — keep.
- All ViewModels' business logic — keep. Only their base class changes
  (`ViewModel3` → `ViewModel4` where they used to emit `navEvents`) and their
  nav calls (`navigateTo(CorpseDetailsRoute)` → `navTo(CorpseDetailsRoute)`).
- `Theming.kt` DataStore state — keep; expose as StateFlow for Compose.
- `asErrorDialogBuilder` dialog text/icon/reporting logic — port the content
  into a Composable `ErrorDialog` but reuse the same copy/translations.
- Every `@Serializable` route data class — keep the shape; only add interface.
- All `common/navigation/routes/*.kt` typed argument wrappers.
- The `CurriculumVitae`, `ShortcutManager`, `Theming`, `MainViewModel` Hilt
  bindings on `MainActivity`.

---

## Verification

**Per-phase** (gates every commit batch):
- `devtools:build-runner` → `./gradlew :app:assembleFossDebug` must be green.
- `devtools:build-runner` → `./gradlew testFossDebugUnitTest` must pass.
  This runs both the existing `BaseTest`-based JUnit 5 suite **and** the new
  Robolectric-backed `ComposeTest` classes added in Phase 2+. Gate with
  `CommonRoutesSerializationTest` and any Compose component test that ships
  with the phase.
- Previews for every composable added in the phase must render in Android
  Studio's preview pane — a broken preview is a blocker.
- Install on device, open the converted screen, tap every interactive
  element present in the old XML version, and tick off the corresponding
  row in the Phase 0 inventory file. The inventory is the progress
  thermometer for the whole rewrite.

**Failure-path verification — the most likely regression surface.**
Rotation alone is insufficient. After any module conversion, run through the
following scenarios on a real device for every converted screen:

- **Process death**: `adb shell am kill eu.darken.sdmse` (or Developer
  Options → "Don't keep activities") while the screen is visible, reopen
  the app. Route must restore, VM state must either restore (via
  `rememberSavedStateNavEntryDecorator`) or cold-start cleanly.
- **Back-stack restore on cold restart**: navigate 3 levels deep, kill the
  app, relaunch — back stack should restore to the deepest route.
- **Shortcut entry path**: from a launcher shortcut
  (`ACTION_OPEN_APPCONTROL`, `ACTION_UPGRADE`), confirm the initial route
  is the shortcut target, not the dashboard.
- **Picker result round-trip**: navigate caller → picker → pick path → pop
  → caller receives the result via `ResultBus`. Do this after process
  death too (caller VM must re-subscribe on restore).
- **Missing external apps**: trigger every `startActivity(intent)` site
  (Play Store, email, file manager) on a device that doesn't have the
  target app installed — must not crash. `SetupFragment` already has
  fallback handling for missing settings/apps; this behavior must be
  preserved.
- **Permission launchers**: deny the permission twice, then allow it.
  The VM must receive each outcome. `MANAGE_EXTERNAL_STORAGE`,
  `SCHEDULE_EXACT_ALARM`, and `POST_NOTIFICATIONS` are the three existing
  contracts — verify each one.
- **Back-into-selection**: in CAB-style multi-select screens
  (SystemCleaner, Exclusion), select items, press back — must exit
  selection without popping the nav stack. Press back again — must pop.
- **Theme switching on all alive activities**: switch Light → Dark →
  Material You while MainActivity is visible. Do the same while
  RecorderActivity is open (debug builds) — RecorderActivity will still
  recreate because it uses the XML theme, which is expected.
- **Configuration change storm**: rotate device 5 times in a second on a
  screen with a large list. LazyColumn must preserve scroll position and
  not drop selection state.
- **Deep route args round-trip**: navigate to a destination with a
  nullable custom-typed argument (e.g. `AppJunkDetailsRoute(installId =
  null)`), rotate, back. Argument should survive serialization.

**End-to-end** (before considering the rewrite ready to merge):
- `./gradlew lintVitalFossRelease lintVitalGplayRelease` both green.
- `./gradlew assembleFossDebug assembleFossRelease assembleGplayDebug assembleGplayRelease`.
- `./gradlew test` across all modules.
- Manual smoke test using `debugbadger:debug` (connected Android device) for
  each cleaning tool's happy path: scan → review result → delete → undo.
  Include onboarding, settings, and the upgrade screen in both flavors.
- Memory sanity check: open Analyzer on a large device, scroll through the
  file list (the old ModularAdapter was hand-tuned; `LazyColumn` needs
  stable `key`s on each item or you'll regress frame pacing).
- Crowdin/string audit: run `/android-translation:strings` to confirm no
  new untranslated strings leaked in.

---

## Risks & mitigations

0. **Per-screen event handler coverage — highest risk.**
   The MainActivity root only collects `MainViewModel`'s nav/error events.
   Every feature VM's `SingleEventFlow`s need a `Host` composable to call
   `ErrorEventHandler(vm)` + `NavigationEventHandler(vm)` — if a screen skips
   the Host pattern, its nav calls silently drop.
   - *Mitigation*: the Host/Page split is mandatory for every screen,
     documented in Section 0 of this plan and enforced by pattern-matching
     the butler guidelines file. Phase 3 spike must include a negative test
     that triggers an error event from a feature VM and confirms the Host
     handler (not the MainActivity handler) is what shows the dialog.
   - *Secondary check*: grep for `ErrorEventHandler(` at the end of every
     phase — the count must equal the number of feature ScreenHosts.

1. **Navigation3 is alpha** — `1.0.0-alpha08`. API may shift mid-rewrite.
   - *Mitigation*: pin the exact version used by butler (which is on the
     same alpha). Monitor butler's bumps and follow them.
   - Butler's `NavigationEntry` uses `EntryProviderBuilder<NavKey>`, capod's
     uses `EntryProviderScope<NavKey>` — the API evolved. Use whichever the
     pinned alpha ships; pick the newer (`EntryProviderScope`) only if the
     pinned version supports it.

2. **Hilt + Navigation3 ViewModel scoping** — sibling projects use
   `rememberViewModelStoreNavEntryDecorator()`. Each entry gets its own
   `ViewModelStoreOwner`. Passing the VM into the entry block means using
   `hiltViewModel()` inside the `entry<Route> { ... }` lambda.
   - *Mitigation*: reference butler's entry impls (e.g.
     `SetupNavigation.kt`, `WorkspaceNavigation.kt`) — they're the canonical
     pattern. The Phase 3 spike must validate this before Phase 4 starts.

3. **Preference screen parity** — 15 preference screens with ~150 individual
   preferences. Easy to miss one and ship a regression.
   - *Mitigation*: do a preference-by-preference diff between the old XML and
     the new Compose file during Phase 5, commit-by-tool. Grep every
     `preferences_*.xml` key against `createValue(…)` calls in the matching
     `*Settings.kt` to catch orphans.
   - *Extra parity check*: preserve today's filtered setup wiring for gated
     settings rows. `showSetupHint` call sites in the legacy tool settings
     screens currently route to `SetupRoute(showCompleted = true, typeFilter =
     ...)`; Compose migrations must not silently downgrade those rows to a
     generic unfiltered setup entry.

4. **Custom chart in SpaceHistoryFragment** — likely uses MPAndroidChart or
   similar non-Compose lib.
   - *Mitigation*: keep inside `AndroidView { … }`. Not worth a bespoke
     Compose chart during this rewrite; revisit in a later branch.

5. **Hidden LiveData fan-out** — any ad-hoc `LiveData` observed in Fragments
   other than nav/error events (e.g. loading states, result lists). Some
   ViewModels use `asLiveData()` for non-event state.
   - *Mitigation*: grep `\.observe2\(` and `\.asLiveData` across the repo
     in Phase 0. Convert each to StateFlow +
     `collectAsStateWithLifecycle()`. Phase 0 should produce a concrete list
     that scopes the conversion work.

6. **Thread contention in `SingleEventFlow`** — butler's `errorEvents` uses
   `emitBlocking` from the `CoroutineExceptionHandler`, which is called from
   arbitrary threads. Validated by butler in production.
   - *Mitigation*: none needed; straight port.

7. **FOSS vs GPlay flavor upgrade screen** — two different
   `upgrade_fragment.xml` files today. Butler handles this with
   flavor-specific `UpgradeNavigation.kt` files under `foss/` and `gplay/`
   source sets, each with their own `@Binds @IntoSet`.
   - *Mitigation*: copy butler's exact layout
     (`app/src/foss/...` + `app/src/gplay/...`) — verified to work.

8. **`android.nonTransitiveRClass=true`** — CLAUDE.md warns that
   `MaterialColors.getColor()` with widget attrs crashes at runtime. With
   the XML theme kept but no longer driving the Compose UI, any leftover
   `?attr/...` lookups in Kotlin during transitional screens must be
   audited and replaced with `MaterialTheme.colorScheme.*`.

9. **Merge conflicts with `main`** — SD Maid SE ships actively. If the
   rewrite lands in a single session the conflict window is small, but any
   work that ships to `main` *during* the rewrite session must be rebased in
   after.
   - *Mitigation*: start from a freshly-rebased `compose-rewrite` branch at
     the top of the session. Hold any unrelated merges to `main` until the
     rewrite lands.

---

## Out of scope

- **Instrumentation tests** that require a connected device or emulator.
  JVM-executable Compose tests under Robolectric are in scope — see
  Section 13.
- Refactoring non-UI modules (`app-common`, `app-common-io`, `app-common-pkgs`,
  etc.) — they're untouched.
- Introducing Kotlin Multiplatform / any new non-Compose architecture work.
- Replacing Hilt.
- Replacing Room / DataStore / Moshi / Coil / FlowShell.
- Onboarding copy changes, feature additions, or any behavior change beyond
  what's needed for UI parity.
- Building additional theme palettes beyond `ThemeColor.GREEN`. The
  infrastructure supports multiple palettes (butler has three), but only the
  current SD Maid green is implemented in this rewrite. Additional palettes
  are a follow-up branch.
