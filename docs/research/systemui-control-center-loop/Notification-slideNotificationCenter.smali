.method public final slideNotificationCenter(IZIZLcom/android/systemui/shade/MiuiNotificationPanelAnimControllerImpl$startSlideNotificationAnimation$1$1;)V
    .registers 13

    iget-object v0, v7, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;->folme:Lmiuix/animation/IStateStyle;
    if-nez v0, :label_0005
    return-void
    :label_0005
    mul-int/lit16 v1, v8, 0xc8
    add-int/lit16 v1, v1, 0xc8
    mul-int/2addr v1, v10
    int-to-float v1, v1
    const/high16 v2, 0x3f800000
    mul-float/2addr v1, v2
    new-instance v2, Lmiuix/animation/controller/AnimState;
    const-string/jumbo v3, "state_slide_out"
    invoke-direct {v2, v3}, Lmiuix/animation/controller/AnimState;-><init>(Ljava/lang/Object;)V
    sget-object v3, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;->SLIDE_TRANS_X:Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl$Companion$EXPAND_ALPHA$1;
    const/4 v4, 0x0
    new-array v4, v4, [J
    invoke-virtual {v2, v3, v1, v4}, Lmiuix/animation/controller/AnimState;->add(Lmiuix/animation/property/FloatProperty;F[J)Lmiuix/animation/controller/AnimState;
    move-result-object v2
    const/4 v3, 0x2
    new-array v3, v3, [F
    fill-array-data v3, :payload_00a2_0000
    const/4 v4, -0x2
    invoke-static {v4, v3}, Lmiuix/animation/utils/EaseManager;->getStyle(I[F)Lmiuix/animation/utils/EaseManager$EaseStyle;
    move-result-object v3
    invoke-virtual {v3}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
    new-instance v4, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl$slideNotificationCenter$listener$1;
    invoke-direct {v4, v7, v12}, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl$slideNotificationCenter$listener$1;-><init>(Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;Lcom/android/systemui/shade/ViewAnimatorListener;)V
    sget-boolean v12, Lcom/miui/utils/configs/MiuiDebugConfig;->DEBUG_PANEL_ANIM:Z
    if-eqz v12, :label_004d
    const-string v12, " ,triggerIn:"
    const-string v5, "  , direction:"
    const-string/jumbo v6, "slideNotificationCenter:  index:"
    invoke-static {v8, v6, v12, v5, v9}, Lcom/android/keyguard/ext/KeyguardUnlockAnimationControllerExtKt$$ExternalSyntheticOutline0;->m(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/StringBuilder;
    move-result-object v8
    const-string v12, ", withAnim:"
    const-string v5, ", x:"
    invoke-static {v8, v10, v12, v11, v5}, Lcom/android/keyguard/ota/KeyguardOTAInteractor$$ExternalSyntheticOutline0;->m(Ljava/lang/StringBuilder;ILjava/lang/String;ZLjava/lang/String;)V
    const-string v10, "MiuiPanelAnimValueObject"
    invoke-static {v1, v10, v8}, Lcom/android/keyguard/clock/animation/allinone/AllInOneClockAnimation$$ExternalSyntheticOutline0;->m(FLjava/lang/String;Ljava/lang/StringBuilder;)V
    :label_004d
    if-eqz v11, :label_0093
    const/4 v8, 0x1
    if-eqz v9, :label_0076
    sget-object v9, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;->STATE_SLIDE_IN:Lmiuix/animation/controller/AnimState;
    new-instance v10, Lmiuix/animation/base/AnimConfig;
    invoke-direct {v10}, Lmiuix/animation/base/AnimConfig;-><init>()V
    iget-object v7, v7, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;->slideTranXInEase:Lmiuix/animation/utils/EaseManager$EaseStyle;
    if-nez v7, :label_005e
    const/4 v7, 0x0
    :label_005e
    invoke-virtual {v10, v7}, Lmiuix/animation/base/AnimConfig;->setEase(Lmiuix/animation/utils/EaseManager$EaseStyle;)Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    invoke-virtual {v7, v8}, Lmiuix/animation/base/AnimConfig;->enableStartImmediately(Z)Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    filled-new-array {v4}, [Lmiuix/animation/listener/TransitionListener;
    move-result-object v8
    invoke-virtual {v7, v8}, Lmiuix/animation/base/AnimConfig;->addListeners([Lmiuix/animation/listener/TransitionListener;)Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    filled-new-array {v7}, [Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    invoke-interface {v0, v9, v7}, Lmiuix/animation/FolmeStyle;->to(Ljava/lang/Object;[Lmiuix/animation/base/AnimConfig;)Lmiuix/animation/IStateStyle;
    return-void
    :label_0076
    new-instance v7, Lmiuix/animation/base/AnimConfig;
    invoke-direct {v7}, Lmiuix/animation/base/AnimConfig;-><init>()V
    invoke-virtual {v7, v3}, Lmiuix/animation/base/AnimConfig;->setEase(Lmiuix/animation/utils/EaseManager$EaseStyle;)Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    invoke-virtual {v7, v8}, Lmiuix/animation/base/AnimConfig;->enableStartImmediately(Z)Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    filled-new-array {v4}, [Lmiuix/animation/listener/TransitionListener;
    move-result-object v8
    invoke-virtual {v7, v8}, Lmiuix/animation/base/AnimConfig;->addListeners([Lmiuix/animation/listener/TransitionListener;)Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    filled-new-array {v7}, [Lmiuix/animation/base/AnimConfig;
    move-result-object v7
    invoke-interface {v0, v2, v7}, Lmiuix/animation/FolmeStyle;->to(Ljava/lang/Object;[Lmiuix/animation/base/AnimConfig;)Lmiuix/animation/IStateStyle;
    return-void
    :label_0093
    if-eqz v9, :label_009b
    sget-object v8, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;->STATE_SLIDE_IN:Lmiuix/animation/controller/AnimState;
    invoke-interface {v0, v8}, Lmiuix/animation/FolmeStyle;->setTo(Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    goto :label_009e
    :label_009b
    invoke-interface {v0, v2}, Lmiuix/animation/FolmeStyle;->setTo(Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    :label_009e
    invoke-virtual {v7}, Lcom/android/systemui/shade/MiuiPanelAnimValueObjectImpl;->scheduleUpdate()V
    return-void
    :payload_00a2_0000
    .array-data 4
        0x3f733333
        0x3f19999a
    .end array-data
.end method
