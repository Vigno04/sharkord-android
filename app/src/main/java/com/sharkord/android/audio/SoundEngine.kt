package com.sharkord.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

enum class SoundType(val value: String) {
    MESSAGE_RECEIVED("message_received"),
    MESSAGE_SENT("message_sent"),
    SERVER_DISCONNECTED("server_disconnected"),

    OWN_USER_LEFT_VOICE_CHANNEL("own_user_left_voice_channel"),
    OWN_USER_JOINED_VOICE_CHANNEL("own_user_joined_voice_channel"),
    OWN_USER_MUTED_MIC("own_user_muted_mic"),
    OWN_USER_UNMUTED_MIC("own_user_unmuted_mic"),
    OWN_USER_MUTED_SOUND("own_user_muted_sound"),
    OWN_USER_UNMUTED_SOUND("own_user_unmuted_sound"),
    OWN_USER_STARTED_WEBCAM("own_user_started_webcam"),
    OWN_USER_STOPPED_WEBCAM("own_user_stopped_webcam"),
    OWN_USER_STARTED_SCREENSHARE("own_user_started_screenshare"),
    OWN_USER_STOPPED_SCREENSHARE("own_user_stopped_screenshare"),

    REMOTE_USER_JOINED_VOICE_CHANNEL("remote_user_joined_voice_channel"),
    REMOTE_USER_LEFT_VOICE_CHANNEL("remote_user_left_voice_channel"),
    REMOTE_USER_STARTED_SCREENSHARE("remote_user_started_screenshare"),
    REMOTE_USER_STOPPED_SCREENSHARE("remote_user_stopped_screenshare")
}

// ---------------------------------------------------------
// Web Audio API Emulation DSL
// ---------------------------------------------------------

sealed class ParamEvent(val value: Float, val time: Float) {
    class SetValue(value: Float, time: Float) : ParamEvent(value, time)
    class ExponentialRamp(value: Float, time: Float) : ParamEvent(value, time)
}

class AudioParam(initialValue: Float) {
    private val events = mutableListOf<ParamEvent>()

    init {
        events.add(ParamEvent.SetValue(initialValue, 0f))
    }

    fun setValueAtTime(value: Number, time: Number) {
        events.add(ParamEvent.SetValue(value.toFloat(), time.toFloat()))
        events.sortBy { it.time }
    }

    fun exponentialRampToValueAtTime(value: Number, time: Number) {
        events.add(ParamEvent.ExponentialRamp(value.toFloat(), time.toFloat()))
        events.sortBy { it.time }
    }

    fun getValueAtTime(t: Float): Float {
        var prevEvent = events.firstOrNull() ?: return 0f
        if (t <= prevEvent.time) return prevEvent.value

        for (i in 1 until events.size) {
            val currEvent = events[i]
            if (t <= currEvent.time) {
                return when (currEvent) {
                    is ParamEvent.SetValue -> prevEvent.value
                    is ParamEvent.ExponentialRamp -> {
                        val t0 = prevEvent.time
                        val t1 = currEvent.time
                        val v0 = prevEvent.value
                        val v1 = currEvent.value

                        if (v0 <= 0f) return v1
                        if (t1 == t0) return v1

                        v0 * (v1 / v0).pow((t - t0) / (t1 - t0))
                    }
                }
            }
            prevEvent = currEvent
        }
        return prevEvent.value
    }
}

class GainNode(initialGain: Float) {
    val gain = AudioParam(initialGain)
}

class OscNode(val type: String, initialFreq: Float) {
    val frequency = AudioParam(initialFreq)
    var connectedGain: GainNode? = null
    var startTime: Float = 0f
    var stopTime: Float = 0f

    fun connect(gain: GainNode): GainNode {
        connectedGain = gain
        return gain
    }

    fun start(time: Number = 0f) {
        startTime = time.toFloat()
    }

    fun stop(time: Number = 0f) {
        stopTime = time.toFloat()
    }
}

class SynthContext {
    val SAMPLE_RATE = 44100
    val SOUNDS_VOLUME = 2f

