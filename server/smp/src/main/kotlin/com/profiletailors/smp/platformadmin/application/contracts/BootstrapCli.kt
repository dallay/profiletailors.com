package com.profiletailors.smp.platformadmin.application.contracts

fun interface BootstrapCli {
    suspend fun run()
}

interface BootstrapConsole {
    fun readLine(prompt: String): String?
    fun writeLine(value: String)
}
