.method public tryAdSplash(Landroid/app/Activity;Ljava/lang/String;)V
    .registers 10

    filled-new-array {v8, v9}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/splash/SplashManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Landroid/app/Activity;
    const-class v3, Ljava/lang/String;
    filled-new-array {v1, v3}, [Ljava/lang/Class;
    move-result-object v5
    sget-object v6, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v3, 0x0
    const v4, 0xa1e0
    move-object v1, v7
    invoke-static/range {v0 .. v6}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_001e
    return-void
    :label_001e
    invoke-static {}, Lcom/xiaomi/market/util/UserAgreement;->allowConnectNetwork()Z
    move-result v0
    if-nez v0, :label_002d
    const-string v8, "SplashManager"
    const-string/jumbo v9, "splash blocked by CTA"
    invoke-static {v8, v9}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    return-void
    :label_002d
    invoke-direct {v7, v8, v9}, Lcom/xiaomi/market/ui/splash/SplashManager;->shownSplashCoverIfNeed(Landroid/app/Activity;Ljava/lang/String;)V
    iget-object v0, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->trySplashTask:Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;
    if-eqz v0, :label_003c
    invoke-static {v0}, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->b(Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;)V
    iget-object v0, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->trySplashTask:Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;
    invoke-static {v0}, Lcom/xiaomi/market/util/ThreadUtils;->cancelAsyncTaskAction(Ljava/lang/Runnable;)V
    :label_003c
    new-instance v0, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;
    invoke-direct {v0, v8, v9}, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;-><init>(Landroid/app/Activity;Ljava/lang/String;)V
    iput-object v0, v7, Lcom/xiaomi/market/ui/splash/SplashManager;->trySplashTask:Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;
    invoke-static {v0}, Lcom/xiaomi/market/util/ThreadUtils;->runInAsyncTask(Ljava/lang/Runnable;)V
    return-void
.end method
