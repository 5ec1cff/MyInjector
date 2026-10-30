.method public final slideControlCenter(ZIZ)V
    .registers 15

    const/4 v0, 0x0
    invoke-static {v0}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object v0
    const/4 v1, 0x0
    const/4 v2, 0x1
    if-eqz v14, :label_0081
    iget-object v14, v11, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->animators:[Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;
    array-length v3, v14
    move v4, v1
    :label_000d
    if-ge v4, v3, :label_0016
    aget-object v5, v14, v4
    invoke-virtual {v5, v2}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->setSlideTransXAnimating(Z)V
    add-int/2addr v4, v2
    goto :label_000d
    :label_0016
    iget-object v14, v11, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->animators:[Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;
    array-length v3, v14
    move v4, v1
    move v5, v4
    :label_001b
    if-ge v4, v3, :label_00c0
    aget-object v6, v14, v4
    add-int/lit8 v7, v5, 0x1
    invoke-virtual {v6}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getRow()I
    move-result v8
    mul-int/lit16 v8, v8, 0xc8
    mul-int/2addr v8, v13
    invoke-virtual {v11}, Lmiui/systemui/util/ViewController;->getContext()Landroid/content/Context;
    move-result-object v9
    invoke-static {v9}, Lmiui/systemui/util/CommonUtils;->isLayoutRtl(Landroid/content/Context;)Z
    move-result v9
    if-eqz v9, :label_0034
    const/4 v9, -0x1
    goto :label_0035
    :label_0034
    move v9, v2
    :label_0035
    mul-int/2addr v8, v9
    if-eqz v12, :label_0071
    invoke-virtual {v6}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getAnim()Lmiuix/animation/IStateStyle;
    move-result-object v9
    sget-object v10, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->SLIDE_TRANS_X:Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Companion$SLIDE_TRANS_X$1;
    invoke-static {v8}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v8
    filled-new-array {v10, v8}, [Ljava/lang/Object;
    move-result-object v8
    invoke-interface {v9, v8}, Lmiuix/animation/FolmeStyle;->setTo([Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    move-result-object v8
    invoke-virtual {v6}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getSlideTransXConfig()Lmiuix/animation/base/AnimConfig;
    move-result-object v9
    invoke-virtual {v6}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getSlideTransXInEase()Lmiuix/animation/utils/EaseManager$EaseStyle;
    move-result-object v6
    invoke-virtual {v9, v6}, Lmiuix/animation/base/AnimConfig;->setEase(Lmiuix/animation/utils/EaseManager$EaseStyle;)Lmiuix/animation/base/AnimConfig;
    move-result-object v6
    if-eqz v5, :label_005d
    const/16 v9, 0x9
    if-eq v5, v9, :label_005d
    goto :label_0069
    :label_005d
    new-instance v9, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$slideControlCenter$2$1$1;
    invoke-direct {v9, v5, v6}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$slideControlCenter$2$1$1;-><init>(ILmiuix/animation/base/AnimConfig;)V
    new-array v5, v2, [Lmiuix/animation/listener/TransitionListener;
    aput-object v9, v5, v1
    invoke-virtual {v6, v5}, Lmiuix/animation/base/AnimConfig;->addListeners([Lmiuix/animation/listener/TransitionListener;)Lmiuix/animation/base/AnimConfig;
    :label_0069
    filled-new-array {v10, v0, v6}, [Ljava/lang/Object;
    move-result-object v5
    invoke-interface {v8, v5}, Lmiuix/animation/FolmeStyle;->to([Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    goto :label_007e
    :label_0071
    invoke-virtual {v6}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getAnim()Lmiuix/animation/IStateStyle;
    move-result-object v5
    sget-object v6, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->SLIDE_TRANS_X:Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Companion$SLIDE_TRANS_X$1;
    filled-new-array {v6, v0}, [Ljava/lang/Object;
    move-result-object v6
    invoke-interface {v5, v6}, Lmiuix/animation/FolmeStyle;->setTo([Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    :label_007e
    add-int/2addr v4, v2
    move v5, v7
    goto :label_001b
    :label_0081
    iget-object v14, v11, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->animators:[Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;
    array-length v3, v14
    move v4, v1
    :label_0085
    if-ge v4, v3, :label_00bd
    aget-object v5, v14, v4
    invoke-virtual {v5, v1}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->setSlideTransXAnimating(Z)V
    invoke-virtual {v5}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getAnim()Lmiuix/animation/IStateStyle;
    move-result-object v6
    invoke-interface {v6}, Lmiuix/animation/ICancelableStyle;->cancel()V
    invoke-virtual {v5}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getRow()I
    move-result v6
    mul-int/lit16 v6, v6, 0xc8
    mul-int/2addr v6, v13
    if-eqz v12, :label_00aa
    invoke-virtual {v5}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getAnim()Lmiuix/animation/IStateStyle;
    move-result-object v5
    sget-object v6, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->SLIDE_TRANS_X:Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Companion$SLIDE_TRANS_X$1;
    filled-new-array {v6, v0}, [Ljava/lang/Object;
    move-result-object v6
    invoke-interface {v5, v6}, Lmiuix/animation/FolmeStyle;->setTo([Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    goto :label_00bb
    :label_00aa
    invoke-virtual {v5}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Row;->getAnim()Lmiuix/animation/IStateStyle;
    move-result-object v5
    sget-object v7, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->SLIDE_TRANS_X:Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator$Companion$SLIDE_TRANS_X$1;
    invoke-static {v6}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;
    move-result-object v6
    filled-new-array {v7, v6}, [Ljava/lang/Object;
    move-result-object v6
    invoke-interface {v5, v6}, Lmiuix/animation/FolmeStyle;->setTo([Ljava/lang/Object;)Lmiuix/animation/IStateStyle;
    :label_00bb
    add-int/2addr v4, v2
    goto :label_0085
    :label_00bd
    invoke-virtual {v11, v2}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->onFrameCallback(Z)V
    :label_00c0
    invoke-direct {v11}, Lmiui/systemui/controlcenter/panel/main/anim/SpreadRowsAnimator;->adjustLifecycleState()V
    return-void
.end method
