// SPDX-License-Identifier: MPL-2.0
// Derived from QTranslate (https://github.com/ahatem/QTranslate).
package com.github.ahatem.qtranslate.plugins.wikimedia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class WikimediaSearchResponse(
    val pages: List<WikimediaSearchPage> = emptyList()
)

@Serializable
internal data class WikimediaSearchPage(
    val key: String,
    val title: String,
    val excerpt: String = "",
    val description: String? = null
)

@Serializable
internal data class WikimediaPageResponse(
    val key: String,
    val title: String,
    val html: String
)

/**
 * One page from the Action API's `titles` query, with its plain-text intro.
 *
 * `formatversion=2` represents a missing page as `missing: true` on the page instead of failing
 * the request, which is why this endpoint is used rather than the REST summary endpoint: a word
 * Wikipedia has no article for is an ordinary result, not an HTTP error.
 */
@Serializable
internal data class WikimediaExtractResponse(
    val query: WikimediaExtractQuery? = null
)

@Serializable
internal data class WikimediaExtractQuery(
    val pages: List<WikimediaExtractPage> = emptyList()
)

@Serializable
internal data class WikimediaExtractPage(
    val title: String = "",
    val missing: Boolean = false,
    val description: String? = null,
    val extract: String? = null
)

internal data class WiktionarySenseGroup(
    val heading: String,
    val definitions: List<String>
)

/**
 * Commons image search, from the Action API rather than the REST one used above.
 *
 * The REST API has no file search, so this is the older `api.php` with `formatversion=2` — which
 * returns `pages` as a list instead of an object keyed by page id, and so needs no special
 * handling to deserialize.
 */
@Serializable
internal data class CommonsImageResponse(
    val query: CommonsQuery? = null
)

@Serializable
internal data class CommonsQuery(
    val pages: List<CommonsPage> = emptyList()
)

@Serializable
internal data class CommonsPage(
    val title: String = "",
    @SerialName("imageinfo") val imageInfo: List<CommonsImageInfo> = emptyList()
)

@Serializable
internal data class CommonsImageInfo(
    @SerialName("thumburl") val thumbnailUrl: String? = null,
    val url: String? = null,
    @SerialName("descriptionurl") val descriptionUrl: String? = null,
    @SerialName("extmetadata") val extraMetadata: Map<String, CommonsMetadataValue> = emptyMap()
)

/**
 * One `extmetadata` entry.
 *
 * Held as a [JsonElement] because the field is typed per key rather than uniformly — most are
 * strings but some arrive as numbers, and a `String` here would fail the whole response over a
 * field we do not read.
 */
@Serializable
internal data class CommonsMetadataValue(
    val value: JsonElement? = null
) {
    val text: String? get() = (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
