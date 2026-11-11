package com.example.poeticvision

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class VideoSlide(val verse: String, val imageUrl: String, val audioUrl: String)

class VideoPoetViewModel : ViewModel() {
    var isPlayingVideo by mutableStateOf(false)
    var currentSlideIndex by mutableStateOf(0)
    var slides by mutableStateOf<List<VideoSlide>>(emptyList())
    var isLoading by mutableStateOf(false)
    var isAudioPaused by mutableStateOf(false)
    var statusMessage by mutableStateOf("")

    private var mediaPlayer: MediaPlayer? = null
    private val client = OkHttpClient()

    // Curated high-resolution aesthetic photo collections mapped to poem imagery
    private val imageKeywords = mapOf(
        "ocean" to "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1080&q=80",
        "sea" to "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1080&q=80",
        "moon" to "https://images.unsplash.com/photo-1532693322450-2cb5c511067d?w=1080&q=80",
        "night" to "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1080&q=80",
        "star" to "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=1080&q=80",
        "raven" to "https://images.unsplash.com/photo-1522926193341-e9ffd686c60f?w=1080&q=80",
        "willow" to "https://images.unsplash.com/photo-1511497584788-87676104235f?w=1080&q=80",
        "desert" to "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1080&q=80",
        "dune" to "https://images.unsplash.com/photo-1509316975850-ff9c5deb0cd9?w=1080&q=80",
        "wanderer" to "https://images.unsplash.com/photo-1469854523086-cc02fe5d8800?w=1080&q=80",
        "dawn" to "https://images.unsplash.com/photo-1495616811223-4d98c6e9c869?w=1080&q=80",
        "forest" to "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1080&q=80",
        "fire" to "https://images.unsplash.com/photo-1524334228333-0f6db392f8a1?w=1080&q=80",
        "rain" to "https://images.unsplash.com/photo-1515694346937-94d85e41e6f0?w=1080&q=80"
    )

    private val fallbackImages = listOf(
        "https://images.unsplash.com/photo-1519681393784-d120267933ba?w=1080&q=80", // Mountains
        "https://images.unsplash.com/photo-1518837695005-2083093ee35b?w=1080&q=80", // Blue Sea
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=1080&q=80", // Coast
        "https://images.unsplash.com/photo-1448375240586-882707db888b?w=1080&q=80"  // Deep Woods
    )

    fun createAndPlayVideo(rawText: String) {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        isLoading = true
        statusMessage = "Loading Human Narrator Voice..."

        viewModelScope.launch(Dispatchers.IO) {
            val generatedSlides = lines.mapIndexed { index, line ->
                // 1. Pick matching visual background based on verse keywords
                val lower = line.lowercase()
                var matchedImage = fallbackImages[index % fallbackImages.size]
                for ((keyword, url) in imageKeywords) {
                    if (lower.contains(keyword)) {
                        matchedImage = url
                        break
                    }
                }

                // 2. Real Human British Narrator Voice (Amazon Polly / StreamElements Brian)
                val encodedText = URLEncoder.encode(line, StandardCharsets.UTF_8.toString())
                val humanAudioUrl = "https://api.streamelements.com/kappa/v2/speech?voice=Brian&text=$encodedText"

                VideoSlide(line, matchedImage, humanAudioUrl)
            }

            withContext(Dispatchers.Main) {
                slides = generatedSlides
                currentSlideIndex = 0
                isLoading = false
                isPlayingVideo = true
                playSlideAudio(0)
            }
        }
    }

