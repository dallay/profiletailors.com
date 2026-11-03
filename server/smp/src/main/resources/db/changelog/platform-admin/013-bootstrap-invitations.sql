CREATE TABLE platform_admin_bootstrap_invitations (
    singleton_id smallint PRIMARY KEY CHECK (singleton_id = 1),
    email_normalized varchar(255) NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    status varchar(16) NOT NULL,
    claimed_principal_id uuid REFERENCES principals (id),
    claimed_at timestamp with time zone,
    completed_at timestamp with time zone,
    CONSTRAINT ck_platform_admin_bootstrap_invitation_state CHECK (
        (status = 'PENDING' AND claimed_principal_id IS NULL AND claimed_at IS NULL AND completed_at IS NULL)
        OR (status = 'CLAIMED' AND claimed_principal_id IS NOT NULL AND claimed_at IS NOT NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND claimed_principal_id IS NOT NULL AND claimed_at IS NOT NULL AND completed_at IS NOT NULL)
    )
);
