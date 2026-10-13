# 物品管家Free（ItemManagerFree）

个人物品整理记录应用：记录物品信息与存放位置，方便日后查找、搜索和汰换。

依据《Open物品收纳-设计文档 v1.0》实现（设计文档原名 Open物品收纳 / Open Item Manager，应用已更名为「物品管家Free」，包名与 Gradle 工程名保持 `com.riverxu.itemmanager` / `OpenItemManager` 未变）。

---

## 一、当前状态

| 项目 | 状态 |
|------|------|
| 代码 | 已完成，`:app:assembleDebug` / `assembleRelease` 通过 |
| 单元测试 | **88 个用例全部通过**（`./gradlew :app:testDebugUnitTest`） |
| 真机测试 | **14 个用例实机全绿**（5 个 UI 流程 + 9 个真机功能） |
| 真机验证 | **已完成** —— 小米 2510DRK44C / Android 16（API 36）/ arm64-v8a / 1156×2510 |

真机验证中确认正常的部分：应用启动、首页列表与计数、添加物品全流程、详情抽屉、
搜索空状态、设置页各分组、真实 JPEG 编码器压缩（容量上限与 1920px 限制）、
照片按 `{物品id}_{时间戳}.jpg` 落盘与清理、真实 SQLite 上的增删改查、复合排序、
删除位置归类到「未设置」、删除住处级联、搬家三种迁移方式。

仍需人工操作验证（涉及系统相机与 SAF 文件选择器，无法自动化）：
拍照压缩、相册选择、导出 ZIP 到自选目录、再导入回来。

已实现的功能全部来自设计文档：

- **住处管理**：多住处、切换、筛选、改名、删除（级联清理）
- **物品记录**：名称、注释、大小分类（家具/大件/小件）、类型、照片、购入时间（含"很久"）、过期日期、位置
- **搜索筛选**：名称+注释模糊搜索，按住处/位置/类型/大小分类/过期状态/购入时间区间筛选
- **复合排序**：最多 3 个字段，可分别设置升/降序与空值位置
- **筛选快捷入口**：保存当前筛选与排序，长按管理
- **列表/网格切换**、左滑删除（可配置是否二次确认）
- **物品详情底部抽屉**：字段就地编辑、点击字段复制到剪贴板、删除
- **搬家模式**：选老/新住处 → 按位置排序浏览 → 多选 → 指定新位置 / 同位迁移 / 灵活迁移
- **设置**：照片压缩质量（32 KB / 64 KB / 128 KB / 256 KB / 512 KB / 1 MB）、主题（跟随系统 / 浅色 / 深色）、删除确认开关、位置与类型管理
- **数据备份**：导出 ZIP（SQLite 数据库 + 全部照片 + manifest），导入时校验完整性、同名住处自动改名
- **照片压缩**：最长边 ≤ 1920px；每个尺寸下用二分查找求满足容量上限的最高 JPEG 质量（下限 30）；
  仍超上限则按尺寸阶梯 `1920 → 1440 → 1080 → 810 → 608 → 480` 逐级缩小；
  解码显示按需采样 + 内存 LRU 缓存

---

## 二、构建与运行

### 环境（本机已就绪）

| 组件 | 位置 / 版本 |
|------|-------------|
| JDK | `D:\dev\jdk17`（构建）、`D:\dev\jdk21`（Robolectric 单测运行） |
| Android SDK | `D:\dev\android-sdk`（platform 36、build-tools 36.0.0、platform-tools） |
| Gradle | `D:\dev\gradle-9.8.0`（项目内含 wrapper） |
| Gradle 缓存 | `D:\dev\gradle-home`（含阿里云镜像初始化脚本） |

### 命令

```powershell
$env:JAVA_HOME = "D:\dev\jdk17"
$env:GRADLE_USER_HOME = "D:\dev\gradle-home"
cd <项目根目录>

# 编译 + 打包 Debug APK
.\gradlew :app:assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk

# 单元测试
.\gradlew :app:testDebugUnitTest
# 报告：app\build\reports\tests\testDebugUnitTest\index.html

# 真机测试（推荐用 am instrument，理由见下一节）
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.riverxu.itemmanager.test/androidx.test.runner.AndroidJUnitRunner

# 也可用 Gradle 路径（注意：UTP 会在跑完后卸载 App，见下节）
.\gradlew :app:connectedDebugAndroidTest

# 安装到手机
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Release 签名与 R8 瘦身

release 构建开启了 R8 代码压缩 + 资源压缩，体积从 44.5 MB 降到 **约 3.1 MB**
（主要是剥掉 material-icons-extended 里数千个未使用的图标）。

签名配置从 **`keystore.properties`** 读取（该文件与 `keystore/` 下的密钥库都**不入库**）：

```properties
storeFile=keystore/itemmanager-release.jks
storePassword=...
keyAlias=itemmanager
keyPassword=...
```

- 文件不存在时 release 仍可构建，只是产出 `app-release-unsigned.apk`
- **密钥库与口令务必单独备份**：丢失后无法再对已发布应用做覆盖升级

```powershell
# 签名 release
.\gradlew :app:assembleRelease
# 产物：app\build\outputs\apk\release\app-release.apk