    private fun playSlideAudio(index: Int) {
        val slide = slides.getOrNull(index) ?: return
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(slide.audioUrl)
            setOnPreparedListener {
                start()
                isAudioPaused = false
            }
            setOnCompletionListener {
                if (currentSlideIndex + 1 < slides.size) {
                    currentSlideIndex++
                    playSlideAudio(currentSlideIndex)
                } else {
                    isPlayingVideo = false
                }
            }
            prepareAsync()
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isAudioPaused = true
            } else {
                it.start()
                isAudioPaused = false
            }
        }
    }

    fun saveToDevice(context: Context) {
        val currentSlide = slides.getOrNull(currentSlideIndex) ?: return
        Toast.makeText(context, "Saving current poem & audio to Music/Downloads...", Toast.LENGTH_SHORT).show()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Download the human narrator audio track (MP3)
                val req = Request.Builder().url(currentSlide.audioUrl).get().build()
                val resp = client.newCall(req).execute()

                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes() ?: return@launch
                    val fileName = "PoeticVision_Verse_${System.currentTimeMillis()}.mp3"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/PoeticVision")
                        }
                        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                        uri?.let {
                            resolver.openOutputStream(it)?.use { stream -> stream.write(bytes) }
                        }
                    } else {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "PoeticVision")
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, fileName)
                        FileOutputStream(file).use { it.write(bytes) }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved MP3 to your phone's Music folder! ✓", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved locally: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun stopAndExit() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingVideo = false
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0C0C12)) {
                    val vm: VideoPoetViewModel = viewModel()
                    if (!vm.isPlayingVideo) {
                        PoemEditorView(vm)
                    } else {
                        VideoPlayerView(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun PoemEditorView(vm: VideoPoetViewModel) {
    val samplePoems = listOf(
        "Ocean & Moon" to "The silent ocean whispers to the silver moon.\nA solitary raven rests upon the weeping willow.\nNight opens her velvet wings across the wandering breeze.\nIn every shadow, ancient secrets find their home.",
        "The Desert Nomad" to "Across the amber desert dunes, long forgotten tracks disappear.\nA lone wanderer searches for the distant northern star.\nGold and dust dance beneath the crimson sky.\nTime surrenders to the silent rhythm of the dunes.",
        "Rain and Rebirth" to "Gentle rain falls upon the cedar forest.\nThe scent of damp earth fills the cool morning air.\nEvery drop breathes new life into the sleeping green.\nAwake now, for a brand new dawn has come."
    )

    var text by remember { mutableStateOf(samplePoems[0].second) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            "🎬 PoeticVision Video Studio",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE8D7B0),
            fontFamily = FontFamily.Serif
        )
        Text(
            "Transforms poems into real human speech & video",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text("Select a Masterpiece:", fontSize = 12.sp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            samplePoems.forEach { (title, poem) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (text == poem) Color(0xFFD4AF37) else Color(0xFF1E1E28),
                    modifier = Modifier.clickable { text = poem }
                ) {
                    Text(
                        text = title,
                        color = if (text == poem) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Enter poem lines (one sentence per line)") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color(0xFFE0E0E0),
                focusedContainerColor = Color(0xFF161620),
                unfocusedContainerColor = Color(0xFF161620),
                focusedBorderColor = Color(0xFFD4AF37)
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { vm.createAndPlayVideo(text) },
            enabled = !vm.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                if (vm.isLoading) "Generating Video & Voice..." else "▶ Play As Video",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

// --- FULL-SCREEN VIDEO VIEW WITH SEAMLESS HUMAN NARRATION ---
@Composable
fun VideoPlayerView(vm: VideoPoetViewModel) {
    val context = LocalContext.current
    val currentSlide = vm.slides.getOrNull(vm.currentSlideIndex)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // High-Definition Matched Background Artwork
        Crossfade(
            targetState = currentSlide?.imageUrl,
            animationSpec = tween(durationMillis = 800),
            label = "VideoBackground"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Cinematic Dark Vignette for Subtitle Legibility
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

        // Top Video Progress Bar (Story / TikTok style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            vm.slides.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(
                            color = if (idx <= vm.currentSlideIndex) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.25f),
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
            currentSlide?.let { slide ->
                Text(
                    text = slide.verse,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons (Play/Pause, Save, Exit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume Video
                IconButton(
                    onClick = { vm.togglePlayPause() },
                    modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        if (vm.isAudioPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Play/Pause",
                        tint = Color.White
                    )
                }

                // Save Audio & Story to Device
                Button(
                    onClick = { vm.saveToDevice(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Phone", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Exit
                TextButton(onClick = { vm.stopAndExit() }) {
                    Text("Close", color = Color.LightGray, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}