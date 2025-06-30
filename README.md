# MyInjector

自用 Xposed 模块

# 功能介绍

下面列出各个 app 的包名及其可用功能  
所有功能在选择了相应作用域后自动启用

## 可用功能

### 系统服务 / 系统框架

```
android
```

可以在模块列表找到本模块进入设置以配置系统服务的相关 hook ，部分功能的配置即时生效。

支持热更新（免重启更新），模块在系统加载后，安装了新模块后可以在设置页面手动更新，新功能可即时生效，无需重启系统。

- 强制 NEW_TASK 规则：给某些 Activity 自动加上 NEW_TASK 或者 NEW_DOCUMENT
  flag，支持通过启动来源的包名/组件名和被启动的包名/组件名编写规则来匹配
  - 一行一个规则，形如 `<source>:<target>[:options]` ，source/target可以是包名或组件名(用/分隔)
    ，可以用*表示除了自己之外的所有包。options用逗号分割，支持的选项包含ir(
    ignoreResult，允许startActivityForResult也使用NEW_TASK，这会导致返回值失效)和 `nd` (
    newDocument，默认使用新窗口打开，效果取决于目标 activity 的类型)
  - 例 1： `com.tencent.mm/com.tencent.mm.plugin.appbrand.ui.AppBrandUI*:com.android.contacts:ir`
    表示微信小程序打开电话时设置 new task ，忽略结果
  - 例 2： `tv.danmaku.bili/tv.danmaku.bili.ui.intent.IntentHandlerActivity:tv.danmaku.bili:nd`
    哔哩哔哩内部
    intent 分发器打开自身都使用 new task + new document
- 剪切板白名单（允许某些 app 后台监听剪切板）
- 允许关闭 miui wakepath 功能（即跨 app 打开启动可能弹出的询问）
- 允许移除某些 miui 锁定的 intent
- 其他功能详见设置

### 百度输入法

```
com.baidu.input
```

- 移除首屏广告，并且在路由到真实页面后后自动结束首屏（防止打开其他页面之后返回后回到首页）
- 阻止输入时显示导入联系人按钮

### Kiwi Broswer / Chrome

```
com.kiwibrowser.browser
com.android.chrome
```

- 禁用网页的下拉刷新
- 移除窗口遮挡检测（以便悬浮窗存在时可以**拒绝**或授予网页权限）

### Termux

```
com.termux
```

- 修复在 MIUI 上使用小窗时界面抽搐的问题
- 阻止终端界面中密码自动填充自动显示
- 退出终端 Activity 后移除最近任务

### Telegram

```
org.telegram.messenger
org.telegram.messenger.web
org.telegram.messenger.beta
org.telegram.plus
com.exteragram.messenger
com.radolyn.ayugram
uz.unnarsx.cherrygram
xyz.nextalone.nagram
nu.gpu.nagram
com.xtaolabs.pagergram
```

注：作者仅适配官方 `org.telegram.messenger` ，因此其他的第三方端，或 web, beta 等都不保证可用

左侧抽屉（侧拉栏）中包含模块的设置入口（大部分功能即时生效，无需重启 app）

- 用户列表中的双向联系人的右侧会显示一个↑↓图标
- 禁用发送框旁语音按钮 / 相机按钮的功能
- 点按包含非 URL 标准字符的链接时显示提示框，可以使用过滤后的结果
- 私聊删除确认框默认勾选为对方删除
- 添加联系人默认不勾选分享手机号
- 在输入框中输入 @ 后出现的选择 at 对象的列表中，长按元素即可对具有用户名的用户使用无用户名的 at 方式
- 进入联系人页面不再显示联系人权限弹窗
- 假装拥有请求安装权限（适用于你有别的安装器但是不想给请求安装权限的场景）
- 无需安装 Google 地图即可使用定位功能（为啥非要安？）
- 修复打开外部链接时可能会触发两次打开（后一次使用 chrome custom tab）的问题
- 支持导入表情名字到 tg emoji 的映射（长按输入框的 emoji 按钮以导入），当输入框粘贴文本（必须通过长按菜单的粘贴）的时候替换为 tg 表情（可用于映射 b 站等平台的表情文本）
- 查看贴纸包 / emoji 包作者（如果能访问则打开 profile ，否则复制用户 id）
- 点击 hashtag 的时候总是默认使用「此聊天」而不是「公开帖子」
- 发送位置和设置位置（企业版功能）时支持自定义经纬度（长按地图右下角的定位按钮）
- 用户资料界面自动滚动到当前头像（而不是第一个头像）
- 默认发送高清晰度图像（需要 11.12.0 (5997) 或更高版本），并移除图片预览左下角的高清标志
- 隐藏主页抽屉的电话号码，点按文本切换显示状态；隐藏资料页面的电话号码，点按右侧按钮切换显示状态
- 移除下拉归档（对 exteraGram 特殊适配了不移除侧栏的归档元素）
- 总是显示下载管理器
- 移除主页浮动按钮

