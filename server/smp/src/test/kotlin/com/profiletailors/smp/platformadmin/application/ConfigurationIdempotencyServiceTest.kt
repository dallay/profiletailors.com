package com.profiletailors.smp.platformadmin.application

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class ConfigurationIdempotencyServiceTest {
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val store = FakeConfigurationIdempotencyStore()
    private val codec = FakeConfigurationIdempotencyCodec()
    private val service = ConfigurationIdempotencyService(store, codec)

    @Test
    fun `replays the same operation without executing again`() = runTest {
        var executions = 0

        val first = service.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
            executions += 1
            TestResult("OPEN")
        }
        val replay = service.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
            executions += 1
            TestResult("CLOSED")
        }

        assertEquals(first, replay)
        assertEquals(1, executions)
    }

    @Test
    fun `does not execute while the same request is still in progress`() = runTest {
        store.records += ConfigurationIdempotencyRecord(operatorId, "change_registration_mode", "key-1")
        var executions = 0

        assertThrows(ConfigurationIdempotencyInProgressException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
                    executions += 1
                    TestResult("CLOSED")
                }
            }
        }

        assertEquals(0, executions)
    }

    @Test
    fun `rejects same key reused for a different operation`() = runTest {
        service.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
            TestResult("CLOSED")
        }

        assertThrows(ConfigurationIdempotencyConflictException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(operatorId, "some_other_operation", "key-1", TestResult::class.java) {
                    TestResult("OPEN")
                }
            }
        }
    }

    @Test
    fun `returns in progress when a concurrent claim has no completed response`() = runTest {
        val racingStore = RacingConfigurationIdempotencyStore(operatorId, "change_registration_mode", "key-1")
        val racingService = ConfigurationIdempotencyService(racingStore, codec)
        var executions = 0

        assertThrows(ConfigurationIdempotencyInProgressException::class.java) {
            kotlinx.coroutines.runBlocking {
                racingService.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
                    executions += 1
                    TestResult("CLOSED")
                }
            }
        }

        assertEquals(0, executions)
    }

    @Test
    fun `returns in progress when a concurrent claim cannot be read yet`() = runTest {
        val racingService = ConfigurationIdempotencyService(MissingConcurrentRecordStore(), codec)

        assertThrows(ConfigurationIdempotencyInProgressException::class.java) {
            kotlinx.coroutines.runBlocking {
                racingService.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
                    TestResult("CLOSED")
                }
            }
        }
    }

    @Test
    fun `replays the completed response when a claim loses the race`() = runTest {
        val expected = TestResult("CLOSED")
        val racingService = ConfigurationIdempotencyService(
            CompletedRaceStore(operatorId, "change_registration_mode", "key-1", codec.encode(expected)),
            codec,
        )
        var executions = 0

        val result = racingService.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
            executions += 1
            TestResult("OPEN")
        }

        assertEquals(expected, result)
        assertEquals(0, executions)
    }

    @Test
    fun `removes an uncompleted claim when execution fails`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(operatorId, "change_registration_mode", "key-1", TestResult::class.java) {
                    throw IllegalStateException("failed")
                }
            }
        }

        assertEquals(0, store.records.size)
    }

    private data class TestResult(val mode: String)

    private class FakeConfigurationIdempotencyStore : ConfigurationIdempotencyStore {
        val records = mutableListOf<ConfigurationIdempotencyRecord>()

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? =
            records.firstOrNull { it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey }

        override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean {
            if (records.any {
                    it.operatorPrincipalId == record.operatorPrincipalId &&
                        it.idempotencyKey == record.idempotencyKey
                }
            ) {
                return false
            }
            records += record
            return true
        }

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) {
            val index = records.indexOfFirst {
                it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey
            }
            records[index] = records[index].copy(responseJson = responseJson)
        }

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) {
            records.removeIf { it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey }
        }
    }

    private class MissingConcurrentRecordStore : ConfigurationIdempotencyStore {
        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? =
            null

        override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean = false

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) = Unit

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) = Unit
    }

    private class RacingConfigurationIdempotencyStore(
        private val operatorId: UUID,
        private val operation: String,
        private val key: String,
    ) : ConfigurationIdempotencyStore {
        private var findCalls = 0

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? {
            if (operatorPrincipalId != operatorId || idempotencyKey != key) return null
            findCalls += 1
            return if (findCalls == 1) null else ConfigurationIdempotencyRecord(operatorId, operation, key)
        }

        override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean = false

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) = Unit

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) = Unit
    }

    private class CompletedRaceStore(
        private val operatorId: UUID,
        private val operation: String,
        private val key: String,
        private val responseJson: String,
    ) : ConfigurationIdempotencyStore {
        private var findCalls = 0

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): ConfigurationIdempotencyRecord? {
            if (operatorPrincipalId != operatorId || idempotencyKey != key) return null
            findCalls += 1
            if (findCalls == 1) return null
            return ConfigurationIdempotencyRecord(operatorId, operation, key, responseJson)
        }

        override suspend fun claim(record: ConfigurationIdempotencyRecord): Boolean = false

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) = Unit

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) = Unit
    }

    private class FakeConfigurationIdempotencyCodec : ConfigurationIdempotencyCodec {
        private val values = mutableMapOf<String, Any>()

        override fun encode(value: Any): String {
            val json = value.toString()
            values[json] = value
            return json
        }

        override fun <T : Any> decode(responseJson: String, responseType: Class<T>): T {
            val value = values[responseJson] ?: error("Missing encoded value")
            if (!responseType.isInstance(value)) error("Unexpected response type")
            return responseType.cast(value)
        }
    }
}
