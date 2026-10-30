.method public final onTouchEvent(Landroid/view/MotionEvent;)Z
    .registers 22

    move-object/from16 v0, v20
    move-object/from16 v1, v21
    iget-object v2, v0, Lcom/android/systemui/shade/NotificationShadeWindowView;->mInteractionEventHandler:Lcom/android/systemui/shade/NotificationShadeWindowViewController$1;
    iget-object v3, v2, Lcom/android/systemui/shade/NotificationShadeWindowViewController$1;->this$0:Lcom/android/systemui/shade/NotificationShadeWindowViewController;
    iget-object v4, v3, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mShadeTouchHandler:Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;
    iget-object v5, v3, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mShadeLogger:Lcom/android/systemui/shade/ShadeLogger;
    iget-object v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->startable:Lcom/miui/systemui/shade/ShadeStartable;
    invoke-virtual {v6}, Lcom/miui/systemui/shade/ShadeStartable;->getAnyPanelExpanded()Z
    move-result v6
    iput-boolean v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->shadeHandling:Z
    invoke-virtual {v4}, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->isShortcutInterceptTouchEvent()Z
    move-result v6
    if-eqz v6, :label_002e
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getAction()I
    move-result v6
    invoke-static {v6}, Landroid/view/MotionEvent;->actionToString(I)Ljava/lang/String;
    move-result-object v6
    const-string/jumbo v11, "onTouch, MiuiShortcutController interceptTouchEvent ev:"
    const-string v12, "MiuiShadeTouchHandler"
    invoke-static {v11, v6, v12}, Landroidx/fragment/app/FragmentManagerViewModel$$ExternalSyntheticOutline0;->m(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    sget-object v6, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    goto/16 :label_02c0
    :label_002e
    iget-object v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->dynamicIslandTouchHandler:Lcom/miui/systemui/notification/island/DynamicIslandTouchHandlerImpl;
    const-string v11, "notif_shade"
    invoke-virtual {v6, v1, v11}, Lcom/miui/systemui/notification/island/DynamicIslandTouchHandlerImpl;->onTouch(Landroid/view/MotionEvent;Ljava/lang/String;)Ljava/lang/Boolean;
    move-result-object v6
    if-eqz v6, :label_003a
    goto/16 :label_02c0
    :label_003a
    iget-object v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->shadeSwitchControllerImpl:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;
    iget-object v11, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startable:Lcom/miui/systemui/shade/ShadeStartable;
    iget-object v12, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->velocityTracker$delegate:Lkotlin/Lazy;
    iget-object v13, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->progressAnimator:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;
    sget-boolean v14, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->DEBUG:Z
    const-string v15, " "
    const-string v7, "ShadeSwitchController"
    if-eqz v14, :label_006b
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v10
    iget-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switchIntercepted:Z
    new-instance v9, Ljava/lang/StringBuilder;
    move-object/from16 v17, v12
    const-string/jumbo v12, "onSwitchTouch "
    invoke-direct {v9, v12}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v9, v10}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v9, v15}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v9, v8}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    invoke-virtual {v9}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v8
    invoke-static {v7, v8}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    goto :label_006d
    :label_006b
    move-object/from16 v17, v12
    :label_006d
    iget-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switchIntercepted:Z
    if-nez v8, :label_0074
    :label_0071
    const/4 v6, 0x0
    goto/16 :label_0209
    :label_0074
    invoke-virtual {v6}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->isSwitchable()Z
    move-result v8
    if-nez v8, :label_007b
    goto :label_0071
    :label_007b
    invoke-interface/range {v17 .. v17}, Lkotlin/Lazy;->getValue()Ljava/lang/Object;
    move-result-object v8
    check-cast v8, Landroid/view/VelocityTracker;
    invoke-virtual {v8, v1}, Landroid/view/VelocityTracker;->addMovement(Landroid/view/MotionEvent;)V
    iget-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->dragging:Z
    if-nez v8, :label_0124
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v8
    const/4 v9, 0x2
    if-ne v8, v9, :label_0124
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getClassification()I
    move-result v8
    iget v9, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->touchSlop:F
    const/4 v10, 0x1
    const/16 v18, 0x0
    if-ne v8, v10, :label_009d
    iget v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->slopMultiplier:F
    mul-float/2addr v9, v8
    :label_009d
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getRawX()F
    move-result v8
    iget v10, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->initX:F
    sub-float/2addr v8, v10
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getRawY()F
    move-result v10
    const/high16 v19, 0x3f800000
    iget v12, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->initY:F
    sub-float/2addr v10, v12
    invoke-static {v8}, Ljava/lang/Math;->abs(F)F
    move-result v12
    cmpl-float v9, v12, v9
    if-lez v9, :label_0128
    invoke-static {v8}, Ljava/lang/Math;->abs(F)F
    move-result v8
    invoke-static {v10}, Ljava/lang/Math;->abs(F)F
    move-result v9
    cmpl-float v8, v8, v9
    if-lez v8, :label_0128
    const/4 v10, 0x1
    iput-boolean v10, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->dragging:Z
    if-eqz v14, :label_00cd
    iget v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startX:F
    const-string v9, "initing start x "
    invoke-static {v8, v9, v7}, Lcom/android/keyguard/clock/animation/TinyClockBaseAnimation$$ExternalSyntheticOutline0;->m(FLjava/lang/String;Ljava/lang/String;)V
    :label_00cd
    iget-object v8, v13, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->anim:Lmiuix/animation/IStateStyle;
    invoke-interface {v8}, Lmiuix/animation/ICancelableStyle;->cancel()V
    iget-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switching:Z
    if-eqz v8, :label_00e1
    iget v8, v13, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->_progress:F
    iput v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->remainProgress:F
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getRawX()F
    move-result v8
    iput v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startX:F
    goto :label_0128
    :label_00e1
    iget-object v8, v11, Lcom/miui/systemui/shade/ShadeStartable;->controlCenter:Lcom/miui/interfaces/shade/ShadeWrapper;
    invoke-interface {v8}, Lcom/miui/interfaces/shade/ShadeWrapper;->getExpanded()Z
    move-result v8
    iget-object v9, v11, Lcom/miui/systemui/shade/ShadeStartable;->notification:Lcom/miui/interfaces/shade/ShadeWrapper;
    invoke-interface {v9}, Lcom/miui/interfaces/shade/ShadeWrapper;->getExpanded()Z
    move-result v9
    if-eqz v14, :label_00f5
    const-string/jumbo v10, "start switching "
    invoke-static {v10, v15, v7, v8, v9}, Lcom/android/keyguard/WallpaperProvider$$ExternalSyntheticOutline0;->m(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)V
    :label_00f5
    if-eqz v8, :label_0100
    if-eqz v9, :label_0100
    const-string v8, "both panel expanded when start dragging."
    invoke-static {v7, v8}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    :label_00fe
    const/4 v8, 0x1
    goto :label_010d
    :label_0100
    if-eqz v8, :label_0103
    goto :label_00fe
    :label_0103
    if-eqz v9, :label_0107
    const/4 v8, 0x0
    goto :label_010d
    :label_0107
    const-string v8, "both panel not expanded when start dragging."
    invoke-static {v7, v8}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I
    goto :label_00fe
    :label_010d
    iput-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->fromCtrl:Z
    if-eqz v8, :label_0114
    move/from16 v8, v18
    goto :label_0116
    :label_0114
    move/from16 v8, v19
    :label_0116
    iput v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->remainProgress:F
    invoke-virtual {v13, v8}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->setProgress(F)V
    const/4 v10, 0x1
    invoke-virtual {v6, v10}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->setSwitching(Z)V
    const/high16 v8, 0x7fc00000
    iput v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startX:F
    goto :label_0128
    :label_0124
    const/16 v18, 0x0
    const/high16 v19, 0x3f800000
    :label_0128
    iget-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->dragging:Z
    if-eqz v8, :label_0207
    iget v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startX:F
    invoke-static {v8}, Ljava/lang/Float;->isNaN(F)Z
    move-result v8
    if-eqz v8, :label_013a
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getRawX()F
    move-result v8
    iput v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startX:F
    :label_013a
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getRawX()F
    move-result v8
    iget v9, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startX:F
    sub-float/2addr v8, v9
    if-eqz v14, :label_015a
    new-instance v10, Ljava/lang/StringBuilder;
    const-string v11, "dragging trans x "
    invoke-direct {v10, v11}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v10, v8}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
    invoke-virtual {v10, v15}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v10, v9}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;
    invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v9
    invoke-static {v7, v9}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :label_015a
    iget v9, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->remainProgress:F
    iget v10, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->switchDistance:F
    div-float/2addr v8, v10
    add-float/2addr v8, v9
    cmpl-float v9, v8, v19
    if-lez v9, :label_016f
    sub-float v12, v19, v8
    move/from16 v9, v19
    invoke-static {v12, v9}, Lmiuix/animation/Folme;->afterFrictionValue(FF)F
    move-result v10
    sub-float v12, v9, v10
    goto :label_017b
    :label_016f
    move/from16 v9, v19
    cmpg-float v10, v8, v18
    if-gez v10, :label_017a
    invoke-static {v8, v9}, Lmiuix/animation/Folme;->afterFrictionValue(FF)F
    move-result v12
    goto :label_017b
    :label_017a
    move v12, v8
    :label_017b
    invoke-virtual {v13, v12}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->setProgress(F)V
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v10
    const/4 v11, 0x1
    if-eq v10, v11, :label_018a
    const/4 v11, 0x3
    if-eq v10, v11, :label_018a
    goto/16 :label_0207
    :label_018a
    invoke-interface/range {v17 .. v17}, Lkotlin/Lazy;->getValue()Ljava/lang/Object;
    move-result-object v10
    check-cast v10, Landroid/view/VelocityTracker;
    const/16 v11, 0x3e8
    invoke-virtual {v10, v11}, Landroid/view/VelocityTracker;->computeCurrentVelocity(I)V
    if-eqz v14, :label_01a7
    invoke-interface/range {v17 .. v17}, Lkotlin/Lazy;->getValue()Ljava/lang/Object;
    move-result-object v10
    check-cast v10, Landroid/view/VelocityTracker;
    invoke-virtual {v10}, Landroid/view/VelocityTracker;->getXVelocity()F
    move-result v10
    const-string/jumbo v11, "velocity on up "
    invoke-static {v10, v11, v7}, Lcom/android/keyguard/clock/animation/TinyClockBaseAnimation$$ExternalSyntheticOutline0;->m(FLjava/lang/String;Ljava/lang/String;)V
    :label_01a7
    const/4 v7, 0x0
    iput-boolean v7, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->dragging:Z
    invoke-interface/range {v17 .. v17}, Lkotlin/Lazy;->getValue()Ljava/lang/Object;
    move-result-object v7
    check-cast v7, Landroid/view/VelocityTracker;
    invoke-virtual {v7}, Landroid/view/VelocityTracker;->getXVelocity()F
    move-result v7
    invoke-static {v7}, Ljava/lang/Math;->abs(F)F
    move-result v10
    const/high16 v11, 0x447a0000
    cmpl-float v10, v10, v11
    if-lez v10, :label_01c0
    const/4 v10, 0x1
    goto :label_01c1
    :label_01c0
    const/4 v10, 0x0
    :label_01c1
    if-eqz v10, :label_01d3
    iget-object v11, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->context:Landroid/content/Context;
    invoke-static {v11}, Landroidx/appcompat/widget/MenuPopupWindow$$ExternalSyntheticOutline0;->m(Landroid/content/Context;)I
    move-result v11
    const/4 v12, 0x1
    if-ne v11, v12, :label_01d4
    cmpl-float v7, v7, v18
    if-lez v7, :label_01d1
    goto :label_01ec
    :label_01d1
    const/4 v12, 0x0
    goto :label_01ec
    :label_01d3
    const/4 v12, 0x1
    :label_01d4
    if-eqz v10, :label_01e5
    iget-object v10, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->context:Landroid/content/Context;
    invoke-static {v10}, Landroidx/appcompat/widget/MenuPopupWindow$$ExternalSyntheticOutline0;->m(Landroid/content/Context;)I
    move-result v10
    if-ne v10, v12, :label_01df
    goto :label_01e5
    :label_01df
    cmpg-float v7, v7, v18
    if-gez v7, :label_01d1
    :label_01e3
    const/4 v12, 0x1
    goto :label_01ec
    :label_01e5
    const/high16 v7, 0x3f000000
    cmpg-float v7, v8, v7
    if-gez v7, :label_01d1
    goto :label_01e3
    :label_01ec
    iput-boolean v12, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->toCtrl:Z
    if-eqz v12, :label_01f3
    move/from16 v10, v18
    goto :label_01f4
    :label_01f3
    move v10, v9
    :label_01f4
    invoke-virtual {v13, v10}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$progressAnimator$1;->setProgress(F)V
    iget-object v7, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->notification:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;
    iget-boolean v8, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->toCtrl:Z
    const/4 v10, 0x1
    xor-int/2addr v8, v10
    invoke-virtual {v7, v8, v10}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->setTriggerAppeared(ZZ)V
    iget-object v7, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->controlCenter:Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;
    iget-boolean v6, v6, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->toCtrl:Z
    invoke-virtual {v7, v6, v10}, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl$notification$1;->setTriggerAppeared(ZZ)V
    :label_0207
    sget-object v6, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    :label_0209
    if-eqz v6, :label_020d
    goto/16 :label_02c0
    :label_020d
    iget-object v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->controlCenterEventHandlerImpl:Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;
    iget-object v7, v6, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->controlCenterSettingsController:Lcom/android/systemui/controlcenter/policy/ControlCenterControllerImpl;
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v8
    iget-boolean v9, v6, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->handlingExpand:Z
    new-instance v10, Ljava/lang/StringBuilder;
    const-string v11, "handleTouchEvent "
    invoke-direct {v10, v11}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v10, v8}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v10, v15}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v10, v9}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    const-string v8, ", sources:notif_shade"
    invoke-virtual {v10, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v10}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v8
    const-string v9, "ControlCenterEventHandler"
    invoke-static {v9, v8}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    iget-object v8, v6, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->contentController:Lcom/miui/systemui/controlcenter/container/ControlCenterContentController;
    iget-object v8, v8, Lcom/miui/systemui/controlcenter/container/ControlCenterContentController;->content:Lcom/android/systemui/plugins/miui/controlcenter/ControlCenterContent;
    if-eqz v8, :label_023d
    const/4 v8, 0x1
    goto :label_023e
    :label_023d
    const/4 v8, 0x0
    :label_023e
    invoke-virtual {v7}, Lcom/android/systemui/controlcenter/policy/ControlCenterControllerImpl;->isUseControlCenter()Z
    move-result v10
    invoke-virtual {v7}, Lcom/android/systemui/controlcenter/policy/ControlCenterControllerImpl;->isPanelEnabled()Z
    move-result v7
    if-eqz v8, :label_0277
    if-eqz v10, :label_0277
    if-nez v7, :label_024d
    goto :label_0277
    :label_024d
    iget-boolean v7, v6, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->handlingExpand:Z
    if-eqz v7, :label_0275
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v7
    if-eqz v7, :label_0272
    const/4 v10, 0x1
    if-eq v7, v10, :label_0269
    const/4 v11, 0x3
    if-eq v7, v11, :label_0269
    const/4 v8, 0x4
    if-eq v7, v8, :label_0266
    invoke-virtual {v6, v1}, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->dispatchTouchEvent(Landroid/view/MotionEvent;)V
    sget-object v6, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    goto :label_0294
    :label_0266
    sget-object v6, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    goto :label_0294
    :label_0269
    invoke-virtual {v6, v1}, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->dispatchTouchEvent(Landroid/view/MotionEvent;)V
    const/4 v7, 0x0
    iput-boolean v7, v6, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->handlingExpand:Z
    sget-object v6, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    goto :label_0294
    :label_0272
    sget-object v6, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
    goto :label_0294
    :label_0275
    const/4 v6, 0x0
    goto :label_0294
    :label_0277
    const-string v11, ", useControlCenter:"
    const-string v12, ", panelEnabled:"
    const-string v13, "handleTouchEvent, hasContent:"
    invoke-static {v13, v11, v12, v8, v10}, Landroidx/compose/material3/internal/TextFieldImplKt$$ExternalSyntheticOutline0;->m(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZ)Ljava/lang/StringBuilder;
    move-result-object v8
    invoke-virtual {v8, v7}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;
    const-string v7, ", reset handle flag"
    invoke-virtual {v8, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    invoke-virtual {v8}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v7
    invoke-static {v9, v7}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    const/4 v7, 0x0
    iput-boolean v7, v6, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->handlingExpand:Z
    goto :label_0275
    :label_0294
    if-eqz v6, :label_0297
    goto :label_02c0
    :label_0297
    iget-boolean v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->notificationIntercept:Z
    if-eqz v6, :label_02bf
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v6
    const/4 v10, 0x1
    if-eq v6, v10, :label_02a9
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v6
    const/4 v11, 0x3
    if-ne v6, v11, :label_02ac
    :label_02a9
    const/4 v7, 0x0
    iput-boolean v7, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->notificationIntercept:Z
    :label_02ac
    iget-object v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->notificationTouchHandler:Lcom/android/systemui/shade/NotificationPanelViewControllerInjector;
    invoke-virtual {v6}, Lcom/android/systemui/shade/NotificationPanelViewControllerInjector;->getPanelViewController()Lcom/android/systemui/shade/NotificationPanelViewController;
    move-result-object v6
    invoke-virtual {v6}, Lcom/android/systemui/shade/NotificationPanelViewController;->getTouchHandler()Lcom/android/systemui/shade/NotificationPanelViewController$TouchHandler;
    move-result-object v6
    invoke-virtual {v6, v1}, Lcom/android/systemui/shade/NotificationPanelViewController$TouchHandler;->onTouchEvent(Landroid/view/MotionEvent;)Z
    move-result v6
    invoke-static {v6}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
    move-result-object v6
    goto :label_02c0
    :label_02bf
    const/4 v6, 0x0
    :label_02c0
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v7
    const/4 v10, 0x1
    if-eq v7, v10, :label_02d5
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v7
    const/4 v11, 0x3
    if-eq v7, v11, :label_02d5
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v7
    const/4 v8, 0x4
    if-ne v7, v8, :label_02d8
    :label_02d5
    const/4 v7, 0x0
    iput-boolean v7, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->shadeHandling:Z
    :label_02d8
    if-eqz v6, :label_02f1
    new-instance v2, Ljava/lang/StringBuilder;
    const-string v3, "NSWVC handleTouchEvent miuiHandled = "
    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v2, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;
    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v2
    invoke-virtual {v5, v2}, Lcom/android/systemui/shade/ShadeLogger;->d(Ljava/lang/String;)V
    invoke-virtual {v6}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v2
    move v10, v2
    goto :label_0354
    :label_02f1
    iget-object v6, v3, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mStatusBarStateController:Lcom/android/systemui/statusbar/SysuiStatusBarStateController;
    invoke-interface {v6}, Lcom/android/systemui/plugins/statusbar/StatusBarStateController;->isDozing()Z
    move-result v6
    if-eqz v6, :label_0302
    iget-object v6, v3, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mDozeServiceHost:Lcom/android/systemui/statusbar/phone/DozeServiceHost;
    iget-boolean v6, v6, Lcom/android/systemui/statusbar/phone/DozeServiceHost;->mPulsing:Z
    const/16 v16, 0x1
    xor-int/lit8 v7, v6, 0x1
    goto :label_0303
    :label_0302
    const/4 v7, 0x0
    :label_0303
    iget-object v6, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->tinyNotificationTouchHandler:Lcom/android/notification/tinypanel/TinyLockscreenTransitionController;
    invoke-interface {v6}, Lcom/android/notification/tinypanel/TinyLockscreenTransitionController;->isDraggingDown()Z
    move-result v8
    if-nez v8, :label_034e
    invoke-interface {v6}, Lcom/android/notification/tinypanel/TinyLockscreenTransitionController;->isDragDownEnabled()Z
    move-result v6
    if-eqz v6, :label_0312
    goto :label_034e
    :label_0312
    iget-boolean v2, v2, Lcom/android/systemui/shade/NotificationShadeWindowViewController$1;->mLastInterceptWasDragDownHelper:Z
    if-eqz v2, :label_032c
    iget-object v2, v3, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mDragDownHelper:Lcom/android/systemui/statusbar/DragDownHelper;
    iget-boolean v4, v2, Lcom/android/systemui/statusbar/DragDownHelper;->isDraggingDown:Z
    if-eqz v4, :label_032c
    invoke-virtual {v2, v1}, Lcom/android/systemui/statusbar/DragDownHelper;->onTouchEvent(Landroid/view/MotionEvent;)Z
    move-result v2
    if-nez v2, :label_0327
    if-eqz v7, :label_0325
    goto :label_0327
    :label_0325
    const/4 v10, 0x0
    goto :label_0328
    :label_0327
    const/4 v10, 0x1
    :label_0328
    or-int v2, v7, v10
    move v10, v2
    goto :label_032d
    :label_032c
    move v10, v7
    :label_032d
    if-nez v10, :label_0354
    iget-object v2, v3, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mShadeViewController:Lcom/android/systemui/shade/ShadeViewController;
    invoke-interface {v2, v1}, Lcom/android/systemui/shade/ShadeViewController;->handleShadeTouch(Landroid/view/MotionEvent;)Z
    move-result v2
    if-eqz v2, :label_0354
    new-instance v2, Ljava/lang/StringBuilder;
    const-string v3, "NPVC handleShadeTouch action:"
    invoke-direct {v2, v3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v3
    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v2
    invoke-virtual {v5, v2}, Lcom/android/systemui/shade/ShadeLogger;->d(Ljava/lang/String;)V
    const/4 v10, 0x1
    goto :label_0354
    :label_034e
    iget-object v2, v4, Lcom/miui/systemui/shade/MiuiShadeTouchHandlerImpl;->tinyNotificationTouchHandler:Lcom/android/notification/tinypanel/TinyLockscreenTransitionController;
    invoke-interface {v2, v1, v7}, Lcom/android/notification/tinypanel/TinyLockscreenTransitionController;->onDragDownTouchEvent(Landroid/view/MotionEvent;Z)Z
    move-result v10
    :label_0354
    if-nez v10, :label_035a
    invoke-super/range {v20 .. v21}, Landroid/widget/FrameLayout;->onTouchEvent(Landroid/view/MotionEvent;)Z
    move-result v10
    :label_035a
    if-nez v10, :label_0375
    iget-object v0, v0, Lcom/android/systemui/shade/NotificationShadeWindowView;->mInteractionEventHandler:Lcom/android/systemui/shade/NotificationShadeWindowViewController$1;
    invoke-virtual {v0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;
    invoke-virtual {v1}, Landroid/view/MotionEvent;->getActionMasked()I
    move-result v1
    const/4 v11, 0x1
    if-eq v1, v11, :label_036b
    const/4 v2, 0x3
    if-ne v1, v2, :label_0375
    :label_036b
    iget-object v0, v0, Lcom/android/systemui/shade/NotificationShadeWindowViewController$1;->this$0:Lcom/android/systemui/shade/NotificationShadeWindowViewController;
    iget-object v0, v0, Lcom/android/systemui/shade/NotificationShadeWindowViewController;->mService:Lcom/android/systemui/statusbar/phone/CentralSurfaces;
    check-cast v0, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;
    const/4 v7, 0x0
    invoke-virtual {v0, v11, v7}, Lcom/android/systemui/statusbar/phone/CentralSurfacesImpl;->setInteracting(IZ)V
    :label_0375
    return v10
.end method
