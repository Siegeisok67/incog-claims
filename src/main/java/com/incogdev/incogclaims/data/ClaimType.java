package com.incogdev.incogclaims.data;

public enum ClaimType {
    PVP,
    PEACEFUL;

    public static ClaimType fromString(String s) {
        if (s == null) return null;
        try {
            return ClaimType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
