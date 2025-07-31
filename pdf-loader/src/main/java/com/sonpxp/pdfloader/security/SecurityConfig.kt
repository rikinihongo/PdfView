package com.sonpxp.pdfloader.security

/**
 * Configuration class for PDF security settings
 * Manages all security-related features and permissions
 */
data class SecurityConfig(
    /** Enable/disable password protection */
    val passwordProtectionEnabled: Boolean = true,

    /** Enable/disable text selection */
    val textSelectionEnabled: Boolean = true,

    /** Enable/disable copy functionality */
    val copyEnabled: Boolean = true,

    /** Enable/disable print functionality */
    val printEnabled: Boolean = true,

    /** Enable/disable save/export functionality */
    val saveEnabled: Boolean = true,

    /** Enable/disable screenshot/screen recording protection */
    val screenCaptureProtectionEnabled: Boolean = false,

    /** Maximum number of password attempts before lockout */
    val maxPasswordAttempts: Int = 3,

    /** Lockout duration in milliseconds after max attempts reached */
    val lockoutDuration: Long = 5 * 60 * 1000, // 5 minutes

    /** Enable/disable password caching */
    val passwordCachingEnabled: Boolean = true,

    /** Password cache duration in milliseconds */
    val passwordCacheTimeout: Long = 30 * 60 * 1000, // 30 minutes

    /** Enable/disable audit logging of security events */
    val auditLoggingEnabled: Boolean = false,

    /** Custom security message for restricted actions */
    val restrictedActionMessage: String = "This action is not permitted for this document",

    /** Enable/disable watermark overlay */
    val watermarkEnabled: Boolean = false,

    /** Watermark text */
    val watermarkText: String = "",

    /** Watermark transparency (0.0 = transparent, 1.0 = opaque) */
    val watermarkOpacity: Float = 0.3f,

    /** Security level for the document */
    val securityLevel: SecurityLevel = SecurityLevel.STANDARD
) {

    enum class SecurityLevel {
        /** No security restrictions */
        NONE,

        /** Standard security with basic restrictions */
        STANDARD,

        /** Enhanced security with additional protections */
        ENHANCED,

        /** Maximum security with all protections enabled */
        MAXIMUM
    }

    /**
     * Validates the security configuration
     */
    fun validate() {
        require(maxPasswordAttempts > 0) { "Max password attempts must be positive" }
        require(lockoutDuration >= 0) { "Lockout duration cannot be negative" }
        require(passwordCacheTimeout >= 0) { "Password cache timeout cannot be negative" }
        require(watermarkOpacity in 0f..1f) { "Watermark opacity must be between 0.0 and 1.0" }
    }

    /**
     * Checks if any protection is enabled
     */
    fun hasAnyProtection(): Boolean {
        return passwordProtectionEnabled ||
                !textSelectionEnabled ||
                !copyEnabled ||
                !printEnabled ||
                !saveEnabled ||
                screenCaptureProtectionEnabled ||
                watermarkEnabled
    }

    /**
     * Checks if user interaction is restricted
     */
    fun isUserInteractionRestricted(): Boolean {
        return !textSelectionEnabled || !copyEnabled
    }

    /**
     * Checks if document output is restricted
     */
    fun isDocumentOutputRestricted(): Boolean {
        return !printEnabled || !saveEnabled
    }

    /**
     * Creates a configuration for the specified security level
     */
    companion object {
        @JvmStatic
        fun forLevel(level: SecurityLevel): SecurityConfig {
            return when (level) {
                SecurityLevel.NONE -> SecurityConfig(
                    passwordProtectionEnabled = false,
                    textSelectionEnabled = true,
                    copyEnabled = true,
                    printEnabled = true,
                    saveEnabled = true,
                    screenCaptureProtectionEnabled = false,
                    watermarkEnabled = false,
                    securityLevel = level
                )

                SecurityLevel.STANDARD -> SecurityConfig(
                    passwordProtectionEnabled = true,
                    textSelectionEnabled = true,
                    copyEnabled = true,
                    printEnabled = true,
                    saveEnabled = false,
                    screenCaptureProtectionEnabled = false,
                    watermarkEnabled = false,
                    securityLevel = level
                )

                SecurityLevel.ENHANCED -> SecurityConfig(
                    passwordProtectionEnabled = true,
                    textSelectionEnabled = true,
                    copyEnabled = false,
                    printEnabled = false,
                    saveEnabled = false,
                    screenCaptureProtectionEnabled = true,
                    watermarkEnabled = true,
                    watermarkText = "CONFIDENTIAL",
                    securityLevel = level
                )

                SecurityLevel.MAXIMUM -> SecurityConfig(
                    passwordProtectionEnabled = true,
                    textSelectionEnabled = false,
                    copyEnabled = false,
                    printEnabled = false,
                    saveEnabled = false,
                    screenCaptureProtectionEnabled = true,
                    watermarkEnabled = true,
                    watermarkText = "RESTRICTED",
                    watermarkOpacity = 0.5f,
                    maxPasswordAttempts = 1,
                    lockoutDuration = 15 * 60 * 1000, // 15 minutes
                    passwordCachingEnabled = false,
                    auditLoggingEnabled = true,
                    securityLevel = level
                )
            }
        }

        /**
         * Creates a default security configuration
         */
        @JvmStatic
        fun createDefault(): SecurityConfig {
            return SecurityConfig()
        }

        /**
         * Creates a configuration with password protection only
         */
        @JvmStatic
        fun passwordOnly(password: String? = null): SecurityConfig {
            return SecurityConfig(
                passwordProtectionEnabled = password != null,
                textSelectionEnabled = true,
                copyEnabled = true,
                printEnabled = true,
                saveEnabled = true,
                screenCaptureProtectionEnabled = false
            )
        }
    }

    /**
     * Creates a copy with updated settings
     */
    fun withTextSelection(enabled: Boolean): SecurityConfig {
        return copy(textSelectionEnabled = enabled)
    }

    fun withCopy(enabled: Boolean): SecurityConfig {
        return copy(copyEnabled = enabled)
    }

    fun withPrint(enabled: Boolean): SecurityConfig {
        return copy(printEnabled = enabled)
    }

    fun withSave(enabled: Boolean): SecurityConfig {
        return copy(saveEnabled = enabled)
    }

    fun withWatermark(enabled: Boolean, text: String = "", opacity: Float = 0.3f): SecurityConfig {
        return copy(
            watermarkEnabled = enabled,
            watermarkText = text,
            watermarkOpacity = opacity
        )
    }

    fun withPasswordProtection(enabled: Boolean, maxAttempts: Int = 3): SecurityConfig {
        return copy(
            passwordProtectionEnabled = enabled,
            maxPasswordAttempts = maxAttempts
        )
    }

    /**
     * Gets a summary of the security configuration
     */
    fun getSummary(): String {
        return buildString {
            appendLine("Security Configuration:")
            appendLine("  Level: $securityLevel")
            appendLine("  Password Protection: $passwordProtectionEnabled")
            appendLine("  Text Selection: $textSelectionEnabled")
            appendLine("  Copy: $copyEnabled")
            appendLine("  Print: $printEnabled")
            appendLine("  Save: $saveEnabled")
            appendLine("  Screen Capture Protection: $screenCaptureProtectionEnabled")
            appendLine("  Watermark: $watermarkEnabled")
            if (watermarkEnabled) {
                appendLine("    Text: '$watermarkText'")
                appendLine("    Opacity: $watermarkOpacity")
            }
            appendLine("  Max Password Attempts: $maxPasswordAttempts")
            appendLine("  Lockout Duration: ${lockoutDuration / 1000}s")
            appendLine("  Password Caching: $passwordCachingEnabled")
            appendLine("  Audit Logging: $auditLoggingEnabled")
        }
    }
}