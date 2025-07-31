package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when document source is invalid or inaccessible
 */
class InvalidSourceException(
    val sourcePath: String,
    message: String,
    cause: Throwable? = null
) : PDFViewException("Invalid source '$sourcePath': $message", cause) {

    override fun getCategory(): String = "SOURCE_ERROR"

    override fun getUserMessage(): String = "Cannot access the PDF file. Please check if the file exists and is readable."

    override fun isRecoverable(): Boolean = false
}