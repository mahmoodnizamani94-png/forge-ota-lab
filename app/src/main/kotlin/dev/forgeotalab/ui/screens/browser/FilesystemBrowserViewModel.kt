package dev.forgeotalab.ui.screens.browser

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.forgeotalab.contracts.model.FilesystemType
import dev.forgeotalab.contracts.model.FsEntryType
import dev.forgeotalab.domain.BrowseFilesystemResult
import dev.forgeotalab.domain.BrowseFilesystemUseCase
import dev.forgeotalab.domain.ExportFileResult
import dev.forgeotalab.domain.ExportFilesystemFileUseCase
import dev.forgeotalab.domain.FsEntryDomain
import dev.forgeotalab.domain.ListDirectoryResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ===========================================================================
// UI state
// ===========================================================================

/**
 * Sealed UI state for the filesystem browser.
 *
 * WHY sealed: Compile-time exhaustive `when` matching prevents
 * forgetting to handle a new state. Each state carries exactly
 * the data needed for its UI, no more.
 */
sealed class FilesystemBrowserUiState {

    /** Initial loading — filesystem is being mounted. */
    data object Loading : FilesystemBrowserUiState()

    /** Active browsing — entries are available. */
    data class Browsable(val data: BrowserScreenData) : FilesystemBrowserUiState()

    /**
     * Unsupported filesystem — informational state with raw export CTA.
     *
     * PRD Failure #11: "Unsupported filesystem → full-screen info state,
     * not error styling. Primary CTA: Export Raw Image."
     */
    data class UnsupportedFs(
        val format: String,
        val artifactId: String,
    ) : FilesystemBrowserUiState()

    /**
     * Parser crash or I/O failure — error state with fallback export CTA.
     *
     * PRD: "Parser crash → isolate via Rust panic handling → offer raw export."
     */
    data class Error(
        val message: String,
        val canRawExport: Boolean,
    ) : FilesystemBrowserUiState()
}

/**
 * Data for the active browsing state.
 */
@Stable
data class BrowserScreenData(
    val currentPath: String,
    val breadcrumbs: List<PathSegment>,
    val entries: List<FsEntryUiItem>,
    val totalCount: Long,
    val totalSize: Long,
    val sortOrder: SortOrder,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val artifactName: String,
    val filesystemType: String,
    val artifactId: String,
)

/**
 * A tappable breadcrumb path segment.
 */
@Immutable
data class PathSegment(
    val name: String,
    val fullPath: String,
)

/**
 * A filesystem entry ready for display.
 */
@Immutable
data class FsEntryUiItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val formattedSize: String,
    val permissions: String,
    val fileType: FsEntryType,
    val modifiedTime: Long,
    val formattedDate: String,
    val inode: Long,
)

/**
 * Directory sort order.
 */
enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_ASC,
    SIZE_DESC,
    TYPE,
}

// ===========================================================================
// One-shot UI events
// ===========================================================================

sealed class FilesystemBrowserEvent {
    data class ExportStarted(val fileName: String) : FilesystemBrowserEvent()
    data class ExportSuccess(val fileName: String, val bytesWritten: Long) : FilesystemBrowserEvent()
    data class ExportFailed(val fileName: String, val message: String) : FilesystemBrowserEvent()
}

// ===========================================================================
// ViewModel
// ===========================================================================

/**
 * Accessibility: Screen title "Filesystem Browser" with Role.Heading.
 * Breadcrumb segments individually tappable with content descriptions.
 * File/folder icons have semantic descriptions.
 * Touch targets ≥ 48dp. Focus order: breadcrumb → sort → list → actions.
 */
