@file:Suppress("DEPRECATION")

package io.github.a13e300.myinjector.arch

import android.content.Context
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceGroup
import android.preference.PreferenceManager
import android.preference.SwitchPreference
import android.view.View
import android.view.ViewGroup

class SwitchPreferenceCompat(context: Context) : SwitchPreference(context) {
    @Deprecated("Deprecated in Java")
    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {

    }
}

class PreferenceCompat(context: Context) : Preference(context) {
    override fun onCreateView(parent: ViewGroup?): View? {
        return super.onCreateView(parent).also { }
    }

    @Deprecated("Deprecated in Java")
    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {

    }
}

class PreferenceCategoryCompat(context: Context) : PreferenceCategory(context) {
    @Deprecated("Deprecated in Java")
    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {

    }
}


inline fun PreferenceGroup.switchPreference(
    title: String,
    key: String,
    summary: String? = null,
    config: SwitchPreference.() -> Unit = {}
) {
    addPreference(SwitchPreferenceCompat(context).also {
        it.title = title
        it.key = key
        it.summary = summary
        it.config()
    })
}

inline fun PreferenceGroup.preference(
    title: String,
    key: String,
    summary: String? = null,
    config: Preference.() -> Unit = {}
) {
    addPreference(PreferenceCompat(context).also {
        it.title = title
        it.key = key
        it.summary = summary
        it.config()
    })
}

inline fun PreferenceGroup.category(title: String, config: PreferenceCategory.() -> Unit) {
    addPreference(PreferenceCategoryCompat(context).also {
        it.title = title
        it.config()
    })
}
