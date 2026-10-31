.method public run()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const v5, 0xa207
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v1, v1, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0017
    return-void
    :label_0017
    iget-object v1, v8, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->activityRef:Ljava/lang/ref/WeakReference;
    invoke-virtual {v1}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
    move-result-object v1
    check-cast v1, Landroid/app/Activity;
    invoke-static {v1}, Lcom/xiaomi/market/ui/splash/SplashManager;->i(Landroid/app/Activity;)Z
    move-result v2
    const-string v3, "SplashManager"
    const/4 v4, -0x1
    const-string v5, ""
    if-eqz v2, :label_0064
    invoke-direct {v8}, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->checkAdInterruptState()Z
    move-result v2
    if-eqz v2, :label_0031
    goto :label_0064
    :label_0031
    iget-object v2, v8, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->pageRef:Ljava/lang/String;
    invoke-direct {v8, v1, v2}, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->initSplashState(Landroid/app/Activity;Ljava/lang/String;)V
    invoke-direct {v8}, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->checkAdInterruptState()Z
    move-result v2
    if-eqz v2, :label_0044
    invoke-static {}, Lcom/xiaomi/market/ui/splash/SplashManager;->getInstance()Lcom/xiaomi/market/ui/splash/SplashManager;
    move-result-object v2
    invoke-static {v2, v1, v5, v4, v0}, Lcom/xiaomi/market/ui/splash/SplashManager;->g(Lcom/xiaomi/market/ui/splash/SplashManager;Landroid/app/Activity;Ljava/lang/String;IZ)V
    return-void
    :label_0044
    const-string v0, "SystemSplashAd.requestAd"
    invoke-static {v3, v0}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    invoke-static {}, Lcom/xiaomi/market/AppGlobals;->getContext()Landroid/app/Application;
    move-result-object v0
    iget-object v2, v8, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->adListener:Lcom/xiaomi/market/ui/splash/SplashManager$AdListener;
    iget-object v3, v8, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->pageRef:Ljava/lang/String;
    invoke-static {v3, v1}, Lcom/xiaomi/market/ui/splash/SplashManager;->l(Ljava/lang/String;Landroid/app/Activity;)Z
    move-result v3
    invoke-static {v1}, Lcom/xiaomi/market/ui/splash/SplashManager;->k(Landroid/app/Activity;)Z
    move-result v1
    invoke-direct {v8, v3, v1}, Lcom/xiaomi/market/ui/splash/SplashManager$SplashTask;->createSplashConfig(ZZ)Lcom/msa/sdk/core/splash/a;
    move-result-object v1
    invoke-static {v0, v2, v1}, Lcom/msa/sdk/core/splash/SystemSplashAd;->j(Landroid/content/Context;Lcom/miui/systemAdSolution/splashAd/IAdListener;Lcom/msa/sdk/core/splash/a;)V
    invoke-static {}, Lcom/xiaomi/market/ui/splash/SplashManager;->tryUpdateRecommendState()V
    return-void
    :label_0064
    invoke-static {}, Lcom/xiaomi/market/ui/splash/SplashManager;->getInstance()Lcom/xiaomi/market/ui/splash/SplashManager;
    move-result-object v2
    invoke-static {v2, v1, v5, v4, v0}, Lcom/xiaomi/market/ui/splash/SplashManager;->g(Lcom/xiaomi/market/ui/splash/SplashManager;Landroid/app/Activity;Ljava/lang/String;IZ)V
    const-string/jumbo v0, "splash can't show"
    invoke-static {v3, v0}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    return-void
.end method
