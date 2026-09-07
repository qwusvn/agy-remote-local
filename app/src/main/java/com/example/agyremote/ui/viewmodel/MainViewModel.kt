package com.example.agyremote.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.agyremote.ui.BrowserTab
import com.example.agyremote.ui.LogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel quản lý State trung tâm (Single Source of Truth) cho AGY Remote.
 * Tách biệt 100% logic quản lý Tab, Log và Trạng thái Agent ra khỏi UI composables.
 */
class MainViewModel : ViewModel() {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // 1. Quản lý danh sách Tab
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    // 2. Quản lý Log & ADB Diagnostics
    private val _logItems = MutableStateFlow<List<LogItem>>(emptyList())
    val logItems: StateFlow<List<LogItem>> = _logItems.asStateFlow()

    private val _errorCount = MutableStateFlow(0)
    val errorCount: StateFlow<Int> = _errorCount.asStateFlow()

    /**
     * Khởi tạo tab đầu tiên nếu danh sách đang rỗng
     */
    fun initInitialTab(defaultUrl: String) {
        if (_tabs.value.isEmpty() && defaultUrl.isNotBlank()) {
            val initialTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Dự án / Phiên",
                url = defaultUrl,
                isWorking = false,
                hasUnread = false
            )
            _tabs.value = listOf(initialTab)
            _activeTabId.value = initialTab.id
        }
    }

    /**
     * Mở một tab mới
     */
    fun createNewTab(defaultUrl: String): BrowserTab {
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = "Dự án / Phiên",
            url = defaultUrl,
            isWorking = false,
            hasUnread = false
        )
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = newTab.id
        return newTab
    }

    /**
     * Đóng một tab
     */
    fun closeTab(tabId: String): BrowserTab? {
        val currentList = _tabs.value
        if (currentList.size <= 1) return null

        val idx = currentList.indexOfFirst { it.id == tabId }
        if (idx < 0) return null

        val updatedList = currentList.toMutableList().apply { removeAt(idx) }
        _tabs.value = updatedList

        if (_activeTabId.value == tabId) {
            val nextTab = updatedList.getOrNull(idx) ?: updatedList.last()
            _activeTabId.value = nextTab.id
            return nextTab
        }
        return null
    }

    /**
     * Chọn tab đang xem
     */
    fun selectTab(tab: BrowserTab) {
        _activeTabId.value = tab.id
        // Xóa cờ chưa đọc khi người dùng bấm vào tab
        _tabs.value = _tabs.value.map {
            if (it.id == tab.id) it.copy(hasUnread = false) else it
        }
    }

    /**
     * Lưu URL hiện tại của tab đang hoạt động trước khi chuyển tab
     */
    fun saveCurrentTabUrl(url: String) {
        if (url.isBlank() || url == "about:blank") return
        val activeId = _activeTabId.value
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == activeId) tab.copy(url = url) else tab
        }
    }

    /**
     * Cập nhật tiêu đề phiên thực tế cho tab đang hoạt động (bảo toàn tên phiên, ngăn Tab ngố)
     */
    fun updateActiveTabSessionInfo(pathOrUrl: String, title: String, baseUrl: String) {
        updateSessionInfoForTab(_activeTabId.value, pathOrUrl, title, baseUrl)
    }

    fun updateSessionInfo(pathOrUrl: String, title: String, baseUrl: String) {
        updateActiveTabSessionInfo(pathOrUrl, title, baseUrl)
    }

    /**
     * Cập nhật tiêu đề phiên theo tabId cụ thể
     */
    fun updateSessionInfoForTab(tabId: String, pathOrUrl: String, title: String, baseUrl: String) {
        if (title.isBlank() || title.startsWith("Antigravity", ignoreCase = true) || title == "about:blank") return

        val fullUrl = if (pathOrUrl.startsWith("http")) pathOrUrl else "${baseUrl.trimEnd('/')}$pathOrUrl"

        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                // Bảo toàn tên phiên: Không ghi đè tên cụ thể bằng generic "Phiên đang mở" khi đang ở trong chat
                val isGeneric = title in listOf("Phiên đang mở", "Dự án / Phiên", "Lịch sử")
                val hasSpecific = tab.title !in listOf("Phiên đang mở", "Dự án / Phiên", "Lịch sử", "")

                val finalTitle = if (isGeneric && hasSpecific && fullUrl.contains("/c/")) {
                    tab.title
                } else {
                    title
                }

                tab.copy(title = finalTitle, url = fullUrl)
            } else {
                tab
            }
        }
    }

    /**
     * Cập nhật trạng thái làm việc (isWorking) cho tab đang active
     */
    fun updateActiveTabWorking(isWorking: Boolean) {
        updateTabWorking(_activeTabId.value, isWorking)
    }

    /**
     * Cập nhật trạng thái làm việc (isWorking) theo tabId cụ thể
     */
    fun updateTabWorking(tabId: String, isWorking: Boolean) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) tab.copy(isWorking = isWorking) else tab
        }
    }

    /**
     * Cập nhật trạng thái làm việc (isWorking) theo đường dẫn (path) của phiên.
     * Đảm bảo chỉ tab khớp với phiên đang báo trạng thái mới bị ảnh hưởng,
     * ngăn chặn tuyệt đối lỗi treo trạng thái working chéo giữa nhiều hội thoại.
     */
    fun updateTabWorkingByPath(path: String, isWorking: Boolean) {
        val activeId = _activeTabId.value
        val cleanPath = path.trim()
        val convoId = if (cleanPath.startsWith("/c/")) {
            cleanPath.removePrefix("/c/").split("?")[0].split("#")[0]
        } else {
            ""
        }

        _tabs.value = _tabs.value.map { tab ->
            val matchesPath = if (convoId.isNotBlank()) {
                tab.url.contains(convoId)
            } else if (cleanPath.isNotBlank() && cleanPath != "/") {
                tab.url.endsWith(cleanPath) || tab.url.contains(cleanPath)
            } else {
                false
            }
            val isCurrentActive = tab.id == activeId

            if (matchesPath || (cleanPath.isBlank() && isCurrentActive) || (cleanPath == "/" && isCurrentActive)) {
                tab.copy(isWorking = isWorking)
            } else {
                tab
            }
        }
    }

    /**
     * Xử lý sự kiện cập nhật phiên Realtime ngầm từ Service
     */
    fun handleSessionUpdate(convoId: String, title: String, isWorking: Boolean) {
        val activeId = _activeTabId.value
        val genericTitles = setOf(
            "Phiên làm việc", "Dự án / Phiên", "Phiên đang mở", 
            "Lịch sử", "Phiên mới", "Phiên thông báo", 
            "Cuộc trò chuyện Antigravity", "Antigravity", ""
        )
        val isSpecificTitle = title.isNotBlank() && title !in genericTitles

        _tabs.value = _tabs.value.map { tab ->
            val matchesConvo = convoId.isNotBlank() && tab.url.contains(convoId)
            val matchesTitle = isSpecificTitle && tab.title.equals(title, ignoreCase = true)
            if (matchesConvo || matchesTitle) {
                val isCurrentActive = tab.id == activeId
                val newTitle = if (isSpecificTitle) title else tab.title
                tab.copy(
                    title = newTitle,
                    isWorking = isWorking,
                    hasUnread = if (!isCurrentActive && !isWorking) true else tab.hasUnread
                )
            } else {
                tab
            }
        }
    }

    /**
     * Xử lý khi bấm vào thông báo phiên trên Android
     */
    fun handleNotificationIntent(targetUrl: String): BrowserTab {
        val existing = _tabs.value.find { it.url == targetUrl }
        return if (existing != null) {
            _activeTabId.value = existing.id
            _tabs.value = _tabs.value.map { if (it.id == existing.id) it.copy(hasUnread = false) else it }
            existing
        } else {
            val newTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Phiên thông báo",
                url = targetUrl,
                isWorking = false,
                hasUnread = false
            )
            _tabs.value = _tabs.value + newTab
            _activeTabId.value = newTab.id
            newTab
        }
    }

    /**
     * Ghi nhận log
     */
    fun addLog(tag: String, msg: String, isError: Boolean) {
        val entry = LogItem(
            time = timeFormat.format(Date()),
            tag = tag,
            message = msg,
            isError = isError
        )
        val updated = (_logItems.value + entry).takeLast(250)
        _logItems.value = updated
        if (isError) {
            _errorCount.value = _errorCount.value + 1
        }
    }

    /**
     * Xóa sạch log
     */
    fun clearLogs() {
        _logItems.value = emptyList()
        _errorCount.value = 0
    }
}
