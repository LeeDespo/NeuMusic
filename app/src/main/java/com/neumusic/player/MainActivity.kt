package com.neumusic.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.neumusic.player.data.DownloadStore
import com.neumusic.player.data.HomeCache
import com.neumusic.player.data.SearchHistoryStore
import com.neumusic.player.data.Prefs
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.ShadeTheme
import com.neumusic.player.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)
        PlayerHost.init(this)
        DownloadStore.init(this)
        DownloadStore.initContext(this)
        HomeCache.init(this)
        SearchHistoryStore.init(this)
        setContent {
            ShadeTheme {
                AppRoot()
            }
        }
    }
}
