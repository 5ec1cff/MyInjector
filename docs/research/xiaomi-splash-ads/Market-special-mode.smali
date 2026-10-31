.method private isSpecialMode()Ljava/lang/Boolean;
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/business_ui/main/MarketTabActivity;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v7, Ljava/lang/Boolean;
    const/4 v4, 0x0
    const/16 v5, 0x1f62
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v2, v1, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v2, :label_001a
    iget-object v0, v1, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v0, Ljava/lang/Boolean;
    return-object v0
    :label_001a
    sget-object v1, Lcom/xiaomi/market/util/ElderChecker;->INSTANCE:Lcom/xiaomi/market/util/ElderChecker;
    invoke-virtual {v1}, Lcom/xiaomi/market/util/ElderChecker;->isElderMode()Z
    move-result v1
    if-nez v1, :label_0034
    sget-object v1, Lcom/xiaomi/market/util/TalkBackUtil;->INSTANCE:Lcom/xiaomi/market/util/TalkBackUtil;
    invoke-virtual {v1}, Lcom/xiaomi/market/util/TalkBackUtil;->isTalkBackEnable()Z
    move-result v1
    if-nez v1, :label_0034
    invoke-static {}, Lcom/xiaomi/market/util/OverseasModeHelper;->getInstance()Lcom/xiaomi/market/util/OverseasModeHelper;
    move-result-object v1
    invoke-virtual {v1}, Lcom/xiaomi/market/util/OverseasModeHelper;->isOverseasMode()Z
    move-result v1
    if-eqz v1, :label_0035
    :label_0034
    const/4 v0, 0x1
    :label_0035
    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v0
    return-object v0
.end method
