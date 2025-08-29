package com.rarchives.ripme.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;

public class ProgressTextField extends JProgressBar {
    private static final Logger logger = LogManager.getLogger(ProgressTextField.class);
    private final JTextField textField = new JTextField();
    private final JLabel valueLabel = new JLabel();

    // JProgressBar uses BoundedRangeModel
    // BoundedRangeModel uses int
    // To represent a byte progress bar, we need long (max int is 2 GiB)
    // I don't want to reimplement everything,
    // so we'll map the real progress from 0 to Integer.MAX_VALUE
    private long minimum = 0;
    private long maximum = 100;

    public ProgressTextField() {
        super(0, Integer.MAX_VALUE);
        setLayout(new BorderLayout());

        // Paint an empty string to reserve height for the text field
        super.setStringPainted(true);
        progressString = ""; // Directly set here so setString can be overridden

        textField.setOpaque(false);
        textField.setEditable(false);
        textField.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        add(textField, BorderLayout.CENTER);

        valueLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, valueLabel.getFont().getSize()));
        valueLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Color.GRAY),
                BorderFactory.createEmptyBorder(0, 5, 0, 5)
        ));
        setValue(0);

        add(valueLabel, BorderLayout.EAST);
    }

    @Override
    public void setString(String s) {
        if (s != null) {
            // super() calls setString(null) during construction; not a problem
            logger.warn("bug: progress bar should use setText, not setString");
        }
    }

    @Override
    public void setValue(int n) {
        setValue((long) n);
    }

    @Override
    public void setMinimum(int n) {
        setMinimum((long) n);
    }

    @Override
    public void setMaximum(int n) {
        setMaximum((long) n);
    }

    private void setMinimum(long n) {
        if (this.minimum != n) {
            this.minimum = n;
            super.setMinimum(0); // Trigger any state changes
        }
    }

    public void setMaximum(long n) {
        if (this.maximum != n) {
            this.maximum = n;
            super.setMaximum(Integer.MAX_VALUE); // Trigger any state change events
        }
    }

    public void setValue(long n) {
        long progress = n - minimum;
        long total = maximum - minimum;
        double fraction = (double) Math.min(progress, total) / total; // Clamp max to 100%
        double percent = 100 * fraction;
        int mappedValue = (int) Math.min((Integer.MAX_VALUE * fraction), Integer.MAX_VALUE);
        super.setValue(mappedValue);
        valueLabel.setText((int) percent + "%");
    }

    public void setText(String t) {
        textField.setText(t);
    }
}
