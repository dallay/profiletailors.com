package com.profiletailors.smp.privacy.application

fun interface PrivacyDataSerializer {
    fun toJson(data: Any?): String
}
