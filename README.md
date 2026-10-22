# 物品管家Free（ItemManagerFree）

个人物品整理记录应用：记录物品信息与存放位置，方便日后查找、搜索和汰换。

依据《Open物品收纳-设计文档 v1.0》实现。应用名为「物品管家Free」，包名与 Gradle 工程名保持
`com.riverxu.itemmanager` / `OpenItemManager`。

---

## 功能

### 住处与位置

- **多住处管理**：新增、切换、改名、删除；删除住处会级联清理其下的位置与物品
- **位置管理**：每个住处自动生成「灵活」「未设置」两个预置位置；位置按最近使用排序（LRU）
- **删除位置**时，该位置下的物品自动归入「未设置」

### 物品

- **物品字段**：名称、注释、大小分类（家具/大件/小件）、类型、标签、照片、购入时间（含"很久"，可一键填「今天」）、
  过期日期、存放位置、录入时间、最近更新位置时间
- **照片**：拍照或从相册选择，按设置的容量上限压缩后落盘为 `{物品id}_{时间戳}.jpg`
- **列表 / 网格**两种视图，左滑删除（可配置是否二次确认），长按进入多选
- **物品详情底部抽屉**：字段就地编辑、点击字段复制到剪贴板、跳转完整编辑、删除

### 标签（多选）

- 标签由用户手动创建，一件物品可同时拥有多个
- 预置「待汰换」「待出售」两个标签，不可删除
- **新建物品时默认不带任何标签**
- 设置页可添加 / 重命名 / 删除标签；重命名到已存在的名称时自动合并两个标签；
  删除标签只解除物品上的关联，不影响物品本身

### 搜索、筛选与排序

- **搜索**：按名称与注释模糊匹配（`LIKE`，已转义 `%` / `_`），带 250ms 防抖
- **筛选**：住处、位置、类型、**标签（可多选，符合任一）**、大小分类、过期状态、购入时间区间
- **复合排序**：最多 3 个字段，每个字段可分别设置升/降序与空值位置
  可选字段：位置、名称、购入时间、过期时间、录入时间、修改时间、最近更新位置、大小分类、类型、住处
- **筛选快捷入口**：保存当前筛选与排序组合；**短按应用、再短按取消**（取消时还原到应用前的条件），
  长按删除。已应用某个快捷入口时，不再显示「清除筛选」「保存为快捷入口」

### 搬家模式

选择原住处与新住处 → 按位置排序浏览物品 → 多选 → 指定具体位置 / 同位迁移 / 灵活迁移。

### 系统自动维护的时间字段

| 字段 | 规则 |
|------|------|
| 录入时间 | 物品创建时写入，之后不可修改 |
| 最近更新位置时间 | 仅当位置或所属住处发生变化时推进；改名称、注释、照片都不会影响；搬家迁移会推进 |

两个字段在详情页只读展示（精确到分钟），不提供编辑入口。

### 设置

- **照片压缩质量**：32 KB / 64 KB / 128 KB / 256 KB / 512 KB / 1 MB（默认 128 KB）
- **主题**：跟随系统 / 浅色 / 深色
- **删除确认**开关
- 住处、位置、类型、标签管理
- 数据统计（物品/住处/位置/类型/标签/照片数量与占用空间）

### 数据备份

- **导出**：生成 ZIP，内含 SQLite 数据库文件、全部照片与 `manifest.json`
- **导入**：校验备份完整性后整库替换，同名住处自动改名
- 可导入旧版本导出的备份（见「数据库」一节）

---

## 技术栈

| 项 | 选型 |
|----|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Repository |
| 数据库 | Room（KSP 注解处理） |
| 异步 | Coroutines + Flow |
| 导航 | Navigation Compose |
| 构建 | AGP 9.0.1（内置 Kotlin）+ Gradle 9.8.0 |
| 最低版本 | minSdk 29（Android 10） / targetSdk 36 / compileSdk 36 |

### 与设计文档的技术选型差异

