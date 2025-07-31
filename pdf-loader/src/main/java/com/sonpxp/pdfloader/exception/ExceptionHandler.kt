package com.sonpxp.pdfloader.exception

/**
 * Utility class for handling PDF exceptions
 */
object ExceptionHandler {

    /**
     * Wraps a generic exception into an appropriate PDFViewException
     */
    @JvmStatic
    fun wrap(throwable: Throwable, context: String = ""): PDFViewException {
        if (throwable is PDFViewException) {
            return throwable
        }

        return when (throwable) {
            is OutOfMemoryError -> MemoryException(
                message = "Out of memory${if (context.isNotEmpty()) " during $context" else ""}",
                cause = throwable
            )
            is java.security.AccessControlException -> SecurityException(
                message = "Access denied${if (context.isNotEmpty()) " for $context" else ""}",
                cause = throwable
            )
            is java.io.FileNotFoundException -> InvalidSourceException(
                sourcePath = context,
                message = "File not found",
                cause = throwable
            )
            is java.io.IOException -> InvalidSourceException(
                sourcePath = context,
                message = "IO error: ${throwable.message}",
                cause = throwable
            )
            is InterruptedException -> ThreadingException(
                message = "Thread interrupted${if (context.isNotEmpty()) " during $context" else ""}",
                cause = throwable
            )
            else -> PDFViewException(
                message = "Unexpected error${if (context.isNotEmpty()) " during $context" else ""}: ${throwable.message}",
                cause = throwable
            )
        }
    }

    /**
     * Gets the most specific error message from a throwable chain
     */
    @JvmStatic
    fun getDetailedMessage(throwable: Throwable): String {
        val messages = mutableListOf<String>()
        var current: Throwable? = throwable

        while (current != null && messages.size < 3) {
            current.message?.let { message ->
                if (message.isNotBlank() && !messages.contains(message)) {
                    messages.add(message)
                }
            }
            current = current.cause
        }

        return if (messages.isNotEmpty()) {
            messages.joinToString(" → ")
        } else {
            throwable.javaClass.simpleName
        }
    }

    /**
     * Logs an exception with appropriate level
     */
    @JvmStatic
    fun logException(tag: String, exception: Throwable, context: String = "") {
        val contextStr = if (context.isNotEmpty()) " [$context]" else ""
        val message = "${exception.javaClass.simpleName}$contextStr: ${exception.message}"

        when (exception) {
            is MemoryException -> android.util.Log.w(tag, message, exception)
            is ConfigurationException -> android.util.Log.w(tag, message, exception)
            is SecurityException -> android.util.Log.i(tag, message) // Don't log stack trace for security
            else -> android.util.Log.e(tag, message, exception)
        }
    }
}