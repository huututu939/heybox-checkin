# 盒签 · 开发计划与状态

目标：备用机（无 SIM 卡）安装一款安卓原生 App，每天在 08:00–20:00 之间随机挑一个时刻自动完成小黑盒签到；分享任务按开关执行，默认关闭。路线为 **A：协议层直调官方接口**。

当前版本：**v1.8.2（versionCode 28）**，签名 release 由 CI 出包并发布。

## 已锁定决策

| 项 | 决定 |
| --- | --- |
| 技术路线 | A 协议层自研，不做无障碍点击、不跑 Docker、不用第三方托管 |
| 登录方式 | 仅短信验证码；备用机发码、主手机收码、回备用机填 |
| 人机验证 | 不实现验证码页，命中时给出明确提示 |
| 任务范围 | 签到固定执行；分享帖子 / 游戏 / 评价各一个开关，默认全关 |
| 设备令牌 | WebView 内跑官方指纹 SDK（唯一无法纯算法复现的一步） |
| 调度 | AlarmManager 精确闹钟 + 前台服务（不用 WorkManager） |
| 会话存储 | 自研 Android Keystore AES-GCM |
| 分发 | 自用侧载 + 小范围群文件；非商业、不代签；是否对外分发由使用者评估平台协议与法律风险 |
| 混淆 | release 开 R8 + 资源收缩（v1.8.1 起，真机回归登录与小组件后启用） |

## 里程碑

| 里程碑 | 内容 | 状态 |
| --- | --- | --- |
| M0 工程骨架 | Gradle KTS、Manifest、主题、依赖 | 完成 |
| M1 签名移植 | native + web 签名，24 组黄金向量回归 | 完成 |
| M2 协议层 | 请求构造、RSA / gzip+AES 上报、接口封装、登录 API | 完成 |
| M3 业务层 | 响应解析与校验、任务编排、失败隔离 | 完成 |
| M4 设备与存储 | WebView 设备令牌（SHA-256 校验）、Keystore 会话 | 完成 |
| M5 调度与界面 | 随机闹钟、前台服务、开机重排、通知、登录 / 首页 | 完成 |
| M6 实机验证 | 华为 JER-AN10 / Android 12 跑通登录与完整签到 | 完成 |
| M7 发布打磨 | 签名 release、首启说明、关于页 / 许可、诊断、小组件、单色图标、Git | 完成 |
| M8 可靠性闭环 | 失败分类、连续漏签提醒、记录页月历与余额走势、更新检查节流 | 完成 |
| M9 值守自愈与回顾 | 截止闹钟 + JobScheduler 看门狗、连续签到与月度汇总、依赖与 CI 加固 | 完成 |
| M10 边角兜底与数据出口 | 当晚补签、时区重排、登录直达、90 天历史、CSV 导出、运行自检 | 完成 |
| M11 工程加固 | 编排单测、HomeScreen 拆分、Dependabot、R8 回归清单 | 完成 |
| M12 R8 上线 | 混淆 + 资源收缩，8.9 MB → 1.7 MB，真机回归通过 | 完成 |
| M13 审查修复 | 补签门控、时区重排、月份选中、权限探测合并、CSV 可见性 | 完成 |

## 本轮（v1.8.2）审查修复

Standards / Spec 两个轴并行审了 `v1.7.0...HEAD`，能落地的 10 条已修；纯风格项（SettingsScreen 再拆、Data Clumps）保留为技术债。

- 当晚补签加「常规重试还排着就等它」门（`Watchdog.shouldLateRetry` 的 `pendingRetry`），避免同一晚两次请求；`repairAlarm` 改用 `Scheduler.rearm`，不再手写排闹钟。
- 改时间 / 换时区重排用 `catchUp = false`，不再绕过网络与预算判定；错过的那次交给截止闹钟。
- 记录页默认选中当前月的最近一天；月份 key 收进 `WeekCell.monthKey` / `MonthGrid.monthKey`。
- 权限探测合并到 `data/PermissionState.kt`，诊断、自检、首页共用；自检按渠道判断通知是否真的发得出来，每个谓词只算一次。
- 分享成功率固定 30 天（`SHARE_WINDOW_DAYS`），界面标注「分享任务 · 30 天」。
- 首页状态从中文字符串改成 `TodayStatus` 枚举；`TodayTab` 去掉没用的 `hideRecent` 参数。
- Manifest 补 `<queries>`，Android 11+ 导出记录时能正确识别 CSV 分享目标。
- 文档：proguard 头注释、R8 清单、PLAN 已锁定决策与 keep 规则对齐。

## 本轮（v1.8.0）落地

方向：把「失败之后」和「换时区 / 改时间」这些边角兜住，再让 90 天历史真的可读、可带走。

