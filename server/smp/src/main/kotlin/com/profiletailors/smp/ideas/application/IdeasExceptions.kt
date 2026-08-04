package com.profiletailors.smp.ideas.application

class IdeaNotFoundException(val ideaId: String) : RuntimeException("Idea not found: $ideaId")

class InvalidIdeaColumnsException(message: String) : RuntimeException(message)
