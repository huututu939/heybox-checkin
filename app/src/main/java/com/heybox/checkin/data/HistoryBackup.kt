package com.heybox.checkin.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * 每次执行后把历史自动备份到公共目录的 Documents/盒签/盒签备份.json。
 *
 * 为什么不做开关：备份文件不含账号凭据（那些本来就换不了机器），只有签到记录，
 * 默认开着收益远大于打扰；换机或清数据后这个文件是唯一的完整历史来源。
 *
 * Android 10+ 走 MediaStore 的 Documents 集合，应用给自己的文件不用存储权限；
 * 旧版本退回应用专属外部目录（卸载会被清，但至少留了层底）。
 */
object HistoryBackup {

    private const val DIRECTORY = "Documents/盒签"
    private const val FILE_NAME = "盒签备份.json"

    fun write(context: Context, history: List<RunRecord>) {
        val text = historyJson(history)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToMediaStore(context, text)
            } else {
                writeToFile(context, text)
            }
        }
    }

    /** 同名文件覆盖写，不每天堆一个新备份。 */
    private fun writeToMediaStore(context: Context, text: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val existing = findExisting(context, collection)
        val uri = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, DIRECTORY)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            },
        ) ?: return
        resolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun findExisting(context: Context, collection: Uri): Uri? {
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            arrayOf("$DIRECTORY/", FILE_NAME),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(collection, cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)))
            }
        }
        return null
    }

    private fun writeToFile(context: Context, text: String) {
        val dir = File(context.getExternalFilesDir(null), "backup").apply { mkdirs() }
        File(dir, FILE_NAME).writeText(text, Charsets.UTF_8)
    }
}
