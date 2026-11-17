import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.davidyaaw"
version = providers.gradleProperty("pluginVersion").orElse("1.1.0").get()

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // QTranslate provides the plugin API at runtime. It is compile-only so the API classes
    // never get bundled into the plugin JAR.
    compileOnly(files("libs/qtranslate-api-2.1.0.jar"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    compileOnly("com.michael-bull.kotlin-result:kotlin-result:2.3.1")
    compileOnly("com.michael-bull.kotlin-result:kotlin-result-coroutines:2.3.1")

    // Bundled: a third-party plugin cannot assume these particular libraries are on the host.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jsoup:jsoup:1.23.2")

    testImplementation(kotlin("test"))
    testImplementation(files("libs/qtranslate-api-2.1.0.jar"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.michael-bull.kotlin-result:kotlin-result:2.3.1")
    testImplementation("com.michael-bull.kotlin-result:kotlin-result-coroutines:2.3.1")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "passed", "skipped")
    }
}

// The thin JAR is only a build by-product. The installable artifact is the shadow JAR.
tasks.jar {
    archiveClassifier.set("thin")
}

// Deterministic archives make the published SHA-256 meaningful across CI and local builds.
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.shadowJar {
    archiveBaseName.set("wikimedia-reference")
    archiveClassifier.set("")
    archiveVersion.set(version.toString())

    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    manifest {
        attributes(
            "Implementation-Title" to "QTranslate Wikimedia Reference",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "davidyaaw"
        )
    }
}

// Shorthand used by the release instructions.
tasks.register("releaseJar") {
    group = "distribution"
    description = "Builds the installable fat JAR (alias for shadowJar)."
    dependsOn(tasks.shadowJar)
}
