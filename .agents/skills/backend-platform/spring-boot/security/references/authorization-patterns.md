# Authorization Patterns and Strategies

## Role-Based Access Control (RBAC)

### Hierarchical Role Structure

```kotlin
@Entity
@Table(name = "roles")
data class Role(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val name: String,

    val description: String? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_hierarchy",
        joinColumns = [JoinColumn(name = "child_role_id")],
        inverseJoinColumns = [JoinColumn(name = "parent_role_id")]
    )
    val parentRoles: Set<Role> = emptySet(),

    @ManyToMany(mappedBy = "parentRoles")
    val childRoles: Set<Role> = emptySet(),

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = [JoinColumn(name = "role_id")],
        inverseJoinColumns = [JoinColumn(name = "permission_id")]
    )
    val permissions: Set<Permission> = emptySet()
) {
    fun getAllPermissions(): Set<Permission> {
        val allPermissions = permissions.toMutableSet()

        // Recursively collect permissions from parent roles
        parentRoles.forEach { parentRole ->
            allPermissions.addAll(parentRole.getAllPermissions())
        }

        return allPermissions
    }

    fun hasPermission(permissionName: String): Boolean =
        getAllPermissions().any { it.name == permissionName }
}
```

### Custom Role Hierarchy Voter

```kotlin
@Component
class RoleHierarchyVoter(
    private val roleHierarchy: RoleHierarchy
) : AccessDecisionVoter<Any> {

    override fun supports(attribute: ConfigAttribute): Boolean =
        attribute.attribute?.startsWith("ROLE_") == true

    override fun supports(clazz: Class<*>): Boolean = true

    override fun vote(
        authentication: Authentication,
        obj: Any,
        attributes: Collection<ConfigAttribute>
    ): Int {
        val authorities = roleHierarchy.getReachableGrantedAuthorities(authentication.authorities)

        for (attribute in attributes) {
            if (authorities.contains(SimpleGrantedAuthority(attribute.attribute))) {
                return ACCESS_GRANTED
            }
        }

        return ACCESS_ABSTAIN
    }
}

@Configuration
class RoleHierarchyConfig {

    @Bean
    fun roleHierarchy(): RoleHierarchy {
        val roleHierarchy = RoleHierarchyImpl()
        val hierarchy = """
            ROLE_ADMIN > ROLE_MANAGER
            ROLE_MANAGER > ROLE_USER
            ROLE_MANAGER > ROLE_SUPPORT
            ROLE_SUPPORT > ROLE_READONLY
        """.trimIndent()
        roleHierarchy.setHierarchy(hierarchy)
        return roleHierarchy
    }
}
```

### Method-Level Security with Roles

```kotlin
@Service
@PreAuthorize("hasRole('USER')")
class DocumentService {

    @PreAuthorize("hasRole('ADMIN')")
    fun deleteAllDocuments() {
        // Only administrators can delete all documents
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    fun approveDocument(documentId: Long) {
        // Admins and managers can approve documents
    }

    @PreAuthorize("hasRole('USER')")
    fun getMyDocuments(): List<Document> {
        // Regular users can view their own documents
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostAuthorize("returnObject.owner.id == authentication.principal.id or hasRole('ADMIN')")
    fun getDocumentForApproval(documentId: Long): Document {
        // Managers can view documents for approval, admins can view any
        return documentRepository.findById(documentId)
            .orElseThrow { DocumentNotFoundException(documentId) }
    }
}
```

## Permission-Based Access Control

### Permission Entity with Resource Types

```kotlin
@Entity
@Table(name = "permissions")
data class Permission(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val name: String,

    val description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val resourceType: ResourceType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val action: ActionType,

    // Permission templates for dynamic permissions
    val template: String? = null,

    // Conditions for conditional permissions
    @Lob
    val conditions: String? = null
)

enum class ResourceType(
    val code: String,
    val description: String
) {
    USER("user", "User Management"),
    DOCUMENT("document", "Document Management"),
    ORDER("order", "Order Processing"),
    PRODUCT("product", "Product Catalog"),
    INVOICE("invoice", "Invoice Management"),
    REPORT("report", "Reporting"),
    SYSTEM("system", "System Administration")
}

enum class ActionType(val code: String) {
    CREATE("create"),
    READ("read"),
    UPDATE("update"),
    DELETE("delete"),
    APPROVE("approve"),
    REJECT("reject"),
    EXPORT("export"),
    IMPORT("import")
}
```

