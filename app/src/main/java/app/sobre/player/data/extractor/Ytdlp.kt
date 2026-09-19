package app.sobre.player.data.extractor

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * yt-dlp, the way out when NewPipe no longer finds the audio.
 *
 * NewPipe is quick and weighs nothing, and it is still what Sobre tries first. But YouTube
 * changes its signatures without warning, and a NewPipe that has not been rebuilt simply stops
 * finding anything — the application looks broken until a new version is published, which can
 * take days. yt-dlp follows those changes within hours, and, unlike a library, it can fetch its
 * own new version on the telephone: so the fallback repairs itself.
 *
 * Three things learned the hard way in Reader's Podcasts and kept here:
 *
 *  - [prepare] must have run before [update], or updating throws "instance not initialized";
 *  - a failed update must never be written down as done, or the application never updates again
 *    and answers 403 Forbidden for ever;
 *  - YouTube turns away an old yt-dlp with a 403 and a warning nobody reads, so rather than wait
 *    for the weekly update, the new version is fetched then and there and the attempt repeated.
 */
object Ytdlp {
    private const val TAG = "Sobre"
    private const val PREFS = "sobre"
    private const val KEY_UPDATED = "ytdlp_updated"
    private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

    /** The best audio-only track, preferring the one Media3 plays without remuxing. */
    private const val FORMAT = "bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio"

    @Volatile private var ready = false

    /** Unpacks Python and yt-dlp into the app's files. Seconds, the first time and after each update. */
    @Synchronized
    fun prepare(context: Context) {
        if (ready) return
        YoutubeDL.getInstance().init(context.applicationContext)
        ready = true
    }

    /**
     * yt-dlp brought up to date if a week has gone by, and never more often than that: the
     * download that follows must not wait on the network more than it has to. A failure here is
     * not one — what follows is attempted all the same.
     */
    fun freshen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong(KEY_UPDATED, 0) < WEEK_MS) return
        // Only a real success is written down.
        if (runCatching { update(context) }.isSuccess) {
            prefs.edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply()
        }
    }

    /** yt-dlp itself, brought up to date. Returns what happened, for a log or a settings line. */
    fun update(context: Context): String {
        prepare(context)
        return YoutubeDL.getInstance()
            .updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)?.name ?: "NOTHING"
    }

    /** What version is in place, or empty when yt-dlp has never been unpacked. */
    fun version(context: Context): String =
        runCatching { YoutubeDL.getInstance().version(context.applicationContext) }.getOrNull().orEmpty()

    fun cancel(id: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
    }

    /** What yt-dlp knows of a video that Sobre has a use for. */
    data class Audio(val url: String, val description: String, val durationSec: Long?)

    /**
     * The audio track of one video, for the player to stream, with the little that goes with it.
     * Throws when yt-dlp could not have it either: that failure belongs to the caller to report.
     */
    fun audio(context: Context, videoId: String): Audio {
        prepare(context)
        freshen(context)
        val request = YoutubeDLRequest(watch(videoId)).apply {
            addOption("-f", FORMAT)
            addOption("--no-playlist")
        }
        val info = attempt(context) { YoutubeDL.getInstance().getInfo(request) }
        val url = info.url?.takeIf { it.isNotBlank() }
        // A format selection usually puts the address at the top; when it does not, the chosen
        // format still carries it, and failing that the best audio-only one in the list.
            ?: info.requestedFormats?.firstNotNullOfOrNull { it.url?.takeIf { u -> u.isNotBlank() } }
            ?: info.formats
                ?.filter { it.acodec != null && it.acodec != "none" && (it.vcodec == null || it.vcodec == "none") }
                ?.filter { !it.url.isNullOrBlank() }
                ?.maxByOrNull { if (it.abr > 0) it.abr else it.tbr }
                ?.url
            ?: throw IllegalStateException("yt-dlp : aucune piste audio")
        return Audio(
            url = url,
            description = info.description.orEmpty(),
            durationSec = info.duration.toLong().takeIf { it > 0 },
        )
    }

    /**
     * The audio of one video into [directory], named after [videoId] with whatever extension the
     * chosen track turns out to have. No conversion, and so no ffmpeg: what YouTube serves as
     * m4a or opus is what Media3 plays. Returns the finished file.
     */
    fun download(
        context: Context,
        videoId: String,
        directory: File,
        onProgress: (Int) -> Unit = {},
    ): File {
        prepare(context)
        freshen(context)
        directory.mkdirs()
        leftovers(directory, videoId).forEach { it.delete() }
        val request = YoutubeDLRequest(watch(videoId)).apply {
            addOption("-f", FORMAT)
            addOption("-o", File(directory, videoId).absolutePath + ".%(ext)s")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--newline")
            addOption("--retries", "3")
        }
        val response = attempt(context) {
            YoutubeDL.getInstance().execute(request, videoId) { progress, _, _ ->
                onProgress(progress.toInt().coerceIn(0, 100))
            }
        }
        return leftovers(directory, videoId).firstOrNull { !it.name.endsWith(".part") }
            ?: throw IllegalStateException(tail(response.err) ?: tail(response.out) ?: "yt-dlp : rien à lire")
    }

    /**
     * One attempt, and a second one after an update when what came back was YouTube turning away
     * a yt-dlp it considers too old. That is the whole difference between an application that
     * works this morning and one that does not.
     */
    private fun <T> attempt(context: Context, block: () -> T): T = runCatching { block() }.getOrElse { first ->
        if (!stale(first)) throw IllegalStateException(tail(first.message) ?: first.javaClass.simpleName, first)
        Log.w(TAG, "yt-dlp refusé (403) : mise à jour puis nouvel essai")
        runCatching { update(context) }
        runCatching { block() }.getOrElse { second ->
            throw IllegalStateException(tail(second.message) ?: second.javaClass.simpleName, second)
        }
    }

    /** Whether what came back is YouTube turning away a yt-dlp it considers too old. */
    internal fun stale(e: Throwable): Boolean {
        val text = e.message.orEmpty()
        return text.contains("403") || text.contains("Forbidden", true) ||
            text.contains("version is out of date", true) || text.contains("nsig extraction failed", true) ||
            text.contains("Sign in to confirm", true)
    }

    private fun watch(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    private fun leftovers(directory: File, videoId: String): List<File> =
        directory.listFiles { f -> f.name.startsWith(videoId) }?.toList().orEmpty()

    /**
     * The last few lines, not just the last one: yt-dlp says what happened over two or three
     * lines and warns about its own age on a fourth, so a single line often named the least
     * useful of them.
     */
    internal fun tail(text: String?): String? = text?.trim()?.lines()
        ?.filter { it.isNotBlank() && !it.startsWith("[download]") }
        ?.takeLast(3)?.joinToString("\n")?.takeIf { it.isNotBlank() }?.take(400)
}
