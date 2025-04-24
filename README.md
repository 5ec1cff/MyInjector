# MyInjector

自用 Xposed 模块

# 功能介绍

下面列出各个 app 的包名及其可用功能  
所有功能在选择了相应作用域后自动启用

## 可用功能

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
```

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

### SystemUI（小米）

```
com.android.systemui
```

- 长按通知时在屏幕顶端显示该通知的详细信息，如通知渠道、id、pid、时间等，点按可以进入对应通知渠道的设置页面

### 小米桌面

```
com.miui.home
```

- 最近任务卡片划动时悬停 700ms
  后再划动即可杀死进程（「清理任务」字样替换为「停止进程」，不会阻止系统的划卡杀，需要配合其他划卡杀白名单模块使用，如 [NoSwipeToKill](https://github.com/dantmnf/NoSwipeToKill)）

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

## 过期功能

长期未维护，可能不可用

### Twitter

```
com.twitter.android
```

- 修复 bitwarden 在 twitter 的搜索页面中错误地显示自动填充的问题
