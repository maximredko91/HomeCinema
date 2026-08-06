package com.homecinema.library.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri

data class ExternalPlayerApp(val packageName: String, val label: String)

/**
 * Every installed app that can handle a video ACTION_VIEW intent, for the "always use this
 * player" setting - queried the same way Android itself resolves playExternally's implicit
 * intent, just enumerating every match instead of only the one the user eventually picks.
 *
 * A previous version tried to filter this down further by inspecting which IntentFilter
 * matched (ResolveInfo.filter) and dropping anything that only matched via a fully-wildcard
 * data type, on the theory that a generic Music app declaring a catch-all MIME type shouldn't
 * show up here. In practice that filter was wrong more than it was right - it hid real,
 * installed video players (confirmed: MX Player disappeared from this list on a real device)
 * while some non-video apps still slipped through anyway, since there's no reliable way from
 * here to tell "this app registered a catch-all because it's a genuine do-anything file opener"
 * apart from "this app registered a catch-all because it's an unrelated app with a broad
 * filter". Hiding a real player is a much worse failure than occasionally showing an irrelevant
 * one, so this goes back to exactly what Android's own "Open with" dialog would show for the
 * same intent - no second-guessing which of those matches "really" means video.
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
