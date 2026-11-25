# 盒签 · 开发计划与状态

目标：备用机（无 SIM 卡）安装一款安卓原生 App，每天在 08:00–20:00 之间随机挑一个时刻自动完成小黑盒签到；分享任务按开关执行，默认关闭。路线为 **A：协议层直调官方接口**。

当前版本：**v1.4.2（versionCode 16）**，签名 release 由 CI 出包并发布。

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
| 混淆 | release 暂不启 R8（WebView JS 桥无法在不丢登录态的前提下回归） |

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
5. 若日后打开 R8，必须先在真机回归登录（WebView JS 桥）与小组件（RemoteViews 反射）。
6. 备用机长期插电，注意电池鼓包。
