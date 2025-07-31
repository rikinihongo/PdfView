package com.sonpxp.pdfloader.exception

/**
 * Exception thrown for threading-related errors
 */
class ThreadingException(
    message: String,
    cause: Throwable? = null
) : PDFViewException("Threading error: $message", cause) {

    override fun getCategory(): String = "THREAD_ERROR"

    override fun getUserMessage(): String = "An internal threading error occurred. Please try again."

    override fun isRecoverable(): Boolean = true
}