# Frontend refinement — 2026-09-07

目标是让用户看清当前内容，并用一次操作进入最常用的下一步。页面高度优先留给历史记录、实际日程和投递信息；主题色只强调选中状态、主要操作和临近提醒。

## 参考与取舍

- [Apple Sidebars](https://developer.apple.com/design/human-interface-guidelines/sidebars)：稳定的位置、熟悉的图标、清晰的分组。桌面将三项训练压成一行，将三个求职页面保留为有文字的独立入口。
- [Apple Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)：手机常用目的地持续可见。因此底部固定四项，而非把三个求职入口藏入更多菜单。输入时给软键盘让出空间。
- [Apple Motion](https://developer.apple.com/design/human-interface-guidelines/motion)：动效简短、可中断、服务于状态理解。保留侧栏和输入栏的合成动画，流式短语在 160ms 内从可读状态淡入，尊重减少动态效果。
- [Linear design refresh](https://linear.app/now/behind-the-latest-design-refresh)：采用安静的中性色、克制的分隔与统一的标题层级。去掉求职页的大色块宣传头图，使用瓷白、石墨和淡鸢尾蓝；不照搬其产品结构。
- [Vercel Web Design Guidelines skill](https://github.com/vercel-labs/agent-skills/blob/main/skills/web-design-guidelines/SKILL.md) 及其 [审查规则](https://github.com/vercel-labs/web-interface-guidelines/blob/main/command.md)：用于检查语义链接、键盘焦点、表单标签、短暂反馈、溢出、触摸区域、动效和对比度。
- [Anthropic frontend-design skill](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md)：参考其明确视觉方向、避免泛化装饰和实看截图的工作方法，具体布局仍以现有业务为准。

## 对抗式审查及修复

| 反例 | 处理与验证 |
| --- | --- |
| 常用求职入口被搜索菜单隐藏 | 桌面和手机都直接提供待办、投递、秋招；真实点击测试覆盖四个宽度 |
| 侧栏导航挤压历史记录 | 三项训练横排；900px 高度下导航为 246px、历史列表为 508px；折叠后恢复列表滚动位置 |
| 笔记本上秋招投递按钮被挤出画面 | 大屏重新分配列宽，中屏改为带字段标签的分组记录；断言操作列完全可见 |
| 320px 下四个统计项产生横向溢出 | 栅格允许缩小，不再设置固定单元最小宽度；页面溢出断言 |
| 固定底部导航压住输入或软键盘 | 内容预留导航高度；以窗口可视区域判断键盘，不使用物理屏幕高度；模拟键盘断言输入栏完整可见 |
| 只看空状态，漏掉真实日程颜色问题 | 使用两个实际结构的日程夹具，分别检查明暗主题；统一旧绿色日期、提醒和面试装饰 |
| 新增操作需要滚动寻找表单 | 顶栏新增按钮直接定位并聚焦公司名；移动端日程列表优先显示 |
| 自动聚焦搜索让手机键盘突然弹起 | 仅精确指针设备打开切换器后自动聚焦；保留手动搜索与桌面快捷键 |
| 快速操作时弹层入场后的自动聚焦抢走选项焦点 | 入场完成时先检查焦点是否已在面板内；键盘回归等待实际焦点状态，避免依赖机器调度时序 |
| 流式回复反复重绘、抢走阅读位置 | 保留增量解析和块复用、100ms 有界刷新及有界短语节点；检查上滚暂停跟随、停止保留内容和草稿、IME Enter |
| 旧通用动画把尺寸变化也变成补间 | 常用输入、按钮、头像控件指定实际需要过渡的属性；最新回复入口只动画透明度和位移 |

## 验收边界

单元/组件测试、正式构建、主要页面四尺寸浏览器回归、明暗主题文字对比度，以及三个求职入口的专项交互检查共同验证此次修改。浏览器使用独立 API 夹具，不向生产账户写入测试数据。截图和报告保存在被 Git 忽略的 `frontend/test-results/`；自动检查覆盖实际执行的状态，不代表所有设备的帧率或所有页面状态的无条件保证。
