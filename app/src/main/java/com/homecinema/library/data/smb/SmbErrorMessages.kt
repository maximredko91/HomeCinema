package com.homecinema.library.data.smb

import jcifs.CIFSUnsupportedCryptoException
import jcifs.smb.NtStatus
import jcifs.smb.SmbException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Translates the exception types that can come out of an SMB connection attempt (scanning,
 * "проверить подключение", downloads) into short Russian messages a non-technical user can
 * act on. Without this, failures surface as raw jcifs/network-stack text - NT-status jargon
 * like "Logon failure: unknown user name or bad password." or a bare
 * "NoSuchAlgorithmException: no such algorithm: MD4 for provider BC" - which explains nothing
 * to someone who isn't the developer.
 */
fun Throwable.toSmbUserMessage(): String = when (this) {
    is SmbException -> ntStatusMessage(ntStatus) ?: (message?.takeIf { it.isNotBlank() } ?: "Ошибка подключения к серверу.")
    is CIFSUnsupportedCryptoException ->
        "Устройству не хватает нужных алгоритмов шифрования. Попробуйте переустановить приложение."
    is UnknownHostException -> "Сервер «${message ?: "?"}» не найден — проверьте адрес."
    is NoRouteToHostException, is ConnectException ->
        "Сервер недоступен — проверьте, что он включён и находится в той же сети."
    is SocketTimeoutException -> "Сервер не отвечает — проверьте подключение к сети."
    else -> message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "Неизвестная ошибка")
}

private fun ntStatusMessage(status: Int): String? = when (status) {
    NtStatus.NT_STATUS_WRONG_PASSWORD, NtStatus.NT_STATUS_LOGON_FAILURE -> "Неверный логин или пароль."
    NtStatus.NT_STATUS_ACCESS_DENIED, NtStatus.NT_STATUS_LOGON_TYPE_NOT_GRANTED ->
        "Доступ запрещён — проверьте права доступа к папке на сервере."
    NtStatus.NT_STATUS_ACCOUNT_LOCKED_OUT -> "Учётная запись заблокирована на сервере."
    NtStatus.NT_STATUS_ACCOUNT_DISABLED -> "Учётная запись отключена на сервере."
    NtStatus.NT_STATUS_PASSWORD_EXPIRED -> "Пароль на сервере истёк — обновите его."
    NtStatus.NT_STATUS_BAD_NETWORK_NAME -> "Общая папка не найдена — проверьте её название."
    NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND ->
        "Указанный путь не найден на сервере."
    else -> null
}
