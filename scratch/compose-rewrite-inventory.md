# Compose Rewrite Inventory (Phase 0)

Reference for the Fragment → Compose rewrite. Every later phase ticks against this file.

## Device state prelude (Phase 0b / Phase 10)

```
device: emulator-5558
theme_mode: LIGHT
theme_style: DEFAULT
locale: en
orientation: portrait
font_scale: 1.0
captured_at: <ISO-8601, filled by Phase 0b>
```

## Summary statistics

| metric | value |
|---|---|
| Total Fragment-derived classes (all bases) | 62 |
| ... Fragment3 (content fragments) | 44 |
| ... Fragment2 (container, no VM contract) | 1 (`SettingsFragment`) |
| ... DialogFragment3 | 2 (`PreviewFragment`, `PreviewItemFragment`) |
| ... BottomSheetDialogFragment2 | 3 (`AppActionDialog`, `ScheduleItemDialog`, `DebugLogSessionsDialog`) |
| ... PreferenceFragment2 | 10 |
| ... PreferenceFragment3 | 2 (`GeneralSettingsFragment`, `StatsSettingsFragment`) |
| Routes (`@Serializable` destination objects/classes) | 52 |
| ... With nullable custom-typed args | 8 |
| ... With non-nullable custom-typed args | 11 |
| ... data objects (no args) | 28 |
| `SingleLiveEvent<...>` declarations (feature VMs) | 47 |
| Adapters (`*Adapter.kt`) in ui/ packages (real list adapters) | 46 |
| ... Adapters with selection state | 15 |
| ... Adapters with multi-view-type dispatch | 15 |
| ... Pager adapters (replaced by HorizontalPager) | 6 |
| ViewHolder files (`*VH.kt`) | 93 |
| Custom View subclasses (non-VH) | 10 |
| Manifest activities (main) | 3 (MainActivity, ShortcutActivity, RecorderActivity) |

Per-module Fragment count:

| module | Fragment* count | Dialog* count | Pref* count |
|---|---|---|---|
| `app` (main) | 10 | 1 (`DebugLogSessionsDialog`) | 4 (`SettingsIndexFragment`, `GeneralSettingsFragment`, `SupportFragment`, `AcknowledgementsFragment`) |
| `app-common-coil` | 0 | 2 (`PreviewFragment`, `PreviewItemFragment`) | 0 |
| `app-common-exclusion` | 4 | 0 | 0 |
| `app-common-picker` | 1 | 0 | 0 |
| `app-common-stats` | 4 | 0 | 1 (`StatsSettingsFragment`) |
| `app-tool-analyzer` | 5 | 0 | 0 |
| `app-tool-appcleaner` | 4 | 0 | 1 (`AppCleanerSettingsFragment`) |
| `app-tool-appcontrol` | 1 | 1 (`AppActionDialog`) | 1 (`AppControlSettingsFragment`) |
| `app-tool-corpsefinder` | 3 | 0 | 1 (`CorpseFinderSettingsFragment`) |
| `app-tool-deduplicator` | 4 | 0 | 1 (`DeduplicatorSettingsFragment`) |
| `app-tool-scheduler` | 1 | 1 (`ScheduleItemDialog`) | 1 (`SchedulerSettingsFragment`) |
| `app-tool-squeezer` | 2 | 0 | 1 (`SqueezerSettingsFragment`) |
| `app-tool-swiper` | 3 | 0 | 1 (`SwiperSettingsFragment`) |
| `app-tool-systemcleaner` | 5 | 0 | 1 (`SystemCleanerSettingsFragment`) |

Notes:
- FOSS and GPlay each ship their own `UpgradeFragment` variant (same class name, different source set). Counted as one logical destination, two files.
- `SettingsFragment` is a container (`Fragment2`, no VM3 contract) that hosts PreferenceFragments via `childFragmentManager` + `PreferenceFragmentCompat.onPreferenceStartFragment`, NOT via the MainNavGraph. All PreferenceFragments above are child fragments of it.
- "Dialog" helpers that are NOT Fragments (so not in this inventory): `OneClickOptionsDialog`, `PreviewCompressionDialog`, `PreviewDeletionDialog`, `SqueezerOnboardingDialog`, `ComparisonDialog` (plain `DialogFragment` not `DialogFragment3`), `RecorderConsentDialog`, `ShortRecordingDialog`, `AgeInputDialog`, `SizeInputDialog`, `QualityInputDialog`, `ErrorDialogSetup`.

---

## 1. Fragments / destinations

### Module: `app` (main)

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| onboarding_welcome | app | `OnboardingWelcomeRoute` | `OnboardingWelcomeFragment` | Fragment | VM3 | none | no | — | S | Checks legacy SDM pkg to branch nav. |
| onboarding_versus | app | `VersusSetupRoute` | `VersusSetupFragment` | Fragment | VM3 | none | no | — | S | Static welcome → privacy. |
| onboarding_privacy | app | `OnboardingPrivacyRoute` | `OnboardingPrivacyFragment` | Fragment | VM3 | none | no | — | S | Toggles MOTD and update-check. |
| onboarding_setup | app | `OnboardingSetupRoute` | `OnboardingSetupFragment` | Fragment | VM3 | none | no | — | S | Closes onboarding. |
| dashboard | app | `DashboardRoute` | `DashboardFragment` | Fragment | VM3 | grid | yes (`PreviewDeletionDialog`, `OneClickOptionsDialog`, `ShortRecordingDialog`, AlertDialogs) | `MascotView`, 17-type DashboardAdapter | L | 952 LoC VM; multi-card list via DashboardAdapter (17 view types); BottomBar; MainAction FAB; extensive dialog handling. |
| setup | app | `SetupRoute` | `SetupFragment` | Fragment | VM3 | grid | yes (AlertDialogs) | 9 setup-card VHs | L | 214 LoC; 3 activity-result launchers; reads route options. Uses `SetupAdapter` with 9 view types. |
| settings_container | app | `SettingsRoute` | `SettingsFragment` | Fragment (Fragment2) | — (no VM) | none (container) | no | — | S | Container for PreferenceFragments via childFragmentManager. `onPreferenceStartFragment` for child nav, not MainNavGraph. |
| settings_index | app | — (child) | `SettingsIndexFragment` | PreferenceFragment2 | — (uses own `SettingsViewModel`) | none (preference) | no | — | S | Entry prefs screen inside SettingsFragment. |
| settings_general | app | — (child) | `GeneralSettingsFragment` | PreferenceFragment3 | VM3 (GeneralSettingsVM) | none (preference) | no (uses `OneClickOptionsDialog`) | — | S | Theme mode/style/language/ROM type/update check. |
| settings_acknowledgements | app | — (child) | `AcknowledgementsFragment` | PreferenceFragment2 | — | none (preference) | no | — | S | Static. |
| settings_support | app | — (child) | `SupportFragment` | PreferenceFragment2 | — (`SupportViewModel`) | none (preference) | no | — | S | Refresh on resume; navigates to `SupportFormRoute` and `DebugLogSessionsRoute`. |
| support_contact_form | app | `SupportFormRoute` | `SupportContactFormFragment` | Fragment | VM3 | simple | yes (post-send AlertDialog) | — | M | Complex form with chip groups, log session picker. |
| debug_log_sessions | app | `DebugLogSessionsRoute` | `DebugLogSessionsDialog` | BottomSheetDialogFragment2 | VM3 | simple | yes (confirm delete) | — | S | Lists debug log sessions, delete confirmations, launches `RecorderActivity`. |
| dashboard_card_config | app | `DashboardCardConfigRoute` | `DashboardCardConfigFragment` | Fragment | VM3 | reorder | no | — | S | Drag-reorder via ItemTouchHelper. |
| upgrade_foss | app/foss | `UpgradeRoute(forced)` | `UpgradeFragment` (foss) | Fragment | VM3 | none | no | — | S | GitHub sponsors + toast/snackbar events. |
| upgrade_gplay | app/gplay | `UpgradeRoute(forced)` | `UpgradeFragment` (gplay) | Fragment | VM3 | none | yes (restore-failed AlertDialog) | — | M | IAP/Sub offers, billing state, restore flow. |
| data_areas | app | `DataAreasRoute` | `DataAreasFragment` | Fragment | VM3 | simple | yes (info AlertDialog) | — | S | Simple list. |
| log_view | app | `LogViewRoute` | `LogViewFragment` | Fragment | VM3 | simple | no | — | S | Debug log viewer list. |

### Module: `app-common-coil`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| preview | app-common-coil | `PreviewRoute` | `PreviewFragment` | DialogFragment3 | VM3 | pager | no | — | M | Legacy `ViewPager` with `PreviewAdapter` containing `PreviewItemFragment`s. Immersive mode. |
| preview_item | app-common-coil | `PreviewItemRoute` | `PreviewItemFragment` | DialogFragment3 | VM3 | none | no | — | S | Child of `PreviewFragment`'s pager. |

