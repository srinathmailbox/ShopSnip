package com.snipnotes.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Plain memory the camera callback uses; it never needs to redraw the screen. */
private class NameMemory {
    var pending: String? = null
    var lastAdded: String? = null
    var filled: String? = null
    var nulls = 0
    var manual = false
}

/** Compares names ignoring case, spaces and punctuation, because the camera text wobbles a little. */
private fun key(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

@Composable
fun ShopScreen(store: Store, pal: Palette, granted: Boolean, onRequest: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(store.loadShop()) }
    var found by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val mem = remember { NameMemory() }
    val listState = rememberLazyListState()

    val analyzer = remember {
        TextAnalyzer<String>({ LabelParser.parseName(it) }) { n ->
            if (n == null) {
                mem.nulls++
                if (mem.nulls >= 3) {
                    found = false
                    mem.pending = null
                    mem.lastAdded = null
                }
            } else {
                mem.nulls = 0
                val k = key(n)
                if (k == mem.lastAdded) {
                    // Same pack as the one just added: wait for the camera to move on.
                    found = false
                } else if (mem.pending == k) {
                    found = true
                    if (!mem.manual || k != mem.filled) {
                        name = n
                        mem.filled = k
                        mem.manual = false
                    }
                } else {
                    mem.pending = k
                }
            }
        }
    }

    fun save(list: List<ShopItem>) {
        items = list
        store.saveShop(list)
    }

    fun add() {
        val n = name.trim()
        if (n.isEmpty()) {
            Toast.makeText(ctx, "Point at a product or type a name first", Toast.LENGTH_SHORT).show()
            return
        }
        save(items + ShopItem(n))
        mem.lastAdded = key(n)
        mem.pending = null
        mem.filled = null
        mem.manual = false
        name = ""
        found = false
    }

    fun copyAll() {
        if (items.isEmpty()) {
            Toast.makeText(ctx, "The list is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val text = items.joinToString("\n") { it.name }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Shopping list", text))
        Toast.makeText(ctx, "Copied ${items.size} items", Toast.LENGTH_SHORT).show()
    }

    var lastCount by remember { mutableStateOf(items.size) }
    LaunchedEffect(items.size) {
        if (items.size > lastCount) listState.animateScrollToItem(items.size - 1)
        lastCount = items.size
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CameraFrame(
            pal = pal,
            height = 136.dp,
            granted = granted,
            onRequest = onRequest,
            chip = if (found) "Name found" else "Looking for a name",
        ) {
            CameraView(Modifier.fillMaxSize(), analyzer = analyzer)
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; mem.manual = true },
            label = { Text("Item name") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors(pal),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { add() },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Text("  Add to shopping list", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Shopping list · ${items.size} ${if (items.size == 1) "item" else "items"}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Ink,
            )
            OutlinedButton(
                onClick = { copyAll() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, pal.accent),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = pal.accent),
                modifier = Modifier.height(44.dp),
            ) {
                Text("Copy list", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Slim rows: tick it off, tap the name to fix spelling, X to remove.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items) { index, item ->
                Row(
                    Modifier.fillMaxWidth().height(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TickBox(item.done, pal, "Got ${item.name}") { c ->
                        save(items.withAt(index, item.copy(done = c)))
                    }
                    SlimField(
                        value = item.name,
                        onValueChange = { v -> save(items.withAt(index, item.copy(name = v))) },
                        pal = pal,
                        modifier = Modifier.weight(1f),
                        done = item.done,
                    )
                    Box(
                        Modifier
                            .size(28.dp)
                            .semantics { contentDescription = "Delete ${item.name}" }
                            .clickable(role = Role.Button) { save(items.removeAtIndex(index)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = pal.label, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (items.isNotEmpty()) {
                item {
                    Row {
                        if (items.any { it.done }) {
                            TextButton(onClick = { save(items.filter { !it.done }) }) {
                                Text("Remove ticked", color = pal.accent)
                            }
                        }
                        TextButton(onClick = { confirmClear = true }) {
                            Text("Clear list", color = Danger)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color.White,
            title = { Text("Clear the whole list?") },
            text = { Text("This removes all ${items.size} items.") },
            confirmButton = {
                TextButton(onClick = {
                    save(emptyList())
                    confirmClear = false
                }) { Text("Clear", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = pal.accent) }
            },
        )
    }
}
