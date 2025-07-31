package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when PDF format is not supported
 */
class UnsupportedFormatException(
    val format: String,
    message: String
) : PDFViewException("Unsupported format '$format': $message") {

    override fun getCategory(): String = "FORMAT_ERROR"

    override fun getUserMessage(): String = "This PDF format is not supported or the file is corrupted."

    override fun isRecoverable(): Boolean = false
}