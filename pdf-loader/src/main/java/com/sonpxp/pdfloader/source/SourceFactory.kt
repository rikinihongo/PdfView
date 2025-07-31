package com.sonpxp.pdfloader.source

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Factory class for creating DocumentSource instances
 * Provides convenient methods to create sources from various input types
 */
object SourceFactory {

    /**
     * Creates a DocumentSource from a File
     */
    @JvmStatic
    fun fromFile(file: File): DocumentSource {
        return FileSource(file)
    }

    /**
     * Creates a DocumentSource from a file path
     */
    @JvmStatic
    fun fromFile(filePath: String): DocumentSource {
        return FileSource(File(filePath))
    }

    /**
     * Creates a DocumentSource from a Uri
     */
    @JvmStatic
    fun fromUri(context: Context, uri: Uri): DocumentSource {
        return UriSource(context, uri)
    }

    /**
     * Creates a DocumentSource from a Uri string
     */
    @JvmStatic
    fun fromUri(context: Context, uriString: String): DocumentSource {
        return UriSource(context, Uri.parse(uriString))
    }

    /**
     * Creates a DocumentSource from a byte array
     */
    @JvmStatic
    fun fromBytes(data: ByteArray, name: String = "ByteArray"): DocumentSource {
        return ByteArraySource(data, name)
    }

    /**
     * Creates a DocumentSource from an InputStream
     */
    @JvmStatic
    fun fromStream(inputStream: InputStream, sourceName: String = "InputStream"): DocumentSource {
        return StreamSource(inputStream, sourceName)
    }

    /**
     * Creates a DocumentSource from an InputStream with expected size
     */
    @JvmStatic
    fun fromStream(inputStream: InputStream, expectedSize: Int, sourceName: String = "InputStream"): DocumentSource {
        return StreamSource(inputStream, expectedSize, sourceName)
    }

    /**
     * Creates a DocumentSource from an Android asset
     */
    @JvmStatic
    fun fromAsset(context: Context, assetName: String): DocumentSource {
        return AssetSource(context, assetName)
    }

    /**
     * Creates a DocumentSource from any compatible object
     */
    @JvmStatic
    fun fromAny(context: Context, source: Any): DocumentSource {
        return when (source) {
            is DocumentSource -> source
            is File -> fromFile(source)
            is Uri -> fromUri(context, source)
            is String -> {
                when {
                    source.startsWith("file://") || source.startsWith("content://") ||
                            source.startsWith("http://") || source.startsWith("https://") ->
                        fromUri(context, source)
                    File(source).exists() -> fromFile(source)
                    else -> fromAsset(context, source)
                }
            }
            is ByteArray -> fromBytes(source)
            is InputStream -> fromStream(source)
            else -> throw IllegalArgumentException(
                "Unsupported source type: ${source::class.java.simpleName}"
            )
        }
    }

    /**
     * Validates that a source can be opened and read
     */
    @JvmStatic
    fun isValidSource(source: DocumentSource): Boolean {
        return source.isValid()
    }

    /**
     * Gets information about a source without fully opening it
     */
    @JvmStatic
    fun getSourceInfo(source: DocumentSource): SourceInfo {
        return SourceInfo(
            description = source.getDescription(),
            size = source.getSize(),
            supportsMultipleReads = source.supportsMultipleReads(),
            isValid = source.isValid(),
            type = source::class.java.simpleName
        )
    }

    /**
     * Data class containing basic information about a DocumentSource
     */
    data class SourceInfo(
        val description: String,
        val size: Long,
        val supportsMultipleReads: Boolean,
        val isValid: Boolean,
        val type: String
    ) {
        fun getSizeFormatted(): String {
            return when {
                size < 0 -> "Unknown size"
                size < 1024 -> "$size bytes"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> "${size / (1024 * 1024)} MB"
            }
        }

        override fun toString(): String {
            return "$type: $description, ${getSizeFormatted()}, multiRead=$supportsMultipleReads, valid=$isValid"
        }
    }
}