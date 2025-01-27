package com.github.kudeplatform.evaluationengine.domain;

public enum SupportedModes {

    AKKA("akka"),
    SPARK("spark");

    private final String mode;

    SupportedModes(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    public static SupportedModes fromString(String mode) {
        for (SupportedModes supportedMode : SupportedModes.values()) {
            if (supportedMode.getMode().equalsIgnoreCase(mode)) {
                return supportedMode;
            }
        }
        throw new IllegalArgumentException("No supported mode found for mode: " + mode);
    }
}
