package com.rarchives.ripme.ui;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

import com.rarchives.ripme.utils.Utils;

class HistoryMenuMouseListener extends MouseAdapter {
    private JPopupMenu popup = new JPopupMenu();
    private JTable tableComponent;
    private final int checkboxColumn = 5;

    @SuppressWarnings("serial")
    public HistoryMenuMouseListener() {
        Action checkAllAction = new AbstractAction(Utils.getLocalizedString("history.check.all")) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                HistoryTableModel model = (HistoryTableModel) tableComponent.getModel();
                int[] allRows = new int[model.getRowCount()];
                for (int i = 0; i < allRows.length; i++) {
                    allRows[i] = i;
                }
                model.setValuesAt(true, allRows, checkboxColumn);
            }
        };
        popup.add(checkAllAction);

        Action uncheckAllAction = new AbstractAction(Utils.getLocalizedString("history.check.none")) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                HistoryTableModel model = (HistoryTableModel) tableComponent.getModel();
                int[] allRows = new int[model.getRowCount()];
                for (int i = 0; i < allRows.length; i++) {
                    allRows[i] = i;
                }
                model.setValuesAt(false, allRows, checkboxColumn);
            }
        };
        popup.add(uncheckAllAction);

        popup.addSeparator();

        Action checkSelected = new AbstractAction(Utils.getLocalizedString("history.check.selected")) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                int[] selectedRows = tableComponent.getSelectedRows();
                int[] modelSelectedRows = new int[selectedRows.length];
                for ( int i = 0; i < selectedRows.length; i++) {
                    modelSelectedRows[i] = tableComponent.convertRowIndexToModel(selectedRows[i]);
                }
                HistoryTableModel model = (HistoryTableModel) tableComponent.getModel();
                model.setValuesAt(true, modelSelectedRows, checkboxColumn);
            }
        };
        popup.add(checkSelected);

        Action uncheckSelected = new AbstractAction(Utils.getLocalizedString("history.uncheck.selected")) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                int[] selectedRows = tableComponent.getSelectedRows();
                int[] modelSelectedRows = new int[selectedRows.length];
                for ( int i = 0; i < selectedRows.length; i++) {
                    modelSelectedRows[i] = tableComponent.convertRowIndexToModel(selectedRows[i]);
                }
                HistoryTableModel model = (HistoryTableModel) tableComponent.getModel();
                model.setValuesAt(false, modelSelectedRows, checkboxColumn);
            }
        };
        popup.add(uncheckSelected);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        checkPopupTrigger(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        checkPopupTrigger(e);
    }

    private void checkPopupTrigger(MouseEvent e) {
        if (e.getModifiersEx() == InputEvent.BUTTON3_DOWN_MASK) {
            if (!(e.getSource() instanceof JTable)) {
                return;
            }

            tableComponent = (JTable) e.getSource();
            tableComponent.requestFocus();

            int nx = e.getX();

            if (nx > 500) {
                nx = nx - popup.getSize().width;
            }
            popup.show(e.getComponent(), nx, e.getY() - popup.getSize().height);
        }
    }
}