# 校验签名
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

### `staging` 构建：验证 R8 产物而不丢数据

R8 有改变运行时行为的风险，而 release 包用 release 密钥签名，没法覆盖安装到
debug 包上（签名冲突，得先卸载、会丢数据）。因此多了一个 `staging` 构建类型：

- R8 / 资源压缩配置**与 release 完全一致**（`initWith(release)`）
- 但用 **debug 密钥签名**，因此可以 `install -r` 覆盖到已装 debug 包的设备上验证

```powershell
.\gradlew :app:assembleStaging
adb install -r -t app\build\outputs\apk\staging\app-staging.apk
```

> 注意：不能用「给 debug 构建开启 minify」来代替 —— debuggable 构建会被 Gradle
> 跳过全部优化与混淆（会打印 `All code optimizations and obfuscation are disabled
> for debuggable builds`），等于没验证到 R8。

### 应用信息

- 应用名：物品管家Free
- 包名：`com.riverxu.itemmanager`
- 版本：0.1.0（versionCode 1）
- minSdk 29（Android 10） / targetSdk 36 / compileSdk 36

### 应用图标

自适应图标（Adaptive Icon），minSdk 29 下所有设备都走 adaptive 路径：

| 层 | 文件 | 内容 |
|----|------|------|
| background | `res/drawable/ic_launcher_background.xml` | 绿色线性渐变（#2E8B72 → #14524A），铺满 108×108 |
| foreground | `res/drawable/ic_launcher_foreground.xml` | 白色「仓库」字形，约占整层 46%，居中落在安全区内 |
| monochrome | 复用 foreground | 供 Android 13+ 主题图标取色 |
| 入口 | `res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | 组合上面三层 |

字形取自 Tabler Icons 的 `building-warehouse`（MIT 许可），已把 SVG 路径数据转为
VectorDrawable；许可证声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

> 注意：`mipmap-anydpi-v26` 这个目录名里的 `-v26` 不能去掉。Lint 会报
> `ObsoleteSdkInt` 说它多余（因为 minSdk 是 29），但去掉后 aapt2 会直接报
> `resource mipmap/ic_launcher not found` —— adaptive-icon 的 XML 只认 v26 及以上的限定符。

---

## 二之二、真机测试注意事项（小米 HyperOS 实测踩坑）

以下三条都是实测踩出来的，换设备/换系统不一定遇到，但遇到时症状都很像"代码坏了"。

### 1. 「后台弹出界面」权限会拦住自动化测试

**症状**：UI 测试卡死不动，日志里能看到

```
MIUILOG- Permission Denied Activity : Intent { ... cmp=com.riverxu.itemmanager/.MainActivity }
ActivityTaskManager: Abort background activity starts from <uid>
```

**原因**：MIUI 默认禁止应用后台弹出界面。`ActivityScenario` 启动 Activity 被系统拒绝，
测试就一直等 Activity 变成 RESUMED。`appops` 无法绕过（试过 `SYSTEM_ALERT_WINDOW`
和多个 op 名，均无效），只能在系统设置里开：

> 设置 → 应用设置 → 应用管理 → 物品管家Free → 权限管理 → 后台弹出界面 → 允许

（也可在设置顶部直接搜"后台弹出界面"，进去后再选该应用。）

### 2. 这个权限不是"一次授权永久有效"

实测它会在下面几种情况被重置，都需要重新开一次：

- 先卸载再安装（全新安装）→ **重置**；用 `adb install -r` 覆盖安装 → 保留
- `adb shell pm clear <包名>`（清除数据）→ 实测也会重置
- 系统清理（手机管家清缓存/后台、长时间未使用后由系统回收）→ 观察到的现象是隔天再跑测试又被拦截了

判断方法：跑测试时如果卡在第一帧不动，先看日志里有没有
`Abort background activity starts`，有就是权限又没了。

> 补充：这只是**自动化测试**的限制。用户从桌面正常点开应用不受影响
> （前台启动永远允许），所以日常使用完全没问题。

### 3. Gradle 的 `connectedDebugAndroidTest` 跑完会卸载 App

UTP 的 `uninstall_after_test` 默认为 true，跑完就把 App 卸掉 →
下次要全新安装 → 权限又被重置，于是又卡住，形成循环。

**因此本项目推荐用 `am instrument` 跑真机测试**：先 `install -r` 装一次，
之后反复 `am instrument` 都不会卸载 App，也不需要再授权。

```powershell
# 首次（或 App 更新后）装一次
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk

