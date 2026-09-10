package snd.komelia.similarity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Every path below is a real one, taken from the library on 2026-09-10 — the
 * rule exists because a third of the series carry no metadata at all, and the
 * only thing they all still have is where they were filed.
 */
class SeriesFolderGenreTest {

    @Test
    fun takesTheFolderBetweenTheRootAndTheSeries() {
        assertEquals("action", seriesFolderGenre("/Mangas/Action/Fire Punch (INT)"))
        assertEquals("super-héros", seriesFolderGenre("/Comics/Super-Héros/Batman"))
        assertEquals("science-fiction", seriesFolderGenre("/BD/Science-Fiction/Roche Limit"))
    }

    /**
     * `Divers` files series directly under the root: 28 distinct second-level
     * names for 35 series, against ten for 584 in `Mangas`. There is no genre
     * to read, and inventing one from the series' own name would make every
     * series its own unique term — the strongest rarity weight in the index,
     * for no meaning whatsoever.
     */
    @Test
    fun refusesWhenTheSeriesSitsDirectlyUnderTheRoot() {
        assertNull(seriesFolderGenre("/Divers/Kazoku Ryokou"))
        assertNull(seriesFolderGenre("/Light Novels/Durarara!!"))
        assertNull(seriesFolderGenre("Mangas"))
        assertNull(seriesFolderGenre(""))
    }

    /**
     * Some shelves sort by language. Grouping every English series as similar
     * to every other English series is a strong term that means nothing.
     */
    @Test
    fun dropsLanguageFolders() {
        assertNull(seriesFolderGenre("/Mangas/JP/Some Series"))
        assertNull(seriesFolderGenre("/Comics/1 EN/Some Series"))
        assertNull(seriesFolderGenre("/Comics/EN/Some Series"))
    }

    /**
     * Franchise folders are kept on purpose: two series filed under the same
     * franchise really are related, and that link is precisely what the sparse
     * metadata never provides.
     */
    @Test
    fun keepsFranchiseFolders() {
        assertEquals("dc essentiels", seriesFolderGenre("/Comics/DC Essentiels/Superman"))
        assertEquals("fairy tail (univers)", seriesFolderGenre("/Mangas/Fairy Tail (univers)/Edens Zero"))
    }

    /** Windows-shaped paths reach the same answer. */
    @Test
    fun handlesBackslashes() {
        assertEquals("action", seriesFolderGenre("\\Mangas\\Action\\Fire Punch (INT)"))
    }
}
