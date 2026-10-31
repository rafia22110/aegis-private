package com.aegismirror.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class PolicyStore extends SQLiteOpenHelper {
    public PolicyStore(Context context) { super(context, "aegis_local_v1.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE profiles (id TEXT PRIMARY KEY NOT NULL, default_action TEXT NOT NULL CHECK(default_action IN ('ALLOW','DENY')))");
        db.execSQL("CREATE TABLE app_state (id INTEGER PRIMARY KEY CHECK(id=1), profile_id TEXT NOT NULL REFERENCES profiles(id))");
        db.execSQL("CREATE TABLE network_policies (id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id TEXT NOT NULL REFERENCES profiles(id), package_name TEXT NOT NULL, destination TEXT NOT NULL, port INTEGER NOT NULL CHECK(port BETWEEN 0 AND 65535), action TEXT NOT NULL CHECK(action IN ('ALLOW','DENY')), UNIQUE(profile_id,package_name,destination,port))");
        db.execSQL("CREATE TABLE policy_events (id INTEGER PRIMARY KEY AUTOINCREMENT, profile_id TEXT NOT NULL REFERENCES profiles(id), package_name TEXT NOT NULL, destination TEXT NOT NULL, port INTEGER NOT NULL, decision TEXT NOT NULL CHECK(decision IN ('ALLOW','DENY')), event_type TEXT NOT NULL CHECK(event_type='POLICY_EVALUATION'), created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        for (LocalPolicy.Profile p : LocalPolicy.Profile.values()) {
            db.execSQL("INSERT INTO profiles(id,default_action) VALUES (?,?)", new Object[]{p.name(), p.fallback.name()});
        }
        db.execSQL("INSERT INTO app_state(id,profile_id) VALUES (1,'STANDARD')");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("No approved migration from " + oldVersion + " to " + newVersion);
    }
    public LocalPolicy.Profile active() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT profile_id FROM app_state WHERE id=1", null)) {
            if (!c.moveToFirst()) throw new SQLiteException("Missing active profile");
            return LocalPolicy.Profile.valueOf(c.getString(0));
        }
    }
    public void select(LocalPolicy.Profile profile) {
        ContentValues values = new ContentValues();
        values.put("profile_id", profile.name());
        if (getWritableDatabase().update("app_state", values, "id=1", null) != 1) throw new SQLiteException("Profile update failed");
    }
    public void save(LocalPolicy.Profile profile, LocalPolicy.Rule rule) {
        ContentValues values = new ContentValues();
        values.put("profile_id", profile.name());
        values.put("package_name", rule.app);
        values.put("destination", rule.host);
        values.put("port", rule.port);
        values.put("action", rule.action.name());
        if (getWritableDatabase().insertWithOnConflict("network_policies", null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1) throw new SQLiteException("Rule save failed");
    }
    public void delete(LocalPolicy.Profile profile, long id) {
        getWritableDatabase().delete("network_policies", "profile_id=? AND id=?", new String[]{profile.name(), Long.toString(id)});
    }
    public List<LocalPolicy.Rule> rules(LocalPolicy.Profile profile) {
        List<LocalPolicy.Rule> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,package_name,destination,port,action FROM network_policies WHERE profile_id=? ORDER BY id DESC", new String[]{profile.name()})) {
            while (c.moveToNext()) result.add(new LocalPolicy.Rule(c.getLong(0), c.getString(1), c.getString(2), c.getInt(3), LocalPolicy.Action.valueOf(c.getString(4))));
        }
        return result;
    }
    public LocalPolicy.Action evaluate(LocalPolicy.Profile profile, String app, String host, int port) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            LocalPolicy.Action decision = LocalPolicy.evaluate(profile, rules(profile), app, host, port);
            db.execSQL("INSERT INTO policy_events(profile_id,package_name,destination,port,decision,event_type) VALUES (?,?,?,?,?,'POLICY_EVALUATION')", new Object[]{profile.name(), app, LocalPolicy.host(host), port, decision.name()});
            db.execSQL("DELETE FROM policy_events WHERE id NOT IN (SELECT id FROM policy_events ORDER BY id DESC LIMIT 100)");
            db.setTransactionSuccessful();
            return decision;
        } finally { db.endTransaction(); }
    }
    public int eventCount() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM policy_events", null)) {
            c.moveToFirst();
            return c.getInt(0);
        }
    }
}
