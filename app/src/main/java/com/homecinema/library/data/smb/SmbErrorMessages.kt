package com.homecinema.library.data.smb

import android.content.Context
import androidx.annotation.StringRes
import com.homecinema.library.R
import jcifs.CIFSUnsupportedCryptoException
import jcifs.smb.NtStatus
import jcifs.smb.SmbException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Thrown when a configured share can't be listed at all (wrong share name, bad path, auth
 * rejected, host unreachable...) - a dedicated type rather than a raw IOException so
 * [toSmbUserMessageSpec] can map it to a real localized message instead of leaking whatever
 * language its own hardcoded text happened to be written in. */
class ShareUnavailableException(val shareName: String) : java.io.IOException("Share unavailable: $shareName")

/** Either a raw, already-human-readable string (an exception's own message, or a bare class
 * name - neither needs translation) or a localized message resource with format args. Kept
 * separate from the final resolved [String] so [Throwable.toSmbUserMessageSpec] can be unit
 * tested without an Android Context/real resource resolution - only [Throwable.toSmbUserMessage]
 * needs one, to actually resolve a [Localized] spec via [Context.getString]. */
internal sealed class SmbUserMessage {
    data class Raw(val text: String) : SmbUserMessage()
    data class Localized(@StringRes val resId: Int, val args: List<Any> = emptyList()) : SmbUserMessage()
}

/**
 * Translates the exception types that can come out of an SMB connection attempt (scanning,
 * "test connection", downloads) into a short message spec a non-technical user can act on.
 * Without this, failures surface as raw jcifs/network-stack text - NT-status jargon like
 * "Logon failure: unknown user name or bad password." or a bare "NoSuchAlgorithmException: no
 * such algorithm: MD4 for provider BC" - which explains nothing to someone who isn't the
 * developer.
 */
internal fun Throwable.toSmbUserMessageSpec(): SmbUserMessage = when (this) {
    is ShareUnavailableException -> SmbUserMessage.Localized(R.string.smb_err_share_unavailable, listOf(shareName))
    is SmbException -> ntStatusMessage(ntStatus)
        ?: message?.takeIf { it.isNotBlank() }?.let { SmbUserMessage.Raw(it) }
        ?: SmbUserMessage.Localized(R.string.smb_err_connection)
    is CIFSUnsupportedCryptoException -> SmbUserMessage.Localized(R.string.smb_err_crypto)
    is UnknownHostException -> SmbUserMessage.Localized(R.string.smb_err_host_not_found, listOf(message ?: "?"))
    is NoRouteToHostException, is ConnectException -> SmbUserMessage.Localized(R.string.smb_err_unreachable)
    is SocketTimeoutException -> SmbUserMessage.Localized(R.string.smb_err_timeout)
    else -> message?.takeIf { it.isNotBlank() }?.let { SmbUserMessage.Raw(it) }
        ?: this::class.simpleName?.let { SmbUserMessage.Raw(it) }
        ?: SmbUserMessage.Localized(R.string.smb_err_unknown)
}

fun Throwable.toSmbUserMessage(context: Context): String = when (val spec = toSmbUserMessageSpec()) {
    is SmbUserMessage.Raw -> spec.text
    is SmbUserMessage.Localized -> context.getString(spec.resId, *spec.args.toTypedArray())
}

private fun ntStatusMessage(status: Int): SmbUserMessage.Localized? = when (status) {
    NtStatus.NT_STATUS_WRONG_PASSWORD, NtStatus.NT_STATUS_LOGON_FAILURE -> SmbUserMessage.Localized(R.string.smb_err_wrong_password)
    NtStatus.NT_STATUS_ACCESS_DENIED, NtStatus.NT_STATUS_LOGON_TYPE_NOT_GRANTED -> SmbUserMessage.Localized(R.string.smb_err_access_denied)
    NtStatus.NT_STATUS_ACCOUNT_LOCKED_OUT -> SmbUserMessage.Localized(R.string.smb_err_account_locked)
    NtStatus.NT_STATUS_ACCOUNT_DISABLED -> SmbUserMessage.Localized(R.string.smb_err_account_disabled)
    NtStatus.NT_STATUS_PASSWORD_EXPIRED -> SmbUserMessage.Localized(R.string.smb_err_password_expired)
    NtStatus.NT_STATUS_BAD_NETWORK_NAME -> SmbUserMessage.Localized(R.string.smb_err_share_not_found)
    NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND -> SmbUserMessage.Localized(R.string.smb_err_path_not_found)
    else -> null
}
