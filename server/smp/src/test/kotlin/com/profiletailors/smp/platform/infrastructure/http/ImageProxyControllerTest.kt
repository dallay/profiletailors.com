package com.profiletailors.smp.platform.infrastructure.http

import org.junit.jupiter.api.Test

class ImageProxyControllerTest {

    @Test
    fun `allows licdn com host`() {
        assert(controller.allowedHosts.contains("media.licdn.com"))
    }

    @Test
    fun `allows twimg com host`() {
        assert(controller.allowedHosts.contains("pbs.twimg.com"))
    }

    @Test
    fun `allows facebook cdn hosts`() {
        assert(controller.allowedHosts.contains("platform-lookaside.fbsbx.com"))
        assert(controller.allowedHosts.contains("scontent.xx.fbcdn.net"))
    }

    @Test
    fun `allows instagram cdn host`() {
        assert(controller.allowedHosts.contains("instagram.fbog1-1.fna.fbcdn.net"))
    }

    @Test
    fun `does not allow arbitrary hosts`() {
        assert("evil.com" !in controller.allowedHosts)
        assert("media.evil.com" !in controller.allowedHosts)
        assert("licdn.com" !in controller.allowedHosts)
        assert("example.com" !in controller.allowedHosts)
    }

    companion object {
        private val controller = ImageProxyController(
            webClient = org.springframework.web.reactive.function.client.WebClient.builder().build(),
        )
    }
}
