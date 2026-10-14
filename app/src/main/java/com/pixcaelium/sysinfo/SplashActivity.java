package com.pixcaelium.sysinfo;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private static final long MIN_SPLASH_MS = 1200;
    private static final String GITHUB_URL = "https://github.com/td-ct/SysInfo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView version = findViewById(R.id.splash_version);
        String vName;
        try {
            vName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            vName = "?";
        }
        version.setText("v" + vName);

        Button github = findViewById(R.id.splash_github);
        github.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)));
            } catch (Exception e) {
                LogUtil.w("GitHub-Link konnte nicht geöffnet werden");
            }
        });

        findViewById(R.id.splash_root).setOnClickListener(v -> startMain());
        new android.os.Handler().postDelayed(this::startMain, MIN_SPLASH_MS);
    }

    private void startMain() {
        if (isFinishing()) return;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
