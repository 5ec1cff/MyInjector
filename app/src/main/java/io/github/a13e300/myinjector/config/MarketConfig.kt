package io.github.a13e300.myinjector.config

/** 模块 remote prefs 中的应用商店配置，缺失项按默认开启处理。 */
data class MarketConfig(
    val removeSplashAd: Boolean = true,
    val superMinimalMode: Boolean = true,
    val simplifyUpdates: Boolean = true,
)