- 当晚补签 `Watchdog.shouldLateRetry`：常规重试（2 次）用完、窗口也过了，如果失败原因是网络 / 服务端 / 核对，且现在有网，截止闹钟或 4 小时看门狗会再给一次机会；一天一次，靠 `SessionStore.takeLateRetry` 记账，避免把失败刷成高频请求。
- 网络判断用 `ConnectivityManager` 的 `NET_CAPABILITY_INTERNET`；没网时不扣预算，等下一次看门狗再试。
- 时区 / 改时间：`BootReceiver` 接 `TIME_SET` / `TIMEZONE_CHANGED` 后重新抽签——原时刻是按旧时区窗口抽的，可能落到窗口外。
- 登录过期：通知标题改「登录已过期」，动作从「立即重试」换成「重新登录」，`MainActivity` 用 `open_login` extra 直达登录页；定时与外观保留。
- 历史 30 → 90 天：记录页一次只展示选中的月份，顶部月份胶囊切换（11/10/9/8 月），余额走势也变成 90 天；连续签到最多可显示 90 天。
- 导出记录：设置 →「导出签到记录」生成带 UTF-8 BOM 的 CSV（RFC 4180 转义），走系统分享；`text/csv` 没有接收方时退回 `text/plain`，并补 `clipData` 把 URI 读权限传给分享面板（否则 chooser 报 Permission Denial）。
- 小组件：4×1 在连续 ≥2 天时把「已签到」换成「连续 N 天」。
- 设置页「运行自检」：一次检查登录、通知、电池优化、精确闹钟、定时、看门狗、设备令牌、接口连通，结果可复制。

## 工程加固（不单独发版）

这一轮没有用户可感知的变化，所以不单独发 Release，随下一个功能版一起出。

- 编排单测 `TaskRunnerTest`：假 Transport 走完「已签 / 签到 + 三分享 / 签到不确认 / 分享不结算 / 账号不匹配」五条路径；`TaskRunner` 加 `pause` 注入，测试不再真等几十秒。
- `HomeScreen.kt` 1708 行拆成 `HomeScreen.kt`（壳、状态、工具）、`TodayScreen.kt`、`SettingsScreen.kt`；纯移动，模拟器截图对比无变化。
- Dependabot：Gradle 每周、Actions 每月，按 androidx / kotlin 分组。
- `docs/r8-checklist.md`：真机回归步骤与最小 keep 规则。
- R8（v1.8.1）：真机验证登录 / 设备令牌 / 小组件 / 自检后开启 `isMinifyEnabled + isShrinkResources`，release 8.9 MB → 1.7 MB；keep 规则在 `app/proguard-rules.pro`。

## 本轮（v1.7.0）落地

方向：闹钟被 ROM 清掉是唯一还没解决的静默失效（用户自己的 11/20–11/25 就是整天空档），这一版让「当天必跑」有两层兜底；记录页从「看得见断在哪天」再往前一步，给出连续天数与月度汇总。

- 值守自愈 `work/Watchdog.kt` + `WatchdogJobService`：每天在窗口结束 10 分钟处落一个截止闹钟，当天没记录就补签；另有 4 小时周期任务（`setPersisted(true)`，跨重启），只负责重排丢失的闹钟。
- 为什么周期任务不直接起服务：Android 12+ 的后台启动限制不豁免 JobScheduler，直接 `startForegroundService` 会抛 `ForegroundServiceStartNotAllowedException`；截止闹钟走的是精确闹钟广播，才有拉起前台服务的豁免。
- 判定保守（`Watchdog.shouldCatchUp`）：今天已有记录（成功或失败）就不补——失败交给服务自己的重试预算，避免看门狗 4 小时一次把失败打成高频请求；今天的闹钟还没到点不抢跑；窗口开始前不打扰。
- 补挂点：`Scheduler.scheduleNext` / `rearm` / `cancel` 统一带上看门狗与截止闹钟，`HeyboxApp` 每次进程启动幂等补一次（覆盖安装、清后台后 JobScheduler 可能丢）。
- 记录页回顾 `ui/RecordStats.kt`：连续签到（`signStreak`，今天没记录不算断）、本月完成天数、分享成功率（`finish / 尝试`）；月历头显示 H 币月增量（月初月末余额差，只有一天余额时显示「—」）。
- 工程：lint 清零（API 级别的系统栏属性交给 `enableEdgeToEdge`，`Modifier.then` 两处收成变量）；CI 增加 `lintDebug`、未签名 `assembleRelease`，发版 job 先跑单测；core / lifecycle / activity 升到当前稳定版。Compose BOM 暂留 2025.12（2026.11 起要求 compileSdk 37.1）。
- R8 仍未打开：WebView JS 桥必须在真机回归登录，模拟器过不了短信验证码，留到有真机时单独做。

## 本轮（v1.6.0）落地

