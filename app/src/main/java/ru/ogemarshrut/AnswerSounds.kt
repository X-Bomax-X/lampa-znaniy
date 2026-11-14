package ru.ogemarshrut

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

internal class AnswerSounds(private val context: Context) {
    private var player: MediaPlayer? = null
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(outcome: AnswerOutcome) {
        stop()
        val resource = when (outcome) {
            AnswerOutcome.CORRECT -> R.raw.answer_correct
            AnswerOutcome.INCORRECT -> R.raw.answer_incorrect
        }
        try {
            val sound = MediaPlayer.create(context, resource, attributes, 0) ?: return
            player = sound
            sound.setOnCompletionListener { if (player === it) stop() }
            sound.setOnErrorListener { media, _, _ ->
                if (player === media) stop()
                true
            }
            sound.start()
        } catch (_: IllegalStateException) {
            stop()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
