package df.root;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

/**
 * Auto-root on boot (DirtyFragX UX + DFRoot s938b backend).
 *
 * Safety rules:
 *  - Expert mode must be on; older builds only had the BootReceiver component
 *    switch, so an absent pref is migrated from that switch.
 *  - Soft-reboot follows the expert "auto_soft_reboot" setting; default is on
 *    to preserve the previous s938b behaviour.
 *  - Three consecutive failures disable the receiver; a success resets the
 *    counter, so a single transient failure does not kill autorun.
 */
public class BootReceiver extends BroadcastReceiver implements IReporter {
    private static final String TAG = "dfroot";
    private static final int MAX_BOOT_FAILURES = 3;

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
    }

    /**
     * Expert mode is a new pref. Older builds only had the BootReceiver
     * component switch, so when the pref is absent migrate it from the
     * component state instead of silently disabling autorun after an update.
     */
    static boolean isExpertMode(Context context) {
        Context deCtx = context.createDeviceProtectedStorageContext();
        SharedPreferences sp = deCtx.getSharedPreferences("dfroot", Context.MODE_PRIVATE);
        if (sp.contains("expert_mode")) {
            return sp.getBoolean("expert_mode", false);
        }
        boolean enabled = context.getPackageManager().getComponentEnabledSetting(
                new ComponentName(context, BootReceiver.class))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        sp.edit().putBoolean("expert_mode", enabled).apply();
        Log.i(TAG, "boot: migrated expert_mode=" + enabled + " from receiver state");
        return enabled;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (new File("/dev/df").exists()) {
            Log.i(TAG, "boot: already hooked, skipping");
            return;
        }
        Log.i(TAG, "boot: " + intent.getAction());

        final Context deCtx = context.createDeviceProtectedStorageContext();
        if (!isExpertMode(context)) {
            Log.i(TAG, "boot: expert mode off - autorun skipped");
            return;
        }

        SharedPreferences sp = deCtx.getSharedPreferences("dfroot", Context.MODE_PRIVATE);
        final boolean autoSoftReboot = sp.getBoolean("auto_soft_reboot", true);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm != null
                ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dfroot:boot")
                : null;
        if (wl != null) wl.acquire(10 * 60 * 1000L);

        new Thread(() -> {
            boolean ok = false;
            try {
                int rc = ExploitRunner.run(deCtx, this, !autoSoftReboot);
                Log.i(TAG, "boot: exploit rc=" + rc + " autoSoftReboot=" + autoSoftReboot);
                ok = rc == 0;
            } catch (Exception e) {
                Log.e(TAG, "boot: exploit exception", e);
            } finally {
                if (wl != null && wl.isHeld()) wl.release();
            }

            if (ok) {
                sp.edit().putInt("boot_fail_count", 0).apply();
                return;
            }

            int failures = sp.getInt("boot_fail_count", 0) + 1;
            sp.edit().putInt("boot_fail_count", failures).apply();
            Log.i(TAG, "boot: failed (" + failures + "/" + MAX_BOOT_FAILURES + ")");
            if (failures >= MAX_BOOT_FAILURES) {
                Log.i(TAG, "boot: disabling autorun after repeated failures");
                context.getPackageManager().setComponentEnabledSetting(
                        new ComponentName(context, BootReceiver.class),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }, "dfroot-boot").start();
    }
}
