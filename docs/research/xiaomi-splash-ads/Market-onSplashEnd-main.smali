.method private synthetic lambda$onSplashEnd$1(Lcom/xiaomi/market/ui/splash/SplashAdInfo;Landroid/app/Activity;I)V
    .registers 12

    new-instance v0, Ljava/lang/Integer;
    invoke-direct {v0, v11}, Ljava/lang/Integer;-><init>(I)V
    filled-new-array {v9, v10, v0}, [Ljava/lang/Object;
    move-result-object v1
    sget-object v3, Lcom/xiaomi/market/ui/splash/SplashManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v0, Landroid/app/Activity;
    sget-object v2, Ljava/lang/Integer;->TYPE:Ljava/lang/Class;
    const-class v4, Lcom/xiaomi/market/ui/splash/SplashAdInfo;
    filled-new-array {v4, v0, v2}, [Ljava/lang/Class;
    move-result-object v6
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const v5, 0xa1f4
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0025
    return-void
    :label_0025
    if-nez v9, :label_002d
    invoke-static {v10}, Lcom/xiaomi/market/util/UIUtils;->quitFullScreen(Landroid/app/Activity;)V
    invoke-direct {v8, v10}, Lcom/xiaomi/market/ui/splash/SplashManager;->hideSplashCoverIfNeed(Landroid/app/Activity;)V
    :label_002d
    sget-object v10, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->INSTANCE:Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;
    invoke-virtual {v10, v9}, Lcom/xiaomi/market/common/component/focus_video/FocusVideoAdManager;->setFocusVideoSplashAdInfo(Lcom/xiaomi/market/ui/splash/SplashAdInfo;)V
    if-eqz v11, :label_0037
    invoke-direct {v8}, Lcom/xiaomi/market/ui/splash/SplashManager;->notifySplashDismiss()V
    :label_0037
    return-void
.end method
