package com.profiletailors.smp.platformadmin.infrastructure

import com.profiletailors.smp.platformadmin.application.BootstrapInvitationIssuer
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapCli
import com.profiletailors.smp.platformadmin.application.contracts.BootstrapConsole
import com.profiletailors.smp.platformadmin.infrastructure.cli.InteractivePlatformAdminBootstrapCli
import com.profiletailors.smp.platformadmin.infrastructure.cli.PlatformAdminBootstrapCliConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.function.Supplier

class PlatformAdminBootstrapCliComposition {
    fun register(context: AnnotationConfigApplicationContext) {
        context.register(PlatformAdminBootstrapCliConfiguration::class.java)
        context.registerBean(
            "bootstrapCli",
            BootstrapCli::class.java,
            Supplier {
                InteractivePlatformAdminBootstrapCli(
                    context.getBean(BootstrapInvitationIssuer::class.java),
                    context.getBean(BootstrapConsole::class.java),
                )
            },
        )
    }
}