    val oscillators = mutableListOf<OscNode>()

    class Destination
    val destination = Destination()

    fun now(): Float = 0f

    fun createOsc(type: String, freq: Number): OscNode {
        val osc = OscNode(type, freq.toFloat())
        oscillators.add(osc)
        return osc
    }

    fun createGain(value: Number = 1f): GainNode {
        val gain = GainNode(value.toFloat())
        gain.gain.setValueAtTime(value.toFloat() * SOUNDS_VOLUME, now())
        return gain
    }

    fun OscNode.connect(dest: Destination) {
        // Just syntactic sugar for ending the chain
    }

    fun GainNode.connect(dest: Destination) {
        // Just syntactic sugar for ending the chain
    }

    fun render(): ShortArray {
        var maxTime = 0f
        for (osc in oscillators) {
            if (osc.stopTime > maxTime) maxTime = osc.stopTime
        }
        if (maxTime <= 0f) return ShortArray(0)

        val totalSamples = (maxTime * SAMPLE_RATE).toInt() + 1
        val buffer = FloatArray(totalSamples)

        for (osc in oscillators) {
            var phase = 0.0
            val startSample = (osc.startTime * SAMPLE_RATE).toInt().coerceAtLeast(0)
            val stopSample = (osc.stopTime * SAMPLE_RATE).toInt().coerceAtMost(totalSamples - 1)

            for (i in startSample..stopSample) {
                val t = i.toFloat() / SAMPLE_RATE
                val freq = osc.frequency.getValueAtTime(t)
                val gain = osc.connectedGain?.gain?.getValueAtTime(t) ?: 1f

                val waveVal = if (osc.type == "sine") {
                    sin(phase).toFloat()
                } else if (osc.type == "triangle") {
                    val p = phase / (2 * PI)
                    (2 * abs(2 * (p - floor(p + 0.5))) - 1).toFloat()
                } else {
                    0f
                }

                buffer[i] += waveVal * gain
                phase += 2 * PI * freq / SAMPLE_RATE
            }
        }

        var maxAmp = 0f
        for (v in buffer) {
            val absV = kotlin.math.abs(v)
            if (absV > maxAmp) maxAmp = absV
        }
        val scale = if (maxAmp > 0f) 0.95f / maxAmp else 1f

        val paddingSamples = (0.05f * SAMPLE_RATE).toInt() // 50ms silence
        val shortBuffer = ShortArray(totalSamples + paddingSamples)
        for (i in buffer.indices) {
            var v = buffer[i] * scale
            if (v > 1f) v = 1f
            if (v < -1f) v = -1f
            shortBuffer[i + paddingSamples] = (v * 32767).toInt().toShort()
        }
        return shortBuffer
    }
}

// ---------------------------------------------------------
// Sound Engine Definition & Logic
// ---------------------------------------------------------

object SoundEngine {

    data class Note(val freq: Number, val gain: Number, val delay: Number = 0f)

    private fun SynthContext.sfxMessageReceived() {
        val osc = createOsc("sine", 600)
        val gain = createGain(0.05f)

        gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.05f)

