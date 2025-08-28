package com.rarchives.ripme.ui;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UniqueListModelTest {

    @Test
    void addElement() {
        // Setup
        UniqueListModel<String> model = new UniqueListModel<>();
        ListDataListener listener = Mockito.spy(ListDataListener.class);
        model.addListDataListener(listener);

        // Test insertion
        model.insertElementAt("zero", 0);
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 0, 0).toString())));
        model.addElement("one");
        model.addElement("two");
        Mockito.reset(listener);
        model.addElement("three");
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 3, 3).toString())));
        assertEquals(4, model.getSize());
        assertEquals("[zero, one, two, three]", model.toString());

        // Test uniqueness
        Mockito.reset(listener);
        model.addElement("zero");
        model.addElement("two");
        model.addElement("one");
        model.addElement("three");
        Mockito.verify(listener, Mockito.times(0)).intervalRemoved(Mockito.any());
        assertEquals(4, model.getSize());
        assertEquals("[zero, one, two, three]", model.toString());
    }

    @Test
    void addAll() {
        // Setup
        UniqueListModel<String> model = new UniqueListModel<>();
        ListDataListener listener = Mockito.spy(ListDataListener.class);
        model.addListDataListener(listener);

        // Test insertion
        Mockito.reset(listener);
        model.addAll(List.of("zero", "one", "four", "five"));
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 0, 3).toString())));
        Mockito.reset(listener);
        model.addAll(2, List.of("two", "three"));
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 2, 3).toString())));
        assertEquals(6, model.getSize());
        assertEquals("[zero, one, two, three, four, five]", model.toString());

        // Test uniqueness
        Mockito.reset(listener);
        model.addAll(List.of("two", "six"));
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 6, 6).toString())));
        assertEquals(7, model.getSize());
        assertEquals("[zero, one, two, three, four, five, six]", model.toString());

        // Test multiple inserted ranges
        model.clear();
        model.addAll(List.of("zero", "one", "four", "five"));
        Mockito.reset(listener);
        model.addAll(2, List.of("one", "two", "three", "four"));
        assertEquals("[zero, one, two, three, four, five]", model.toString());
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 2, 3).toString())));
        assertEquals(6, model.getSize());
    }

    @Test
    void deletion() {
        // Setup
        UniqueListModel<String> model = new UniqueListModel<>();
        ListDataListener listener = Mockito.spy(ListDataListener.class);
        model.addListDataListener(listener);
        model.addAll(List.of("zero", "one", "two", "three"));

        // Test deletion
        Mockito.reset(listener);
        model.removeElement("two");
        Mockito.verify(listener, Mockito.times(1)).intervalRemoved(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_REMOVED, 2, 2).toString())));
        assertEquals(3, model.getSize());
        assertEquals("[zero, one, three]", model.toString());
        model.removeElement("zero");
        assertEquals(2, model.getSize());
        assertEquals("[one, three]", model.toString());
        Mockito.reset(listener);
        String removed = model.remove(1);
        Mockito.verify(listener, Mockito.times(1)).intervalRemoved(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_REMOVED, 1, 1).toString())));
        assertEquals("[one]", model.toString());
        assertEquals("three", removed);
    }

    @Test
    void insertElementAt() {
        // Setup
        UniqueListModel<String> model = new UniqueListModel<>();
        ListDataListener listener = Mockito.spy(ListDataListener.class);
        model.addListDataListener(listener);
        model.addAll(List.of("zero", "one", "three"));

        // Test insertElementAt
        Mockito.reset(listener);
        assertThrows(IndexOutOfBoundsException.class, () -> model.insertElementAt("two", 5));
        Mockito.verify(listener, Mockito.times(0)).intervalAdded(Mockito.any());

        Mockito.reset(listener);
        model.insertElementAt("two", 2);
        Mockito.verify(listener, Mockito.times(1)).intervalAdded(Mockito.argThat(
                event -> event.toString().equals(new ListDataEvent(model, ListDataEvent.INTERVAL_ADDED, 2, 2).toString())));
        assertEquals(4, model.getSize());
        assertEquals("[zero, one, two, three]", model.toString());
    }

    @Test
    void insertElementAt_movedIndex() {
        // Setup
        UniqueListModel<String> model = new UniqueListModel<>();
        ListDataListener listener = Mockito.spy(ListDataListener.class);
        model.addListDataListener(listener);
        model.addAll(List.of("zero", "one", "two", "three"));

        // Test moving with insertElementAt
        model.insertElementAt("two", 0);
        assertEquals(4, model.getSize());
        assertEquals("[two, zero, one, three]", model.toString());
        model.insertElementAt("two", 2);
        assertEquals(4, model.getSize());
        assertEquals("[zero, one, two, three]", model.toString());
        model.insertElementAt("two", 3);
        assertEquals(4, model.getSize());
        assertEquals("[zero, one, three, two]", model.toString());
    }

}
