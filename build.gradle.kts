/*
 * Copyright 2019 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("io.gitlab.arturbosch.detekt") version("1.15.0") apply(false)
}

buildscript {
    repositories {
        mavenCentral()
        google()

        maven { url = uri("https://dl.bintray.com/kotlin/kotlin") }
        maven { url = uri("https://kotlin.bintray.com/kotlinx") }
        maven { url = uri("https://plugins.gradle.org/m2/") }
        maven { url = uri("https://dl.bintray.com/icerockdev/plugins") }

        jcenter()
    }
    dependencies {
        classpath("dev.icerock.moko:resources-generator") // composite build used
        classpath("gradle:resources-deps:1")
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()

        maven { url = uri("https://kotlin.bintray.com/kotlin") }
        maven { url = uri("https://kotlin.bintray.com/kotlinx") }
        maven { url = uri("https://dl.bintray.com/icerockdev/moko") }

        jcenter()
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        input.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/iosMain/kotlin")
    }

    dependencies {
        "detektPlugins"(Deps.Libs.Jvm.detektFormatting)
    }

    plugins.withId(Deps.Plugins.androidLibrary.id) {
        configure<com.android.build.gradle.LibraryExtension> {
            compileSdkVersion(Deps.Android.compileSdk)

            defaultConfig {
                minSdkVersion(Deps.Android.minSdk)
                targetSdkVersion(Deps.Android.targetSdk)
            }
        }
    }
}

tasks.register("clean", Delete::class).configure {
    group = "build"
    delete(rootProject.buildDir)
}
