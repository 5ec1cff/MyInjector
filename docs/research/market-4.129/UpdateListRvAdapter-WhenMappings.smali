.method static constructor <clinit>()V
    .registers 3

    invoke-static {}, Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;->values()[Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;
    move-result-object v0
    array-length v0, v0
    new-array v0, v0, [I
    :label_0007
    sget-object v1, Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;->Expand:Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;
    invoke-virtual {v1}, Ljava/lang/Enum;->ordinal()I
    move-result v1
    const/4 v2, 0x1
    aput v2, v0, v1
    :label_0010
    sget-object v1, Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;->Collapse:Lcom/xiaomi/market/ui/UpdateListRvAdapter$PageCollapseState;
    invoke-virtual {v1}, Ljava/lang/Enum;->ordinal()I
    move-result v1
    const/4 v2, 0x2
    aput v2, v0, v1
    :label_0019
    sput-object v0, Lcom/xiaomi/market/ui/UpdateListRvAdapter$WhenMappings;->$EnumSwitchMapping$0:[I
    return-void
    .catch Ljava/lang/NoSuchFieldError; {:label_0007 .. :label_0010} :label_0010
    .catch Ljava/lang/NoSuchFieldError; {:label_0010 .. :label_0019} :label_0019
.end method