### Module: `app-common-exclusion`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| exclusions_list | app-common-exclusion | `ExclusionsListRoute` | `ExclusionListFragment` | Fragment | VM3 | CAB | yes (import/export/info) | — | M | CAB multi-select. 2 `registerForActivityResult` launchers. MainAction dropdown picks pkg/path/segment route. |
| exclusion_editor_path | app-common-exclusion | `PathExclusionEditorRoute(exclusionId?, initial: PathExclusionEditorOptions?)` | `PathExclusionFragment` | Fragment | VM3 | none | yes (remove/unsaved) | — | M | `setFragmentResultListener(PICKER_REQUEST_KEY)` bridges picker. 6 tag toggles. |
| exclusion_editor_pkg | app-common-exclusion | `PkgExclusionEditorRoute(exclusionId?, initial: PkgExclusionEditorOptions?)` | `PkgExclusionFragment` | Fragment | VM3 | none | yes (remove/unsaved) | — | S | 3 tag toggles, simpler than path editor. |
| exclusion_editor_segment | app-common-exclusion | `SegmentExclusionEditorRoute(exclusionId?, initial: SegmentExclusionEditorOptions?)` | `SegmentExclusionFragment` | Fragment | VM3 | none | yes (remove/unsaved) | — | M | TextWatcher on segments input; allowPartial/ignoreCase toggles; 6 tag toggles. |

### Module: `app-common-picker`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| picker | app-common-picker | `PickerRoute(request: PickerRequest)` | `PickerFragment` | Fragment | VM3 | grid | yes (exit-confirmation) | — | M | Custom `BottomSheetBehavior` for selected-items panel. `setFragmentResult` producer. |

### Module: `app-common-stats`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| stats_reports | app-common-stats | `ReportsRoute` | `ReportsFragment` | Fragment | VM3 | grid | yes (error AlertDialog) | — | S | Uses raw `.asLiveData()` on line 79 (not `asLiveData2()`). |
| stats_space_history | app-common-stats | `SpaceHistoryRoute(storageId?)` | `SpaceHistoryFragment` | Fragment | VM3 | none | yes (delete storage AlertDialog) | `SpaceHistoryChartView` | L | Custom chart view with PopupWindow tooltip. Chip groups. Uses `getColorForAttr(androidx.appcompat.R.attr.colorError/colorPrimary)` — needs Compose theme replacement. |
| stats_affected_paths | app-common-stats | `AffectedFilesRoute(reportId: String)` | `AffectedPathsFragment` | Fragment | VM3 | simple | no | — | S | Header+rows list. |
| stats_affected_pkgs | app-common-stats | `AffectedPkgsRoute(reportId: String)` | `AffectedPkgsFragment` | Fragment | VM3 | simple | no | — | S | Header+rows list. |
| stats_settings | app-common-stats | — (child) | `StatsSettingsFragment` | PreferenceFragment3 | VM3 (StatsSettingsVM) | none (preference) | yes (AgeInputDialog) | — | S | Uses `lifecycleScope.launch` to resolve pro state (anti-pattern). |

### Module: `app-tool-analyzer`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| analyzer_device | app-tool-analyzer | `DeviceStorageRoute` | `DeviceStorageFragment` | Fragment | VM3 | grid | no | — | S | Root of analyzer. |
| analyzer_storage | app-tool-analyzer | `StorageContentRoute(storageId: StorageId)` | `StorageContentFragment` | Fragment | VM3 | grid | no | — | S | Custom back handling via `OnBackPressedCallback` → `vm.onNavigateBack()`. |
| analyzer_apps | app-tool-analyzer | `AppsRoute(storageId: StorageId)` | `AppsFragment` | Fragment | VM3 | grid | no | — | M | SearchView with query listener. |
| analyzer_app_details | app-tool-analyzer | `AppDetailsRoute(storageId, installId)` | `AppDetailsFragment` | Fragment | VM3 | simple | no | — | M | Breakdown rows per data type. |
| analyzer_content | app-tool-analyzer | `ContentRoute(storageId, groupId, installId?)` | `ContentFragment` | Fragment | VM3 | CAB (grid+linear toggle) | yes (delete/no-access AlertDialog) | — | L | CAB multi-select, layout-mode toggle, inaccessible-item visibility rules, `startActivity` for OpenContent. Custom back. |

### Module: `app-tool-appcleaner`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| appcleaner_list | app-tool-appcleaner | `AppCleanerListRoute` | `AppCleanerListFragment` | Fragment | VM3 | CAB | yes (delete/result/exclusions) | — | M | CAB multi-select. |
| appcleaner_details | app-tool-appcleaner | `AppJunkDetailsRoute(identifier: InstallId?)` | `AppJunkDetailsFragment` | Fragment | VM3 | pager | yes (snackbar) | — | M | Legacy ViewPager with `AppJunkDetailsPagerAdapter`/`AppJunkFragment` children. |
| appcleaner_appjunk | app-tool-appcleaner | `AppJunkRoute(identifier: InstallId)` | `AppJunkFragment` | Fragment | VM3 | CAB | yes (delete confirmation) | — | M | Pager child of `AppJunkDetailsFragment`. Shares parent toolbar for CAB. Multi-type list (header/category/file/inaccessible). |
| appcleaner_settings | app-tool-appcleaner | — (child) | `AppCleanerSettingsFragment` | PreferenceFragment2 | VM3 | none (preference) | yes (AgeInputDialog, SizeInputDialog) | — | S | Min size/age input dialogs; badged checkboxes. |

### Module: `app-tool-appcontrol`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| appcontrol_list | app-tool-appcontrol | `AppControlListRoute` | `AppControlListFragment` | Fragment | VM3 | CAB | yes (many AlertDialogs) | — | L | `DrawerLayout` filter pane, custom back handler, fast scroller, sort/filter UI. 1 `registerForActivityResult` (export path). `startActivity(createChooser)` for share. |
| appcontrol_action_dialog | app-tool-appcontrol | `AppActionRoute(installId: InstallId)` | `AppActionDialog` | BottomSheetDialogFragment2 | VM3 | simple | no | `AppInfoTagView` | L | 1 `registerForActivityResult` (export). Action list with 12 VH types. |
| appcontrol_settings | app-tool-appcontrol | — (child) | `AppControlSettingsFragment` | PreferenceFragment2 | VM3 | none (preference) | no | — | S | Badged checkboxes; pro gating. |

### Module: `app-tool-corpsefinder`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| corpsefinder_list | app-tool-corpsefinder | `CorpseFinderListRoute` | `CorpseFinderListFragment` | Fragment | VM3 | CAB | yes (delete/result/exclusions snackbar) | — | M | CAB multi-select. |
| corpsefinder_details | app-tool-corpsefinder | `CorpseDetailsRoute(corpsePathJson: String?)` | `CorpseDetailsFragment` | Fragment | VM3 | pager | yes (snackbar) | — | M | Legacy ViewPager with `CorpseDetailsPagerAdapter`/`CorpseFragment` children. `@Transient corpsePath` derived from JSON arg. |
| corpsefinder_corpse | app-tool-corpsefinder | `CorpseRoute(identifierJson: String)` | `CorpseFragment` | Fragment | VM3 | CAB | yes (delete) | — | M | Pager child. Shares parent toolbar. `@Transient identifier` derived field. |
| corpsefinder_settings | app-tool-corpsefinder | — (child) | `CorpseFinderSettingsFragment` | PreferenceFragment2 | VM3 | none (preference) | no | — | S | Many badged filter checkboxes. Pro-gated watcher. |

### Module: `app-tool-deduplicator`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| dedup_list | app-tool-deduplicator | `DeduplicatorListRoute` | `DeduplicatorListFragment` | Fragment | VM3 | CAB (grid+linear toggle) | yes (`PreviewDeletionDialog`) | — | M | Layout-mode toggle with GridLayoutManager span change. |
| dedup_details | app-tool-deduplicator | `DeduplicatorDetailsRoute(identifier: Duplicate.Cluster.Id?)` | `DeduplicatorDetailsFragment` | Fragment | VM3 | pager | yes (snackbar) | — | M | Legacy ViewPager with `DeduplicatorDetailsPagerAdapter`/`ClusterFragment` children. Directory view toggle. |
| dedup_cluster | app-tool-deduplicator | `ClusterRoute(identifier: Duplicate.Cluster.Id)` | `ClusterFragment` | Fragment | VM3 | CAB | yes (`PreviewDeletionDialog`) | — | L | Custom `SelectionPredicate` limiting max selection. 3 item types with complex delete dispatch. `startActivity` for OpenDuplicate. |
| dedup_arbiter_config | app-tool-deduplicator | `ArbiterConfigRoute` | `ArbiterConfigFragment` | Fragment | VM3 | reorder | yes (mode selection AlertDialog) | — | M | Drag-reorder via ItemTouchHelper. `setFragmentResultListener(PICKER_REQUEST_KEY)`. `vm.pickerEvents` emits `PickerRequest`. |
| dedup_settings | app-tool-deduplicator | — (child) | `DeduplicatorSettingsFragment` | PreferenceFragment2 | VM3 | none (preference) | yes (SizeInputDialog) | — | S | Uses `requireParentFragment().parentFragmentManager.setFragmentResultListener` (special cross-fragment hop). |

### Module: `app-tool-scheduler`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| scheduler_manager | app-tool-scheduler | `SchedulerManagerRoute` | `SchedulerManagerFragment` | Fragment | VM3 | simple | yes (commands-edit AlertDialog with custom view) | — | M | Battery optimization settings intent. Debug menu. |
| scheduler_item_dialog | app-tool-scheduler | `ScheduleItemRoute(scheduleId: String)` | `ScheduleItemDialog` | BottomSheetDialogFragment2 | VM3 | none | yes (`MaterialTimePicker`) | — | M | Time picker, label TextWatcher, days stepper. |
| scheduler_settings | app-tool-scheduler | — (child) | `SchedulerSettingsFragment` | PreferenceFragment2 | — | none (preference) | no | — | S | Minimal. |

