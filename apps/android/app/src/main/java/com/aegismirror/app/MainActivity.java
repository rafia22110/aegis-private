package com.aegismirror.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView status = findViewById(R.id.engine_status);
        status.setText(getString(R.string.engine_status, EngineStatus.UNKNOWN.name()));
    }
}
