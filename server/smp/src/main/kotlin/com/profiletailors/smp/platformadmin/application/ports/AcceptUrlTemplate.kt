package com.profiletailors.smp.platformadmin.application.ports

/**
 * Builds the fully-formed accept URL with the raw token embedded. Centralised so the URL
 * shape stays consistent across invite/resend flows and so the raw token never has to be
 * mixed into controller code.
 */
fun interface AcceptUrlTemplate {
    fun build(rawToken: String): String
}
