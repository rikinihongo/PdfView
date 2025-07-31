package com.sonpxp.pdfloader.listeners.error

/**
 * Enhanced error handler utility class for managing and categorizing errors
 */
class ErrorHandler {

    companion object {
        /**
         * Categories of errors that can occur
         */
        enum class ErrorCategory {
            /** Errors related to loading the document */
            LOADING,

            /** Errors related to rendering pages */
            RENDERING,

            /** Errors related to memory management */
            MEMORY,

            /** Errors related to security/permissions */
            SECURITY,

            /** Errors related to file I/O */
            IO,

            /** Errors related to network operations */
            NETWORK,

            /** Errors related to user interactions */
            INTERACTION,

            /** Errors related to configuration */
            CONFIGURATION,

            /** Unknown or uncategorized errors */
            UNKNOWN
        }

        /**
         * Error severity levels
         */
        enum class ErrorSeverity {
            /** Low severity - warning level */
            LOW,

            /** Medium severity - error but recoverable */
            MEDIUM,

            /** High severity - critical error */
            HIGH,

            /** Fatal severity - application cannot continue */
            FATAL
        }

        /**
         * Categorizes an error based on its type and message
         * @param error the error to categorize
         * @return the error category
         */
        @JvmStatic
        fun categorizeError(error: Throwable): ErrorCategory {
            return when {
                error is SecurityException -> ErrorCategory.SECURITY
                error is OutOfMemoryError -> ErrorCategory.MEMORY
                error is java.io.IOException -> ErrorCategory.IO
                error is java.net.UnknownHostException -> ErrorCategory.NETWORK
                error is java.net.SocketTimeoutException -> ErrorCategory.NETWORK
                error is IllegalArgumentException -> ErrorCategory.CONFIGURATION
                error is IllegalStateException -> ErrorCategory.CONFIGURATION
                error.message?.contains("render", ignoreCase = true) == true -> ErrorCategory.RENDERING
                error.message?.contains("load", ignoreCase = true) == true -> ErrorCategory.LOADING
                error.message?.contains("tap", ignoreCase = true) == true -> ErrorCategory.INTERACTION
                error.message?.contains("zoom", ignoreCase = true) == true -> ErrorCategory.INTERACTION
                else -> ErrorCategory.UNKNOWN
            }
        }

        /**
         * Determines error severity
         * @param error the error to assess
         * @return the error severity
         */
        @JvmStatic
        fun getErrorSeverity(error: Throwable): ErrorSeverity {
            return when {
                error is OutOfMemoryError -> ErrorSeverity.FATAL
                error is SecurityException -> ErrorSeverity.HIGH
                error is java.io.FileNotFoundException -> ErrorSeverity.HIGH
                error is java.net.UnknownHostException -> ErrorSeverity.MEDIUM
                error is IllegalArgumentException -> ErrorSeverity.MEDIUM
                error.message?.contains("fatal", ignoreCase = true) == true -> ErrorSeverity.FATAL
                error.message?.contains("critical", ignoreCase = true) == true -> ErrorSeverity.HIGH
                else -> ErrorSeverity.LOW
            }
        }

        /**
         * Gets a user-friendly error message
         * @param error the error to get a message for
         * @return user-friendly error message
         */
        @JvmStatic
        fun getUserFriendlyMessage(error: Throwable): String {
            return when (categorizeError(error)) {
                ErrorCategory.LOADING -> "Failed to load the PDF document. Please check if the file exists and is valid."
                ErrorCategory.RENDERING -> "Failed to render the PDF page. The document may be corrupted."
                ErrorCategory.MEMORY -> "Not enough memory to display the PDF. Try closing other apps."
                ErrorCategory.SECURITY -> "Access denied. The PDF may be password protected or have restricted permissions."
                ErrorCategory.IO -> "Failed to read the PDF file. Please check if the file is accessible."
                ErrorCategory.NETWORK -> "Network error occurred while loading the PDF. Please check your connection."
                ErrorCategory.INTERACTION -> "An error occurred while processing your interaction. Please try again."
                ErrorCategory.CONFIGURATION -> "Invalid configuration settings. Please check your setup."
                ErrorCategory.UNKNOWN -> "An unexpected error occurred: ${error.message ?: "Unknown error"}"
            }
        }

        /**
         * Determines if an error is recoverable
         * @param error the error to check
         * @return true if the error might be recoverable
         */
        @JvmStatic
        fun isRecoverable(error: Throwable): Boolean {
            return when (categorizeError(error)) {
                ErrorCategory.MEMORY -> true // Might recover if memory is freed
                ErrorCategory.IO -> true // Might recover if file becomes accessible
                ErrorCategory.NETWORK -> true // Might recover if network is restored
                ErrorCategory.INTERACTION -> true // User can try again
                ErrorCategory.CONFIGURATION -> true // Configuration can be fixed
                ErrorCategory.RENDERING -> false // Usually indicates corrupted document
                ErrorCategory.LOADING -> false // Usually indicates invalid document
                ErrorCategory.SECURITY -> false // Requires user action (password)
                ErrorCategory.UNKNOWN -> false // Unknown errors are assumed non-recoverable
            }
        }

        /**
         * Gets suggested actions for an error
         * @param error the error to get suggestions for
         * @return list of suggested actions
         */
        @JvmStatic
        fun getSuggestedActions(error: Throwable): List<String> {
            return when (categorizeError(error)) {
                ErrorCategory.LOADING -> listOf(
                    "Verify the file is a valid PDF",
                    "Check if the file is corrupted",
                    "Try opening a different PDF file",
                    "Ensure the file path is correct"
                )
                ErrorCategory.RENDERING -> listOf(
                    "Try reducing the zoom level",
                    "Switch to low quality rendering mode",
                    "Close other apps to free memory",
                    "The PDF may be corrupted"
                )
                ErrorCategory.MEMORY -> listOf(
                    "Close other applications",
                    "Reduce the cache size",
                    "Enable low memory mode",
                    "Restart the application"
                )
                ErrorCategory.SECURITY -> listOf(
                    "Enter the correct password",
                    "Contact the document owner for permissions",
                    "Check if document allows viewing"
                )
                ErrorCategory.IO -> listOf(
                    "Check file permissions",
                    "Verify the file path is correct",
                    "Ensure the file is not in use by another app",
                    "Check available storage space"
                )
                ErrorCategory.NETWORK -> listOf(
                    "Check your internet connection",
                    "Verify the URL is correct",
                    "Try again in a few minutes",
                    "Check if the server is accessible"
                )
                ErrorCategory.INTERACTION -> listOf(
                    "Try the gesture again",
                    "Check if the feature is enabled",
                    "Restart the viewer if problems persist"
                )
                ErrorCategory.CONFIGURATION -> listOf(
                    "Check configuration settings",
                    "Reset to default settings",
                    "Update the application",
                    "Contact support for help"
                )
                ErrorCategory.UNKNOWN -> listOf(
                    "Try restarting the application",
                    "Check if the device has enough storage",
                    "Update to the latest version",
                    "Contact support if the issue persists"
                )
            }
        }

        /**
         * Creates a detailed error report
         * @param error the error to report
         * @param context additional context information
         * @return detailed error report
         */
        @JvmStatic
        fun createErrorReport(error: Throwable, context: String = ""): ErrorReport {
            return ErrorReport(
                category = categorizeError(error),
                severity = getErrorSeverity(error),
                message = error.message ?: "No message",
                userFriendlyMessage = getUserFriendlyMessage(error),
                isRecoverable = isRecoverable(error),
                suggestedActions = getSuggestedActions(error),
                stackTrace = error.stackTraceToString(),
                context = context,
                timestamp = System.currentTimeMillis(),
                errorType = error::class.java.simpleName
            )
        }

        /**
         * Logs an exception with appropriate level
         */
        @JvmStatic
        fun logException(tag: String, exception: Throwable, context: String = "") {
            val contextStr = if (context.isNotEmpty()) " [$context]" else ""
            val message = "${exception.javaClass.simpleName}$contextStr: ${exception.message}"
            val severity = getErrorSeverity(exception)

            when (severity) {
                ErrorSeverity.LOW -> android.util.Log.i(tag, message, exception)
                ErrorSeverity.MEDIUM -> android.util.Log.w(tag, message, exception)
                ErrorSeverity.HIGH -> android.util.Log.e(tag, message, exception)
                ErrorSeverity.FATAL -> {
                    android.util.Log.wtf(tag, message, exception)
                    // In production, you might want to send crash reports here
                }
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
         * Handles common PDF viewer errors with predefined responses
         */
        @JvmStatic
        fun handleCommonErrors(error: Throwable, onError: OnErrorListener? = null): Boolean {
            val category = categorizeError(error)
            val severity = getErrorSeverity(error)

            // Handle fatal errors immediately
            if (severity == ErrorSeverity.FATAL) {
                onError?.onError(error)
                return true
            }

            // Handle recoverable errors with user guidance
            when (category) {
                ErrorCategory.MEMORY -> {
                    // Trigger garbage collection
                    System.gc()
                    onError?.onError(error)
                    return true
                }
                ErrorCategory.NETWORK -> {
                    // Could implement retry logic here
                    onError?.onError(error)
                    return true
                }
                else -> {
                    // Let caller handle other errors
                    onError?.onError(error)
                    return false
                }
            }
        }
    }

    /**
     * Detailed error report data class
     */
    data class ErrorReport(
        val category: ErrorCategory,
        val severity: ErrorSeverity,
        val message: String,
        val userFriendlyMessage: String,
        val isRecoverable: Boolean,
        val suggestedActions: List<String>,
        val stackTrace: String,
        val context: String,
        val timestamp: Long,
        val errorType: String
    ) {
        /**
         * Gets formatted timestamp
         */
        fun getFormattedTimestamp(): String {
            return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        }

        /**
         * Gets short summary of the error
         */
        fun getSummary(): String {
            return "$errorType ($severity): $message"
        }

        /**
         * Gets full report as formatted string
         */
        override fun toString(): String {
            return buildString {
                appendLine("Error Report - ${getFormattedTimestamp()}")
                appendLine("Category: $category")
                appendLine("Severity: $severity")
                appendLine("Type: $errorType")
                appendLine("Message: $message")
                appendLine("User Message: $userFriendlyMessage")
                appendLine("Recoverable: $isRecoverable")
                if (context.isNotEmpty()) {
                    appendLine("Context: $context")
                }
                if (suggestedActions.isNotEmpty()) {
                    appendLine("Suggested Actions:")
                    suggestedActions.forEach { appendLine("  - $it") }
                }
                appendLine("Stack Trace:")
                appendLine(stackTrace)
            }
        }
    }
}