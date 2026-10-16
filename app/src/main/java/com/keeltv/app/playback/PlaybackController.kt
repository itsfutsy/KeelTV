package com.keeltv.app.playback

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.session.MediaSession
import com.keeltv.app.data.net.USER_AGENT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val BUFFER_BYTES = 64 * 1024 * 1024

data class PlaybackRequest(
  val candidates: List<String>,
  val live: Boolean,
  val startAtMs: Long = 0,
  val title: String = "",
)

data class PlaybackState(
  val loading: Boolean = false,
  val reconnecting: Boolean = false,
  val stalled: Boolean = false,
  val error: String? = null,
  val playing: Boolean = false,
  val paused: Boolean = false,
  val behindLiveMs: Long = 0,
  val tracks: List<PlayerTrack> = emptyList(),
  val audioId: String? = null,
  val subtitleId: String? = null,
) {
  val undecodable: Boolean
    get() = error == PlaybackCopy.CODEC

  val audioUndecodable: Boolean
    get() = tracks.any { it.kind == TrackKind.AUDIO } &&
      tracks.none { it.kind == TrackKind.AUDIO && it.supported }
}

@OptIn(UnstableApi::class)
class PlaybackController(
  private val context: Context,
  http: OkHttpClient,
  private val scope: CoroutineScope,
) {
  var state by mutableStateOf(PlaybackState())
    private set

  var player: ExoPlayer? = null
    private set

  var onEnded: (() -> Unit)? = null

  private var session: MediaSession? = null
  @Volatile private var request: PlaybackRequest? = null
  private var preferences = PlaybackPreferences()
  private var overrides = emptyMap<String, TrackSelectionOverride>()

  private var reopen: Job? = null
  private var watchdog: Job? = null
  private var failures = 0
  private var candidate = 0
  private var played = false
  private var attemptPlayed = false

  private var deepest = 0L

  private var anchorWall = 0L
  private var anchorPosition = 0L
  private var resumeAtMs = 0L
  private var suspended = false

  private val dataSource = OkHttpDataSource.Factory(
    http.newBuilder()
      .readTimeout(10, TimeUnit.SECONDS)
      .connectTimeout(15, TimeUnit.SECONDS)
      .build(),
  ).setUserAgent(USER_AGENT)

  /**
   * An MPEG-TS stream can tag a DTS track as stream type 0x82, and Media3 drops that stream unless
   * this flag is on, so the channel comes through with no audio track at all. The flag is off by default because ATSC uses 0x82 for splice subtitles, which an
   * IPTV catalogue does not carry.
   */
  private val extractors = LiveSeekableExtractors(
    DefaultExtractorsFactory()
      .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS),
  )

  private val loadPolicy = object : DefaultLoadErrorHandlingPolicy() {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
      Reconnect.loadRetryDelayMs(request?.live == true, info.exception.upstreamStatus(), info.errorCount)
        ?: C.TIME_UNSET
  }

  private val listener = object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) = handleError(error)

    override fun onPlaybackStateChanged(playbackState: Int) {
      when (playbackState) {
        Player.STATE_READY -> {
          played = true
          failures = 0
          val position = player?.currentPosition ?: 0
          if (!attemptPlayed) {
            attemptPlayed = true
            anchorWall = SystemClock.elapsedRealtime()
            anchorPosition = position
          }
          state = state.copy(
            loading = false,
            reconnecting = false,
            stalled = false,
            error = null,
            behindLiveMs = behind(position),
          )
        }
        Player.STATE_BUFFERING ->
          if (!state.reconnecting) state = state.copy(loading = true, stalled = played)
        Player.STATE_ENDED -> {
          if (request?.live == true) reconnect(null) else onEnded?.invoke()
        }
        else -> Unit
      }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      state = state.copy(playing = isPlaying)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
      state = state.copy(paused = !playWhenReady)
    }

    override fun onTracksChanged(tracks: Tracks) = readTracks(tracks)
  }

  fun open(next: PlaybackRequest) {
    reopen?.cancel()
    request = next
    failures = 0
    candidate = 0
    played = false
    resumeAtMs = next.startAtMs
    state = PlaybackState(loading = true)
    start()
  }

  fun retry() {
    val current = request ?: return
    reopen?.cancel()
    failures = 0
    candidate = 0
    played = false
    if (!current.live) resumeAtMs = player?.currentPosition ?: resumeAtMs
    state = state.copy(loading = true, reconnecting = false, stalled = false, error = null)
    start()
  }

  fun applyPreferences(prefs: PlaybackPreferences) {
    preferences = prefs
    player?.currentTracks?.let(::readTracks)
  }

  fun select(kind: TrackKind, id: String?) {
    val p = player ?: return
    val type = if (kind == TrackKind.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
    val override = id?.let { overrides[it] }
    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
      .setTrackTypeDisabled(type, id == null && kind == TrackKind.SUBTITLE)
      .clearOverridesOfType(type)
      .apply { if (override != null) addOverride(override) }
      .build()
    state = if (kind == TrackKind.AUDIO) state.copy(audioId = id) else state.copy(subtitleId = id)
  }

  fun suspendPlayback() {
    val current = request ?: return
    reopen?.cancel()
    watchdog?.cancel()
    val p = player ?: return
    if (!current.live) resumeAtMs = p.currentPosition
    p.stop()
    p.clearMediaItems()
    suspended = true
  }

  fun resumePlayback() {
    if (!suspended) return
    suspended = false
    failures = 0
    candidate = 0
    played = false
    state = state.copy(loading = true, reconnecting = false, stalled = false, error = null)
    start()
  }

  fun togglePlay() {
    val p = player ?: return
    if (p.playWhenReady) p.pause() else p.play()
  }

  fun shift(deltaMs: Long) {
    val p = player ?: return
    if (request?.live != true || !attemptPlayed) return
    val position = p.currentPosition
    deepest = maxOf(deepest, position)
    val target = TimeShift.stepTo(position, p.bufferedPosition, deepest, deltaMs)
    if (target == position) return
    p.seekTo(target)
    state = state.copy(behindLiveMs = behind(target))
  }

  private fun behind(positionMs: Long): Long =
    if (anchorWall == 0L) 0
    else TimeShift.behindMs(SystemClock.elapsedRealtime() - anchorWall, positionMs - anchorPosition)

  fun release() {
    reopen?.cancel()
    watchdog?.cancel()
    session?.release()
    session = null
    player?.removeListener(listener)
    player?.release()
    player = null
  }

  private fun ensurePlayer(live: Boolean): ExoPlayer {
    player?.let { return it }
    val renderers = DefaultRenderersFactory(context)
      .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
      .setEnableDecoderFallback(true)

    val load = DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        if (live) 4_000 else 15_000,
        if (live) TimeShift.PAUSE_MS else 60_000,
        if (live) 2_000 else 2_500,
        if (live) 4_000 else 5_000,
      )
      .setBackBuffer(if (live) TimeShift.BACK_BUFFER_MS else 0, true)
      .setTargetBufferBytes(BUFFER_BYTES)
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()

    val built = ExoPlayer.Builder(context)
      .setRenderersFactory(renderers)
      .setLoadControl(load)
      .setMediaSourceFactory(
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, dataSource), extractors)
          .setLoadErrorHandlingPolicy(loadPolicy),
      )
      .build()
      .apply {
        setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
          true,
        )
        setHandleAudioBecomingNoisy(true)
        addListener(listener)
      }
    player = built
    session = runCatching { MediaSession.Builder(context, built).build() }.getOrNull()
    return built
  }

  private fun start() {
    val current = request ?: return
    val p = ensurePlayer(current.live)
    // One connection at a time: whatever is open has to be closed before the next request goes out.
    p.stop()
    p.clearMediaItems()
    p.setMediaItem(mediaItem(current.candidates[candidate], current))
    if (!current.live && resumeAtMs > 0) p.seekTo(resumeAtMs)
    attemptPlayed = false
    deepest = 0
    anchorWall = 0
    extractors.live = current.live
    p.prepare()
    p.playWhenReady = true
    watch(current.live)
  }

  private fun watch(live: Boolean) {
    watchdog?.cancel()
    watchdog = scope.launch {
      var stalledSince = -1L
      while (true) {
        delay(1_000)
        val p = player ?: break
        val position = p.currentPosition
        deepest = maxOf(deepest, position)
        val behind = behind(position)
        if (behind / 1_000 != state.behindLiveMs / 1_000) state = state.copy(behindLiveMs = behind)
        if (!p.playWhenReady || p.playbackState != Player.STATE_BUFFERING) {
          stalledSince = -1L
          continue
        }
        val now = SystemClock.elapsedRealtime()
        if (stalledSince < 0) stalledSince = now
        if (now - stalledSince < Reconnect.stallBudgetMs(attemptPlayed, live)) continue
        reconnect(null, stalled = true)
        break
      }
    }
  }

  private fun mediaItem(url: String, current: PlaybackRequest): MediaItem =
    MediaItem.Builder()
      .setUri(url)
      .setMediaMetadata(
        androidx.media3.common.MediaMetadata.Builder().setTitle(current.title).build(),
      )
      .apply {
        if (current.live) {
          setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(4_000).build(),
          )
        }
      }
      .build()

  private fun handleError(error: PlaybackException) {
    val current = request ?: return
    if (error.isDecodeFailure() || (!played && failures > 0 && error.isVideoBeyondDecoder())) {
      fail(PlaybackCopy.CODEC)
      return
    }
    if (!played && candidate + 1 < current.candidates.size) {
      candidate += 1
      scheduleStart(Reconnect.FIRST_DELAY_MS)
      return
    }
    reconnect(error)
  }

  private fun reconnect(error: PlaybackException?, stalled: Boolean = false) {
    val current = request ?: return
    val status = error?.upstreamStatus()
    failures += 1
    if (!Reconnect.shouldRetry(failures, played, status)) {
      fail(exhausted(error, played, current.live, stalled))
      return
    }
    if (!played) candidate = 0
    if (!current.live) resumeAtMs = player?.currentPosition ?: resumeAtMs
    state = state.copy(reconnecting = true, error = null)
    scheduleStart(Reconnect.delayMs(failures))
  }

  private fun scheduleStart(waitMs: Long) {
    reopen?.cancel()
    watchdog?.cancel()
    // Stop first: the slot we are about to ask for is the one this connection is still holding.
    player?.stop()
    player?.clearMediaItems()
    reopen = scope.launch {
      delay(waitMs)
      start()
    }
  }

  private fun fail(message: String) {
    reopen?.cancel()
    watchdog?.cancel()
    player?.stop()
    player?.clearMediaItems()
    state = state.copy(loading = false, reconnecting = false, stalled = false, error = message)
  }

  private fun readTracks(tracks: Tracks) {
    val found = mutableListOf<PlayerTrack>()
    val map = mutableMapOf<String, TrackSelectionOverride>()
    var selectedAudio: String? = null
    var selectedText: String? = null

    tracks.groups.forEachIndexed { groupIndex, group ->
      val kind = when (group.type) {
        C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
        C.TRACK_TYPE_TEXT -> TrackKind.SUBTITLE
        else -> return@forEachIndexed
      }
      for (i in 0 until group.length) {
        val format = group.getTrackFormat(i)
        val id = "${if (kind == TrackKind.AUDIO) "a" else "s"}:$groupIndex:$i"
        found += PlayerTrack(
          id = id,
          kind = kind,
          language = format.language,
          label = format.label.orEmpty(),
          codec = codecName(format.sampleMimeType),
          channels = if (format.channelCount == androidx.media3.common.Format.NO_VALUE) 0 else format.channelCount,
          isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
          forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
          supported = group.isTrackSupported(i),
        )
        map[id] = TrackSelectionOverride(group.mediaTrackGroup, i)
        if (group.isTrackSelected(i)) {
          if (kind == TrackKind.AUDIO) selectedAudio = id else selectedText = id
        }
      }
    }

    overrides = map
    val known = state.audioId != null || state.subtitleId != null
    state = state.copy(
      tracks = found,
      audioId = selectedAudio ?: state.audioId,
      subtitleId = selectedText,
    )
    if (!known && found.isNotEmpty()) applyChoice(chooseTracks(found, preferences))
  }

  private fun applyChoice(choice: TrackChoice) {
    if (choice.audioId != null && choice.audioId != state.audioId) select(TrackKind.AUDIO, choice.audioId)
    if (choice.subtitleId != state.subtitleId) select(TrackKind.SUBTITLE, choice.subtitleId)
  }
}

private fun codecName(mimeType: String?): String? = when (mimeType) {
  null -> null
  "audio/mp4a-latm" -> "AAC"
  "audio/ac3" -> "AC-3"
  "audio/eac3", "audio/eac3-joc" -> "E-AC-3"
  "audio/mpeg", "audio/mpeg-L2" -> "MP2"
  "audio/vnd.dts", "audio/vnd.dts.hd" -> "DTS"
  "audio/opus" -> "Opus"
  "audio/flac" -> "FLAC"
  else -> mimeType.substringAfterLast('/').uppercase()
}
