package com.sonpxp.pdfloader.core

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sonpxp.pdfloader.PageRenderState
import com.sonpxp.pdfloader.RenderQuality
import com.sonpxp.pdfloader.SecurityLevel
import com.sonpxp.pdfloader.exception.InvalidSourceException
import com.sonpxp.pdfloader.model.PageInfo
import com.sonpxp.pdfloader.source.DocumentSource
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper class for PDF document handling using Android's PdfRenderer
 * Provides high-level access to PDF document operations and page management
 */
class PdfDocument(
    private val documentSource: DocumentSource,
    private val password: String? = null
) {

    companion object {
        private const val TAG = "PdfDocument"
        private const val TEMP_FILE_PREFIX = "pdfviewer_temp_"
        private const val TEMP_FILE_SUFFIX = ".pdf"
    }

    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var tempFile: File? = null
    private var isOpened = false

    // Document metadata
    private var totalPages = 0
    private var documentTitle: String? = null
    private var documentAuthor: String? = null
    private var documentSubject: String? = null
    private var creationDate: String? = null
    private var modificationDate: String? = null
    private var securityLevel = SecurityLevel.NONE

    // Page cache and management
    private val openPages = ConcurrentHashMap<Int, PdfRenderer.Page>()
    private val pageInfoCache = ConcurrentHashMap<Int, PageInfo>()
    private var maxConcurrentPages = 3 // Limit concurrent open pages to save memory

    // Document statistics
    private var openTime: Long = 0
    private var totalPagesAccessed = 0
    private var totalRenderRequests = 0

    /**
     * Opens the PDF document and initializes the renderer
     * @throws InvalidSourceException if document cannot be opened
     * @throws SecurityException if password is required or incorrect
     */
    @Throws(InvalidSourceException::class, SecurityException::class)
    fun open() {
        if (isOpened) {
            Log.w(TAG, "Document is already opened")
            return
        }

        val startTime = System.currentTimeMillis()

        try {
            // Create temporary file from source
            tempFile = createTempFileFromSource()

            // Open parcel file descriptor
            parcelFileDescriptor = ParcelFileDescriptor.open(
                tempFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )

            // Create PDF renderer
            pdfRenderer = try {
                PdfRenderer(parcelFileDescriptor!!)
            } catch (e: SecurityException) {
                throw SecurityException(
                    "PDF document is password protected or has security restrictions",
                    e
                )
            } catch (e: IOException) {
                throw InvalidSourceException(
                    documentSource.getDescription(),
                    "Failed to create PDF renderer: ${e.message}",
                    e
                )
            }

            // Initialize document info
            totalPages = pdfRenderer!!.pageCount
            openTime = System.currentTimeMillis() - startTime
            isOpened = true

            // Extract document metadata
            extractDocumentMetadata()

            Log.d(TAG, "PDF document opened successfully: $totalPages pages in ${openTime}ms")

        } catch (e: Exception) {
            cleanup()
            when (e) {
                is InvalidSourceException, is SecurityException -> throw e
                else -> throw InvalidSourceException(
                    documentSource.getDescription(),
                    "Failed to open PDF document: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Closes the PDF document and releases all resources
     */
    fun close() {
        if (!isOpened) return

        try {
            // Close all open pages
            closeAllPages()

            // Close renderer and file descriptor
            pdfRenderer?.close()
            parcelFileDescriptor?.close()

            // Clean up temporary file
            tempFile?.let { file ->
                if (file.exists()) {
                    val deleted = file.delete()
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete temporary file: ${file.absolutePath}")
                    }
                }
            }

            cleanup()

            Log.d(TAG, "PDF document closed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error closing PDF document", e)
        }
    }

    /**
     * Gets a PDF page for rendering
     * @param pageNumber the page number (0-indexed)
     * @return PDF page object
     * @throws IllegalStateException if document is not opened
     * @throws IndexOutOfBoundsException if page number is invalid
     */
    @Throws(IllegalStateException::class, IndexOutOfBoundsException::class)
    fun getPage(pageNumber: Int): PdfRenderer.Page {
        checkDocumentOpened()
        checkPageNumber(pageNumber)

        // Return cached page if available
        openPages[pageNumber]?.let { return it }

        // Limit concurrent open pages
        if (openPages.size >= maxConcurrentPages) {
            closeOldestPage()
        }

        try {
            val page = pdfRenderer!!.openPage(pageNumber)
            openPages[pageNumber] = page
            totalPagesAccessed++

            Log.d(TAG, "Opened page $pageNumber (${openPages.size} pages currently open)")
            return page

        } catch (e: Exception) {
            throw IllegalStateException("Failed to open page $pageNumber: ${e.message}", e)
        }
    }

    /**
     * Releases a specific page from memory
     * @param pageNumber the page number (0-indexed)
     */
    fun releasePage(pageNumber: Int) {
        openPages.remove(pageNumber)?.let { page ->
            try {
                page.close()
                Log.d(TAG, "Released page $pageNumber")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing page $pageNumber", e)
            }
        }
    }

    /**
     * Gets information about a specific page
     * @param pageNumber the page number (0-indexed)
     * @return page information
     */
    fun getPageInfo(pageNumber: Int): PageInfo {
        checkDocumentOpened()
        checkPageNumber(pageNumber)

        // Return cached info if available
        pageInfoCache[pageNumber]?.let { return it }

        // Create page info from PDF page
        val page = getPage(pageNumber)
        val pageInfo = PageInfo(
            pageNumber = pageNumber,
            originalWidth = page.width.toFloat(),
            originalHeight = page.height.toFloat(),
            renderedWidth = page.width.toFloat(),
            renderedHeight = page.height.toFloat(),
            zoomLevel = 1.0f,
            rotation = 0,
            renderState = PageRenderState.NOT_RENDERED,
            hasText = true, // Assume pages have text
            hasImages = false, // Would need content analysis to determine
            hasAnnotations = false, // Would need content analysis to determine
            renderQuality = RenderQuality.MEDIUM
        )

        // Cache the page info
        pageInfoCache[pageNumber] = pageInfo

        return pageInfo
    }

    /**
     * Gets all page information at once
     * @return list of page information for all pages
     */
    fun getAllPageInfo(): List<PageInfo> {
        checkDocumentOpened()

        return (0 until totalPages).map { pageNumber ->
            getPageInfo(pageNumber)
        }
    }

    /**
     * Checks if the document is password protected
     * @return true if password protected
     */
    fun isPasswordProtected(): Boolean {
        return securityLevel != SecurityLevel.NONE
    }

    /**
     * Gets the total number of pages
     * @return page count
     */
    fun getPageCount(): Int {
        checkDocumentOpened()
        return totalPages
    }

    /**
     * Gets document metadata
     * @return document metadata
     */
    fun getDocumentMetadata(): DocumentMetadata {
        checkDocumentOpened()

        return DocumentMetadata(
            title = documentTitle,
            author = documentAuthor,
            subject = documentSubject,
            creationDate = creationDate,
            modificationDate = modificationDate,
            pageCount = totalPages,
            securityLevel = securityLevel,
            fileSize = documentSource.getSize(),
            source = documentSource.getDescription()
        )
    }

    /**
     * Gets document statistics
     * @return document usage statistics
     */
    fun getDocumentStatistics(): DocumentStatistics {
        return DocumentStatistics(
            openTimeMs = openTime,
            totalPagesAccessed = totalPagesAccessed,
            totalRenderRequests = totalRenderRequests,
            currentOpenPages = openPages.size,
            maxConcurrentPages = maxConcurrentPages,
            cachedPageInfoCount = pageInfoCache.size,
            isOpened = isOpened
        )
    }

    /**
     * Preloads page information for better performance
     * @param pageRange range of pages to preload (null for all pages)
     */
    fun preloadPageInfo(pageRange: IntRange? = null) {
        checkDocumentOpened()

        val range = pageRange ?: (0 until totalPages)

        Thread {
            for (pageNumber in range) {
                if (pageNumber < totalPages) {
                    try {
                        getPageInfo(pageNumber)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error preloading page info for page $pageNumber", e)
                    }
                }
            }
            Log.d(TAG, "Preloaded page info for range: $range")
        }.start()
    }

    /**
     * Optimizes memory usage by closing unused pages
     */
    fun optimizeMemoryUsage() {
        val pagesToClose = openPages.size - (maxConcurrentPages / 2)

        if (pagesToClose > 0) {
            repeat(pagesToClose) {
                closeOldestPage()
            }
            Log.d(TAG, "Optimized memory usage: closed $pagesToClose pages")
        }
    }

    /**
     * Sets the maximum number of concurrent open pages
     * @param maxPages maximum concurrent pages (minimum 1)
     */
    fun setMaxConcurrentPages(maxPages: Int) {
        maxConcurrentPages = maxPages.coerceAtLeast(1)

        // Close excess pages if necessary
        while (openPages.size > maxConcurrentPages) {
            closeOldestPage()
        }
    }

    /**
     * Checks if the document is currently opened
     * @return true if opened
     */
    fun isOpened(): Boolean = isOpened

    /**
     * Gets the document source
     * @return document source
     */
    fun getSource(): DocumentSource = documentSource

    private fun createTempFileFromSource(): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"))
        val tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, tempDir)

        documentSource.openInputStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }

    private fun extractDocumentMetadata() {
        // Note: Android's PdfRenderer doesn't provide access to document metadata
        // This would require a more advanced PDF library like PDFBox or iText
        // For now, we'll set basic information

        documentTitle = "PDF Document"
        documentAuthor = "Unknown"
        securityLevel = if (password != null) SecurityLevel.BASIC else SecurityLevel.NONE

        // In a real implementation, you would extract actual metadata from the PDF
    }

    private fun closeAllPages() {
        for ((pageNumber, page) in openPages) {
            try {
                page.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing page $pageNumber", e)
            }
        }
        openPages.clear()
    }

    private fun closeOldestPage() {
        if (openPages.isNotEmpty()) {
            val oldestPageNumber = openPages.keys.first()
            releasePage(oldestPageNumber)
        }
    }

    private fun cleanup() {
        pdfRenderer = null
        parcelFileDescriptor = null
        tempFile = null
        isOpened = false
        totalPages = 0
        openPages.clear()
        pageInfoCache.clear()
        totalPagesAccessed = 0
        totalRenderRequests = 0
    }

    private fun checkDocumentOpened() {
        if (!isOpened) {
            throw IllegalStateException("PDF document is not opened")
        }
    }

    private fun checkPageNumber(pageNumber: Int) {
        if (pageNumber < 0 || pageNumber >= totalPages) {
            throw IndexOutOfBoundsException(
                "Page number $pageNumber is out of bounds (0-${totalPages - 1})"
            )
        }
    }

    /**
     * Document metadata information
     */
    data class DocumentMetadata(
        val title: String?,
        val author: String?,
        val subject: String?,
        val creationDate: String?,
        val modificationDate: String?,
        val pageCount: Int,
        val securityLevel: SecurityLevel,
        val fileSize: Long,
        val source: String
    ) {
        override fun toString(): String {
            return "PDF Document: '$title' by $author, $pageCount pages, " +
                    "${formatFileSize(fileSize)}, Security: $securityLevel"
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "${bytes}B"
                bytes < 1024 * 1024 -> "${bytes / 1024}KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
                else -> "${bytes / (1024 * 1024 * 1024)}GB"
            }
        }
    }

    /**
     * Document usage statistics
     */
    data class DocumentStatistics(
        val openTimeMs: Long,
        val totalPagesAccessed: Int,
        val totalRenderRequests: Int,
        val currentOpenPages: Int,
        val maxConcurrentPages: Int,
        val cachedPageInfoCount: Int,
        val isOpened: Boolean
    ) {
        override fun toString(): String {
            return "Document Stats: Opened in ${openTimeMs}ms, " +
                    "$totalPagesAccessed pages accessed, " +
                    "$currentOpenPages/$maxConcurrentPages pages open, " +
                    "$cachedPageInfoCount cached page info"
        }
    }
}