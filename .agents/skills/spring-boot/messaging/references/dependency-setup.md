# Event-Driven Architecture Dependencies

Versions for the artifacts below live in `gradle/libs.versions.toml`; the snippets
use placeholders for the obvious fields.

## Maven Dependencies

```xml
<dependencies>
    <!-- Spring Boot WebFlux (reactive HTTP) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring Data R2DBC (reactive persistence) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-r2dbc</artifactId>
    </dependency>

    <!-- Kafka for distributed messaging -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Spring Cloud Stream -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-stream</artifactId>
        <version>${spring-cloud-stream.version}</version>
    </dependency>

    <!-- Reactive testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers for integration testing -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Gradle Dependencies

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-r2dbc'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'org.springframework.cloud:spring-cloud-stream'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:testcontainers'
}
```

## Version Selection

- Spring Boot 4.x pairs with Spring Kafka 3.x and Spring Cloud Stream 4.x.
- Always confirm compatible versions at the
  [Spring Cloud Release Train](https://spring.io/projects/spring-cloud).
- The SMP backend sources versions from `gradle/libs.versions.toml`; never hardcode
  them in this document.
