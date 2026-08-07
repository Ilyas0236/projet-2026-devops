package com.wydad.digital.auth.model;

public enum MembershipLevel {
    ROUGE(500, "Rouge"),
    OR(1200, "Or"),
    DIAMANT(3000, "Diamant"),
    LEGENDE(0, "Légende"),
    JUNIOR(200, "Junior");

    private final int price;
    private final String displayName;

    MembershipLevel(int price, String displayName) {
        this.price = price;
        this.displayName = displayName;
    }

    public int getPrice() {
        return price;
    }

    public String getDisplayName() {
        return displayName;
    }
}