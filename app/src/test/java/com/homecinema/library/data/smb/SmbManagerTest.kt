package com.homecinema.library.data.smb

import com.homecinema.library.data.settings.SmbConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SmbManagerTest {

    private val manager = SmbManager()

    @Test
    fun `builds the share root url with no sub-path or rootPath`() {
        val config = SmbConfig(host = "192.168.1.1", share = "disk1_ssd")
        assertEquals("smb://192.168.1.1/disk1_ssd/", manager.rootUrl(config))
    }

    @Test
    fun `includes the configured rootPath`() {
        val config = SmbConfig(host = "192.168.1.1", share = "disk1_ssd", rootPath = "Video/Movies")
        assertEquals("smb://192.168.1.1/disk1_ssd/Video/Movies/", manager.rootUrl(config))
    }

    @Test
    fun `trims leading and trailing slashes from share and rootPath`() {
        val config = SmbConfig(host = "192.168.1.1", share = "/disk1_ssd/", rootPath = "/Video/Movies/")
        assertEquals("smb://192.168.1.1/disk1_ssd/Video/Movies/", manager.rootUrl(config))
    }

    @Test
    fun `appends an extra sub-path after rootPath`() {
        val config = SmbConfig(host = "192.168.1.1", share = "disk1_ssd", rootPath = "Video")
        assertEquals("smb://192.168.1.1/disk1_ssd/Video/Extras/", manager.rootUrl(config, subPath = "Extras"))
    }

    @Test
    fun `sub-path works even with no configured rootPath`() {
        val config = SmbConfig(host = "192.168.1.1", share = "disk1_ssd")
        assertEquals("smb://192.168.1.1/disk1_ssd/Extras/", manager.rootUrl(config, subPath = "/Extras/"))
    }
}
