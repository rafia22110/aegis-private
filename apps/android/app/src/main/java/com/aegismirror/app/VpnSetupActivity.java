package com.aegismirror.app;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** System consent only: this activity never starts a VPN or establishes a TUN. */
public final class VpnSetupActivity extends Activity {
    private static final int CONSENT_REQUEST = 3001;
    private static final String RESULT_STATE = "consent_result";
    private static final int NONE = 0, CANCELLED = 1, FAILED = 2;
    private int lastResult = NONE;
    private TextView status;
    private Button request;
    private LinearLayout content;

    private void text(int resource) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(18);
        view.setPadding(0, 12, 0, 12);
        content.addView(view);
    }
    private Button button(int resource, Runnable action) {
        Button view = new Button(this);
        view.setText(resource);
        view.setOnClickListener(v -> action.run());
        content.addView(view);
        return view;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) lastResult = state.getInt(RESULT_STATE, NONE);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        text(R.string.vpn_setup_title);
        text(R.string.vpn_setup_boundary);
        text(R.string.vpn_existing_warning);
        status = new TextView(this);
        status.setTextSize(18);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        request = button(R.string.vpn_request_consent, this::requestConsent);
        button(R.string.vpn_refresh_consent, () -> { lastResult = NONE; refresh(); });
        button(R.string.vpn_system_settings, () -> {
            try { startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); }
            catch (RuntimeException error) { Toast.makeText(this, R.string.vpn_settings_unavailable, Toast.LENGTH_LONG).show(); }
        });
        button(R.string.vpn_open_policy, () -> startActivity(new Intent(this, MainActivity.class)));
    }
    @Override protected void onResume() {
        super.onResume();
        refresh();
    }
    private void refresh() {
        try {
            boolean prepared = VpnService.prepare(this) == null;
            request.setEnabled(!prepared);
            status.setText(prepared ? R.string.vpn_consent_ready : lastResult == CANCELLED
                    ? R.string.vpn_consent_cancelled : lastResult == FAILED
                    ? R.string.vpn_consent_failed : R.string.vpn_consent_missing);
        } catch (RuntimeException error) {
            request.setEnabled(true);
            status.setText(R.string.vpn_consent_failed);
        }
    }
    private void requestConsent() {
        lastResult = NONE;
        try {
            Intent consent = VpnService.prepare(this);
            if (consent == null) { refresh(); return; }
            startActivityForResult(consent, CONSENT_REQUEST);
        } catch (RuntimeException error) {
            lastResult = FAILED;
            status.setText(R.string.vpn_consent_failed);
        }
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CONSENT_REQUEST) return;
        lastResult = resultCode == RESULT_OK ? NONE : CANCELLED;
        refresh();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(RESULT_STATE, lastResult);
        super.onSaveInstanceState(state);
    }
}