# 之后反复跑，不会卸载 App
adb shell am instrument -w com.riverxu.itemmanager.test/androidx.test.runner.AndroidJUnitRunner

# 只跑某个类
adb shell am instrument -w -e class com.riverxu.itemmanager.CoreFlowUiTest `
  com.riverxu.itemmanager.test/androidx.test.runner.AndroidJUnitRunner
```

> 如果测试卡住而日志没有被拦截的痕迹，用 `adb shell am force-stop` 清理一下再重跑：
> 被 `Stop-Job` 之类中断的 `am instrument` 会在设备上留下仍在等待的测试进程。

---

## 三、技术实现与设计文档的差异说明

设计文档指定的技术栈基本落地，以下两点做了替换，原因如下：

| 设计文档 | 实际实现 | 原因 |
|----------|----------|------|
| Hilt 依赖注入 | 手写 `AppContainer` 容器 | AGP 9 起 Kotlin 支持内置，Hilt 注解处理器与当前 AGP/Kotlin 组合存在版本兼容风险，会导致无法构建。手写容器同样做到单一实例、懒加载、便于测试替换。 |
| Coil 图片加载 | 自研 `PhotoCompressor` + `PhotoStore`（`LruCache` 内存缓存 + 按需采样解码） | 避免额外依赖，并直接满足设计文档"照片加载使用采样压缩、滑动时取消不可见项加载"的要求。 |
| 压缩档位 500KB / 1MB / 2MB | **32 KB / 64 KB / 128 KB / 256 KB / 512 KB / 1 MB**（默认 128 KB） | 按需求调整：物品照片主要用作辨认参照，原档位偏大。改为 32 KB 起的倍增序列，小目标下压缩器会逐级缩小尺寸来达到（见下）。 |

另外三点补充说明：

- **全文搜索**：设计文档写的是 SQLite FTS。当前实现为 JOIN 查询 + `name`/`note` 上的 `LIKE` 索引扫描，并对 `%`、`_` 做了转义。在个人物品量级（数千条）下响应远快于 500ms 的目标；若数据量增大，可按设计文档追加 FTS4/FTS5 虚拟表，`ItemRepository` 接口无需变更。
- **分页**：设计文档写的是 Paging 3，每页 50 条。当前用 `LazyColumn` + `@RawQuery` 动态查询，`ItemQueryBuilder` 已支持 `LIMIT/OFFSET`（默认不分页，全部加载）。这一层替换同样不影响接口。
- **压缩算法为达到小目标做的调整**：设计文档只写了"JPEG 质量从 90 递减"。要压到 32 KB 仅靠降质量不够，因此额外做了三点：质量下限放宽到 30；质量选择用二分查找而非线性递减（编码次数由最坏约 35 次降到约 15 次，且取到的是满足上限的最高质量）；仍超上限时走尺寸阶梯 `1920 → 1440 → 1080 → 810 → 608 → 480`。若到底仍达不到目标，保存后会**如实提示实际大小**，而不是假装满足。

## 四、项目结构

```
app/src/main/java/com/riverxu/itemmanager/
├── data/
│   ├── local/
│   │   ├── database/          # Room 实体、DAO、AppDatabase、类型转换器
│   │   ├── photo/             # 照片压缩与存储
│   │   ├── export/            # ZIP 导入导出
│   │   └── prefs/             # 设置与"上次输入"记忆
│   ├── repository/            # ItemRepository 接口 + SQLite 实现 + 查询构造
│   └── model/                 # 数据层模型
├── domain/model/              # 领域模型（枚举、筛选条件、预置数据、字段上限）
├── ui/
│   ├── home/                  # 首页、物品列表/网格、筛选与排序面板
│   ├── item/                  # 添加/编辑表单、详情抽屉
│   ├── move/                  # 搬家模式
│   ├── settings/              # 设置、住处/位置/类型管理
│   ├── components/            # 通用组件（照片、选择器、日期、对话框）
│   └── theme/                 # Material 3 主题
├── di/                        # AppContainer
└── util/                      # 日期处理、筛选条件 JSON 编解码
```

## 五、测试覆盖

### 单元测试（JVM，88 个用例，全部通过）

