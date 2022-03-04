/*
 * Copyright 2022 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.generator.js

import com.squareup.kotlinpoet.*
import dev.icerock.gradle.generator.MRGenerator
import dev.icerock.gradle.utils.calculateResourcesHash
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.library.impl.KotlinLibraryLayoutImpl
import java.io.File

class JsMRGenerator(
    generatedDir: File,
    sourceSet: SourceSet,
    mrSettings: MRSettings,
    generators: List<Generator>,
    private val compilation: KotlinJsIrCompilation
) : MRGenerator(
    generatedDir = generatedDir,
    sourceSet = sourceSet,
    mrSettings = mrSettings,
    generators = generators
) {
    private val flattenClassName: String get() = mrSettings.packageName.replace(".", "")

    override val resourcesGenerationDir: File
        get() = outputDir.resolve("$flattenClassName/res")

    override fun getMRClassModifiers(): Array<KModifier> = arrayOf(KModifier.ACTUAL)

    override fun processMRClass(mrClass: TypeSpec.Builder) {
        mrClass.addProperty(
            PropertySpec.builder("contentHash", STRING, KModifier.PRIVATE)
                .initializer("%S", resourcesGenerationDir.calculateResourcesHash())
                .build()
        )
        mrClass.addProperty(
            PropertySpec.builder(
                "stringsLoader",
                ClassName("dev.icerock.moko.resources.provider", "RemoteJsStringLoader"),
            ).initializer(
                "strings.stringsLoader + plurals.stringsLoader"
            ).build()
        )
    }

    override fun apply(generationTask: Task, project: Project) {
        project.tasks.withType<Kotlin2JsCompile>().configureEach {
            it.dependsOn(generationTask)
        }
        setupKLibResources(generationTask)
        setupResources()
    }

    private fun setupKLibResources(generationTask: Task) {
        val compileTask: Kotlin2JsCompile = compilation.compileKotlinTask
        compileTask.dependsOn(generationTask)

        @Suppress("UNCHECKED_CAST")
        compileTask.doLast(CopyResourcesToKLibAction(resourcesGenerationDir) as Action<in Task>)
    }

    private fun setupResources() {
        val kotlinTarget = compilation.target as KotlinJsIrTarget

        kotlinTarget.compilations
            .map { it.compileKotlinTask }
            .forEach { compileTask ->
                @Suppress("UNCHECKED_CAST")
                compileTask.doLast(CopyResourcesToExecutableAction(resourcesGenerationDir) as Action<in Task>)
            }
    }

    class CopyResourcesToKLibAction(private val resourcesDir: File) : Action<Kotlin2JsCompile> {
        override fun execute(task: Kotlin2JsCompile) {
            val unpackedKLibDir: File = task.outputFileProperty.get()
            val defaultDir = File(unpackedKLibDir, "default")
            val resRepackDir = File(defaultDir, "resources")

            val resDir = File(resRepackDir, "moko-resources-js")
            resourcesDir.copyRecursively(
                resDir,
                overwrite = true
            )
        }
    }

    class CopyResourcesToExecutableAction(
        private val resourcesGeneratedDir: File
    ) : Action<Kotlin2JsCompile> {
        override fun execute(task: Kotlin2JsCompile) {
            val project: Project = task.project

            task.classpath.forEach { dependency ->
                println(dependency.extension)
                copyResourcesFromLibraries(
                    inputFile = dependency,
                    project = project,
                    outputDir = resourcesGeneratedDir
                )
            }

            generateWebpackConfig(project, resourcesGeneratedDir)
            generateKarmaConfig(project)
        }

        private fun generateWebpackConfig(project: Project, resourcesOutput: File) {
            val webpackDir: File = File(project.projectDir, "webpack.config.d")
            webpackDir.mkdirs()

            val webpackTestConfig: File = File(webpackDir, "moko-resources-generated.js")
            webpackTestConfig.writeText(
                """
                const path = require('path');

                const mokoResourcePath = path.resolve("${resourcesOutput.absolutePath}")

                config.module.rules.push(
                    {
                        test: /\.(.*)/,
                        include: [
                            path.resolve(mokoResourcePath)
                        ],
                        type: 'asset/resource'
                    }
                );

                config.resolve.modules.push(
                    path.resolve(mokoResourcePath)
                )
                """.trimIndent()
            )
        }

        private fun generateKarmaConfig(project: Project) {
            val webpackDir: File = File(project.projectDir, "karma.config.d")
            webpackDir.mkdirs()

            val webpackTestConfig: File = File(webpackDir, "moko-resources-generated.js")
            val pattern = "`\${output.path}/**/*`"
            webpackTestConfig.writeText(
                """
                // workaround from https://github.com/ryanclark/karma-webpack/issues/498#issuecomment-790040818

                const output = {
                  path: require("os").tmpdir() + '/' + '_karma_webpack_' + Math.floor(Math.random() * 1000000),
                }
                
                config.set(
                    {
                        webpack: {... createWebpackConfig(), output},
                        files: config.files.concat([{
                                pattern: $pattern,
                                watched: false,
                                included: false,
                            }]
                        )
                    }
                )
                """.trimIndent()
            )
        }

        private fun copyResourcesFromLibraries(
            inputFile: File,
            project: Project,
            outputDir: File
        ) {
            if (inputFile.extension != "klib") return

            project.logger.info("copy resources from $inputFile into $outputDir")
            val klibKonan = org.jetbrains.kotlin.konan.file.File(inputFile.path)
            val klib = KotlinLibraryLayoutImpl(klib = klibKonan, component = "default")
            val layout = klib.extractingToTemp

            println(layout.resourcesDir.listFiles)

            File(layout.resourcesDir.path, "moko-resources-js")
                .takeIf(File::exists)
                ?.copyRecursively(
                    target = outputDir,
                    overwrite = true
                )
        }
    }

    companion object {
        const val STRINGS_JSON_NAME = "stringsJson"
        const val PLURALS_JSON_NAME = "pluralsJson"

        const val SUPPORTED_LOCALES_PROPERTY_NAME = "supportedLocales"
        const val STRINGS_FALLBACK_FILE_URL_PROPERTY_NAME = "stringsFallbackFileUrl"
        const val PLURALS_FALLBACK_FILE_URL_PROPERTY_NAME = "stringsFallbackFileUrl"
        const val LOCALIZATION_DIR = "localization"
    }
}
