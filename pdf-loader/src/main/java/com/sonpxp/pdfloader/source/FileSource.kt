package com.sonpxp.pdfloader.source

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * DocumentSource implementation for local files
 */
class FileSource(private val file: File) : DocumentSource() {

    init {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        require(file.isFile) { "Path is not a file: ${file.absolutePath}" }
        require(file.canRead()) { "File is not readable: ${file.absolutePath}" }
    }

    @Throws(Exception::class)
    override fun openInputStream(): InputStream {
        return FileInputStream(file)
    }

    override fun getSize(): Long {
        return file.length()
    }

    override fun getDescription(): String {
        return "File: ${file.absolutePath} (${getSize()} bytes)"
    }

    override fun supportsMultipleReads(): Boolean = true

    override fun isValid(): Boolean {
        return file.exists() && file.isFile && file.canRead()
    }

    /**
     * Gets the underlying file
     * @return the File object
     */
    fun getFile(): File = file

    /**
     * Gets the file name without path
     */
    fun getFileName(): String = file.name

    /**
     * Gets the file extension
     */
    fun getFileExtension(): String = file.extension

    /**
     * Gets the parent directory
     */
    fun getParentDirectory(): File? = file.parentFile

    /**
     * Gets the last modified timestamp
     */
    fun getLastModified(): Long = file.lastModified()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileSource) return false
        return file == other.file
    }

    override fun hashCode(): Int {
        return file.hashCode()
    }

    override fun toString(): String {
        return getDescription()
    }
}