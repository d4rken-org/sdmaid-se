package eu.darken.sdmse.appcontrol.ui.list.actions.items

import eu.darken.sdmse.appcontrol.core.AppInfo
import eu.darken.sdmse.common.pkgs.isEnabled
import eu.darken.sdmse.exclusion.core.types.ExclusionId

/**
 * Pure inputs needed to materialise [AppActionItem] for a single [AppInfo].
 *
 * Pulled out of [AppInfo] / [AppControl.State] / Hilt deps so [buildAppActionItems] stays a
 * pure function and can be unit-tested without Robolectric.
 *
 * - [isCurrentUser] is `true` when [AppInfo.installId.userHandle] matches the device's primary user.
 * - [launchAvailable] reflects `pkg.id.getLaunchIntent(context) != null` — i.e. the OS exposes a
 *   launchable activity. Hidden in cross-user contexts because the intent would target the wrong
 *   profile anyway.
 * - [appStoreAvailable] reflects `(pkg as? InstallDetails)?.installerInfo?.installer != null` —
 *   the installer is known and the AppStore intent is constructible.
 * - [canForceStop] / [canArchive] / [canRestore] / [canToggle] mirror the corresponding flags on
 *   `AppControl.State`. Combined with `AppInfo.canBeStopped` / `canBeArchived` / `canBeRestored` /
 *   `canBeToggled` they decide if the row is visible.
 * - [existingExclusionId] is the id of the existing `Exclusion.Pkg` that already matches this app,
 *   or null. Drives the "Add" vs "Edit" rendering in [AppActionItem.Action.Exclude].
 */
data class AppActionItemContext(
    val isCurrentUser: Boolean,
    val launchAvailable: Boolean,
    val appStoreAvailable: Boolean,
    val canForceStop: Boolean,
    val canArchive: Boolean,
    val canRestore: Boolean,
    val canToggle: Boolean,
    val existingExclusionId: ExclusionId?,
)

/**
 * Build the per-app action sheet contents.
 *
 * Order mirrors the legacy `AppActionViewModel` (lines 109–327): info rows first (Size, Usage),
 * then actions in fixed order (Launch, ForceStop, SystemSettings, AppStore, Exclude, Toggle,
 * Uninstall, Archive, Restore, Export). Inclusion rules mirror the legacy conditional bindings
 * exactly.
 *
 * The [AppActionItem.Action.Toggle] item carries `isEnabled` so the screen can pick the right
 * label/icon without re-reading [AppInfo]. Same idea for [AppActionItem.Action.Exclude] which
 * carries the existing exclusion id (when set), so `vm.onActionTapped` can route to the editor
 * directly.
 */
fun buildAppActionItems(
    appInfo: AppInfo,
    ctx: AppActionItemContext,
): List<AppActionItem> = buildList {
    appInfo.sizes?.let { add(AppActionItem.Info.Size(it)) }
    add(
        AppActionItem.Info.Usage(
            installedAt = appInfo.installedAt,
            updatedAt = appInfo.updatedAt,
            usage = appInfo.usage,
        ),
    )

    if (ctx.isCurrentUser && ctx.launchAvailable) {
        add(AppActionItem.Action.Launch(appInfo.installId))
    }
    if (ctx.canForceStop && appInfo.canBeStopped) {
        add(AppActionItem.Action.ForceStop(appInfo.installId))
    }
    if (ctx.isCurrentUser) {
        add(AppActionItem.Action.SystemSettings(appInfo.installId))
    }
    if (ctx.isCurrentUser && ctx.appStoreAvailable) {
        add(AppActionItem.Action.AppStore(appInfo.installId))
    }
    if (ctx.isCurrentUser) {
        add(
            AppActionItem.Action.Exclude(
                installId = appInfo.installId,
                existingExclusionId = ctx.existingExclusionId,
            ),
        )
    }
    if (ctx.canToggle && appInfo.canBeToggled) {
        add(
            AppActionItem.Action.Toggle(
                installId = appInfo.installId,
                isEnabled = appInfo.pkg.isEnabled,
            ),
        )
    }
    if (appInfo.canBeDeleted) {
        add(AppActionItem.Action.Uninstall(appInfo.installId))
    }
    if (ctx.canArchive && appInfo.canBeArchived) {
        add(AppActionItem.Action.Archive(appInfo.installId))
    }
    if (ctx.canRestore && appInfo.canBeRestored) {
        add(AppActionItem.Action.Restore(appInfo.installId))
    }
    if (appInfo.canBeExported && ctx.isCurrentUser) {
        add(AppActionItem.Action.Export(appInfo.installId))
    }
}
