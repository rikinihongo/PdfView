package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when PDF document loading fails
 */
class DocumentLoadException(
    message: String,
    cause: Throwable? = null
) : PDFViewException("Document load error: $message", cause) {

    override fun getCategory(): String = "LOAD_ERROR"

    override fun getUserMessage(): String = "Failed to load the PDF document. The file may be corrupted or in an unsupported format."

    override fun isRecoverable(): Boolean = false
}
