.method private onSplashEnd(Landroid/app/Activity;Ljava/lang/String;IZ)V
    .registers 14

    new-instance v0, Ljava/lang/Integer;
    invoke-direct {v0, v12}, Ljava/lang/Integer;-><init>(I)V
    new-instance v1, Ljava/lang/Byte;
    invoke-direct {v1, v13}, Ljava/lang/Byte;-><init>(B)V
    filled-new-array {v10, v11, v0, v1}, [Ljava/lang/Object;
    move-result-object v2
    sget-object v4, Lcom/xiaomi/market/ui/splash/SplashManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    sget-object v0, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    sget-object v1, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const-class v3, Landroid/app/Activity;
    const-class v5, Ljava/lang/String;
    filled-new-array {v3, v5, v0, v1}, [Ljava/lang/Class;
    move-result-object v7
    sget-object v8, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v5, 0x0
    const v6, 0xa1ec
    move-object v3, v9
    invoke-static/range {v2 .. v8}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_002c
    return-void
    :label_002c
    const-string v0, "SplashManager"
    const-string/jumbo v1, "onSplashEnd"
    invoke-static {v0, v1}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {v10}, Lcom/xiaomi/market/util/ActivityMonitor;->isActive(Landroid/content/Context;)Z
    move-result v0
    if-nez v0, :label_003b
    return-void
    :label_003b
    const/4 v0, 0x0
    iput-boolean v0, v9, Lcom/xiaomi/market/ui/splash/SplashManager;->isDurationSplash:Z
    if-eqz v13, :label_0045
    invoke-direct {v9, v11}, Lcom/xiaomi/market/ui/splash/SplashManager;->getSplashAdInfo(Ljava/lang/String;)Lcom/xiaomi/market/ui/splash/SplashAdInfo;
    move-result-object v11
    goto :label_0046
    :label_0045
    const/4 v11, 0x0
    :label_0046
    invoke-static {}, Lcom/xiaomi/market/AppGlobals;->getMainHandler()Landroid/os/Handler;
    move-result-object v13
    new-instance v0, Lcom/xiaomi/market/ui/splash/c;
    invoke-direct {v0, v9, v11, v10, v12}, Lcom/xiaomi/market/ui/splash/c;-><init>(Lcom/xiaomi/market/ui/splash/SplashManager;Lcom/xiaomi/market/ui/splash/SplashAdInfo;Landroid/app/Activity;I)V
    invoke-virtual {v13, v0}, Landroid/os/Handler;->postAtFrontOfQueue(Ljava/lang/Runnable;)Z
    return-void
.end method
