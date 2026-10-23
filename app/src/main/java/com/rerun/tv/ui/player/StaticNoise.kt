package com.rerun.tv.ui.player

import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

/** Full-screen TV snow: a tiny random-gray bitmap, regenerated every frame and scaled up. */
@Composable
fun StaticNoise(modifier: Modifier = Modifier) {
    val w = 160
    val h = 90
    val bitmap = remember { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    val pixels = remember { IntArray(w * h) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { frame++ }
    }
    Canvas(modifier) {
        frame // read so every frame redraws
        val rnd = Random.Default
        for (i in pixels.indices) {
            val v = rnd.nextInt(256)
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        drawImage(
            bitmap.asImageBitmap(),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/** A short burst of white noise for channel changes. Fire-and-forget. */
object NoiseBurst {
    private const val RATE = 22_050
    private val samples: ShortArray by lazy {
        val n = RATE * 150 / 1000
        ShortArray(n) { i ->
            val fade = if (i > n - 600) (n - i) / 600f else 1f
            (Random.nextInt(-6000, 6000) * fade).toInt().toShort()
        }
    }

    fun play() {
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            track.write(samples, 0, samples.size)
            track.setNotificationMarkerPosition(samples.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) = t.release()
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }
}
