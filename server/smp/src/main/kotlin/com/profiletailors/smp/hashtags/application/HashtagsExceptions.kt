package com.profiletailors.smp.hashtags.application

class HashtagSavedSetNotFoundException(val setId: String) : RuntimeException("Hashtag set not found: $setId")

class HashtagSetNameBlankException : RuntimeException("Hashtag set name must not be blank.")

class HashtagSetEmptyException : RuntimeException("Hashtag set must contain at least one hashtag.")
