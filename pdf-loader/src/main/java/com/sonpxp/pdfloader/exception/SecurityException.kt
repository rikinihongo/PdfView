package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when security restrictions prevent access
 */
open class SecurityException(
    message: String,
    cause: Throwable? = null
) : PDFViewException("Security error: $message", cause) {

    override fun getCategory(): String = "SECURITY_ERROR"

    override fun getUserMessage(): String = "Access denied. The PDF may be password protected or have restricted permissions."

    override fun isRecoverable(): Boolean = true // User might provide correct password
}