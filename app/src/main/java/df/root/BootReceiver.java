package df.root;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

/**
 * Auto-root on boot (DirtyFragX UX + DFRoot s938b backend).
 *
 * Safety rules:
 *  - Expert mode must be on (DirtyFragX behaviour).
 *  - A failed run disables the receiver; the user re-enables it in the app.
 *  - Soft-reboot is always requested on boot ({@code skipSoftReboot=false}),
 *    matching the existing s938b BootReceiver.
 */
public class BootReceiver extends BroadcastReceiver implements IReporter {
    private static final String TAG = "dfroot";

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (new File("/dev/df").exists()) {
            Log.i(TAG, "boot: already hooked, skipping");
            return;
        }
        Log.i(TAG, "boot: " + intent.getAction());
        final Context deCtx = context.createDeviceProtectedStorageContext();
        boolean expert = deCtx.getSharedPreferences("dfroot", Context.MODE_PRIVATE)
                .getBoolean("expert_mode", false);
        if (!expert) {
            Log.i(TAG, "boot: expert mode off - autorun skipped");
            return;
        }

        boolean autoSoftReboot = deCtx.getSharedPreferences("dfroot", Context.MODE_PRIVATE)
                .getBoolean("auto_soft_reboot", false);
        // Mirror for any future bootstrap readers; JNI uses skipSoftReboot=false.
        deCtx.getSharedPreferences("dfroot", Context.MODE_PRIVATE)
                .edit().putBoolean("soft_reboot", autoSoftReboot).apply();

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm != null
                ? pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dfroot:boot")
                : null;
        if (wl != null) wl.acquire(10 * 60 * 1000L);

        new Thread(() -> {
            boolean ok = false;
            try {
                int rc = ExploitRunner.run(deCtx, this, false);
                Log.i(TAG, "boot: exploit rc=" + rc);
                ok = rc == 0;
            } catch (Exception e) {
                Log.e(TAG, "boot: exploit exception", e);
            } finally {
                if (wl != null && wl.isHeld()) wl.release();
            }
            if (!ok) {
                Log.i(TAG, "boot: failed - disabling autorun interlock");
                context.getPackageManager().setComponentEnabledSetting(
                        new ComponentName(context, BootReceiver.class),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        }, "dfroot-boot").start();
    }
}