| 设计文档 | 实际实现 | 说明 |
|----------|----------|------|
| Hilt 依赖注入 | 手写 `AppContainer` 容器 | AGP 9 起 Kotlin 支持内置，Hilt 注解处理器与该组合存在版本兼容风险。手写容器同样做到单一实例、懒加载、便于测试替换 |
| Coil 图片加载 | 自研 `PhotoCompressor` + `PhotoStore` | 避免额外依赖，并直接满足"采样压缩、滑动时取消不可见项加载"的要求 |
| 压缩档位 500KB / 1MB / 2MB | 32 KB ~ 1 MB 倍增序列（默认 128 KB） | 物品照片主要用作辨认参照，原档位偏大 |
| SQLite FTS 全文搜索 | `LIKE` + 索引扫描，转义 `%` / `_` | 个人物品量级下响应足够；数据量增大后可追加 FTS 虚拟表，`ItemRepository` 接口无需变更 |
| Paging 3 分页 | `LazyColumn` + `@RawQuery`，`ItemQueryBuilder` 已支持 `LIMIT/OFFSET` | 默认不分页；替换不影响接口 |

### 照片压缩算法

目标是在设定的容量上限内保留尽可能高的画质：

1. 最长边不超过 1920px
2. 每个尺寸下用**二分查找**求满足容量上限的最高 JPEG 质量（质量范围 90 → 30）
3. 若该尺寸下最低质量仍超上限，按尺寸阶梯 `1920 → 1440 → 1080 → 810 → 608 → 480` 逐级缩小后重试
4. 若到达尺寸与质量下限仍无法满足，保存后**如实提示实际大小**

照片显示采用按需采样解码 + 内存 `LruCache`。

---

## 项目结构

```
app/src/main/java/com/riverxu/itemmanager/
├── data/
│   ├── local/
│   │   ├── database/          # Room 实体、DAO、AppDatabase、迁移、类型转换器
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
│   ├── settings/              # 设置、住处/位置/类型/标签管理
│   ├── components/            # 通用组件（照片、选择器、日期、对话框）
│   └── theme/                 # Material 3 主题
├── di/                        # AppContainer
└── util/                      # 日期处理、筛选条件 JSON 编解码
```

---

## 构建与运行

### 环境

| 组件 | 版本 |
|------|------|
| JDK | 17（构建）、21（Robolectric 单元测试运行） |
| Android SDK | platform 36、build-tools 36.0.0 |
| Gradle | 9.8.0（项目内含 wrapper） |

### 常用命令

```bash
# 编译 + 打包 Debug APK
./gradlew :app:assembleDebug

# 单元测试
./gradlew :app:testDebugUnitTest

# Release（R8 压缩 + 签名）
./gradlew :app:assembleRelease

# 真机测试
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.riverxu.itemmanager.test/androidx.test.runner.AndroidJUnitRunner
```

### 构建类型

| 类型 | 签名 | R8 压缩/混淆 | 用途 |
|------|------|--------------|------|
| `debug` | debug | 否 | 日常开发与真机测试 |
| `release` | release | 是 | 发布 |
| `staging` | debug | 是 | 在已装 debug 包的设备上覆盖安装，用于验证 R8 产物 |

`staging` 与 `release` 的 R8 与资源压缩配置完全一致，仅签名不同，因此可以
`adb install -r` 覆盖到已装 debug 包的设备上验证混淆后的运行时行为。

R8 代码压缩 + 资源压缩将 APK 从 44.5 MB 降到约 3.2 MB。

### Release 签名

签名信息从 `keystore.properties` 读取，该文件与 `keystore/` 下的密钥库均**不入库**：

```properties
storeFile=keystore/itemmanager-release.jks
storePassword=...
keyAlias=itemmanager
keyPassword=...
```

文件不存在时 release 仍可构建，产物为 `app-release-unsigned.apk`。

