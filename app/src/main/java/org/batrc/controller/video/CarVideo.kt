package org.batrc.controller.video

import android.content.Context
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.batrc.controller.protocol.Protocol

sealed interface VideoStatus {
    data object Idle : VideoStatus
    data object Connecting : VideoStatus
    data object Playing : VideoStatus
    data class Error(val detail: String) : VideoStatus
}

/**
 * Vídeo en directo de la cámara del vehículo (RTSP) con el mínimo búfer posible.
 * Si la conexión falla se reintenta sola cada dos segundos.
 */
@OptIn(markerClass = [UnstableApi::class])
@Composable
fun CarVideo(
    network: Network,
    overTcp: Boolean,
    onStatus: (VideoStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val report by rememberUpdatedState(onStatus)
    val player = remember(network, overTcp) { createLowLatencyPlayer(context.applicationContext) }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(player, lifecycleOwner) {
        val handler = Handler(Looper.getMainLooper())

        fun load() {
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(overTcp)
                .setTimeoutMs(4000)
                // Las conexiones RTSP salen siempre por el Wi-Fi del vehículo.
                .setSocketFactory(network.socketFactory)
                .createMediaSource(MediaItem.fromUri(Protocol.RTSP_URL))
            player.setMediaSource(source)
            player.prepare()
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) report(VideoStatus.Connecting)
                if (playbackState == Player.STATE_READY) report(VideoStatus.Playing)
                if (playbackState == Player.STATE_ENDED) handler.postDelayed({ load() }, RETRY_MS)
            }

            override fun onPlayerError(error: PlaybackException) {
                report(VideoStatus.Error(error.errorCodeName))
                handler.postDelayed({ load() }, RETRY_MS)
            }
        }

        // El vídeo solo corre con la app visible. Compose deja de recomponer
        // cuando la actividad se detiene, así que se controla aquí directamente.
        // Al registrarse, el observador recibe ON_START si la app ya está visible.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    report(VideoStatus.Connecting)
                    load()
                }

                Lifecycle.Event.ON_STOP -> {
                    handler.removeCallbacksAndMessages(null)
                    player.stop()
                }

                else -> Unit
            }
        }

        player.addListener(listener)
        player.playWhenReady = true
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            handler.removeCallbacksAndMessages(null)
            player.removeListener(listener)
            player.release()
            report(VideoStatus.Idle)
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> view.player = player },
    )
}

private const val RETRY_MS = 2000L

@OptIn(markerClass = [UnstableApi::class])
private fun createLowLatencyPlayer(context: Context): ExoPlayer {
    // Búfer mínimo: empieza a reproducir en cuanto llega el primer fotograma.
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 250,
            /* maxBufferMs = */ 1000,
            /* bufferForPlaybackMs = */ 0,
            /* bufferForPlaybackAfterRebufferMs = */ 0,
        )
        .build()
    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .build()
}
