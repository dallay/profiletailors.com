package com.profiletailors.smp

import com.profiletailors.common.domain.bus.event.EventConsumer
import com.profiletailors.smp.authorization.infrastructure.http.AuthorizationProblemDetailsHandler
import com.profiletailors.smp.credentials.infrastructure.R2dbcApiKeyCredentialReplacementGateway
import com.profiletailors.smp.publishing.infrastructure.credentials.R2dbcLinkedInCredentialGateway
import com.profiletailors.smp.test.TestStorageConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import

@SpringBootTest(
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///smp_app_test?options=MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.r2dbc.username=sa",
        "spring.r2dbc.password=",
        "spring.liquibase.enabled=true",
        "spring.liquibase.url=jdbc:h2:mem:smp_app_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.liquibase.user=sa",
        "spring.liquibase.password=",
        "spring.main.allow-bean-definition-overriding=true",
        "management.endpoint.health.group.readiness.include=readinessState",
        "management.endpoint.health.group.liveness.include=livenessState",
        // Storage — local filesystem for tests (StorageAutoConfiguration)
        "platform.storage.default=local",
        "platform.storage.providers.local.type=local",
        "platform.storage.providers.local.base-path=/tmp/smp-test-storage",
    ]
)
@Import(TestStorageConfiguration::class)
class SmpApplicationTests {

	@Autowired
	private lateinit var applicationContext: ApplicationContext

	@Test
	fun contextLoads() {
	}

	/**
	 * Spec scenario "No `EventConsumer` is registered more than once after `EventConfiguration` is
	 * loaded" and "New test asserts `EventConsumer` registration uniqueness": after the smp
	 * context is loaded (with `EventConfiguration` active), every `EventConsumer` bean must
	 * appear exactly once.
	 */
	@Test
	fun eventConsumersAreRegisteredUniquely() {
		val consumers = applicationContext.getBeansOfType(EventConsumer::class.java)

		consumers.entries
			.groupBy { it.value.javaClass.kotlin }
			.forEach { (consumerClass, entries) ->
				check(entries.size == 1) {
					"EventConsumer of type $consumerClass is registered ${entries.size} times: " +
						entries.joinToString { it.key }
				}
			}

		val identityHashes = consumers.entries.groupBy { System.identityHashCode(it.value) }
		identityHashes.forEach { (hash, entries) ->
			check(entries.size == 1) {
				"Multiple EventConsumer bean names map to the same instance " +
					"(identityHashCode=$hash): ${entries.joinToString { it.key }}"
			}
		}
	}

	/**
	 * Spec scenario "New test asserts one bean per stereotype is present": asserts that at
	 * least one bean of each stereotype the smp module uses is present in the context. The
	 * custom-`@Service` handler is resolved via FQCN because
	 * `WorkspaceAuthorizationService` is declared `internal` (per ADR 5.4 in `design.md`).
	 */
	@Test
	fun loadsAllExpectedBeanStereotypes() {
		applicationContext.getBean(
			Class.forName("com.profiletailors.smp.authorization.application.WorkspaceAuthorizationService"),
		)

		applicationContext.getBean(R2dbcApiKeyCredentialReplacementGateway::class.java)

		applicationContext.getBean(R2dbcLinkedInCredentialGateway::class.java)

		applicationContext.getBean(AuthorizationProblemDetailsHandler::class.java)
	}

}
