.method public final getAllowParentInterceptSwitchEvent()Z
    .registers 3

    iget-object v2, v2, Lcom/miui/systemui/controlcenter/ControlCenterImpl;->eventHandlerImpl$delegate:Lkotlin/Lazy;
    invoke-interface {v2}, Lkotlin/Lazy;->getValue()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;
    iget-boolean v0, v2, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->handlingExpand:Z
    const/4 v1, 0x0
    if-nez v0, :label_0025
    iget-object v0, v2, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->startable:Lcom/miui/systemui/controlcenter/ControlCenterImpl;
    invoke-virtual {v0}, Lcom/miui/systemui/controlcenter/ControlCenterImpl;->getExpanded()Z
    move-result v0
    if-eqz v0, :label_0025
    iget-object v2, v2, Lcom/miui/systemui/controlcenter/container/ControlCenterEventHandlerImpl;->contentController:Lcom/miui/systemui/controlcenter/container/ControlCenterContentController;
    iget-object v2, v2, Lcom/miui/systemui/controlcenter/container/ControlCenterContentController;->content:Lcom/android/systemui/plugins/miui/controlcenter/ControlCenterContent;
    if-eqz v2, :label_0020
    invoke-interface {v2}, Lcom/android/systemui/plugins/miui/controlcenter/ControlCenterContent;->getAllowInterceptSwitchEvent()Z
    move-result v2
    goto :label_0021
    :label_0020
    move v2, v1
    :label_0021
    if-eqz v2, :label_0025
    const/4 v2, 0x1
    return v2
    :label_0025
    return v1
.end method
