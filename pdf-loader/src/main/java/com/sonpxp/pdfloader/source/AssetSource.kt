package com.sonpxp.pdfloader.source

import android.content.Context
import java.io.InputStream

/**
 * DocumentSource implementation for Android assets
 * Provides access to PDF files stored in the assets folder
 */
class AssetSource(
    private val context: Context,
    private val assetName: String
) : DocumentSource() {

    private var cachedSize: Long = -1

    init {
        require(assetName.isNotBlank()) { "Asset name cannot be blank" }

        // Validate that asset exists
        try {
            context.assets.open(assetName).use { }
        } catch (e: Exception) {
            throw IllegalArgumentException("Asset '$assetName' does not exist or cannot be opened", e)
        }
    }

    @Throws(Exception::class)
    override fun openInputStream(): InputStream {
        return context.assets.open(assetName)
    }

    override fun getSize(): Long {
        if (cachedSize == -1L) {
            cachedSize = calculateSize()
        }
        return cachedSize
    }

    override fun getDescription(): String {
        val size = getSize()
        val sizeStr = if (size > 0) " (${size} bytes)" else ""
        return "Asset: $assetName$sizeStr"
    }

    override fun supportsMultipleReads(): Boolean = true

    override fun isValid(): Boolean {
        return try {
            context.assets.open(assetName).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the asset name
     * @return the asset file name
     */
    fun getAssetName(): String = assetName

    /**
     * Gets the asset folder path (if any)
     */
    fun getAssetFolder(): String? {
        val lastSlash = assetName.lastIndexOf('/')
        return if (lastSlash > 0) assetName.substring(0, lastSlash) else null
    }

    /**
     * Gets just the file name without folder path
     */
    fun getFileName(): String {
        val lastSlash = assetName.lastIndexOf('/')
        return if (lastSlash >= 0) assetName.substring(lastSlash + 1) else assetName
    }

    /**
     * Lists all assets in the same folder
     */
    fun listSiblingAssets(): Array<String>? {
        return try {
            val folder = getAssetFolder() ?: ""
            context.assets.list(folder)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateSize(): Long {
        return try {
            // Try using AssetFileDescriptor first (more efficient)
            context.assets.openFd(assetName).use { fd ->
                fd.length
            }
        } catch (e: Exception) {
            // Fallback to stream-based calculation
            try {
                context.assets.open(assetName).use { stream ->
                    var size = 0L
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (stream.read(buffer).also { bytesRead = it } != -1) {
                        size += bytesRead
                    }
                    size
                }
            } catch (e2: Exception) {
                -1L
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssetSource) return false
        return assetName == other.assetName
    }

    override fun hashCode(): Int {
        return assetName.hashCode()
    }

    override fun toString(): String {
        return getDescription()
    }
}