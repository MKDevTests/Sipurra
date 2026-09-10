package snd.komelia.similarity

import snd.komga.client.series.KomgaSeries

/**
 * Mirrors GenreLabels.PREFIX in the UI module. Duplicated on purpose rather
 * than inverting the module dependency for a single string — if the namespace
 * ever changes, both move together.
 */
private const val GENRE_PREFIX = "kora:genre:"

/** The curated tag namespace. Everything else under `kora:` is a marker. */
private const val TAG_PREFIX = "kora:tag:"

/**
 * Tags that carry app state instead of taste, and must never be scored.
 *
 * They looked harmless until the bench ran on the real library: `nextrelease:`
 * tags are one per series (volume + date), so their rarity weight is the
 * highest of any term — two series sharing a release date would have outranked
 * two series sharing an author. `kora:hidden` is on 57 manga series and means
 * "an admin hid this", which is not a genre.
 */
/**
 * Author credits that name nobody.
 *
 * The bench found a credit literally named "a" on 140 series across the two
 * libraries — enough to make it a strong shared term, and it was ranking
 * unrelated series first while stealing the per-author cap from the real
 * author. One-letter names are never real credits here; the placeholders below
 * are what Komga/Komf leave behind when a source has no author.
 */
fun isJunkAuthorName(name: String): Boolean {
    val trimmed = name.trim()
    if (trimmed.length <= 1) return true
    return foldTerm(trimmed) in JUNK_AUTHOR_NAMES
}

private val JUNK_AUTHOR_NAMES = setOf("n/a", "na", "unknown", "inconnu", "anonyme", "anonymous", "various", "divers", "?", "-", "--")

fun isSimilarityMarkerTag(tag: String): Boolean = tag.lowercase().let {
    it.startsWith("nextrelease:") ||
        (it.startsWith("kora:") && !it.startsWith(GENRE_PREFIX) && !it.startsWith(TAG_PREFIX))
}

/**
 * Turns a series into the handful of terms the scorer needs.
 *
 * Called on every series of a page while indexing, so it must keep NOTHING it
 * doesn't score: the caller drops the KomgaSeries right after. Holding the full
 * objects for a few thousand series — summaries included, twice over — is what
 * would turn an index build into a memory spike on a tablet.
 */
/**
 * The genre a series is filed under on disk, or null when the path does not
 * say.
 *
 * Measured on the real library on 2026-09-10: a third of the series carry no
 * genre, no tag, no publisher and no author, which makes them invisible to the
 * scorer for good — nothing can be similar to nothing. But every one of them
 * has a path, and the path is a taxonomy the user maintained by hand:
 * `Mangas/Action/Fire Punch (INT)`, `Comics/Super-Héros/…`. Over 725 sampled
 * paths, `Mangas` used ten distinct second-level names for 584 series and
 * `Comics` four for 43 — a genre. `Divers` used 28 for 35 — a title.
 *
 * The rule separating those two without any global view: a series folder sits
 * at `<root>/<genre>/<series>`, so a genre exists only when there is a segment
 * BETWEEN the library root and the series folder. `Divers/<series>` has none
 * and yields null, which is the correct answer.
 */
internal fun seriesFolderGenre(url: String): String? {
    val segments = url.split('/', '\\').filter { it.isNotBlank() }
    // <root>/<genre>/<series> is the shortest shape that names a genre.
    if (segments.size < 3) return null
    val candidate = segments[segments.size - 2].trim().lowercase()
    if (candidate.isEmpty()) return null
    // Some shelves sort by language instead: `Mangas/JP/…`, `Comics/1 EN/…`.
    // Grouping every English series as similar to every other would be a
    // strong term with no meaning, so those are dropped. Franchise folders
    // (`DC Essentiels`, `Fairy Tail (univers)`) are deliberately KEPT — two
    // series filed under the same franchise really are related, and that is
    // exactly the kind of link the sparse metadata never provides.
    if (candidate.first().isDigit()) return null
    if (candidate in LANGUAGE_FOLDERS) return null
    return candidate
}

private val LANGUAGE_FOLDERS = setOf(
    "en", "fr", "jp", "ja", "us", "uk", "vo", "vf", "vostfr", "eng", "fra", "multi", "raw",
)

fun KomgaSeries.toSimilarityTerms(): SeriesTerms {
    val seriesTags = metadata.tags.map { it.trim() }
        .filter { it.isNotEmpty() && !isSimilarityMarkerTag(it) }

    // `kora:genre:*` is the curated taxonomy and scores highest; everything else
    // is a plain tag. Splitting here keeps the weighting honest.
    val taggedGenres = seriesTags.filter { it.startsWith(GENRE_PREFIX) }
        .map { it.removePrefix(GENRE_PREFIX).lowercase() }
        .toSet()

    // The folder genre fills in ONLY where the curated taxonomy says nothing.
    // Deliberately not merged in everywhere: a series that already has genres
    // keeps exactly the vector it had, so this cannot move any existing
    // "Similar" result. It buys reach among the series that had none, which is
    // the whole point, at zero risk to the ones that already worked.
    val genres = taggedGenres.ifEmpty {
        setOfNotNull(seriesFolderGenre(url))
    }
    val tags = seriesTags.filterNot { it.startsWith(GENRE_PREFIX) }
        .map { it.lowercase() }
        .toSet()

    // One entry per author name: a name credited under several roles keeps the
    // most significant one, so "writer + colorist" doesn't score as a colorist.
    val authors = booksMetadata.authors
        .mapNotNull { author ->
            val name = author.name.trim()
            if (isJunkAuthorName(name)) null else name to author.role.trim().lowercase()
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, roles) -> roles.minByOrNull { ROLE_RANK[it] ?: ROLE_RANK.size } ?: roles.first() }

    return SeriesTerms(
        authors = authors,
        genres = genres,
        tags = tags,
        bookTags = booksMetadata.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
        publisher = metadata.publisher.trim().lowercase().ifEmpty { null },
    )
}

/** Most-significant-first, for picking one role out of several credits. */
private val ROLE_RANK: Map<String, Int> = listOf(
    "writer", "artist", "penciller", "inker", "colorist", "letterer", "translator",
).withIndex().associate { (index, role) -> role to index }
