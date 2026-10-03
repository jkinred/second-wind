package io.github.jkinred.secondwind.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

/** Single JSON file in app-private storage; whole-file atomic rewrite. */
class Store(context: Context) {
    private val file = File(context.filesDir, "state.json")
    /** Real data parked here while demo data is loaded; its presence is the "demo active" flag. */
    private val backup = File(context.filesDir, "state.real.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun load(): AppData = read(file) ?: AppData()

    fun save(data: AppData) = write(file, data)

    val hasBackup: Boolean get() = backup.exists()
    fun saveBackup(data: AppData) = write(backup, data)
    fun loadBackup(): AppData? = read(backup)
    fun clearBackup() { backup.delete() }

    private fun read(f: File): AppData? = runCatching {
        if (f.exists()) json.decodeFromString(AppData.serializer(), f.readText()) else null
    }.getOrNull()

    private fun write(f: File, data: AppData) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), data))
        tmp.renameTo(f)
    }
}
