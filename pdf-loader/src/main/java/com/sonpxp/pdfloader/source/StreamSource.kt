package com.sonpxp.pdfloader.source

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * DocumentSource implementation for InputStreams
 * Note: Since InputStreams can only be read once, this implementation
 * buffers the data internally to support multiple reads
 */
class StreamSource private constructor(
    private val bufferedData: ByteArray,
    private val sourceName: String = "InputStream"
) : DocumentSource() {

    init {
        require(bufferedData.isNotEmpty()) { "InputStream data cannot be empty" }
    }

    /**
     * Primary constructor that takes an InputStream and buffers it
     */
    constructor(inputStream: InputStream, sourceName: String = "InputStream") : this(
        bufferInputStream(inputStream),
        sourceName
    )

    /**
     * Constructor with expected size for better performance
     */
    constructor(inputStream: InputStream, expectedSize: Int, sourceName: String = "InputStream") : this(
        bufferInputStream(inputStream, expectedSize),
        sourceName
    )

    @Throws(Exception::class)
    override fun openInputStream(): InputStream {
        return ByteArrayInputStream(bufferedData)
    }

    override fun getSize(): Long {
        return bufferedData.size.toLong()
    }

    override fun getDescription(): String {
        return "$sourceName (buffered, ${bufferedData.size} bytes)"
    }

    override fun supportsMultipleReads(): Boolean = true

    override fun isValid(): Boolean = bufferedData.isNotEmpty()

    /**
     * Gets a copy of the buffered data
     * @return copy of the buffered byte array
     */
    fun getBufferedData(): ByteArray = bufferedData.copyOf()

    /**
     * Gets the source name
     */
    fun getSourceName(): String = sourceName

    /**
     * Creates a StreamSource from an InputStream
     */
    companion object {
        @JvmStatic
        fun from(inputStream: InputStream, sourceName: String = "InputStream"): StreamSource {
            return StreamSource(inputStream, sourceName)
        }

        @JvmStatic
        fun from(inputStream: InputStream, expectedSize: Int, sourceName: String = "InputStream"): StreamSource {
            return StreamSource(inputStream, expectedSize, sourceName)
        }

        /**
         * Buffers an InputStream into a ByteArray
         */
        private fun bufferInputStream(inputStream: InputStream): ByteArray {
            return inputStream.use { stream ->
                ByteArrayOutputStream().use { buffer ->
                    stream.copyTo(buffer)
                    buffer.toByteArray()
                }
            }
        }

        /**
         * Buffers an InputStream with expected size for better performance
         */
        private fun bufferInputStream(inputStream: InputStream, expectedSize: Int): ByteArray {
            return inputStream.use { stream ->
                ByteArrayOutputStream(expectedSize).use { buffer ->
                    stream.copyTo(buffer)
                    buffer.toByteArray()
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StreamSource) return false
        return bufferedData.contentEquals(other.bufferedData) && sourceName == other.sourceName
    }

    override fun hashCode(): Int {
        var result = bufferedData.contentHashCode()
        result = 31 * result + sourceName.hashCode()
        return result
    }

    override fun toString(): String {
        return getDescription()
    }
}