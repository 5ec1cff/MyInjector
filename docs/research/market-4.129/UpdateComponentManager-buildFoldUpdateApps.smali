.method private final buildFoldUpdateApps(Ljava/util/concurrent/CopyOnWriteArrayList;)Ljava/util/List;
    .registers 9

    filled-new-array {v8}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Ljava/util/concurrent/CopyOnWriteArrayList;
    filled-new-array {v1}, [Ljava/lang/Class;
    move-result-object v5
    const-class v6, Ljava/util/List;
    const/4 v3, 0x0
    const v4, 0xa703
    move-object v1, v7
    invoke-static/range {v0 .. v6}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v1, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v1, :label_0020
    iget-object v8, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->result:Ljava/lang/Object;
    check-cast v8, Ljava/util/List;
    return-object v8
    :label_0020
    const/4 v0, 0x2
    iput v0, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->foldShowSize:I
    invoke-virtual {v8}, Ljava/util/concurrent/CopyOnWriteArrayList;->isEmpty()Z
    move-result v0
    const/4 v1, 0x1
    const/4 v2, 0x0
    if-nez v0, :label_0044
    invoke-static {}, Lcom/xiaomi/market/data/LocalAppManager;->getManager()Lcom/xiaomi/market/data/LocalAppManager;
    move-result-object v0
    invoke-virtual {v8, v2}, Ljava/util/concurrent/CopyOnWriteArrayList;->get(I)Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Lcom/xiaomi/market/model/LocalAppInfo;
    iget-object v3, v3, Lcom/xiaomi/market/model/LocalAppInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v0, v3}, Lcom/xiaomi/market/data/LocalAppManager;->getDetailAppInfo(Ljava/lang/String;)Lcom/xiaomi/market/model/AppInfo;
    move-result-object v0
    if-eqz v0, :label_0044
    iget-boolean v0, v0, Lcom/xiaomi/market/model/AppInfo;->isImportantUpdate:Z
    if-ne v0, v1, :label_0044
    const/4 v0, 0x3
    iput v0, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->foldShowSize:I
    :label_0044
    invoke-virtual {v8}, Ljava/util/concurrent/CopyOnWriteArrayList;->size()I
    move-result v0
    iget v3, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->foldShowSize:I
    if-le v0, v3, :label_0055
    iget-object v0, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->pageCollapseState:Lcom/xiaomi/market/ui/update/UpdateComponentManager$PageCollapseState;
    invoke-virtual {v0}, Lcom/xiaomi/market/ui/update/UpdateComponentManager$PageCollapseState;->isFoldState()Z
    move-result v0
    if-eqz v0, :label_0055
    goto :label_0056
    :label_0055
    move v1, v2
    :label_0056
    iput-boolean v1, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->foldButtonVisible:Z
    new-instance v0, Ljava/util/ArrayList;
    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V
    iget-boolean v1, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->foldButtonVisible:Z
    if-eqz v1, :label_0074
    iget v1, v7, Lcom/xiaomi/market/ui/update/UpdateComponentManager;->foldShowSize:I
    :label_0063
    if-ge v2, v1, :label_0077
    invoke-virtual {v8, v2}, Ljava/util/concurrent/CopyOnWriteArrayList;->get(I)Ljava/lang/Object;
    move-result-object v3
    const-string v4, "get(...)"
    invoke-static {v3, v4}, Lkotlin/jvm/internal/s;->i(Ljava/lang/Object;Ljava/lang/String;)V
    invoke-interface {v0, v3}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    add-int/lit8 v2, v2, 0x1
    goto :label_0063
    :label_0074
    invoke-interface {v0, v8}, Ljava/util/List;->addAll(Ljava/util/Collection;)Z
    :label_0077
    return-object v0
.end method
