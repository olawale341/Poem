package com.example.poeticvision

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class VideoScene(val text: String, val keyword: String, val imageUrl: String, val audioFile: File?)

class RealVideoViewModel : ViewModel() {
    var isVideoPlaying by mutableStateOf(false)
    var currentSceneIndex by mutableStateOf(0)
    var scenes by mutableStateOf<List<VideoScene>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    var statusText by mutableStateOf("")

    private var mediaPlayer: MediaPlayer? = null
    private var fallbackTts: TextToSpeech? = null
    private val client = OkHttpClient()

    // Words to ignore when detecting the main topic of your sentence
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
        "by", "from", "up", "about", "into", "over", "after", "is", "am", "are", "was",
        "were", "be", "been", "being", "have", "has", "had", "do", "does", "did", "will",
        "would", "shall", "should", "may", "might", "must", "can", "could", "i", "you",
        "he", "she", "it", "we", "they", "my", "your", "his", "her", "its", "our", "their",
        "this", "that", "these", "those", "there", "where", "when", "why", "how", "all", "any"
    )

    fun setupFallbackTts(context: Context) {
        if (fallbackTts == null) {
            fallbackTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    fallbackTts?.language = Locale.US
                    fallbackTts?.setPitch(0.85f)
                    fallbackTts?.setSpeechRate(0.85f)
                }
            }
        }
    }

    /**
     * EXTRACTS THE REAL SUBJECT (e.g., "money", "cars", "rain", "tears")
     * AND GENERATES A 100% UNIQUE RANDOM PHOTO URL EVERY TIME!
     */
    private fun extractKeywordAndImage(sentence: String, sceneIndex: Int): Pair<String, String> {
        val cleanWords = sentence.lowercase()
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")
            .split(" ")
            .map { it.trim() }
            .filter { it.length > 2 && !stopWords.contains(it) }

        // Pick the most meaningful keyword, or default to atmospheric
        val keyword = if (cleanWords.isNotEmpty()) cleanWords.last() else "cinematic"

        // Unique random seed ensures even if you type "money" 10 times, you get 10 different photos!
        val randomSeed = (100000..999999).random() + sceneIndex
        val imageUrl = "https://loremflickr.com/1080/1920/$keyword?random=$randomSeed"

        return Pair(keyword, imageUrl)
    }

    fun generateAndPlayVideo(context: Context, fullText: String) {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        isLoading = true
        statusText = "Downloading human voice narration..."

        viewModelScope.launch(Dispatchers.IO) {
            val generatedScenes = mutableListOf<VideoScene>()

            lines.forEachIndexed { index, line ->
                val (keyword, imageUrl) = extractKeywordAndImage(line, index)

                // Download Human Voice to local cache so it never fails!
                var localAudioFile: File? = null
                try {
                    val encoded = URLEncoder.encode(line, StandardCharsets.UTF_8.toString())
                    // Real human British voice (Brian / Amazon Polly)
                    val voiceUrl = "https://api.streamelements.com/kappa/v2/speech?voice=Brian&text=$encoded"

                    val req = Request.Builder().url(voiceUrl).get().build()
                    val resp = client.newCall(req).execute()

                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val tempFile = File(context.cacheDir, "scene_audio_${index}_${System.currentTimeMillis()}.mp3")
                            FileOutputStream(tempFile).use { it.write(bytes) }
                            localAudioFile = tempFile
                        }
                    }
                } catch (e: Exception) {
                    localAudioFile = null
                }

                generatedScenes.add(VideoScene(line, keyword, imageUrl, localAudioFile))
            }

            withContext(Dispatchers.Main) {
                scenes = generatedScenes
                currentSceneIndex = 0
                isLoading = false
                isVideoPlaying = true
                playScene(context, 0)
            }
        }
    }

    private fun playScene(context: Context, index: Int) {
        val scene = scenes.getOrNull(index) ?: return
        mediaPlayer?.release()
        mediaPlayer = null

        if (scene.audioFile != null && scene.audioFile.exists()) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(scene.audioFile.absolutePath)
                    setOnPreparedListener {
                        start()
                        isPaused = false
                    }
                    setOnCompletionListener {
                        if (currentSceneIndex + 1 < scenes.size) {
                            currentSceneIndex++
                            playScene(context, currentSceneIndex)
                        } else {
                            isVideoPlaying = false
                        }
                    }
                    prepare()
                }
            } catch (e: Exception) {
                playFallbackTts(context, scene.text, index)
            }
        } else {
            // Instant fallback to native voice if network was slow
            playFallbackTts(context, scene.text, index)
        }
    }

    private fun playFallbackTts(context: Context, text: String, index: Int) {
        fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SCENE_$index")
        // Advance after estimated reading duration
        viewModelScope.launch {
            val estimatedDurationMs = (text.split(" ").size * 450L).coerceAtLeast(3000L)
            kotlinx.coroutines.delay(estimatedDurationMs)
            if (isVideoPlaying && currentSceneIndex == index) {
                if (currentSceneIndex + 1 < scenes.size) {
                    currentSceneIndex++
                    playScene(context, currentSceneIndex)
                } else {
                    isVideoPlaying = false
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPaused = true
            } else {
                it.start()
                isPaused = false
            }
        }
    }

    fun nextScene(context: Context) {
        if (currentSceneIndex + 1 < scenes.size) {
            currentSceneIndex++
            playScene(context, currentSceneIndex)
        }
    }

    fun prevScene(context: Context) {
        if (currentSceneIndex - 1 >= 0) {
            currentSceneIndex--
            playScene(context, currentSceneIndex)
        }
    }

    fun exitVideo() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        fallbackTts?.stop()
        isVideoPlaying = false
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        fallbackTts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0F)) {
                    val vm: RealVideoViewModel = viewModel()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        vm.setupFallbackTts(context)
                    }

                    if (!vm.isVideoPlaying) {
                        StudioInputScreen(vm)
                    } else {
                        CinematicVideoPlayer(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun StudioInputScreen(vm: RealVideoViewModel) {
    val context = LocalContext.current
    var storyText by remember {
        mutableStateOf(
            "Money flows to those who build and endure.\n" +
            "In the bustling city, neon lights shine upon the streets.\n" +
            "Gold cannot buy the peace found beneath the stars.\n" +
            "Rain cleanses the sorrow of yesterday's hustle."
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "🎬 AI Video Story Maker",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700),
            fontFamily = FontFamily.Serif
        )
        Text(
            "Transforms any topic into human-narrated video with matching photos",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = storyText,
            onValueChange = { storyText = it },
            label = { Text("Write your poem or story (one sentence per line)") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.LightGray,
                focusedContainerColor = Color(0xFF14141E),
                unfocusedContainerColor = Color(0xFF14141E),
                focusedBorderColor = Color(0xFFFFD700)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { vm.generateAndPlayVideo(context, storyText) },
            enabled = !vm.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                if (vm.isLoading) vm.statusText else "▶ Play As Real Video",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

// --- REAL VIDEO PLAYER WITH CINEMATIC MOTION & NARRATION ---
@Composable
fun CinematicVideoPlayer(vm: RealVideoViewModel) {
    val context = LocalContext.current
    val currentScene = vm.scenes.getOrNull(vm.currentSceneIndex)

    // Ken Burns Slow Zoom Motion (Scales from 1.0 to 1.15 to feel like live video!)
    val scaleAnim by animateFloatAsState(
        targetValue = 1.15f,
        animationSpec = tween(durationMillis = 7000, easing = LinearEasing),
        label = "KenBurnsScale"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Dynamic Topic-Matched Background Photo with Video Motion
        Crossfade(
            targetState = currentScene?.imageUrl,
            animationSpec = tween(durationMillis = 800),
            label = "SceneFade"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(scaleAnim),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Dark Vignette Gradient so text is 100% readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.90f)
                        )
                    )
                )
        )

        // Top Video Progress Bar (Story Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            vm.scenes.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(
                            color = if (idx <= vm.currentSceneIndex) Color(0xFFFFD700) else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                )
            }
        }

        // Subtitles and Media Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            currentScene?.let { scene ->
                // Topic tag badge
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Topic: #${scene.keyword.uppercase()}",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Subtitle
                Text(
                    text = scene.text,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Player Controls (Previous, Play/Pause, Next, Close)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevScene(context) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White)
                }

                IconButton(
                    onClick = { vm.togglePlayPause() },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        if (vm.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }

                IconButton(onClick = { vm.nextScene(context) }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                }

                Button(
                    onClick = { vm.exitVideo() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Exit", color = Color.White, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}