package com.profiletailors.leadcapture.waitlist.domain

class WaitlistNotFoundException(key: WaitlistKey) : RuntimeException("Waitlist with key '$key' not found")

class WaitlistClosedException(key: WaitlistKey, status: WaitlistStatus) :
    RuntimeException("Waitlist '$key' is not accepting entries (status=$status)")
