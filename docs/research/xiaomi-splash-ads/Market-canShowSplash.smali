.method private static canShowSplash(Landroid/app/Activity;)Z
    .registers 8

    filled-new-array {v7}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/splash/SplashManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Landroid/app/Activity;
    filled-new-array {v1}, [Ljava/lang/Class;
    move-result-object v5
    sget-object v6, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const/4 v1, 0x0
    const/4 v3, 0x1
    const v4, 0xa1da
    invoke-static/range {v0 .. v6}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0024
    iget-object v7, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v7, Ljava/lang/Boolean;
    invoke-virtual {v7}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v7
    return v7
    :label_0024
    instance-of v0, v7, Lcom/xiaomi/market/ui/BaseActivity;
    const/4 v1, 0x0
    if-nez v0, :label_002a
    return v1
    :label_002a
    invoke-static {v7}, Lcom/xiaomi/market/util/ActivityMonitor;->isActive(Landroid/content/Context;)Z
    move-result v0
    if-nez v0, :label_0031
    return v1
    :label_0031
    invoke-static {}, Lcom/xiaomi/market/common/compat/ConnectivityManagerCompat;->isConnected()Z
    move-result v0
    if-nez v0, :label_0038
    return v1
    :label_0038
    invoke-static {}, Lcom/xiaomi/market/util/UserAgreement;->allowConnectNetwork()Z
    move-result v0
    if-nez v0, :label_003f
    return v1
    :label_003f
    move-object v0, v7
    check-cast v0, Lcom/xiaomi/market/ui/BaseActivity;
    invoke-virtual {v0}, Lcom/xiaomi/market/ui/BaseActivity;->needShowSplash()Z
    move-result v2
    if-nez v2, :label_0049
    return v1
    :label_0049
    invoke-static {v7}, Lcom/xiaomi/market/ui/splash/SplashManager;->isPassiveSplashAd(Landroid/app/Activity;)Z
    move-result v7
    const/4 v2, 0x1
    if-eqz v7, :label_0051
    return v2
    :label_0051
    invoke-virtual {v0}, Lcom/xiaomi/market/ui/BaseActivity;->getPageRef()Ljava/lang/String;
    move-result-object v7
    const-string v3, "com.miui.home"
    invoke-static {v7, v3}, Lcom/xiaomi/market/util/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
    move-result v7
    if-nez v7, :label_0069
    invoke-virtual {v0}, Lcom/xiaomi/market/ui/BaseActivity;->getCallingPackage()Ljava/lang/String;
    move-result-object v7
    sget-object v0, Lcom/xiaomi/market/util/Constants$PackageName;->DEFAULT_MIUI_HOME_PACKAGE:Ljava/lang/String;
    invoke-static {v7, v0}, Lcom/xiaomi/market/util/TextUtils;->equals(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z
    move-result v7
    if-eqz v7, :label_006a
    :label_0069
    move v1, v2
    :label_006a
    return v1
.end method
