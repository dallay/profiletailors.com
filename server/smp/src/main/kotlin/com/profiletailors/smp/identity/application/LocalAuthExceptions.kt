package com.profiletailors.smp.identity.application

class InvalidEmailPasswordException : RuntimeException("Invalid email or password.")

class UserAlreadyExistsException(
    email: String,
) : RuntimeException("A user with email '$email' already exists.")

class InvalidRegistrationInputException(
    message: String,
) : RuntimeException(message)

class UnverifiedEmailException(
    val email: String,
) : RuntimeException("Email verification required for '$email'.")

class InvalidVerificationTokenException : RuntimeException("Invalid or expired verification token.")
