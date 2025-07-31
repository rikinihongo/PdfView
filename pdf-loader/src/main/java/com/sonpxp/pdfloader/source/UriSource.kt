package com.sonpxp.pdfloader.source

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

/**
 * DocumentSource implementation for Android URIs
 * Supports content:// schemes, file:// schemes, and other URI types
 */
class UriSource(
    private val context: Context,
    private val uri: Uri
) : DocumentSource() {

    private var cachedSize: Long = -1
    private var cachedDisplayName: String? = null
    private var cachedMimeType: String? = null

    @Throws(Exception::class)
    override fun openInputStream(): InputStream {
        return context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open InputStream for URI: $uri")
    }

    override fun getSize(): Long {
        if (cachedSize == -1L) {
            cachedSize = querySize()
        }
        return cachedSize
    }

    override fun getDescription(): String {
        val displayName = getDisplayName()
        val size = getSize()
        val sizeStr = if (size > 0) " (${size} bytes)" else ""
        return "URI: $displayName$sizeStr"
    }

    override fun supportsMultipleReads(): Boolean = true

    override fun isValid(): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the display name of the document
     * @return display name or URI string if not available
     */
    fun getDisplayName(): String {
        if (cachedDisplayName == null) {
            cachedDisplayName = queryDisplayName()
        }
        return cachedDisplayName ?: uri.toString()
    }

    /**
     * Gets the MIME type of the document
     */
    fun getMimeType(): String? {
        if (cachedMimeType == null) {
            cachedMimeType = context.contentResolver.getType(uri)
        }
        return cachedMimeType
    }

    /**
     * Gets the underlying URI
     * @return the Uri object
     */
    fun getUri(): Uri = uri

    /**
     * Checks if this is a content URI
     */
    fun isContentUri(): Boolean = uri.scheme == "content"

    /**
     * Checks if this is a file URI
     */
    fun isFileUri(): Boolean = uri.scheme == "file"

    private fun querySize(): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        cursor.getLong(sizeIndex)
                    } else -1L
                } else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    private fun queryDisplayName(): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UriSource) return false
        return uri == other.uri
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun toString(): String {
        return getDescription()
    }
}
