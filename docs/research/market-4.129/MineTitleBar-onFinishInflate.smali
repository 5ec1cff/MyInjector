.method public onFinishInflate()V
    .registers 9

    const/4 v0, 0x0
    new-array v1, v0, [Ljava/lang/Object;
    sget-object v3, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->changeQuickRedirect:Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;
    new-array v6, v0, [Ljava/lang/Class;
    sget-object v7, Ljava/lang/Void;->TYPE:Ljava/lang/Class;
    const/4 v4, 0x0
    const/16 v5, 0x2dd0
    move-object v2, v8
    invoke-static/range {v1 .. v7}, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxy;->proxy([Ljava/lang/Object;Ljava/lang/Object;Lcom/xiaomi/gamecenter/sdk/robust/ChangeQuickRedirect;ZI[Ljava/lang/Class;Ljava/lang/Class;)Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;
    move-result-object v0
    iget-boolean v0, v0, Lcom/xiaomi/gamecenter/sdk/robust/PatchProxyResult;->isSupported:Z
    if-eqz v0, :label_0016
    return-void
    :label_0016
    invoke-super {v8}, Landroid/view/View;->onFinishInflate()V
    invoke-static {v8}, Lcom/xiaomi/market/databinding/NativeMineTitlebarLayoutBinding;->bind(Landroid/view/View;)Lcom/xiaomi/market/databinding/NativeMineTitlebarLayoutBinding;
    move-result-object v0
    iput-object v0, v8, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->viewBinding:Lcom/xiaomi/market/databinding/NativeMineTitlebarLayoutBinding;
    invoke-direct {v8}, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->adjustTitleBarHeight()V
    sget v0, Lcom/xiaomi/market/R$id;->t_download_view:I
    invoke-virtual {v8, v0}, Landroid/view/View;->findViewById(I)Landroid/view/View;
    move-result-object v0
    const-string v1, "findViewById(...)"
    invoke-static {v0, v1}, Lkotlin/jvm/internal/s;->i(Ljava/lang/Object;Ljava/lang/String;)V
    check-cast v0, Lcom/xiaomi/market/widget/MainDownloadView;
    iput-object v0, v8, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->downloadView:Lcom/xiaomi/market/widget/MainDownloadView;
    invoke-direct {v8}, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->initDarkMode()V
    invoke-direct {v8}, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->initListeners()V
    sget-object v0, Lcom/xiaomi/market/util/ElderChecker;->INSTANCE:Lcom/xiaomi/market/util/ElderChecker;
    invoke-virtual {v0}, Lcom/xiaomi/market/util/ElderChecker;->isElderMode()Z
    move-result v0
    if-eqz v0, :label_0042
    invoke-direct {v8}, Lcom/xiaomi/market/business_ui/main/mine/view/MineTitleBar;->adaptElderModeView()V
    :label_0042
    return-void
.end method
