package org.leavesmc.leaves.protocol.syncmatica;

public enum Feature {
    CORE,
    FEATURE,
    MODIFY,
    MESSAGE,
    QUOTA,
    DEBUG,
    CORE_EX;

    @org.jetbrains.annotations.Nullable
    public static Feature fromString(final String s) {
        for (final Feature f : Feature.values()) {
            if (f.toString().equals(s)) {
                return f;
            }
        }
        return null;
    }
}
