.method public final updateTransX()V
    .registers 6

    iget-object v0, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->shade:Lcom/miui/interfaces/shade/ShadeWrapper;
    invoke-interface {v0}, Lcom/miui/interfaces/shade/ShadeWrapper;->getExpanded()Z
    move-result v1
    const/4 v2, 0x0
    if-eqz v1, :label_0037
    iget-object v1, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->this$0:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;
    iget v3, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->$r8$classId:I
    packed-switch v3, :payload_004c_000d
    iget-boolean v4, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switching:Z
    if-eqz v4, :label_002b
    iget-object v2, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->progressAnimator:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;
    iget v2, v2, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->_progress:F
    iget v1, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switchDistance:F
    mul-float/2addr v2, v1
    goto :label_002b
    :label_001c
    iget-boolean v4, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switching:Z
    if-eqz v4, :label_002b
    iget v4, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switchDistance:F
    iget-object v1, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->progressAnimator:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;
    iget v1, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->_progress:F
    invoke-static {v4, v2, v1}, Lcom/miui/utils/MathUtils;->lerp(FFF)F
    move-result v1
    neg-float v2, v1
    :label_002b
    packed-switch v3, :payload_0052_002b
    iget v1, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->triggerShifting:F
    goto :label_0033
    :label_0031
    iget v1, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->triggerShifting:F
    :label_0033
    iget v5, v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->triggerProgress:F
    mul-float/2addr v1, v5
    add-float/2addr v2, v1
    :label_0037
    invoke-interface {v0}, Lcom/miui/interfaces/shade/ShadeWrapper;->getContainer()Landroid/view/View;
    move-result-object v5
    invoke-virtual {v5, v2}, Landroid/view/View;->setTranslationX(F)V
    sget-boolean v5, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->DEBUG:Z
    if-eqz v5, :label_004a
    const-string v5, "ShadeSwitchController"
    const-string/jumbo v0, "translationX = "
    invoke-static {v2, v0, v5}, Lcom/android/keyguard/clock/animation/TinyClockBaseAnimation$$ExternalSyntheticOutline0;->m(FLjava/lang/String;Ljava/lang/String;)V
    :label_004a
    return-void
    nop
    :payload_004c_000d
    .packed-switch 0x0
        :label_001c
    .end packed-switch
    :payload_0052_002b
    .packed-switch 0x0
        :label_0031
    .end packed-switch
.end method
