package com.rarchives.ripme.ripper;

public class SkipAlbumRipException extends RipStatusException {
    public static enum Reason {
        KNOWN_REMOVED,
        HIDDEN,
    }

    private final Reason reason;

    public SkipAlbumRipException(Reason reason) {
        super(reason.toString());
        this.reason = reason;
    }

    public SkipAlbumRipException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
