.method private trySplash()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/business_ui/main/MarketTabActivity;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x1f54
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0016
    return-void
    :label_0016
    sget-object v1, Lcom/xiaomi/market/business_ui/main/home/HomePageStateManager;->INSTANCE:Lcom/xiaomi/market/business_ui/main/home/HomePageStateManager;
    invoke-virtual {v1}, Lcom/xiaomi/market/business_ui/main/home/HomePageStateManager;->isRecreated()Z
    move-result v2
    const-string v3, "MarketTab"
    if-nez v2, :label_0072
    invoke-direct {v8}, Lcom/xiaomi/market/business_ui/main/MarketTabActivity;->isSpecialMode()Ljava/lang/Boolean;
    move-result-object v2
    invoke-virtual {v2}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v2
    if-nez v2, :label_0072
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/main/MarketTabActivity;->isInFirstRecommendProcess()Z
    move-result v2
    if-eqz v2, :label_0031
    goto :label_0072
    :label_0031
    sget-object v0, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->INSTANCE:Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;
    invoke-virtual {v0}, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->clearAdInfo()V
    invoke-virtual {v8}, Lcom/xiaomi/market/ui/BaseActivity;->getCallingPackage()Ljava/lang/String;
    move-result-object v1
    sget-object v2, Lcom/xiaomi/market/util/Constants$PackageName;->DEFAULT_MIUI_HOME_PACKAGE:Ljava/lang/String;
    invoke-static {v1, v2}, Lcom/xiaomi/market/util/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
    move-result v1
    if-eqz v1, :label_0061
    const-string/jumbo v0, "tryAdSplash : start"
    invoke-static {v3, v0}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {}, Lcom/xiaomi/market/ui/splash/SplashManager;->getInstance()Lcom/xiaomi/market/ui/splash/SplashManager;
    move-result-object v0
    invoke-virtual {v0, v8}, Lcom/xiaomi/market/ui/splash/SplashManager;->addSplashDismissListener(Lcom/xiaomi/market/ui/splash/SplashManager$SplashDismissListener;)V
    invoke-static {}, Lcom/xiaomi/market/ui/splash/SplashManager;->getInstance()Lcom/xiaomi/market/ui/splash/SplashManager;
    move-result-object v0
    invoke-virtual {v8}, Lcom/xiaomi/market/ui/BaseActivity;->getPageRef()Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v8, v1}, Lcom/xiaomi/market/ui/splash/SplashManager;->tryAdSplash(Landroid/app/Activity;Ljava/lang/String;)V
    const-string/jumbo v0, "tryAdSplash : end"
    invoke-static {v3, v0}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    goto :label_0071
    :label_0061
    const-string/jumbo v1, "setFocusVideoSplashAdInfo : start"
    invoke-static {v3, v1}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->setFocusVideoSplashAdInfo(Lcom/xiaomi/market/ui/splash/SplashAdInfo;)V
    const-string/jumbo v0, "setFocusVideoSplashAdInfo : end"
    invoke-static {v3, v0}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    :label_0071
    return-void
    :label_0072
    new-instance v2, Ljava/lang/StringBuilder;
    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V
    const-string v4, "isRecreated : "
    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1}, Lcom/xiaomi/market/business_ui/main/home/HomePageStateManager;->isRecreated()Z
    move-result v4
    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    const-string v4, ",isSpecialMode : "
    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-direct {v8}, Lcom/xiaomi/market/business_ui/main/MarketTabActivity;->isSpecialMode()Ljava/lang/Boolean;
    move-result-object v4
    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    const-string v4, ",isInFirstRecommendProcess : "
    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/main/MarketTabActivity;->isInFirstRecommendProcess()Z
    move-result v4
    invoke-virtual {v2, v4}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v2
    invoke-static {v3, v2}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    invoke-virtual {v1, v0}, Lcom/xiaomi/market/business_ui/main/home/HomePageStateManager;->setFocusVideoState(Z)V
    sget-object v0, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->INSTANCE:Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;
    const/4 v1, 0x1
    invoke-virtual {v0, v1}, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->setFocusVideoMakeSure(Z)V
    return-void
.end method