@HiltViewModel
class FilesystemBrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val browseFilesystemUseCase: BrowseFilesystemUseCase,
    private val exportFilesystemFileUseCase: ExportFilesystemFileUseCase,
) : ViewModel() {

    val artifactId: String = savedStateHandle["artifactId"] ?: ""

    private val _uiState = MutableStateFlow<FilesystemBrowserUiState>(
        FilesystemBrowserUiState.Loading,
    )
    val uiState: StateFlow<FilesystemBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FilesystemBrowserEvent>()
    val events = _events.asSharedFlow()

    /**
     * Navigation stack — each entry is a directory path with scroll position.
     * WHY a stack: enables back-navigating up the directory tree while
     * preserving scroll position, per PRD breadcrumb requirement.
     */
    private val navigationStack = mutableListOf<String>()

    init {
        if (artifactId.isNotBlank()) {
            initialize()
        }
    }

    /**
     * Mount the filesystem and load root directory.
     * PRD FR-8: "Filesystem mount within 2 seconds for images ≤ 4 GB."
     */
    fun initialize() {
        viewModelScope.launch {
            _uiState.value = FilesystemBrowserUiState.Loading
            navigationStack.clear()

            when (val result = browseFilesystemUseCase.browseArtifact(artifactId)) {
                is BrowseFilesystemResult.Success -> {
                    navigationStack.add("/")
                    val entries = result.filesystem.rootEntries.map { it.toUiItem() }

                    _uiState.value = FilesystemBrowserUiState.Browsable(
                        BrowserScreenData(
                            currentPath = "/",
                            breadcrumbs = buildBreadcrumbs("/"),
                            entries = sortEntries(entries, SortOrder.NAME_ASC),
                            totalCount = result.filesystem.totalCount,
                            totalSize = result.filesystem.totalSize,
                            sortOrder = SortOrder.NAME_ASC,
                            isLoadingMore = false,
                            hasMore = result.filesystem.hasMore,
                            artifactName = artifactId.take(8),
                            filesystemType = result.filesystem.filesystemType.name.lowercase(),
                            artifactId = artifactId,
                        ),
                    )
                }
                is BrowseFilesystemResult.Unsupported -> {
                    _uiState.value = FilesystemBrowserUiState.UnsupportedFs(
                        format = result.format,
                        artifactId = artifactId,
                    )
                }
                is BrowseFilesystemResult.Error -> {
                    _uiState.value = FilesystemBrowserUiState.Error(
                        message = result.message,
                        canRawExport = result.canRawExport,
                    )
                }
            }
        }
    }

    /**
     * Navigate into a directory — lazy-load its entries.
     *
     * PRD: "Lazy loading — directories load on expand, not all upfront."
     * PRD: "Back button navigates up one level (not to previous screen)."
     */
    fun navigateTo(path: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is FilesystemBrowserUiState.Browsable) {
                _uiState.value = currentState.copy(
                    data = currentState.data.copy(isLoadingMore = true),
                )
            }

            when (val result = browseFilesystemUseCase.listDirectory(artifactId, path)) {
                is ListDirectoryResult.Success -> {
                    navigationStack.add(path)
                    val entries = result.entries.map { it.toUiItem() }
                    val sortOrder = (currentState as? FilesystemBrowserUiState.Browsable)
                        ?.data?.sortOrder ?: SortOrder.NAME_ASC

                    _uiState.update { state ->
                        if (state is FilesystemBrowserUiState.Browsable) {
                            FilesystemBrowserUiState.Browsable(
                                state.data.copy(
                                    currentPath = path,
                                    breadcrumbs = buildBreadcrumbs(path),
                                    entries = sortEntries(entries, sortOrder),
                                    totalCount = result.totalCount,
                                    totalSize = result.totalSize,
                                    isLoadingMore = false,
                                    hasMore = result.hasMore,
                                ),
                            )
                        } else {
                            state
                        }
                    }
                }
                is ListDirectoryResult.Error -> {
                    _uiState.update { state ->
                        if (state is FilesystemBrowserUiState.Browsable) {
                            FilesystemBrowserUiState.Browsable(
                                state.data.copy(isLoadingMore = false),
                            )
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    /**
     * Navigate up one directory level.
     *
     * PRD: "Pressing system back goes up one directory level.
     * When at root, system back exits the browser screen."
     *
     * @return true if navigated up, false if at root (caller should exit screen)
     */
    fun navigateUp(): Boolean {
        if (navigationStack.size <= 1) {
            return false // At root — caller should exit screen
        }

        navigationStack.removeLastOrNull()
        val parentPath = navigationStack.lastOrNull() ?: "/"

        viewModelScope.launch {
            when (val result = browseFilesystemUseCase.listDirectory(artifactId, parentPath)) {
                is ListDirectoryResult.Success -> {
                    val sortOrder = (_uiState.value as? FilesystemBrowserUiState.Browsable)
                        ?.data?.sortOrder ?: SortOrder.NAME_ASC
                    val entries = result.entries.map { it.toUiItem() }

                    _uiState.update { state ->
                        if (state is FilesystemBrowserUiState.Browsable) {
                            FilesystemBrowserUiState.Browsable(
                                state.data.copy(
                                    currentPath = parentPath,
                                    breadcrumbs = buildBreadcrumbs(parentPath),
                                    entries = sortEntries(entries, sortOrder),
                                    totalCount = result.totalCount,
                                    totalSize = result.totalSize,
                                    hasMore = result.hasMore,
                                ),
                            )
                        } else {
                            state
                        }
                    }
                }
                is ListDirectoryResult.Error -> { /* Keep current state */ }
            }
        }

        return true
    }

    /**
     * Navigate to a specific breadcrumb segment.
     * Truncates the navigation stack to that segment's depth.
     */
    fun navigateToSegment(segmentPath: String) {
        // Truncate stack to the segment's position
        val segmentIndex = navigationStack.indexOf(segmentPath)
        if (segmentIndex >= 0) {
            while (navigationStack.size > segmentIndex + 1) {
                navigationStack.removeLastOrNull()
            }
        }
        navigateTo(segmentPath)
    }

    /**
     * Change the sort order for the current directory.
     */
    fun changeSortOrder(order: SortOrder) {
        _uiState.update { state ->
            if (state is FilesystemBrowserUiState.Browsable) {
                FilesystemBrowserUiState.Browsable(
                    state.data.copy(
                        sortOrder = order,
                        entries = sortEntries(state.data.entries, order),
                    ),
                )
            } else {
                state
            }
        }
    }

    /**
     * Export a file from the filesystem image via SAF.
     */
    fun exportFile(filePath: String, outputPath: String) {
        viewModelScope.launch {
            val fileName = filePath.substringAfterLast('/')
            _events.emit(FilesystemBrowserEvent.ExportStarted(fileName))

            when (val result = exportFilesystemFileUseCase.exportFile(
                artifactId,
                filePath,
                outputPath,
            )) {
                is ExportFileResult.Success -> {
                    _events.emit(
                        FilesystemBrowserEvent.ExportSuccess(fileName, result.bytesWritten),
                    )
                }
                is ExportFileResult.Error -> {
                    _events.emit(
                        FilesystemBrowserEvent.ExportFailed(fileName, result.message),
                    )
                }
            }
        }
    }

    // =======================================================================
    // Internal helpers
    // =======================================================================

    /**
     * Build breadcrumb segments from a path.
     * "/" → [PathSegment("/", "/")]
     * "/system/app/Chrome" → [("Root", "/"), ("system", "/system"), ("app", "/system/app"), ("Chrome", "/system/app/Chrome")]
     */
    private fun buildBreadcrumbs(path: String): List<PathSegment> {
        val segments = mutableListOf(PathSegment("Root", "/"))

        if (path == "/" || path.isBlank()) {
            return segments
        }

        val parts = path.trimStart('/').split('/')
        var accumulated = ""
        for (part in parts) {
            if (part.isNotBlank()) {
                accumulated += "/$part"
                segments.add(PathSegment(part, accumulated))
            }
        }

        return segments
    }

    /**
     * Sort entries according to the given order.
     * Directories are always sorted first regardless of sort order.
     */
    private fun sortEntries(entries: List<FsEntryUiItem>, order: SortOrder): List<FsEntryUiItem> {
        return entries.sortedWith(
            compareByDescending<FsEntryUiItem> { it.isDir }
                .then(
                    when (order) {
                        SortOrder.NAME_ASC -> compareBy { it.name.lowercase() }
                        SortOrder.NAME_DESC -> compareByDescending { it.name.lowercase() }
                        SortOrder.SIZE_ASC -> compareBy { it.size }
                        SortOrder.SIZE_DESC -> compareByDescending { it.size }
                        SortOrder.TYPE -> compareBy { it.fileType.name }
                    },
                ),
        )
    }
}

// ===========================================================================
// Extension: Domain → UI mapping
// ===========================================================================

/**
 * Map domain entry to UI item with formatted fields.
 */
private fun FsEntryDomain.toUiItem(): FsEntryUiItem = FsEntryUiItem(
    name = name,
    path = path,
    isDir = isDir,
    size = size,
    formattedSize = formatFileSize(size),
    permissions = permissions,
    fileType = fileType,
    modifiedTime = modifiedTime,
    formattedDate = formatTimestamp(modifiedTime),
    inode = inode,
)

/**
 * Format bytes into human-readable size.
 */
private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/**
 * Format Unix timestamp to readable date.
 */
private fun formatTimestamp(epochSeconds: Long): String {
    if (epochSeconds == 0L) return "—"
    return try {
        val instant = java.time.Instant.ofEpochSecond(epochSeconds)
        val zdt = instant.atZone(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zdt)
    } catch (_: Exception) {
        "—"
    }
}
