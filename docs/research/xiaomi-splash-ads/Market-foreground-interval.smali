.method public trySplashWhenApplicationForeground(Landroid/app/Activity;)V
    .registers 9

    filled-new-array {v8}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/splash/SplashManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Landroid/app/Activity;
    filled-new-array {v1}, [Ljava/lang/Class;
    move-result-object v5
    sget-object v6, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v3, 0x0
    const v4, 0xa1e1
    move-object v1, v7
    invoke-static/range {v0 .. v6}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_001c
    return-void
    :label_001c
    iget-wide v0, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->mBackgroundTime:J
    const-wide/16 v2, -0x1
    cmp-long v0, v0, v2
    if-eqz v0, :label_005e
    invoke-static {}, Landroid/os/SystemClock;->elapsedRealtime()J
    move-result-wide v0
    iget-wide v2, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->mBackgroundTime:J
    sub-long/2addr v0, v2
    iput-wide v0, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->mBackgroundTime:J
    new-instance v0, Ljava/lang/StringBuilder;
    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V
    const-string/jumbo v1, "mBackgroundTime real"
    invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    iget-wide v1, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->mBackgroundTime:J
    invoke-virtual {v0, v1, v2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    const-string v1, "SplashManager"
    invoke-static {v1, v0}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    iget-wide v0, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->mBackgroundTime:J
    invoke-static {}, Lcom/xiaomi/market/model/ClientConfig;->get()Lcom/xiaomi/market/model/ClientConfig;
    move-result-object v2
    iget-wide v2, v2, Lcom/xiaomi/market/model/ClientConfig;->splashInterval:J
    cmp-long v0, v0, v2
    if-lez v0, :label_0058
    const-string v0, "background"
    invoke-virtual {v7, v8, v0}, Lcom/xiaomi/market/ui/splash/SplashManager;->tryAdSplash(Landroid/app/Activity;Ljava/lang/String;)V
    goto :label_005b
    :label_0058
    invoke-direct {v7}, Lcom/xiaomi/market/ui/splash/SplashManager;->notifySplashDismiss()V
    :label_005b
    invoke-virtual {v7}, Lcom/xiaomi/market/ui/splash/SplashManager;->resetBackgroundTime()V
    :label_005e
    return-void
.end method
