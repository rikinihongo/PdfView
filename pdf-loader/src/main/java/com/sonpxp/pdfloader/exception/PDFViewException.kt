package com.sonpxp.pdfloader.exception

/**
 * Base exception class for all PDF viewer related errors
 */
/**
 * Base exception class for all PDF viewer related errors
 */
open class PDFViewException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Gets the error category for this exception
     */
    open fun getCategory(): String = "PDF_ERROR"

    /**
     * Gets user-friendly error message
     */
    open fun getUserMessage(): String = message ?: "An error occurred with the PDF viewer"

    /**
     * Checks if this error is recoverable
     */
    open fun isRecoverable(): Boolean = false
}