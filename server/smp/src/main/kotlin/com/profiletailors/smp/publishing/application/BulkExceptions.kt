@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package com.profiletailors.smp.publishing.application

class BulkWorkspaceMismatchException(message: String = "Workspace path does not match the authenticated workspace.") :
    RuntimeException(message)
