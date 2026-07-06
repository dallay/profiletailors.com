package com.profiletailors.smp.mediaprovider.unsplash

import com.profiletailors.smp.media.application.port.MediaProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * Verifies the conditional registration of Unsplash provider beans.
 *
 * Uses [ApplicationContextRunner] so no full Spring context or network call is needed.
 * The [MediaProviderConfig] is the sole entrypoint — beans must appear only when
 * `mediaprovider.unsplash.enabled=true` AND a valid access key is set.
 */
class MediaProviderConfigTest {

    /**
     * Verifies that [MediaProviderConfig] registers Unsplash beans only when the
     * feature flag is on. [UnsplashWebClient] is deliberately NOT included here:
     * it has no `@Component` — its sole registration path is the `@Bean` method
     * inside [MediaProviderConfig].
     */
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(MediaProviderConfig::class.java)

    // ── Feature flag OFF ─────────────────────────────────────────────────

    @Test
    fun `no MediaProvider bean when enabled is false`() {
        runner
            .withPropertyValues("mediaprovider.unsplash.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(MediaProvider::class.java)
            }
    }

    @Test
    fun `no UnsplashClient bean when enabled is false`() {
        runner
            .withPropertyValues("mediaprovider.unsplash.enabled=false")
            .run { ctx ->
                assertThat(ctx).doesNotHaveBean(UnsplashClient::class.java)
            }
    }

    @Test
    fun `no MediaProvider bean when enabled property is absent`() {
        runner.run { ctx ->
            assertThat(ctx).doesNotHaveBean(MediaProvider::class.java)
        }
    }

    // ── Feature flag ON ──────────────────────────────────────────────────

    @Test
    fun `MediaProvider bean is present when enabled and access key configured`() {
        runner
            .withPropertyValues(
                "mediaprovider.unsplash.enabled=true",
                "mediaprovider.unsplash.access-key=test-key-abc",
            )
            .run { ctx ->
                assertThat(ctx).hasSingleBean(MediaProvider::class.java)
                assertThat(ctx.getBean(MediaProvider::class.java)).isInstanceOf(UnsplashAdapter::class.java)
            }
    }

    @Test
    fun `UnsplashClient bean is present when enabled`() {
        runner
            .withPropertyValues(
                "mediaprovider.unsplash.enabled=true",
                "mediaprovider.unsplash.access-key=test-key-abc",
            )
            .run { ctx ->
                assertThat(ctx).hasSingleBean(UnsplashClient::class.java)
            }
    }

    @Test
    fun `UnsplashProperties binds access key from property`() {
        runner
            .withPropertyValues(
                "mediaprovider.unsplash.enabled=true",
                "mediaprovider.unsplash.access-key=my-secret-key",
            )
            .run { ctx ->
                val props = ctx.getBean(UnsplashProperties::class.java)
                assertThat(props.accessKey).isEqualTo("my-secret-key")
                assertThat(props.enabled).isTrue()
            }
    }
}