| 测试类 | 用例数 | 覆盖内容 |
|--------|-------|----------|
| `SQLiteItemRepositoryTest` | 39 | 预置数据、物品增删改查、名称校验、位置 LRU、删除位置/类型的级联处理、复合排序与各类筛选、搬家三种模式、住处级联删除、统计 |
| `PhotoCompressorTest` | 17 | 容量上限、1920px 限制、不放大小图、**六档位均为倍增序列且每档都能达到目标**、**尺寸阶梯收尾于下限 480**、二分选最高质量、按尺寸采样解码、照片落盘命名与清理、缓存命中 |
| `ItemQueryBuilderTest` | 13 | 排序字段与方向、空值位置、关键字与筛选参数绑定、LIKE 转义、分页、计数查询 |
| `DateUtilsTest` | 10 | "很久"、日期格式、剩余/已过期天数文案、过期等级、字节格式化 |
| `FilterCodecTest` | 9 | 筛选条件与排序的 JSON 往返、缺字段容错、非法输入 |

### 真机测试（Android 16 实机，14 个用例，全部通过）

| 测试类 | 用例数 | 覆盖内容 |
|--------|-------|----------|
| `CoreFlowUiTest` | 5 | 首页标题与 FAB、添加物品后入列表、点击打开详情抽屉、搜索空状态、设置页各分组（含 LazyColumn 滚动） |
| `RealDeviceFunctionalTest` | 9 | 真实 JPEG 编码器的容量上限、**32 KB 最小档位也能达到**、1920px 限制、小图不放大、**损坏文件解码失败**、照片落盘命名与清理、真实 SQLite 增删改查/删除位置归类/住处级联/搬家迁移 |

> 真机测试需要设备与「后台弹出界面」权限（见第二节）。
>
> 注意：改了 app 侧代码后**必须同时重建测试 APK**，否则测试包还引用旧字段，
> 会以 `NoSuchFieldError` 失败（本项目就踩过一次：改压缩档位后漏重建测试包，
> 5 个用例全报 `NoSuchFieldError: Quality.MEDIUM`）。

**关于两处测试盲区**：

1. Robolectric 的 `BitmapFactory` 影子实现会把任意输入伪造成 100×100 图片，所以
   "照片损坏 → 解码失败"在 JVM 单测里无法构造。这一点由 `RealDeviceFunctionalTest`
   在真机上覆盖（该用例已验证通过），单测里也显式记录了这个限制。
2. 涉及系统相机与 SAF 文件选择器的流程（拍照、选相册、导出/导入）需要人工操作，
   未纳入自动化。

### 真机测试写法上的两个坑（已在测试代码中规避）

1. **不能用文字定位可点击容器**：`ExtendedFloatingActionButton` 内部的 `Text`
   不会合并进它自己的语义节点（实测 `merged` 树中匹配数为 0、`unmerged` 树为 1），
   于是既点不到也断言不了显示。统一改用 `TestTags` 里的 `testTag` 定位控件。
2. **`waitForIdle()` 不等 Room Flow 与防抖**：保存物品后列表要等 Room 的 Flow 发射
   才会更新，搜索有 250ms 防抖（走真实时间）。断言前用 `waitUntil { ... }` 轮询等待，
   否则会出现"时而通过时而失败"的竞态。

## 六、真机验证中发现并修复的问题

| 问题 | 现象 | 修复 |
|------|------|------|
| 顶栏标题折行 | 1156px 宽屏幕上应用名被折成两行 | 标题改 `titleLarge` + `maxLines=1`，把排序/布局切换/搬家/设置收进溢出菜单 |
| 左滑删除背景透出 | 每个卡片右侧一直能看到垃圾桶图标和红色底层 | 卡片背景由半透明 `surfaceVariant.copy(alpha=0.5f)` 改为不透明的 `surfaceContainerLow` |
| 卡片变粉紫色 | 补了不透明背景后卡片呈粉紫，与绿色主题不符 | 自定义配色缺少 `surfaceContainer*` 色阶，Material3 退回默认紫色基线；在明暗两套配色里补齐该色阶 |
| **32 KB 档位达不到目标** | 真机测试报「32 KB 档实际 43 KB，应不超过 32 KB」 | 尺寸回退只写了"下一个值 ≤ 480 就停"，于是 1920 起递减只跑到 608px，**480 这一档从未被尝试**。改为显式尺寸阶梯 `1920→1440→1080→810→608→480`，最后一档必定是 `MIN_DIMENSION` |

> 最后一条是**真机测试真正抓出来的算法缺陷**：JVM 单测里 Robolectric 的编码器是伪造的，
> 每个档位都"轻松通过"，只有真实 JPEG 编码器才暴露出 480px 这档没跑到。
> 现在单测里也补了直接校验尺寸阶梯构成的回归用例。

## 七、仍需人工确认的项

1. 相机拍照 → 照片按配置质量压缩后落盘，列表与详情可正常显示
2. 相册选择照片（`PickVisualMedia`）在 Android 10～16 各版本的表现
3. SAF 导出到自选目录、再导入回来的完整往返（含同名住处自动改名）
4. 1000+ 物品时列表滑动流畅度与搜索响应
5. 深色模式与系统主题联动
6. 不同屏幕尺寸下的详情抽屉与表单布局
