package df.root;

import android.content.ComponentName;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import df.root.databinding.ActivityMainBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements IReporter {

    private static final String TAG = "dfroot";

    static { System.loadLibrary("exp"); }

    private ActivityMainBinding binding;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();

    public void report(String msg) {
        mMain.post(() -> {
            binding.outputPlaceholder.setVisibility(View.GONE);
            binding.outputView.append(msg);
            binding.outputScroll.post(() -> binding.outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    static native int nativeRunAll(IReporter reporter, int encapPort, int spi,
                                    byte[] aesCbcKey, byte[] hmacKey, int icvLen,
                                    int senderPort, String ksudPath, boolean skipSoftReboot);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyInsets();
        applySystemBarAppearance();
        populateDeviceInfo();
        refreshRunState();
        showPage(savedInstanceState != null && savedInstanceState.getBoolean("settingsPage"));

        binding.navHome.setOnClickListener(v -> showPage(false));
        binding.navSettings.setOnClickListener(v -> showPage(true));
        binding.btnCopyDevice.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.section_device), deviceInfo()));
            Toast.makeText(this, R.string.device_info_copied, Toast.LENGTH_SHORT).show();
        });

        binding.btnRun.setOnClickListener(v -> {
            binding.btnRun.setEnabled(false);
            binding.outputView.setText("");
            binding.outputPlaceholder.setVisibility(View.VISIBLE);
            setRunningState();
            binding.contentScroll.post(() -> binding.contentScroll.smoothScrollTo(
                    0, binding.homePage.getTop() + binding.logCard.getTop()));
            mExec.execute(this::runExploit);
        });

        binding.btnClearOutput.setOnClickListener(v -> {
            binding.outputView.setText("");
            binding.outputPlaceholder.setVisibility(View.VISIBLE);
        });

        ComponentName bootReceiver = new ComponentName(this, BootReceiver.class);
        int state = getPackageManager().getComponentEnabledSetting(bootReceiver);
        binding.switchBootStart.setChecked(state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        binding.switchBootStart.setOnCheckedChangeListener((btn, checked) ->
            getPackageManager().setComponentEnabledSetting(bootReceiver,
                checked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("settingsPage", binding.settingsPage.getVisibility() == View.VISIBLE);
        super.onSaveInstanceState(outState);
    }

    private void showPage(boolean settings) {
        binding.homePage.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.settingsPage.setVisibility(settings ? View.VISIBLE : View.GONE);
        binding.btnRun.setVisibility(settings ? View.GONE : View.VISIBLE);
        binding.pageTitle.setText(settings ? R.string.nav_settings : R.string.dashboard_title);
        int active = ContextCompat.getColor(this, R.color.dashboard_accent);
        int inactive = ContextCompat.getColor(this, R.color.dashboard_secondary);
        binding.navHome.setTextColor(settings ? inactive : active);
        binding.navHome.setIconTint(ColorStateList.valueOf(settings ? inactive : active));
        binding.navSettings.setTextColor(settings ? active : inactive);
        binding.navSettings.setIconTint(ColorStateList.valueOf(settings ? active : inactive));
        binding.navHome.setSelected(!settings);
        binding.navSettings.setSelected(settings);
        binding.contentScroll.scrollTo(0, 0);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.content.setPadding(dp(16), bars.top, dp(16), dp(24));
            binding.bottomBarWrapper.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
    }

    private void applySystemBarAppearance() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void populateDeviceInfo() {
        addInfoRow(R.string.info_model, Build.MANUFACTURER + " " + Build.MODEL);
        addInfoRow(R.string.info_system, Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        addInfoRow(R.string.info_firmware, Build.DISPLAY);
        addInfoRow(R.string.info_kernel, DeviceCheck.release());
        addInfoRow(R.string.info_kmi, DeviceCheck.kmi());
        addInfoRow(R.string.info_arch, Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "—");
        addInfoRow(R.string.info_fingerprint, Build.FINGERPRINT);
    }

    private void addInfoRow(int label, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_info_row,
                binding.deviceInfoContainer, false);
        ((TextView) row.findViewById(R.id.infoLabel)).setText(label);
        ((TextView) row.findViewById(R.id.infoValue)).setText(value == null || value.isEmpty() ? "—" : value);
        binding.deviceInfoContainer.addView(row);
    }

    private String deviceInfo() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < binding.deviceInfoContainer.getChildCount(); i++) {
            View row = binding.deviceInfoContainer.getChildAt(i);
            if (i > 0) result.append('\n');
            result.append(((TextView) row.findViewById(R.id.infoLabel)).getText())
                    .append(": ")
                    .append(((TextView) row.findViewById(R.id.infoValue)).getText());
        }
        return result.toString();
    }

    /**
     * 只有在确实安全时才允许运行：
     *  - 本轮已经开过钩（/dev/df）；
     *  - 检测到别的 root 方案已经在生效（/system/bin/su 等仍然存在）。
     * 后者会让 ksud 跳过加载模块、停留在 vendor_modprobe 域，最终安装失败并把
     * su 截成 0 字节 —— 实测过，所以直接拦住。
     */
    private void refreshRunState() {
        String reason = DeviceCheck.blockReason();
        if (reason == null) {
            binding.statusText.setText(R.string.status_ready_title);
            binding.statusDetail.setText(R.string.status_ready_detail);
            applyStatus(R.color.dashboard_status, R.color.dashboard_status_text, R.drawable.ic_shield_check);
            binding.btnRun.setEnabled(true);
            binding.btnRun.setContentDescription(getString(R.string.btn_run));
        } else {
            binding.statusText.setText(R.string.status_blocked_title);
            binding.statusDetail.setText(reason);
            applyStatus(R.color.dashboard_error, R.color.dashboard_error_text, R.drawable.ic_error);
            binding.btnRun.setEnabled(false);
            binding.btnRun.setContentDescription(getString(R.string.btn_run_blocked));
        }
    }

    private void setRunningState() {
        binding.statusText.setText(R.string.status_running_title);
        binding.statusDetail.setText(R.string.status_running_detail);
        applyStatus(R.color.dashboard_running, R.color.dashboard_running_text, R.drawable.ic_autorenew);
        binding.btnRun.setEnabled(false);
        binding.btnRun.setContentDescription(getString(R.string.btn_run_running));
    }

    private void applyStatus(int containerColor, int textColor, int iconRes) {
        int container = ContextCompat.getColor(this, containerColor);
        int onContainer = ContextCompat.getColor(this, textColor);

        binding.statusCard.setCardBackgroundColor(container);
        binding.statusText.setTextColor(onContainer);
        binding.statusDetail.setTextColor(onContainer);
        binding.statusDetail.setAlpha(0.75f);
        binding.statusIcon.setImageResource(iconRes);
        binding.statusIcon.setImageTintList(ColorStateList.valueOf(onContainer));
        binding.statusBadge.setTextColor(onContainer);
        binding.statusBadge.setAlpha(0.75f);
    }

    private void runExploit() {
        try {
            String reason = DeviceCheck.blockReason();
            if (reason != null) {
                log("已中止：" + reason);
                return;
            }
            log(DeviceCheck.report());
            if (!DeviceCheck.preflight("手动运行")) {
                log("\n自检未通过：依赖路径缺失 —— 没有修改任何文件，已中止。");
                return;
            }

            IpSecManager ipsec = (IpSecManager) getSystemService(IPSEC_SERVICE);

            IpSecManager.UdpEncapsulationSocket encapSock = ipsec.openUdpEncapsulationSocket();
            int encapPort = encapSock.getPort();
            log("IPSec 封装端口: " + encapPort);

            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            IpSecManager.SecurityParameterIndex spiObj =
                    ipsec.allocateSecurityParameterIndex(loopback);
            int spiVal = spiObj.getSpi();
            log("SPI: 0x" + Integer.toHexString(spiVal));

            SecureRandom rng = new SecureRandom();
            byte[] aesKey  = new byte[32]; rng.nextBytes(aesKey);
            byte[] hmacKey = new byte[32]; rng.nextBytes(hmacKey);

            IpSecAlgorithm enc  = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey);
            IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, hmacKey, 128);

            DatagramSocket senderSock = new DatagramSocket();
            int senderPort = senderSock.getLocalPort();
            senderSock.close();

            IpSecTransform transform = new IpSecTransform.Builder(this)
                    .setEncryption(enc)
                    .setAuthentication(auth)
                    .setIpv4Encapsulation(encapSock, senderPort)
                    .buildTransportModeTransform(loopback, spiObj);

            stageAsset(this, "ksud", true, getFilesDir());
            log("ksud 已暂存到: " + new File(getFilesDir(), "ksud").getAbsolutePath());
            log("开始执行漏洞利用……");

            int icvLen = 128 / 8;
            String ksudPath = new File(getFilesDir(), "ksud").getAbsolutePath();
            int rc = nativeRunAll(this, encapPort, spiVal, aesKey, hmacKey, icvLen, senderPort, ksudPath, true);

            transform.close();
            spiObj.close();
            encapSock.close();

        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            log("\n发生异常: " + e);
        } finally {
            mMain.post(this::refreshRunState);
        }
    }

    static void stageAsset(Context ctx, String name, boolean executable, File dir) throws IOException {
        File dest = new File(dir, name);
        File tmp = new File(dest.getPath() + ".tmp");
        try (InputStream in = ctx.getAssets().open(name);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        if (!tmp.renameTo(dest)) { tmp.delete(); throw new IOException("rename failed: " + dest); }
        if (executable) dest.setExecutable(true, false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void log(String msg) {
        Log.i(TAG, msg);
        report(msg + "\n");
    }
}
