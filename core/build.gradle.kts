plugins {
    alias(libs.plugins.kotlin.jvm)
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

// Network lists written by `:tmdb:run` (gitignored). Without them channels use Plex's network field.
sourceSets {
    main {
        resources.srcDir(rootProject.file("local/tmdb"))
    }
}

dependencies {
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Keep tests independent of a locally generated TMDB reference.
    systemProperty("rerun.tmdb", "off")
}
