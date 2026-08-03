package com.homecinema.library.data.smb

import jcifs.CIFSUnsupportedCryptoException
import jcifs.smb.NtStatus
import jcifs.smb.SmbException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SmbErrorMessagesTest {

    @Test
    fun `wrong password maps to a friendly login message`() {
        val e = SmbException(NtStatus.NT_STATUS_WRONG_PASSWORD, false)
        assertEquals("Неверный логин или пароль.", e.toSmbUserMessage())
    }

    @Test
    fun `logon failure maps to the same friendly login message`() {
        val e = SmbException(NtStatus.NT_STATUS_LOGON_FAILURE, false)
        assertEquals("Неверный логин или пароль.", e.toSmbUserMessage())
    }

    @Test
    fun `bad network name maps to share-not-found message`() {
        val e = SmbException(NtStatus.NT_STATUS_BAD_NETWORK_NAME, false)
        assertEquals("Общая папка не найдена — проверьте её название.", e.toSmbUserMessage())
    }

    @Test
    fun `object path not found maps to path-not-found message`() {
        val e = SmbException(NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND, false)
        assertEquals("Указанный путь не найден на сервере.", e.toSmbUserMessage())
    }

    @Test
    fun `access denied maps to permissions message`() {
        val e = SmbException(NtStatus.NT_STATUS_ACCESS_DENIED, false)
        assertEquals("Доступ запрещён — проверьте права доступа к папке на сервере.", e.toSmbUserMessage())
    }

    @Test
    fun `unmapped nt status falls back to the exception's own message`() {
        val e = SmbException("Some other SMB failure")
        assertEquals("Some other SMB failure", e.toSmbUserMessage())
    }

    @Test
    fun `unknown host maps to a server-not-found message with the host name`() {
        val e = UnknownHostException("nas.local")
        assertEquals("Сервер «nas.local» не найден — проверьте адрес.", e.toSmbUserMessage())
    }

    @Test
    fun `connect exception maps to a server-unreachable message`() {
        val e = ConnectException("Connection refused")
        assertEquals("Сервер недоступен — проверьте, что он включён и находится в той же сети.", e.toSmbUserMessage())
    }

    @Test
    fun `socket timeout maps to a not-responding message`() {
        val e = SocketTimeoutException("timeout")
        assertEquals("Сервер не отвечает — проверьте подключение к сети.", e.toSmbUserMessage())
    }

    @Test
    fun `unsupported crypto maps to a reinstall hint`() {
        val e = CIFSUnsupportedCryptoException("no such algorithm: MD4 for provider BC")
        assertEquals(
            "Устройству не хватает нужных алгоритмов шифрования. Попробуйте переустановить приложение.",
            e.toSmbUserMessage()
        )
    }

    @Test
    fun `unrecognized exception type falls back to its message`() {
        val e = IllegalStateException("something odd happened")
        assertEquals("something odd happened", e.toSmbUserMessage())
    }

    @Test
    fun `unrecognized exception with no message falls back to the class name`() {
        val e = IllegalStateException()
        assertEquals("IllegalStateException", e.toSmbUserMessage())
    }
}
