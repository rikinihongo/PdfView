package com.sonpxp.pdfloader.security


import android.os.SystemClock
import com.sonpxp.pdfloader.exception.PasswordException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages password authentication and caching for PDF documents
 * Handles password validation, attempt tracking, and security timeouts
 */
class PasswordManager(private val config: SecurityConfig) {

    private val passwordCache = ConcurrentHashMap<String, CachedPassword>()
    private val attemptTracker = ConcurrentHashMap<String, AttemptRecord>()
    private val securityEvents = mutableListOf<SecurityEvent>()

    /**
     * Represents a cached password entry
     */
    private data class CachedPassword(
        val passwordHash: String,
        val timestamp: Long,
        val documentId: String
    )

    /**
     * Tracks password attempts for a document
     */
    private data class AttemptRecord(
        val documentId: String,
        var attemptCount: Int = 0,
        var lastAttemptTime: Long = 0,
        var lockoutUntil: Long = 0
    )

    /**
     * Represents a security event for audit logging
     */
    data class SecurityEvent(
        val documentId: String,
        val eventType: EventType,
        val timestamp: Long,
        val success: Boolean,
        val details: String = ""
    ) {
        enum class EventType {
            PASSWORD_ATTEMPT,
            PASSWORD_SUCCESS,
            PASSWORD_FAILURE,
            LOCKOUT_TRIGGERED,
            LOCKOUT_EXPIRED,
            CACHE_HIT,
            CACHE_MISS,
            CACHE_EXPIRED
        }
    }

    /**
     * Validates a password for a document
     * @param documentId unique identifier for the document
     * @param providedPassword the password to validate
     * @param actualPassword the correct password for the document
     * @return true if password is valid and user is not locked out
     * @throws SecurityException if user is locked out
     * @throws PasswordException if password is incorrect
     */
    @Throws(SecurityException::class, PasswordException::class)
    fun validatePassword(documentId: String, providedPassword: String, actualPassword: String): Boolean {
        val currentTime = SystemClock.elapsedRealtime()

        // Check if user is locked out
        checkLockoutStatus(documentId, currentTime)

        // Check cache first if enabled
        if (config.passwordCachingEnabled && checkPasswordCache(documentId, providedPassword, currentTime)) {
            logSecurityEvent(documentId, SecurityEvent.EventType.CACHE_HIT, true)
            return true
        }

        // Validate the password
        val isValid = validatePasswordInternal(providedPassword, actualPassword)

        if (isValid) {
            handleSuccessfulAuthentication(documentId, providedPassword, currentTime)
        } else {
            handleFailedAuthentication(documentId, currentTime)
        }

        return isValid
    }

    /**
     * Checks if a document requires password authentication
     */
    fun requiresPassword(documentId: String): Boolean {
        return config.passwordProtectionEnabled
    }

