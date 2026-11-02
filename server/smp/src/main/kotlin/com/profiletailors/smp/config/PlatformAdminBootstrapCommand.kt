package com.profiletailors.smp.config

import com.profiletailors.smp.identity.application.PrincipalIdentityLookup
import com.profiletailors.smp.identity.infrastructure.R2dbcPrincipalIdentityLookup
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapCli
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapConsole
import com.profiletailors.smp.platformadmin.infrastructure.PlatformAdminBootstrapCliComposition
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.r2dbc.core.DatabaseClient
import java.io.Console
import java.util.function.Supplier

internal object PlatformAdminBootstrapCommand {
    fun run() {
        val console =
            System.console() ?: throw IllegalStateException("Platform bootstrap requires an interactive console")
        AnnotationConfigApplicationContext().use { context ->
            configure(context, SystemBootstrapConsole(console))
            context.refresh()
            runBlocking { context.getBean(BootstrapCli::class.java).run() }
        }
    }

    fun configure(context: AnnotationConfigApplicationContext, console: BootstrapConsole) {
        context.registerBean("bootstrapConsole", BootstrapConsole::class.java, Supplier { console })
        context.registerBean(
            "bootstrapIdentityLookup",
            PrincipalIdentityLookup::class.java,
            Supplier { R2dbcPrincipalIdentityLookup(context.getBean(DatabaseClient::class.java)) },
        )
        PlatformAdminBootstrapCliComposition().register(context)
    }
}

private class SystemBootstrapConsole(private val console: Console) : BootstrapConsole {
    override fun readLine(prompt: String): String? = console.readLine("%s", prompt)

    override fun writeLine(value: String) {
        console.printf("%s%n", value)
        console.flush()
    }
}
