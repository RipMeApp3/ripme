package com.rarchives.ripme.ui;

import javax.swing.table.AbstractTableModel;

public class HistoryTableModel extends AbstractTableModel {
    private final History history;

    public HistoryTableModel(History history) {
        this.history = history;
    }

    @Override
    public String getColumnName(int col) {
        return history.getColumnName(col);
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return getValueAt(0, c).getClass();
    }

    @Override
    public Object getValueAt(int row, int col) {
        return history.getValueAt(row, col);
    }

    @Override
    public int getRowCount() {
        return history.toList().size();
    }

    @Override
    public int getColumnCount() {
        return history.getColumnCount();
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return (col == 0 || col == 5);
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        if (col == 5) {
            history.get(row).selected = (Boolean) value;
            this.fireTableDataChanged();
        }
    }

    public void setValuesAt(Object value, int[] rows, int col) {
        // fireTableDataChanged() is very slow, so batch the updates to call it once.
        if (col == 5) {
            for (int row : rows) {
                history.get(row).selected = (Boolean) value;
            }
            this.fireTableDataChanged();
        }
    }
}
