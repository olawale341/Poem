package com.example.poeticvision

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class SlideItem(val verse: String, val artUrl: String)

class PoeticViewModel : ViewModel(), TextToSpeech.OnInitListener {
    var isRecitalActive by mutableStateOf(false)
    var currentSlideIndex by mutableStateOf(0)
    var slides by mutableStateOf<List<SlideItem>>(emptyList())
    var isTtsReady by mutableStateOf(false)

    private var tts: TextToSpeech? = null

    fun initializeTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                language = Locale.UK // British cadence for classical poetry
                setPitch(0.88f)      // Deep, resonant tone
                setSpeechRate(0.80f)  // Measured poetic delivery
            }
            setupListener()
            isTtsReady = true
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (currentSlideIndex + 1 < slides.size) {
                    currentSlideIndex++
                    speakLine(currentSlideIndex)
                } else {
                    isRecitalActive = false
                }
            }

            override fun onError(utteranceId: String?) {}
        })
    }

    fun prepareAndStart(text: String) {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        slides = lines.map { verse ->
            val prompt = "atmospheric cinematic fine art masterpiece, poetic mood, dramatic lighting, $verse"
            val encoded = URLEncoder.encode(prompt, StandardCharsets.UTF_8.toString())
            val imageUrl = "https://image.pollinations.ai/prompt/$encoded?width=1080&height=1920&nologo=true"
            SlideItem(verse, imageUrl)
        }

        currentSlideIndex = 0
        isRecitalActive = true
        speakLine(0)
    }

    private fun speakLine(index: Int) {
        val verse = slides.getOrNull(index)?.verse ?: return
        tts?.speak(verse, TextToSpeech.QUEUE_FLUSH, null, "POEM_VERSE_$index")
    }

    fun nextVerse() {
        if (currentSlideIndex + 1 < slides.size) {
            currentSlideIndex++
            speakLine(currentSlideIndex)
        }
    }

    fun prevVerse() {
        if (currentSlideIndex - 1 >= 0) {
            currentSlideIndex--
            speakLine(currentSlideIndex)
        }
    }

    fun exitRecital() {
        tts?.stop()
        isRecitalActive = false
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F14)) {
                    val vm: PoeticViewModel = viewModel()
                    LaunchedEffect(Unit) {
                        vm.initializeTts(this@MainActivity)
                    }

                    if (!vm.isRecitalActive) {
                        PoemStudioScreen(onStart = { text -> vm.prepareAndStart(text) })
                    } else {
                        CinemaPlayerScreen(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun PoemStudioScreen(onStart: (String) -> Unit) {
    val presets = listOf(
        "Nightfall" to "The silent ocean whispers to the silver moon.\nA solitary raven rests upon the weeping willow.\nNight opens her velvet wings, embracing the wandering breeze.\nIn every shadow, forgotten memories find their quiet home.",
        "The Wanderer" to "Across the amber desert dunes, ancient footsteps disappear.\nA lone traveller seeks the northern star amidst the quiet storm.\nGold and dust dance beneath the crimson horizon.\nTime yields to the timeless heart of the wanderer.",
        "Embers of Dawn" to "Golden rays pierce the veil of morning mist.\nThe forest breathes with the scent of wild cedar and pine.\nA quiet stream carries the morning symphony down to the sea.\nAwake, heart, for life calls again in pure radiant light."
    )

    var poemText by remember { mutableStateOf(presets[0].second) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "✨ PoeticVision Studio",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF0E6D2),
            fontFamily = FontFamily.Serif
        )
        Text(
            text = "Craft your words into living cinema and voice",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Preset Pills
        Text("Quick Themes:", fontSize = 12.sp, color = Color.LightGray)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (name, content) ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (poemText == content) Color(0xFFD4AF37) else Color(0xFF22222E),
                    modifier = Modifier.clickable { poemText = content }
                ) {
                    Text(
                        text = name,
                        color = if (poemText == content) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = poemText,
            onValueChange = { poemText = it },
            label = { Text("Write your poem (one thought per line)", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color(0xFFFAF7F2),
                unfocusedTextColor = Color(0xFFDCD6CD),
                focusedContainerColor = Color(0xFF171720),
                unfocusedContainerColor = Color(0xFF171720),
                focusedBorderColor = Color(0xFFD4AF37),
                unfocusedBorderColor = Color(0xFF2A2A38)
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { onStart(poemText) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                "🎬 Begin Cinematic Recital",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun CinemaPlayerScreen(vm: PoeticViewModel) {
    val currentSlide = vm.slides.getOrNull(vm.currentSlideIndex)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Crossfade Artwork Transition
        Crossfade(
            targetState = currentSlide?.artUrl,
            animationSpec = tween(durationMillis = 1000),
            label = "CinemaArt"
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

        // Dark Vignette & Gradient Mask
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.88f)
                        )
                    )
                )
        )

        // Progress Pill Top Bar
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

        // Verse Text & Navigation Bar
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
                    color = Color(0xFFFFF9E6),
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Player Navigation Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { vm.prevVerse() }) {
                    Text("◀ Prev", color = Color.LightGray, fontSize = 14.sp)
                }

                Button(
                    onClick = { vm.exitRecital() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Exit", color = Color.White, fontSize = 13.sp)
                }

                TextButton(onClick = { vm.nextVerse() }) {
                    Text("Next ▶", color = Color.LightGray, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}