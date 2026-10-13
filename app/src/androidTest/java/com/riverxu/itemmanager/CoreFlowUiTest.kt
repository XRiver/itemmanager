package com.riverxu.itemmanager

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverxu.itemmanager.ui.TestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 核心流程 UI 测试（需连接设备或模拟器执行）。
 *
 * 覆盖：首页 → 添加物品 → 保存 → 列表中可见 → 打开详情 → 搜索 → 设置。
 *
 * 两点环境/实现说明：
 * 1. 部分国产 ROM（如小米 HyperOS）默认拦截「后台弹出界面」，会让 `ActivityScenario`
 *    启动 Activity 被拒（日志：Abort background activity starts），表现为测试卡住不动。
 *    需在系统设置中为「物品管家Free」开启该权限。
 * 2. 可点击容器（如 ExtendedFloatingActionButton）内部的文字不一定合并进容器自身的
 *    语义节点，因此不能用 `onNodeWithText` 定位这类控件，统一改用 testTag。
 */
@RunWith(AndroidJUnit4::class)
class CoreFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun uniqueName(prefix: String): String =
        "$prefix${System.currentTimeMillis() % 100000}"

    /**
     * 通过 FAB 进入添加页并填写名称，返回所用名称。
     *
     * 保存后必须等待新物品真正出现在首页列表里：`waitForIdle` 只保证 Compose 空闲，
     * 不会等 Room 的 Flow 发射与随之而来的重组，否则后续断言会因竞态而随机失败。
     */
    private fun addItemNamed(prefix: String): String {
        val name = uniqueName(prefix)
        composeRule.onNodeWithTag(TestTags.FAB_ADD_ITEM).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.FIELD_ITEM_NAME).performTextInput(name)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ACTION_SAVE).assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        return name
    }

    @Test
    fun 首页显示标题与添加按钮() {
        composeRule.onNodeWithText("物品管家Free").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.FAB_ADD_ITEM).assertIsDisplayed()
    }

    @Test
    fun 添加物品后出现在列表中() {
        val name = addItemNamed("UI测试物品")
        composeRule.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun 点击物品打开详情抽屉() {
        val name = addItemNamed("详情测试")

        composeRule.onNodeWithText(name).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("大小分类").assertIsDisplayed()
        composeRule.onNodeWithText("位置").assertIsDisplayed()
        composeRule.onNodeWithText("完整编辑").assertIsDisplayed()
    }

    @Test
    fun 搜索无结果时显示空状态() {
        val name = addItemNamed("搜索测试")

        composeRule.onNodeWithTag(TestTags.HOME_ACTION_SEARCH).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.SEARCH_FIELD).performTextInput("绝不存在的关键字xyz")

        // 搜索有 250ms 防抖，且防抖走真实时间，waitForIdle 不会等它，这里显式轮询
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("没有符合条件的物品")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("没有符合条件的物品").assertExists()
        composeRule.onNodeWithText(name).assertDoesNotExist()
    }

    @Test
    fun 打开设置页面显示各分组() {
        composeRule.onNodeWithTag(TestTags.HOME_ACTION_MORE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.waitForIdle()

        // 首屏可见的分组
        composeRule.onNodeWithText("每张照片最大容量").assertIsDisplayed()
        composeRule.onNodeWithText("导出数据").assertIsDisplayed()

        // 靠后的分组需要滚动到才能断言可见
        composeRule.onNodeWithTag(TestTags.SETTINGS_LIST)
            .performScrollToNode(hasText("住处管理"))
        composeRule.onNodeWithText("住处管理").assertIsDisplayed()
    }
}
