package com.profiletailors.leadcapture.waitlist.application

data class JoinResult private constructor(val distinction: Distinction) {

    enum class Distinction { JOINED_NEW, ALREADY_JOINED }

    override fun toString(): String = "Accepted"

    companion object {
        val JOINED_NEW: JoinResult = JoinResult(Distinction.JOINED_NEW)
        val ALREADY_JOINED: JoinResult = JoinResult(Distinction.ALREADY_JOINED)
    }
}
