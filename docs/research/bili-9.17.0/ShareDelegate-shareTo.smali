.method public final shareTo(Ljava/lang/String;)V
    .registers 4

    iget-object v0, v2, Lcom/bilibili/lib/sharewrapper/a;->b:Ljava/lang/ref/WeakReference;
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    const-string v1, "share.helper.inner"
    if-nez v0, :label_0010
    const-string v3, "mCallback.get() is null"
    invoke-static {v1, v3}, Lcom/bilibili/lib/sharewrapper/Bshare/ShareBLog;->e(Ljava/lang/String;Ljava/lang/String;)V
    return-void
    :label_0010
    iget-object v0, v2, Lcom/bilibili/lib/sharewrapper/a;->b:Ljava/lang/ref/WeakReference;
    invoke-virtual {v0}, Ljava/lang/ref/WeakReference;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/bilibili/lib/sharewrapper/ShareHelper$Callback;
    invoke-interface {v0, v3}, Lcom/bilibili/lib/sharewrapper/ShareHelper$Callback;->getShareContent(Ljava/lang/String;)Landroid/os/Bundle;
    move-result-object v0
    if-nez v0, :label_0024
    const-string v3, "empty share params"
    invoke-static {v1, v3}, Lcom/bilibili/lib/sharewrapper/Bshare/ShareBLog;->d(Ljava/lang/String;Ljava/lang/String;)V
    return-void
    :label_0024
    invoke-virtual {v2, v3, v0}, Lcom/bilibili/lib/sharewrapper/a;->shareTo(Ljava/lang/String;Landroid/os/Bundle;)V
    return-void
.end method