    /**
     * Checks if a user is currently locked out
     */
    fun isLockedOut(documentId: String): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        val record = attemptTracker[documentId] ?: return false
        return record.lockoutUntil > currentTime
    }

    /**
     * Gets the remaining lockout time in milliseconds
     */
    fun getRemainingLockoutTime(documentId: String): Long {
        val currentTime = SystemClock.elapsedRealtime()
        val record = attemptTracker[documentId] ?: return 0
        return maxOf(0, record.lockoutUntil - currentTime)
    }

    /**
     * Gets the number of remaining attempts before lockout
     */
    fun getRemainingAttempts(documentId: String): Int {
        val record = attemptTracker[documentId] ?: return config.maxPasswordAttempts
        return maxOf(0, config.maxPasswordAttempts - record.attemptCount)
    }

    /**
     * Clears password cache for a document
     */
    fun clearPasswordCache(documentId: String) {
        passwordCache.remove(documentId)
        logSecurityEvent(documentId, SecurityEvent.EventType.CACHE_MISS, true, "Cache cleared manually")
    }

    /**
     * Clears all cached passwords
     */
    fun clearAllPasswordCache() {
        passwordCache.clear()
    }

    /**
     * Resets attempt count for a document (admin function)
     */
    fun resetAttempts(documentId: String) {
        attemptTracker.remove(documentId)
    }

    /**
     * Gets security events for audit logging
     */
    fun getSecurityEvents(documentId: String? = null): List<SecurityEvent> {
        return if (documentId != null) {
            securityEvents.filter { it.documentId == documentId }
        } else {
            securityEvents.toList()
        }
    }

    /**
     * Clears security event log
     */
    fun clearSecurityEvents() {
        securityEvents.clear()
    }

    /**
     * Performs cleanup of expired cache entries and old events
     */
    fun performCleanup() {
        val currentTime = SystemClock.elapsedRealtime()

        // Clean expired password cache
        if (config.passwordCachingEnabled) {
            val expiredKeys = passwordCache.filter { (_, cached) ->
                currentTime - cached.timestamp > config.passwordCacheTimeout
            }.keys

            expiredKeys.forEach { key ->
                passwordCache.remove(key)
                logSecurityEvent(key, SecurityEvent.EventType.CACHE_EXPIRED, true)
            }
        }

        // Clean expired lockouts
        val expiredLockouts = attemptTracker.filter { (_, record) ->
            record.lockoutUntil > 0 && record.lockoutUntil <= currentTime
        }.keys

        expiredLockouts.forEach { key ->
            attemptTracker[key]?.let { record ->
                record.lockoutUntil = 0
                record.attemptCount = 0
                logSecurityEvent(key, SecurityEvent.EventType.LOCKOUT_EXPIRED, true)
            }
        }

        // Limit security events to prevent memory issues
        if (securityEvents.size > 1000) {
            securityEvents.removeAt(0)
        }
    }

    // Private helper methods

    private fun checkLockoutStatus(documentId: String, currentTime: Long) {
        val record = attemptTracker[documentId]
        if (record != null && record.lockoutUntil > currentTime) {
            val remainingTime = record.lockoutUntil - currentTime
            throw SecurityException("Account locked. Try again in ${remainingTime / 1000} seconds.")
        }
    }

    private fun checkPasswordCache(documentId: String, password: String, currentTime: Long): Boolean {
        val cached = passwordCache[documentId] ?: return false

        // Check if cache entry is expired
        if (currentTime - cached.timestamp > config.passwordCacheTimeout) {
            passwordCache.remove(documentId)
            logSecurityEvent(documentId, SecurityEvent.EventType.CACHE_EXPIRED, true)
            return false
        }

        // Validate cached password
        val passwordHash = hashPassword(password)
        return passwordHash == cached.passwordHash
    }

    private fun validatePasswordInternal(providedPassword: String, actualPassword: String): Boolean {
        // Simple password comparison - in production, you might want more sophisticated validation
        return providedPassword == actualPassword
    }

    private fun handleSuccessfulAuthentication(documentId: String, password: String, currentTime: Long) {
        // Reset attempt count
        attemptTracker[documentId]?.let { record ->
            record.attemptCount = 0
            record.lockoutUntil = 0
        }

        // Cache password if enabled
        if (config.passwordCachingEnabled) {
            val passwordHash = hashPassword(password)
            passwordCache[documentId] = CachedPassword(passwordHash, currentTime, documentId)
        }

        logSecurityEvent(documentId, SecurityEvent.EventType.PASSWORD_SUCCESS, true)
    }

    private fun handleFailedAuthentication(documentId: String, currentTime: Long) {
        val record = attemptTracker.getOrPut(documentId) { AttemptRecord(documentId) }

        record.attemptCount++
        record.lastAttemptTime = currentTime

        // Check if lockout should be triggered
        if (record.attemptCount >= config.maxPasswordAttempts) {
            record.lockoutUntil = currentTime + config.lockoutDuration
            logSecurityEvent(documentId, SecurityEvent.EventType.LOCKOUT_TRIGGERED, false,
                "Locked for ${config.lockoutDuration / 1000} seconds")

            throw SecurityException("Too many failed attempts. Account locked for ${config.lockoutDuration / 1000} seconds.")
        }

        val remainingAttempts = config.maxPasswordAttempts - record.attemptCount
        logSecurityEvent(documentId, SecurityEvent.EventType.PASSWORD_FAILURE, false,
            "Attempts remaining: $remainingAttempts")

        throw PasswordException("Invalid password. $remainingAttempts attempts remaining.", false)
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(password.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun logSecurityEvent(documentId: String, eventType: SecurityEvent.EventType, success: Boolean, details: String = "") {
        if (config.auditLoggingEnabled) {
            val event = SecurityEvent(
                documentId = documentId,
                eventType = eventType,
                timestamp = System.currentTimeMillis(),
                success = success,
                details = details
            )
            securityEvents.add(event)

            // Also log to Android Log for debugging
            val tag = "PasswordManager"
            val message = "[$documentId] $eventType: $success${if (details.isNotEmpty()) " - $details" else ""}"

            if (success) {
                android.util.Log.i(tag, message)
            } else {
                android.util.Log.w(tag, message)
            }
        }
    }

    /**
     * Gets statistics about password manager usage
     */
    fun getStatistics(): PasswordStatistics {
        val currentTime = SystemClock.elapsedRealtime()

        return PasswordStatistics(
            totalDocuments = attemptTracker.size,
            lockedOutDocuments = attemptTracker.count { (_, record) -> record.lockoutUntil > currentTime },
            cachedPasswords = passwordCache.size,
            totalSecurityEvents = securityEvents.size,
            successfulAuthentications = securityEvents.count { it.eventType == SecurityEvent.EventType.PASSWORD_SUCCESS },
            failedAuthentications = securityEvents.count { it.eventType == SecurityEvent.EventType.PASSWORD_FAILURE },
            cacheHits = securityEvents.count { it.eventType == SecurityEvent.EventType.CACHE_HIT },
            cacheMisses = securityEvents.count { it.eventType == SecurityEvent.EventType.CACHE_MISS }
        )
    }

    data class PasswordStatistics(
        val totalDocuments: Int,
        val lockedOutDocuments: Int,
        val cachedPasswords: Int,
        val totalSecurityEvents: Int,
        val successfulAuthentications: Int,
        val failedAuthentications: Int,
        val cacheHits: Int,
        val cacheMisses: Int
    ) {
        fun getCacheHitRate(): Float {
            val total = cacheHits + cacheMisses
            return if (total > 0) cacheHits.toFloat() / total else 0f
        }

        fun getSuccessRate(): Float {
            val total = successfulAuthentications + failedAuthentications
            return if (total > 0) successfulAuthentications.toFloat() / total else 0f
        }
    }
}