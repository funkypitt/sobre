package app.sobre.player

import app.sobre.player.data.extractor.Ytdlp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure decisions of the fallback: when to fetch a newer yt-dlp, and what to tell the
 * reader when nothing came down. Everything else needs Python and a telephone.
 */
class YtdlpTest {

    @Test fun youTubeTurningAwayAnOldYtDlpIsRecognised() {
        listOf(
            "ERROR: unable to download video data: HTTP Error 403: Forbidden",
            "HTTP Error 403: forbidden",
            "WARNING: your yt-dlp version is out of date, update it",
            "ERROR: nsig extraction failed: Some formats may be missing",
            "ERROR: Sign in to confirm you're not a bot",
        ).forEach { assertTrue(it, Ytdlp.stale(IllegalStateException(it))) }
    }

    @Test fun anOrdinaryFailureIsNotAnOldYtDlp() {
        listOf(
            "ERROR: Video unavailable",
            "java.net.UnknownHostException: www.youtube.com",
            "ERROR: This live event will begin in 2 hours",
            "",
        ).forEach { assertFalse(it, Ytdlp.stale(IllegalStateException(it))) }
    }

    @Test fun whatIsShownIsTheEndOfWhatYtDlpSaidWithoutItsProgressLines() {
        val said = """
            [download]   0.0% of 4.00MiB at Unknown speed ETA Unknown
            [download]  53.1% of 4.00MiB at  1.00MiB/s ETA 00:02
            ERROR: unable to download video data
            HTTP Error 403: Forbidden
        """.trimIndent()
        assertEquals("ERROR: unable to download video data\nHTTP Error 403: Forbidden", Ytdlp.tail(said))
    }

    @Test fun nothingToSayIsSaidAsNothing() {
        assertNull(Ytdlp.tail(null))
        assertNull(Ytdlp.tail("   \n  \n"))
        assertNull(Ytdlp.tail("[download] 100% of 4.00MiB"))
    }

    @Test fun aLongComplaintIsCutRatherThanFillingTheScreen() {
        val huge = (1..50).joinToString("\n") { "ERROR: quelque chose d'assez long s'est mal passé, ligne $it" }
        assertTrue((Ytdlp.tail(huge)?.length ?: 0) <= 400)
    }
}
