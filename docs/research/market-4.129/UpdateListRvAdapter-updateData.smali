.method public final updateData(Ljava/util/List;)V
    .registers 10

    filled-new-array {v9}, [Ljava/lang/Object;
    move-result-object v0
    sget-object v2, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    const-class v1, Ljava/util/List;
    filled-new-array {v1}, [Ljava/lang/Class;
    move-result-object v5
    sget-object v6, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v3, 0x0
    const v4, 0x9814
    move-object v1, v8
    invoke-static/range {v0 .. v6}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_001c
    return-void
    :label_001c
    const-string/jumbo v0, "updateData"
    invoke-static {v9, v0}, Lkotlin/jvm/internal/s;->j(Ljava/lang/Object;Ljava/lang/String;)V
    invoke-direct {v8}, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->isCollapseState()Z
    move-result v0
    invoke-static {}, Lcom/xiaomi/market/ui/UpdateAppsSortHelp;->getMajorUpdateSwitchState()Z
    move-result v1
    iput-boolean v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->isMajorUpdateState:Z
    invoke-direct {v8, v9}, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->removeUninstallingApp(Ljava/util/List;)V
    new-instance v1, Ljava/util/concurrent/CopyOnWriteArrayList;
    check-cast v9, Ljava/util/Collection;
    invoke-direct {v1, v9}, Ljava/util/concurrent/CopyOnWriteArrayList;-><init>(Ljava/util/Collection;)V
    invoke-static {}, Lcom/xiaomi/market/data/LocalAppManager;->getManager()Lcom/xiaomi/market/data/LocalAppManager;
    move-result-object v9
    invoke-static {}, Lcom/xiaomi/market/AppGlobals;->getPkgName()Ljava/lang/String;
    move-result-object v2
    invoke-virtual {v9, v2}, Lcom/xiaomi/market/data/LocalAppManager;->getLocalAppInfo(Ljava/lang/String;)Lcom/xiaomi/market/model/LocalAppInfo;
    move-result-object v9
    new-instance v2, Ljava/util/concurrent/CopyOnWriteArrayList;
    invoke-static {v1, v9}, Lcom/xiaomi/market/util/ArrayListUtils;->moveToEnd(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
    move-result-object v9
    check-cast v9, Ljava/util/Collection;
    invoke-direct {v2, v9}, Ljava/util/concurrent/CopyOnWriteArrayList;-><init>(Ljava/util/Collection;)V
    invoke-direct {v8, v2}, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->tryExposeUpdateList(Ljava/util/List;)V
    const/4 v9, 0x0
    iput-boolean v9, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->orchardGuideBannerExposed:Z
    new-instance v1, Ljava/util/ArrayList;
    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
    const/4 v3, 0x2
    iput v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->foldShowSize:I
    invoke-virtual {v2}, Ljava/util/concurrent/CopyOnWriteArrayList;->isEmpty()Z
    move-result v3
    const/4 v4, 0x1
    if-nez v3, :label_007b
    invoke-static {}, Lcom/xiaomi/market/data/LocalAppManager;->getManager()Lcom/xiaomi/market/data/LocalAppManager;
    move-result-object v3
    invoke-virtual {v2, v9}, Ljava/util/concurrent/CopyOnWriteArrayList;->get(I)Ljava/lang/Object;
    move-result-object v5
    check-cast v5, Lcom/xiaomi/market/model/LocalAppInfo;
    iget-object v5, v5, Lcom/xiaomi/market/model/LocalAppInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v3, v5}, Lcom/xiaomi/market/data/LocalAppManager;->getDetailAppInfo(Ljava/lang/String;)Lcom/xiaomi/market/model/AppInfo;
    move-result-object v3
    if-eqz v3, :label_007b
    iget-boolean v3, v3, Lcom/xiaomi/market/model/AppInfo;->isImportantUpdate:Z
    if-ne v3, v4, :label_007b
    const/4 v3, 0x3
    iput v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->foldShowSize:I
    :label_007b
    invoke-virtual {v2}, Ljava/util/concurrent/CopyOnWriteArrayList;->size()I
    move-result v3
    iget v5, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->foldShowSize:I
    if-le v3, v5, :label_0089
    if-nez v0, :label_0089
    invoke-direct {v8, v2, v1}, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->handleFoldAppsUpdate(Ljava/util/List;Ljava/util/ArrayList;)V
    goto :label_008c
    :label_0089
    invoke-virtual {v1, v2}, Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z
    :label_008c
    iput-object v2, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->updateApps:Ljava/util/List;
    iget-object v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mConsistentUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v3}, Ljava/util/ArrayList;->clear()V
    iget-object v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mNormalUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v3}, Ljava/util/ArrayList;->clear()V
    iget-object v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mInconsistentUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v3}, Ljava/util/ArrayList;->clear()V
    iget-object v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mImportanceUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v3}, Ljava/util/ArrayList;->clear()V
    invoke-interface {v2}, Ljava/util/Collection;->isEmpty()Z
    move-result v3
    xor-int/2addr v3, v4
    iput-boolean v3, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mShowLowSpaceHint:Z
    new-instance v3, Ljava/util/ArrayList;
    invoke-direct {v3}, Ljava/util/ArrayList;-><init>()V
    invoke-virtual {v1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :label_00b2
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v5
    if-eqz v5, :label_0104
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v5
    check-cast v5, Lcom/xiaomi/market/model/LocalAppInfo;
    invoke-static {}, Lcom/xiaomi/market/data/LocalAppManager;->getManager()Lcom/xiaomi/market/data/LocalAppManager;
    move-result-object v6
    iget-object v7, v5, Lcom/xiaomi/market/model/LocalAppInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v6, v7}, Lcom/xiaomi/market/data/LocalAppManager;->getDetailAppInfo(Ljava/lang/String;)Lcom/xiaomi/market/model/AppInfo;
    move-result-object v6
    if-nez v6, :label_00cb
    goto :label_00b2
    :label_00cb
    invoke-virtual {v6}, Lcom/xiaomi/market/model/AppInfo;->isInconsistentUpdate()Z
    move-result v7
    if-eqz v7, :label_00d7
    iget-object v7, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mInconsistentUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v7, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :label_00eb
    :label_00d7
    iget-object v7, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mConsistentUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v7, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    iget-boolean v7, v6, Lcom/xiaomi/market/model/AppInfo;->isImportantUpdate:Z
    if-eqz v7, :label_00e6
    iget-object v7, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mImportanceUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v7, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    goto :label_00eb
    :label_00e6
    iget-object v7, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mNormalUpdateApps:Ljava/util/ArrayList;
    invoke-virtual {v7, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :label_00eb
    invoke-virtual {v3}, Ljava/util/ArrayList;->clear()V
    invoke-virtual {v3, v6}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    invoke-static {}, Lcom/xiaomi/market/business_core/update/auto/AutoUpdateManager;->getManager()Lcom/xiaomi/market/business_core/update/auto/AutoUpdateManager;
    move-result-object v5
    invoke-virtual {v5, v3}, Lcom/xiaomi/market/business_core/update/auto/AutoUpdateManager;->isStorageSpaceEnough(Ljava/util/List;)Z
    move-result v5
    if-eqz v5, :label_00fd
    iput-boolean v9, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mShowLowSpaceHint:Z
    :label_00fd
    sget-boolean v5, Lcom/xiaomi/market/util/MarketUtils;->DEBUG_NO_SPACE_FOR_UPDATE:Z
    if-eqz v5, :label_00b2
    iput-boolean v4, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->mShowLowSpaceHint:Z
    goto :label_00b2
    :label_0104
    iget-object v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->updateApps:Ljava/util/List;
    if-eqz v1, :label_010d
    invoke-interface {v1}, Ljava/util/List;->size()I
    move-result v1
    goto :label_010e
    :label_010d
    move v1, v9
    :label_010e
    iput v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->updateAppSize:I
    iget-object v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->updateApps:Ljava/util/List;
    if-eqz v1, :label_0145
    check-cast v1, Ljava/lang/Iterable;
    new-instance v3, Ljava/util/ArrayList;
    invoke-direct {v3}, Ljava/util/ArrayList;-><init>()V
    invoke-interface {v1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;
    move-result-object v1
    :label_011f
    invoke-interface {v1}, Ljava/util/Iterator;->hasNext()Z
    move-result v5
    if-eqz v5, :label_0140
    invoke-interface {v1}, Ljava/util/Iterator;->next()Ljava/lang/Object;
    move-result-object v5
    move-object v6, v5
    check-cast v6, Lcom/xiaomi/market/model/LocalAppInfo;
    invoke-static {}, Lcom/xiaomi/market/data/LocalAppManager;->getManager()Lcom/xiaomi/market/data/LocalAppManager;
    move-result-object v7
    iget-object v6, v6, Lcom/xiaomi/market/model/LocalAppInfo;->packageName:Ljava/lang/String;
    invoke-virtual {v7, v6}, Lcom/xiaomi/market/data/LocalAppManager;->getDetailAppInfo(Ljava/lang/String;)Lcom/xiaomi/market/model/AppInfo;
    move-result-object v6
    if-eqz v6, :label_011f
    iget-boolean v6, v6, Lcom/xiaomi/market/model/AppInfo;->isImportantUpdate:Z
    if-ne v6, v4, :label_011f
    invoke-interface {v3, v5}, Ljava/util/Collection;->add(Ljava/lang/Object;)Z
    goto :label_011f
    :label_0140
    invoke-interface {v3}, Ljava/util/List;->size()I
    move-result v1
    goto :label_0146
    :label_0145
    move v1, v9
    :label_0146
    iput v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->importantAppSize:I
    invoke-direct {v8, v2}, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->getAllInconsistentApps(Ljava/util/List;)Ljava/util/List;
    move-result-object v1
    invoke-interface {v1}, Ljava/util/List;->size()I
    move-result v1
    iput v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->inconsistentAppSize:I
    iget v1, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->updateAppSize:I
    iget v2, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->foldShowSize:I
    if-le v1, v2, :label_015b
    if-nez v0, :label_015b
    move v9, v4
    :label_015b
    iput-boolean v9, v8, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->foldButtonVisible:Z
    invoke-direct {v8}, Lcom/xiaomi/market/ui/UpdateListRvAdapter;->generateUpdateItem()Ljava/util/List;
    move-result-object v9
    invoke-virtual {v8, v9}, Lcom/chad/library/adapter/base/BaseQuickAdapter;->setDiffNewData(Ljava/util/List;)V
    return-void
.end method
