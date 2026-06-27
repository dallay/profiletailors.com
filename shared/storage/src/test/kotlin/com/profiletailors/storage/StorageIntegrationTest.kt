package com.profiletailors.storage

import com.profiletailors.storage.domain.BucketRegistry
import com.profiletailors.storage.domain.Storage
import com.profiletailors.storage.infrastructure.LocalFilesystemStorage
import com.profiletailors.storage.infrastructure.S3Storage
import com.profiletailors.storage.infrastructure.StorageAutoConfiguration
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [StorageIntegrationTest.TestApplication::class])
@TestPropertySource(
    properties = [
        "platform.storage.default=local-bucket",
        "platform.storage.providers.local-bucket.type=local",
        "platform.storage.providers.local-bucket.base-path=\${java.io.tmpdir}/smp-tests",
        "platform.storage.providers.s3-bucket.type=s3",
        "platform.storage.providers.s3-bucket.bucket=my-s3-bucket",
        "platform.storage.providers.s3-bucket.region=us-east-1",
        "platform.storage.providers.s3-bucket.access-key-id=dummy",
        "platform.storage.providers.s3-bucket.secret-access-key=dummy",
    ],
)
class StorageIntegrationTest {

    @Configuration
    @ComponentScan(basePackages = ["com.profiletailors.storage.infrastructure.metrics"])
    @Import(StorageAutoConfiguration::class)
    class TestApplication {
        @Bean
        fun meterRegistry() = SimpleMeterRegistry()
    }

    @Autowired
    lateinit var registry: BucketRegistry

    @Autowired
    lateinit var defaultStorage: Storage

    @Test
    fun `should load multiple providers from properties`() {
        val local = registry.getStorage("local-bucket")
        val s3 = registry.getStorage("s3-bucket")

        assertNotNull(local)
        assertNotNull(s3)
        assertTrue(local is LocalFilesystemStorage)
        assertTrue(s3 is S3Storage)
    }

    @Test
    fun `should load default storage`() {
        assertNotNull(defaultStorage)
        assertTrue(defaultStorage is LocalFilesystemStorage)
    }
}
