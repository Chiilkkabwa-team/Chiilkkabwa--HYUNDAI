package com.chiilkkabwa.app.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.chiilkkabwa.app.core.voice.PreviewAudio
import java.util.Locale

/** Android Mock preview using an installed Korean offline voice. */
internal class AndroidPreviewAudio(context: Context) : PreviewAudio {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var focus: AudioFocusRequest? = null
    private var requestId = 0L
    private var activeId: String? = null
    private var callback: ((Boolean) -> Unit)? = null
    private var closed = false
    private val timeout = Runnable {
        val id = activeId ?: return@Runnable
        tts.stop()
        complete(id, false)
    }

    override var ready = false
        private set

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        handler.post { initialize(status) }
    }

    private fun initialize(status: Int) {
        if (closed || status != TextToSpeech.SUCCESS) return
        val korean = tts.voices.orEmpty().filter {
            it.locale.language == Locale.KOREAN.language &&
                !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
        }.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale == Locale.KOREA }
            .thenByDescending { it.quality }
            .thenBy { it.name }).firstOrNull() ?: return

        if (tts.setVoice(korean) != TextToSpeech.SUCCESS) return
        if (tts.setAudioAttributes(attributes) != TextToSpeech.SUCCESS) return
        ready = tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = dispatch(utteranceId, true)
            override fun onError(utteranceId: String) = dispatch(utteranceId, false)
            override fun onStop(utteranceId: String, interrupted: Boolean) = dispatch(utteranceId, false)
        }) == TextToSpeech.SUCCESS
    }

    override fun acquireFocus(onLoss: () -> Unit): Boolean {
        val audioManager = manager ?: return false
        lateinit var request: AudioFocusRequest
        request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (change < 0 && focus === request) onLoss()
            }, handler)
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return true
    }

    override fun play(text: String, onFinished: (Boolean) -> Unit): Boolean {
        val id = (++requestId).toString()
        activeId = id
        callback = onFinished
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) return false
        handler.postDelayed(timeout, 4_000L)
        return true
    }

    private fun dispatch(id: String, completed: Boolean) {
        handler.post { complete(id, completed) }
    }

    private fun complete(id: String, completed: Boolean) {
        if (activeId != id) return
        handler.removeCallbacks(timeout)
        activeId = null
        val finished = callback
        callback = null
        finished?.invoke(completed)
    }

    override fun stop() {
        handler.removeCallbacks(timeout)
        activeId = null
        callback = null
        tts.stop()
    }

    override fun abandonFocus() {
        val request = focus ?: return
        focus = null
        manager?.abandonAudioFocusRequest(request)
    }

    fun close() {
        closed = true
        ready = false
        stop()
        abandonFocus()
        tts.shutdown()
    }
}