### 知乎

```
com.zhihu.android
```

仅在 10.2.0 测试过可用

- 阻止首页自动刷新
- 阻止复制链接被替换为带跟踪信息的链接

### 权限管理服务（小米）

```
com.lbe.security.miui
```

- 阻止自动移除长期未展示可见窗口的 App 的自启动权限

### 手机管家（小米）

```
com.miui.securitycenter
```

- 在 App 管理界面显示一个「保持自启动」按钮，启用可防止该 App 被权限管理服务自动移除自启动权限
- 阻止各种危险设置的倒计时确认

### SystemUI

```
com.android.systemui
```

可以在模块列表找到本模块进入设置以配置 System UI 的相关 hook ，部分功能的配置即时生效。

- （仅限小米）长按通知时在屏幕顶端显示该通知的详细信息，如通知渠道、id、pid、时间等，点按可以进入对应通知渠道的设置页面
- （仅限小米）阻止小米手机上通过磁贴外的方式开关勿扰模式开关后显示 Toast 或者灵动额头
- 总是展开通知

### 小米桌面

```
com.miui.home
```

- 最近任务卡片划动时悬停 700ms
  后再划动即可杀死进程（「清理任务」字样替换为「停止进程」，不会阻止系统的划卡杀，需要配合其他划卡杀白名单模块使用，如 [NoSwipeToKill](https://github.com/dantmnf/NoSwipeToKill)）
- 禁用 app 预启动

### FV 悬浮球

```
com.fooview.android.fooview
```

- 帮助绕过 Hidden API 限制，可能解决无法使用小窗启动的问题
- (1.6.0/160) 阻止无障碍服务发现「正在其他应用上层显示」通知时提示授予通知栏权限

## 设置（小米）

```
com.android.settings
```

- 阻止通知管理的按钮变灰（这允许禁用一些本来不可禁用的通知/渠道）

## LanDrop (2.8.1)

```
app.landrop.landrop_flutter
```

- 修复分享文本文件时会以文本形式发送路径而非发送原文件的 bug

## IntentResolver

```
com.android.intentresolver
```

- 去除打开的 activity 的 EXCLUDE_FROM_RECENTS flag 。

## Spotify

```
com.spotify.music
```

- 去除加号（群友请求的功能）

## DocumentsUI

```
com.google.android.documentsui
```

- 如果使用 ACTION_OPEN_DOCUMENT 选择文件，强制显示 Picker Activity （类似 ACTION_GET_CONTENT 的行为）。
- 打开时自动显示抽屉，且根目录列表滚动到 App （我知道你想用其他 Picker Activity ，对吧？）

## Sudoku

```
com.easybrain.sudoku.android
```

- 移除广告（仍有概率看到广告，待解决）
- 阻止隐藏系统导航栏

## 过期功能

长期未维护，可能不可用

### Twitter

```
com.twitter.android
```

- 修复 bitwarden 在 twitter 的搜索页面中错误地显示自动填充的问题

### MeiZuCustomizerCenter

```
com.meizu.customizecenter
```

- 将本应用在 /sdcard 下创建的目录重定向到 /sdcard/Documents （不建议使用）（群友请求的功能）

# Credits

[哔哩漫游](https://github.com/yujincheng08/BiliRoaming)  
[DexKit](https://github.com/LuckyPray/DexKit)  
