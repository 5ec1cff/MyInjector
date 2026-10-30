.method public final switchToControlCenter()V
    .registers 6

    iget-boolean v0, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switching:Z
    invoke-virtual {v5}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->getNotificationExpanded()Z
    move-result v1
    invoke-virtual {v5}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->getControlCenterExpanded()Z
    move-result v2
    const-string/jumbo v3, "switchToControlCenter "
    const-string v4, " "
    invoke-static {v3, v4, v4, v0, v1}, Landroidx/compose/material3/internal/TextFieldImplKt$$ExternalSyntheticOutline0;->m(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/StringBuilder;
    move-result-object v0
    invoke-virtual {v0, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v0
    const-string v1, "ShadeSwitchController"
    invoke-static {v1, v0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    invoke-virtual {v5}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->isSwitchable()Z
    move-result v0
    if-nez v0, :label_0026
    goto :label_005a
    :label_0026
    iget-boolean v0, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switching:Z
    if-nez v0, :label_0031
    invoke-virtual {v5}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->getControlCenterExpanded()Z
    move-result v0
    if-eqz v0, :label_0031
    goto :label_005a
    :label_0031
    iget-object v0, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->statusBarStateController:Lcom/android/systemui/plugins/statusbar/StatusBarStateController;
    invoke-interface {v0}, Lcom/android/systemui/plugins/statusbar/StatusBarStateController;->getState()I
    move-result v1
    const/4 v2, 0x1
    if-eq v1, v2, :label_005a
    invoke-interface {v0}, Lcom/android/systemui/plugins/statusbar/StatusBarStateController;->getState()I
    move-result v0
    const/4 v1, 0x2
    if-ne v0, v1, :label_0042
    goto :label_005a
    :label_0042
    const/4 v0, 0x0
    iput-boolean v0, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->fromCtrl:Z
    iput-boolean v2, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->toCtrl:Z
    invoke-virtual {v5, v2}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->setSwitching(Z)V
    iget-object v1, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->progressAnimator:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;
    const/4 v3, 0x0
    invoke-virtual {v1, v3}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->setProgress(F)V
    iget-object v1, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->notification:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;
    invoke-virtual {v1, v0, v2}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->setTriggerAppeared(ZZ)V
    iget-object v5, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->controlCenter:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;
    invoke-virtual {v5, v2, v2}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->setTriggerAppeared(ZZ)V
    :label_005a
    return-void
.end method
