package eu.darken.sdmse.appcleaner.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Chat
import androidx.compose.material.icons.automirrored.twotone.FormatListBulleted
import androidx.compose.material.icons.automirrored.twotone.HelpOutline
import androidx.compose.material.icons.twotone.AdsClick
import androidx.compose.material.icons.twotone.Analytics
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.PermMedia
import androidx.compose.material.icons.twotone.PhotoLibrary
import androidx.compose.material.icons.twotone.SignalCellularOff
import androidx.compose.material.icons.twotone.SportsEsports
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.AdvertisementFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.AnalyticsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.BugReportingFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.CodeCacheFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPrivateFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.GameFilesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.HiddenFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.MobileQQFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.OfflineCacheFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.RecycleBinsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ShortcutServiceFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.TelegramFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThreemaFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThumbnailsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ViberFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WeChatFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WebViewCacheFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WhatsAppBackupsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WhatsAppReceivedFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WhatsAppSentFilter
import eu.darken.sdmse.common.compose.icons.Bug
import eu.darken.sdmse.common.compose.icons.Chrome
import eu.darken.sdmse.common.compose.icons.Qq
import eu.darken.sdmse.common.compose.icons.SdmIcons
import eu.darken.sdmse.common.compose.icons.WeChat
import eu.darken.sdmse.common.compose.icons.WhatsApp
import kotlin.reflect.KClass

@get:StringRes
val <T : ExpendablesFilter> KClass<T>.labelRes: Int
    get() = when (this) {
        DefaultCachesPublicFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_defaultcachespublic_label
        DefaultCachesPrivateFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_defaultcachesprivate_label
        HiddenFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_hiddencaches_label
        ThumbnailsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_thumbnails_label
        CodeCacheFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_codecache_label
        AdvertisementFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_advertisement_label
        BugReportingFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_bugreporting_label
        AnalyticsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_analytics_label
        GameFilesFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_gamefiles_label
        OfflineCacheFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_offlinecache_label
        RecycleBinsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_recyclebins_label
        WebViewCacheFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_webview_label
        ShortcutServiceFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_shortcutservice_label
        WhatsAppBackupsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_whatsapp_backups_label
        WhatsAppReceivedFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_whatsapp_received_label
        WhatsAppSentFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_whatsapp_sent_label
        TelegramFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_telegram_label
        ThreemaFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_threema_label
        WeChatFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_wechat_label
        ViberFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_viber_label
        MobileQQFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_qqchat_label
        else -> eu.darken.sdmse.common.R.string.general_todo_msg
    }

@get:StringRes
val <T : ExpendablesFilter> KClass<T>.descriptionRes: Int
    get() = when (this) {
        DefaultCachesPublicFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_defaultcachespublic_summary
        DefaultCachesPrivateFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_defaultcachesprivate_summary
        HiddenFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_hiddencaches_summary
        ThumbnailsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_thumbnails_summary
        CodeCacheFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_codecache_summary
        AdvertisementFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_advertisement_summary
        BugReportingFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_bugreporting_summary
        AnalyticsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_analytics_summary
        GameFilesFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_gamefiles_summary
        OfflineCacheFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_offlinecache_summary
        RecycleBinsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_recyclebins_summary
        WebViewCacheFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_webview_summary
        ShortcutServiceFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_shortcutservice_summary
        WhatsAppBackupsFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_whatsapp_backups_summary
        WhatsAppReceivedFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_whatsapp_received_summary
        WhatsAppSentFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_whatsapp_sent_summary
        TelegramFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_telegram_summary
        ThreemaFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_threema_summary
        WeChatFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_wechat_summary
        ViberFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_viber_summary
        MobileQQFilter::class -> eu.darken.sdmse.appcleaner.R.string.appcleaner_filter_qqchat_summary
        else -> eu.darken.sdmse.common.R.string.general_todo_msg
    }

val <T : ExpendablesFilter> KClass<T>.icon: ImageVector
    get() = when (this) {
        DefaultCachesPublicFilter::class -> Icons.AutoMirrored.TwoTone.FormatListBulleted
        DefaultCachesPrivateFilter::class -> Icons.AutoMirrored.TwoTone.FormatListBulleted
        HiddenFilter::class -> Icons.TwoTone.VisibilityOff
        ThumbnailsFilter::class -> Icons.TwoTone.PermMedia
        CodeCacheFilter::class -> Icons.AutoMirrored.TwoTone.FormatListBulleted
        AdvertisementFilter::class -> Icons.TwoTone.AdsClick
        BugReportingFilter::class -> SdmIcons.Bug
        AnalyticsFilter::class -> Icons.TwoTone.Analytics
        GameFilesFilter::class -> Icons.TwoTone.SportsEsports
        OfflineCacheFilter::class -> Icons.TwoTone.SignalCellularOff
        RecycleBinsFilter::class -> Icons.TwoTone.Delete
        WebViewCacheFilter::class -> SdmIcons.Chrome
        ShortcutServiceFilter::class -> Icons.TwoTone.PhotoLibrary
        WhatsAppBackupsFilter::class -> SdmIcons.WhatsApp
        WhatsAppReceivedFilter::class -> SdmIcons.WhatsApp
        WhatsAppSentFilter::class -> SdmIcons.WhatsApp
        TelegramFilter::class -> Icons.AutoMirrored.TwoTone.Chat
        ThreemaFilter::class -> Icons.AutoMirrored.TwoTone.Chat
        WeChatFilter::class -> SdmIcons.WeChat
        ViberFilter::class -> Icons.AutoMirrored.TwoTone.Chat
        MobileQQFilter::class -> SdmIcons.Qq
        else -> Icons.AutoMirrored.TwoTone.HelpOutline
    }
