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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Plain memory the camera callback uses; it never needs to redraw the screen. */
private class ScanMemory {
    var pendingPrice: String? = null
    var lastAddedPrice: String? = null
    var filledPrice: String? = null
    var nulls = 0
    var manual = false
}

@Composable
fun PriceScreen(store: Store, pal: Palette, granted: Boolean, onRequest: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(store.loadPrices()) }
    var found by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val mem = remember { ScanMemory() }
    val listState = rememberLazyListState()

    val analyzer = remember {
        TextAnalyzer<Detected>({ LabelParser.parse(it) }) { d ->
            if (d == null) {
                mem.nulls++
                if (mem.nulls >= 3) {
                    found = false
                    mem.pendingPrice = null
                    mem.lastAddedPrice = null
                }
            } else {
                mem.nulls = 0
                if (d.price == mem.lastAddedPrice) {
                    // Same label as the one just added: wait for the camera to move on.
                    found = false
                } else if (mem.pendingPrice == d.price) {
                    // Two readings in a row agree, so fill the boxes.
                    found = true
                    if (!mem.manual || d.price != mem.filledPrice) {
                        name = d.name
                        price = d.price
                        mem.filledPrice = d.price
                        mem.manual = false
                    }
                } else {
                    mem.pendingPrice = d.price
                }
            }
        }
    }

    fun save(list: List<PriceItem>) {
        items = list
        store.savePrices(list)
    }

    fun add() {
        val n = name.trim()
        val p = normalizePrice(price)
        if (n.isEmpty() || p == null) {
            Toast.makeText(ctx, "Enter an item name and a price like 1.55", Toast.LENGTH_SHORT).show()
            return
        }
        save(items + PriceItem(n, p))
        mem.lastAddedPrice = p
        mem.pendingPrice = null
        mem.filledPrice = null
        mem.manual = false
        name = ""
        price = ""
        found = false
    }

    val badRow = items.firstOrNull { normalizePrice(it.price) == null }

    fun copyAll() {
        if (items.isEmpty()) {
            Toast.makeText(ctx, "The list is empty", Toast.LENGTH_SHORT).show()
            return
        }
        if (badRow != null) {
            Toast.makeText(ctx, "Fix the price for ${badRow.name.ifBlank { "the red row" }} first", Toast.LENGTH_SHORT).show()
            return
        }
        // One row per item: item name, TAB, price. Pastes into two spreadsheet columns.
        val text = items.joinToString("\n") {
            "${it.name.replace('\t', ' ')}\t${normalizePrice(it.price)}"
        }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Price list", text))
        Toast.makeText(ctx, "Copied ${items.size} rows. Paste into your spreadsheet.", Toast.LENGTH_SHORT).show()
    }

    // Scroll to the newest row only when one is added (not when you edit or delete).
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
            chip = if (found) "Label found" else "Looking for a label",
        ) {
            CameraView(Modifier.fillMaxSize(), analyzer = analyzer)
        }

        // Item name and price: two separate boxes.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; mem.manual = true },
                label = { Text("Item name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(pal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = price,
                onValueChange = { price = it; mem.manual = true },
                label = { Text("Price (£)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(pal),
                modifier = Modifier.width(112.dp),
            )
        }

        Button(
            onClick = { add() },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(22.dp))
            Text("  Add to list", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Price list · ${items.size} ${if (items.size == 1) "item" else "items"}",
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
                Text("Copy for spreadsheet", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Slim, editable rows: tap a name or a price to fix it.
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items) { index, item ->
                Row(
                    Modifier.fillMaxWidth().height(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SlimField(
                        value = item.name,
                        onValueChange = { v -> save(items.withAt(index, item.copy(name = v))) },
                        pal = pal,
                        modifier = Modifier.weight(1f),
                    )
                    SlimField(
                        value = item.price,
                        onValueChange = { v -> save(items.withAt(index, item.copy(price = v))) },
                        pal = pal,
                        modifier = Modifier.width(84.dp),
                        prefix = "£",
                        alignEnd = true,
                        bold = true,
                        error = normalizePrice(item.price) == null,
                        keyboardType = KeyboardType.Decimal,
                        onBlur = {
                            // "1.5" becomes "1.50" when you leave the box.
                            val n = normalizePrice(item.price)
                            if (n != null && n != item.price) save(items.withAt(index, item.copy(price = n)))
                        },
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
                    TextButton(onClick = { confirmClear = true }) {
                        Text("Clear list", color = Danger)
                    }
                }
            }
        }

        if (badRow != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${badRow.name.ifBlank { "Item" }}: price must be a number like 2.10",
                    color = Danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Color.White,
            title = { Text("Clear the whole list?") },
            text = { Text("This removes all ${items.size} items. Copy them first if you need them.") },
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
