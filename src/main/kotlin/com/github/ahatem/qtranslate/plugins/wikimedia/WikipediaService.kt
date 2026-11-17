// SPDX-License-Identifier: MPL-2.0
// Derived from QTranslate (https://github.com/ahatem/QTranslate).
package com.github.ahatem.qtranslate.plugins.wikimedia


import com.github.ahatem.qtranslate.api.dictionary.Definition
import com.github.ahatem.qtranslate.api.dictionary.DictionaryEntry
import com.github.ahatem.qtranslate.api.dictionary.DictionaryRequest
import com.github.ahatem.qtranslate.api.dictionary.DictionaryResponse
import com.github.ahatem.qtranslate.api.dictionary.LanguageSwitchDictionary
import com.github.ahatem.qtranslate.api.language.LanguageCode
import com.github.ahatem.qtranslate.api.plugin.ServiceError
import com.github.ahatem.qtranslate.api.plugin.SupportedLanguages
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold

/**
 * Wikipedia as a dictionary: the intro of the article for a word.
 *
 * The Action API's `titles` query is used rather than the REST search endpoint because it
 * normalises the title and follows redirects, so an inflected form such as "слова" reaches
 * "Слово" and "привет" reaches "Приветствие". Only when that finds nothing does a search run,
 * and a search hit is accepted only when its title is the same word or a close form of it;
 * otherwise a query like "бежать" would confidently return a film called "Некуда бежать".
 */
internal class WikipediaService(private val client: WikimediaClient) : LanguageSwitchDictionary {
    override val key = "wikimedia-wikipedia"
    override val name = "Wikipedia"
    override val iconPath = "assets/wikipedia.svg"
    override val version = "1.1.0"
    override val supportedLanguages = SupportedLanguages.Specific(WikimediaLanguages.supported)

    /**
     * Russian first: the person this switch exists for reads Russian, and a Russian word should
     * open the Russian article even when the surrounding translation pair is English.
     */
    override val switchLanguages: List<LanguageCode> =
        listOf(LanguageCode.RUSSIAN, LanguageCode.ENGLISH)

    override suspend fun lookup(request: DictionaryRequest): Result<DictionaryResponse, ServiceError> =
        coroutineBinding {
            validateLanguage(request.language)?.let { return@coroutineBinding Err(it).bind() }
            val language = WikimediaLanguages.editionCode(request.language)

            val direct = client.pageExtract("wikipedia", language, request.word).bind()
            val page = direct ?: searchFallback(language, request.word)
            if (page == null) return@coroutineBinding DictionaryResponse(emptyList())

            val extract = page.extract?.trim().orEmpty()
            val definitions = buildList {
                page.description?.trim()?.takeIf(String::isNotBlank)?.let { add(Definition(it)) }
                extract.takeIf(String::isNotBlank)?.let { add(Definition(it)) }
            }.distinctBy(Definition::text)

            if (definitions.isEmpty()) DictionaryResponse(emptyList())
            else DictionaryResponse(
                listOf(
                    DictionaryEntry(
                        word = page.title.ifBlank { request.word },
                        partOfSpeech = "encyclopedia",
                        definitions = definitions
                    )
                )
            )
        }

    /**
     * A last attempt for spellings the title normaliser does not bridge.
     *
     * Search results are only trusted when the title is the word itself or shares a meaningful
     * prefix with it. That keeps a real article reachable while refusing to present an unrelated
     * article — the failure mode that made the old search-only lookup feel like it "found the
     * wrong word".
     */
    private suspend fun searchFallback(language: String, word: String): WikimediaExtractPage? {
        val found = client.search("wikipedia", language, word)
            .fold(success = { it }, failure = { null })
            ?: return null
        if (!isCloseMatch(found.title, word)) return null
        return client.pageExtract("wikipedia", language, found.title)
            .fold(success = { it }, failure = { null })
    }

    private fun isCloseMatch(title: String, word: String): Boolean {
        val a = normalize(title)
        val b = normalize(word)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        return a.startsWith(b) || b.startsWith(a)
    }

    /** Case, `ё`, punctuation and spacing are not differences in a Wikipedia title. */
    private fun normalize(value: String): String =
        value.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private fun validateLanguage(language: LanguageCode): ServiceError.UnsupportedLanguageError? =
        language.takeUnless { it in WikimediaLanguages.supported }?.let {
            ServiceError.UnsupportedLanguageError(it, "Wikipedia does not support language '${it.tag}'.")
        }
}