### Custom Permission Evaluator

```kotlin
@Component("permissionEvaluator")
class CustomPermissionEvaluator(
    private val permissionService: PermissionService
) : PermissionEvaluator {

    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: Any?
    ): Boolean {
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }

        val user = authentication.principal as User
        val permissionName = permission.toString()

        // Check direct permissions
        if (user.hasPermission(permissionName)) {
            return true
        }

        // Check resource-specific permissions
        if (targetDomainObject != null) {
            return checkResourcePermission(user, targetDomainObject, permissionName)
        }

        return false
    }

    override fun hasPermission(
        authentication: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        permission: Any?
    ): Boolean {
        if (authentication == null || !authentication.isAuthenticated) {
            return false
        }

        val user = authentication.principal as User
        val permissionName = permission.toString()

        return permissionService.hasResourcePermission(user, targetId, targetType, permissionName)
    }

    private fun checkResourcePermission(user: User, resource: Any, permission: String): Boolean {
        // Extract resource information
        val resourceType = extractResourceType(resource)
        val resourceId = extractResourceId(resource)

        // Check ownership
        if (isOwner(user, resource) && hasOwnershipPermission(permission)) {
            return true
        }

        // Check department/organizational permissions
        if (isInSameDepartment(user, resource) && hasDepartmentPermission(permission)) {
            return true
        }

        // Check global permissions
        return permissionService.hasGlobalPermission(user, resourceType, permission)
    }

    private fun hasOwnershipPermission(permission: String): Boolean =
        permission.endsWith("_OWN") || permission.endsWith("_MY")

    private fun hasDepartmentPermission(permission: String): Boolean =
        permission.endsWith("_DEPT") || permission.endsWith("_ORG")
}
```

### Dynamic Permission Builder

```kotlin
@Service
class PermissionBuilderService {

    fun buildPermissions(user: User): Set<String> {
        val permissions = mutableSetOf<String>()

        // Role-based permissions
        user.roles.forEach { role ->
            permissions.add("ROLE_${role.name}")
            role.permissions.forEach { permission ->
                permissions.add(permission.name)
            }
        }

        // User-specific permissions
        user.userPermissions
            .filter { isPermissionValid(it) }
            .forEach { userPermission ->
                permissions.add(buildPermissionString(userPermission))
            }

        // Dynamic permissions based on user attributes
        addDynamicPermissions(user, permissions)

        return permissions
    }

    private fun addDynamicPermissions(user: User, permissions: MutableSet<String>) {
        // Department-based permissions
        user.department?.let { dept ->
            permissions.add("DEPT_${dept.code}_READ")
        }

        // Location-based permissions
        user.location?.let { location ->
            permissions.add("LOCATION_${location.code}_ACCESS")
        }

        // Project-based permissions
        user.projectMemberships.forEach { membership ->
            permissions.add("PROJECT_${membership.project.id}_MEMBER")
            if (membership.role == ProjectRole.MANAGER) {
                permissions.add("PROJECT_${membership.project.id}_MANAGER")
            }
        }
    }

    private fun buildPermissionString(userPermission: UserPermission): String =
        "${userPermission.resourceType}_${userPermission.action}_${userPermission.scope}"
}
```

## Attribute-Based Access Control (ABAC)

### Policy-Based Authorization

