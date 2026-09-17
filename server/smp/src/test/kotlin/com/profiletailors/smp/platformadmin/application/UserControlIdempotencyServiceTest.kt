package com.profiletailors.smp.platformadmin.application

import com.profiletailors.smp.identity.domain.UserAccountState
import com.profiletailors.smp.platformadmin.application.model.UserControlResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class UserControlIdempotencyServiceTest {
    private val operatorId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val store = FakeUserControlIdempotencyStore()
    private val codec = FakeUserControlIdempotencyCodec()
    private val service = UserControlIdempotencyService(store, codec)

    @Test
    fun `replays the same operation and target without executing again`() = runTest {
        var executions = 0

        val first = service.execute(
            operatorId,
            "disable",
            "user-1",
            "key-1",
            UserControlResult::class.java,
        ) {
            executions += 1
            UserControlResult("user-1", UserAccountState.DISABLED, 2)
        }
        val replay = service.execute(
            operatorId,
            "disable",
            "user-1",
            "key-1",
            UserControlResult::class.java,
        ) {
            executions += 1
            UserControlResult("user-1", UserAccountState.ACTIVE, 0)
        }

        assertEquals(first, replay)
        assertEquals(1, executions)
    }

    @Test
    fun `does not execute while the same request is still in progress`() = runTest {
        store.records += UserControlIdempotencyRecord(
            operatorPrincipalId = operatorId,
            operation = "disable",
            targetPrincipalId = "user-1",
            idempotencyKey = "key-1",
        )
        var executions = 0

        assertThrows(UserControlIdempotencyInProgressException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(
                    operatorId,
                    "disable",
                    "user-1",
                    "key-1",
                    UserControlResult::class.java,
                ) {
                    executions += 1
                    UserControlResult("user-1", UserAccountState.DISABLED, 0)
                }
            }
        }

        assertEquals(0, executions)
    }

    @Test
    fun `rejects same key reused for a different operation or target`() = runTest {
        service.execute(
            operatorId,
            "disable",
            "user-1",
            "key-1",
            UserControlResult::class.java,
        ) { UserControlResult("user-1", UserAccountState.DISABLED, 0) }

        assertThrows(UserControlIdempotencyConflictException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(
                    operatorId,
                    "enable",
                    "user-1",
                    "key-1",
                    UserControlResult::class.java,
                ) { UserControlResult("user-1", UserAccountState.ACTIVE, 0) }
            }
        }
        assertThrows(UserControlIdempotencyConflictException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(
                    operatorId,
                    "disable",
                    "user-2",
                    "key-1",
                    UserControlResult::class.java,
                ) { UserControlResult("user-2", UserAccountState.DISABLED, 0) }
            }
        }
    }

    @Test
    fun `returns in progress when a concurrent claim has no completed response`() = runTest {
        val racingStore = RacingUserControlIdempotencyStore(operatorId, "disable", "user-1", "key-1")
        val racingService = UserControlIdempotencyService(racingStore, codec)
        var executions = 0

        assertThrows(UserControlIdempotencyInProgressException::class.java) {
            kotlinx.coroutines.runBlocking {
                racingService.execute(
                    operatorId,
                    "disable",
                    "user-1",
                    "key-1",
                    UserControlResult::class.java,
                ) {
                    executions += 1
                    UserControlResult("user-1", UserAccountState.DISABLED, 0)
                }
            }
        }

        assertEquals(0, executions)
    }

    @Test
    fun `returns in progress when a concurrent claim cannot be read yet`() = runTest {
        val racingService = UserControlIdempotencyService(
            store = MissingConcurrentRecordStore(),
            codec = codec,
        )

        assertThrows(UserControlIdempotencyInProgressException::class.java) {
            kotlinx.coroutines.runBlocking {
                racingService.execute(
                    operatorId,
                    "disable",
                    "user-1",
                    "key-1",
                    UserControlResult::class.java,
                ) { UserControlResult("user-1", UserAccountState.DISABLED, 0) }
            }
        }
    }

    @Test
    fun `removes an uncompleted claim when execution fails`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                service.execute(
                    operatorId,
                    "disable",
                    "user-1",
                    "key-1",
                    UserControlResult::class.java,
                ) { throw IllegalStateException("failed") }
            }
        }

        assertEquals(0, store.records.size)
    }

    private class FakeUserControlIdempotencyStore : UserControlIdempotencyStore {
        val records = mutableListOf<UserControlIdempotencyRecord>()

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): UserControlIdempotencyRecord? =
            records.firstOrNull { it.operatorPrincipalId == operatorPrincipalId && it.idempotencyKey == idempotencyKey }

        override suspend fun claim(record: UserControlIdempotencyRecord): Boolean {
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

    private class MissingConcurrentRecordStore : UserControlIdempotencyStore {
        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): UserControlIdempotencyRecord? =
            null

        override suspend fun claim(record: UserControlIdempotencyRecord): Boolean = false

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) = Unit

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) = Unit
    }

    private class RacingUserControlIdempotencyStore(
        private val operatorId: UUID,
        private val operation: String,
        private val targetId: String,
        private val key: String,
    ) : UserControlIdempotencyStore {
        private var findCalls = 0

        override suspend fun find(operatorPrincipalId: UUID, idempotencyKey: String): UserControlIdempotencyRecord? {
            if (operatorPrincipalId != operatorId || idempotencyKey != key) return null
            findCalls += 1
            return if (findCalls == 1) null else UserControlIdempotencyRecord(operatorId, operation, targetId, key)
        }

        override suspend fun claim(record: UserControlIdempotencyRecord): Boolean = false

        override suspend fun complete(operatorPrincipalId: UUID, idempotencyKey: String, responseJson: String) = Unit

        override suspend fun remove(operatorPrincipalId: UUID, idempotencyKey: String) = Unit
    }

    private class FakeUserControlIdempotencyCodec : UserControlIdempotencyCodec {
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
