.method public final calculateCtrlHotZone(FFZ)Lkotlin/Pair;
    .registers 4

    invoke-virtual {v0}, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->getCtrlHotZoneShare()F
    move-result v0
    if-eqz v3, :label_0016
    invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object v3
    mul-float/2addr v2, v0
    add-float/2addr v2, v1
    invoke-static {v2}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object v0
    new-instance v1, Lkotlin/Pair;
    invoke-direct {v1, v3, v0}, Lkotlin/Pair;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V
    return-object v1
    :label_0016
    const/high16 v3, 0x3f800000
    sub-float/2addr v3, v0
    mul-float/2addr v3, v2
    add-float/2addr v3, v1
    invoke-static {v3}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object v0
    add-float/2addr v1, v2
    invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object v1
    new-instance v2, Lkotlin/Pair;
    invoke-direct {v2, v0, v1}, Lkotlin/Pair;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V
    return-object v2
.end method
