package com.rarchives.ripme.ui;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Like {@link DefaultListModel} of an ordered set.
 * Reimplementing everything is ugly, but still the cleanest way to ensure the list is unique without making mistakes.
 */
// Note: the JDK only unit tests DefaultListModel#addAll...
public class UniqueListModel<E> extends AbstractListModel<E> {
    private final List<E> delegateList = Collections.synchronizedList(new ArrayList<>());
    private final HashSet<E> delegateSet = new HashSet<>();

    // Access to the model may happen from different threads, so we need to synchronize on a lock.
    // DefaultListModel uses Vector which is synchronized by default, which is why that doesn't use it.
    ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    @Override
    public int getSize() {
        readLock.lock();
        try {
            return delegateList.size();
        } finally {
            readLock.unlock();
        }
    }

    public int size() {
        return getSize();
    }

    public boolean isEmpty() {
        readLock.lock();
        try {
            return delegateList.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    public List<E> getList() {
        readLock.lock();
        try {
            return new ArrayList<>(delegateList);
        } finally {
            readLock.unlock();
        }
    }

    public boolean contains(E element) {
        readLock.lock();
        try {
            return delegateSet.contains(element);
        } finally {
            readLock.unlock();
        }
    }

    public int indexOf(E element) {
        readLock.lock();
        try {
            if (delegateSet.contains(element)) {
                return delegateList.indexOf(element);
            }
            return -1;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public E getElementAt(int index) {
        readLock.lock();
        try {
            return delegateList.get(index);
        } finally {
            readLock.unlock();
        }

    }

    public E get(int index) {
        return getElementAt(index);
    }


    public void insertElementAt(E element, int index) {
        writeLock.lock();
        try {
            if (index < 0 || index > size()) {
                throw new IndexOutOfBoundsException("index out of bounds");
            }
            // new element; simple
            if (!delegateSet.contains(element)) {
                if (delegateSet.add(element)) {
                    delegateList.add(index, element);
                    fireIntervalAdded(this, index, index);
                }
                return;
            }
            // existing element; move to new index
            int originalIndex = delegateList.indexOf(element);
            if (originalIndex == index) {
                return;
            }
            delegateList.remove(originalIndex);
            delegateList.add(index, element);
            // TODO fire both interval added and interval removed?
        } finally {
            writeLock.unlock();
        }
    }

    public void add(int index, E element) {
        insertElementAt(element, index);
    }

    public void addElement(E element) {
        writeLock.lock();
        try {
            if (delegateSet.add(element)) {
                int index = delegateList.size();
                delegateList.add(element);
                fireIntervalAdded(this, index, index);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void addAll(int index, Collection<? extends E> collection) {
        writeLock.lock();
        try {
            if (index < 0 || index > getSize()) {
                throw new ArrayIndexOutOfBoundsException("index out of range: " + index);
            }
            if (collection.isEmpty()) {
                return;
            }
            int i = 0;
            for (E element : collection) {
                if (delegateSet.add(element)) {
                    delegateList.add(index + i, element);
                    i++;
                }
            }
            fireIntervalAdded(this, index, index + i - 1);
        } finally {
            writeLock.unlock();
        }
    }

    public void addAll(Collection<? extends E> collection) {
        writeLock.lock();
        try {
            if (collection.isEmpty()) {
                return;
            }
            int startIndex = delegateList.size();
            for (E element : collection) {
                if (delegateSet.add(element)) {
                    delegateList.add(element);
                }
            }
            fireIntervalAdded(this, startIndex, delegateList.size() - 1);
        } finally {
            writeLock.unlock();
        }
    }

    public E remove(int index) {
        writeLock.lock();
        try {
            E removedElement = delegateList.remove(index);
            delegateSet.remove(removedElement);
            fireIntervalRemoved(this, index, index);
            return removedElement;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean removeElement(E element) {
        writeLock.lock();
        try {
            if (delegateSet.remove(element)) {
                int index = delegateList.indexOf(element);
                delegateList.remove(index);
                fireIntervalRemoved(this, index, index);
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    public void removeAllElements() {
        writeLock.lock();
        try {
            int originalLastIndex = delegateList.size() - 1;
            delegateList.clear();
            delegateSet.clear();
            if (originalLastIndex >= 0) {
                fireIntervalRemoved(this, 0, originalLastIndex);
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void clear() {
        removeAllElements();
    }

    public String toString() {
        readLock.lock();
        try {
            return delegateList.toString();
        } finally {
            readLock.unlock();
        }
    }
}
