# Delta for Credentials — Auth Security Hardening

## ADDED Requirements

### Requirement: Argon2id Password Hashing Interface (Design Only)

The system SHOULD extend the `PasswordHasher` interface to support algorithm identification for future password hashing migration.

The interface SHALL get a new property: `algorithm: String`
The BCrypt implementation SHALL return `algorithm = "bcrypt"`
A future Argon2id implementation SHALL return `algorithm = "argon2id"`
The migration strategy SHALL specify rehash on login if BCrypt hash is detected.

This requirement is DESIGN ONLY. Implementation is deferred.

#### Scenario: PasswordHasher interface extended for algorithm property

- GIVEN the design extends PasswordHasher interface
- WHEN the design is reviewed
- THEN the interface SHALL declare `algorithm: String` property
- AND BCrypt implementation SHALL return `algorithm = "bcrypt"`
- AND future Argon2id implementation SHALL return `algorithm = "argon2id"`
- AND migration strategy SHALL specify rehash on login for BCrypt hashes
