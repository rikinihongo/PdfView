package com.sonpxp.pdfloader.exception

/**
 * Exception thrown when password is required or incorrect
 */
class PasswordException(
    message: String,
    val isWrongPassword: Boolean = false,
    val attemptsRemaining: Int = 0,
    val isLockedOut: Boolean = false,
    val lockoutTimeRemaining: Long = 0,
    cause: Throwable? = null
) : SecurityException(message, cause) {

    override fun getCategory(): String = "PASSWORD_ERROR"

    override fun getUserMessage(): String = when {
        isLockedOut -> "Too many failed attempts. Account locked for ${lockoutTimeRemaining / 1000} seconds."
        isWrongPassword && attemptsRemaining > 0 -> "Incorrect password. $attemptsRemaining attempts remaining."
        isWrongPassword && attemptsRemaining == 0 -> "Incorrect password. This was your last attempt."
        else -> "This PDF is password protected. Please enter the password."
    }

    override fun isRecoverable(): Boolean = !isLockedOut

    /**
     * Gets formatted lockout time remaining
     */
    fun getFormattedLockoutTime(): String {
        val seconds = lockoutTimeRemaining / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        return when {
            minutes > 0 -> "${minutes}m ${remainingSeconds}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Checks if this is a first-time password request
     */
    fun isInitialPasswordRequest(): Boolean = !isWrongPassword && attemptsRemaining == 0

    /**
     * Creates a password exception for wrong password
     */
    companion object {
        @JvmStatic
        fun wrongPassword(attemptsRemaining: Int): PasswordException {
            return PasswordException(
                message = "Invalid password provided",
                isWrongPassword = true,
                attemptsRemaining = attemptsRemaining
            )
        }

        @JvmStatic
        fun lockedOut(lockoutTimeRemaining: Long): PasswordException {
            return PasswordException(
                message = "Account locked due to too many failed attempts",
                isWrongPassword = false,
                isLockedOut = true,
                lockoutTimeRemaining = lockoutTimeRemaining
            )
        }

        @JvmStatic
        fun required(): PasswordException {
            return PasswordException(
                message = "Password required to access document"
            )
        }
    }
}