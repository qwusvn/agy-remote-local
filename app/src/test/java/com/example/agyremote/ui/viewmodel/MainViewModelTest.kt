package com.example.agyremote.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        viewModel = MainViewModel()
    }

    @Test
    fun testInitialTabCreation() {
        viewModel.initInitialTab("http://192.168.1.220:4400/")
        val tabs = viewModel.tabs.value
        assertEquals(1, tabs.size)
        assertEquals("http://192.168.1.220:4400/", tabs[0].url)
        assertEquals(viewModel.activeTabId.value, tabs[0].id)
        assertFalse(tabs[0].isWorking)
    }

    @Test
    fun testUpdateTabWorkingByPath_MultipleTabsIsolation() {
        viewModel.initInitialTab("http://192.168.1.220:4400/")
        val tab1 = viewModel.createNewTab("http://192.168.1.220:4400/c/convo-alpha")
        val tab2 = viewModel.createNewTab("http://192.168.1.220:4400/c/convo-beta")

        // Chỉ định convo-alpha đang làm việc
        viewModel.updateTabWorkingByPath("/c/convo-alpha", true)

        val updatedTabs = viewModel.tabs.value
        val alphaTab = updatedTabs.find { it.id == tab1.id }!!
        val betaTab = updatedTabs.find { it.id == tab2.id }!!

        assertTrue("convo-alpha must be working", alphaTab.isWorking)
        assertFalse("convo-beta must NOT be affected by convo-alpha", betaTab.isWorking)

        // Reset trạng thái làm việc của convo-alpha
        viewModel.updateTabWorkingByPath("/c/convo-alpha", false)
        val resetAlpha = viewModel.tabs.value.find { it.id == tab1.id }!!
        assertFalse("convo-alpha must be reset to not working", resetAlpha.isWorking)
    }

    @Test
    fun testHandleSessionUpdate_GenericTitlesDoNotMismatch() {
        viewModel.initInitialTab("http://192.168.1.220:4400/") // Title is "Dự án / Phiên"
        val convoTab = viewModel.createNewTab("http://192.168.1.220:4400/c/fix-issue-99")

        // Phát broadcast với title generic "Phiên làm việc" không kèm convoId
        viewModel.handleSessionUpdate("", "Phiên làm việc", true)
        val initialTab = viewModel.tabs.value.find { it.url == "http://192.168.1.220:4400/" }!!
        assertFalse("Generic title 'Phiên làm việc' must not trigger isWorking on other tabs", initialTab.isWorking)

        // Cập nhật với convoId chuẩn xác
        viewModel.handleSessionUpdate("fix-issue-99", "Fix Bug #99", true)
        val updatedConvoTab = viewModel.tabs.value.find { it.id == convoTab.id }!!
        assertTrue("Convo tab matching convoId must be working", updatedConvoTab.isWorking)
        assertEquals("Fix Bug #99", updatedConvoTab.title)
    }

    @Test
    fun testTabSwitchingAndUnreadNotification() {
        viewModel.initInitialTab("http://192.168.1.220:4400/c/task-1")
        val tab1Id = viewModel.activeTabId.value
        val tab2 = viewModel.createNewTab("http://192.168.1.220:4400/c/task-2")

        // Hiện tại tab2 đang active
        assertEquals(tab2.id, viewModel.activeTabId.value)

        // Tab1 hoàn tất trong background
        viewModel.handleSessionUpdate("task-1", "Task 1 Finished", false)
        val tab1 = viewModel.tabs.value.find { it.id == tab1Id }!!
        assertTrue("Inactive tab should have unread badge when completed", tab1.hasUnread)

        // Người dùng chuyển về tab1 -> unread badge được xóa
        viewModel.selectTab(tab1)
        val activeTab = viewModel.tabs.value.find { it.id == tab1Id }!!
        assertFalse("Selected tab must clear unread badge", activeTab.hasUnread)
    }
}
