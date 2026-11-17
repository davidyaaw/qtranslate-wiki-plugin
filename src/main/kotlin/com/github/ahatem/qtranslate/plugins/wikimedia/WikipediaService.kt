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
 * The marker the host recognises on a suggestions-only entry.
 *
 * Kept as a plain string rather than a new API type so the plugin still loads on a stock
 * QTranslate 1.5.0 host. A stock host simply shows it as another dictionary section; the
 * patched host renders the titles as clickable "did you mean" buttons.
 */
internal const val DID_YOU_MEAN_PART_OF_SPEECH = "did_you_mean"

/**
 * Wikipedia as a dictionary: the intro of the article for a word.
 *
 * The Action API's `titles` query is used rather than the REST search endpoint because it
 * normalises the title and follows redirects, so an inflected form such as "слова" reaches
 * "Слово" and "привет" reaches "Приветствие". Only when that finds nothing does a search run.
 *
 * A search hit is accepted when its title is the same word, a close form, or a likely
 * inflection of it ("сессии" → "Сессия"). Anything else is returned as a suggestion, never as
 * the answer, so "бежать" cannot confidently open a film called "Некуда бежать".
 */
internal class WikipediaService(private val client: WikimediaClient) : LanguageSwitchDictionary {
    override val key = "wikimedia-wikipedia"
    override val name = "Wikipedia"
    override val iconPath = "assets/wikipedia.svg"
    override val version = "1.2.0"
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
            if (direct != null) return@coroutineBinding responseOf(direct, request.word)

            val fallback = searchFallback(language, request.word)
            fallback.page?.let { return@coroutineBinding responseOf(it, request.word) }

            if (fallback.suggestions.isEmpty()) {
                DictionaryResponse(emptyList())
            } else {
                DictionaryResponse(
                    listOf(
                        DictionaryEntry(
                            word = request.word,
                            partOfSpeech = DID_YOU_MEAN_PART_OF_SPEECH,
                            definitions = fallback.suggestions.map(::Definition)
                        )
                    )
                )
            }
        }

    private fun responseOf(page: WikimediaExtractPage, fallbackWord: String): DictionaryResponse {
        val extract = page.extract?.trim().orEmpty()
        val definitions = buildList {
            page.description?.trim()?.takeIf(String::isNotBlank)?.let { add(Definition(it)) }
            extract.takeIf(String::isNotBlank)?.let { add(Definition(it)) }
        }.distinctBy(Definition::text)

        if (definitions.isEmpty()) return DictionaryResponse(emptyList())
        return DictionaryResponse(
            listOf(
                DictionaryEntry(
                    word = page.title.ifBlank { fallbackWord },
                    partOfSpeech = "encyclopedia",
                    definitions = definitions
                )
            )
        )
    }

    /**
     * Searches for a title close enough to answer the lookup, and otherwise keeps the candidates
     * as suggestions.
     *
     * Search results are only trusted when the title is the word itself, shares a meaningful
     * prefix, or differs only in a short inflectional ending. That keeps a real article reachable
     * while refusing to present an unrelated article — the failure mode that made the old
     * search-only lookup feel like it "found the wrong word".
     */
    private suspend fun searchFallback(language: String, word: String): Fallback {
        val pages = client.searchPages("wikipedia", language, word, SEARCH_LIMIT)
            .fold(success = { it }, failure = { emptyList() })
        if (pages.isEmpty()) return Fallback()

        val close = pages.firstOrNull {
            isCloseMatch(it.title, word) || isInflectionMatch(it.title, word)
        }
        if (close != null) {
            val page = client.pageExtract("wikipedia", language, close.title)
                .fold(success = { it }, failure = { null })
            if (page != null) return Fallback(page = page)
        }

        val normalizedWord = normalize(word)
        val suggestions = pages
            .map { it.title }
            .filter { it.isNotBlank() && normalize(it) != normalizedWord }
            .distinct()
            .take(SUGGESTION_LIMIT)
        return Fallback(suggestions = suggestions)
    }

    private fun isCloseMatch(title: String, word: String): Boolean {
        val a = normalize(title)
        val b = normalize(word)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        return a.startsWith(b) || b.startsWith(a)
    }

    /**
     * A conservative Russian-inflection bridge: "сессии" → "Сессия", "слова" → "Слово".
     *
     * It is deliberately narrow. At least four leading characters must match and make up most
     * of the shorter title, and only a short ending may differ. That accepts case endings while
     * rejecting a different word that merely starts with the query ("стол" vs "столица") and
     * unrelated titles that contain the query in the middle.
     */
    private fun isInflectionMatch(title: String, word: String): Boolean {
        val a = normalize(title)
        val b = normalize(word)
        if (a.isEmpty() || b.isEmpty() || b.length < 5) return false

        var prefix = 0
        while (prefix < a.length && prefix < b.length && a[prefix] == b[prefix]) prefix++
        val shorter = minOf(a.length, b.length)
        if (prefix < 4 || prefix.toDouble() / shorter < 0.6) return false

        val titleEnding = a.length - prefix
        val wordEnding = b.length - prefix
        return titleEnding <= 3 && wordEnding <= 3
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

    private data class Fallback(
        val page: WikimediaExtractPage? = null,
        val suggestions: List<String> = emptyList()
    )

    private companion object {
        const val SEARCH_LIMIT = 5
        const val SUGGESTION_LIMIT = 5
    }
}
