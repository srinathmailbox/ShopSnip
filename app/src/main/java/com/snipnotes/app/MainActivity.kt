package com.snipnotes.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent { App(store) }
    }
}

@Composable
fun App(store: Store) {
    // mode 0 = Snap & Reference, mode 1 = Scan Prices (sub 0 = Item + price, sub 1 = Shopping list)
    var mode by rememberSaveable { mutableStateOf(0) }
    var sub by rememberSaveable { mutableStateOf(0) }
    // True while a snip is open or snips are being selected: the page takes over the screen.
    var full by remember { mutableStateOf(false) }

    val pal = when {
        mode == 0 -> SnapPalette
        sub == 0 -> PricePalette
        else -> ShopPalette
    }
    val ctx = LocalContext.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    val help = when {
        mode == 0 -> "Take a small photo and add a short note. Tap a snip to edit it, or press and hold to pin, star or delete."
        sub == 0 -> "Point at a shelf label. Check the item and price, then tap Add. Tap any name or price in the list to fix it."
        else -> "Point at a product name. Tap Add, and fix any spelling by tapping the name in the list. Tick items off as you shop."
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = pal.accent,
            onPrimary = Color.White,
            background = pal.bg,
            surface = Color.White,
            onSurface = Ink,
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
        ) {
            if (!full) {
                ModeToggle(mode, pal) { mode = it }
                if (mode == 1) {
                    Spacer(Modifier.height(10.dp))
                    SubToggle(sub, pal) { sub = it }
                }
                Spacer(Modifier.height(10.dp))
                HelpNote(help, pal)
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.weight(1f)) {
                val request = { launcher.launch(Manifest.permission.CAMERA) }
                when {
                    mode == 0 -> SnapScreen(store, pal, granted, request) { full = it }
                    sub == 0 -> PriceScreen(store, pal, granted, request)
                    else -> ShopScreen(store, pal, granted, request)
                }
            }
        }
    }
}
