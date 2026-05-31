package app.sobre.player.data.opml

import app.sobre.player.data.db.Channel
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object OpmlManager {

    fun export(channels: List<Channel>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("""  <head><title>Sobre subscriptions</title></head>""")
        sb.appendLine("""  <body>""")
        for (channel in channels) {
            val escapedTitle = channel.title.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
            sb.appendLine("""    <outline text="$escapedTitle" type="rss" xmlUrl="${channel.rssUrl}" htmlUrl="https://www.youtube.com/channel/${channel.channelId}" />""")
        }
        sb.appendLine("""  </body>""")
        sb.appendLine("""</opml>""")
        return sb.toString()
    }

    data class OpmlEntry(val title: String, val rssUrl: String, val channelId: String)

    fun parse(opml: String): List<OpmlEntry> {
        val entries = mutableListOf<OpmlEntry>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(opml))

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                val text = parser.getAttributeValue(null, "text") ?: ""
                if (xmlUrl != null && xmlUrl.contains("channel_id=")) {
                    val channelId = xmlUrl.substringAfter("channel_id=").substringBefore("&")
                    if (channelId.startsWith("UC")) {
                        entries.add(OpmlEntry(title = text, rssUrl = xmlUrl, channelId = channelId))
                    }
                }
            }
            parser.next()
        }
        return entries
    }
}
