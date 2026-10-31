.method public onRefreshPageData(Ljava/util/List;ZLorg/json/JSONObject;)V
    .registers 12

    new-instance v0, Ljava/lang/Byte;
    invoke-direct {v0, v10}, Ljava/lang/Byte;-><init>(B)V
    filled-new-array {v9, v0, v11}, [Ljava/lang/Object;
    move-result-object v1
    sget-object v3, Lcom/xiaomi/market/ui/update/UpdateListV2Fragment;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    sget-object v10, Ljava/lang/Boolean;->TYPE:Ljava/lang/Class;
    const-class v0, Lorg/json/JSONObject;
    const-class v2, Ljava/util/List;
    filled-new-array {v2, v10, v0}, [Ljava/lang/Class;
    move-result-object v6
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const v5, 0xa726
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v10
    iget-boolean v10, v10, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v10, :label_0025
    return-void
    :label_0025
    const-string v10, "componentList"
    invoke-static {v9, v10}, Lkotlin/jvm/internal/s;->j(Ljava/lang/Object;Ljava/lang/String;)V
    const-string/jumbo v10, "responseJson"
    invoke-static {v11, v10}, Lkotlin/jvm/internal/s;->j(Ljava/lang/Object;Ljava/lang/String;)V
    new-instance v10, Ljava/lang/StringBuilder;
    invoke-direct {v10}, Ljava/lang/StringBuilder;-><init>()V
    const-string/jumbo v0, "onRefreshPageData: responseJson = "
    invoke-virtual {v10, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v10, v11}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v10
    const-string v0, "UpdateListV2Fragment"
    invoke-static {v0, v10}, Lcom/xiaomi/market/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)V
    sget-object v10, Lcom/xiaomi/market/ui/update/native_active/NativeActiveItemHelper;->INSTANCE:Lcom/xiaomi/market/ui/update/native_active/NativeActiveItemHelper;
    invoke-virtual {v10, v9}, Lcom/xiaomi/market/ui/update/native_active/NativeActiveItemHelper;->filterActiveList(Ljava/util/List;)Ljava/util/List;
    move-result-object v10
    iput-object v10, v8, Lcom/xiaomi/market/ui/update/UpdateListV2Fragment;->mRecommendComponentList:Ljava/util/List;
    invoke-direct {v8}, Lcom/xiaomi/market/ui/update/UpdateListV2Fragment;->getDataListSize()I
    move-result v10
    if-lez v10, :label_005c
    invoke-direct {v8}, Lcom/xiaomi/market/ui/update/UpdateListV2Fragment;->initRecommendCardPosition()V
    invoke-virtual {v8, v9}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->appendDataList(Ljava/util/List;)V
    goto :label_0063
    :label_005c
    invoke-direct {v8}, Lcom/xiaomi/market/ui/update/UpdateListV2Fragment;->buildComponentList()Ljava/util/List;
    move-result-object v9
    invoke-virtual {v8, v9}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->setDataList(Ljava/util/List;)V
    :label_0063
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->getEmptyLoadingView()Lcom/xiaomi/market/common/view/NativeEmptyLoadingView;
    move-result-object v9
    invoke-virtual {v9}, Lcom/xiaomi/market/common/view/NativeEmptyLoadingView;->loadSuccess()V
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->getRecyclerView()Landroidx/recyclerview/widget/RecyclerView;
    move-result-object v9
    const/4 v10, 0x0
    invoke-virtual {v9, v10}, Landroid/view/View;->setVisibility(I)V
    invoke-virtual {v8, v10}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->setHasNextPageLoadFailed(Z)V
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->tryRecordTtfd()V
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->getHandler()Landroid/os/Handler;
    move-result-object v9
    const/16 v10, 0x1001
    invoke-virtual {v9, v10}, Landroid/os/Handler;->sendEmptyMessage(I)Z
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->tryReportLoadTime()V
    invoke-virtual {v8}, Lcom/xiaomi/market/business_ui/base/NativeFeedFragment;->getCurrentPage()I
    move-result v9
    invoke-virtual {v8, v9, v11}, Lcom/xiaomi/market/ui/update/UpdateListV2Fragment;->loadSuccess(ILorg/json/JSONObject;)V
    return-void
.end method
