package com.sonpxp.pdfloader.source

import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * DocumentSource implementation for byte arrays
 * Useful for in-memory PDF data or data received from network
 */
class ByteArraySource(private val data: ByteArray, private val name: String = "ByteArray") : DocumentSource() {

    init {
        require(data.isNotEmpty()) { "Byte array cannot be empty" }
    }

    @Throws(Exception::class)
    override fun openInputStream(): InputStream {
        return ByteArrayInputStream(data)
    }

    override fun getSize(): Long {
        return data.size.toLong()
    }

    override fun getDescription(): String {
        return "$name (${data.size} bytes)"
    }

    override fun supportsMultipleReads(): Boolean = true

    override fun isValid(): Boolean = data.isNotEmpty()

    /**
     * Gets a copy of the underlying byte array
     * @return copy of the byte array
     */
    fun getData(): ByteArray = data.copyOf()

    /**
     * Gets the underlying byte array (direct reference)
     * Warning: Modifying this array will affect the source
     * @return direct reference to the byte array
     */
    fun getDataDirect(): ByteArray = data

    /**
     * Gets the name/identifier for this byte array source
     */
    fun getName(): String = name

    /**
     * Creates a new ByteArraySource with a different name
     */
    fun withName(newName: String): ByteArraySource {
        return ByteArraySource(data, newName)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArraySource) return false
        return data.contentEquals(other.data) && name == other.name
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString(): String {
        return getDescription()
    }
}