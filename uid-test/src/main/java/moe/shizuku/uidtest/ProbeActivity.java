package moe.shizuku.uidtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("TokenX UID Test"); title.setTextSize(26); layout.addView(title);
        TextView detail = new TextView(this);
        detail.setTextSize(16);
        detail.setText("\nActual UID: " + Process.myUid() + "\nPID: " + Process.myPid()
                + "\nVariant: " + BuildConfig.FLAVOR + "\nProcess: " + ProbeService.self("/proc/self/cmdline")
                + "\nSELinux: " + ProbeService.self("/proc/self/attr/current")
                + "\n\nUse TokenX → UID 1000 test companion to provision, verify or remove this app."
                + "\n\nUID 1000 does not establish System Server injection. TokenX checks that backend separately.");
        layout.addView(detail); setContentView(layout);
    }
}
