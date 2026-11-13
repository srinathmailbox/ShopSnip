package com.snipnotes.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Snip(
    val id: String,
    val note: String,
    val time: Long,
    val pinned: Boolean = false,
    val starred: Boolean = false,
)

data class PriceItem(val name: String, val price: String)

data class ShopItem(val name: String, val done: Boolean = false)

fun <T> List<T>.withAt(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }

/** Everything is stored privately on the phone. No network, no accounts. */
class Store(context: Context) {
    private val snipDir = File(context.filesDir, "snips").apply { mkdirs() }
    private val snipIndex = File(context.filesDir, "snips.json")
    private val priceFile = File(context.filesDir, "prices.json")
    private val shopFile = File(context.filesDir, "shop.json")

    fun snipFile(id: String): File = File(snipDir, "$id.jpg")

    /** Pinned snips first, then newest first. */
    private fun sorted(list: List<Snip>): List<Snip> =
        list.sortedWith(compareByDescending<Snip> { it.pinned }.thenByDescending { it.time })

    fun loadSnips(): List<Snip> = runCatching {
        val arr = JSONArray(snipIndex.readText())
        sorted((0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Snip(
                id = o.getString("id"),
                note = o.optString("note"),
                time = o.optLong("time"),
                pinned = o.optBoolean("pinned", false),
                starred = o.optBoolean("starred", false),
            )
        })
    }.getOrDefault(emptyList())

    private fun writeSnips(list: List<Snip>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("note", it.note)
                    .put("time", it.time)
                    .put("pinned", it.pinned)
                    .put("starred", it.starred)
            )
        }
        snipIndex.writeText(arr.toString())
    }

    fun addSnip(bitmap: Bitmap, note: String): List<Snip> {
        val time = System.currentTimeMillis()
        val id = time.toString()
        snipFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        val list = loadSnips() + Snip(id, note, time)
        writeSnips(list)
        return sorted(list)
    }

    fun updateSnips(ids: Set<String>, change: (Snip) -> Snip): List<Snip> {
        val list = loadSnips().map { if (it.id in ids) change(it) else it }
        writeSnips(list)
        return sorted(list)
    }

    fun deleteSnips(ids: Set<String>): List<Snip> {
        ids.forEach { snipFile(it).delete() }
        val list = loadSnips().filter { it.id !in ids }
        writeSnips(list)
        return list
    }

    fun loadPrices(): List<PriceItem> = runCatching {
        val arr = JSONArray(priceFile.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PriceItem(o.getString("name"), o.getString("price"))
        }
    }.getOrDefault(emptyList())

    fun savePrices(list: List<PriceItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("price", it.price)) }
        priceFile.writeText(arr.toString())
    }

    fun loadShop(): List<ShopItem> = runCatching {
        val arr = JSONArray(shopFile.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            ShopItem(o.getString("name"), o.optBoolean("done", false))
        }
    }.getOrDefault(emptyList())

    fun saveShop(list: List<ShopItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("done", it.done)) }
        shopFile.writeText(arr.toString())
    }
}

/** Turns a camera frame into a small upright bitmap (longest side 1280 px). */
fun ImageProxy.toSmallBitmap(maxSide: Int = 1280): Bitmap {
    val raw = toBitmap()
    val matrix = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    val scale = maxSide.toFloat() / maxOf(raw.width, raw.height)
    if (scale < 1f) matrix.postScale(scale, scale)
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
}

/** Decodes a saved photo at roughly the requested size to keep memory low. */
fun decodeSampled(file: File, target: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
