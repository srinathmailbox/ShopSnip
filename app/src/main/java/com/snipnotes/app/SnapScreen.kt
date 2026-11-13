package com.snipnotes.app

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SnapScreen(
    store: Store,
    pal: Palette,
    granted: Boolean,
    onRequest: () -> Unit,
    onFull: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val main = remember { ContextCompat.getMainExecutor(ctx) }
    val worker = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { worker.shutdown() } }

    var snips by remember { mutableStateOf(store.loadSnips()) }
    var note by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var openId by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Select mode and the single-snip screen take over the whole page.
    val full = selecting || openId != null
    LaunchedEffect(full) { onFull(full) }
    DisposableEffect(Unit) { onDispose { onFull(false) } }

    BackHandler(enabled = selecting && openId == null) {
        selecting = false
        selected = emptySet()
    }

    fun shoot() {
        val c = capture ?: return
        val text = note.trim()
        c.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bmp = image.toSmallBitmap()
                    val list = store.addSnip(bmp, text)
                    main.execute {
                        snips = list
                        note = ""
                        Toast.makeText(ctx, "Snip saved", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    main.execute { Toast.makeText(ctx, "Could not save the photo", Toast.LENGTH_SHORT).show() }
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                main.execute { Toast.makeText(ctx, "Photo failed, try again", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    fun update(ids: Set<String>, change: (Snip) -> Snip) {
        snips = store.updateSnips(ids, change)
    }

    val rows = snips.chunked(4)

    // ---------- One snip ----------
    val openSnip = snips.firstOrNull { it.id == openId }
    if (openId != null && openSnip == null) {
        // It was deleted, go back to the list.
        LaunchedEffect(openId) { openId = null }
    }
    if (openSnip != null) {
        SnipDetail(
            store = store,
            snip = openSnip,
            pal = pal,
            onBack = { openId = null },
            onUpdate = { id, change -> update(setOf(id), change) },
            onDelete = { id ->
                snips = store.deleteSnips(setOf(id))
                openId = null
            },
        )
        return
    }

    // ---------- Select mode ----------
    if (selecting) {
        val chosen = snips.filter { it.id in selected }
        val pinTarget = chosen.isNotEmpty() && !chosen.all { it.pinned }
        val starTarget = chosen.isNotEmpty() && !chosen.all { it.starred }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    selecting = false
                    selected = emptySet()
                }) {
                    Text("Cancel", color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Text("${selected.size} selected", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Ink)
                Spacer(Modifier.width(72.dp))
            }
            HelpNote("Tap snips to select them. Pinned snips stay at the top of the list.", pal)
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.first().id }) { row ->
                    TileRow(store, row, pal, selecting = true, selected = selected,
                        onTap = { s -> selected = if (s.id in selected) selected - s.id else selected + s.id },
                        onLong = { })
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(pal.track)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    label = if (chosen.isNotEmpty() && !pinTarget) "Unpin" else "Pin to top",
                    enabled = chosen.isNotEmpty(),
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    icon = { PinGlyph(it, Modifier.size(22.dp)) },
                ) { update(selected) { s -> s.copy(pinned = pinTarget) } }
                ActionButton(
                    label = if (chosen.isNotEmpty() && !starTarget) "Unstar" else "Star",
                    enabled = chosen.isNotEmpty(),
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Filled.Star, null, tint = it, modifier = Modifier.size(22.dp)) },
                ) { update(selected) { s -> s.copy(starred = starTarget) } }
                ActionButton(
                    label = "Delete",
                    enabled = chosen.isNotEmpty(),
                    color = Danger,
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Filled.Delete, null, tint = it, modifier = Modifier.size(22.dp)) },
                ) { confirmDelete = true }
            }
        }
        if (confirmDelete) {
            val n = selected.size
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = Color.White,
                title = { Text(if (n == 1) "Delete this snip?" else "Delete $n snips?") },
                text = { Text("The photos cannot be recovered.") },
                confirmButton = {
                    TextButton(onClick = {
                        snips = store.deleteSnips(selected)
                        selected = emptySet()
                        selecting = false
                        confirmDelete = false
                    }) { Text("Delete", color = Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = pal.accent) }
                },
            )
        }
        return
    }

    // ---------- Normal page ----------
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            CameraFrame(pal, 250.dp, granted, onRequest, null) {
                CameraView(Modifier.fillMaxSize(), onCapture = { capture = it })
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(4.dp, pal.accent, CircleShape)
                        .background(Color.White)
                        .semantics { contentDescription = "Take photo" }
                        .clickable(role = Role.Button) { shoot() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(50.dp).clip(CircleShape).background(pal.accent))
                }
            }
        }
        item {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note for this snip") },
                placeholder = { Text("Add a short note (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(pal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Saved snips", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
                TextButton(
                    onClick = { selecting = true },
                    enabled = snips.isNotEmpty(),
                ) {
                    Text("Select", color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
        if (snips.isEmpty()) {
            item {
                Text("No snips yet. Your photos will appear here.", color = pal.label, fontSize = 14.sp)
            }
        }
        items(rows, key = { it.first().id }) { row ->
            TileRow(store, row, pal, selecting = false, selected = emptySet(),
                onTap = { s -> openId = s.id },
                onLong = { s ->
                    selecting = true
                    selected = setOf(s.id)
                })
        }
    }
}

@Composable
private fun TileRow(
    store: Store,
    row: List<Snip>,
    pal: Palette,
    selecting: Boolean,
    selected: Set<String>,
    onTap: (Snip) -> Unit,
    onLong: (Snip) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { snip ->
            SnipTile(store, snip, pal, selecting, snip.id in selected, Modifier.weight(1f),
                onTap = { onTap(snip) }, onLong = { onLong(snip) })
        }
        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun Badge(pal: Palette, content: @Composable () -> Unit) {
    Box(
        Modifier.size(20.dp).clip(CircleShape).background(pal.accent),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnipTile(
    store: Store,
    snip: Snip,
    pal: Palette,
    selecting: Boolean,
    selected: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
    onLong: () -> Unit,
) {
    val bmp by produceState<Bitmap?>(null, snip.id) {
        value = withContext(Dispatchers.IO) { decodeSampled(store.snipFile(snip.id), 300) }
    }
    val shape = RoundedCornerShape(12.dp)
    val state = listOfNotNull(
        if (snip.pinned) "pinned" else null,
        if (snip.starred) "starred" else null,
    )
    val desc = (snip.note.ifBlank { "Saved snip" }) + if (state.isEmpty()) "" else ", " + state.joinToString(", ")

    Box(
        modifier
            .height(76.dp)
            .clip(shape)
            .background(Color(0xFFDDE3E8))
            .then(if (selected) Modifier.border(3.dp, pal.accent, shape) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = desc }
            .combinedClickable(onClick = onTap, onLongClick = onLong),
    ) {
        bmp?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (snip.note.isNotBlank()) {
            Text(
                snip.note,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (snip.pinned) Badge(pal) { PinGlyph(Color.White, Modifier.size(12.dp)) }
            if (snip.starred) Badge(pal) {
                Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
        if (selecting) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) pal.accent else Color.White)
                    .then(if (selected) Modifier else Modifier.border(2.dp, Color(0xFF8A97A3), CircleShape)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    val tint = if (enabled) color else Color(0xFF9AA5B0)
    Column(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon(tint)
        Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SnipDetail(
    store: Store,
    snip: Snip,
    pal: Palette,
    onBack: () -> Unit,
    onUpdate: (String, (Snip) -> Snip) -> Unit,
    onDelete: (String) -> Unit,
) {
    var text by remember(snip.id) { mutableStateOf(snip.note) }
    var confirm by remember { mutableStateOf(false) }
    val bmp by produceState<Bitmap?>(null, snip.id) {
        value = withContext(Dispatchers.IO) { decodeSampled(store.snipFile(snip.id), 1000) }
    }

    fun saveNote() {
        val t = text.trim()
        if (t != snip.note) onUpdate(snip.id) { it.copy(note = t) }
    }

    fun leave() {
        saveNote()
        onBack()
    }

    BackHandler { leave() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to saved snips", tint = Ink)
            }
            Text("Snip", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ink)
        }
        HelpNote("Change the note, pin it to the top of your list, or star it. The photo stays as it was taken.", pal)
        Box(
            Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFDDE3E8)),
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = snip.note.ifBlank { "Saved snip" },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Note for this snip") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors(pal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleButton(
                label = if (snip.pinned) "Pinned to top" else "Pin to top",
                on = snip.pinned,
                pal = pal,
                modifier = Modifier.weight(1f),
                icon = { PinGlyph(it, Modifier.size(18.dp)) },
            ) { onUpdate(snip.id) { s -> s.copy(pinned = !s.pinned) } }
            ToggleButton(
                label = if (snip.starred) "Starred" else "Star",
                on = snip.starred,
                pal = pal,
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Filled.Star, null, tint = it, modifier = Modifier.size(18.dp)) },
            ) { onUpdate(snip.id) { s -> s.copy(starred = !s.starred) } }
        }
        Button(
            onClick = { leave() },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save note", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        Button(
            onClick = { confirm = true },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Danger),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Danger),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Delete snip", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(16.dp))
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Color.White,
            title = { Text("Delete this snip?") },
            text = { Text("The photo cannot be recovered.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDelete(snip.id)
                }) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancel", color = pal.accent) }
            },
        )
    }
}

@Composable
private fun ToggleButton(
    label: String,
    on: Boolean,
    pal: Palette,
    modifier: Modifier,
    icon: @Composable (Color) -> Unit,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = if (on) pal.accent else Color(0xFF3E4852)
    Row(
        modifier
            .height(48.dp)
            .clip(shape)
            .background(if (on) pal.tint else Color.White)
            .border(2.dp, if (on) pal.accent else Color(0xFFC5CED6), shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() }),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(Modifier.width(8.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
