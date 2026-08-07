package com.homecinema.library.data.smb

import com.homecinema.library.R
import jcifs.CIFSUnsupportedCryptoException
import jcifs.smb.NtStatus
import jcifs.smb.SmbException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Asserts on the resolved [SmbUserMessage] spec (resource id + args, or raw text) rather than
 * final translated text - that keeps this suite language-independent (it doesn't need a real
 * Android Context/resource resolution, unlike [Throwable.toSmbUserMessage] itself) while still
 * covering the actual branching logic these messages depend on. */
class SmbErrorMessagesTest {

    @Test
    fun `share unavailable maps to a localized message with the share name`() {
        val e = ShareUnavailableException("Movies")
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_share_unavailable, listOf("Movies")), e.toSmbUserMessageSpec())
    }

    @Test
    fun `wrong password maps to a friendly login message`() {
        val e = SmbException(NtStatus.NT_STATUS_WRONG_PASSWORD, false)
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_wrong_password), e.toSmbUserMessageSpec())
    }

    @Test
    fun `logon failure maps to the same friendly login message`() {
        val e = SmbException(NtStatus.NT_STATUS_LOGON_FAILURE, false)
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_wrong_password), e.toSmbUserMessageSpec())
    }

    @Test
    fun `bad network name maps to share-not-found message`() {
        val e = SmbException(NtStatus.NT_STATUS_BAD_NETWORK_NAME, false)
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_share_not_found), e.toSmbUserMessageSpec())
    }

    @Test
    fun `object path not found maps to path-not-found message`() {
        val e = SmbException(NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND, false)
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_path_not_found), e.toSmbUserMessageSpec())
    }

    @Test
    fun `access denied maps to permissions message`() {
        val e = SmbException(NtStatus.NT_STATUS_ACCESS_DENIED, false)
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_access_denied), e.toSmbUserMessageSpec())
    }

    @Test
    fun `unmapped nt status falls back to the exception's own message`() {
        val e = SmbException("Some other SMB failure")
        assertEquals(SmbUserMessage.Raw("Some other SMB failure"), e.toSmbUserMessageSpec())
    }

    @Test
    fun `unknown host maps to a server-not-found message with the host name`() {
        val e = UnknownHostException("nas.local")
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_host_not_found, listOf("nas.local")), e.toSmbUserMessageSpec())
    }

    @Test
    fun `connect exception maps to a server-unreachable message`() {
        val e = ConnectException("Connection refused")
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_unreachable), e.toSmbUserMessageSpec())
    }

    @Test
    fun `socket timeout maps to a not-responding message`() {
        val e = SocketTimeoutException("timeout")
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_timeout), e.toSmbUserMessageSpec())
    }

    @Test
    fun `unsupported crypto maps to a reinstall hint`() {
        val e = CIFSUnsupportedCryptoException("no such algorithm: MD4 for provider BC")
        assertEquals(SmbUserMessage.Localized(R.string.smb_err_crypto), e.toSmbUserMessageSpec())
    }

    @Test
    fun `unrecognized exception type falls back to its message`() {
        val e = IllegalStateException("something odd happened")
        assertEquals(SmbUserMessage.Raw("something odd happened"), e.toSmbUserMessageSpec())
    }

    @Test
    fun `unrecognized exception with no message falls back to the class name`() {
        val e = IllegalStateException()
        assertEquals(SmbUserMessage.Raw("IllegalStateException"), e.toSmbUserMessageSpec())
    }
}
