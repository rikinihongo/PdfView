package com.sonpxp.pdfloader.source

import java.io.InputStream

/**
 * Abstract interface for PDF document sources
 * Provides a unified way to handle different input types (files, URIs, streams, etc.)
 */
abstract class DocumentSource {

    /**
     * Opens an InputStream to read the PDF document
     * @return InputStream for reading the PDF data
     * @throws Exception if the source cannot be opened
     */
    @Throws(Exception::class)
    abstract fun openInputStream(): InputStream

    /**
     * Gets the size of the document in bytes
     * @return size in bytes, or -1 if unknown
     */
    abstract fun getSize(): Long

    /**
     * Gets a human-readable description of the source
     * @return description string for debugging/logging
     */
    abstract fun getDescription(): String

    /**
     * Releases any resources held by this source
     * Should be called when the source is no longer needed
     */
    open fun release() {
        // Default implementation does nothing
        // Subclasses can override to clean up resources
    }

    /**
     * Checks if this source supports multiple concurrent reads
     * @return true if multiple InputStreams can be opened simultaneously
     */
    open fun supportsMultipleReads(): Boolean = false

    /**
     * Validates that the source is accessible and readable
     * @return true if the source can be read successfully
     */
    open fun isValid(): Boolean {
        return try {
            openInputStream().use { it.available() >= 0 }
        } catch (e: Exception) {
            false
        }
    }
}