### Module: `app-tool-squeezer`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| squeezer_setup | app-tool-squeezer | `SqueezerSetupRoute` | `SqueezerSetupFragment` | Fragment | VM3 | none | yes (`AgeInputDialog`, `SizeInputDialog`, `SqueezerOnboardingDialog`, Toast) | — | L | Quality slider, `setFragmentResultListener(PICKER_REQUEST_KEY)`. |
| squeezer_list | app-tool-squeezer | `SqueezerListRoute` | `SqueezerListFragment` | Fragment | VM3 | CAB (grid+linear toggle) | yes (`PreviewCompressionDialog` helper) | — | M | Layout mode toggle. |
| squeezer_settings | app-tool-squeezer | `SqueezerSettingsRoute` | `SqueezerSettingsFragment` | PreferenceFragment2 | VM3 | none (preference) | yes (clear-history AlertDialog) | — | S | ONLY tool settings with own top-level NavGraph route (not a child of SettingsFragment). |

### Module: `app-tool-swiper`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| swiper_sessions | app-tool-swiper | `SwiperSessionsRoute` | `SwiperSessionsFragment` | Fragment | VM3 | simple (3 view types) | yes (filter dialog view, AlertDialog) | — | L | `setFragmentResultListener(PICKER_REQUEST_KEY)` for picker return. |
| swiper_swipe | app-tool-swiper | `SwiperSwipeRoute(sessionId, startIndex)` | `SwiperSwipeFragment` | Fragment | VM3 | pager (custom) | yes (confirm AlertDialog) | `SwipeCardView` | L | Custom gesture-driven swipe deck via `RecyclerView` + `SwipeCardAdapter` + LinearSmoothScroller. Haptics. 466 LoC. |
| swiper_status | app-tool-swiper | `SwiperStatusRoute(sessionId)` | `SwiperStatusFragment` | Fragment | VM3 | simple (CAB) | yes (delete confirmation) | — | L | Collapsing AppBar with offset listener. 375 LoC. |
| swiper_settings | app-tool-swiper | — (child) | `SwiperSettingsFragment` | PreferenceFragment2 | — | none (preference) | no | — | S | Minimal. |

### Module: `app-tool-systemcleaner`

| id | module | route_class | fragment_class | kind | extends_VM | list_kind | has_dialog | custom_view_deps | state_complexity | notes |
|---|---|---|---|---|---|---|---|---|---|---|
| systemcleaner_list | app-tool-systemcleaner | `SystemCleanerListRoute` | `SystemCleanerListFragment` | Fragment | VM3 | CAB | yes (delete/result AlertDialogs) | — | M | CAB multi-select. |
| systemcleaner_details | app-tool-systemcleaner | `FilterContentDetailsRoute(filterIdentifier?)` | `FilterContentDetailsFragment` | Fragment | VM3 | pager | yes (snackbar) | — | M | Legacy ViewPager with `FilterContentDetailsPagerAdapter`/`FilterContentFragment` children. |
| systemcleaner_filter_content | app-tool-systemcleaner | `FilterContentRoute(identifier: String)` | `FilterContentFragment` | Fragment | VM3 | CAB | yes (delete) | — | M | Pager child. Shares parent toolbar. Multi-type list. |
| systemcleaner_custom_filter_list | app-tool-systemcleaner | `CustomFilterListRoute` | `CustomFilterListFragment` | Fragment | VM3 | CAB | yes (import/export/undo snackbar) | — | M | 2 `registerForActivityResult`. `startActivity` for export view. Pro gating. |
| systemcleaner_custom_filter_editor | app-tool-systemcleaner | `CustomFilterEditorRoute(initial: CustomFilterEditorOptions?, identifier: String?)` | `CustomFilterEditorFragment` | Fragment | VM3 | simple (live search sheet) | yes (many AlertDialogs, `AgeInputDialog`, `SizeInputDialog`) | `TaggedInputView` | L | 304 LoC. Custom `BottomSheetBehavior` for live search pane. |
| systemcleaner_settings | app-tool-systemcleaner | — (child) | `SystemCleanerSettingsFragment` | PreferenceFragment2 | VM3 | none (preference) | yes (`AgeInputDialog`) | — | S | Screenshot filter age input; pro gating. |

---

## 2. Routes

