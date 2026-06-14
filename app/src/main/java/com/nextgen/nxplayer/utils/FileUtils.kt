package com.nextgen.nxplayer.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

object FileUtils {
    fun moveToPrivateFolder(context: Context, uri: Uri): Boolean {
        return try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return false
            val displayName = getFileName(context, uri) ?: "video.mp4"
            val dest = File(context.filesDir, "vault/$displayName")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            // Delete original (optional)
            contentResolver.delete(uri, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) else null
        }
    }
}