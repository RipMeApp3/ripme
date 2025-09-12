package com.rarchives.ripme.ui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;

/**
 * Utility to prevent copying in the app from triggering the clipboard auto rip thread
 */
public class AppClipboardOwner implements ClipboardOwner {
    private boolean isAppOwner = false;

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        isAppOwner = false;
    }

    public void setAppOwner(boolean isAppOwner) {
        this.isAppOwner = isAppOwner;
    }

    public boolean isAppOwner() {
        return isAppOwner;
    }
}
