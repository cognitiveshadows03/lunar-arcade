// Pure Kotlin/JVM module. ZERO main-source dependencies by design: no Android,
// no coroutines, no network. Tests may use junit + coroutines (runBlocking).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.core)
    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    useJUnit()
}