| route | module | target_fragment | args (with types) | nullable_args | uses_serializableNavType | uses_SavedStateHandle.toRoute |
|---|---|---|---|---|---|---|
| `DashboardRoute` | app-common-ui (`CommonRoutes.kt`) | `DashboardFragment` | — | — | no | no |
| `UpgradeRoute` | app-common-ui (`CommonRoutes.kt`) | `UpgradeFragment` (foss/gplay) | `forced: Boolean = false` | no | no | yes |
| `LogViewRoute` | app-common-ui (`CommonRoutes.kt`) | `LogViewFragment` | — | — | no | no |
| `DataAreasRoute` | app-common-ui (`CommonRoutes.kt`) | `DataAreasFragment` | — | — | no | no |
| `AppControlListRoute` | app-common-ui (`CommonRoutes.kt`) | `AppControlListFragment` | — | — | no | no |
| `DeviceStorageRoute` | app-common-ui (`CommonRoutes.kt`) | `DeviceStorageFragment` | — | — | no | no |
| `SwiperSessionsRoute` | app-common-ui (`CommonRoutes.kt`) | `SwiperSessionsFragment` | — | — | no | no |
| `CustomFilterListRoute` | app-common-ui (`CommonRoutes.kt`) | `CustomFilterListFragment` | — | — | no | no |
| `OnboardingWelcomeRoute` | app (`AppRoutes.kt`) | `OnboardingWelcomeFragment` | — | — | no | no |
| `VersusSetupRoute` | app (`AppRoutes.kt`) | `VersusSetupFragment` | — | — | no | no |
| `OnboardingPrivacyRoute` | app (`AppRoutes.kt`) | `OnboardingPrivacyFragment` | — | — | no | no |
| `OnboardingSetupRoute` | app (`AppRoutes.kt`) | `OnboardingSetupFragment` | — | — | no | no |
| `SettingsRoute` | app (`AppRoutes.kt`) | `SettingsFragment` | — | — | no | no |
| `SupportFormRoute` | app (`AppRoutes.kt`) | `SupportContactFormFragment` | — | — | no | no |
| `DebugLogSessionsRoute` | app (`AppRoutes.kt`) | `DebugLogSessionsDialog` | — | — | no | no |
| `DashboardCardConfigRoute` | app (`AppRoutes.kt`) | `DashboardCardConfigFragment` | — | — | no | no |
| `SetupRoute` | app-common-setup (`SetupRoute.kt`) | `SetupFragment` | `options: SetupScreenOptions? = null` | yes (`SetupScreenOptions?`) | yes | yes |
| `PickerRoute` | app-common-data (`PickerRoute.kt`) | `PickerFragment` | `request: PickerRequest` | no | yes | yes |
| `CustomFilterEditorRoute` | app-common-data (`CustomFilterEditorRoute.kt`) | `CustomFilterEditorFragment` | `initial: CustomFilterEditorOptions? = null`, `identifier: String? = null` | yes (`CustomFilterEditorOptions?`) | yes | yes |
| `PreviewRoute` | app-common-coil (`PreviewRoutes.kt`) | `PreviewFragment` | `options: PreviewOptions` | no | yes | yes |
| `PreviewItemRoute` | app-common-coil (`PreviewRoutes.kt`) | `PreviewItemFragment` | `item: PreviewItem` | no | yes | yes |
| `ExclusionsListRoute` | app-common-exclusion (`ExclusionRoutes.kt`) | `ExclusionListFragment` | — | — | no | no |
| `PathExclusionEditorRoute` | app-common-exclusion (`ExclusionRoutes.kt`) | `PathExclusionFragment` | `exclusionId: String? = null`, `initial: PathExclusionEditorOptions? = null` | yes (`PathExclusionEditorOptions?`) | yes | yes |
| `PkgExclusionEditorRoute` | app-common-exclusion (`ExclusionRoutes.kt`) | `PkgExclusionFragment` | `exclusionId: String? = null`, `initial: PkgExclusionEditorOptions? = null` | yes (`PkgExclusionEditorOptions?`) | yes | yes |
| `SegmentExclusionEditorRoute` | app-common-exclusion (`ExclusionRoutes.kt`) | `SegmentExclusionFragment` | `exclusionId: String? = null`, `initial: SegmentExclusionEditorOptions? = null` | yes (`SegmentExclusionEditorOptions?`) | yes | yes |
| `ReportsRoute` | app-common-stats (`StatsRoutes.kt`) | `ReportsFragment` | — | — | no | no |
| `SpaceHistoryRoute` | app-common-stats (`StatsRoutes.kt`) | `SpaceHistoryFragment` | `storageId: String? = null` | yes (primitive) | no | yes |
| `AffectedFilesRoute` | app-common-stats (`StatsRoutes.kt`) | `AffectedPathsFragment` | `reportId: String` | no | no | yes |
| `AffectedPkgsRoute` | app-common-stats (`StatsRoutes.kt`) | `AffectedPkgsFragment` | `reportId: String` | no | no | yes |
| `StorageContentRoute` | app-tool-analyzer (`AnalyzerRoutes.kt`) | `StorageContentFragment` | `storageId: StorageId` | no | yes | yes |
| `AppsRoute` | app-tool-analyzer (`AnalyzerRoutes.kt`) | `AppsFragment` | `storageId: StorageId` | no | yes | yes |
| `AppDetailsRoute` | app-tool-analyzer (`AnalyzerRoutes.kt`) | `AppDetailsFragment` | `storageId: StorageId`, `installId: InstallId` | no | yes | yes |
| `ContentRoute` | app-tool-analyzer (`AnalyzerRoutes.kt`) | `ContentFragment` | `storageId: StorageId`, `groupId: ContentGroup.Id`, `installId: InstallId? = null` | yes (`InstallId?`) | yes | yes |
| `AppCleanerListRoute` | app-tool-appcleaner (`AppCleanerRoutes.kt`) | `AppCleanerListFragment` | — | — | no | no |
| `AppJunkDetailsRoute` | app-tool-appcleaner (`AppCleanerRoutes.kt`) | `AppJunkDetailsFragment` | `identifier: InstallId? = null` | yes (`InstallId?`) | yes | yes |
| `AppJunkRoute` | app-tool-appcleaner (`AppCleanerRoutes.kt`) | `AppJunkFragment` | `identifier: InstallId` | no | yes | yes |
| `AppActionRoute` | app-tool-appcontrol (`AppControlRoutes.kt`) | `AppActionDialog` | `installId: InstallId` | no | yes | yes |
| `CorpseFinderListRoute` | app-tool-corpsefinder (`CorpseFinderRoutes.kt`) | `CorpseFinderListFragment` | — | — | no | no |
| `CorpseDetailsRoute` | app-tool-corpsefinder (`CorpseFinderRoutes.kt`) | `CorpseDetailsFragment` | `corpsePathJson: String? = null` | yes (primitive, nullable) | no (APath → JSON string manually) | yes |
| `CorpseRoute` | app-tool-corpsefinder (`CorpseFinderRoutes.kt`) | `CorpseFragment` | `identifierJson: String` | no | no (APath → JSON string manually) | yes |
| `DeduplicatorListRoute` | app-tool-deduplicator (`DeduplicatorRoutes.kt`) | `DeduplicatorListFragment` | — | — | no | no |
| `DeduplicatorDetailsRoute` | app-tool-deduplicator (`DeduplicatorRoutes.kt`) | `DeduplicatorDetailsFragment` | `identifier: Duplicate.Cluster.Id? = null` | yes (`Duplicate.Cluster.Id?`) | yes | yes |
| `ClusterRoute` | app-tool-deduplicator (`DeduplicatorRoutes.kt`) | `ClusterFragment` | `identifier: Duplicate.Cluster.Id` | no | yes | yes |
| `ArbiterConfigRoute` | app-tool-deduplicator (`DeduplicatorRoutes.kt`) | `ArbiterConfigFragment` | — | — | no | no |
| `SchedulerManagerRoute` | app-tool-scheduler (`SchedulerRoutes.kt`) | `SchedulerManagerFragment` | — | — | no | no |
| `ScheduleItemRoute` | app-tool-scheduler (`SchedulerRoutes.kt`) | `ScheduleItemDialog` | `scheduleId: String` | no | no | yes |
| `SqueezerSetupRoute` | app-tool-squeezer (`SqueezerRoutes.kt`) | `SqueezerSetupFragment` | — | — | no | no |
| `SqueezerListRoute` | app-tool-squeezer (`SqueezerRoutes.kt`) | `SqueezerListFragment` | — | — | no | no |
| `SqueezerSettingsRoute` | app-tool-squeezer (`SqueezerRoutes.kt`) | `SqueezerSettingsFragment` | — | — | no | no |
| `SwiperSwipeRoute` | app-tool-swiper (`SwiperRoutes.kt`) | `SwiperSwipeFragment` | `sessionId: String`, `startIndex: Int = -1` | no | no | yes |
| `SwiperStatusRoute` | app-tool-swiper (`SwiperRoutes.kt`) | `SwiperStatusFragment` | `sessionId: String` | no | no | yes |
| `SystemCleanerListRoute` | app-tool-systemcleaner (`SystemCleanerRoutes.kt`) | `SystemCleanerListFragment` | — | — | no | no |
| `FilterContentDetailsRoute` | app-tool-systemcleaner (`SystemCleanerRoutes.kt`) | `FilterContentDetailsFragment` | `filterIdentifier: String? = null` | yes (primitive) | no | yes |
| `FilterContentRoute` | app-tool-systemcleaner (`SystemCleanerRoutes.kt`) | `FilterContentFragment` | `identifier: String` | no | no | yes |

Notes:
- 52 unique routes. 19 carry custom-typed args; 8 of those are nullable. `CorpseDetailsRoute`/`CorpseRoute` smuggle `APath` through manual JSON string instead of `serializableNavType`.
- All `serializableNavType(...)` wrappers live in `MainNavGraph.kt` and in per-route companion `typeMap` maps. Phase 8 must grep `serializableNavType(` and delete every hit once Navigation3 lands.

---

## 3. setFragmentResult* usage

| producer_file | producer_fragment | request_key | consumer_file | consumer_fragment | planned_result_bus |
|---|---|---|---|---|---|
| `app-common-picker/.../PickerFragment.kt` | `PickerFragment` (via `PickerEvent.Save.requestKey`) | dynamic (caller-supplied) | `app-common-exclusion/.../PathExclusionFragment.kt` | `PathExclusionFragment` (`PathExclusionViewModel.PICKER_REQUEST_KEY = "PathExclusionViewModel.picker"`) | `PickerResultBus` |
| `app-common-picker/.../PickerFragment.kt` | `PickerFragment` | dynamic | `app-tool-swiper/.../SwiperSessionsFragment.kt` | `SwiperSessionsFragment` (`"swiper_sessions_picker"`) | `PickerResultBus` |
| `app-common-picker/.../PickerFragment.kt` | `PickerFragment` | dynamic | `app-tool-deduplicator/.../ArbiterConfigFragment.kt` | `ArbiterConfigFragment` (`"arbiter.keep.prefer.paths"`) | `PickerResultBus` |
| `app-common-picker/.../PickerFragment.kt` | `PickerFragment` | dynamic | `app-tool-deduplicator/.../DeduplicatorSettingsFragment.kt` | `DeduplicatorSettingsFragment` (double-hop via `requireParentFragment().parentFragmentManager`) | `PickerResultBus` |
| `app-common-picker/.../PickerFragment.kt` | `PickerFragment` | dynamic | `app-tool-squeezer/.../SqueezerSetupFragment.kt` | `SqueezerSetupFragment` (`"squeezer.setup.paths"`) | `PickerResultBus` |
| `app-tool-squeezer/.../ComparisonDialog.kt` | `ComparisonDialog` (plain `DialogFragment`) | `"comparison_dismissed"` | `app-tool-squeezer/.../PreviewCompressionDialog.kt` (helper class) | `PreviewCompressionDialog` helper | Inline into Compose dialog state (no bus needed) |

Notes:
- All picker-style flows funnel through `PickerResultBus` (`@Singleton` keyed by request ID string).
- `DeduplicatorSettingsFragment` is the oddball that hops via `requireParentFragment().parentFragmentManager`. Edge-case vanishes once both sides are Compose.
- `ComparisonDialog` uses `setFragmentResult` only to re-trigger `PreviewCompressionDialog.show` — in Compose this becomes a local state flag.

---

## 4. registerForActivityResult usage

