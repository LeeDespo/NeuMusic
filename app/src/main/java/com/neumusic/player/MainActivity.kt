package com.neumusic.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.neumusic.player.data.DownloadStore
import com.neumusic.player.data.AppLog
import com.neumusic.player.data.HomeCache
import com.neumusic.player.data.RecommendStore
import com.neumusic.player.data.SearchHistoryStore
import com.neumusic.player.data.Prefs
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.DayLightHost
import com.neumusic.player.shade.ShadeTheme
import com.neumusic.player.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Prefs.init(this)
        DayLightHost.install()
        PlayerHost.init(this)
        DownloadStore.init(this)
        DownloadStore.initContext(this)
        HomeCache.init(this)
        RecommendStore.init(this)
        RecommendStore.loadExcluded(this)
        AppLog.init(this)
        SearchHistoryStore.init(this)
        setContent {
            // 「光影随时间变化」的时钟：进应用就开始对时（固定光影模式下不读它）。
            LaunchedEffect(Unit) { DayLightHost.runClock() }
            ShadeTheme {
                AppRoot()
            }
        }
    }
}
