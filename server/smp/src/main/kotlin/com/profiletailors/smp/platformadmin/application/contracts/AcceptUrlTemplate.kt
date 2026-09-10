package com.profiletailors.smp.platformadmin.application.contracts

fun interface AcceptUrlTemplate {
    fun build(rawToken: String): String
}
