package app.Client.Datastructures;

import app.Client.Layers.BlockchainLayer.KeyWithOrigin;

public class BlameCircle {
    private static class CircleElement {
        private KeyWithOrigin keyWithOrigin;
        private KeyWithOrigin blockAssembler;
        private byte[] poolIdentifier;
        private CircleElement previousElement;
        private CircleElement nextElement;

        public CircleElement(KeyWithOrigin keyWithOrigin, KeyWithOrigin blockAssembler, byte[] poolIdentifier) {
            this.keyWithOrigin = keyWithOrigin;
            this.blockAssembler = blockAssembler;
            this.poolIdentifier = poolIdentifier;
            previousElement = null;
            nextElement = null;
        }

        public KeyWithOrigin getKeyWithOrigin() {
            return keyWithOrigin;
        }

        public KeyWithOrigin getBlockAssembler() {
            return blockAssembler;
        }

        public byte[] getPoolIdentifier() {
            return poolIdentifier;
        }

        public CircleElement getPreviousElement() {
            return previousElement;
        }

        public CircleElement getNextElement() {
            return nextElement;
        }

        public void setPreviousElement(CircleElement previousElement) {
            this.previousElement = previousElement;
        }

        public void setNextElement(CircleElement nextElement) {
            this.nextElement = nextElement;
        }
    }

    private CircleElement firstElement;
    private CircleElement lastElement;
    private int maxSize;
    private int size;
    private final int hashLength;
    private final int poolIdentifierSize;
    private final ByteMap<ByteMap<ByteMap<CircleElement>>> contentMappedEntries;

    public BlameCircle(int maxSize, int hashLength, int poolIdentifierSize) {
        firstElement = null;
        lastElement = null;
        size = 0;
        this.maxSize = maxSize;
        this.hashLength = hashLength;
        this.poolIdentifierSize = poolIdentifierSize;
        contentMappedEntries = new ByteMap<>(hashLength);
    }

    public void add(KeyWithOrigin keyWithOrigin, KeyWithOrigin blockAssembler, byte[] poolIdentifier) {
        CircleElement currentElement = new CircleElement(keyWithOrigin, blockAssembler, poolIdentifier);
        if (lastElement == null) {
            firstElement = currentElement;
        } else {
            lastElement.setNextElement(currentElement);
            currentElement.setPreviousElement(lastElement);
        }
        lastElement = currentElement;
        size++;
        if (!contentMappedEntries.containsKey(keyWithOrigin.getPublicKey())) {
            contentMappedEntries.put(keyWithOrigin.getPublicKey(), new ByteMap<>(hashLength));
        }
        ByteMap<ByteMap<CircleElement>> contentMappedEntriesOfKey = contentMappedEntries.get(keyWithOrigin.getPublicKey());
        if (!contentMappedEntriesOfKey.containsKey(blockAssembler.getPublicKey())) {
            contentMappedEntriesOfKey.put(blockAssembler.getPublicKey(), new ByteMap<>(poolIdentifierSize));
        }
        ByteMap<CircleElement> contentMappedEntriesPerPool = contentMappedEntriesOfKey.get(blockAssembler.getPublicKey());
        if (contentMappedEntriesPerPool.containsKey(poolIdentifier)) {
            CircleElement elementToRemove = contentMappedEntriesPerPool.get(poolIdentifier);
            CircleElement elementBeforeElementToRemove = elementToRemove.getPreviousElement();
            CircleElement elementAfterElementToRemove = elementToRemove.getNextElement();
            if (elementBeforeElementToRemove == null) {
                firstElement = elementAfterElementToRemove;
            } else {
                elementBeforeElementToRemove.setNextElement(elementAfterElementToRemove);
            }
            if (elementAfterElementToRemove == null) {
                lastElement = elementBeforeElementToRemove;
            } else {
                elementAfterElementToRemove.setPreviousElement(elementBeforeElementToRemove);
            }
            size--;
        }
        contentMappedEntriesPerPool.put(poolIdentifier, currentElement);
        while (size > maxSize) {
            pop();
        }
    }