```kotlin
@Component
class PolicyBasedAccessDecisionManager(
    private val policyRepository: PolicyRepository,
    private val evaluationService: PolicyEvaluationService
) : AccessDecisionManager {

    override fun decide(
        authentication: Authentication,
        obj: Any,
        configAttributes: Collection<ConfigAttribute>
    ) {
        if (configAttributes.isEmpty()) {
            return
        }

        for (attribute in configAttributes) {
            if (supports(attribute)) {
                val policyName = attribute.attribute
                val policy = policyRepository.findByName(policyName)
                    .orElseThrow { PolicyNotFoundException(policyName) }

                if (!evaluationService.evaluate(policy, authentication, obj)) {
                    throw AccessDeniedException("Access denied by policy: $policyName")
                }
            }
        }
    }

    override fun supports(attribute: ConfigAttribute): Boolean =
        attribute.attribute?.startsWith("POLICY_") == true

    override fun supports(clazz: Class<*>): Boolean = true
}

@Service
class PolicyEvaluationService {

    fun evaluate(policy: Policy, authentication: Authentication, resource: Any): Boolean {
        // Build evaluation context
        val context = EvaluationContext(
            subject = extractSubjectInfo(authentication),
            resource = extractResourceInfo(resource),
            environment = extractEnvironmentInfo()
        )

        // Evaluate policy rules
        return policy.rules.all { rule -> evaluateRule(rule, context) }
    }

    private fun evaluateRule(rule: PolicyRule, context: EvaluationContext): Boolean {
        // Implement rule evaluation logic using SPEL or custom evaluator
        val spelContext = StandardEvaluationContext(context)
        val parser = SpelExpressionParser()
        val expression = parser.parseExpression(rule.condition)

        return expression.getValue(spelContext, Boolean::class.java) ?: false
    }
}
```

### ABAC Policy Definitions

```kotlin
@Entity
@Table(name = "policies")
data class Policy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val name: String,

    val description: String? = null,

    @Enumerated(EnumType.STRING)
    val effect: PolicyEffect, // PERMIT or DENY

    @OneToMany(mappedBy = "policy", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("priority ASC")
    val rules: List<PolicyRule> = emptyList(),

    @Column(columnDefinition = "TEXT")
    val targetCondition: String? = null // SPEL expression for target matching
) {
    enum class PolicyEffect {
        PERMIT, DENY
    }
}

@Entity
@Table(name = "policy_rules")
data class PolicyRule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    val policy: Policy,

    val description: String? = null,

    @Column(columnDefinition = "TEXT")
    val condition: String, // SPEL expression

    val priority: Int,

    @Enumerated(EnumType.STRING)
    val type: RuleType
) {
    enum class RuleType {
        SUBJECT, RESOURCE, ENVIRONMENT, COMPOSITE
    }
}
```

## Time-Based Access Control

### Time-Restricted Permissions

```kotlin
@Component
class TimeBasedPermissionEvaluator {

    fun hasTimeBasedPermission(
        authentication: Authentication,
        permission: Any,
        accessTime: Instant
    ): Boolean {
        val user = authentication.principal as User

        // Check business hours
        if (!isWithinBusinessHours(accessTime)) {
            return user.hasPermission("AFTER_HOURS_ACCESS")
        }

        // Check time-based restrictions
        return user.timeRestrictions.none { restriction ->
            isRestricted(restriction, accessTime)
        }
    }

    private fun isWithinBusinessHours(accessTime: Instant): Boolean {
        val zdt = accessTime.atZone(ZoneId.systemDefault())
        val dayOfWeek = zdt.dayOfWeek
        val hour = zdt.hour

        // Monday to Friday, 9 AM to 6 PM
        return dayOfWeek != DayOfWeek.SATURDAY &&
                dayOfWeek != DayOfWeek.SUNDAY &&
                hour in 9..17
    }
}

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@timeBasedPermissionEvaluator.hasTimeBasedPermission(authentication, 'READ', T(java.time.Instant).now())")
annotation class TimeRestrictedAccess(
    val value: String = "READ"
)
```

### Expiration-Based Access

