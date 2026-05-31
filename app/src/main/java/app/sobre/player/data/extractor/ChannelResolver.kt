package app.sobre.player.data.extractor

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfo

data class ResolvedChannel(
    val channelId: String,
    val title: String,
    val rssUrl: String
)

object ChannelResolver {

    private val VIDEO_PATTERNS = listOf(
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/watch\?v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""youtube\.com/embed/([A-Za-z0-9_-]{11})""")
    )

    private val CHANNEL_PATTERNS = listOf(
        Regex("""youtube\.com/channel/(UC[A-Za-z0-9_-]+)"""),
        Regex("""youtube\.com/@[\w.-]+"""),
        Regex("""youtube\.com/c/[\w.-]+"""),
        Regex("""youtube\.com/user/[\w.-]+""")
    )

    fun isVideoUrl(url: String): Boolean {
        return VIDEO_PATTERNS.any { it.containsMatchIn(url) }
    }

    fun isChannelUrl(url: String): Boolean {
        return CHANNEL_PATTERNS.any { it.containsMatchIn(url) }
    }

    fun resolve(url: String): ResolvedChannel {
        val channelUrl = if (isVideoUrl(url)) {
            val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, url)
            streamInfo.uploaderUrl
                ?: throw IllegalStateException("Cannot resolve channel from video")
        } else {
            url
        }

        val channelInfo = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
        val channelId = extractChannelId(channelInfo.url)

        return ResolvedChannel(
            channelId = channelId,
            title = channelInfo.name,
            rssUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        )
    }

    private fun extractChannelId(url: String): String {
        val match = Regex("""channel/(UC[A-Za-z0-9_-]+)""").find(url)
        return match?.groupValues?.get(1)
            ?: throw IllegalStateException("Cannot extract channel ID from: $url")
    }
}
