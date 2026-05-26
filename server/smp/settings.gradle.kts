rootProject.name = "smp"

include(":shared-common")
project(":shared-common").projectDir = file("../../shared/common")

include(":shared-spring-boot-common")
project(":shared-spring-boot-common").projectDir = file("../../shared/spring-boot-common")

include(":shared-storage")
project(":shared-storage").projectDir = file("../../shared/storage")