方向：把「没签上」从只有一条失败通知，变成能解释、能行动、能一眼看见的闭环；再把已有 30 天历史变成可读的日历与走势。

- 失败分类 `data/Failure.kt`：异常翻译成网络 / 服务 / 登录 / 验证 / 限流 / 核对六类，每类带一句下一步提示；分类 key 落进 `RunRecord.errorKind`，记录页不必再解析文案。人机验证不消耗自动重试预算。
- 连续漏签 `data/Streak.kt`：从昨天往前数，失败和「整天没记录」都算漏签（后者用于发现闹钟没响这类静默失效）；新装的 App 只数到第一条记录，不误报。≥2 天时通知走 `checkin_warn` 通道（IMPORTANCE_DEFAULT，会响），首页顶部出提示条，登录类失败直接给「重新登录」。
- 更新检查节流 `UpdateCheck.shouldAutoCheck`：GitHub 未认证接口 60 次/小时/IP，原来每次冷启动都查；现在成功等 12 小时、失败等 1 小时，手动检查不受限。
- 记录页 `ui/RecordScreen.kt`：30 行列表换成按月日历热力（周一在首列、颜色即状态、今天带小点），点格子看当天明细；顶部加 30 天 H 币余额走势（用余额而非等级内经验，升级归零会把曲线画成锯齿）。
- 首页节奏：区块间距 18/20 → 14/16，四件卡片最小高 86 → 80dp；提示条用单行，保证出现提示时主按钮仍不被悬浮底栏压住。

## 本轮（v1.4.0）落地

- 登录页验证码框被键盘遮挡：`imePadding` + `BringIntoViewRequester` + IME Done；真机截图确认。
- Release 签名：`keystore/heybox-release.jks`，`versionCode 14 / 1.4.0`，`apksigner` 校验通过。
- 许可证：`THIRD_PARTY_NOTICES.md` + App 内「开源许可」页（读 raw 纯文本）。
- 首启「开始之前」风险 / 数据说明，确认后写 `notice_version`。
- 关于页：两条参考项目链接可点、许可页入口、诊断信息一键复制。
- 崩溃兜底：`HeyboxApp` 捕获未处理异常写 `filesDir/last_crash.txt`，诊断文本会带上最近一条。
- 小组件：`updatePeriodMillis` 30 分钟（跨天自愈）、登录过期态、按钮改「去登录」；文案逻辑抽成纯函数并补 5 个单测。
- Android 13 单色主题图标（`<monochrome>` + 五档 alpha 蒙版）。
- Git 仓库初始化，keystore / 密码 / 构建产物不入库。

## 更新日志格式

`docs/release-notes/<tag>.md` 是 Release 正文的唯一来源，按固定顺序分节，没有内容的小节直接不写：

`### 新增` → `### 优化` → `### 修复` →（必要时）`### 安全` / `### 工程`

- 一条只写一件事，名词或动词开头，说清"改了什么、对他有什么用"
- 不写导语，不写"这一版把……做完整了""顺手修的"这类叙述；原因最多半句，不展开成段落
- **每条之间空一行**：应用内更新弹窗要读这段正文，旧版客户端（≤1.5.4）按空行分段、只取第一段。条目紧挨着写，它会把整段拼成一句
- 底部固定三段，各一行：安装、校验、非官方声明

理由：用户打开 Release 是来查"这版有没有我要的改动"，分节才能三秒扫完；叙述式日志会被直接跳过。应用内更新弹窗读的也是这段正文（`UpdateCheck.parseChanges` 按分节拆条，最多显示两条），所以分节标题只认 `新增 / 优化 / 修复 / 安全` 这几个词，`工程 / 安装 / 校验` 不会进弹窗。

## 验收标准

- `gradle testDebugUnitTest assembleDebug assembleRelease` 全绿
- 真机：首启拦截 → 登录 → 手动执行 → 通知 → 小组件 → 关于 / 许可 / 诊断均可用
- release 包 `apksigner verify` 通过，`aapt2 dump badging` 版本与 minSdk 正确、无 debuggable
- 关闭分享任务时，一次执行只有签到请求；打开的任务都有独立开关与记录

## 待观察风险

1. 官方设备令牌 SDK 升级 → SHA-256 校验主动失败，需要更新常量。
2. APP 版本强制升级 → 签名常量与黄金向量都要换。
3. 签到成功但奖励不到账（上游 issue #6，机房 IP + 固定行为触发的风控），自建只能缓解。
4. 国产 ROM 夜间清理后台 → 依赖电池白名单 / 自启动 / 精确闹钟三项权限。
5. R8 已开启：以后新增反射 / JS 桥 / 新协议库时，先按 `docs/r8-checklist.md` 补 keep 规则并在真机回归登录与小组件。
6. 备用机长期插电，注意电池鼓包。
