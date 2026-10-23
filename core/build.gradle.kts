plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
