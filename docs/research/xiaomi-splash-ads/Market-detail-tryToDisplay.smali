.method private final tryToDisplaySplashAd(Lcom/xiaomi/market/common/network/retrofit/response/bean/AppDetailV3;)Z
    .registers 9

    filled-new-array {v8}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/OnePixel2DetailActivity;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Lcom/xiaomi/market/common/network/retrofit/response/bean/AppDetailV3;
    filled-new-array {v1}, [Ljava/lang/Class;
    move-result-object v5
    sget-object v6, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const/4 v3, 0x0
    const v4, 0x8e6f
    move-object v1, v7
    invoke-static/range {v0 .. v6}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0024
    iget-object v8, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v8, Ljava/lang/Boolean;
    invoke-virtual {v8}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v8
    return v8
    :label_0024
    new-instance v0, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager$Env;
    invoke-virtual {v7}, Lcom/xiaomi/market/ui/BaseActivity;->getCallingPackage()Ljava/lang/String;
    move-result-object v1
    const-string v2, "getCallingPackage(...)"
    invoke-static {v1, v2}, Lkotlin/jvm/internal/s;->i(Ljava/lang/Object;Ljava/lang/String;)V
    invoke-direct {v0, v8, v1}, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager$Env;-><init>(Lcom/xiaomi/market/common/network/retrofit/response/bean/AppDetailV3;Ljava/lang/String;)V
    invoke-static {v0}, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager;->canRequestSplashAd(Lcom/xiaomi/market/ui/splash/DetailSplashAdManager$Env;)Z
    move-result v0
    if-eqz v0, :label_0043
    new-instance v0, Lcom/xiaomi/market/ui/q6;
    invoke-direct {v0, v7, v8}, Lcom/xiaomi/market/ui/q6;-><init>(Lcom/xiaomi/market/ui/OnePixel2DetailActivity;Lcom/xiaomi/market/common/network/retrofit/response/bean/AppDetailV3;)V
    const/4 v8, 0x0
    invoke-static {v7, v8, v0}, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager;->tryToRequestSplashAd(Lcom/xiaomi/market/ui/BaseActivity;Ljava/lang/Runnable;Ljava/lang/Runnable;)V
    const/4 v8, 0x1
    return v8
    :label_0043
    const/4 v8, 0x0
    return v8
.end method
