package app.Client.Datastructures;

import app.Client.Layers.BlockchainLayer.DilutionApplication;
import app.Client.Layers.BlockchainLayer.KeyWithOrigin;
import org.bouncycastle.util.encoders.Hex;

import java.util.Iterator;

public class DilutionApplicationCircle implements Iterable<DilutionApplication> {
    private static int BLAME_PENALTY = 5;

    private static class CircleElement {
//        private KeyWithOrigin keyWithOrigin;
        private DilutionApplication dilutionApplication;
        private CircleElement previousElement;
        private CircleElement nextElement;

        public CircleElement(DilutionApplication dilutionApplication) {
            this.dilutionApplication = dilutionApplication;
            previousElement = null;
            nextElement = null;
        }

        public CircleElement getNextElement() {
            return nextElement;
        }

        public void setNextElement(CircleElement nextElement) {
            this.nextElement = nextElement;
        }

        public CircleElement getPreviousElement() {
            return previousElement;
        }

        public void setPreviousElement(CircleElement previousElement) {
            this.previousElement = previousElement;
        }

        public DilutionApplication getKeyWithOrigin() {
            return dilutionApplication;
        }
    }

    public static class HashCircleIterator implements Iterator<DilutionApplication> {
        private CircleElement cursor;

        public HashCircleIterator(DilutionApplicationCircle dilutionApplicationCircle) {
            cursor = dilutionApplicationCircle.firstElement;
        }

        @Override
        public boolean hasNext() {
            return cursor != null;
        }

        @Override
        public DilutionApplication next() {
            DilutionApplication dilutionApplication = cursor.dilutionApplication;;
            cursor = cursor.nextElement;
            return dilutionApplication;
        }
    }

    private CircleElement firstElement;
    private CircleElement lastElement;
    private int maxSize;
    private int size;
    private final ByteMap<CircleElement> contentMappedEntries;

    public DilutionApplicationCircle(int maxSize, int hashLength) {
        firstElement = null;
        lastElement = null;
        size = 0;
        this.maxSize = maxSize;
        contentMappedEntries = new ByteMap<>(hashLength);
    }

    public void add(DilutionApplication keyWithOrigin) {
        byte[] key = keyWithOrigin.getKeyWithOrigin().getPublicKey();
        if (lastElement == null) {
            if (size != 0) {
                System.out.println("HashCircle size was " + size + " when the linked list was empty.");
            }
            firstElement = new CircleElement(keyWithOrigin);
            lastElement = firstElement;
            contentMappedEntries.put(key, lastElement);
            size++;
        } else {
            if (!contentMappedEntries.containsKey(key)) {
                CircleElement newElement = new CircleElement(keyWithOrigin);
                lastElement.setNextElement(newElement);
                newElement.setPreviousElement(lastElement);
                lastElement = newElement;
                contentMappedEntries.put(key, lastElement);
                size++;
            }
            if (size >= maxSize) {
                pop(false, null);
            }
        }
    }

/*    public void add(DilutionApplication keyWithOrigin) {
        if (size < maxSize) {
            byte[] key = keyWithOrigin.getKeyWithOrigin().getPublicKey();
            if (lastElement == null) {
                if (size != 0) {
                    System.out.println("HashCircle size was " + size + " when the linked list was empty.");
                }
                firstElement = new CircleElement(keyWithOrigin);
                lastElement = firstElement;
                contentMappedEntries.put(key, lastElement);
                size++;
            } else {
                if (!contentMappedEntries.containsKey(key)) {
                    CircleElement newElement = new CircleElement(keyWithOrigin);
                    lastElement.setNextElement(newElement);
                    newElement.setPreviousElement(lastElement);
                    lastElement = newElement;
                    contentMappedEntries.put(key, lastElement);
                    size++;
                }
            }
        }
    }*/

    public DilutionApplication pop(boolean acceptBlamed, BlameCircle blameSet) {
//        public DilutionApplication pop(boolean acceptBlamed, BlameCircle blameSet) {
        if (size == 0) {
            return null;
        }
        int blameCounter = 0;
        int leastBlamedElementValue = -1;
        CircleElement leastBlamedElement = null;
        CircleElement currentElement = firstElement;
        CircleElement previousElement = null;
        if (blameSet == null) {
            leastBlamedElement = firstElement;
        } else {
            while (currentElement != null && (leastBlamedElementValue == -1 || leastBlamedElementValue > blameCounter)) {
                int blamedElementValue = blameCounter + BLAME_PENALTY * blameSet.getBlameFactor(currentElement.getKeyWithOrigin().getKeyWithOrigin());
                if (leastBlamedElementValue == -1 || blamedElementValue < leastBlamedElementValue) {
                    leastBlamedElementValue = blamedElementValue;
                    leastBlamedElement = currentElement;
                }
                previousElement = currentElement;
                currentElement = currentElement.getNextElement();
                blameCounter++;
            }
        }


/*        while (currentElement != null && blameSet != null && (blameCounter < BLAME_PENALTY * blameSet.getBlameFactor(currentElement.getKeyWithOrigin().getKeyWithOrigin())  || !acceptBlamed)) {
//            while ((blameCounter < BLAME_PENALTY || !acceptBlamed) && currentElement != null && blameSet != null && blameSet.contains(currentElement.getKeyWithOrigin().getKeyWithOrigin().getPublicKey())) {
            previousElement = currentElement;
            currentElement = currentElement.getNextElement();
            blameCounter++;
        }
        if (currentElement == null) {
            if (!acceptBlamed) {
                return null;
            }
            currentElement = firstElement;
            previousElement = null;
        }*/

        if (leastBlamedElement == null) {
            System.out.println("Least blamed element was null while size was " + size);
            return null;
        }

        DilutionApplication toReturn = leastBlamedElement.getKeyWithOrigin();
        contentMappedEntries.remove(toReturn.getKeyWithOrigin().getPublicKey());
        size--;
        CircleElement nextElement = leastBlamedElement.getNextElement();
        if (previousElement == null) {
            firstElement = nextElement;
        } else {
            previousElement.setNextElement(nextElement);
        }
        if (firstElement == null) {
            lastElement = null;
        } else {
            if (nextElement == null) {
                lastElement = previousElement;
            } else {
                nextElement.setPreviousElement(previousElement);
            }
        }
        return toReturn;
    }

    public boolean contains(KeyWithOrigin keyWithOrigin) {
        return contentMappedEntries.containsKey(keyWithOrigin.getPublicKey());
    }

    public int getSize() {
        return size;
    }

    public void remove(byte[] key) {
        CircleElement elementToRemove = contentMappedEntries.remove(key);
        if (elementToRemove != null) {
            size--;
            CircleElement previousElement = elementToRemove.getPreviousElement();
            CircleElement nextElement = elementToRemove.getNextElement();
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
    }

    public void print() {
        CircleElement currentElement = firstElement;
        while (currentElement != null) {
            System.out.println("_^_" + Hex.toHexString(currentElement.dilutionApplication.getKeyWithOrigin().getPublicKey()));
            currentElement = currentElement.nextElement;
        }
    }

    @Override
    public Iterator<DilutionApplication> iterator() {
        return new HashCircleIterator(this);
    }
}
