.method private static synthetic lambda$tryUpdateRecommendState$0()V
    .registers 8

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/ui/splash/SplashManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v2, 0x0
    const/4 v4, 0x1
    const v5, 0xa1f5
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0017
    return-void
    :label_0017
    invoke-static {}, Lcom/xiaomi/market/util/SettingsUtils;->shouldShowPersonalizedRecommend()Z
    move-result v0
    new-instance v1, Ljava/lang/StringBuilder;
    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V
    const-string/jumbo v2, "tryUpdateRecommendState state : "
    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v1
    const-string v2, "SplashManager"
    invoke-static {v2, v1}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    const-string v1, "content://com.miui.systemAdSolution.adSwitch/adPersonalizedSwitch"
    invoke-static {v1}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;
    move-result-object v1
    new-instance v3, Landroid/content/ContentValues;
    invoke-direct {v3}, Landroid/content/ContentValues;-><init>()V
    const-string v4, "adPackage"
    invoke-static {}, Lcom/xiaomi/market/AppGlobals;->getPkgName()Ljava/lang/String;
    move-result-object v5
    invoke-virtual {v3, v4, v5}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v0
    const-string v4, "adPersonalizedSwitchStatus"
    invoke-virtual {v3, v4, v0}, Landroid/content/ContentValues;->put(Ljava/lang/String;Ljava/lang/Boolean;)V
    :label_004f
    invoke-static {}, Lcom/xiaomi/market/AppGlobals;->getContentResolver()Landroid/content/ContentResolver;
    move-result-object v0
    invoke-virtual {v0, v1}, Landroid/content/ContentResolver;->acquireContentProviderClient(Landroid/net/Uri;)Landroid/content/ContentProviderClient;
    move-result-object v0
    const/4 v4, 0x0
    invoke-virtual {v0, v1, v3, v4, v4}, Landroid/content/ContentProviderClient;->update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I
    :label_005b
    goto :label_0072
    :label_005c
    move-exception v0
    new-instance v1, Ljava/lang/StringBuilder;
    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V
    const-string/jumbo v3, "tryUpdateRecommendState : "
    invoke-virtual {v1, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v1, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    invoke-static {v2, v0}, Lcom/xiaomi/market/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)V
    :label_0072
    return-void
    .catch Ljava/lang/Exception; {:label_004f .. :label_005b} :label_005c
.end method
