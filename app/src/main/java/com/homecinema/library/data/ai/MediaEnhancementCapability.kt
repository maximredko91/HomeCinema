package com.homecinema.library.data.ai

import android.content.Context
import androidx.core.performance.DevicePerformance
import androidx.core.performance.play.services.PlayServicesDevicePerformance
import com.google.android.gms.media.effect.enhancement.Enhancement
import kotlinx.coroutines.tasks.await

/** Result of probing this device for on-device AI media enhancement (poster/video upscaling,
 * tone mapping) support - see [checkCapability]. Two independent signals, kept separate rather
 * than collapsed into one boolean, since they answer different questions worth surfacing to the
 * user distinctly if this ever needs debugging: [mediaPerformanceClass] is Android's own coarse
 * "how capable is this SoC" rating (0 if the device predates the standard or doesn't qualify for
 * any tier), while [enhancementApiSupported] is the actual, specific answer from the Enhancement
 * API itself for whether it can run at all on this hardware. */
data class MediaEnhancementCapability(
    val mediaPerformanceClass: Int,
    val enhancementApiSupported: Boolean
)

object MediaEnhancementCapabilityChecker {
    suspend fun checkCapability(context: Context): MediaEnhancementCapability {
        val devicePerformance: DevicePerformance = PlayServicesDevicePerformance(context)
        val mpc = devicePerformance.mediaPerformanceClass

        val supported = runCatching {
            Enhancement.getClient(context).isDeviceSupported().await()
        }.getOrDefault(false)

        return MediaEnhancementCapability(
            mediaPerformanceClass = mpc,
            enhancementApiSupported = supported
        )
    }
}
