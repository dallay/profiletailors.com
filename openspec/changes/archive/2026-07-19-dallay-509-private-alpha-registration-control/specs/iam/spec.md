# Delta for IAM

## ADDED Requirements

### Requirement: Existing Authentication Remains Available

Registration availability MUST NOT alter login, refresh, or authentication of existing users in either configuration state.

#### Scenario: Existing user authenticates while registration is disabled

- GIVEN registration is disabled and an existing user has valid credentials
- WHEN the user logs in and subsequently refreshes the session
- THEN login and refresh MUST follow their existing successful contracts

#### Scenario: Existing user authenticates while registration is enabled

- GIVEN registration is enabled and an existing user has valid credentials
- WHEN the user logs in
- THEN authentication MUST follow its existing successful contract
