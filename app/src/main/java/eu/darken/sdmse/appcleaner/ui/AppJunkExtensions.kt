package eu.darken.sdmse.appcleaner.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import eu.darken.sdmse.R
import eu.darken.sdmse.appcleaner.core.forensics.ExpendablesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.AdvertisementFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.AnalyticsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.BugReportingFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.CodeCacheFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPrivateFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.DefaultCachesPublicFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.GameFilesFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.HiddenFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.OfflineCacheFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.RecycleBinsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.TelegramFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThreemaFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ThumbnailsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.ViberFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WeChatFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WebViewCacheFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WhatsAppBackupsFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WhatsAppReceivedFilter
import eu.darken.sdmse.appcleaner.core.forensics.filter.WhatsAppSentFilter
import kotlin.reflect.KClass

@get:StringRes
val <T : ExpendablesFilter> KClass<T>.labelRes: Int
    get() = when (this) {
        DefaultCachesPublicFilter::class -> R.string.appcleaner_filter_defaultcachespublic_label
        DefaultCachesPrivateFilter::class -> R.string.appcleaner_filter_defaultcachesprivate_label
        HiddenFilter::class -> R.string.appcleaner_filter_hiddencaches_label
        ThumbnailsFilter::class -> R.string.appcleaner_filter_thumbnails_label
        CodeCacheFilter::class -> R.string.appcleaner_filter_codecache_label
        AdvertisementFilter::class -> R.string.appcleaner_filter_advertisement_label
        BugReportingFilter::class -> R.string.appcleaner_filter_bugreporting_label
        AnalyticsFilter::class -> R.string.appcleaner_filter_analytics_label
        GameFilesFilter::class -> R.string.appcleaner_filter_gamefiles_label
        OfflineCacheFilter::class -> R.string.appcleaner_filter_offlinecache_label
        RecycleBinsFilter::class -> R.string.appcleaner_filter_recyclebins_label
        WebViewCacheFilter::class -> R.string.appcleaner_filter_webview_label
        WhatsAppBackupsFilter::class -> R.string.appcleaner_filter_whatsapp_backups_label
        WhatsAppReceivedFilter::class -> R.string.appcleaner_filter_whatsapp_received_label
        WhatsAppSentFilter::class -> R.string.appcleaner_filter_whatsapp_sent_label
        TelegramFilter::class -> R.string.appcleaner_filter_telegram_label
        ThreemaFilter::class -> R.string.appcleaner_filter_threema_label
        WeChatFilter::class -> R.string.appcleaner_filter_wechat_label
        ViberFilter::class -> R.string.appcleaner_filter_viber_label
        else -> eu.darken.sdmse.common.R.string.general_todo_msg
    }

@get:StringRes
val <T : ExpendablesFilter> KClass<T>.descriptionRes: Int
    get() = when (this) {
        DefaultCachesPublicFilter::class -> R.string.appcleaner_filter_defaultcachespublic_summary
        DefaultCachesPrivateFilter::class -> R.string.appcleaner_filter_defaultcachesprivate_summary
        HiddenFilter::class -> R.string.appcleaner_filter_hiddencaches_summary
        ThumbnailsFilter::class -> R.string.appcleaner_filter_thumbnails_summary
        CodeCacheFilter::class -> R.string.appcleaner_filter_codecache_summary
        AdvertisementFilter::class -> R.string.appcleaner_filter_advertisement_summary
        BugReportingFilter::class -> R.string.appcleaner_filter_bugreporting_summary
        AnalyticsFilter::class -> R.string.appcleaner_filter_analytics_summary
        GameFilesFilter::class -> R.string.appcleaner_filter_gamefiles_summary
        OfflineCacheFilter::class -> R.string.appcleaner_filter_offlinecache_summary
        RecycleBinsFilter::class -> R.string.appcleaner_filter_recyclebins_summary
        WebViewCacheFilter::class -> R.string.appcleaner_filter_webview_summary
        WhatsAppBackupsFilter::class -> R.string.appcleaner_filter_whatsapp_backups_summary
        WhatsAppReceivedFilter::class -> R.string.appcleaner_filter_whatsapp_received_summary
        WhatsAppSentFilter::class -> R.string.appcleaner_filter_whatsapp_sent_summary
        TelegramFilter::class -> R.string.appcleaner_filter_telegram_summary
        ThreemaFilter::class -> R.string.appcleaner_filter_threema_summary
        WeChatFilter::class -> R.string.appcleaner_filter_wechat_summary
        ViberFilter::class -> R.string.appcleaner_filter_viber_summary
        else -> eu.darken.sdmse.common.R.string.general_todo_msg
    }

@get:DrawableRes
val <T : ExpendablesFilter> KClass<T>.iconsRes: Int
    get() = when (this) {
        DefaultCachesPublicFilter::class -> R.drawable.ic_baseline_format_list_bulleted_24
        DefaultCachesPrivateFilter::class -> R.drawable.ic_baseline_format_list_bulleted_24
        HiddenFilter::class -> R.drawable.ic_hidden_file_24
        ThumbnailsFilter::class -> R.drawable.ic_multimedia_24
        CodeCacheFilter::class -> R.drawable.ic_baseline_format_list_bulleted_24
        AdvertisementFilter::class -> R.drawable.ic_baseline_ads_click_24
        BugReportingFilter::class -> R.drawable.ic_bug_report
        AnalyticsFilter::class -> R.drawable.ic_analytics_onsurface
        GameFilesFilter::class -> R.drawable.ic_game_controller_24
        OfflineCacheFilter::class -> R.drawable.ic_signal_off_24
        RecycleBinsFilter::class -> R.drawable.ic_recycle_bin_24
        WebViewCacheFilter::class -> R.drawable.ic_chrome_24
        WhatsAppBackupsFilter::class -> R.drawable.ic_whatsapp_24
        WhatsAppReceivedFilter::class -> R.drawable.ic_whatsapp_24
        WhatsAppSentFilter::class -> R.drawable.ic_whatsapp_24
        TelegramFilter::class -> R.drawable.ic_chat_24
        ThreemaFilter::class -> R.drawable.ic_chat_24
        WeChatFilter::class -> R.drawable.ic_wechat_24
        ViberFilter::class -> R.drawable.ic_chat_24
        else -> R.drawable.file_question
    }