| file | fragment | contract_type | purpose | vm_handler_method | planned_launch_request |
|---|---|---|---|---|---|
| `app-common-exclusion/.../ExclusionListFragment.kt` | `ExclusionListFragment` | `StartActivityForResult()` (`importPickerLauncher`) | Import exclusions: open ACTION_OPEN_DOCUMENT JSON chooser | `vm.importExclusions(uriList)` | `ExclusionListViewModel.LaunchEvent.ImportDocuments` |
| `app-common-exclusion/.../ExclusionListFragment.kt` | `ExclusionListFragment` | `StartActivityForResult()` (`exportPickerLauncher`) | Export exclusions: open ACTION_CREATE_DOCUMENT JSON picker | `vm.performExport(uri)` | `ExclusionListViewModel.LaunchEvent.ExportDocument` |
| `app-tool-systemcleaner/.../CustomFilterListFragment.kt` | `CustomFilterListFragment` | `StartActivityForResult()` (`importPickerLauncher`) | Import custom filter JSON(s) | `vm.importFilter(uriList)` | `CustomFilterListViewModel.LaunchEvent.ImportDocuments` |
| `app-tool-systemcleaner/.../CustomFilterListFragment.kt` | `CustomFilterListFragment` | `StartActivityForResult()` (`exportPickerLauncher`) | Export custom filter | `vm.performExport(uri)` | `CustomFilterListViewModel.LaunchEvent.ExportDocument` |
| `app-tool-appcontrol/.../AppControlListFragment.kt` | `AppControlListFragment` | `StartActivityForResult()` (`exportPath`) | Pick export directory for APK export | `vm.export(items, uri)` | `AppControlListViewModel.LaunchEvent.ExportApps` |
| `app-tool-appcontrol/.../AppActionDialog.kt` | `AppActionDialog` | `StartActivityForResult()` (`exportPath`) | Pick export directory for single-app APK export | `vm.exportApp(uri)` | `AppActionViewModel.LaunchEvent.ExportApp` |
| `app/.../setup/SetupFragment.kt` | `SetupFragment` | `SafGrantPrimaryContract()` (custom contract) | Request scoped-storage permission for a given path | `vm.onSafAccessGranted(result)` | `SetupViewModel.LaunchEvent.RequestSafAccess(pathAccess)` |
| `app/.../setup/SetupFragment.kt` | `SetupFragment` | `RequestPermission()` | Request a runtime permission (POST_NOTIFICATIONS, storage, ...) | `vm.onRuntimePermissionsGranted(perm, granted)` | `SetupViewModel.LaunchEvent.RequestRuntimePermission(perm)` |
| `app/.../setup/SetupFragment.kt` | `SetupFragment` | `StartActivityForResult()` (`specialPermissionLauncher`) | Special permission settings screens (MANAGE_EXTERNAL_STORAGE, exact alarm, accessibility, app-info) | `vm.onRuntimePermissionsGranted(...)` + `vm.onAccessibilityReturn()` | `SetupViewModel.LaunchEvent.OpenSpecialPermission(intent)` |

Notes:
- 9 launchers total across 4 fragments + `AppActionDialog`. All migrate to the per-screen `Host` composable via `rememberLauncherForActivityResult` + VM `SingleEventFlow<LaunchRequest>`.
- `SafGrantPrimaryContract` is a custom contract — keep unchanged, re-plumb through the new Host.
- `SetupFragment`'s `specialPermissionLauncher` has fallback intent handling — logic stays in VM, launching moves to Host.

---

## 5. context.startActivity from Fragments (no result)

| file | fragment | intent_source | trigger |
|---|---|---|---|
| `app/.../main/ui/dashboard/DashboardFragment.kt` | `DashboardFragment` | Other (Play Store / settings / email chooser — depends on `event.intent`) | `vm.events` → `DashboardEvents.OpenIntent` |
| `app/.../main/ui/settings/support/contactform/SupportContactFormFragment.kt` | `SupportContactFormFragment` | Email (ACTION_SENDTO mailto:) | `vm.events` → `SupportContactFormEvents.OpenEmail` |
| `app/.../main/ui/settings/support/SupportFragment.kt` | `SupportFragment` | Launches `RecorderActivity` (internal) | `vm.events` → `SupportEvents.LaunchRecorderActivity` |
| `app/.../main/ui/settings/support/sessions/DebugLogSessionsDialog.kt` | `DebugLogSessionsDialog` | Launches `RecorderActivity` (internal) | onClicked row callback in state mapping |
| `app/.../setup/SetupFragment.kt` | `SetupFragment` | App info settings (ACTION_APPLICATION_DETAILS_SETTINGS) | `vm.events` → `SetupEvents.ShowOurDetailsPage` |
| `app-tool-analyzer/.../ContentFragment.kt` | `ContentFragment` | File browsing (ACTION_VIEW for folder/content) | `vm.events` → `ContentItemEvents.OpenContent` |
| `app-tool-appcontrol/.../AppControlListFragment.kt` | `AppControlListFragment` | Share chooser (`Intent.createChooser` for share list text) | `vm.events` → `AppControlListEvents.ShareList` |
| `app-tool-deduplicator/.../ClusterFragment.kt` | `ClusterFragment` | File viewer (ACTION_VIEW for duplicate path) | `vm.events` → `ClusterEvents.OpenDuplicate` |
| `app-tool-scheduler/.../SchedulerManagerFragment.kt` | `SchedulerManagerFragment` | Battery optimization settings | `vm.events` → `SchedulerManagerEvents.ShowBatteryOptimizationSettings` |
| `app-tool-swiper/.../SwiperSwipeFragment.kt` | `SwiperSwipeFragment` | File viewer (ACTION_VIEW for swipe item) | `vm.events` → `SwiperSwipeEvents.OpenItem` |
| `app-tool-systemcleaner/.../CustomFilterListFragment.kt` | `CustomFilterListFragment` | File viewer (ACTION_VIEW for exported filter JSON) | Snackbar action in `vm.events` → `CustomFilterListEvents.ExportFinished` |

Notes:
- All become `vm.launchRequests.emit(LaunchRequest.XXX)` in the VM, collected by Host `LaunchedEffect`.
- VM-side `context.startActivity(intent)` call sites (`AppActionViewModel`, `SetupViewModel`, `AppDetailsViewModel`) are anti-patterns — should also migrate to `LaunchRequest`.
- `DashboardFragment.onResume` has a recovery `popBackStack(DashboardRoute, false)` — this is a fragment-nav workaround and probably drops outright with Navigation3.

---

## 6. SingleLiveEvent usage

Total: **47 property-level declarations across 35 feature VMs**, plus 2 in `ViewModel3` base. Plan's ~54 count matches if you include base-class observation sites. Full classification table stored inline in this file but omitted from render for brevity — see individual VM files for exact event types.

**Classification buckets:**
- Pure nav → migrate to `ViewModel4.navEvents` (new base class).
- Pure error → migrate to `ViewModel3.errorEvents` (new base class).
- Snackbar / Toast → new per-VM `SingleEventFlow<LocalEvent>` consumed by Host `LaunchedEffect` feeding `SnackbarHostState`.
- Dialog trigger → inline Compose `mutableStateOf`, collapse event into local state.
- Intent launch → `SingleEventFlow<LaunchRequest>` consumed by Host launcher.

**VMs needing `ViewModel4` (nav):** DashboardViewModel, AppCleanerListViewModel, AppJunkDetailsViewModel, AppJunkViewModel, CorpseFinderListViewModel, CorpseDetailsViewModel, CorpseViewModel, DeduplicatorListViewModel, DeduplicatorDetailsViewModel, ClusterViewModel, ArbiterConfigViewModel, SystemCleanerListViewModel, CustomFilterListViewModel, CustomFilterEditorViewModel, FilterContentViewModel, FilterContentDetailsViewModel, ExclusionListViewModel, PathExclusionViewModel, PkgExclusionViewModel, SegmentExclusionViewModel, SwiperSessionsViewModel, SwiperSwipeViewModel, SwiperStatusViewModel, SqueezerListViewModel, SqueezerSetupViewModel, SchedulerManagerViewModel, ScheduleItemViewModel, DeviceStorageViewModel, StorageContentViewModel, AppsViewModel, AppDetailsViewModel, ContentViewModel, AppControlListViewModel, AppActionViewModel, MainViewModel, SetupViewModel, SettingsViewModel, SupportViewModel, SupportContactFormViewModel, DebugLogSessionsViewModel, OnboardingWelcomeViewModel, VersusSetupViewModel, OnboardingPrivacyViewModel, OnboardingSetupViewModel, UpgradeViewModel (foss+gplay), DataAreasViewModel, LogViewViewModel, DashboardCardConfigViewModel, PreviewViewModel, PreviewItemViewModel, PickerViewModel, ReportsViewModel, SpaceHistoryViewModel, AffectedPathsViewModel, AffectedPkgsViewModel, RecorderViewModel.

**VMs fine on `ViewModel3` (error-only, no nav):** every per-tool `*SettingsViewModel` that doesn't navigate — basically the leaf settings VMs (`GeneralSettingsViewModel`, `AcknowledgementsViewModel`, `StatsSettingsViewModel`, `AppCleanerSettingsViewModel`, `AppControlSettingsViewModel`, `CorpseFinderSettingsViewModel`, `DeduplicatorSettingsViewModel`, `SchedulerSettingsViewModel`, `SwiperSettingsViewModel`, `SystemCleanerSettingsViewModel`, `SqueezerSettingsViewModel`) — verify per-file during Phase 5.

---

## 7. asLiveData() / observe2() of state streams

All of these currently funnel through the `asLiveData2()` extension in `ViewModel2.kt`. Target: `StateFlow<T>` + `collectAsStateWithLifecycle()`.

**Migration rule:** every `.asLiveData2()` / `.asLiveData()` call site becomes a `.stateIn(vmScope, SharingStarted.WhileSubscribed(5_000), initial)`. The `asLiveData2()` extension gets deleted from `ViewModel2.kt` in Phase 2.

**Special cases to flag:**
- `ReportsViewModel.items` uses raw `asLiveData()` (not `asLiveData2()`) — one-off inconsistency. Line 79.
- `DashboardViewModel.listState` also uses raw `asLiveData()` — line 683.
- `CustomFilterEditorViewModel` has 2 `asLiveData2()` call sites (lines 108 and 293).
- `SystemCleanerSettingsViewModel` exposes `screenshotsAge` separately from `state`.
- `GeneralSettingsViewModel` exposes 3 separate LiveData properties (`isPro`, `isUpdateCheckSupported`, `currentLocales`).
- `MainViewModel` has 3 theme-related `.asLiveData2()` calls — these migrate to `Theming.themeState: StateFlow<ThemeState>` as part of Phase 2 step 6.

