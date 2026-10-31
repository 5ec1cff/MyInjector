.method public getNativeMineFragment()Ljava/lang/Class;
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/model/TabInfo;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    const-class v7, Ljava/lang/Class;
    const/4 v4, 0x0
    const v5, 0x82c6
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_001b
    iget-object v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v0, Ljava/lang/Class;
    return-object v0
    :label_001b
    invoke-static {}, Lcom/xiaomi/market/util/OverseasModeHelper;->getInstance()Lcom/xiaomi/market/util/OverseasModeHelper;
    move-result-object v0
    invoke-virtual {v0}, Lcom/xiaomi/market/util/OverseasModeHelper;->isOverseasMode()Z
    move-result v0
    if-eqz v0, :label_0028
    const-class v0, Lcom/xiaomi/market/business_ui/main/mine/overseas/NativeOverseasMinePagerFragment;
    return-object v0
    :label_0028
    const-class v0, Lcom/xiaomi/market/business_ui/main/mine/v2/NativeMinePagerV2Fragment;
    return-object v0
.end method
