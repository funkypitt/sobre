package app.sobre.player

import android.app.Application
import app.sobre.player.data.extractor.SobreDownloader
import org.schabi.newpipe.extractor.NewPipe

class SobreApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(SobreDownloader.instance)
    }
}
