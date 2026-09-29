package com.caleon.client.module;

public enum Category {
    COMBAT("Combat"), MISC("Misc"), BASEFINDING("Basefinding"), RENDER("Render");
    public final String label;
    Category(String label) { this.label = label; }
}
