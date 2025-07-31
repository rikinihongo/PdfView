package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when configuration is invalid
 */
class ConfigurationException(
    val parameter: String,
    message: String
) : PDFViewException("Configuration error for '$parameter': $message") {

    override fun getCategory(): String = "CONFIG_ERROR"

    override fun getUserMessage(): String = "Invalid configuration: $message"

    override fun isRecoverable(): Boolean = true
}