.method private final isCollapseState()Z
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const v5, 0x9818
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v1
    iget-boolean v2, v1, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v2, :label_001f
    iget-object v0, v1, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v0, Ljava/lang/Boolean;
    invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v0
    return v0
    :label_001f
    iget-object v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->pageCollapseState:Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;
    sget-object v2, Lcom/xiaomi/market/ui/UpdateListRvAdapter$WhenMappings;->$EnumSwitchMapping$0:[I
    invoke-virtual {v1}, Ljava/lang/Enum;->ordinal()I
    move-result v1
    aget v1, v2, v1
    const/4 v2, 0x1
    if-eq v1, v2, :label_002d
    goto :label_002e
    :label_002d
    move v0, v2
    :label_002e
    return v0
.end method
