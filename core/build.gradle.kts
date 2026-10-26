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

// Fresh local lists override the public snapshot. Fresh clones bundle the snapshot without a token.
sourceSets {
    main {
        resources.srcDir(providers.provider {
            val local = rootProject.file("local/tmdb")
            if (local.resolve("rerun/tmdb-networks.json").isFile) local
            else rootProject.file("data/tmdb")
        })
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
