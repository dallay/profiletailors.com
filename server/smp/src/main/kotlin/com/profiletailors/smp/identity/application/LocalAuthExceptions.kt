package com.profiletailors.smp.identity.application

class InvalidEmailPasswordException : RuntimeException("Invalid email or password.")

class UserAlreadyExistsException(
    email: String,
) : RuntimeException("A user with email '$email' already exists.")

class InvalidRegistrationInputException(
    message: String,
) : RuntimeException(message)