        osc.connect(gain).connect(destination)
        osc.start()
        osc.stop(now() + 0.05f)
    }

    private fun SynthContext.sfxMessageSent() {
        val osc = createOsc("sine", 750)
        val gain = createGain(0.04f)

        gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.04f)

        osc.connect(gain).connect(destination)
        osc.start()
        osc.stop(now() + 0.04f)
    }

    private fun SynthContext.sfxServerDisconnected() {
        val notes = listOf(
            Note(988, 0.115f, 0f),
            Note(784, 0.108f, 0.09f),
            Note(659, 0.106f, 0.18f),
            Note(523, 0.12f, 0.27f)
        )

        notes.forEachIndexed { index, (freq, g, delay) ->
            val startAt = now() + delay.toFloat()
            val endAt = startAt + if (index == notes.size - 1) 0.24f else 0.18f
            val osc = createOsc("sine", freq)
            val gain = createGain(g)

            gain.gain.setValueAtTime(g.toFloat() * SOUNDS_VOLUME, startAt)
            gain.gain.exponentialRampToValueAtTime(0.0001f, endAt)

            if (index == notes.size - 1) {
                osc.frequency.exponentialRampToValueAtTime(440, endAt)

                val harmonicOsc = createOsc("triangle", 784)
                val harmonicGain = createGain(0.05f)

                harmonicGain.gain.exponentialRampToValueAtTime(0.0001f, endAt)

                harmonicOsc.connect(harmonicGain).connect(destination)
                harmonicOsc.start(startAt + 0.02f)
                harmonicOsc.stop(endAt)
            }

            osc.connect(gain).connect(destination)
            osc.start(startAt)
            osc.stop(endAt)
        }
    }

    private fun SynthContext.sfxOwnUserJoinedVoiceChannel() {
        val chord1 = listOf(
            Note(523, 0.09f),
            Note(659, 0.07f),
            Note(784, 0.06f)
        )

        chord1.forEach { (freq, g) ->
            val osc = createOsc("sine", freq)
            val gain = createGain(g)

            gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.25f)

            osc.connect(gain).connect(destination)
            osc.start()
            osc.stop(now() + 0.25f)
        }

        val chord2 = listOf(
            Note(1046, 0.04f),
            Note(1318, 0.03f)
        )

        chord2.forEach { (freq, g) ->
            val osc = createOsc("triangle", freq)
            val gain = createGain(g)

            gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.3f)

            osc.connect(gain).connect(destination)
            osc.start(now() + 0.08f)
            osc.stop(now() + 0.3f)
        }
    }

    private fun SynthContext.sfxOwnUserLeftVoiceChannel() {
        val chord1 = listOf(
            Note(440, 0.09f),
            Note(523, 0.07f),
            Note(659, 0.06f)
        )

        chord1.forEach { (freq, g) ->
            val osc = createOsc("sine", freq)
            val gain = createGain(g)

            gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.3f)

            osc.connect(gain).connect(destination)
            osc.start()
            osc.stop(now() + 0.3f)
        }

        val osc2 = createOsc("triangle", 880)
        val gain2 = createGain(0.04f)

        gain2.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.25f)

        osc2.connect(gain2).connect(destination)
        osc2.start(now() + 0.05f)
        osc2.stop(now() + 0.3f)
    }

    private fun SynthContext.sfxOwnUserMutedMic() {
        val osc = createOsc("sine", 350)
        val gain = createGain(0.05f)

        gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.06f)

        osc.connect(gain).connect(destination)
        osc.start()
        osc.stop(now() + 0.06f)
    }

    private fun SynthContext.sfxOwnUserUnmutedMic() {
        val osc = createOsc("sine", 500)
        val gain = createGain(0.05f)

        gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.06f)

        osc.connect(gain).connect(destination)
        osc.start()
        osc.stop(now() + 0.06f)
    }

    private fun SynthContext.sfxOwnUserMutedSound() {
        val osc = createOsc("sine", 450)
        val gain = createGain(0.05f)

        gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.06f)

        osc.connect(gain).connect(destination)
        osc.start()
        osc.stop(now() + 0.06f)
    }

    private fun SynthContext.sfxOwnUserUnmutedSound() {
        val osc = createOsc("sine", 650)
        val gain = createGain(0.05f)

        gain.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.06f)

        osc.connect(gain).connect(destination)
        osc.start()
        osc.stop(now() + 0.06f)
    }

    private fun SynthContext.sfxOwnUserStartedWebcam() {
        val osc1 = createOsc("sine", 700)
        val gain1 = createGain(0.07f)

        gain1.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.12f)

        osc1.connect(gain1).connect(destination)
        osc1.start()
        osc1.stop(now() + 0.12f)

        val osc2 = createOsc("sine", 900)
        val gain2 = createGain(0.04f)

        gain2.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.1f)

        osc2.connect(gain2).connect(destination)
        osc2.start(now() + 0.04f)
        osc2.stop(now() + 0.12f)
    }

    private fun SynthContext.sfxOwnUserStoppedWebcam() {
        val osc1 = createOsc("sine", 700)
        val gain1 = createGain(0.07f)

        osc1.frequency.exponentialRampToValueAtTime(500, now() + 0.12f)
        gain1.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.14f)

        osc1.connect(gain1).connect(destination)
        osc1.start()
        osc1.stop(now() + 0.14f)
    }

    private fun SynthContext.sfxOwnUserStartedScreenshare() {
        val pulses = listOf(
            Note(600, 0f, 0f),
            Note(800, 0f, 0.06f),
            Note(1000, 0f, 0.12f)
        )

        pulses.forEach { (freq, _, delay) ->
            val t = now() + delay.toFloat()
            val osc = createOsc("sine", freq)
            val gain = createGain(0.08f)

            gain.gain.exponentialRampToValueAtTime(0.0001f, t + 0.1f)

            osc.connect(gain).connect(destination)
            osc.start(t)
            osc.stop(t + 0.1f)
        }

        val osc2 = createOsc("triangle", 1200)
        val gain2 = createGain(0.03f)

        gain2.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.2f)

        osc2.connect(gain2).connect(destination)
        osc2.start(now() + 0.08f)
        osc2.stop(now() + 0.22f)
    }

    private fun SynthContext.sfxOwnUserStoppedScreenshare() {
        val osc1 = createOsc("sine", 900)
        val gain1 = createGain(0.08f)

        osc1.frequency.exponentialRampToValueAtTime(550, now() + 0.18f)
        gain1.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.2f)

        osc1.connect(gain1).connect(destination)
        osc1.start()
        osc1.stop(now() + 0.2f)

        val osc2 = createOsc("triangle", 1100)
        val gain2 = createGain(0.03f)

        osc2.frequency.exponentialRampToValueAtTime(700, now() + 0.18f)
        gain2.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.2f)

        osc2.connect(gain2).connect(destination)
        osc2.start(now() + 0.05f)
        osc2.stop(now() + 0.2f)
    }

    private fun SynthContext.sfxRemoteUserJoinedVoiceChannel() {
        val tones = listOf(
            Note(587, 0.06f, 0f),
            Note(740, 0.05f, 0.06f),
            Note(880, 0.04f, 0.12f)
        )

        tones.forEach { (freq, g, delay) ->
            val t = now() + delay.toFloat()
            val osc = createOsc("sine", freq)
            val gain = createGain(g)

            gain.gain.exponentialRampToValueAtTime(0.0001f, t + 0.2f)

            osc.connect(gain).connect(destination)
            osc.start(t)
            osc.stop(t + 0.2f)
        }
    }

    private fun SynthContext.sfxRemoteUserLeftVoiceChannel() {
        val tones = listOf(
            Note(659, 0.06f, 0f),
            Note(523, 0.05f, 0.06f),
            Note(440, 0.04f, 0.12f)
        )

        tones.forEach { (freq, g, delay) ->
            val t = now() + delay.toFloat()
            val osc = createOsc("sine", freq)
            val gain = createGain(g)

            gain.gain.exponentialRampToValueAtTime(0.0001f, t + 0.2f)

            osc.connect(gain).connect(destination)
            osc.start(t)
            osc.stop(t + 0.2f)
        }
    }

    private fun SynthContext.sfxRemoteUserStartedScreenshare() {
        val pulses = listOf(
            Note(600, 0f, 0f),
            Note(800, 0f, 0.06f),
            Note(1000, 0f, 0.12f)
        )

        pulses.forEach { (freq, _, delay) ->
            val t = now() + delay.toFloat()
            val osc = createOsc("sine", freq)
            val gain = createGain(0.06f)

            gain.gain.exponentialRampToValueAtTime(0.0001f, t + 0.1f)

            osc.connect(gain).connect(destination)
            osc.start(t)
            osc.stop(t + 0.1f)
        }

        val osc2 = createOsc("triangle", 1200)
        val gain2 = createGain(0.02f)

        gain2.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.2f)

        osc2.connect(gain2).connect(destination)
        osc2.start(now() + 0.08f)
        osc2.stop(now() + 0.22f)
    }

    private fun SynthContext.sfxRemoteUserStoppedScreenshare() {
        val osc1 = createOsc("sine", 900)
        val gain1 = createGain(0.06f)

        osc1.frequency.exponentialRampToValueAtTime(550, now() + 0.18f)
        gain1.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.2f)

        osc1.connect(gain1).connect(destination)
        osc1.start()
        osc1.stop(now() + 0.2f)

        val osc2 = createOsc("triangle", 1100)
        val gain2 = createGain(0.02f)

        osc2.frequency.exponentialRampToValueAtTime(700, now() + 0.18f)
        gain2.gain.exponentialRampToValueAtTime(0.0001f, now() + 0.2f)

        osc2.connect(gain2).connect(destination)
        osc2.start(now() + 0.05f)
        osc2.stop(now() + 0.2f)
    }

    @Suppress("OPT_IN_USAGE")
    fun playSound(type: SoundType) {
        GlobalScope.launch(Dispatchers.Default) {
            val ctx = SynthContext()
            ctx.apply {
                when (type) {
                    SoundType.MESSAGE_RECEIVED -> sfxMessageReceived()
                    SoundType.MESSAGE_SENT -> sfxMessageSent()
                    SoundType.SERVER_DISCONNECTED -> sfxServerDisconnected()
                    SoundType.OWN_USER_JOINED_VOICE_CHANNEL -> sfxOwnUserJoinedVoiceChannel()
                    SoundType.OWN_USER_LEFT_VOICE_CHANNEL -> sfxOwnUserLeftVoiceChannel()
                    SoundType.OWN_USER_MUTED_MIC -> sfxOwnUserMutedMic()
                    SoundType.OWN_USER_UNMUTED_MIC -> sfxOwnUserUnmutedMic()
                    SoundType.OWN_USER_MUTED_SOUND -> sfxOwnUserMutedSound()
                    SoundType.OWN_USER_UNMUTED_SOUND -> sfxOwnUserUnmutedSound()
                    SoundType.OWN_USER_STARTED_WEBCAM -> sfxOwnUserStartedWebcam()
                    SoundType.OWN_USER_STOPPED_WEBCAM -> sfxOwnUserStoppedWebcam()
                    SoundType.OWN_USER_STARTED_SCREENSHARE -> sfxOwnUserStartedScreenshare()
                    SoundType.OWN_USER_STOPPED_SCREENSHARE -> sfxOwnUserStoppedScreenshare()
                    SoundType.REMOTE_USER_JOINED_VOICE_CHANNEL -> sfxRemoteUserJoinedVoiceChannel()
                    SoundType.REMOTE_USER_LEFT_VOICE_CHANNEL -> sfxRemoteUserLeftVoiceChannel()
                    SoundType.REMOTE_USER_STARTED_SCREENSHARE -> sfxRemoteUserStartedScreenshare()
                    SoundType.REMOTE_USER_STOPPED_SCREENSHARE -> sfxRemoteUserStoppedScreenshare()
                }
            }

            val pcmData = ctx.render()
            if (pcmData.isEmpty()) return@launch

            try {
                val context = com.sharkord.android.data.network.SharkordClient.applicationContext
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                
                val usageAttribute = if (audioManager.mode == android.media.AudioManager.MODE_IN_COMMUNICATION) {
                    AudioAttributes.USAGE_VOICE_COMMUNICATION
                } else {
                    AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(usageAttribute)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ctx.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setBufferSizeInBytes(pcmData.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcmData, 0, pcmData.size)
                track.setVolume(AudioTrack.getMaxVolume())
                track.play()

                // Vibrate
                try {
                    val vibrator = com.sharkord.android.data.network.SharkordClient.applicationContext.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(50)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Release the track after playing
                Thread.sleep((pcmData.size.toFloat() / ctx.SAMPLE_RATE * 1000).toLong() + 100)
                track.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
