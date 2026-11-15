package com.chiilkkabwa.app.core.voice

internal enum class PreviewResult { IDLE, PLAYING, SPOKEN, BLOCKED, UNAVAILABLE, INTERRUPTED }

internal interface PreviewAudio {
    val ready: Boolean
    fun acquireFocus(onLoss: () -> Unit): Boolean
    fun play(text: String, onFinished: (Boolean) -> Unit): Boolean
    fun stop()
    fun abandonFocus()
}

/** User-initiated preview. All methods and callbacks run on the main thread. */
internal class ParkedVoicePreview(
    private val audio: PreviewAudio,
    private val onResult: (PreviewResult) -> Unit,
) {
    private var nextRequest = 0L
    private var active: Long? = null

    fun play(allowed: Boolean) {
        if (!allowed) {
            cancel()
            onResult(PreviewResult.BLOCKED)
            return
        }
        if (active != null) return
        if (!audio.ready) {
            onResult(PreviewResult.UNAVAILABLE)
            return
        }

        val id = ++nextRequest
        if (!audio.acquireFocus { finish(id, PreviewResult.INTERRUPTED, stop = true) }) {
            onResult(PreviewResult.BLOCKED)
            return
        }
        active = id
        onResult(PreviewResult.PLAYING)
        val started = audio.play("이렇게 안내해 드려요.") { completed ->
            finish(id, if (completed) PreviewResult.SPOKEN else PreviewResult.UNAVAILABLE, stop = false)
        }
        if (!started) finish(id, PreviewResult.UNAVAILABLE, stop = true)
    }

    fun cancel() {
        active?.let { finish(it, PreviewResult.INTERRUPTED, stop = true) }
    }

    private fun finish(id: Long, result: PreviewResult, stop: Boolean) {
        if (active != id) return
        active = null
        if (stop) audio.stop()
        audio.abandonFocus()
        onResult(result)
    }
}
