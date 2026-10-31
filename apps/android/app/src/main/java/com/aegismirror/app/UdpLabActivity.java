package com.aegismirror.app;

import android.app.Activity;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.text.InputType;
import android.widget.*;

public final class UdpLabActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private VpnPreparationService.LocalBinder service;
    private boolean bound, visible, busy;
    private int generation;
    private EditText host, port;
    private Switch allow;
    private TextView status;
    private Button probe;
    private LinearLayout content;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!(binder instanceof VpnPreparationService.LocalBinder)) return;
            service = (VpnPreparationService.LocalBinder) binder;
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null; busy = false; generation++;
            status.setText(R.string.lab_failed); refresh();
        }
    };
    private void text(int resource) {
        TextView view = new TextView(this);
        view.setText(resource); view.setTextSize(18); view.setPadding(0, 12, 0, 12);
        content.addView(view);
    }
    private Button button(int resource, Runnable action) {
        Button view = new Button(this); view.setText(resource);
        view.setOnClickListener(v -> action.run()); content.addView(view); return view;
    }
    private EditText input(int hint, int type) {
        EditText view = new EditText(this); view.setHint(hint); view.setSingleLine(true);
        view.setInputType(type); content.addView(view); return view;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this); scroll.addView(content); setContentView(scroll);
        text(R.string.lab_title); text(R.string.lab_boundary); text(R.string.lab_warning);
        host = input(R.string.lab_host, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        port = input(R.string.lab_port, InputType.TYPE_CLASS_NUMBER);
        allow = new Switch(this); allow.setText(R.string.lab_allow); content.addView(allow);
        if (state != null) {
            host.setText(state.getString("host", "")); port.setText(state.getString("port", ""));
            allow.setChecked(state.getBoolean("allow", false));
        }
        status = new TextView(this); status.setText(R.string.lab_idle); content.addView(status);
        probe = button(R.string.lab_probe, this::probe);
        button(R.string.lab_cancel, () -> {
            generation++; if (service != null) service.cancel(); busy = false;
            status.setText(R.string.lab_cancelled); refresh();
        });
        button(R.string.lab_consent, () -> startActivity(new Intent(this, VpnSetupActivity.class)));
        button(R.string.vpn_open_policy, () -> startActivity(new Intent(this, MainActivity.class)));
        refresh();
    }
    private void refresh() {
        probe.setEnabled(service != null && visible && !busy);
        host.setEnabled(!busy); port.setEnabled(!busy); allow.setEnabled(!busy);
    }
    private void probe() {
        try {
            String destination = host.getText().toString();
            int targetPort = Integer.parseInt(port.getText().toString());
            new UdpLabTarget(destination, targetPort);
            if (VpnService.prepare(this) != null) { status.setText(R.string.lab_need_consent); return; }
            if (service == null || busy) return;
            int token = ++generation;
            busy = true; status.setText(R.string.lab_running); refresh();
            service.probe(destination, targetPort, allow.isChecked(), message -> handler.post(() -> {
                if (!visible || token != generation || isFinishing() || isDestroyed()) return;
                busy = false; status.setText(message); refresh();
            }));
        } catch (IllegalArgumentException error) { busy = false; status.setText(R.string.lab_invalid_target); refresh(); }
        catch (RuntimeException error) { busy = false; status.setText(R.string.lab_failed); refresh(); }
    }
    @Override protected void onStart() {
        super.onStart(); visible = true;
        try {
            bound = bindService(new Intent(this, VpnPreparationService.class).setAction(VpnPreparationService.LAB_ACTION), connection, BIND_AUTO_CREATE);
            if (!bound) status.setText(R.string.lab_failed);
        } catch (RuntimeException error) { bound = false; status.setText(R.string.lab_failed); }
        refresh();
    }
    @Override protected void onStop() {
        visible = false; generation++;
        if (service != null) service.cancel();
        if (bound) { unbindService(connection); bound = false; }
        service = null;
        if (busy) status.setText(R.string.lab_cancelled);
        busy = false; refresh(); super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("host", host.getText().toString()); state.putString("port", port.getText().toString());
        state.putBoolean("allow", allow.isChecked()); super.onSaveInstanceState(state);
    }
}
