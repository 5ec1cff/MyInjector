# Telegram 未混淆第三方版本适配

本次检查的是 `~/tg-apks` 中的两个 arm64 APK，均为 13.0.3 / versionCode 71835：

| 应用 | 包名 | SHA-256 |
| --- | --- | --- |
| AyuGram | com.radolyn.ayugram | 2e7b0620f5d2258c1559603dcb613736aed97cf03448332b2a812c9c9cd696bd |
| exteraGram | com.exteragram.messenger | 5a4877ecfc054941602c6fa06330415435cac7af3693a563e0a2b246c4dc7dbc |

包名、作用域和模块首页已有支持，本次修改成员解析与调用兼容性。使用 dexsql MCP 检查 Manifest、成员签名、调用关系和关键 smali，再用本地 DexKit JVM 对生产查询执行静态回归。

## 实现

- BaseFragment 优先按完整类名查找；未混淆版有三个类声明 fragmentView，原唯一匹配会中断整个 Handler。
- 未混淆方法优先按类名、方法名和完整参数列表定位，找不到时保留原有混淆特征查询。修正设置工厂未内联、asShadow / setInfo 重载、RecyclerView 桥接方法、合成字段访问器等差异。
- 资料页模糊、打开链接和媒体查看器使用集中定义并逐项验证的未混淆成员映射；适配新 actions 文字颜色参数时复用字段及 shader 路径，保留原文字色。
- 消息 ID 使用 SpannableStringBuilder 拼接，兼容新版具体字段类型并保留原有文本 span。隐藏手机号只挂接解析出的 setInfo 重载，避免包装方法重复执行回调。
- 复读适配七参数 forwardMessages，保留原权限判断；在线时间兼容两个 formatDateOnline 重载。Telegram 适配缓存版本由 2 升至 3，升级后重新解析。

## 复现与验证

`check_telegram_apks.py` 从当前生产代码提取查询函数与复读权限判断，在临时 source set 中使用已有 DexKit JVM 工程执行，不改动该工程的源文件。不依赖目标 APK 启动、登录或启用 Xposed。

```sh
python3 scripts/check_telegram_apks.py --dexkit ~/DexKit \
  ~/tg-apks/AyuGram-arm64-v8a-20261028.apk \
  ~/tg-apks/exteraGram-arm64-v8a-20261028.apk \
  ~/nagram-1249.apk
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease
```

三个 APK 均通过 39 组检查：30 组解析、7 组保留 API 检查，以及反射签名和复读权限场景检查。旧版 NaGram 12.10.3（1249）的混淆路径通过回归。复读检查包含 21 个权限场景及菜单打开后权限变化。debug、release 构建和已有单元测试通过。

验证边界：静态检查确认成员存在、签名及已覆盖的调用关系；没有在真实 Xposed 宿主中验证账号界面、绘制效果、消息发送或热更新。安装模块后需重启目标应用，再检查设置中的错误记录及实际功能。

## Telegram 13.1.1（72371）头像模糊适配

从当前 ADB 设备提取官方 `org.telegram.messenger` 的 base APK 和 arm64、xxhdpi split，保存于 `~/tg-apks/telegram-13.1.1-72371/`。base APK 的 SHA-256 为 `f4b44311c7f7451836084b4bcdd8379857e3f2ca1b64b7e063e8568f6002128f`。

使用与报错 map ID 一致的 308 版 release mapping 还原堆栈，并在提取的 APK 上复现失败。dexsql 检查发现：TopView 更新颜色时，按钮视图和音乐视图都含有 RadialGradient；原先根据这一字段及 View 父类唯一匹配 actionsView，会得到两个候选。

现在通过模糊视图 `setActionsView` 的参数类型约束 actionsView，保留旧版本没有此 setter 时的原特征匹配。实际选中 `ProfileActivity.Z`（按钮视图），而非 `ProfileActivity.b0`（音乐视图）。Telegram 适配缓存版本升至 4。

静态回归补充验证：资料页与模糊视图所持按钮字段类型一致、setter 写入正确字段、按钮与音乐类型不同；未混淆版本的合成 getter 也纳入检查。下面四个 APK 均通过 39 组检查，新版显示两个渐变候选，之前的三个版本各为一个候选：

```sh
python3 scripts/check_telegram_apks.py \
  ~/tg-apks/telegram-13.1.1-72371/base.apk \
  ~/tg-apks/AyuGram-arm64-v8a-20261028.apk \
  ~/tg-apks/exteraGram-arm64-v8a-20261028.apk \
  ~/nagram-1249.apk
```

debug、release 构建及已有单元测试通过。本次设备操作仅提取 APK，实际绘制与头像展开效果需安装修复版并重启 Telegram 后验证。

## 展开头像时音乐卡片的浅色背景

关闭个人资料页配色后，音乐卡片使用浅色背景；头像展开到 0.8 时原生逻辑改为白字。禁用头像模糊后，这层浅色背景仍然存在，因此出现白底白字。这是既有绘制行为，与 13.1.1 的字段识别问题独立。

在音乐视图原生 `onDraw` 外包裹背景修正：展开进度达到 0.8 且背景亮度较高时，临时用 `0x66000000`（40% 不透明度的黑色）绘制背景，保留原生文字、图标、布局和显示动画。绘制结束通过 finally 恢复背景颜色、alpha 和 Shader，避免收起、主题切换、关闭功能或热更新后留下修改。深色卡片不改色。

成员定位复用现有 DexKit 数据流扫描，从 `Paint.getAlpha` 和 `setParentExpanded` 写入定位真正的背景画笔与展开进度，排除图标、描边和渐变专用画笔。适配缓存版本升至 5。四个 APK（官方 13.1.1、AyuGram、exteraGram、NaGram）均通过 39 组静态检查，新增检查确认该画笔实际用于圆角背景绘制。

Android 15 模拟器的 debug 测试使用生产绘制函数和真实 Paint／Canvas，验证展开边界与背景像素、展开后收起、深色背景、主题变化、Shader 恢复、异常和嵌套绘制恢复。入口仅存在于 debug：

```sh
adb -s emulator-5554 shell am start -W \
  -n io.github.a13e300.myinjector/.ui.ProfileMusicPaintTestActivity
adb -s emulator-5554 logcat -d -s ProfileMusicPaintTest:I '*:S'
```

debug、release 构建和已有单元测试通过；实际宿主账号的头像与音乐卡片效果仍需安装修复版后验证。
