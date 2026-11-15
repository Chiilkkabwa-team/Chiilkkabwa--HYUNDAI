package com.chiilkkabwa.app.core.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class ParkedVoicePreviewTest {
    private class FakeAudio : PreviewAudio {
        override var ready = true
        var grantsFocus = true
        var startsPlayback = true
        var abandonCount = 0
        var stopCount = 0
        val completions = mutableListOf<(Boolean) -> Unit>()
        var onLoss: (() -> Unit)? = null

        override fun acquireFocus(onLoss: () -> Unit): Boolean {
            this.onLoss = onLoss
            return grantsFocus
        }

        override fun play(text: String, onFinished: (Boolean) -> Unit): Boolean {
            completions += onFinished
            return startsPlayback
        }

        override fun stop() { stopCount++ }
        override fun abandonFocus() { abandonCount++ }
    }

    private val audio = FakeAudio()
    private val results = mutableListOf<PreviewResult>()
    private val preview = ParkedVoicePreview(audio) { results += it }

    @Test fun disallowedOrDeniedFocusNeverStartsSpeech() {
        preview.play(allowed = false)
        audio.grantsFocus = false
        preview.play(allowed = true)
        assertEquals(listOf(PreviewResult.BLOCKED, PreviewResult.BLOCKED), results)
        assertEquals(0, audio.completions.size)
    }

    @Test fun oneRequestAndCompletionReleaseFocus() {
        preview.play(allowed = true)
        preview.play(allowed = true)
        assertEquals(1, audio.completions.size)
        audio.completions.single()(true)
        assertEquals(listOf(PreviewResult.PLAYING, PreviewResult.SPOKEN), results)
        assertEquals(1, audio.abandonCount)
    }

    @Test fun focusLossAndCancelDiscardLateCallbacks() {
        preview.play(allowed = true)
        val firstLoss = audio.onLoss
        firstLoss?.invoke()
        preview.play(allowed = true)
        firstLoss?.invoke()
        audio.completions[0](true)
        assertEquals(PreviewResult.PLAYING, results.last())
        preview.cancel()
        audio.completions[1](true)
        assertEquals(PreviewResult.INTERRUPTED, results.last())
        assertEquals(2, audio.stopCount)
        assertEquals(2, audio.abandonCount)
    }

    @Test fun unreadyOrFailedPlaybackNeverReportsCompletion() {
        audio.ready = false
        preview.play(allowed = true)
        audio.ready = true
        audio.startsPlayback = false
        preview.play(allowed = true)
        assertEquals(listOf(PreviewResult.UNAVAILABLE, PreviewResult.PLAYING, PreviewResult.UNAVAILABLE), results)
        assertEquals(1, audio.stopCount)
        assertEquals(1, audio.abandonCount)
    }
}