    public KeyWithOrigin pop() {
        if (firstElement == null) {
            return null;
        } else {
            KeyWithOrigin toReturn = firstElement.getKeyWithOrigin();
            if (contentMappedEntries.containsKey(toReturn.getPublicKey())) {
                ByteMap<ByteMap<CircleElement>> contentMappedEntriesOfKey = contentMappedEntries.get(toReturn.getPublicKey());
                if (contentMappedEntriesOfKey.containsKey(firstElement.getBlockAssembler().getPublicKey())) {
                    ByteMap<CircleElement> contentMappedEntriesPerPool = contentMappedEntriesOfKey.get(firstElement.getBlockAssembler().getPublicKey());
                    contentMappedEntriesPerPool.remove(firstElement.getPoolIdentifier());
                    if (contentMappedEntriesPerPool.size() == 0) {
                        contentMappedEntriesOfKey.remove(firstElement.getBlockAssembler().getPublicKey());
                    }
                }
                if (contentMappedEntriesOfKey.size() == 0) {
                    contentMappedEntries.remove(firstElement.getKeyWithOrigin().getPublicKey());
                }
            }
            firstElement = firstElement.getNextElement();
            firstElement.setPreviousElement(null);
            size--;
            return toReturn;
        }
    }

    public void remove(KeyWithOrigin keyWithOrigin, KeyWithOrigin blockAssembler, byte[] poolIdentifier) {
        if (contentMappedEntries.containsKey(keyWithOrigin.getPublicKey())) {
            ByteMap<ByteMap<CircleElement>> contentMappedEntriesOfKey = contentMappedEntries.get(keyWithOrigin.getPublicKey());
            if (contentMappedEntriesOfKey.containsKey(blockAssembler.getPublicKey())) {
                ByteMap<CircleElement> contentMappedEntriesPerPool = contentMappedEntriesOfKey.get(blockAssembler.getPublicKey());
                if (contentMappedEntriesPerPool.containsKey(poolIdentifier)) {
                    CircleElement elementToRemove = contentMappedEntriesPerPool.get(poolIdentifier);
                    CircleElement elementBeforeElementToRemove = elementToRemove.getPreviousElement();
                    CircleElement elementAfterElementToRemove = elementToRemove.getNextElement();
                    if (elementBeforeElementToRemove == null) {
                        firstElement = elementAfterElementToRemove;
                    } else {
                        elementBeforeElementToRemove.setNextElement(elementAfterElementToRemove);
                    }
                    if (elementAfterElementToRemove == null) {
                        lastElement = elementBeforeElementToRemove;
                    } else {
                        elementAfterElementToRemove.setPreviousElement(elementBeforeElementToRemove);
                    }
                    size--;
                    contentMappedEntriesPerPool.remove(poolIdentifier);
                }
                if (contentMappedEntriesPerPool.size() == 0) {
                    contentMappedEntriesOfKey.remove(blockAssembler.getPublicKey());
                }
            }
            if (contentMappedEntriesOfKey.size() == 0) {
                contentMappedEntries.remove(keyWithOrigin.getPublicKey());
            }
        }
    }

    public int getSize() {
        return size;
    }

    public int getBlameFactor(KeyWithOrigin keyWithOrigin) {
        if (contentMappedEntries.containsKey(keyWithOrigin)) {
            ByteMap<ByteMap<CircleElement>> contentMappedEntriesOfKey = contentMappedEntries.get(keyWithOrigin.getPublicKey());
            int blameFactor = 0;
            for (byte[] blockAssembler: contentMappedEntriesOfKey.keySet()) {
                blameFactor += contentMappedEntriesOfKey.get(blockAssembler).size();

            }
            return blameFactor;
        } else {
            return 0;
        }
    }
}
