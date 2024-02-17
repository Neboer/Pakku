@file:Suppress("UNUSED_VARIABLE", "KotlinRedundantDiagnosticSuppress")

import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.InputStream

plugins {
    kotlin("multiplatform") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin
    id("io.ktor.plugin") version libs.versions.ktor
}

group = "teksturepako.pakku"
version = "0.0.8"

repositories {
    mavenCentral()
}

private val ktorVersion: String = libs.versions.ktor.get()

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "teksturepako.pakku.main"
            }
        }
    }

    jvm()

    sourceSets {
        // -- COMMON --

        val commonMain by getting {
            dependencies {
                // Kotlin
                implementation("org.jetbrains.kotlin:kotlin-stdlib")

                // Ktor
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

                // URL Encoding
                implementation("net.thauvin.erik.urlencoder:urlencoder-lib:1.4.0")

                // File I/O
                implementation("com.soywiz.korlibs.korio:korio:4.0.10")

                // CLI
                implementation("com.github.ajalt.clikt:clikt:4.2.1")

                // Cache
                implementation("io.github.reactivecircus.cache4k:cache4k:0.12.0")

                // Logging
                implementation("org.slf4j:slf4j-api:2.0.7")
                implementation("org.slf4j:slf4j-simple:2.0.7")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        // -- NATIVE --

        val nativeMain by getting {
            dependencies {
                // Ktor
                implementation("io.ktor:ktor-client-curl:$ktorVersion")
            }
        }

        val nativeTest by getting

        // -- JVM --

        val jvmMain by getting {
            dependencies {
                // Ktor
                implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            }
        }

        val jvmTest by getting
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks.withType<Jar> {
    doFirst {
        archiveFileName.set("pakku.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val main by kotlin.jvm().compilations.getting
        manifest {
            attributes(
                "Main-Class" to "teksturepako.pakku.MainKt",
            )
        }
        from(main.runtimeDependencyFiles.files.filter { it.name.endsWith("jar") }.map { zipTree(it) })
    }
}

// -- VERSION --

private val sourceFile = File("$rootDir/resources/teksturepako/pakku/TemplateVersion.kt")
private val destFile = File("$rootDir/src/commonMain/kotlin/teksturepako/pakku/Version.kt")

tasks.register("generateVersion") {
    group = "build"

    inputs.file(sourceFile)
    outputs.file(destFile)

    doFirst {
        generateVersion()
    }
}

tasks.named("compileKotlinNative") {
    dependsOn("generateVersion")
}

fun generateVersion() {
    val inputStream: InputStream = sourceFile.inputStream()

    destFile.printWriter().use { out ->
        inputStream.bufferedReader().forEachLine { inputLine ->
            val newLine = inputLine.replace("__VERSION", version.toString())
            out.println(newLine)
        }
    }

    inputStream.close()
}