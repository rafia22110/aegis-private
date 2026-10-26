package com.aegismirror.app;

/** Build-stage status only. No engine or device protection is implemented. */
public enum EngineStatus {
    UNKNOWN;

    public boolean isProtectionVerified() {
        return false;
    }
}
