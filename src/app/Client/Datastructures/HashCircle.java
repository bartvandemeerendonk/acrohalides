package app.Client.Datastructures;

import java.util.Iterator;

public class HashCircle implements  Iterable<byte[]> {
    private static class CircleElement {
        private byte[] hash;
        private HashCircle.CircleElement previousElement;
        private HashCircle.CircleElement nextElement;

        public CircleElement(byte[] hash) {
            this.hash = hash;
            previousElement = null;
            nextElement = null;
        }

        public CircleElement getPreviousElement() {
            return previousElement;
        }

        public void setPreviousElement(CircleElement previousElement) {
            this.previousElement = previousElement;
        }

        public CircleElement getNextElement() {
            return nextElement;
        }

        public void setNextElement(CircleElement nextElement) {
            this.nextElement = nextElement;
        }

        public byte[] getHash() {
            return hash;
        }
    }

    private static class HashCircleIterator implements Iterator<byte[]> {
        private CircleElement cursor;

        public HashCircleIterator(HashCircle hashCircle) {
            cursor = hashCircle.firstElement;
        }

        @Override
        public boolean hasNext() {
            return cursor != null;
        }

        @Override
        public byte[] next() {
            byte[] hash = cursor.getHash();
            cursor = cursor.getNextElement();
            return hash;
        }
    }

    private HashCircle.CircleElement firstElement;
    private HashCircle.CircleElement lastElement;
    private int maxSize;
    private int size;
    private final ByteMap<CircleElement> contentMappedEntries;

    public HashCircle(int maxSize, int hashLength) {
        firstElement = null;
        lastElement = null;
        size = 0;
        this.maxSize = maxSize;
        contentMappedEntries = new ByteMap<>(hashLength);
    }

    private synchronized int getSizeFromLinkedList() {
        int toReturn = 0;
        CircleElement currentElement = firstElement;
        while (currentElement != null) {
            toReturn++;
            currentElement = currentElement.getNextElement();
        }
        return toReturn;
    }

    public synchronized void add(byte[] hash) {
        remove(hash);
        if (lastElement == null) {
            firstElement = new HashCircle.CircleElement(hash);
            lastElement = firstElement;
        } else {
            HashCircle.CircleElement newElement = new HashCircle.CircleElement(hash);
            lastElement.setNextElement(newElement);
            newElement.setPreviousElement(lastElement);
            lastElement = newElement;
        }
        contentMappedEntries.put(hash, lastElement);
        size++;
        if (size != getSizeFromLinkedList() || size != contentMappedEntries.size()) {
            System.out.println("HASHCIRCLE SIZE DISCREPANCY (add 1) size == " + size + "; getSizeFromLinkedList() == " + getSizeFromLinkedList() + "; contentMappedEntries.size() == " + contentMappedEntries.size());
        }
        while (size > maxSize) {
            pop();
        }
        if (size != getSizeFromLinkedList() || size != contentMappedEntries.size()) {
            System.out.println("HASHCIRCLE SIZE DISCREPANCY (add 2) size == " + size + "; getSizeFromLinkedList() == " + getSizeFromLinkedList() + "; contentMappedEntries.size() == " + contentMappedEntries.size());
        }
    }

    public synchronized byte[] pop() {
        if (size == 0) {
            if (size != getSizeFromLinkedList() || size != contentMappedEntries.size()) {
                System.out.println("HASHCIRCLE SIZE DISCREPANCY (pop 1) size == " + size + "; getSizeFromLinkedList() == " + getSizeFromLinkedList() + "; contentMappedEntries.size() == " + contentMappedEntries.size());
            }
            return null;
        }
        byte[] toReturn = firstElement.getHash();
        contentMappedEntries.remove(firstElement.getHash());
        size--;
        firstElement = firstElement.getNextElement();
        if (firstElement == null) {
            lastElement = null;
        } else {
            firstElement.setPreviousElement(null);
        }
        if (size != getSizeFromLinkedList() || size != contentMappedEntries.size()) {
            System.out.println("HASHCIRCLE SIZE DISCREPANCY (pop 2) size == " + size + "; getSizeFromLinkedList() == " + getSizeFromLinkedList() + "; contentMappedEntries.size() == " + contentMappedEntries.size());
        }
        return toReturn;
    }

    public boolean contains(byte[] hash) {
        return contentMappedEntries.containsKey(hash);
    }

    public int getSize() {
        return size;
    }

    public synchronized void remove(byte[] hash) {
        HashCircle.CircleElement elementToRemove = contentMappedEntries.remove(hash);
        if (elementToRemove != null) {
            size--;
            HashCircle.CircleElement previousElement = elementToRemove.getPreviousElement();
            HashCircle.CircleElement nextElement = elementToRemove.getNextElement();
            if (previousElement == null) {
                firstElement = nextElement;
            } else {
                previousElement.setNextElement(nextElement);
            }
            if (nextElement == null) {
                lastElement = previousElement;
            } else {
                nextElement.setPreviousElement(previousElement);
            }
        }
        if (size != getSizeFromLinkedList() || size != contentMappedEntries.size()) {
            System.out.println("HASHCIRCLE SIZE DISCREPANCY (remove) size == " + size + "; getSizeFromLinkedList() == " + getSizeFromLinkedList() + "; contentMappedEntries.size() == " + contentMappedEntries.size());
        }
    }

    @Override
    public Iterator<byte[]> iterator() {
        return new HashCircleIterator(this);
    }
}
