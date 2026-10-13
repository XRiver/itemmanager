# 第三方资源与许可证

本项目自身代码为原创。启动图标使用了第三方开源图标，声明如下。

---

## 启动图标字形

应用启动图标中的「仓库」字形取自 **Tabler Icons** 的 `building-warehouse`。

- 项目：Tabler Icons — https://github.com/tabler/tabler-icons
- 许可：MIT License
- 使用方式：提取该图标的 SVG 路径数据，转为 Android VectorDrawable
  （`app/src/main/res/drawable/ic_launcher_foreground.xml`），
  颜色与背景由本项目自行设计。

```
MIT License

Copyright (c) 2020-2026 Paweł Kuna

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## 图标候选阶段的参考资料

选型时对比过以下图标库（最终**只使用**了上面 Tabler 的那一个字形，
其余均未被采用，此处仅记录评估来源）：

| 图标库 | 许可 | 说明 |
|--------|------|------|
| [Tabler Icons](https://github.com/tabler/tabler-icons) | MIT | **已采用** |
| [Lucide](https://github.com/lucide-icons/lucide) | ISC | 仅评估 |
| [Phosphor Icons](https://github.com/phosphor-icons/core) | MIT | 仅评估 |
| [Bootstrap Icons](https://github.com/twbs/icons) | MIT | 仅评估 |
| [Material Symbols](https://github.com/google/material-design-icons) | Apache-2.0 | 仅评估 |

---

## 运行期依赖

运行期第三方库（Jetpack Compose、Room、Navigation、Coroutines 等）均为
AndroidX / Kotlin 官方组件，许可证为 Apache-2.0，通过 Gradle 依赖引入，
其许可证随 AAR 一同分发。完整清单可用以下命令查看：

```powershell
.\gradlew :app:dependencies --configuration debugRuntimeClasspath
```
