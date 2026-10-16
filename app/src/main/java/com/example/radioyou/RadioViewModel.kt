package com.example.radioyou

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("radio", 0)

    var query by mutableStateOf("")
    var country by mutableStateOf("JP")
    var stations by mutableStateOf<List<Station>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var current by mutableStateOf<Station?>(null)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var favorites by mutableStateOf(prefs.getStringSet("fav", emptySet())!!.toSet())
    var favoritesOnly by mutableStateOf(false)

    private var controller: MediaController? = null
    private var searchJob: Job? = null
    private val favCache = mutableMapOf<String, Station>()

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                    }
                    override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                        error = "再生エラー: ${e.errorCodeName}"
                    }
                })
            }
        }, MoreExecutors.directExecutor())
        search()
    }

    fun onQueryChange(v: String) { query = v; search(debounce = true) }
    fun onCountryChange(v: String) { country = v; search() }

    fun search(debounce: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounce) delay(400)
            loading = true; error = null
            runCatching { withContext(Dispatchers.IO) { RadioApi.search(query, country) } }
                .onSuccess { stations = it; it.forEach { s -> if (s.uuid in favorites) favCache[s.uuid] = s } }
                .onFailure { error = "読み込みに失敗しました: ${it.message}" }
            loading = false
        }
    }

    fun play(s: Station) {
        val c = controller ?: return
        current = s
        favCache[s.uuid] = s
        c.setMediaItem(
            MediaItem.Builder().setUri(s.url).setMediaId(s.uuid)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.name).setArtist(s.tags.take(60)).build())
                .build()
        )
        c.prepare(); c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun stop() { controller?.stop(); current = null }

    fun toggleFavorite(s: Station) {
        favCache[s.uuid] = s
        favorites = if (s.uuid in favorites) favorites - s.uuid else favorites + s.uuid
        prefs.edit().putStringSet("fav", favorites).apply()
    }

    fun visibleStations(): List<Station> =
        if (favoritesOnly) favorites.mapNotNull { favCache[it] } else stations

    override fun onCleared() { controller?.release() }
}
