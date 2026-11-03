package com.lagradost.cloudstream3.desktop.sandbox.host

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxedPluginHostTest {

    @Test
    fun privateAndLocalRepositoryUrlsAreRejected() {
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://127.0.0.1:8080/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://localhost/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://10.0.0.1/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://172.16.0.1/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://192.168.1.1/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://169.254.1.1/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("http://[::1]/repo.json"))
        assertTrue(SandboxedPluginHost.isBlockedOrPrivateUrl("file:///C:/repo.json"))
    }

    @Test
    fun publicRepositoryUrlIsAllowed() {
        assertFalse(SandboxedPluginHost.isBlockedOrPrivateUrl("https://1.1.1.1/repo.json"))
    }
}
