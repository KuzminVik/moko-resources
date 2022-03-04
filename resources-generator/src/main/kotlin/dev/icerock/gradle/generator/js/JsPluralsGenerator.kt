/*
 * Copyright 2022 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.gradle.generator.js

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import dev.icerock.gradle.generator.KeyType
import dev.icerock.gradle.generator.LanguageType
import dev.icerock.gradle.generator.NOPObjectBodyExtendable
import dev.icerock.gradle.generator.ObjectBodyExtendable
import dev.icerock.gradle.generator.PluralMap
import dev.icerock.gradle.generator.PluralsGenerator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gradle.api.file.FileTree
import java.io.File
import java.lang.IllegalArgumentException
import java.text.MessageFormat
import java.util.Locale

class JsPluralsGenerator(
    pluralsFileTree: FileTree,
    mrClassPackage: String
) : PluralsGenerator(pluralsFileTree), ObjectBodyExtendable by NOPObjectBodyExtendable() {

    private val flattenClassPackage = mrClassPackage.replace(".", "")

    override fun getClassModifiers(): Array<KModifier> = arrayOf(KModifier.ACTUAL)

    override fun getPropertyModifiers(): Array<KModifier> = arrayOf(KModifier.ACTUAL)

    override fun getPropertyInitializer(key: String): CodeBlock {
        return CodeBlock.of("PluralsResource(key = %S)", key)
    }

    override fun beforeGenerateResources(
        objectBuilder: TypeSpec.Builder,
        languageMap: Map<LanguageType, Map<KeyType, PluralMap>>
    ) {
        objectBuilder.generateFallbackAndSupportedLanguageProperties(
            languages = languageMap.keys.toList(),
            folder = JsMRGenerator.LOCALIZATION_DIR,
            fallbackFilePropertyName = JsMRGenerator.PLURALS_FALLBACK_FILE_URL_PROPERTY_NAME,
            fallbackFile = "${flattenClassPackage}_${JsMRGenerator.PLURALS_JSON_NAME}.json",
            supportedLocalesPropertyName = JsMRGenerator.SUPPORTED_LOCALES_PROPERTY_NAME,
            getFileNameForLanguage = { language -> "${flattenClassPackage}_${JsMRGenerator.PLURALS_JSON_NAME}_$language.json" }
        )
    }

    override fun generateResources(
        resourcesGenerationDir: File,
        language: String?,
        strings: Map<KeyType, PluralMap>
    ) {
        val fileDirName = when (language) {
            null -> "${flattenClassPackage}_${JsMRGenerator.PLURALS_JSON_NAME}"
            else -> "${flattenClassPackage}_${JsMRGenerator.PLURALS_JSON_NAME}_$language"
        }

        val localizationDir = File(resourcesGenerationDir, JsMRGenerator.LOCALIZATION_DIR).apply {
            mkdirs()
        }

        val pluralsFile = File(localizationDir, "$fileDirName.json")

        val content = buildJsonObject {
            strings.forEach { (key, pluralMap) ->
                val messageFormatString = StringBuilder().apply {
                    append("{ PLURAL, plural, ")
                    pluralMap.forEach { (pluralKey, pluralString) ->
                        //Zero isn't allowed in english (which is default for base), but we support it through =0
                        val actPluralKey = when (pluralKey) {
                            "zero" -> "=0"
                            "two" -> "=2"
                            else -> pluralKey
                        }

                        append(actPluralKey)
                        append(" ")
                        append("{")
                        append(pluralString.replaceAndroidParams())
                        append("} ")
                    }

                    append("}")
                }.toString()

                put(key, messageFormatString)
            }
        }.toString()

        pluralsFile.writeText(content)
    }

    //For JS we can use =0 and zero
    override fun processLanguagePlurals(languagePlurals: Map<KeyType, PluralMap>): Map<KeyType, PluralMap> =
        languagePlurals
}