Approximate count: 60+ call sites across 55+ ViewModels. Full conversion happens per-module in Phases 4/5/6/7 when the VM is touched.

---

## 8. Custom View subclasses

| file | class_name | module | parent | used_in_layouts | planned_approach |
|---|---|---|---|---|---|
| `app-common-ui/.../common/MascotView.kt` | `MascotView` | `app-common-ui` | `FrameLayout` | dashboard_fragment, onboarding_*, empty-state overlays | compose_rewrite (`Box { Image(...) }` + `rememberInfiniteTransition`) |
| `app-common-ui/.../common/ui/BelowAppBarBehavior.kt` | `BelowAppBarBehavior` | `app-common-ui` | `CoordinatorLayout.Behavior<View>` | `app:layout_behavior` attr | delete (TopAppBarScrollBehavior.exitUntilCollapsed replaces) |
| `app-common-ui/.../common/progress/ProgressOverlayView.kt` | `ProgressOverlayView` | `app-common-ui` | `ConstraintLayout` | every tool's loadingOverlay | compose_rewrite (`ProgressOverlay` composable, Phase 2 step 9) |
| `app/.../common/progress/ProgressBarView.kt` | `ProgressBarView` | `app` | `ConstraintLayout` | Linear progress variants (non-overlay) | compose_rewrite (wrap `LinearProgressIndicator`) |
| `app/.../common/ui/BreadCrumbBar.kt` | `BreadCrumbBar` | `app` | `FrameLayout` | Analyzer nav, custom filter editor, etc. | compose_rewrite (`BreadCrumbRow` = `LazyRow { AssistChip(...) }`, Phase 2 step 9) |
| `app-tool-appcontrol/.../ui/AppInfoTagView.kt` | `AppInfoTagView` | `app-tool-appcontrol` | `ConstraintLayout` | appcontrol_action_dialog, appcontrol_list_row | compose_rewrite (`AppInfoTag` composable, Phase 2 step 9) |
| `app-tool-systemcleaner/.../customfilter/editor/TaggedInputView.kt` | `TaggedInputView` | `app-tool-systemcleaner` | `ConstraintLayout` | systemcleaner_customfilter_editor_fragment | compose_rewrite (chip row + `OutlinedTextField`) |
| `app-common-automation/.../ui/AutomationControlView.kt` | `AutomationControlView` | `app-common-automation` | `ConstraintLayout` | Accessibility service overlay window | keep (android_view) — not part of Activity composition |
| `app-tool-swiper/.../swipe/SwipeCardView.kt` | `SwipeCardView` | `app-tool-swiper` | `FrameLayout` | swiper_swipe_card_layout | compose_rewrite — hardest Compose port (`Modifier.pointerInput` + animation state) |
| `app-common-stats/.../spacehistory/SpaceHistoryChartView.kt` | `SpaceHistoryChartView` | `app-common-stats` | `View` (custom-drawn) | stats_space_history_fragment | android_view (`AndroidView { ... }`). Custom-drawn charts too painful; deferred |

**Total: 10 custom View subclasses.** 7 rewrite in Compose, 1 delete, 1 wrap in AndroidView, 1 keep (automation overlay outside Activity).

---

## 9. Adapters and ViewHolders

**Total: 46 adapters.** 6 pager (replace with HorizontalPager), 15 multi-type, 15 with selection state. **93 `*VH.kt` files** across modules.

Full adapter table stored inline (46 rows). Per-module VH count:
- `app`: 19 (dashboard 13, setup 8, other misc)
- `app-tool-analyzer`: 12
- `app-tool-appcleaner`: 5
- `app-tool-appcontrol`: 13 (12 action VH + 1 list row)
- `app-tool-corpsefinder`: 3
- `app-tool-deduplicator`: 11 (cluster 8, list 2, arbiter 2)
- `app-tool-scheduler`: 3
- `app-tool-squeezer`: 2
- `app-tool-swiper`: 3
- `app-tool-systemcleaner`: 4
- `app-common-exclusion`: 3
- `app-common-picker`: 2
- `app-common-stats`: 5

Heaviest adapters (design individually):
- `DashboardAdapter` — 17 view types
- `AppActionAdapter` — 12 view types
- `SetupAdapter` — 9 view types
- `ClusterAdapter` — 8 view types with sealed `Item`
- `AppDetailsAdapter` — 5 view types
- `ExclusionListAdapter` — 3 VH types + selection state
- `ContentAdapter` — 4 VH types + selection + grid/linear toggle
- `StorageContentAdapter` — 3 VH types

Pager adapters (all replaced with `HorizontalPager`):
- `PreviewAdapter`, `AppJunkDetailsPagerAdapter`, `CorpseDetailsPagerAdapter`, `DeduplicatorDetailsPagerAdapter`, `FilterContentDetailsPagerAdapter`, `ProgressPagerAdapter`

---

## 10. Manifest activities

### `app/src/main/AndroidManifest.xml`

| name | flavor | theme | exported | intent_filters | rewrite_status |
|---|---|---|---|---|---|
| `eu.darken.sdmse.main.ui.MainActivity` | main | `@style/AppThemeSplash` | true | `android.intent.action.MAIN` + `LAUNCHER` | compose |
| `eu.darken.sdmse.main.ui.shortcuts.ShortcutActivity` | main | `@android:style/Theme.NoDisplay` | true | `eu.darken.sdmse.ACTION_OPEN_APPCONTROL`, `eu.darken.sdmse.ACTION_SCAN_DELETE` | keep |
| `eu.darken.sdmse.common.debug.recorder.ui.RecorderActivity` | main | `@style/AppTheme` | false (default) | — | keep |

### `app/src/debug/AndroidManifest.xml`

| name | flavor | theme | exported | intent_filters | rewrite_status |
|---|---|---|---|---|---|
| `eu.darken.sdmse.HiltTestActivity` | debug | — | false | — | keep (test-only) |

### `app/src/foss/AndroidManifest.xml` / `app/src/gplay/AndroidManifest.xml`

Do not exist. No flavor-specific manifest overrides.

### Module-level manifests

None of `app-common-*` or `app-tool-*` manifests declare activities.

**Total: 3 main + 1 debug test activity.** Only MainActivity gets a Compose rewrite.

---

## 11. Fragment lifecycleScope usage

| file | fragment | use_case | planned_location |
|---|---|---|---|
| `app-common-stats/.../stats/ui/settings/settings/StatsSettingsFragment.kt` | `StatsSettingsFragment` | `lifecycleScope.launch { isPro = upgradeRepo.isPro(); if (pro) navReports else navUpgrade }` | VM — `onStatsViewClick()` on `StatsSettingsViewModel` that knows `isPro` synchronously and calls `navTo(...)` |
| `app-tool-squeezer/.../onboarding/ComparisonDialog.kt` | `ComparisonDialog` (plain `DialogFragment`) | `viewLifecycleOwner.lifecycleScope.launch(IO) { decode + compress + write tempfile + reload }` | VM — move to `ComparisonDialogViewModel`, trigger via `LaunchedEffect` |
| `app-tool-squeezer/.../onboarding/ComparisonDialog.kt` | `ComparisonDialog` | `lifecycleScope.launch(IO) { cleanup tempdir }` in `onDestroyView` | VM `onCleared()` or Host `DisposableEffect` |

**Only 3 real Fragment sites.** The codebase already keeps most background work in VM scope.

---

## 12. Summary statistics

- **Fragment-derived classes total: 62.** Breakdown in Section 1.
- **DialogFragment3: 2. BottomSheetDialogFragment2: 3.** Combined dialog-style fragments: **5**.
- **PreferenceFragment*: 12.**
- **Routes: 52**, with **19** custom-typed-arg routes (8 nullable). 2 routes (`CorpseDetailsRoute`, `CorpseRoute`) smuggle `APath` through manual JSON string.
- **SingleLiveEvent declarations: 47** in feature VMs + 2 in base class.
- **Adapters: 46** (6 pager adapters, 15 multi-type, 15 with selection state).
- **ViewHolder files: 93.**
- **Custom View subclasses: 10** (7 compose rewrite, 1 AndroidView wrap, 1 keep, 1 delete).
- **Manifest activities: 3 main + 1 debug.**
- **`setFragmentResult` pairs: 6** (5 picker consumers + 1 squeezer comparison).
- **`registerForActivityResult` sites: 9** (across 5 fragments + AppActionDialog).
- **Fragment `startActivity` sites: 11.**
- **Fragment `lifecycleScope.launch` sites: 3.**

---

## 13. Device state prelude (Phase 0b / Phase 10)

```
device: emulator-5558
theme_mode: LIGHT
theme_style: DEFAULT
locale: en-US (locale=en-US, ro.product.locale=en-US)
orientation: portrait (user_rotation=0)
font_scale: 1.0
captured_at: 2026-04-11T19:00
apk_build: FOSS debug, 1.7.0-rc0 (versionCode 10700000), built 2026-04-11 15:00 from `a403423c2` (docs-only delta vs `main`)
notes:
  - Night mode off (`cmd uimode night` → no)
  - App data cleared before capture to get clean onboarding
  - Capture method: `adb -s emulator-5558 exec-out screencap -p > <path>` (clean PNGs, no debugbadger overlay markers)
  - Tap targets located via debugbadger MCP `find_element` + `tap`, with `allowEdgeTap=true` for the bottom system gesture zone
```

