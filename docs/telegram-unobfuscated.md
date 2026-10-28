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
