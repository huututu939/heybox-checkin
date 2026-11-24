# 盒签 · 小黑盒自动签到（安卓原生）

自己用的小黑盒（heybox）自动签到 App，协议层直调官方接口，不跑 Docker、不需要无障碍点击，装在不插 SIM 卡的备用机上，每天随机时刻自动完成签到；三个分享任务各有开关，默认关闭，按需打开。

参考并移植自 [`Cansiny0320/heybox-daily`](https://github.com/Cansiny0320/heybox-daily)（MIT）。原始签名算法来自 `XiaHouSheng/heybox-core`（MIT）。

## 功能

- 每日签到（`/task/sign_v3/sign`），并轮询 `get_sign_state` 确认到账
- 分享帖子 / 分享游戏 / 分享游戏评价（任务 id `1` / `19` / `31`），每个任务按官方上报顺序 show → tap → success 打点；每次随机挑帖子和游戏，三步之间插入 1–4 秒随机停顿
- 三个分享任务各有一个开关，**默认关闭**，只签到也能正常用；开关状态即时落盘
- 每天的定时任务：`AlarmManager` 精确闹钟，触发时刻每天在可配置窗口（默认 08:00–20:00）内重新随机 + 前台服务执行，跑完自动排下一天
- 结果落通知栏，失败通知带「立即重试」按钮；App 内也能看到上次结果
- 登录只要短信验证码，设备令牌由内置 WebView 跑官方指纹 SDK 取得
- 登录失效时首页顶部出现「登录已过期 / 重新登录」，重登只清凭据，定时和外观保留
- 首页「最近七天」折叠显示每日到账，展开逐日查看

## 安装

调试包（可覆盖安装，保留登录态）：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

发布包（已签名，给其他设备 / 群文件用）：

```powershell
app\build\outputs\apk\release\app-release.apk
```

或者直接把 APK 拷进备用机点安装（需要允许「安装未知来源应用」）。发布包与调试包签名不同，二者不能互相覆盖；从调试包换发布包需要先卸载，卸载会丢掉本机登录态与历史。

## 首次登录（跨设备收码）

备用机不插卡，流程是：

1. 打开 App，输入手机号，点「获取验证码」。首次会先下载并执行官方设备指纹 SDK，可能要十几秒。
2. 主手机收到短信后，把 6 位验证码填回来，点「登录」。
3. 登录成功后会话用 Android Keystore 加密存在本机。

注意两点代价：

- 验证码有时效，几分钟内要填完，超时就重新获取。
- 登录带 `is_new_device=1`，会把手机端小黑盒 App 的登录挤下线一次，重新登录手机端即可。

## 备用机权限（很关键）

国产 ROM 会在夜里把后台任务杀掉，App 首页「值守条件」里有三行可自查，另加一行自启动：

1. **通知权限** — 系统里允许通知
2. **关闭电池优化** — 跳系统「忽略电池优化」授权
3. **允许精确闹钟** — Android 12+ 需要，否则闹钟可能被挪几十分钟
4. **自启动 / 后台运行** — 这一项系统不给读，点「去设置」会弹出各机型的路径（华为：设置 → 应用 → 应用启动管理），按提示手动开一次

> 实测 EMUI 12 用签名级权限锁住了「应用启动管理」页面，第三方 App 无法直接跳转，所以这一项做成了图文指引 + 兜底跳到应用详情页。

建议备用机长期插充电器，并在系统里关掉「低温/高温保护充电」以外的休眠限制。

## 工作原理

- **请求签名**：`HeyboxSign.kt` 移植了官方 APP 的自定义 SHA-512 变体 + 自定义 HMAC（ipad/opad 为 `0x52/0x6c`）+ CRC32 变换，`hkey` / `_rnd` / `nonce` 完全复现；Web 接口另有一套 MD5 + 字符置换签名。
- **登录态**：手机号用 APP 的 RSA 公钥 PKCS1 加密后提交，验证码换 `pkey` 与 `heybox_id`。
- **设备令牌**：`DeviceTokenProvider` 内置 WebView，注入与上游完全一致的 `_smConf`（organization / appId / publicKey / staticHost），执行 `fp.min.js` 后轮询 `SMSdk.getDeviceId()`。SDK 源码带 SHA-256 校验，官网一换版本就主动失败而不是盲跑。
- **分享上报**：8 字节随机 key 当 AES-128 密钥 → gzip → AES-CBC（IV `abcdefghijklmnop`）→ Android 风格 Base64（76 字符换行）→ RSA 包裹 key，`sid = md5(key+time) + md5(data)`。

## 已验证

- 签名：上游 `test/fixtures/native-v14.json` 的 **24 组黄金向量**，`hkey` / `_rnd` / `_time` 逐字段一致（`app/src/test/.../HeyboxSignGoldenTest.kt`）
- 协议：Base64 换行、RSA 密文长度、上报体加解密往返与 sid、native / web 两套 URL 与请求头、Cookie 逐字匹配、手机号归一化、分享事件字段、北京时间日切判断，**共 10 项**（`HeyboxProtocolTest.kt`）
- 调度：随机时刻落在窗口内、跨天不重复（`SchedulerTest.kt`）
- 小组件：今天已签 / 昨天记录跨天失效 / 登录过期 / 今日与明日时间格式，**5 项**（`WidgetLogicTest.kt`）
- 构建：`gradle testDebugUnitTest assembleDebug assembleRelease` 通过；debug 约 9.5 MB，release 约 6.9 MB

```powershell
$env:JAVA_HOME="F:\dev-tools\jdk\jdk-17.0.20.1+1"
$env:GRADLE_USER_HOME="F:\dev-tools\gradle-home"
$env:ANDROID_HOME="F:\dev-tools\android-sdk"
F:\dev-tools\gradle-8.13\bin\gradle.bat testDebugUnitTest
F:\dev-tools\gradle-8.13\bin\gradle.bat assembleDebug
```

## 实测记录（2026-11-24）

- 华为 JER-AN10 / Android 12：短信验证码登录成功（heybox_id 已隐去），手动执行一次得到 **经验 +90 · H 币 +300 · 电池 +1**，签到与三个分享任务均结算
- 定时开关打开后 `dumpsys alarm` 可看到 `RTC_WAKEUP ... exactAllowReason=permission`，触发时间由 00:30 改为随机窗口（示例 2026-11-24 17:22:58）
- Lenovo TB710FU / Android 17：安装启动正常
- 第二轮打磨实测（同一台华为）：执行时段 +1 小时立刻把「下一次」改写并重排（prefs `window_end=21`、`next_trigger` 变化）；「最近七天」从旧记录补位显示 `11/24 未执行（已由其它入口完成）`；`--ez preview_expired true` 下过期横幅正常出现（文案 + 重新登录）；自启动「去设置」在 EMUI 12 上被签名级权限拦下，按设计回落到应用详情页并给出机型指引
- 关于页实测：首页底部「关于」可进入，版本显示 v1.3.0（build 13），参考项目与三句免责声明完整，末尾「By 胡图图」；白天 / 夜间两套色板都正常，「← 返回」回到首页且不退出应用
- 发布前回归（2026-11-24，同一台华为 / Android 12）：首启「开始之前」正常拦截并可进入登录；关于页 v1.4.0、两条参考项目链接可点、开源许可页可滚动；「复制诊断信息」点击后出现 toast 且按钮变「已复制」；登录页点验证码框后输入框停在键盘上沿（截图 `tools/login-keyboard.png`）；2×2 小组件仍在桌面正常渲染「已签到 / 今日已完成 / 定时未开启」。
- 过程中修掉两个真机才暴露的 bug：登录请求误在主线程（`NetworkOnMainThreadException`，message 为 null）；`TaskParser` 的 `Any?.toLongOrNull` 自己调自己导致 `StackOverflowError`

## 逻辑审查（2026-11-24）

重复执行、漏签、并发与失败重试这几个点单独做了处理：

- **同一天不会排两次**：`last_run_day` 记住今天已跑过，跑完（含「今天已完成，跳过」）一律把下一次强制排到明天窗口；实测第二次执行得到「经验 +0 · H 币 +0」，没有重复签到请求。
- **进程内互斥**：闹钟、前台服务、界面「立即执行一次」共用一个 `RunLock`，并发时后来者直接放弃，不会出现两次签到叠加。
- **窗口过期不漏签**：如果今天还没跑、但随机窗口已过完，会把下一次设成 1–3 分钟后补跑，而不是静默推到明天。
- **失败重试有预算**：当天最多重试 2 次（10–25 分钟后），预算用完排到明天，避免夜里失败后每两分钟打一次接口。
- **接收器不长跑**：闹钟触发只负责拉起前台服务；系统拒绝后台启动时给通知并按预算延后，不在 `BroadcastReceiver` 里执行两分钟的任务（否则必然 ANR）。
- **少打一轮接口**：定时任务把首次巡检结果直接传给编排器，不再重复请求任务列表。

仍未消除的边界（不改就是故意留的）：

- 运行时间窗默认 08:00–20:00，不会跨零点，因此不存在「签到确认时已经换天」的问题；如果把窗口改到 23 点以后，跨零点会判定「未确认到账」并报错。
- 设备系统时间不准会让签名与 `signed_date` 同时失效，表现为接口签名错误或永远显示已签到。
- 某个分享任务部分失败后重跑，会对同一个任务重新打一遍 show/tap/success（上游也是这个行为），风控意义上是一天多一次分享上报。
- 人机验证仍未实现，只在命中 `show_captcha` 时明确提示。

## 界面设计（2026-11-24）

原来的界面是 Material 默认色（浅底 + 紫按钮），读起来像一个没做完的调试页。重做后的方向叫「打卡台」：账本式排版，只留一种琥珀色作强调，数字用等宽字体。同一套结构做了**夜间 / 白天两套色板**，可以跟随系统，也可以在首页「外观」里固定。

- **夜间色板**（`DarkPalette`）：`canvas #0D1312` / `surface #151D1B` / `raised #1C2724` / `line #26332F`，正文 `ink #E9EFEC`，次要 `muted #8DA29B`，强调 `accent #F0B44C`。深底带一点绿相而不是纯黑，避免 OLED 上的「洞」感。
- **白天色板**（`LightPalette`）：`canvas #F5F1E8` / `surface #FCF9F2` / `raised #EDE7DA` / `line #D3C9B5`，正文 `ink #1B2320`，次要 `muted #5E6C64`，强调 `accent #A3671A`。纸不是纯白（带一点暖），强调色退到青铜——浅底上做 11sp 小字仍有 5:1 以上的对比度。
- **切换方式**：首页「外观」三个胶囊（跟随系统 / 白天 / 夜间），存 `appearance` 偏好；状态栏图标的深浅会跟着主题一起翻。
- **强调色配额**：只给三处角色——主按钮、开关与「今天已完成」状态、少量对勾与步骤序号。四个任务的状态文字统一用灰色，不再是一列琥珀。
- **字体**：标题用衬线（`displayLarge` 40sp），正文无衬线，所有数值走 `Monospace`（+80 / +466 / +4、`11月25日 11:15`），三种字形互相拉开层级。
- **圆角分三档**：账单卡 28dp、信息卡 18dp、输入框 10dp、按钮与状态胶囊 100dp。
- **首页顺序按「今天怎么样 → 做了什么 → 什么时候再跑 → 是否具备条件 → 现在就跑」**：账单卡（经验/H 币/电池 + 下一次时刻）→ 每日四件 → 每天自动签到开关 → 值守条件（通知、电池优化、精确闹钟）→ 外观 → 主按钮。
- **账单只算增量，余额单独一行**：接口的 `level_info.exp` 是「当前等级内的经验进度」、`coin` 是 H 币余额、`user.battery` 是电池余额，都不是「今天赚了多少」。所以首页大字显示本次（或今天累计）的前后差值，下面一行灰字才是余额（例如 `Lv 3 80/200`）。早期版本直接显示余额，才会出现「第一次 +90、第二次 +80」这种看起来对不上的落差——其实是升级后经验从 80/200 重新起算。
- **登录页**：品牌小方块 + 01/02 两步（手机号 → 验证码），并明确写清「这台机器没有卡，用主手机收码」，避免备用机上第一次用的人卡住。
- **一处动效**：只在执行中让状态点呼吸，其余不动。

设计走查用的调试入口（仅 debug 包生效，不写会话）：

```powershell
adb shell am start -n com.heybox.checkin/.ui.MainActivity --ez preview_login true
adb shell am start -n com.heybox.checkin/.ui.MainActivity --ez preview_expired true
```

**应用图标（2026-11-24 改版）**：深墨绿 `#0C1512` 底 + 青铜色账本轮廓（两条目）+ 奶油白对勾落在页面内部右下角，图形只占画布 66%，所以圆形、圆角方形、方形三种遮罩下都不会被切。对勾特意收进账本边框内，是为了让图形包围盒等于页面本身，居中时左右留白才能严格相等；自适应图标用 `mipmap-anydpi-v26` + 五档密度 PNG（`ic_launcher` / `ic_launcher_round` / `ic_launcher_foreground`），原来的琥珀底「圆圈 + 勾」矢量图标已移除。缩到 48px 仍能看清账本和对勾。Android 13 的「主题图标」另加了 `<monochrome>` 层：把前景图层按轮廓扣成纯白 alpha 蒙版（`ic_launcher_monochrome.png`，五档密度），系统取色后只保留线稿，不再是彩色方块。

## 细节打磨（2026-11-24 第二轮）

- **分享对象随机化**：帖子从当日 feed 前 20 条随机取，游戏从前 10 个推荐随机取，评价在 6 个游戏里随机挑、再从其评论里随机挑一条；show → tap → success 之间分别停顿 1.2–3.7 秒、3.0–7.0 秒。固定目标 + 机械 2 秒间隔是明显的机器特征，这一步是为了把行为摊开。
- **登录失效可见**：`SessionExpiredException` 会被落盘成 `session_expired`，首页顶部出现提示条；点「重新登录」只清凭据（`pkey` / `heybox_id` / 设备令牌），定时、外观、历史全部保留，重登后不用重设。
- **值守条件补第四行**：自启动 / 后台运行。系统不提供读取接口，所以点「去设置」给的是分机型图文指引（华为 / 小米 / OPPO / vivo）+ 兜底跳应用详情。
- **失败可一键重试**：失败通知带「立即重试」action，直接拉起前台服务并带 `force` 标记；`force` 触发的那次失败不再自动重试，避免连点造成循环。
- **最近七天**：每天执行后写一条历史（同日只留最后一次），首页折叠展示最近一条，展开逐日看「经验 +x · H 币 +y」；旧版本只存了一条记录时用它补位，不会空着。
- **小项**：通知栏小图标改为自绘单色圆环对勾（避免小图标变白块）；执行时段改成两个步进器（开始 / 结束，步长 1 小时，强制 `start + 2 ≤ end ≤ 24`），改完立即重排闹钟；未开定时时首页顶部出现「自动签到还没开启 / 开启」提示，点一下就置为开启并排下一次。

## 任务范围开关（2026-11-24）

「每日四件」里签到固定执行，三个分享任务各带一个「开 / 关」胶囊，**默认全关**。理由是这两类动作性质不同：签到是领自己账号的奖励，分享（帖子 / 游戏 / 评价）是往平台灌互动数据，默认不该替使用者决定。开关落到 `task_1` / `task_19` / `task_31` 三个布尔，`TaskRunner` 只跑集合内的 id，最终校验也只校验已开启的任务；关掉的任务在执行记录里记为 `disabled`，不占用重试。

关掉之后，登录后不开任何开关，App 的行为就是「每天随机时间签到一次」，请求量比全开少三分之二。

## 关于页（2026-11-24）

首页底部「退出登录」旁边多了「关于」，点进去是一页三块：版本、参考项目、免责声明，末尾署名 **By 胡图图**。

- **版本**：直接读 `BuildConfig`（v1.4.0 / build 14），并写明「协议层直调官方接口，不跑 Docker、不做无障碍点击、不经过任何第三方托管」。
- **参考项目**：`Cansiny0320/heybox-daily` 与 `XiaHouSheng/heybox-core`（均 MIT License），两条链接都可点开；下方「查看完整开源许可」进入独立页，直接读 `res/raw/third_party_notices.txt` 的全文。
- **免责声明**：压成三句——非官方客户端、与官方无关联或授权；仅供个人学习，禁止代签 / 售卖等商业行为；账号风险与数据异常由使用者自行承担。
- **遇到问题**：一个「复制诊断信息」按钮，把版本 / 机型 / 权限 / 调度 / 上次结果 / 最近崩溃拼成文本写进剪贴板，不含 heybox_id、pkey、token。
- 页面复用首页同一套 `Ink` 色板与圆角档位，深浅主题都适配；返回用页面内的「← 返回」，不动系统返回栈。

## 桌面小组件（2026-11-24）

两个尺寸，都是经典 `RemoteViews`（不引 Glance，不进新依赖），色板沿用首页的纸面 / 墨绿与琥珀，浅深主题各自一套颜色。

- **4×1**：标题 `盒签` + 一行状态（`已签到 · 定时未开启` / `已签到 · 下次 14:27`）+ 右侧「签到」胶囊。
- **2×2**：标题 + 右上状态徽标（`已签到` / `待签到` / `未同步`），中间是今日增量（`+80 经验 · +4 H 币`；今天由其它入口签到时显示 `今日已完成`），下行是下次时间，底部整宽「立即执行」。
- **点击行为**：文字区打开 App；「签到 / 立即执行」直接拉前台服务，走和失败通知「立即重试」同一条路径（带 `force`）。今天已经完成时会走「今天已完成，跳过」，不会重复请求。
- **刷新时机**：`CheckInService.finish()`、开机 / 覆盖安装后的 `BootReceiver`、首页回到前台、首页手动执行成功之后，都会调 `Widgets.refresh()`；另外 `updatePeriodMillis = 1800000`，系统每 30 分钟也会拉一次，用来处理跨天（昨天的「已签到」会自己变回「待签到」）。
- **登录过期**：`read()` 同时看 `heybox_id` 是否存在与 `session_expired`。凭据没了时状态行变「登录已过期 · 点击重新登录」，按钮文字改成「去登录」并只负责打开 App，不会再去拉起签到服务空跑。
- 调试时钉到桌面（仅 debug 包，先 `force-stop`，否则 singleTop 只会把已有界面提到前台）：

```powershell
adb shell am force-stop com.heybox.checkin
adb shell am start -n com.heybox.checkin/.ui.MainActivity --es pin_widget 4x1
adb shell am start -n com.heybox.checkin/.ui.MainActivity --es pin_widget 2x2
```

- 组件只读本地 prefs（`run_record` / `scheduled` / `next_trigger` / `session_expired`），不发网络请求；30 分钟的系统刷新只做文案重算，真正数据仍以最后一次打开 App 或最后一次执行为准。

## 首启说明（v1.4.0）

第一次打开（或提示文案升级后第一次打开）先进入「开始之前」，三块说清：它替你做什么、数据留在哪、你要承担什么；底部还提醒去开自启动 / 电池白名单 / 精确闹钟。点「我已了解，开始使用」后把 `notice_version = 1` 写进 prefs，之后不再拦。

这不是可勾选的用户协议，只是风险告知。

## 发布（v1.4.0）

- **版本**：`versionCode 14` / `versionName 1.4.0`。
- **签名**：`keystore/heybox-release.jks`（alias `heybox`），凭据在 `keystore.properties`；两者都不进 Git。**keystore 丢了就无法给已装用户做覆盖升级，必须单独备份。**
- **构建**：

```powershell
$env:JAVA_HOME="F:\dev-tools\jdk\jdk-17.0.20.1+1"
$env:GRADLE_USER_HOME="F:\dev-tools\gradle-home"
$env:ANDROID_HOME="F:\dev-tools\android-sdk"
F:\dev-tools\gradle-8.13\bin\gradle.bat testDebugUnitTest assembleRelease
```

- **R8**：release 暂不开混淆 / 资源压缩。设备指纹走 WebView JS 桥，混淆一旦漏 keep 只在登录时才炸，而登录需要真实短信、没法自动化回归；等有稳定测试机再打开。
- **许可证**：`THIRD_PARTY_NOTICES.md` 是仓库版本，`app/src/main/res/raw/third_party_notices.txt` 是 App 内「开源许可」页读的纯文本版，两份内容要同步改。
- **分发**：发群文件 / 侧载时保留关于页与许可页，不修改包名、不二次签名；是否对外分发由使用者自行评估平台协议与当地法律。

## 已知限制与风险

| 风险 | 表现 | 应对 |
| --- | --- | --- |
| 官方 SDK 升级 | 登录时提示「官网设备令牌组件已更新」 | 更新 `SDK_SHA256` 与 `sdkUrl` 版本号后重编 |
| APP 版本强制升级 | 接口返回 `failed` / 签名无效 | 改 `HeyboxSign.APP_VERSION/BUILD/SHADER_VERSION`，重跑黄金向量（fixture 也要换） |
| 人机验证 | 返回 `status=show_captcha` | 目前只给出明确提示，不做验证码页；上游 5 条 issue 里没人卡在这一步 |
| 签到无奖励 | 签到成功但经验 / H 币不到账 | 上游 issue #6 判定为风控（机房 IP + 固定行为）；自建 + 真实网络只能缓解，不能保证 |
| 国产 ROM 杀后台 | 某天没跑 | 电池白名单 + 自启动 + 精确闹钟，必要时加「应用锁」防清理 |
| 登录态失效 | 接口 `relogin` / `lack_token` | 首页顶部「登录已过期」+ 通知，点「重新登录」重发验证码，定时与外观保留 |
| 合规 | 属非官方工具，对外分发没有授权依据 | 不做代签 / 售卖；对外分发前自行评估平台协议与当地法律风险 |

## 目录

```
app/src/main/java/com/heybox/checkin/
├── core/HeyboxSign.kt        签名算法（native + web）
├── core/HeyboxProtocol.kt    请求构造、加密、接口封装、登录 API
├── core/TaskModels.kt        响应解析、黄金规则校验、分享事件拼装
├── core/TaskRunner.kt        每日任务编排（签到 → 三个分享 → 最终校验）
├── data/SessionStore.kt      Keystore AES-GCM 会话存储
├── data/Diagnostics.kt       诊断文本与全局崩溃落盘
├── data/LoginFlow.kt         短信验证码登录流程
├── device/DeviceTokenProvider.kt  WebView 跑官方指纹 SDK
├── work/Scheduler.kt         每日随机时刻精确闹钟（08:00–20:00 窗口）
├── work/CheckInService.kt    前台服务执行签到
├── work/CheckInReceiver.kt   闹钟触发（起服务，失败兜底）
├── work/BootReceiver.kt      重启后重排闹钟
├── work/Notifications.kt     通知渠道与进度
├── HeyboxApp.kt              Application，安装崩溃兜底
├── ui/MainActivity.kt        界面根（会话态 + 预览开关 + 首启门）
├── ui/WelcomeScreen.kt       首启风险与数据说明
├── ui/LoginScreen.kt         登录页（两步式，键盘避让）
├── ui/HomeScreen.kt          首页（账单 / 每日四件 / 开关 / 值守条件）
├── ui/AboutScreen.kt         关于页（版本 / 参考项目 / 许可 / 免责 / 诊断 / 署名）
├── widget/Widgets.kt         桌面小组件（4×1 / 2×2 两个 Provider，RemoteViews 渲染）
├── widget/WidgetLogic.kt     小组件文案纯函数（跨天 / 过期，可单测）
├── ui/Format.kt              时间与错误文案格式化
├── ui/theme/Theme.kt         色板、字形与圆角 token
└── data/RunRecord.kt         最近一次执行的账单快照
```
