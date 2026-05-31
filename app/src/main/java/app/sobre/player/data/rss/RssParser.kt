package app.sobre.player.data.rss

import app.sobre.player.data.db.Episode
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class RssEpisode(
    val videoId: String,
    val title: String,
    val published: Long,
    val description: String
)

object RssParser {

    fun parse(xml: String): List<RssEpisode> {
        val episodes = mutableListOf<RssEpisode>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var inEntry = false
        var videoId: String? = null
        var title: String? = null
        var published: String? = null
        var description: String? = null
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when {
                        parser.name == "entry" -> {
                            inEntry = true
                            videoId = null
                            title = null
                            published = null
                            description = null
                        }
                        inEntry && parser.name == "yt:videoId" -> currentTag = "yt:videoId"
                        inEntry && parser.name == "media:description" -> currentTag = "media:description"
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inEntry) {
                        when (currentTag) {
                            "yt:videoId" -> videoId = parser.text.trim()
                            "title" -> title = parser.text.trim()
                            "published" -> published = parser.text.trim()
                            "media:description" -> description = parser.text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        inEntry = false
                        if (videoId != null && title != null && published != null) {
                            episodes.add(
                                RssEpisode(
                                    videoId = videoId!!,
                                    title = title!!,
                                    published = parseIso8601(published!!),
                                    description = description ?: ""
                                )
                            )
                        }
                    }
                    currentTag = ""
                }
            }
            parser.next()
        }
        return episodes
    }

    private fun parseIso8601(dateStr: String): Long {
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    fun toEpisodes(rssEpisodes: List<RssEpisode>, channelId: String, channelTitle: String): List<Episode> {
        return rssEpisodes.map { rss ->
            Episode(
                videoId = rss.videoId,
                channelId = channelId,
                channelTitle = channelTitle,
                title = rss.title,
                description = rss.description,
                publishedAt = rss.published
            )
        }
    }
}
