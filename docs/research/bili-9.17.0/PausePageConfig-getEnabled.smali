.method public final getEnabled()Z
    .registers 4

    sget-object v0, Lkntr/base/config/KConfig;->INSTANCE:Lkntr/base/config/KConfig;
    const-string v1, "dd_ad_pausedpage_enable"
    const/4 v2, 0x1
    invoke-virtual {v0, v1, v2}, Lkntr/base/config/KConfig;->ff(Ljava/lang/String;Z)Z
    move-result v0
    return v0
.end method
