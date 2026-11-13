package com.snipnotes.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0F1720)
val Danger = Color(0xFFB91C1C)

/** One colour set per page, so you always know where you are. */
class Palette(
    val accent: Color,
    val tint: Color,
    val bg: Color,
    val track: Color,
    val inactive: Color,
    val bracket: Color,
    val cam: Color,
    val fieldBorder: Color,
    val cellBorder: Color,
    val label: Color,
)

// Page 1: Snap & Reference (teal)
val SnapPalette = Palette(
    accent = Color(0xFF0F766E),
    tint = Color(0xFFE3F2F0),
    bg = Color(0xFFF4F6F8),
    track = Color(0xFFE6EBEF),
    inactive = Color(0xFF3E4852),
    bracket = Color(0xFF5EEAD4),
    cam = Color(0xFF121820),
    fieldBorder = Color(0xFF8A97A3),
    cellBorder = Color(0xFFC5CED6),
    label = Color(0xFF4B5560),
)

// Page 2: Item + price (blue)
val PricePalette = Palette(
    accent = Color(0xFF1D4ED8),
    tint = Color(0xFFE0EAFB),
    bg = Color(0xFFF3F6FC),
    track = Color(0xFFE3EAF6),
    inactive = Color(0xFF34425A),
    bracket = Color(0xFF93C5FD),
    cam = Color(0xFF101827),
    fieldBorder = Color(0xFF8FA3C4),
    cellBorder = Color(0xFFB9C6DC),
    label = Color(0xFF4A5878),
)

// Page 3: Shopping list (purple)
val ShopPalette = Palette(
    accent = Color(0xFF6D28D9),
    tint = Color(0xFFEDE5FB),
    bg = Color(0xFFF7F4FC),
    track = Color(0xFFEBE4F6),
    inactive = Color(0xFF43395A),
    bracket = Color(0xFFC4B5FD),
    cam = Color(0xFF17121F),
    fieldBorder = Color(0xFFA594C6),
    cellBorder = Color(0xFFCFC3E4),
    label = Color(0xFF594D75),
)

@Composable
fun ModeToggle(mode: Int, pal: Palette, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pal.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("Snap & Reference", "Scan Prices").forEachIndexed { index, label ->
            val selected = index == mode
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) pal.accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color.White else pal.inactive,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** "Item + price" or "Shopping list", shown under the main buttons on page 2. */
@Composable
fun SubToggle(sub: Int, pal: Palette, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Item + price", "Shopping list").forEachIndexed { index, label ->
            val selected = index == sub
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) pal.tint else Color.White)
                    .border(2.dp, if (selected) pal.accent else pal.cellBorder, RoundedCornerShape(50))
                    .selectable(selected = selected, role = Role.Tab, onClick = { onChange(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = pal.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        label,
                        color = if (selected) pal.accent else pal.inactive,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun HelpNote(text: String, pal: Palette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pal.tint)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = pal.accent, modifier = Modifier.size(20.dp))
        Text(text, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun Brackets(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val inset = 36.dp.toPx()
        val len = 28.dp.toPx()
        val stroke = 3.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Round)
        }
        corner(inset, inset, 1f, 1f)
        corner(size.width - inset, inset, -1f, 1f)
        corner(inset, size.height - inset, 1f, -1f)
        corner(size.width - inset, size.height - inset, -1f, -1f)
    }
}

/** A small push-pin drawn by hand (no extra icon library needed). */
@Composable
fun PinGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(0.34f * w, 0.08f * h)
            lineTo(0.66f * w, 0.08f * h)
            lineTo(0.60f * w, 0.46f * h)
            lineTo(0.78f * w, 0.62f * h)
            lineTo(0.78f * w, 0.70f * h)
            lineTo(0.22f * w, 0.70f * h)
            lineTo(0.22f * w, 0.62f * h)
            lineTo(0.40f * w, 0.46f * h)
            close()
        }
        drawPath(p, color)
        drawLine(color, Offset(0.5f * w, 0.70f * h), Offset(0.5f * w, 0.96f * h), 0.1f * w, StrokeCap.Round)
    }
}

/** Rounded camera window with corner marks, an optional status chip and a permission prompt. */
@Composable
fun CameraFrame(
    pal: Palette,
    height: Dp,
    granted: Boolean,
    onRequest: () -> Unit,
    chip: String?,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(pal.cam),
    ) {
        if (granted) {
            content()
            Brackets(pal.bracket, Modifier.fillMaxSize())
        } else {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Camera access is needed", color = Color(0xFFD5DCE3), fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = pal.accent)) {
                    Text("Allow camera")
                }
            }
        }
        if (granted && chip != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(chip, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun fieldColors(pal: Palette) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = pal.accent,
    unfocusedBorderColor = pal.fieldBorder,
    focusedLabelColor = pal.accent,
    unfocusedLabelColor = pal.label,
    cursorColor = pal.accent,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
)

/**
 * A slim 28dp text box for list rows (same 15sp text as before, tighter box).
 * Red border = a value that is not valid. Thick accent border = being edited.
 */
@Composable
fun SlimField(
    value: String,
    onValueChange: (String) -> Unit,
    pal: Palette,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    alignEnd: Boolean = false,
    bold: Boolean = false,
    error: Boolean = false,
    done: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onBlur: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val borderColor = when {
        error -> Danger
        focused -> pal.accent
        else -> pal.cellBorder
    }
    val borderWidth = if (error || focused) 2.dp else 1.dp
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 15.sp,
            color = if (done) pal.label else Ink,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        ),
        cursorBrush = SolidColor(pal.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .height(28.dp)
            .clip(shape)
            .background(if (done) pal.track else Color.White)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged {
                if (focused && !it.isFocused) onBlur()
                focused = it.isFocused
            },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (prefix != null) {
                    Text(prefix, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.width(2.dp))
                }
                Box(
                    Modifier.weight(1f),
                    contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
                ) { inner() }
            }
        },
    )
}

/** Small tick box for the shopping list. */
@Composable
fun TickBox(checked: Boolean, pal: Palette, description: String, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) pal.accent else Color.White)
                .border(2.dp, if (checked) pal.accent else pal.fieldBorder, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Replaces the old helper so list code stays short. */
fun <T> List<T>.removeAtIndex(index: Int): List<T> = filterIndexed { i, _ -> i != index }
