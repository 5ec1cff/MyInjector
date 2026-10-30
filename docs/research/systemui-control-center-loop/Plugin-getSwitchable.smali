.method public getSwitchable()Z
    .registers 3

    iget-object v0, v2, Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewController;->secondaryPanelRouter:Lmiui/systemui/controlcenter/panel/SecondaryPanelRouter;
    invoke-virtual {v0}, Lmiui/systemui/controlcenter/panel/SecondaryPanelRouter;->getInMainPanel()Z
    move-result v0
    if-eqz v0, :label_002c
    iget-object v0, v2, Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewController;->secondaryPanelRouter:Lmiui/systemui/controlcenter/panel/SecondaryPanelRouter;
    invoke-virtual {v0}, Lmiui/systemui/controlcenter/panel/SecondaryPanelRouter;->getInIdleState()Z
    move-result v0
    if-eqz v0, :label_002c
    iget-object v0, v2, Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewController;->mainPanelController:Lmiui/systemui/controlcenter/panel/main/MainPanelController;
    invoke-virtual {v0}, Lmiui/systemui/controlcenter/panel/main/MainPanelController;->getModeController()Lmiui/systemui/controlcenter/panel/main/MainPanelModeController;
    move-result-object v0
    invoke-virtual {v0}, Lmiui/systemui/controlcenter/panel/main/MainPanelModeController;->getMode()Lmiui/systemui/controlcenter/panel/main/MainPanelController$Mode;
    move-result-object v0
    sget-object v1, Lmiui/systemui/controlcenter/panel/main/MainPanelController$Mode;->EDIT:Lmiui/systemui/controlcenter/panel/main/MainPanelController$Mode;
    if-eq v0, v1, :label_002c
    iget-object v2, v2, Lmiui/systemui/controlcenter/windowview/ControlCenterWindowViewController;->mainPanelController:Lmiui/systemui/controlcenter/panel/main/MainPanelController;
    invoke-virtual {v2}, Lmiui/systemui/controlcenter/panel/main/MainPanelController;->getModeController()Lmiui/systemui/controlcenter/panel/main/MainPanelModeController;
    move-result-object v2
    invoke-virtual {v2}, Lmiui/systemui/controlcenter/panel/main/MainPanelModeController;->getInPendingEditMode()Z
    move-result v2
    if-nez v2, :label_002c
    const/4 v2, 0x1
    goto :label_002d
    :label_002c
    const/4 v2, 0x0
    :label_002d
    return v2
.end method
