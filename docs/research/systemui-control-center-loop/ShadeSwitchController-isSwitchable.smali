.method public final isSwitchable()Z
    .registers 2

    iget-object v0, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->keyguardDelegate:Ldagger/Lazy;
    invoke-interface {v0}, Ldagger/Lazy;->get()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lcom/miui/keyguard/KeyguardDelegateImpl;
    iget-object v0, v0, Lcom/miui/keyguard/KeyguardDelegateImpl;->isKeyguardShowing:Lkotlinx/coroutines/flow/ReadonlyStateFlow;
    iget-object v0, v0, Lkotlinx/coroutines/flow/ReadonlyStateFlow;->$$delegate_0:Lkotlinx/coroutines/flow/StateFlow;
    invoke-interface {v0}, Lkotlinx/coroutines/flow/StateFlow;->getValue()Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Ljava/lang/Boolean;
    invoke-virtual {v0}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v0
    if-nez v0, :label_002c
    iget-object v1, v1, Lcom/miui/systemui/shade/ShadeSwitchControllerImpl;->startable:Lcom/miui/systemui/shade/ShadeStartable;
    iget-object v0, v1, Lcom/miui/systemui/shade/ShadeStartable;->notification:Lcom/miui/interfaces/shade/ShadeWrapper;
    invoke-interface {v0}, Lcom/miui/interfaces/shade/ShadeWrapper;->getSwitchable()Z
    move-result v0
    if-eqz v0, :label_002c
    iget-object v1, v1, Lcom/miui/systemui/shade/ShadeStartable;->controlCenter:Lcom/miui/interfaces/shade/ShadeWrapper;
    invoke-interface {v1}, Lcom/miui/interfaces/shade/ShadeWrapper;->getSwitchable()Z
    move-result v1
    if-eqz v1, :label_002c
    const/4 v1, 0x1
    return v1
    :label_002c
    const/4 v1, 0x0
    return v1
.end method
