# 盒签 · 小黑盒每日自动签到

Android 原生 · 协议层直调官方接口 · 不跑 Docker · 不用无障碍点击

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.12-4285F4)
![Version](https://img.shields.io/badge/version-1.4.0-blue)
![Release](https://img.shields.io/github/v/release/huututu939/heybox-checkin?label=release)
![CI](https://github.com/huututu939/heybox-checkin/actions/workflows/android.yml/badge.svg)
![License](https://img.shields.io/badge/license-MIT-yellow)

装在不插 SIM 卡的备用机上，每天在可配置的时间窗口内随机挑一个时刻，自动完成小黑盒签到。三个分享任务各有开关，**默认关闭**；会话用 Android Keystore 加密，只存在本机。

参考并移植自 [`Cansiny0320/heybox-daily`](https://github.com/Cansiny0320/heybox-daily)（MIT）与 [`XiaHouSheng/heybox-core`](https://github.com/XiaHouSheng/heybox-core)（MIT）。

> **非官方工具**：与小黑盒官方无关联或授权，仅供个人学习自用。请勿用于代签、售卖等商业行为；账号风险与平台协议请自行评估。

## 目录

- [功能](#功能)
- [快速开始](#快速开始)
- [首次登录](#首次登录)
- [备用机权限](#备用机权限)
- [工作原理](#工作原理)
- [调度与防重复](#调度与防重复)
- [界面设计](#界面设计)
- [桌面小组件](#桌面小组件)
- [项目结构](#项目结构)
- [验证](#验证)
- [已知限制与风险](#已知限制与风险)
- [构建与发布](#构建与发布)
- [许可](#许可)

## 功能

- **每日签到**：调用 `/task/sign_v3/sign`，再轮询 `get_sign_state` 确认到账。
- **可选分享**：分享帖子 / 游戏 / 评价（任务 id `1` / `19` / `31`），按官方顺序 show → tap → success 打点；素材随机、步骤间随机停顿。三个开关默认全关。
- **随机定时**：`AlarmManager` 精确闹钟，每天在可配置窗口（默认 08:00–20:00）内重新随机；前台服务执行，跑完自动排下一天。
- **结果可见**：通知栏给结果，失败通知带「立即重试」；首页显示今日增量、账户余额与「最近七天」。
- **登录简单**：只要短信验证码；设备令牌由内置 WebView 跑官方指纹 SDK 取得。
- **失效可恢复**：登录过期时首页出现「登录已过期 / 重新登录」，重登只清凭据，定时、外观、历史保留。
- **两套外观**：夜间 / 白天双色板，可跟随系统或手动固定；4×1、2×2 两种桌面小组件。
- **可交付**：首启风险说明、关于页、开源许可页、一键复制诊断信息。

## 快速开始

### 环境

| 项 | 要求 |
| --- | --- |
| 系统 | Android 8.0+（`minSdk 26`） |
| 编译 | JDK 17、Gradle 8.13+、Android SDK 35 |
| 设备 | 允许安装未知来源应用；备用机建议长期插电 |

### 构建

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Windows 用 `gradlew.bat`，或在 `local.properties` 里写 `sdk.dir=...`。

> 首次运行 `./gradlew` 会下载 Gradle 8.13。网络受限时可直接用本机 Gradle 8.13+ 执行同名任务（`gradle testDebugUnitTest` / `gradle assembleDebug`）。

### 安装

普通用户直接下载 [最新 Release](https://github.com/huututu939/heybox-checkin/releases/latest) 里的签名 `app-release.apk`，允许「安装未知来源应用」后侧载即可。

从源码构建的 debug 包：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

自己构建 release 需要自备签名文件（见[构建与发布](#构建与发布)）。debug 与 release 签名不同，不能互相覆盖；从 debug 换 release 需要先卸载，会丢掉本机登录态与历史。

## 首次登录

备用机不插卡，流程是：

1. 打开 App，输入手机号，点「获取验证码」。首次会下载并执行官方设备指纹 SDK，可能要十几秒。
2. 主手机收到短信后，把 6 位验证码填回来，点「登录」。
3. 登录成功后，`pkey`、`heybox_id` 与设备标识用 Android Keystore AES-GCM 加密，只存在本机。

两点代价：

- 验证码几分钟内有效；超时重新获取。
- 登录带 `is_new_device=1`，会把手机端小黑盒 App 挤下线一次，重新登录手机端即可。

## 备用机权限

国产 ROM 会在夜里清理后台，首页「值守条件」可以逐项自查：

| 条件 | 作用 | 入口 |
| --- | --- | --- |
| 通知权限 | 显示执行进度与结果 | 系统通知设置 |
| 忽略电池优化 | 降低夜间被杀概率 | 首页「去设置」 |
| 精确闹钟 | Android 12+ 定时精度 | 首页「去设置」 |
| 自启动 / 后台运行 | 防止 ROM 清理 | 分机型图文指引 |

> EMUI 12 用签名级权限锁住了「应用启动管理」页面，第三方 App 无法直接跳转，因此这一项做成了图文指引 + 兜底跳应用详情页。

建议长期插电，并在系统里关掉不必要的休眠 / 省电限制。

## 工作原理

| 模块 | 文件 | 说明 |
| --- | --- | --- |
| 请求签名 | `core/HeyboxSign.kt` | 自定义 SHA-512 变体 + HMAC（ipad/opad 为 `0x52/0x6c`）+ CRC32；Web 接口另有一套 MD5 + 字符置换 |
| 协议层 | `core/HeyboxProtocol.kt` | 请求构造、RSA / gzip + AES 上报、接口封装、登录 API |
| 任务编排 | `core/TaskRunner.kt` | 巡检 → 签到 → 三个分享 → 最终校验 |
| 会话存储 | `data/SessionStore.kt` | Android Keystore AES-GCM，Keystore 不在备份范围 |
| 设备令牌 | `device/DeviceTokenProvider.kt` | WebView 注入 `_smConf`，执行 `fp.min.js` 后轮询 `SMSdk.getDeviceId()` |
| 调度 | `work/Scheduler.kt` | 每日随机时刻精确闹钟 |
| 执行 | `work/CheckInService.kt` | dataSync 前台服务承载长流程 |

关键实现：

- **签名**：`hkey` / `_rnd` / `nonce` 复现官方 APP；`native-v14.json` 的 24 组黄金向量逐字段校验。
- **登录**：手机号用 APP 的 RSA 公钥 PKCS1 加密后提交，验证码换 `pkey` 与 `heybox_id`。
- **设备令牌**：SDK 源码带 SHA-256 校验，官网一换版本就主动失败，不盲跑未知脚本。
- **分享上报**：8 字节随机 key 当 AES-128 密钥 → gzip → AES-CBC（IV `abcdefghijklmnop`）→ Android 风格 Base64（76 字符换行）→ RSA 包裹 key；`sid = md5(key+time) + md5(data)`。

## 调度与防重复

- **同一天不会排两次**：`last_run_day` 记住今天已跑过，跑完（含「今天已完成，跳过」）一律把下一次排到明天窗口。
- **进程内互斥**：闹钟、前台服务、界面「立即执行一次」共用一个 `RunLock`；界面只负责拉起服务，不自己跑长流程。并发时后来者静默退出，不会重复签到，也不会误发「签到失败」。
- **冷启动只补闹钟、不重抽**：`force-stop`、系统清理或部分 ROM 会取消闹钟，但 `next_trigger` 仍留在未来。首页恢复时用同一个时刻重新落一次闹钟，不改变今天的随机时间；只有时间已过或从未排过才重新抽签。
- **窗口过期不漏签**：今天还没跑、但随机窗口已过完时，下一次设成 1–3 分钟后补跑。
- **失败重试有预算**：当天最多重试 2 次（10–25 分钟后），预算用完排到明天。
- **接收器不长跑**：闹钟只负责拉起前台服务；系统拒绝后台启动时给通知并按预算延后，不在 `BroadcastReceiver` 里执行两分钟任务。
- **少打一轮接口**：定时任务把首次巡检结果直接交给编排器。
- **分享校验按开关**：解析层只校验已开启的分享任务；关掉的任务缺失或状态异常，不会把纯签到一起卡死。

仍未消除的边界（故意保留）：

- 默认窗口 08:00–20:00，不跨零点；如果把窗口改到 23 点以后，跨零点会判定「未确认到账」。
- 设备系统时间不准会让签名与 `signed_date` 同时失效。
- 某个分享任务部分失败后重跑，会对同一任务重新打一遍 show/tap/success（上游也是这个行为）。
- 人机验证未实现，只在命中 `show_captcha` 时明确提示。

## 界面设计

方向叫「打卡台」：账本式排版，只留一种琥珀色作强调，数字用等宽字体。同一套结构有**夜间 / 白天两套色板**，可跟随系统，也可在首页「外观」里固定。

| 角色 | 夜间 | 白天 |
| --- | --- | --- |
| canvas | `#0D1312` | `#F5F1E8` |
| surface | `#151D1B` | `#FCF9F2` |
| raised | `#1C2724` | `#EDE7DA` |
| line | `#26332F` | `#D3C9B5` |
| ink | `#E9EFEC` | `#1B2320` |
| muted | `#8DA29B` | `#5E6C64` |
| accent | `#F0B44C` | `#A3671A` |

- **字体**：标题衬线（40sp），正文无衬线，数值统一 `Monospace`，三种字形拉开层级。
- **圆角**：账单卡 28dp、信息卡 18dp、输入框 10dp、按钮与状态胶囊 100dp。
- **首页顺序**：账单卡（经验 / H 币 / 电池 + 下一次）→ 每日四件 → 自动签到开关 → 值守条件 → 外观 → 最近七天 → 主按钮。
- **增量与余额分开**：接口返回的 `level_info.exp` 是当前等级内进度、`coin` 是 H 币余额、`battery` 是电池余额，都不是「今天赚了多少」。首页大字显示本次或今天累计的差值，下面灰字才是余额（如 `Lv 3 80/200`）；无法可靠计算时显示 `—`。
- **状态文案**：只要已开启的分享任务都完成（全关时签到完成即可），才显示「今日已完成」。
- **登录页**：品牌小方块 + 01/02 两步（手机号 → 验证码），明确写清「这台机器没有卡，用主手机收码」。
- **一处动效**：只在执行中让状态点呼吸，其余不动。

设计走查用的调试入口（仅 debug 包生效，不写会话）：

```bash
adb shell am start -n com.heybox.checkin/.ui.MainActivity --ez preview_login true
adb shell am start -n com.heybox.checkin/.ui.MainActivity --ez preview_expired true
adb shell am start -n com.heybox.checkin/.ui.MainActivity --ez preview_welcome true
```

## 桌面小组件

两个尺寸，都是经典 `RemoteViews`（不引 Glance），色板沿用首页，浅深主题各一套。

| 尺寸 | 内容 |
| --- | --- |
| 4×1 | 标题 `盒签` + 一行状态（`已签到 · 定时未开启` / `已签到 · 下次 14:27`）+ 右侧「签到」胶囊 |
| 2×2 | 标题 + 状态徽标 + 今日增量 + 下次时间 + 底部「立即执行」 |

- **点击行为**：文字区打开 App；「签到 / 立即执行」直接拉前台服务，带 `force`，和失败通知「立即重试」同一条路径。今天已完成时走「今天已完成，跳过」。
- **刷新时机**：服务结束、开机 / 覆盖安装、首页回到前台、手动执行成功后都会刷新；`updatePeriodMillis = 1800000`，系统每 30 分钟再拉一次处理跨天。
- **登录过期**：凭据缺失或 `session_expired` 时，状态行变「登录已过期 · 点击重新登录」，按钮改「去登录」，不会空跑签到服务。
- **网络**：只读本地 prefs（`run_record` / `scheduled` / `next_trigger` / `session_expired`），不发请求。

调试时钉到桌面（仅 debug 包，先 `force-stop`）：

```bash
adb shell am force-stop com.heybox.checkin
adb shell am start -n com.heybox.checkin/.ui.MainActivity --es pin_widget 4x1
adb shell am start -n com.heybox.checkin/.ui.MainActivity --es pin_widget 2x2
```

## 项目结构

```text
app/src/main/java/com/heybox/checkin/
├── core/HeyboxSign.kt            签名算法（native + web）
├── core/HeyboxProtocol.kt        请求构造、加密、接口封装、登录 API
├── core/TaskModels.kt            响应解析、规则校验、分享事件拼装
├── core/TaskRunner.kt            每日任务编排
├── data/SessionStore.kt          Keystore AES-GCM 会话与本地偏好
├── data/LoginFlow.kt             短信验证码登录流程
├── data/Diagnostics.kt           诊断文本与全局崩溃落盘
├── data/RunRecord.kt             最近一次执行的账单快照
├── device/DeviceTokenProvider.kt WebView 跑官方指纹 SDK
├── work/Scheduler.kt             随机时刻精确闹钟
├── work/CheckInService.kt        前台服务执行签到
├── work/CheckInReceiver.kt       闹钟触发（起服务，失败兜底）
├── work/BootReceiver.kt          重启 / 覆盖安装后重落闹钟
├── work/RunLock.kt               进程内执行互斥
├── work/RunState.kt              执行状态（界面只观察）
├── work/Notifications.kt         通知渠道与进度
├── ui/MainActivity.kt            界面根（会话态 + 预览开关 + 首启门）
├── ui/WelcomeScreen.kt           首启风险与数据说明
├── ui/LoginScreen.kt             登录页（两步式，键盘避让）
├── ui/HomeScreen.kt              首页（账单 / 每日四件 / 开关 / 值守条件）
├── ui/AboutScreen.kt             关于页（版本 / 参考项目 / 许可 / 免责 / 诊断）
├── ui/Format.kt                  时间与错误文案格式化
├── ui/theme/Theme.kt             色板、字形与圆角 token
├── widget/Widgets.kt             桌面小组件（4×1 / 2×2）
├── widget/WidgetLogic.kt         小组件文案纯函数（可单测）
└── HeyboxApp.kt                  Application，崩溃兜底
```

## 验证

- **单元测试**：`./gradlew testDebugUnitTest`，6 个测试类、28 个用例，27 个执行、1 个 `@Ignore`，0 失败。
- **签名**：`native-v14.json` 的 24 组黄金向量，`hkey` / `_rnd` / `_time` 逐字段一致。
- **协议**：Base64 换行、RSA 密文长度、上报体加解密往返与 `sid`、native / web URL 与请求头、Cookie、手机号归一化、分享事件、北京时间日切，共 10 个断言点。
- **解析**：关闭的分享任务缺失 / 状态异常不阻塞签到；启用的分享任务缺失则明确失败。
- **调度**：随机时刻落在窗口内、跨天不重复、窗口过期补跑。
- **小组件**：跨天失效、登录过期、今日 / 明日时间、分享未完成、已开启但未排定，共 7 项。
- **真机**：华为 JER-AN10 / Android 12 完成短信登录与签到，实测经验 +90、H 币 +300、电池 +1；`dumpsys alarm` 可见精确闹钟；`force-stop` 后重开能按原时刻重落 `RUN_DAILY`。Lenovo TB710FU / Android 17 安装启动正常。
- **构建**：debug 约 8.9 MB；当前无签名配置时 release 约 6.6 MB（`app-release-unsigned.apk`）。
- **持续集成**：GitHub Actions（`.github/workflows/android.yml`）在每次推送与 PR 上跑 `testDebugUnitTest` + `assembleDebug`，并归档 debug APK 与测试报告。

## 已知限制与风险

| 风险 | 表现 | 应对 |
| --- | --- | --- |
| 官方 SDK 升级 | 登录提示「官网设备令牌组件已更新」 | 更新 `SDK_SHA256` 与 `sdkUrl` 版本号后重编 |
| APP 版本强制升级 | 接口返回 `failed` / 签名无效 | 修改 `HeyboxSign.APP_VERSION/BUILD/SHADER_VERSION`，重跑黄金向量 |
| 人机验证 | 返回 `status=show_captcha` | 目前只给明确提示，不做验证码页 |
| 签到无奖励 | 签到成功但经验 / H 币不到账 | 上游 issue #6 判定为风控；真实网络只能缓解，不能保证 |
| 国产 ROM 杀后台 | 某天没跑 | 电池白名单 + 自启动 + 精确闹钟，必要时加应用锁 |
| 登录态失效 | 接口 `relogin` / `lack_token` | 首页「登录已过期」+ 通知，重新登录后恢复 |
| 合规 | 非官方工具，对外分发没有授权依据 | 不做代签 / 售卖；分发前自行评估平台协议与当地法律 |

## 构建与发布

- **版本**：`versionCode 14` / `versionName 1.4.0`，在 `app/build.gradle.kts` 修改。
- **签名**：release 读取仓库根目录的 `keystore.properties` 与 `keystore/`，两者都不进 Git。**keystore 丢了就无法给已装用户覆盖升级，必须单独备份。**
- **构建**：

```bash
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

- **R8**：release 暂不开混淆 / 资源压缩。设备指纹走 WebView JS 桥，混淆一旦漏 keep 只在登录时暴露，而登录需要真实短信，没法自动化回归。
- **许可证文本**：`THIRD_PARTY_NOTICES.md` 是仓库版，`app/src/main/res/raw/third_party_notices.txt` 是 App 内「开源许可」页读取的纯文本版，两份要同步改。
- **分发**：侧载或发群文件时保留关于页与许可页，不修改包名、不二次签名；是否对外分发由使用者自行评估。

## 许可

本项目自身代码以 **MIT License** 发布，完整文本见 [LICENSE](LICENSE)。

签到 / 分享流程、请求签名与设备指纹实现参考并移植自：

- [`Cansiny0320/heybox-daily`](https://github.com/Cansiny0320/heybox-daily)（MIT）
- [`XiaHouSheng/heybox-core`](https://github.com/XiaHouSheng/heybox-core)（MIT）

第三方版权与许可全文见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，App 内「关于 → 开源许可」也会展示同一份文本。

> 本项目为非官方客户端，与小黑盒官方无任何关联或授权。仅供个人学习，请勿用于代签、售卖等商业行为；因使用本工具产生的账号风险或数据异常，由使用者自行承担。
