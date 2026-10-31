.method private final isRequestApprovedByServer(Lcom/xiaomi/market/ui/splash/DetailSplashAdManager$Env;)Z
    .registers 9

    filled-new-array {v8}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager$Env;
    filled-new-array {v1}, [Ljava/lang/Class;
    move-result-object v5
    sget-object v6, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const/4 v3, 0x0
    const v4, 0xa1b7
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
    invoke-virtual {v8}, Lcom/xiaomi/market/ui/splash/DetailSplashAdManager$Env;->getAppDetailV3()Lcom/xiaomi/market/common/network/retrofit/response/bean/AppDetailV3;
    move-result-object v8
    const/4 v0, 0x0
    if-eqz v8, :label_0033
    invoke-virtual {v8}, Lcom/xiaomi/market/common/network/retrofit/response/bean/AppDetailV3;->getShowOpenScreenAd()Z
    move-result v8
    const/4 v1, 0x1
    if-ne v8, v1, :label_0033
    move v0, v1
    :label_0033
    new-instance v8, Ljava/lang/StringBuilder;
    invoke-direct {v8}, Ljava/lang/StringBuilder;-><init>()V
    const-string/jumbo v1, "request approved by server? "
    invoke-virtual {v8, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v8, v0}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v8
    const-string v1, "DirectDetailSplashAdManager"
    invoke-static {v1, v8}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    return v0
.end method
