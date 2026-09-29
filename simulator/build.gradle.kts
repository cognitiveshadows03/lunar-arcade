plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dev.citali.sim.MainKt")
}

dependencies {
    implementation(project(":core:gamification"))
    implementation(libs.coroutines.core)
}
