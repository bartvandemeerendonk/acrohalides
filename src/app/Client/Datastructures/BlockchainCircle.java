package app.Client.Datastructures;

import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;

import java.util.Iterator;

public class BlockchainCircle implements Iterable<Blockchain> {
    private static class CircleElement {
        private Blockchain blockchain;
        private BlockchainCircle.CircleElement previousElement;
        private BlockchainCircle.CircleElement nextElement;
        private byte[] hash;

        public CircleElement(Blockchain blockchain, byte[] hash) {
            this.blockchain = blockchain;
            previousElement = null;
            nextElement = null;
            this.hash = hash;
        }

        public BlockchainCircle.CircleElement getNextElement() {
            return nextElement;
        }

        public void setNextElement(BlockchainCircle.CircleElement nextElement) {
            this.nextElement = nextElement;
        }

        public BlockchainCircle.CircleElement getPreviousElement() {
            return previousElement;
        }

        public void setPreviousElement(BlockchainCircle.CircleElement previousElement) {
            this.previousElement = previousElement;
        }

        public Blockchain getBlockchain() {
            return blockchain;
        }

        public byte[] getHash() {
            return hash;
        }
    }

    private static class BlockchainCircleIterator implements Iterator<Blockchain> {
        private CircleElement cursor;

        public BlockchainCircleIterator(BlockchainCircle blockchainCircle) {
            cursor = blockchainCircle.firstElement;
        }

        @Override
        public boolean hasNext() {
            return cursor != null;
        }

        @Override
        public Blockchain next() {
            Blockchain blockchain = cursor.getBlockchain();
            cursor = cursor.getNextElement();
            return blockchain;
        }
    }

    private BlockchainCircle.CircleElement firstElement;
    private BlockchainCircle.CircleElement lastElement;
    private int maxSize;
    private int size;
    private final ByteMap<CircleElement> contentMappedEntries;

    public BlockchainCircle(int maxSize, int hashLength) {
        firstElement = null;
        lastElement = null;
        size = 0;
        this.maxSize = maxSize;
        contentMappedEntries = new ByteMap<>(hashLength);
    }

    public void add(byte[] hash, Blockchain keyWithOrigin) {
        remove(hash);
        if (lastElement == null) {
            firstElement = new BlockchainCircle.CircleElement(keyWithOrigin, hash);
            lastElement = firstElement;
        } else {
            BlockchainCircle.CircleElement newElement = new BlockchainCircle.CircleElement(keyWithOrigin, hash);
            lastElement.setNextElement(newElement);
            newElement.setPreviousElement(lastElement);
            lastElement = newElement;
        }
        contentMappedEntries.put(hash, lastElement);
        size++;
        while (size > maxSize) {
            pop();
        }
    }

    public Blockchain pop() {
        if (size == 0) {
            return null;
        }
        Blockchain toReturn = firstElement.getBlockchain();
        contentMappedEntries.remove(firstElement.getHash());
        size--;
        firstElement = firstElement.getNextElement();
        if (firstElement == null) {
            lastElement = null;
        } else {
            firstElement.setPreviousElement(null);
        }
        return toReturn;
    }

    public boolean contains(byte[] hash) {
        return contentMappedEntries.containsKey(hash);
    }

    public Blockchain get(byte[] hash) {
        CircleElement circleElement = contentMappedEntries.get(hash);
        if (circleElement == null) {
            return null;
        } else {
            return circleElement.getBlockchain();
        }
    }

    public int getSize() {
        return size;
    }

    public void remove(byte[] key) {
        BlockchainCircle.CircleElement elementToRemove = contentMappedEntries.remove(key);
        if (elementToRemove != null) {
            size--;
            BlockchainCircle.CircleElement previousElement = elementToRemove.getPreviousElement();
            BlockchainCircle.CircleElement nextElement = elementToRemove.getNextElement();
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

    @Override
    public Iterator<Blockchain> iterator() {
        return new BlockchainCircleIterator(this);
    }
}
