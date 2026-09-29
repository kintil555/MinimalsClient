package com.minimals.client.worldhost;

/**
 * Holds the most recent domain e4mc assigned to our QuiclimeSession, captured by
 * {@code E4mcSessionAccessorMixin}. e4mc itself only prints this to chat; there is no
 * public getter on QuiclimeSession, so we intercept it at the source instead of scraping
 * chat components.
 */
public final class E4mcDomainHolder {
    private static volatile String domain;

    private E4mcDomainHolder() {}

    public static void set(String value) {
        domain = value;
        WorldHostManager.onDomainAssigned();
    }

    /** Null until e4mc finishes domain assignment for the current session. */
    public static String get() {
        return domain;
    }

    public static void clear() {
        domain = null;
    }
}
