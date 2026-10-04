package com.tokenx.uidtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String TAG = "TokenX-UIDTest";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        int uid = Process.myUid();
        int pid = Process.myPid();
        String result = "TokenX UID Test\n\n"
                + "package=com.tokenx.uidtest\n"
                + "uid=" + uid + "\n"
                + "pid=" + pid + "\n\n"
                + (uid == Process.SYSTEM_UID
                    ? "RESULT: UID 1000 / SYSTEM"
                    : "RESULT: NOT UID 1000");
        Log.i(TAG, result.replace('\n', ' '));
        TextView view = new TextView(this);
        view.setText(result);
        view.setTextSize(18f);
        view.setPadding(48, 72, 48, 48);
        setContentView(view);
    }
}
