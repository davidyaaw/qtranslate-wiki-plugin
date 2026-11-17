// SPDX-License-Identifier: MPL-2.0
// Derived from QTranslate (https://github.com/ahatem/QTranslate).
package com.github.ahatem.qtranslate.plugins.wikimedia

import com.github.ahatem.qtranslate.api.plugin.ServiceError
import com.github.ahatem.qtranslate.api.plugin.HttpClient
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class WikimediaClient(
    private val httpClient: HttpClient,
    private val minimumIntervalMillis: Long = 300,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wait: suspend (Long) -> Unit = { delay(it) }
) {
    private val requestMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var lastRequestStartedAt = 0L

    suspend fun search(
        project: String,
        language: String,
        query: String
    ): Result<WikimediaSearchPage?, ServiceError> = request {
        httpClient.get(
            url = "https://$language.$project.org/w/rest.php/v1/search/page",
            headers = HEADERS,
            queryParams = mapOf("q" to query, "limit" to 1)
        ).fold(
            success = { body -> parse<WikimediaSearchResponse>(body).fold(
                success = { Ok(it.pages.firstOrNull()) },
                failure = { Err(it) }
            ) },
            failure = { Err(mapError(project, it)) }
        )
    }

    suspend fun pageWithHtml(
        project: String,
        language: String,
        pageKey: String
    ): Result<WikimediaPageResponse, ServiceError> = request {
        httpClient.get(
            url = "https://$language.$project.org/w/rest.php/v1/page/${encodePath(pageKey)}/with_html",
            headers = HEADERS
        ).fold(
            success = { parse(it) },
            failure = { Err(mapError(project, it)) }
        )
    }

    /**
     * Fetches a page's plain-text intro by title.
     *
     * The Action API follows redirects and normalises the title itself, so a lookup for an
     * inflected form or a redirect ("слова", "привет") reaches the canonical article without a
     * separate search round trip. A page that does not exist comes back as `null` rather than an
     * error: "Wikipedia has no article for this word" is an answer, not a failure.
     */
    suspend fun pageExtract(
        project: String,
        language: String,
        title: String
    ): Result<WikimediaExtractPage?, ServiceError> = request {
        httpClient.get(
            url = "https://$language.$project.org/w/api.php",
            headers = HEADERS,
            queryParams = mapOf(
                "action" to "query",
                "format" to "json",
                "formatversion" to 2,
                "redirects" to 1,
                "titles" to title,
                "prop" to "extracts|description",
                "exintro" to 1,
                "explaintext" to 1
            )
        ).fold(
            success = { body ->
                parse<WikimediaExtractResponse>(body).map { response ->
                    response.query?.pages
                        ?.firstOrNull()
                        ?.takeUnless { it.missing }
                }
            },
            failure = { Err(mapError(project, it)) }
        )
    }

    /**
     * Searches Commons for files matching [query].
     *
     * Commons is one wiki rather than one per language, so this takes no language: the search
     * index covers every language's captions at once. [fileTypes] is a Commons search keyword
     * (`bitmap`, `drawing`, or both joined by `|`) which keeps audio, video and PDFs out of a
     * grid of thumbnails.
     */
    suspend fun searchImages(
        query: String,
        limit: Int,
        fileTypes: String,
        thumbnailWidth: Int
    ): Result<List<CommonsPage>, ServiceError> = request {
        httpClient.get(
            url = "https://commons.wikimedia.org/w/api.php",
            headers = HEADERS,
            queryParams = mapOf(
                "action" to "query",
                "format" to "json",
                "formatversion" to 2,
                "generator" to "search",
                "gsrsearch" to "$query filetype:$fileTypes",
                // Namespace 6 is File:. Without it the search returns article pages, which have
                // no image to show.
                "gsrnamespace" to 6,
                "gsrlimit" to limit,
                "prop" to "imageinfo",
                "iiprop" to "url|extmetadata",
                // Asking for a thumbnail rather than scaling the original: some Commons files are
                // tens of megabytes, and a grid of them would be unusable.
                "iiurlwidth" to thumbnailWidth
            )
        ).fold(
            success = { body ->
                parse<CommonsImageResponse>(body).map { it.query?.pages.orEmpty() }
            },
            failure = { Err(mapError(COMMONS, it)) }
        )
    }

    private suspend fun <T> request(block: suspend () -> Result<T, ServiceError>): Result<T, ServiceError> =
        requestMutex.withLock {
            val remaining = minimumIntervalMillis - (clockMillis() - lastRequestStartedAt)
            if (lastRequestStartedAt != 0L && remaining > 0) wait(remaining)
            lastRequestStartedAt = clockMillis()
            block()
        }

    private inline fun <reified T> parse(body: String): Result<T, ServiceError> =
        runCatching { json.decodeFromString<T>(body) }.fold(
            onSuccess = { Ok(it) },
            onFailure = {
                Err(ServiceError.InvalidResponseError("Wikimedia returned an unexpected API response.", it))
            }
        )

    private fun mapError(project: String, error: ServiceError): ServiceError {
        val serviceName = when (project) {
            "wikipedia" -> "Wikipedia"
            COMMONS -> "Wikimedia Commons"
            else -> "Wiktionary"
        }
        return when (error) {
            is ServiceError.RateLimitError -> ServiceError.RateLimitError(
                "$serviceName is rate-limiting requests. Please wait and try again.",
                error.retryAfterSeconds,
                error.cause
            )
            is ServiceError.AuthenticationError -> ServiceError.ServiceUnavailableError(
                "$serviceName rejected the request. Please try again later.",
                error.cause
            )
            is ServiceError.ServiceUnavailableError -> ServiceError.ServiceUnavailableError(
                "$serviceName is temporarily unavailable. Please try again later.",
                error.cause
            )
            else -> error
        }
    }

    private fun encodePath(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private companion object {
        const val COMMONS = "commons"

        val HEADERS = mapOf(
            "Accept" to "application/json",
            "User-Agent" to "QTranslate/1.0 (https://github.com/ahatem/QTranslate)"
        )
    }
}