```bash
# 校验签名
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

### 应用信息

| 项 | 值 |
|----|-----|
| 应用名 | 物品管家Free |
| 包名 | `com.riverxu.itemmanager` |
| 版本 | 0.3.0（versionCode 3） |
| minSdk / targetSdk / compileSdk | 29 / 36 / 36 |

### 应用图标

自适应图标（Adaptive Icon），由三层构成：

| 层 | 文件 | 内容 |
|----|------|------|
| background | `res/drawable/ic_launcher_background.xml` | 绿色线性渐变（#2E8B72 → #14524A），108×108 |
| foreground | `res/drawable/ic_launcher_foreground.xml` | 白色「仓库」字形，约占整层 46%，位于安全区内 |
| monochrome | 复用 foreground | 供 Android 13+ 主题图标取色 |

入口为 `res/mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml`。

字形取自 [Tabler Icons](https://github.com/tabler/tabler-icons) 的 `building-warehouse`（MIT 许可），
已将 SVG 路径数据转为 VectorDrawable。许可证声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

---

## 数据库

数据库版本与 app 版本分开管理，当前 **DB v2**，Schema 快照导出在 `app/schemas/`。

### 表结构

| 表 | 说明 |
|----|------|
| `residence` | 住处 |
| `location` | 位置，外键关联住处（CASCADE） |
| `item_type` | 类型 |
| `tag` | 标签，含 `isPreset` 标记 |
| `item` | 物品 |
| `item_tag` | 物品 ↔ 标签多对多关联（`itemId` + `tagId` 联合主键，双向 CASCADE） |
| `filter_shortcut` | 筛选快捷入口（筛选与排序的 JSON 快照） |

### 迁移历史

**v1 → v2（0.1.0 → 0.2.0）**

- 新增表 `tag`、`item_tag`
- `item` 新增列 `locationUpdatedAt`
- 已有行按 `createdAt` 回填 `locationUpdatedAt`
- 写入两个预置标签

### 备份兼容

导出的 ZIP 内直接存放 SQLite 文件。导入时会先读取被附加库的 `user_version`，
若低于当前版本则先补齐结构再复制数据，因此新版本可以直接导入旧版本的备份。

---

## 测试

### 单元测试（JVM / Robolectric，126 个用例）

```bash
./gradlew :app:testDebugUnitTest
```

| 测试类 | 用例数 | 覆盖内容 |
|--------|-------|----------|
| `SQLiteItemRepositoryTest` | 39 | 预置数据、物品增删改查、名称校验、位置 LRU、删除位置/类型的级联处理、复合排序与各类筛选、搬家三种模式、住处级联删除、统计 |
| `ItemTagsAndTimestampsTest` | 26 | 预置标签、标签多选与去重、自动建标签、重命名（含合并）、删除只解除关联、标签筛选、录入时间不可改、最近更新位置时间的推进规则、标签筛选的快捷入口编解码 |
| `PhotoCompressorTest` | 17 | 容量上限、1920px 限制、不放大小图、各档位均可达到目标、尺寸阶梯、二分选最高质量、按尺寸采样解码、照片落盘命名与清理、缓存命中 |
| `ItemQueryBuilderTest` | 13 | 排序字段与方向、空值位置、关键字与筛选参数绑定、LIKE 转义、分页、计数查询 |
| `HomeShortcutToggleTest` | 8 | 快捷入口短按应用 / 再短按取消并还原、排序一并还原、未保存排序时不影响当前排序、手动改动筛选后取消选中、多快捷入口之间切换互不干扰 |
| `DateUtilsTest` | 10 | "很久"、日期格式、剩余/已过期天数文案、过期等级、字节格式化 |
| `FilterCodecTest` | 9 | 筛选条件与排序的 JSON 往返、缺字段容错、非法输入 |
| `MigrationTest` | 4 | v1→v2 迁移后既有数据完整保留、新列回填、预置标签就位、升级后新结构可用 |

### 真机测试（15 个用例）

| 测试类 | 用例数 | 覆盖内容 |
|--------|-------|----------|
| `CoreFlowUiTest` | 5 | 首页标题与 FAB、添加物品后入列表、点击打开详情抽屉、搜索空状态、设置页各分组 |
| `RealDeviceFunctionalTest` | 10 | 真实 JPEG 编码器的容量上限与 32 KB 最小档位、1920px 限制、小图不放大、损坏文件解码失败、照片落盘命名与清理、真实 SQLite 上的标签多选与筛选、增删改查 / 删除位置归类 / 住处级联 / 搬家迁移 |

说明：

- `CoreFlowUiTest` 通过 `MainActivity` 使用应用真实数据库，并在结束后清理自身创建的条目
- `RealDeviceFunctionalTest` 使用独立的测试数据库，运行前后都会删除
- 在 MIUI 等系统上运行 UI 测试，需为应用开启「后台弹出界面」权限
- 修改 app 侧代码后需同时重建测试 APK，否则测试包引用的仍是旧代码

### 未纳入自动化的部分

以下流程依赖系统相机与 SAF 文件选择器，需人工验证：

1. 拍照 → 压缩落盘 → 列表与详情显示
2. 相册选择照片（`PickVisualMedia`）
3. 导出 ZIP 到自选目录并重新导入
4. 大量物品（1000+）时的滑动流畅度与搜索响应
5. 深色模式与系统主题联动
6. 不同屏幕尺寸下的详情抽屉与表单布局

---

## 许可

应用图标使用的第三方图形资源许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
