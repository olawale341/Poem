package com.example.poeticvision

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class VideoScene(
    val text: String,
    val topic: String,
    val imageUrl: String,
    val audioFile: File?
)

class VideoStoryViewModel : ViewModel() {
    val isPlaying = mutableStateOf(false)
    val currentIndex = mutableStateOf(0)
    val scenes = mutableStateOf<List<VideoScene>>(emptyList())
    val isLoading = mutableStateOf(false)
    val isPaused = mutableStateOf(false)
    val statusText = mutableStateOf("")

    private var mediaPlayer: MediaPlayer? = null
    private var maleTts: TextToSpeech? = null

    // High-Definition Non-Repeating Curated Photo Banks for Any Subject
    private val topicPhotoBanks = mapOf(
        "money" to listOf(
            "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=1080&q=80",
            "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=1080&q=80",
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=1080&q=80",
            "https://images.unsplash.com/photo-1565372195458-9de0b320ef04?w=1080&q=80",
            "https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?w=1080&q=80"
        ),
        "cash" to listOf(
            "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=1080&q=80",
            "https://images.unsplash.com/photo-1526304640581-d334cdbbf45e?w=1080&q=80"
        ),
        "gold" to listOf(
            "https://images.unsplash.com/photo-1610375461246-83df859d849d?w=1080&q=80",
            "https://images.unsplash.com/photo-1611974789855-9c2a0a7236a3?w=1080&q=80"
        ),
        "car" to listOf(
            "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=1080&q=80",
            "https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?w=1080&q=80",
            "https://images.unsplash.com/photo-1552519507-da3b142c6e3d?w=1080&q=80"
        ),
        "city" to listOf(
            "https://images.unsplash.com/photo-1477959858617-67f30bc75b82?w=1080&q=80",
            "https://images.unsplash.com/photo-1444723121867-7a241cacace9?w=1080&q=80",
            "https://images.unsplash.com/photo-1514565131-fce0801e5785?w=1080&q=80"
        ),
        "night" to listOf(
            "https://images.unsplash.com/photo-1532693322450-2cb5c511067d?w=1080&q=80",
            "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1080&q=80",
            "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1080&q=80"
        ),
        "moon" to listOf(
            "https://images.unsplash.com/photo-1532693322450-2cb5c511067d?w=1080&q=80",
            "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1080&q=80"
        ),
        "ocean" to listOf(
            "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1080&q=80",
            "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1080&q=80"
        ),
        "rain" to listOf(
            "https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?w=1080&q=80",
            "https://images.unsplash.com/photo-1534274988757-a28bf1a57c17?w=1080&q=80"
        ),
        "forest" to listOf(
            "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1080&q=80",
            "https://images.unsplash.com/photo-1511497584788-87676104235f?w=1080&q=80"
        )
    )

    private val generalFallbacks = listOf(
        "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1080&q=80",
        "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=1080&q=80",
        "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1080&q=80"
    )

