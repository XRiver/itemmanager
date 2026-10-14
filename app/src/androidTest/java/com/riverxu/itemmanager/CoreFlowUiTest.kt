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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverxu.itemmanager.domain.model.FilterSpec
import com.riverxu.itemmanager.domain.model.SortField
import com.riverxu.itemmanager.ui.TestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 核心流程 UI 测试（需连接设备或模拟器执行）。
 *
 * 覆盖：首页 → 添加物品 → 保存 → 列表中可见 → 打开详情 → 搜索 → 设置。
 *
 * 三点环境/实现说明：
 * 1. 部分国产 ROM（如小米 HyperOS）默认拦截「后台弹出界面」，会让 `ActivityScenario`
 *    启动 Activity 被拒（日志：Abort background activity starts），表现为测试卡住不动。
 *    需在系统设置中为「物品管家Free」开启该权限。
 * 2. 可点击容器（如 ExtendedFloatingActionButton）内部的文字不一定合并进容器自身的
 *    语义节点，因此不能用 `onNodeWithText` 定位这类控件，统一改用 testTag。
 * 3. **这个测试直接写真实数据库**（MainActivity 用的就是应用的 AppContainer，
 *    没有测试专用库），所以必须在 @After 里删掉自己造的数据，
 *    否则会在用户的真实数据里留下「UI测试物品xxxx」这类垃圾条目。
 */
@RunWith(AndroidJUnit4::class)
class CoreFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** 本次测试创建过的物品名（仅用于日志/调试） */
    private val createdNames = mutableListOf<String>()

    /**
     * 收尾清理。
     *
     * 采用**按名称前缀清扫**而不是"记住自己创建了哪些名字"：
     * 后者依赖测试正常走完（断言失败、超时、app 包与测试包版本不一致时都可能漏掉），
     * 实测就会偶尔漏下一条。前缀是本测试类独有的，清扫是幂等的，
     * 因此即使上次运行中断留下残余，下一次运行也会一并扫掉。
     */
    @After
    fun removeItemsCreatedByThisTest() {
        val app = ApplicationProvider.getApplicationContext<ItemManagerApp>()
        runBlocking {
            val repository = app.container.itemRepository
            val rows = repository.observeItems(FilterSpec.EMPTY, SortField.defaultSort()).first()
            rows.filter { row -> TEST_NAME_PREFIXES.any { row.name.startsWith(it) } }
                .forEach { repository.deleteItem(it.id) }
        }
        createdNames.clear()
    }

    private fun uniqueName(prefix: String): String =
        "$prefix${System.currentTimeMillis() % 100000}"

    /**
     * 通过 FAB 进入添加页并填写名称，返回所用名称。
     *
     * 保存后必须等到物品**真正写入数据库**再继续：`waitForIdle` 只保证 Compose 空闲，
     * 不会等 Room 的 Flow 发射与随之而来的重组，否则后续断言会因竞态而随机失败。
     *
     * 这里刻意在数据层等待、而不是等列表上出现该文字：列表是 `LazyColumn`，
     * 物品多起来之后新建的条目可能排在可视区之外、根本没有被渲染，
     * 等"看得见"会随数据量增大而必然失败。
     */
    private fun addItemNamed(prefix: String): String {
        val name = uniqueName(prefix)
        composeRule.onNodeWithTag(TestTags.FAB_ADD_ITEM).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.FIELD_ITEM_NAME).performTextInput(name)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.ACTION_SAVE).assertIsEnabled().performClick()

        waitUntilPersisted(name)
        createdNames += name
        return name
    }

    /** 轮询数据库，确认物品已落库。 */
    private fun waitUntilPersisted(name: String) {
        val app = ApplicationProvider.getApplicationContext<ItemManagerApp>()
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val exists = runBlocking {
                app.container.itemRepository
                    .observeItems(FilterSpec.EMPTY, SortField.defaultSort())
                    .first()
                    .any { it.name == name }
            }
            if (exists) return
            Thread.sleep(100)
        }
        throw AssertionError("物品未在 5 秒内写入数据库：$name")
    }

    /** 把列表滚动到目标条目，使可见性断言与滚动位置无关。 */
    private fun scrollToItem(name: String) {
        composeRule.onNodeWithTag(TestTags.ITEM_LIST).performScrollToNode(hasText(name))
        composeRule.waitForIdle()
    }

    @Test
    fun 首页显示标题与添加按钮() {
        composeRule.onNodeWithText("物品管家Free").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.FAB_ADD_ITEM).assertIsDisplayed()
    }

    @Test
    fun 添加物品后出现在列表中() {
        val name = addItemNamed("UI测试物品")
        scrollToItem(name)
        composeRule.onNodeWithText(name).assertIsDisplayed()
    }

    @Test
    fun 点击物品打开详情抽屉() {
        val name = addItemNamed("详情测试")
        scrollToItem(name)

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

    private companion object {
        /**
         * 本测试类创建物品时统一使用的前缀。
         *
         * 这些名字只可能由本测试产生，因此收尾时按前缀清扫不会误伤真实数据。
         */
        val TEST_NAME_PREFIXES = listOf("UI测试物品", "详情测试", "搜索测试")
    }
}
