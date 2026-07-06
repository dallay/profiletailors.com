# Delta for Publishing

## ADDED Requirements

### Requirement: LinkedIn Completion Persists Connection and Account Atomically

The system MUST persist LinkedIn OAuth completion state atomically when finalizing a workspace social connection. The social connection write and social account write SHALL commit together or roll back together. The system MUST publish channel events only after the transaction commits successfully.

#### Scenario: LinkedIn completion commits both records

- GIVEN a valid authenticated workspace and successful LinkedIn OAuth completion data
- WHEN the backend finalizes the LinkedIn connection
- THEN the social connection MUST be persisted
- AND the social account MUST be persisted for the same workspace and provider account
- AND a channel event MAY be published after successful persistence

#### Scenario: Social account failure rolls back social connection

- GIVEN LinkedIn completion starts persisting a social connection and social account
- AND the social account persistence fails before transaction commit
- WHEN the completion handler returns an error
- THEN the social connection MUST NOT remain persisted
- AND the social account MUST NOT remain persisted
- AND no channel event MUST be published

#### Scenario: Event publishing is after transaction success

- GIVEN LinkedIn completion persistence succeeds inside a transaction
- WHEN the transaction commits successfully
- THEN the system MAY publish the channel-connected event
- AND event publication MUST NOT be required for the transaction to commit
