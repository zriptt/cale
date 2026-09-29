package com.caleon.client.module;

public abstract class Setting {
    public final String name;
    protected Setting(String name) { this.name = name; }

    public static class Bool extends Setting {
        public boolean value;
        public Bool(String name, boolean value) { super(name); this.value = value; }
    }

    public static class Num extends Setting {
        public double value, min, max;
        public Num(String name, double value, double min, double max) {
            super(name); this.value = value; this.min = min; this.max = max;
        }
    }
}
