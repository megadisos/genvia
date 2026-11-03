package com.jorge.videosia.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.jorge.videosia.data.Currency
import com.jorge.videosia.data.Gallery
import com.jorge.videosia.data.Provider
import com.jorge.videosia.data.VideoModel
import com.jorge.videosia.data.SavedVideo
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF6C4CF5)
private val Green = Color(0xFF2EB872)
private val Gold = Color(0xFFFFD54F)

@Composable
fun App(vm: MainViewModel = viewModel()) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, secondary = Gold)) {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Movie, null) }, label = { Text("Crear") })
                    NavigationBarItem(tab == 1, { tab = 1; vm.refreshLibrary() }, { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Mis videos") })
                    NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("Ajustes") })
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> CreateScreen(vm)
                    1 -> LibraryScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySwitch(vm: MainViewModel) {
    SingleChoiceSegmentedButtonRow {
        Currency.entries.forEachIndexed { i, c ->
            SegmentedButton(
                selected = vm.currency == c, onClick = { vm.chooseCurrency(c) },
                shape = SegmentedButtonDefaults.itemShape(i, Currency.entries.size)
            ) { Text(c.name) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val money = vm.money
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { vm.setImage(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("GenVia", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Videos con IA para tus redes", color = Color.Gray)
            }
            CurrencySwitch(vm)
        }

        OutlinedTextField(
            value = vm.prompt, onValueChange = { vm.prompt = it },
            label = { Text("Describe el video") },
            placeholder = { Text("Ej: un gato astronauta bailando en la luna, estilo cine") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = !vm.busy
        )

        // ---- Imagen de referencia ----
        val preview = vm.imagePreview
        if (preview == null) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !vm.busy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp))
                Text("Agregar imagen de referencia (opcional)")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val framed = remember(preview, vm.aspect) { vm.framedPreview() ?: preview }
                Image(
                    framed.asImageBitmap(), "Imagen de referencia",
                    Modifier.height(110.dp).aspectRatio(framed.width.toFloat() / framed.height).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
                Column(Modifier.weight(1f)) {
                    Text("Imagen de referencia", fontWeight = FontWeight.SemiBold)
                    Text("Así la recibe la IA: completa y centrada, como primer cuadro del video.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (!vm.model.imageOk) Text("${vm.model.name} no acepta imagen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = vm::clearImage, enabled = !vm.busy) { Icon(Icons.Default.Close, "Quitar imagen") }
            }
        }

        Text("Formato", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = mapOf("9:16" to "Vertical (TikTok, Reels)", "16:9" to "Horizontal (YouTube)", "1:1" to "Cuadrado")
            vm.model.aspects.forEach { v -> FilterChip(vm.aspect == v, { vm.aspect = v }, label = { Text(labels[v] ?: v) }) }
        }

        Text("Modelo de IA", fontWeight = FontWeight.SemiBold)
        val needImage = vm.imageJpeg != null
        var order by remember { mutableIntStateOf(0) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Mejor a peor", "Por uso", "Por proveedor", "Más barato").forEachIndexed { i, l ->
                FilterChip(order == i, { order = i }, label = { Text(l) })
            }
        }
        val all = Catalog.models
        when (order) {
            0 -> ModelGroup("De mejor a peor calidad", all.sortedWith(compareBy({ it.rank }, { it.usdPerSecond })), vm, needImage)
            1 -> com.jorge.videosia.data.UseCase.entries.forEach { u ->
                ModelGroup(u.label, all.filter { it.useCase == u }.sortedBy { it.rank }, vm, needImage)
            }
            2 -> Provider.entries.forEach { p ->
                ModelGroup(p.label, all.filter { it.provider == p }.sortedBy { it.rank }, vm, needImage)
            }
            else -> ModelGroup("Del más barato al más caro (por segundo)", all.sortedBy { it.usdPerSecond }, vm, needImage)
        }

        if (vm.model.durations.size > 1) {
            Text("Duración del video", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.model.durations.forEach { s -> FilterChip(vm.seconds == s, { vm.seconds = s }, label = { Text("${s}s") }) }
            }
        }

        InfoCard(vm)

        if (vm.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(vm.status)
            OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Dejar de esperar") }
            Text("Si cancelas, el proveedor puede terminar el video y cobrarlo igual.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            Button(onClick = vm::generate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Generar video de ${vm.seconds}s por ~${money.fmt(vm.model.estimateUsd(vm.seconds))}")
            }
            if (vm.status.isNotBlank()) Text(vm.status, color = Green)
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        vm.lastInfo?.let { Text(it, fontWeight = FontWeight.SemiBold) }

        vm.saved?.let { v ->
            VideoPlayer(v.uri)
            Text("Ruta: ${v.path}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Button(onClick = { Gallery.share(ctx, v.uri) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir en redes")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ModelGroup(title: String, models: List<VideoModel>, vm: MainViewModel, needImage: Boolean) {
    if (models.isEmpty()) return
    val money = vm.money
    Text(title, style = MaterialTheme.typography.labelLarge, color = Color.Gray)
    models.forEach { m ->
        val selected = vm.model.id == m.id
        val dim = needImage && !m.imageOk
        val refSecs = if (selected) vm.seconds else (m.durations.firstOrNull { it >= 5 } ?: m.durations.first())
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !vm.busy) { vm.pick(m) },
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Color.DarkGray)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${m.rank} ${m.name}", fontWeight = FontWeight.SemiBold, color = if (dim) Color.Gray else Color.Unspecified)
                        Text("${m.company} · ${m.country} · ${m.provider.label} · ${m.quality}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money.fmt(m.usdPerSecond), color = Gold, fontWeight = FontWeight.Bold)
                        Text("por segundo", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (m.bestFor.isNotBlank()) Text("Ideal para: ${m.bestFor}", style = MaterialTheme.typography.bodySmall, color = Gold)
                Text(
                    "Duración: ${m.durationText} · 1 video por generación · video de ${refSecs}s ≈ ${money.fmt(m.estimateUsd(refSecs))}",
                    style = MaterialTheme.typography.bodySmall
                )
                val left = vm.videosLeft(m, refSecs)
                Text(
                    if (left != null) "Con tu saldo te alcanzan ~$left videos de ${refSecs}s"
                    else "Agrega tu clave o tu saldo en Ajustes para ver cuántos te quedan",
                    style = MaterialTheme.typography.bodySmall, color = if (left != null) Green else Color.Gray
                )
                val tags = buildList {
                    if (m.freePollenOk) add("Acepta Pollen gratis")
                    if (m.imageOk) add("Acepta imagen")
                    if (m.audio) add("Con audio")
                }
                if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Accent)
            }
        }
    }
}

@Composable
private fun InfoCard(vm: MainViewModel) {
    val m = vm.model; val money = vm.money; val st = vm.stats
    val bal = vm.balances[m.provider]
    val left = vm.videosLeft(m, vm.seconds)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${m.name} en ${m.provider.label}", fontWeight = FontWeight.Bold)
            Text("Costo estimado: ${money.fmt(m.estimateUsd(vm.seconds))} (${money.fmt(m.usdPerSecond)}/s × ${vm.seconds}s)")
            BalanceLines(bal, money)
            vm.usableUsd(m)?.let { if (!m.freePollenOk && bal?.quest != null) Text("Este modelo solo usa saldo pagado: ${money.fmt(it)}", style = MaterialTheme.typography.bodySmall) }
            Text("Videos disponibles con ese saldo: " + (left?.toString() ?: "—"))
            Text("Hechos hoy: ${st.today} · este mes: ${st.month} · gastado este mes: ${money.fmt(st.spentMonthUsd)}")
            Text(m.provider.freeInfo, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (vm.currency == Currency.COP) {
                Text(
                    "Tasa: 1 USD = ${String.format(Locale.US, "%,.0f", vm.copPerUsd)} COP. Precios aproximados, revisados en nov. 2026.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(uri: Uri) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f))
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val money = vm.money
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text("Moneda", fontWeight = FontWeight.SemiBold)
        CurrencySwitch(vm)
        val updated = vm.settings.copRateUpdatedAt
        Text(
            "1 USD = ${String.format(Locale.US, "%,.0f", vm.copPerUsd)} COP" +
                (if (updated > 0) " · actualizado ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(updated))}" else " · tasa aproximada"),
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        TextButton(onClick = { vm.refreshRate() }) { Text("Actualizar tasa") }
        HorizontalDivider()

        Text("Las claves se guardan solo en este teléfono.", color = Color.Gray)
        Provider.entries.forEach { p ->
            var key by remember { mutableStateOf(vm.settings.key(p)) }
            val manual = vm.settings.manualBalance(p)
            var bal by remember { mutableStateOf(if (manual >= 0) manual.toString() else "") }
            Text(p.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(p.freeInfo, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            OutlinedTextField(
                value = key, onValueChange = { key = it; vm.settings.setKey(p, it) },
                label = { Text(p.keyHint) }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            BalanceLines(vm.balances[p], money)
            OutlinedTextField(
                value = bal,
                onValueChange = { v -> bal = v; v.replace(",", ".").toDoubleOrNull()?.let { vm.settings.setManualBalance(p, it) } },
                label = { Text("Saldo a mano en USD (si la clave no deja leerlo)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row {
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.keyUrl))) }) { Text("Obtener clave") }
                TextButton(onClick = { vm.refreshBalances() }) { Text("Consultar saldo") }
            }
            HorizontalDivider()
        }

        val version = remember {
            runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        }
        Text("Versión de la app: $version", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        HorizontalDivider()

        val st = vm.stats
        Text("Tu uso", fontWeight = FontWeight.SemiBold)
        Text("Videos hoy: ${st.today} · este mes: ${st.month}")
        Text("Gastado este mes: ${money.fmt(st.spentMonthUsd)} · total: ${money.fmt(st.spentTotalUsd)}")
        OutlinedButton(onClick = vm::resetTotal) { Text("Reiniciar total") }
    }
}

@Composable
fun LibraryScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    // Pide permiso para leer videos: así aparecen también los de versiones anteriores de la app.
    val readPerm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_VIDEO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshLibrary() }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 29 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, readPerm) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(readPerm)
    }
    var playing by remember { mutableStateOf<Uri?>(null) }
    var toDelete by remember { mutableStateOf<SavedVideo?>(null) }
    val groups = vm.library.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Mis videos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Se guardan en la galería, en una carpeta por IA: Movies/GenVia/<nombre de la IA>",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray
        )
        if (groups.isEmpty()) Text("Todavía no has generado videos.", color = Color.Gray)

        playing?.let { VideoPlayer(it) }

        groups.forEach { (folder, videos) ->
            Spacer(Modifier.height(6.dp))
            Text("$folder · ${videos.size} video${if (videos.size == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(videos.first().path.substringBeforeLast('/'), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            videos.forEach { v ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(v.dateMs)) +
                                    " · " + String.format(Locale.US, "%.1f MB", v.sizeBytes / 1_048_576.0),
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray
                            )
                        }
                        IconButton(onClick = { playing = v.uri }) { Icon(Icons.Default.PlayArrow, "Ver") }
                        IconButton(onClick = { Gallery.share(ctx, v.uri) }) { Icon(Icons.Default.Share, "Compartir") }
                        IconButton(onClick = { toDelete = v }) { Icon(Icons.Default.Delete, "Borrar") }
                    }
                }
            }
        }
    }

    toDelete?.let { v ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar video?") },
            text = { Text("Se borrará ${v.name} de ${v.folder}. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { if (playing == v.uri) playing = null; vm.deleteVideo(v); toDelete = null }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun BalanceLines(b: com.jorge.videosia.net.BalanceInfo?, money: com.jorge.videosia.data.Money) {
    val small = MaterialTheme.typography.bodySmall
    when {
        b == null -> Text("Saldo: consultando…", style = small)
        b.total == null -> Text("Saldo: no se pudo leer (${b.error ?: "sin datos"}). Puedes escribirlo a mano en Ajustes.", style = small, color = Color.Gray)
        b.paid != null && b.quest != null -> {
            Text("Saldo total: ${money.fmt(b.total ?: 0.0)}", fontWeight = FontWeight.SemiBold)
            Text(
                "Pagado: ${money.fmt(b.paid)} · Misiones (gratis): ${money.fmt(b.quest)}" + if (b.questLocked) " (bloqueado)" else "",
                style = small
            )
        }
        b.keyBudget -> Text("Saldo de esta clave: ${money.fmt(b.total ?: 0.0)} (para ver pagado y misiones, dale a la clave el permiso account:usage)", style = small)
        b.manual -> Text("Saldo (escrito a mano): ${money.fmt(b.total ?: 0.0)}", style = small)
        else -> Text("Saldo: ${money.fmt(b.total ?: 0.0)}", style = small)
    }
}
