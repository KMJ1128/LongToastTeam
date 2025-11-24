package com.longtoast.bilbil.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

object ImageUtil {

    fun bitmapToMultipart(context: Context, bitmap: Bitmap, partName: String): MultipartBody.Part {
        val fileName = "image_${UUID.randomUUID()}.jpg"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }

        val requestFile = file.asRequestBody("image/jpeg".toMediaType())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }

    fun uriToMultipart(context: Context, uri: Uri, partName: String): MultipartBody.Part? {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        val fileName = "image_${UUID.randomUUID()}.$extension"

        val file = File(context.cacheDir, fileName)
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        inputStream?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        val requestFile = file.asRequestBody(mimeType.toMediaType())
        return MultipartBody.Part.createFormData(partName, file.name, requestFile)
    }
}
