.method public final a(Ltv/danmaku/bili/MainActivityV2;Landroid/view/ViewGroup;)Z
    .registers 7

    const/4 v0, 0x0
    if-nez v6, :label_0004
    return v0
    :label_0004
    iput-object v6, v4, Ltv/danmaku/bili/splash/shell/a;->a:Landroid/view/ViewGroup;
    invoke-virtual {v5}, Landroidx/fragment/app/FragmentActivity;->getSupportFragmentManager()Landroidx/fragment/app/FragmentManager;
    move-result-object v1
    const-string v2, "ADSplashFragment"
    invoke-virtual {v1, v2}, Landroidx/fragment/app/FragmentManager;->findFragmentByTag(Ljava/lang/String;)Landroidx/fragment/app/Fragment;
    move-result-object v3
    if-eqz v3, :label_0015
    invoke-static {v1, v3}, Lcom/bilibili/bilibililive/streaming/pandora/extension/client/mode/g;->b(Landroidx/fragment/app/FragmentManager;Landroidx/fragment/app/Fragment;)V
    :label_0015
    invoke-virtual {v1}, Landroidx/fragment/app/FragmentManager;->beginTransaction()Landroidx/fragment/app/FragmentTransaction;
    move-result-object v1
    invoke-virtual {v6, v0}, Landroid/view/ViewGroup;->setVisibility(I)V
    new-instance v6, Lkntr/srcs/app/splash/startup/SplashContainerFragment;
    invoke-direct {v6}, Lkntr/srcs/app/splash/startup/SplashContainerFragment;-><init>()V
    const v0, 0x7f094251
    invoke-virtual {v1, v0, v6, v2}, Landroidx/fragment/app/FragmentTransaction;->add(ILandroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;
    invoke-virtual {v1}, Landroidx/fragment/app/FragmentTransaction;->commitAllowingStateLoss()I
    invoke-virtual {v4, v5}, Ltv/danmaku/bili/splash/shell/a;->b(Ltv/danmaku/bili/MainActivityV2;)V
    const/4 v5, 0x1
    return v5
.end method