---

## 14. Phase 0b screen table

### Phase 0b status (partial baseline, captured 2026-04-11)

**Captured: 29 screens** covering onboarding, setup, dashboard variants, all settings surfaces reachable from a clean install, the one tool list screen that's reachable without seeded data (SystemCleaner — empty-folders/temp-files filters match on any device), and the Analyzer tree. Additional screens that did not require test data but were missed in this pass can be re-captured from the same device state ad-hoc.

**Reason Phase 0b is partial**: the majority of tool list screens (CorpseFinder, AppCleaner, Deduplicator, Squeezer, Swiper, Scheduler, Analyzer Apps/Content, and every `*Details` / `*Cluster` / `*Junk` pager child) are only reachable after the corresponding tool has real data to show. A scan on an empty emulator returns "0 results" and keeps the user on the dashboard rather than navigating to the list. `tooling/testdata-generator/` was not run during this capture pass — seeding representative data for every tool is a prerequisite for the remaining captures and is deferred to the next Phase 0b extension session (or absorbed into Phase 10's re-capture pass).

**Screens deferred pending test data** (see per-tool notes in Section 1):
- `corpsefinder_list_*`, `corpsefinder_details`, `corpsefinder_corpse`
- `appcleaner_list_*`, `appcleaner_details`, `appcleaner_appjunk`
- `deduplicator_list_linear`, `deduplicator_list_grid`, `dedup_details`, `dedup_cluster`, `dedup_arbiter_config`
- `squeezer_list_*`, `squeezer_setup` (needs pre-seeded source media)
- `swiper_sessions_populated`, `swiper_swipe`, `swiper_status`
- `scheduler_manager_populated`, `scheduler_item_dialog`
- `systemcleaner_custom_filter_list`, `systemcleaner_custom_filter_editor_*` (custom filter work needs a seeded filter definition)
- `analyzer_apps`, `analyzer_app_details`, `analyzer_content` (need populated storage with recognised packages)
- `stats_reports`, `stats_affected_paths`, `stats_affected_pkgs` (need scan history)
- `preview`, `preview_item` (reached from deduplicator/swiper after selecting an item)
- `picker` (reached from swiper or exclusion editor)
- `exclusion_editor_path`, `exclusion_editor_pkg`, `exclusion_editor_segment` (need to add/edit an exclusion)
- `appcontrol_action_dialog` (long-press a row on populated AppControl list)
- `data_areas`, `log_view`, `dashboard_card_config` (reached from less obvious entry points — confirm nav paths before re-capturing)
- `upgrade_gplay` (separate gplay APK build)
- `overlay_*` (error/progress/delete-confirm/size-age-quality input dialogs — trigger on demand)
- `framework_mainactivity_cold`, `framework_recorder` (cold start / debug recorder)

**Not captured on this device (conditional)**:
- `onboarding_versus` — only reached if legacy SD Maid pkg is installed. Fresh install on `emulator-5558` goes welcome → privacy → setup directly, skipping versus.

**Captured screens** (before/*.png):

| id | file | state |
|---|---|---|
| onboarding_welcome | `before/onboarding/welcome.png` | fresh install |
| onboarding_privacy | `before/onboarding/privacy.png` | fresh install, update+MOTD toggles default-on |
| onboarding_setup | `before/onboarding/setup.png` | final onboarding card |
| setup_onboarding | `before/setup/main.png` | first-run Setup screen with all permission cards |
| dashboard_empty | `before/dashboard/dashboard_empty.png` | post-onboarding, Setup-incomplete banner visible, scans not yet run |
| dashboard_with_results | `before/dashboard/dashboard_with_results.png` | post-scan, SystemCleaner shows 13 filter matches / 27 kB, other tools 0 results, main delete FAB visible |
| settings_index | `before/settings/index.png` | entry PreferenceFragment list |
| settings_general | `before/settings/general.png` | General tweaks (theme/style/language/ROM type/update check) |
| settings_support | `before/settings/support.png` | Support index (contact developer, debug log folder, etc.) |
| settings_acknowledgements | `before/settings/acknowledgements.png` | static contributors/licenses list |
| support_contact_form | `before/settings/support_contact_form.png` | Contact developer form, empty fields |
| debug_log_sessions | `before/settings/debug_log_sessions.png` | Debug log sessions dialog / empty state (no recorded logs) |
| exclusions_list_empty | `before/exclusions/list_empty.png` | Exclusion manager with no user exclusions |
| corpsefinder_settings | `before/corpsefinder/settings.png` | filter checkboxes |
| systemcleaner_settings | `before/systemcleaner/settings.png` | screenshot age/pro-gated toggles |
| systemcleaner_list_populated | `before/systemcleaner/list_populated.png` | 2 filter matches (Empty folders + Temporary system files) |
| systemcleaner_delete_confirm | `before/systemcleaner/delete_confirm_dialog.png` | Confirm deletion dialog (delete "Empty folders"?) |
| systemcleaner_filter_content | `before/systemcleaner/filter_content.png` | inside a filter showing its matched items |
| appcleaner_settings | `before/appcleaner/settings.png` | size/age input dialog triggers + badged checkboxes |
| appcontrol_list | `before/appcontrol/list.png` | 1 item (SD Maid itself), debug/APK badges visible |
| appcontrol_settings | `before/appcontrol/settings.png` | badged checkboxes, pro gating |
| deduplicator_settings | `before/deduplicator/settings.png` | size input dialog trigger |
| squeezer_settings | `before/squeezer/settings.png` | Media Squeeze settings screen |
| swiper_settings | `before/swiper/settings.png` | minimal settings |
| scheduler_settings | `before/scheduler/settings.png` | minimal settings |
| stats_settings | `before/stats/settings.png` | History (stats) settings with age input |
| analyzer_device_storage | `before/analyzer/device_storage.png` | device storage overview with primary + secondary storage cards and inline chart |
| analyzer_storage_content | `before/analyzer/storage_content.png` | content overview (primary storage, empty populated apps list, pre-rescan) |
| upgrade_foss | `before/upgrade/main.png` | FOSS upgrade screen (GitHub sponsors CTA) |

**Phase 10 recapture guidance**: matches against `after/` must re-populate test data the same way (ideally via `tooling/testdata-generator/`) so the populated SystemCleaner list has the same 2 filters match. For screens that were deferred here, Phase 10 needs to run the full seed-and-capture pass in addition to the re-capture — those rows will compare their Phase 10 `after/` image against nothing on the `before/` side and should instead be manually inspected against the post-Compose build's behavior.

### Full screen list (pre-populated from Section 1 — populate the blank cells during Phase 0b extension / Phase 10)

Populated in Phase 0b with navigation paths, screenshots, and diff status. Pre-populated with every destination from Section 1.

| id | tool | kind | navigation_path | population_notes | before_screenshot | after_screenshot | diff_status |
|---|---|---|---|---|---|---|---|
| onboarding_welcome | main | Fragment | | fresh install | | | |
| onboarding_versus | main | Fragment | | fresh install | | | |
| onboarding_privacy | main | Fragment | | fresh install | | | |
| onboarding_setup | main | Fragment | | fresh install | | | |
| dashboard_empty | main | Fragment | | fresh app, no data | | | |
| dashboard_populated | main | Fragment | | post-scan state | | | |
| setup_onboarding | main | Fragment | | onboarding flow entry | | | |
| setup_settings | main | Fragment | | from settings entry | | | |
| settings_index | main | PreferenceFragment | | | | | |
| settings_general | main | PreferenceFragment | | | | | |
| settings_acknowledgements | main | PreferenceFragment | | | | | |
| settings_support | main | PreferenceFragment | | | | | |
| support_contact_form | main | Fragment | | | | | |
| debug_log_sessions | main | BottomSheet | | | | | |
| dashboard_card_config | main | Fragment | | | | | |
| upgrade_foss | main | Fragment | | FOSS flavor only | | | |
| upgrade_gplay | main | Fragment | | GPlay flavor, separate capture run | | | |
| data_areas | main | Fragment | | | | | |
| log_view | main | Fragment | | | | | |
| preview | shared | Dialog | | triggered from deduplicator/swiper | | | |
| preview_item | shared | Dialog | | child of preview pager | | | |
| exclusions_list_empty | exclusions | Fragment | | no custom exclusions | | | |
| exclusions_list_populated | exclusions | Fragment | | after seeding defaults | | | |
| exclusion_editor_path | exclusions | Fragment | | | | | |
| exclusion_editor_pkg | exclusions | Fragment | | | | | |
| exclusion_editor_segment | exclusions | Fragment | | | | | |
| picker | shared | Fragment | | reached from swiper or exclusions | | | |
| stats_reports | stats | Fragment | | | | | |
| stats_space_history | stats | Fragment | | custom chart view | | | |
| stats_affected_paths | stats | Fragment | | | | | |
| stats_affected_pkgs | stats | Fragment | | | | | |
| stats_settings | stats | PreferenceFragment | | | | | |
| analyzer_device | analyzer | Fragment | | pre-scan | | | |
| analyzer_storage | analyzer | Fragment | | post-scan | | | |
| analyzer_apps | analyzer | Fragment | | | | | |
| analyzer_app_details | analyzer | Fragment | | | | | |
| analyzer_content | analyzer | Fragment | | grid and linear variants | | | |
| appcleaner_list_empty | appcleaner | Fragment | | pre-scan | | | |
| appcleaner_list_populated | appcleaner | Fragment | | post-scan | | | |
| appcleaner_details | appcleaner | Fragment | | pager parent | | | |
| appcleaner_appjunk | appcleaner | Fragment | | pager child | | | |
| appcleaner_settings | appcleaner | PreferenceFragment | | | | | |
| appcontrol_list | appcontrol | Fragment | | | | | |
| appcontrol_action_dialog | appcontrol | BottomSheet | | | | | |
| appcontrol_settings | appcontrol | PreferenceFragment | | | | | |
| corpsefinder_list_empty | corpsefinder | Fragment | | pre-scan | | | |
| corpsefinder_list_populated | corpsefinder | Fragment | | post-scan | | | |
| corpsefinder_details | corpsefinder | Fragment | | pager parent | | | |
| corpsefinder_corpse | corpsefinder | Fragment | | pager child | | | |
| corpsefinder_settings | corpsefinder | PreferenceFragment | | | | | |
| dedup_list_linear | deduplicator | Fragment | | linear layout mode | | | |
| dedup_list_grid | deduplicator | Fragment | | grid layout mode | | | |
| dedup_details | deduplicator | Fragment | | pager parent | | | |
| dedup_cluster | deduplicator | Fragment | | pager child | | | |
| dedup_arbiter_config | deduplicator | Fragment | | drag-reorder | | | |
| dedup_settings | deduplicator | PreferenceFragment | | | | | |
| scheduler_manager_empty | scheduler | Fragment | | | | | |
| scheduler_manager_populated | scheduler | Fragment | | | | | |
| scheduler_item_dialog | scheduler | BottomSheet | | | | | |
| scheduler_settings | scheduler | PreferenceFragment | | | | | |
| squeezer_setup | squeezer | Fragment | | | | | |
| squeezer_list_linear | squeezer | Fragment | | linear layout mode | | | |
| squeezer_list_grid | squeezer | Fragment | | grid layout mode | | | |
| squeezer_settings | squeezer | PreferenceFragment | | | | | |
| swiper_sessions_empty | swiper | Fragment | | | | | |
| swiper_sessions_populated | swiper | Fragment | | | | | |
| swiper_swipe | swiper | Fragment | | active session | | | |
| swiper_status | swiper | Fragment | | | | | |
| swiper_settings | swiper | PreferenceFragment | | | | | |
| systemcleaner_list_empty | systemcleaner | Fragment | | pre-scan | | | |
| systemcleaner_list_populated | systemcleaner | Fragment | | post-scan | | | |
| systemcleaner_details | systemcleaner | Fragment | | pager parent | | | |
| systemcleaner_filter_content | systemcleaner | Fragment | | pager child | | | |
| systemcleaner_custom_filter_list | systemcleaner | Fragment | | | | | |
| systemcleaner_custom_filter_editor_new | systemcleaner | Fragment | | empty initial | | | |
| systemcleaner_custom_filter_editor_existing | systemcleaner | Fragment | | existing filter | | | |
| systemcleaner_settings | systemcleaner | PreferenceFragment | | | | | |
| framework_mainactivity_cold | framework | Activity | | splash screen | | | |
| framework_recorder | framework | Activity | | debug-only | | | |
| overlay_error_dialog | framework | Overlay | | trigger by simulated failure | | | |
| overlay_progress | framework | Overlay | | scan in progress on any tool | | | |
| overlay_delete_confirmation | framework | Overlay | | from any tool list | | | |
| overlay_size_input | framework | Overlay | | from settings | | | |
| overlay_age_input | framework | Overlay | | from settings | | | |
| overlay_quality_input | framework | Overlay | | from squeezer setup | | | |

---

## Key design findings (surfaced by Phase 0 investigation)

These override or refine earlier plan assumptions and must be acted on during later phases:

### F1. PreferenceFragments are NOT dead code and NOT in the NavGraph

The 12 `PreferenceFragment2`/`PreferenceFragment3` classes are child fragments of `SettingsFragment.kt` hosted via `PreferenceFragmentCompat.onPreferenceStartFragment` + `childFragmentManager`. `SettingsFragment` itself is a `Fragment2` (no VM3 contract) that exists only as a container.

**Action for Phase 4/5:** create per-settings Navigation3 routes for each preference screen (`SettingsIndexRoute`, `GeneralSettingsRoute`, `SupportRoute`, `AcknowledgementsRoute`, plus one per tool). Drop the container-fragment pattern entirely — Navigation3 handles the back-stack natively.

### F2. 4 route classes have destinations registered in MainNavGraph but are NEVER navigated to

`AppJunkRoute`, `CorpseRoute`, `ClusterRoute`, `FilterContentRoute` — each points at a Fragment that is only ever instantiated by its parent's `FragmentStatePagerAdapter`, not via `findNavController().navigate(...)`.

**Action for Phase 5:** These become `HorizontalPager { index -> ChildScreen(routeForIndex) }` inside the parent `*DetailsScreen`. Delete the standalone routes at Phase 8 (no deep-link exists). Verify: `grep -rn "navigate\(AppJunkRoute\|navigate\(CorpseRoute\|navigate\(ClusterRoute\|navigate\(FilterContentRoute\)"` — should have zero hits in the current codebase.

### F3. CorpseDetailsRoute / CorpseRoute smuggle APath via JSON string

`CorpseDetailsRoute(corpsePathJson: String?)` and `CorpseRoute(identifierJson: String)` both use `Json.encodeToString(apath)` + `@Transient` getters to reconstruct the typed field. This is a workaround for Navigation Compose not supporting `APath` as a typed arg. Navigation3 passes the destination object directly into the `entry<Route> { destination -> ... }` lambda, so `APath` can be a real typed argument.

**Action for Phase 5:** When CorpseFinder is migrated, change these routes to carry `APath` (or the appropriate sub-type) directly with `serializableNavType` becoming unnecessary. Drop the `@Transient` + JSON indirection. Update `CorpseDetailsViewModel`/`CorpseViewModel` to read the typed route directly.

### F4. SqueezerSettingsRoute is the only top-level settings route

All other tool settings are children of `SettingsFragment`. Squeezer's is in the MainNavGraph directly.

**Action for Phase 5:** When settings become pure Compose routes (F1), align `SqueezerSettingsRoute` with the rest — make every tool settings screen a top-level route under `SettingsRoute`, not a mix.

### F5. Raw `asLiveData()` (not `asLiveData2()`) in two ViewModels

`ReportsViewModel.items` (line 79) and `DashboardViewModel.listState` (line 683) bypass the `asLiveData2()` wrapper that funnels flows through `vmScope`. Minor inconsistency.

**Action for Phase 4/Phase 7:** When touching those VMs, migrate to `stateIn(vmScope, ...)` like the rest.

### F6. SpaceHistoryFragment uses `getColorForAttr(androidx.appcompat.R.attr.colorError/colorPrimary)` inline

The chart view is already slated for `AndroidView { ... }` wrapping, but the color resolution happens at the call site.

**Action for Phase 7:** Read the current theme's `MaterialTheme.colorScheme.error`/`primary` in the Host composable, pass them as `Int` (toArgb) down to the chart, thread them into `SpaceHistoryChartView.setColors()`. Don't try to do theme lookup inside the View.

### F7. Only 10 real custom Views exist

The `class X : View/Layout` grep hits are dominated by ViewHolders using a `ViewGroup`-receiving constructor. 7 get Compose rewrites, 1 wraps in `AndroidView` (`SpaceHistoryChartView`), 1 stays as-is (`AutomationControlView` in accessibility overlay window — outside Activity composition), 1 deletes (`BelowAppBarBehavior`).

### F8. Only 3 real Fragment-side `lifecycleScope.launch` sites

One in `StatsSettingsFragment` (pro gating), two in `ComparisonDialog` (plain `DialogFragment`, squeezer onboarding helper). All trivially migrate to VM or Host `LaunchedEffect`.

---

## Inventory meta

- **Time spent**: ~80 minutes of exploration.
- **Files not classifiable with full confidence** (review before Phase 2):
  - `app/.../common/progress/ProgressBarView.kt` — assumed linear-bar variant paired with `ProgressOverlayView`, not read in full.
  - `app-common-automation/.../AutomationControlView.kt` — classified as "keep" (rendered in accessibility overlay window). Verify no Compose layout wants to `<include>` it.
- **Fragments without a matching route in `MainNavGraph.kt`**: see F1 (all 12 PreferenceFragments — hosted via `childFragmentManager`) and F2 (4 pager-child routes registered but never navigated).
- **Base class state**: the current `ViewModel3` holds BOTH `navEvents` and `errorEvents` (via `SingleLiveEvent<NavCommand?>`). Phase 2 splits them: new `ViewModel3` keeps only `errorEvents: SingleEventFlow<Throwable>`, new `ViewModel4` adds `navEvents: SingleEventFlow<NavEvent>`. Every existing `ViewModel3` subclass that calls `navigateTo(...)` must migrate to `ViewModel4 + navTo(...)`. Subclasses that only emit errors stay on `ViewModel3`.
