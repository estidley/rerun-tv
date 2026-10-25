// Build-time tool: downloads what aired on each real network from TMDB and writes the
// reference the app bundles. Needs TMDB_TOKEN in the environment; the token is never stored.
// Usage: .\gradlew.bat :tmdb:run   (or pass -TmdbToken to scripts\install.ps1)
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("com.rerun.tmdb.MainKt")
}

tasks.named<JavaExec>("run") {
    environment("RERUN_TMDB_OUT", rootProject.file("local/tmdb/rerun/tmdb-networks.json").absolutePath)
}
