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
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

application {
    mainClass.set("com.rerun.tmdb.MainKt")
}

tasks.named<JavaExec>("run") {
    environment("RERUN_TMDB_OUT", rootProject.file("local/tmdb/rerun/tmdb-networks.json").absolutePath)
}

tasks.register<JavaExec>("publishSnapshot") {
    group = "application"
    description = "Validate and publish the last local TMDB refresh as the shared, token-free snapshot."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("--publish-from", rootProject.file("local/tmdb/rerun/tmdb-networks.json").absolutePath,
        "--out", rootProject.file("data/tmdb/rerun/tmdb-networks.json").absolutePath)
}

val verifySnapshot by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check the shared TMDB snapshot without credentials or network access."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args("--publish-from", rootProject.file("data/tmdb/rerun/tmdb-networks.json").absolutePath,
        "--out", layout.buildDirectory.file("verified-snapshot.json").get().asFile.absolutePath)
}

tasks.check { dependsOn(verifySnapshot) }
