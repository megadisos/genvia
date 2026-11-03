package com.jorge.videosia.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jorge.videosia.data.Catalog
import com.jorge.videosia.data.Gallery
import com.jorge.videosia.data.Provider
import com.jorge.videosia.data.VideoModel
import java.io.File
import java.util.Locale

private val Accent = Color(0xFF6C4CF5)
private val FreeGreen = Color(0xFF2EB872)

fun usd(v: Double): String = "$" + String.format(Locale.US, if (v < 1) "%.3f" else "%.2f", v)

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val scheme = darkColorScheme(primary = Accent, secondary = Color(0xFFFFD54F))
    MaterialTheme(colorScheme = scheme) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Movie, null) }, label = { Text("Crear") })
                    NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Ajustes") })
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (tab == 0) CreateScreen(vm) else SettingsScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("GenVia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Videos con IA para tus redes", color = Color.Gray)

        OutlinedTextField(
            value = vm.prompt, onValueChange = { vm.prompt = it },
            label = { Text("Describe el video") },
            placeholder = { Text("Ej: un gato astronauta bailando en la luna, estilo cine") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !vm.busy
        )

        Text("Formato", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("9:16" to "Vertical (TikTok, Reels)", "16:9" to "Horizontal (YouTube)", "1:1" to "Cuadrado").forEach { (v, l) ->
                FilterChip(vm.aspect == v, { vm.aspect = v }, label = { Text(l) })
            }
        }

        Text("Modelo de IA", fontWeight = FontWeight.SemiBold)
        ModelGroup("Gratis", Catalog.models.filter { it.isFree }, vm)
        ModelGroup("Chinas (pago)", Catalog.models.filter { !it.isFree && it.country == "China" }, vm)
        ModelGroup("Otras (pago)", Catalog.models.filter { !it.isFree && it.country != "China" }, vm)

        if (vm.model.durations.size > 1) {
            Text("Duración", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.model.durations.forEach { s -> FilterChip(vm.seconds == s, { vm.seconds = s }, label = { Text("${s}s") }) }
            }
        }

        CostCard(vm.model, vm.seconds)

        if (vm.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(vm.status)
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Dejar de esperar") }
            Text(
                "Si cancelas, el proveedor puede terminar el video y cobrarlo igual.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray
            )
        } else {
            Button(onClick = vm::generate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (vm.model.isFree) "Generar gratis" else "Generar por ~${usd(vm.model.estimate(vm.seconds))}")
            }
            if (vm.status.isNotBlank()) Text(vm.status, color = FreeGreen)
        }

        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        vm.resultFile?.let { file ->
            VideoPlayer(file)
            vm.galleryUri?.let { uri ->
                Button(onClick = { Gallery.share(ctx, uri) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir en redes")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModelGroup(title: String, models: List<VideoModel>, vm: MainViewModel) {
    if (models.isEmpty()) return
    Text(title, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
    models.forEach { m ->
        val selected = vm.model.id == m.id
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { vm.pick(m) },
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Color.DarkGray)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, fontWeight = FontWeight.SemiBold)
                    Text("${m.company} · ${m.country}${if (m.audio) " · con audio" else ""}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(m.priceNote, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Text(
                    if (m.isFree) "GRATIS" else "${usd(m.usdPerSecond)}/s",
                    color = if (m.isFree) FreeGreen else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CostCard(m: VideoModel, seconds: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (m.isFree) {
                Text("Costo: GRATIS", color = FreeGreen, fontWeight = FontWeight.Bold)
                Text("Usa los créditos mensuales gratis de tu cuenta de Hugging Face. Si se acaban, Hugging Face puede cobrar o pedir esperar al mes siguiente.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Costo estimado: ${usd(m.estimate(seconds))} USD", fontWeight = FontWeight.Bold)
                Text("${usd(m.usdPerSecond)} por segundo × ${seconds}s · se cobra en tu cuenta de ${m.provider.label.substringBefore(" (")}. Precio aproximado, revisado en nov. 2026.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun VideoPlayer(file: File) {
    val ctx = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
    )
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Las claves se guardan solo en este teléfono.", color = Color.Gray)
        Provider.entries.forEach { p ->
            var value by remember { mutableStateOf(vm.settings.key(p)) }
            Text(p.label, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it; vm.settings.setKey(p, it) },
                label = { Text(p.keyHint) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyUrl))) }) {
                Text("Obtener clave")
            }
        }
        HorizontalDivider()
        Text("Gasto estimado acumulado: ${usd(vm.spent)} USD", fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = vm::resetSpent) { Text("Reiniciar contador") }
    }
}
