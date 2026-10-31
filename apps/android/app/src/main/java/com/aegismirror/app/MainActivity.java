package com.aegismirror.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private PolicyStore store;
    private Spinner profiles, actions;
    private EditText app, host, port;
    private TextView result, events;
    private LinearLayout rows;
    private LinearLayout content;
    private void label(int resource) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(18);
        view.setPadding(0, 12, 0, 12);
        content.addView(view);
    }
    private EditText input(int hint, int type) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setSingleLine(true);
        view.setInputType(type);
        content.addView(view);
        return view;
    }
    private Button button(int title, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setOnClickListener(v -> action.run());
        content.addView(view);
        return view;
    }
    private interface Work { void run() throws Exception; }
    private void background(Work work) {
        worker.execute(() -> {
            try { work.run(); }
            catch (Exception error) { ui(() -> Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_LONG).show()); }
        });
    }
    private void ui(Runnable action) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) action.run(); });
    }
    private LocalPolicy.Profile selected() { return LocalPolicy.Profile.values()[profiles.getSelectedItemPosition()]; }
    private int enteredPort() { return Integer.parseInt(port.getText().toString().trim()); }
    private void refresh(LocalPolicy.Profile profile) throws Exception {
        List<LocalPolicy.Rule> rules = store.rules(profile);
        int count = store.eventCount();
        ui(() -> {
            if (selected() != profile) return;
            rows.removeAllViews();
            for (LocalPolicy.Rule rule : rules) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                TextView text = new TextView(this);
                text.setText(getString(R.string.rule_format, rule.app, rule.host, rule.port, rule.action.name()));
                row.addView(text);
                Button delete = new Button(this);
                delete.setText(R.string.delete_rule);
                delete.setOnClickListener(v -> background(() -> { store.delete(profile, rule.id); refresh(profile); }));
                row.addView(delete);
                rows.addView(row);
            }
            events.setText(getString(R.string.event_count, count));
        });
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new PolicyStore(getApplicationContext());
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        setContentView(scroll);
        label(R.string.app_name);
        label(R.string.local_core_notice);
        label(R.string.profile_explanation);
        profiles = new Spinner(this);
        profiles.setAdapter(ArrayAdapter.createFromResource(this, R.array.profile_names, android.R.layout.simple_spinner_dropdown_item));
        content.addView(profiles);
        app = input(R.string.package_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        host = input(R.string.host_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        port = input(R.string.port_hint, InputType.TYPE_CLASS_NUMBER);
        port.setText(R.string.default_port);
        actions = new Spinner(this);
        actions.setAdapter(ArrayAdapter.createFromResource(this, R.array.rule_actions, android.R.layout.simple_spinner_dropdown_item));
        content.addView(actions);
        Button save = button(R.string.save_rule, () -> {
            try {
                LocalPolicy.Profile p = selected();
                LocalPolicy.Action a = LocalPolicy.Action.values()[actions.getSelectedItemPosition()];
                LocalPolicy.Rule rule = new LocalPolicy.Rule(0, app.getText().toString().trim(), host.getText().toString(), enteredPort(), a);
                background(() -> { store.save(p, rule); refresh(p); });
            } catch (IllegalArgumentException error) { Toast.makeText(this, R.string.invalid_rule, Toast.LENGTH_LONG).show(); }
        });
        Button check = button(R.string.evaluate_rule, () -> {
            try {
                LocalPolicy.Profile p = selected();
                String packageName = LocalPolicy.packageName(app.getText().toString().trim());
                String destination = LocalPolicy.host(host.getText().toString());
                int requestPort = enteredPort();
                if (requestPort < 1 || requestPort > 65535) throw new IllegalArgumentException();
                background(() -> {
                    LocalPolicy.Action decision = store.evaluate(p, packageName, destination, requestPort);
                    ui(() -> result.setText(getString(R.string.decision_format, p.name(), decision.name())));
                    refresh(p);
                });
            } catch (IllegalArgumentException error) { Toast.makeText(this, R.string.invalid_request, Toast.LENGTH_LONG).show(); }
        });
        result = new TextView(this);
        result.setText(R.string.no_decision);
        content.addView(result);
        events = new TextView(this);
        content.addView(events);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        content.addView(rows);
        save.setEnabled(false);
        check.setEnabled(false);
        background(() -> {
            LocalPolicy.Profile active = store.active();
            ui(() -> {
                profiles.setSelection(active.ordinal());
                profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        LocalPolicy.Profile p = LocalPolicy.Profile.values()[position];
                        result.setText(R.string.no_decision);
                        background(() -> { store.select(p); refresh(p); });
                    }
                });
                save.setEnabled(true);
                check.setEnabled(true);
            });
            refresh(active);
        });
    }
    @Override protected void onDestroy() {
        worker.execute(() -> store.close());
        worker.shutdown();
        super.onDestroy();
    }
}
