package com.keeltv.app.playback

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlaybackException

object PlaybackCopy {
  const val NETWORK = "The provider closed the connection before any video arrived."
  const val REFUSED = "The provider refused this stream. It may be offline, or another device may be using the account."
  const val CODEC = "This stream uses a video format this player cannot decode."
  const val GENERIC = "The stream stopped responding."
  const val KEEPS_DROPPING = "This channel keeps dropping out."
  const val NO_AUDIO = "This device cannot decode this stream's audio."

  fun upstreamStatus(status: Int): String = when {
    status == Reconnect.BUSY_SLOT ->
      "Your provider allows one stream at a time, and another one is already open."
    status == 666 || status == 888 ->
      "This channel is not available from your provider right now."
    // Some providers' CDNs answer 511 to every stream request from a VPN or datacentre address,
    // before they even look at the credentials.
    status == 511 -> "Your provider is refusing this network. Turn off any VPN or proxy and try again."
    status == 401 || status == 403 || status == 513 -> "Your provider rejected these credentials."
    status == 404 -> "Your provider no longer has this stream."
    status >= 500 -> "Your provider returned an error ($status)."
    else -> "Your provider refused the stream ($status)."
  }
}

fun Throwable.upstreamStatus(): Int? {
  var cause: Throwable? = this
  var hops = 0
  while (cause != null && hops < 8) {
    if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
    cause = cause.cause
    hops += 1
  }
  return null
}

fun PlaybackException.isDecodeFailure(): Boolean = when (errorCode) {
  PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
  PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
  PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
  PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
  PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
  -> true
  else -> false
}

fun PlaybackException.isVideoBeyondDecoder(): Boolean =
  this is ExoPlaybackException && type == ExoPlaybackException.TYPE_RENDERER &&
    rendererFormatSupport != C.FORMAT_HANDLED && MimeTypes.isVideo(rendererFormat?.sampleMimeType)

fun PlaybackException.describe(): String {
  val status = upstreamStatus()
  if (status != null) return PlaybackCopy.upstreamStatus(status)
  if (isDecodeFailure()) return PlaybackCopy.CODEC
  return when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    -> PlaybackCopy.NETWORK
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackCopy.REFUSED
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlaybackCopy.upstreamStatus(404)
    PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackCopy.GENERIC
    else -> PlaybackCopy.GENERIC
  }
}

fun exhausted(
  error: PlaybackException?,
  played: Boolean,
  live: Boolean,
  stalled: Boolean,
): String {
  val status = error?.upstreamStatus()
  return when {
    status != null -> PlaybackCopy.upstreamStatus(status)
    error?.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackCopy.CODEC
    played && live -> PlaybackCopy.KEEPS_DROPPING
    error != null -> error.describe()
    stalled -> PlaybackCopy.GENERIC
    else -> PlaybackCopy.NETWORK
  }
}
