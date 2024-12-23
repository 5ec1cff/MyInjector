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

- 移除页面跳转的广告
- 阻止输入时显示导入联系人

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

### Telegram

```
org.telegram.messenger
org.telegram.messenger.web
org.telegram.messenger.beta
```

- 联系人页面展示是否为双向联系人
- 禁用发送框旁语音按钮 / 相机按钮的功能
- 点按包含非 URL 标准字符的链接时显示提示框，可以使用过滤后的结果
- 私聊删除确认框默认勾选为对方删除
- 添加联系人默认不勾选分享手机号
- 在输入框中输入 @ 后出现的选择 at 对象的列表中，长按元素即可对具有用户名的用户使用无用户名的 at 方式
- 进入联系人页面不再显示联系人权限弹窗

### 权限管理服务（小米）

```
com.lbe.security.miui
```

- 阻止自动移除长期未展示可见窗口的 App 的自启动权限

### 手机管家（小米）

```
com.miui.securitycenter
```

- 在 App 管理界面显示一个「保持自启动」按钮，启用可防止 App 被权限管理服务自动移除自启动权限

### SystemUI

```
com.android.systemui
```

- 长按通知时在屏幕顶端显示该通知的详细信息，如通知渠道、id、pid、时间等，点按可以进入对应通知渠道的设置页面

## 过期功能

长期未维护，可能不可用

### Twitter

```
com.twitter.android
```

- 修复 bitwarden 在 twitter 的搜索页面中错误地显示自动填充的问题

### FV 悬浮球

```
com.fooview.android.fooview
```

- 帮助绕过 Hidden API 限制，可能解决无法使用小窗启动的问题

### QQ

```
com.tencent.mobileqq
```

- 阻止启动 logcat  
