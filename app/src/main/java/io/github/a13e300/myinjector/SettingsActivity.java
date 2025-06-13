package io.github.a13e300.myinjector;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new SettingsFragment()).commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesName("system_server");
            addPreferencesFromResource(R.xml.prefs);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            view.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                Insets insets = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets = windowInsets.getInsets(WindowInsets.Type.systemBars());
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    insets = windowInsets.getSystemWindowInsets();
                }
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    mlp.leftMargin = insets.left;
                    mlp.bottomMargin = insets.bottom;
                    mlp.rightMargin = insets.right;
                    mlp.topMargin = insets.top;
                } else {
                    mlp.leftMargin = windowInsets.getSystemWindowInsetLeft();
                    mlp.bottomMargin = windowInsets.getSystemWindowInsetBottom();
                    mlp.rightMargin = windowInsets.getSystemWindowInsetRight();
                    mlp.topMargin = windowInsets.getSystemWindowInsetTop();
                }
                v.setLayoutParams(mlp);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return WindowInsets.CONSUMED;
                } else return windowInsets.consumeSystemWindowInsets();
            });
            super.onViewCreated(view, savedInstanceState);
            findPreference("commit").setOnPreferenceClickListener((x) -> {
                commitSystem();
                return true;
            });
            findPreference("commitSysUI").setOnPreferenceClickListener((x) -> {
                commitSystemUI();
                return true;
            });
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            var p = findPreference(key);
            if (p == null) return;
            var pr = p.getParent();
            if (pr == null) return;
            if ("system".equals(pr.getKey())) {
                commitSystem();
            } else if ("systemui".equals(pr.getKey())) {
                commitSystemUI();
            }
        }

        private void commitSystem() {
            var context = getContext();
            var intent = new Intent("io.github.a13e300.myinjector.UPDATE_SYSTEM_CONFIG");
            var pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
            var sp = getPreferenceManager().getSharedPreferences();
            var config = SystemServerConfig.newBuilder()
                    .setNoWakePath(sp.getBoolean("noWakePath", false))
                    .setNoMiuiIntent(sp.getBoolean("noMiuiIntent", false))
                    .setClipboardWhitelist(sp.getBoolean("clipboardWhitelist", false))
                    .setFixSync(sp.getBoolean("fixSync", false))
                    .setXSpace(sp.getBoolean("xSpace", false))
                    .addAllClipboardWhitelistPackages(Arrays.stream(sp.getString("clipboardWhitelistPackages", "").trim().split("\n")).collect(Collectors.toList()))
                    .setForceNewTask(sp.getBoolean("forceNewTask", false))
                    .addAllForceNewTaskRules(Arrays.stream(sp.getString("forceNewTaskRules", "").trim().split("\n")).map(
                            x -> {
                                var parts = x.split(":");
                                boolean ignoreResult = false;
                                boolean useNewDoc = false;
                                if (parts.length == 3) {
                                    var options = parts[2].split(",");
                                    for (var o : options) {
                                        if ("ir".equals(o)) {
                                            ignoreResult = true;
                                        } else if ("nd".equals(o)) {
                                            useNewDoc = true;
                                        }
                                    }
                                } else if (parts.length != 2) return null;
                                var sourcePackage = parts[0];
                                var targetPackage = parts[1];
                                var sourceComponent = "";
                                var targetComponent = "";
                                if (sourcePackage.contains("/")) {
                                    var l = sourcePackage.split("/");
                                    if (l.length == 2) {
                                        sourcePackage = l[0];
                                        sourceComponent = l[1];
                                        if (sourceComponent.startsWith("."))
                                            sourceComponent = l[0] + l[1];
                                    }
                                }
                                if (targetPackage.contains("/")) {
                                    var l = targetPackage.split("/");
                                    if (l.length == 2) {
                                        targetPackage = l[0];
                                        targetComponent = l[1];
                                        if (targetComponent.startsWith("."))
                                            targetComponent = l[0] + l[1];
                                    }
                                }
                                return NewTaskRule.newBuilder()
                                        .setSourcePackage(sourcePackage)
                                        .setTargetPackage(targetPackage)
                                        .setSourceComponent(sourceComponent)
                                        .setTargetComponent(targetComponent)
                                        .setUseNewDocument(useNewDoc)
                                        .setIgnoreResult(ignoreResult)
                                        .build();
                            }
                    ).filter(Objects::nonNull).collect(Collectors.toList()))
                    .setOverrideStatusBar(sp.getBoolean("overrideStatusBar", false))
                    .addAllOverrideStatusBarRules(
                            Arrays.stream(sp.getString("overrideStatusBarRules", "")
                                    .trim()
                                    .split("\n")
                            ).map(x -> {
                                var parts = x.split(":");
                                if (parts.length != 2) return null;
                                var pkg = parts[0];
                                var light = false;
                                if ("light".equals(parts[1])) {
                                    light = true;
                                } else if (!"dark".equals(parts[2])) {
                                    return null;
                                }
                                var component = "";
                                var pkgSplit = pkg.split("/");
                                if (pkgSplit.length == 2) {
                                    pkg = pkgSplit[0];
                                    component = pkgSplit[1];
                                    if (component.startsWith(".")) component = pkg + component;
                                } else if (pkgSplit.length != 1) return null;
                                return OverrideStatusBarRule.newBuilder()
                                        .setPackage(pkg)
                                        .setComponent(component)
                                        .setLight(light)
                                        .build();
                            }).filter(Objects::nonNull).collect(Collectors.toList())
                    )
                    .setForceNewTaskDebug(sp.getBoolean("forceNewTaskDebug", false))
                    .build();
            intent.putExtra("EXTRA_CREDENTIAL", pendingIntent);
            intent.putExtra("EXTRA_CONFIG", config.toByteArray());
            context.sendBroadcast(intent);
        }

        private void commitSystemUI() {
            var context = getContext();
            var intent = new Intent("io.github.a13e300.myinjector.UPDATE_SYSTEMUI_CONFIG");
            var pendingIntent = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE);
            var sp = getPreferenceManager().getSharedPreferences();
            var config = SystemUIConfig.newBuilder()
                    .setAlwaysExpandNotification(sp.getBoolean("alwaysExpandNotification", false))
                    .setNoDndNotification(sp.getBoolean("noDndNotification", false))
                    .setShowNotificationDetail(sp.getBoolean("showNotificationDetail", false))
                    .build();
            intent.putExtra("EXTRA_CREDENTIAL", pendingIntent);
            intent.putExtra("EXTRA_CONFIG", config.toByteArray());
            context.sendBroadcast(intent);
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }
}
