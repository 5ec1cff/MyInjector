package io.github.a13e300.myinjector.system_server

internal class XSpaceWhitelist(
    private val callingPackages: MutableList<String>,
    private val publicActions: MutableList<String>,
) {
    private var savedPublicActions: List<String>? = null
    private var addedCallingPackages = emptyList<String>()

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (savedPublicActions != null) return
            savedPublicActions = publicActions.toList()
            addedCallingPackages = listOf("com.android.shell", "com.xiaomi.xmsf")
                .filterNot { it in callingPackages }
            callingPackages.addAll(addedCallingPackages)
            publicActions.clear()
        } else {
            val actions = savedPublicActions ?: return
            // Only remove packages added by this module.
            addedCallingPackages.forEach { callingPackages.remove(it) }
            publicActions.addAll(actions)
            savedPublicActions = null
            addedCallingPackages = emptyList()
        }
    }
}