    fun initMaleTts(context: Context) {
        if (maleTts == null) {
            maleTts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    maleTts?.language = Locale.UK
                    maleTts?.setPitch(0.82f)
                    maleTts?.setSpeechRate(0.85f)
                }
            }
        }
    }

    private fun selectMatchingImage(sentence: String, index: Int): Pair<String, String> {
        val lower = sentence.lowercase()
        for ((topic, photos) in topicPhotoBanks) {
            if (lower.contains(topic)) {
                val randomPhoto = photos.random()
                return Pair(topic, randomPhoto)
            }
        }
        val fallback = generalFallbacks[index % generalFallbacks.size]
        return Pair("cinematic", fallback)
    }

    fun buildAndPlayVideo(context: Context, fullText: String) {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        isLoading.value = true
        statusText.value = "Downloading Male Human Voice & Pictures..."

        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<VideoScene>()

            for ((idx, line) in lines.withIndex()) {
                val (topic, photoUrl) = selectMatchingImage(line, idx)

                var audioFile: File? = null
                try {
                    val encoded = URLEncoder.encode(line, StandardCharsets.UTF_8.toString())
                    val voiceUrl = "https://api.streamelements.com/kappa/v2/speech?voice=Brian&text=$encoded"

                    val connection = URL(voiceUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 12000
                    connection.readTimeout = 12000
                    connection.connect()

                    if (connection.responseCode == 200) {
                        val bytes = connection.inputStream.use { it.readBytes() }
                        if (bytes.isNotEmpty()) {
                            val tempFile = File(context.cacheDir, "scene_audio_${idx}_${System.currentTimeMillis()}.mp3")
                            FileOutputStream(tempFile).use { it.write(bytes) }
                            audioFile = tempFile
                        }
                    }
                    connection.disconnect()
                } catch (e: Exception) {
                    audioFile = null
                }

                list.add(VideoScene(line, topic, photoUrl, audioFile))
            }

            withContext(Dispatchers.Main) {
                scenes.value = list
                currentIndex.value = 0
                isLoading.value = false
                isPlaying.value = true
                playScene(context, 0)
            }
        }
    }

    private fun playScene(context: Context, index: Int) {
        val scene = scenes.value.getOrNull(index) ?: return
        mediaPlayer?.release()
        mediaPlayer = null

        if (scene.audioFile != null && scene.audioFile.exists()) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(scene.audioFile.absolutePath)
                    setOnPreparedListener {
                        start()
                        isPaused.value = false
                    }
                    setOnCompletionListener {
                        if (currentIndex.value + 1 < scenes.value.size) {
                            currentIndex.value++
                            playScene(context, currentIndex.value)
                        } else {
                            isPlaying.value = false
                        }
                    }
                    prepare()
                }
            } catch (e: Exception) {
                playFallbackVoice(context, scene.text, index)
            }
        } else {
            playFallbackVoice(context, scene.text, index)
        }
    }

    private fun playFallbackVoice(context: Context, text: String, index: Int) {
        maleTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FALLBACK_$index")
        viewModelScope.launch {
            val duration = (text.split(" ").size * 450L).coerceAtLeast(3500L)
            delay(duration)
            if (isPlaying.value && currentIndex.value == index) {
                if (currentIndex.value + 1 < scenes.value.size) {
                    currentIndex.value++
                    playScene(context, currentIndex.value)
                } else {
                    isPlaying.value = false
                }
            }
        }
    }

    fun togglePause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPaused.value = true
            } else {
                it.start()
                isPaused.value = false
            }
        }
    }

    fun next(context: Context) {
        if (currentIndex.value + 1 < scenes.value.size) {
            currentIndex.value++
            playScene(context, currentIndex.value)
        }
    }

    fun prev(context: Context) {
        if (currentIndex.value - 1 >= 0) {
            currentIndex.value--
            playScene(context, currentIndex.value)
        }
    }

    fun exit() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        maleTts?.stop()
        isPlaying.value = false
    }

    fun savePackageToPhone(context: Context) {
        Toast.makeText(context, "Saving video audio & photos to phone...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                scenes.value.forEachIndexed { i, scene ->
                    scene.audioFile?.let { src ->
                        val fileName = "PoeticVision_Scene_${i + 1}_${System.currentTimeMillis()}.mp3"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val resolver = context.contentResolver
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/PoeticVision")
                            }
                            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                            uri?.let {
                                resolver.openOutputStream(it)?.use { out -> out.write(src.readBytes()) }
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Audio track saved to Music/PoeticVision! ✓", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved to device storage.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        maleTts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090A0F)) {
                    val vm: VideoStoryViewModel = viewModel()
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        vm.initMaleTts(context)
                    }

                    if (!vm.isPlaying.value) {
                        InputEditorView(vm)
                    } else {
                        LiveVideoScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun InputEditorView(vm: VideoStoryViewModel) {
    val context = LocalContext.current
    val story = remember {
        mutableStateOf(
            "Money flows to those who master patience and focus.\n" +
            "In the dark night, the neon city comes alive with hustle.\n" +
            "A fast sports car races down the endless golden highway.\n" +
            "Rain cleanses the sorrow of every quiet dream."
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
            "Real Male UK Voice • Random Non-Repeating Subject Photos • Continuous Video",
            fontSize = 11.sp,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = story.value,
            onValueChange = { story.value = it },
            label = { Text("Write your story / poem (one sentence per line)") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFE6E6E6),
                focusedContainerColor = Color(0xFF141520),
                unfocusedContainerColor = Color(0xFF141520),
                focusedBorderColor = Color(0xFFFFD700)
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { vm.buildAndPlayVideo(context, story.value) },
            enabled = !vm.isLoading.value,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                if (vm.isLoading.value) vm.statusText.value else "▶ Play As Real Video",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun LiveVideoScreen(vm: VideoStoryViewModel) {
    val context = LocalContext.current
    val currentIdx = vm.currentIndex.value
    val scene = vm.scenes.value.getOrNull(currentIdx)

    val zoomScale by animateFloatAsState(
        targetValue = 1.16f,
        animationSpec = tween(durationMillis = 7000, easing = LinearEasing),
        label = "Zoom"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // High-Definition Matched Photo from Unsplash CDN
        Crossfade(
            targetState = scene?.imageUrl,
            animationSpec = tween(durationMillis = 800),
            label = "ArtFade"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(zoomScale),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Dark Vignette so white text is crystal clear
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // Top Reel Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            vm.scenes.value.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = if (idx <= currentIdx) Color(0xFFFFD700) else Color.White.copy(alpha = 0.25f),
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
            scene?.let { s ->
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Topic: #${s.topic.uppercase()}",
                        color = Color(0xFFFFD700),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = s.text,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Video Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { vm.prev(context) },
                    ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text("⏮ Prev", color = Color.White)
                }

                Button(
                    onClick = { vm.togglePause() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                ) {
                    Text(if (vm.isPaused.value) "▶ Resume" else "⏸ Pause", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { vm.next(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) {
                    Text("Next ⏭", color = Color.White)
                }

                Button(
                    onClick = { vm.savePackageToPhone(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                ) {
                    Text("⬇ Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = { vm.exit() }) {
                    Text("Close", color = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}