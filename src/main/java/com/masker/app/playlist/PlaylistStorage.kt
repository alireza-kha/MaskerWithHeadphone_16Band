package com.masker.app.playlist

import android.content.Context
import com.masker.app.storage.MaskerStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * ذخیره‌سازی فهرست پلی‌لیست به‌صورت یک فایل JSON در Documents/Masker/History (کنار تاریخچه
 * آزمون‌ها)؛ فایل‌های صوتی واقعی در Documents/Masker/Playlist نگه‌داری می‌شوند.
 */
object PlaylistStorage {
    private const val FILE_NAME = "playlist.json"

    fun loadTracks(context: Context): List<PlaylistTrack> {
        val file = storageFile(context)
        val json = try {
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<PlaylistTrack>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlaylistTrack(
                        id = obj.optString("id"),
                        fileName = obj.optString("fileName"),
                        title = obj.optString("title"),
                        addedAtMillis = obj.optLong("addedAt")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTracks(context: Context, tracks: List<PlaylistTrack>) {
        val arr = JSONArray()
        for (t in tracks) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("fileName", t.fileName)
            obj.put("title", t.title)
            obj.put("addedAt", t.addedAtMillis)
            arr.put(obj)
        }
        try {
            storageFile(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun addTrack(context: Context, track: PlaylistTrack) {
        val all = loadTracks(context).toMutableList()
        all.add(track)
        saveTracks(context, all)
    }

    /** حذف یک آهنگ از فهرست و پاک کردن فایل صوتی مربوطه از Documents/Masker/Playlist */
    fun removeTrack(context: Context, trackId: String) {
        removeTracks(context, setOf(trackId))
    }

    /** حذف گروهی چند آهنگ انتخاب‌شده (و فایل‌های صوتی/تامنیل مربوطه) با فقط یک بار خواندن/نوشتن فایل فهرست */
    fun removeTracks(context: Context, trackIds: Set<String>) {
        if (trackIds.isEmpty()) return
        val all = loadTracks(context).toMutableList()
        val removed = all.filter { it.id in trackIds }
        all.removeAll { it.id in trackIds }
        saveTracks(context, all)
        for (t in removed) {
            try {
                File(MaskerStorage.playlistDir(context), t.fileName).delete()
            } catch (_: Exception) {
            }
            PlaylistThumbnails.deleteThumbnail(context, t.id)
        }
    }

    /** حذف کل پلی‌لیست (همه فایل‌های صوتی و تامنیل‌ها) */
    fun removeAllTracks(context: Context) {
        val all = loadTracks(context)
        saveTracks(context, emptyList())
        for (t in all) {
            try {
                File(MaskerStorage.playlistDir(context), t.fileName).delete()
            } catch (_: Exception) {
            }
            PlaylistThumbnails.deleteThumbnail(context, t.id)
        }
    }

    private fun storageFile(context: Context): File = File(MaskerStorage.historyDir(context), FILE_NAME)
}