```kotlin
@Entity
@Table(name = "access_grants")
data class AccessGrant(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val user: User,

    @Column(nullable = false)
    val resource: String,

    @Column(nullable = false)
    val permission: String,

    @Column(nullable = false)
    val grantedAt: Instant,

    @Column(nullable = false)
    val expiresAt: Instant,

    val grantedBy: String? = null,

    @Column(columnDefinition = "TEXT")
    val reason: String? = null
) {
    fun isValid(): Boolean = Instant.now().isBefore(expiresAt)
}

@Service
class AccessGrantService(
    private val accessGrantRepository: AccessGrantRepository
) {

    @Transactional
    fun grantTemporaryAccess(
        user: User,
        resource: String,
        permission: String,
        duration: Duration,
        grantedBy: String,
        reason: String
    ): AccessGrant {
        val grant = AccessGrant(
            user = user,
            resource = resource,
            permission = permission,
            grantedAt = Instant.now(),
            expiresAt = Instant.now().plus(duration),
            grantedBy = grantedBy,
            reason = reason
        )

        return accessGrantRepository.save(grant)
    }

    fun hasTemporaryAccess(user: User, resource: String, permission: String): Boolean =
        accessGrantRepository
            .findByUserAndResourceAndPermissionAndExpiresAtAfter(
                user, resource, permission, Instant.now()
            )
            .isPresent
}
```

## Location-Based Access Control

### IP Address Restrictions

```kotlin
@Component
class LocationBasedAccessControl(
    private val ipRangeRepository: IpRangeRepository
) {

    fun isAccessAllowedFromIp(authentication: Authentication, ipAddress: String): Boolean {
        val user = authentication.principal as User

        // Check if user has location restrictions
        if (!user.hasLocationRestrictions()) {
            return true
        }

        // Check IP against allowed ranges
        val allowedRanges = ipRangeRepository.findByUser(user)
        return allowedRanges.any { range -> isInRange(ipAddress, range) }
    }

    private fun isInRange(ipAddress: String, ipRange: IpRange): Boolean {
        return try {
            val address = InetAddress.getByName(ipAddress)
            val networkAddress = InetAddress.getByName(ipRange.networkAddress)
            val prefixLength = ipRange.prefixLength

            val addressBytes = address.address
            val networkBytes = networkAddress.address

            val fullPrefix = prefixLength / 8
            val partialPrefix = prefixLength % 8

            for (i in 0 until fullPrefix) {
                if (addressBytes[i] != networkBytes[i]) {
                    return false
                }
            }

            if (partialPrefix > 0) {
                val mask = (0xFF shl (8 - partialPrefix)).toByte()
                if ((addressBytes[fullPrefix] and mask) != (networkBytes[fullPrefix] and mask)) {
                    return false
                }
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}
```

## Organizational Access Control

### Department-Based Security

```kotlin
@Entity
@Table(name = "departments")
data class Department(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val code: String,

    val name: String,

    val description: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    val parentDepartment: Department? = null,

    @OneToMany(mappedBy = "parentDepartment")
    val childDepartments: List<Department> = emptyList(),

    val level: Int
) {
    // Get all child departments recursively
    fun getAllChildDepartments(): List<Department> {
        val allChildren = mutableListOf<Department>()
        for (child in childDepartments) {
            allChildren.add(child)
            allChildren.addAll(child.getAllChildDepartments())
        }
        return allChildren
    }
}

@Service
class DepartmentSecurityService {

    fun canAccessDepartmentData(user: User, targetDepartment: Department): Boolean {
        // Users can access their own department
        if (user.department == targetDepartment) {
            return true
        }

        // Check parent department access
        if (canAccessParentDepartment(user, targetDepartment)) {
            return true
        }

        // Check child department access
        if (canAccessChildDepartments(user, targetDepartment)) {
            return true
        }

        return false
    }

    private fun canAccessParentDepartment(user: User, department: Department): Boolean {
        var current = department.parentDepartment
        while (current != null) {
            if (current == user.department && user.hasPermission("DEPT_CHILDREN_ACCESS")) {
                return true
            }
            current = current.parentDepartment
        }
        return false
    }

    private fun canAccessChildDepartments(user: User, department: Department): Boolean =
        user.department.getAllChildDepartments().contains(department) &&
                user.hasPermission("DEPT_PARENT_ACCESS")
}
```
