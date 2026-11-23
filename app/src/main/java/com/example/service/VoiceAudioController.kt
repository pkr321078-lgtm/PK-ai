package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.UUID

class VoiceAudioController(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    init {
        initDeviceTts()
    }

    private fun initDeviceTts() {
        try {
            tts = TextToSpeech(appContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                        _audioLevel.value = 0.55f
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _audioLevel.value = 0f
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _audioLevel.value = 0f
                    }
                })
            }
        } catch (_: Throwable) {
            ttsReady = false
        }
    }

    fun isSpeechRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (_: Throwable) {
            false
        }
    }

    fun isDeviceTtsReady(): Boolean = ttsReady

    fun startListening(
        language: AppLanguage,
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            stopSpeaking()
            if (!isSpeechRecognitionAvailable()) {
                onError("Android speech recognition service is unavailable on this device.")
                return@post
            }
            try {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                            _partialTranscript.value = ""
                        }

                        override fun onBeginningOfSpeech() {
                            _isListening.value = true
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1.0f)
                            _audioLevel.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) = Unit

                        override fun onEndOfSpeech() {
                            _isListening.value = false
                            _audioLevel.value = 0f
                        }

                        override fun onError(error: Int) {
                            _isListening.value = false
                            _audioLevel.value = 0f
                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error in speech recognizer."
                                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Tap mic and try again."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                                else -> "Speech recognition stopped (code $error)."
                            }
                            onError(message)
                        }

                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            _audioLevel.value = 0f
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spoken = matches?.firstOrNull().orEmpty().trim()
                            _partialTranscript.value = ""
                            if (spoken.isNotEmpty()) {
                                onFinalResult(spoken)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            _partialTranscript.value = partial
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (t: Throwable) {
                _isListening.value = false
                onError(t.message ?: "Failed to start microphone recognition.")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Throwable) {
            } finally {
                _isListening.value = false
                _audioLevel.value = 0f
            }
        }
    }

    fun playAudioFile(file: File, onCompleted: () -> Unit = {}, onError: (String) -> Unit = {}) {
        mainHandler.post {
            stopSpeaking()
            try {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnPreparedListener {
                        _isSpeaking.value = true
                        _isPaused.value = false
                        _audioLevel.value = 0.72f
                        start()
                    }
                    setOnCompletionListener { mp ->
                        _isSpeaking.value = false
                        _isPaused.value = false
                        _audioLevel.value = 0f
                        mp.release()
                        mediaPlayer = null
                        file.delete()
                        onCompleted()
                    }
                    setOnErrorListener { mp, _, _ ->
                        _isSpeaking.value = false
                        _isPaused.value = false
                        _audioLevel.value = 0f
                        mp.release()
                        mediaPlayer = null
                        file.delete()
                        onError("Audio playback error.")
                        true
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (t: Throwable) {
                _isSpeaking.value = false
                file.delete()
                onError(t.message ?: "Could not play synthesized audio.")
            }
        }
    }

    fun speakWithDeviceTts(text: String, language: AppLanguage, speed: Float) {
        mainHandler.post {
            stopSpeaking()
            val engine = tts
            if (!ttsReady || engine == null) return@post
            try {
                val locale = when (language) {
                    AppLanguage.HINDI, AppLanguage.HINGLISH -> Locale.forLanguageTag("hi-IN")
                    AppLanguage.ENGLISH -> Locale.US
                }
                val langStatus = engine.isLanguageAvailable(locale)
                if (langStatus >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = locale
                } else {
                    engine.language = Locale.US
                }
                engine.setSpeechRate(speed.coerceIn(0.65f, 1.5f))
                engine.speak(
                    text.take(1500),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    UUID.randomUUID().toString()
                )
            } catch (_: Throwable) {
                _isSpeaking.value = false
            }
        }
    }

    fun pauseOrResumePlayback() {
        mainHandler.post {
            val mp = mediaPlayer ?: return@post
            try {
                if (mp.isPlaying) {
                    mp.pause()
                    _isPaused.value = true
                    _audioLevel.value = 0f
                } else if (_isPaused.value) {
                    mp.start()
                    _isPaused.value = false
                    _audioLevel.value = 0.7f
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                mediaPlayer?.let {
                    if (it.isPlaying) it.stop()
                    it.release()
                }
            } catch (_: Throwable) {
            } finally {
                mediaPlayer = null
            }
            try {
                tts?.stop()
            } catch (_: Throwable) {
            }
            _isSpeaking.value = false
            _isPaused.value = false
            _audioLevel.value = 0f
        }
    }

    fun release() {
        stopListening()
        stopSpeaking()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.shutdown()
                tts = null
            } catch (_: Throwable) {
            }
        }
    }
}
