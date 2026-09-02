package com.profiletailors.smp.platformadmin.application

import com.profiletailors.common.domain.AggregateRoot
import com.profiletailors.common.domain.ValueObject
import com.profiletailors.smp.platformadmin.application.contracts.InvitationRepository
import com.profiletailors.smp.platformadmin.domain.Invitation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.UUID

@Tag("ddd-conformance")
internal class InvitationSecurityBoundaryTest {

    @Test
    fun invitationAggregateDoesNotExposeRawTokenAcceptUrlOrDeliveryFields() {
        val declaredFieldNames = Invitation::class.java.declaredFields
            .map { it.name }
            .toSet()

        assertThat(declaredFieldNames)
            .withFailMessage(
                "First-class Invitation MUST NOT expose raw token, URL, or delivery fields. " +
                    "Declared: $declaredFieldNames",
            )
            .doesNotContain(
                "rawToken",
                "token",
                "rawTokenHash",
                "acceptUrl",
                "url",
                "deliveryStatus",
                "lastDeliveryAttemptAt",
                "deliveryAttemptCount",
                "notificationStatus",
                "notificationFailure",
            )
    }

    @Test
    fun invitationAggregateIsAnnotatedAsAggregateRoot() {
        assertThat(Invitation::class.java.isAnnotationPresent(AggregateRoot::class.java))
            .withFailMessage("Invitation MUST be marked @AggregateRoot")
            .isTrue()
    }

    @Test
    fun canonicalInvitationRepositoryContractExcludesBearerOrDeliveryBehaviour() {
        val methodNames: Set<String> = InvitationRepository::class.java.declaredMethods
            .map { it.name }
            .toSet()

        assertThat(methodNames)
            .withFailMessage(
                "InvitationRepository MUST NOT expose token generation, hashing, URL, or delivery methods. " +
                    "Declared: $methodNames",
            )
            .doesNotContain(
                "generateToken",
                "hashToken",
                "matchToken",
                "candidateKey",
                "buildAcceptUrl",
                "acceptUrl",
                "sendNotification",
                "recordDelivery",
                "deliveryStatus",
            )

        val noneAcceptsOrReturnsByteOrCharArray = InvitationRepository::class.java.declaredMethods
            .none { method ->
                method.parameterTypes.any { it == ByteArray::class.java || it == CharArray::class.java } ||
                    method.returnType == ByteArray::class.java ||
                    method.returnType == CharArray::class.java
            }
        assertThat(noneAcceptsOrReturnsByteOrCharArray)
            .withFailMessage("InvitationRepository contract MUST NOT accept or return raw bearer values")
            .isTrue()
    }

    @Test
    fun invitationRepositoryContractTreatsCandidateKeyAsOpaqueString() {
        val findByCandidateKey: Method = InvitationRepository::class.java.declaredMethods
            .singleOrNull { it.name == "findByCandidateKeyForUpdate" }
            ?: error("findByCandidateKeyForUpdate must exist on InvitationRepository")

        val parameters = findByCandidateKey.parameters
        val candidateKeyParam = parameters.firstOrNull { it.type == String::class.java }
            ?: error("findByCandidateKeyForUpdate must accept a String candidate key")
        assertThat(candidateKeyParam.name)
            .withFailMessage("Candidate key parameter must keep its declared name, not be renamed to a raw-token alias")
            .isEqualTo("candidateKey")
        assertThat(parameters)
            .withFailMessage(
                "findByCandidateKeyForUpdate must not declare a parameter named like a raw token or bearer value",
            )
            .noneMatch { p ->
                val name = p.name ?: ""
                name == "rawToken" || name == "token" || name == "raw" || name == "bearer"
            }
    }

    @Test
    fun platformAdminDomainAndApplicationPackagesDoNotDefineANewTokenSubsystem() {
        val packages = setOf(
            "com.profiletailors.smp.platformadmin.domain",
            "com.profiletailors.smp.platformadmin.application",
        )
        val loadedClasses = ClasspathScanner.scan(packages)
        val legacyNames = legacyTokenTypeNames()

        val newTokenTypes: List<Class<*>> = loadedClasses.filter { klass ->
            klass.name !in legacyNames &&
                (
                    klass.simpleName.endsWith("TokenGenerator") ||
                        klass.simpleName.endsWith("TokenHasher") ||
                        klass.simpleName.endsWith("TokenHasherImpl") ||
                        klass.simpleName.endsWith("UrlBuilder") ||
                        klass.simpleName.endsWith("AcceptUrlTemplate")
                    )
        }

        assertThat(newTokenTypes)
            .withFailMessage(
                "DALLAY-564 MUST NOT introduce a second token subsystem or URL builder. " +
                    "Offending types: ${newTokenTypes.map { it.name }}",
            )
            .isEmpty()
    }

    @Test
    fun platformAdminDomainAndApplicationPackagesDoNotReferenceNotification() {
        val packages = setOf(
            "com.profiletailors.smp.platformadmin.domain",
            "com.profiletailors.smp.platformadmin.application",
        )
        val loadedClasses = ClasspathScanner.scan(packages)

        val referencingNotification: List<Class<*>> = loadedClasses.filter { klass ->
            val fields = klass.declaredFields
            val methods = klass.declaredMethods
            val anyField = fields.any { typeMentionsNotification(it.type.name) }
            val anyMethod = methods.any { method ->
                method.parameterTypes.any { typeMentionsNotification(it.name) } ||
                    typeMentionsNotification(method.returnType.name)
            }
            anyField || anyMethod
        }

        assertThat(referencingNotification)
            .withFailMessage(
                "DALLAY-564 MUST NOT introduce Notification dependencies inside the first-class " +
                    "Invitation boundary. Offending types: ${referencingNotification.map { it.name }}",
            )
            .isEmpty()
    }

    @Test
    fun invitationIdentityIsUuidBackedValueObject() {
        val idClass = Class.forName("com.profiletailors.smp.platformadmin.domain.InvitationId")
        assertThat(idClass.isAnnotationPresent(ValueObject::class.java))
            .withFailMessage("InvitationId MUST be marked @ValueObject")
            .isTrue()
        val uuidField: Field = idClass.declaredFields.first { !Modifier.isStatic(it.modifiers) }
        assertThat(UUID::class.java.isAssignableFrom(uuidField.type))
            .withFailMessage("InvitationId MUST carry a UUID-typed identifier field")
            .isTrue()
    }

    private fun typeMentionsNotification(typeName: String): Boolean =
        typeName.contains("Notification") && !typeName.endsWith(".Notification")

    private fun legacyTokenTypeNames(): Set<String> = setOf(
        "com.profiletailors.smp.platformadmin.application.contracts.TokenHasher",
        "com.profiletailors.smp.platformadmin.application.contracts.InvitationTokenCandidateKey",
        "com.profiletailors.smp.platformadmin.application.contracts.AcceptUrlTemplate",
        "com.profiletailors.smp.platformadmin.infrastructure.BCryptTokenHasher",
    )
}
