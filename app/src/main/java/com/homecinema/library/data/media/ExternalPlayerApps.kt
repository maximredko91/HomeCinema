package com.homecinema.library.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri

data class ExternalPlayerApp(val packageName: String, val label: String)

/**
 * Every installed app that can handle a video ACTION_VIEW intent, for the "always use this
 * player" setting - queried the same way Android itself resolves playExternally's implicit
 * intent, just enumerating every match instead of only the one the user eventually picks.
 */
fun queryExternalPlayerApps(context: Context): List<ExternalPlayerApp> {
    val probeIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse("content://dummy/video"), "video/*")
    }
    val packageManager = context.packageManager
    // The typed ResolveInfoFlags overload needs API 33 - minSdk here is 31, so the plain-Int
    // overload (still functional on newer APIs too, just soft-deprecated) is what actually
    // works across this app's whole supported range.
    @Suppress("DEPRECATION")
    return packageManager.queryIntentActivities(probeIntent, 0)
        .map { resolveInfo ->
            ExternalPlayerApp(
                packageName = resolveInfo.activityInfo.packageName,
                label = resolveInfo.loadLabel(packageManager).toString()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
