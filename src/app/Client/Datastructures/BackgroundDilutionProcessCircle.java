package app.Client.Datastructures;

import app.Client.Layers.BlockchainLayer.BackgroundDilutionProcess;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Utils.PrivacyUtils;

public class BackgroundDilutionProcessCircle {
    private static class CircleElement {
        private BackgroundDilutionProcess backgroundDilutionProcess;
        private BackgroundDilutionProcessCircle.CircleElement previousElement;
        private BackgroundDilutionProcessCircle.CircleElement nextElement;

        public CircleElement(BackgroundDilutionProcess backgroundDilutionProcess) {
            this.backgroundDilutionProcess = backgroundDilutionProcess;
            previousElement = null;
            nextElement = null;
        }

        public BackgroundDilutionProcessCircle.CircleElement getNextElement() {
            return nextElement;
        }

        public void setNextElement(BackgroundDilutionProcessCircle.CircleElement nextElement) {
            this.nextElement = nextElement;
        }

        public BackgroundDilutionProcessCircle.CircleElement getPreviousElement() {
            return previousElement;
        }

        public void setPreviousElement(BackgroundDilutionProcessCircle.CircleElement previousElement) {
            this.previousElement = previousElement;
        }

        public BackgroundDilutionProcess getBackgroundDilutionProcess() {
            return backgroundDilutionProcess;
        }
    }

    private BackgroundDilutionProcessCircle.CircleElement firstElement;
    private BackgroundDilutionProcessCircle.CircleElement lastElement;
    private int maxSize;
    private int size;
    private final ByteMap<ByteMap<CircleElement>> contentMappedEntries;

    public BackgroundDilutionProcessCircle(int maxSize) {
        firstElement = null;
        lastElement = null;
        size = 0;
        this.maxSize = maxSize;
        contentMappedEntries = new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH);
    }

    public void add(BackgroundDilutionProcess backgroundDilutionProcess) {
        ByteMap<BackgroundDilutionProcessCircle.CircleElement> contentMappedEntriesForPoolManager;
        if (contentMappedEntries.containsKey(backgroundDilutionProcess.getPoolManager().getPublicKey())) {
            contentMappedEntriesForPoolManager = contentMappedEntries.get(backgroundDilutionProcess.getPoolManager().getPublicKey());
        } else {
            contentMappedEntriesForPoolManager = new ByteMap<>(BlockchainLayer.POOL_IDENTIFIER_SIZE);
            contentMappedEntries.put(backgroundDilutionProcess.getPoolManager().getPublicKey(), contentMappedEntriesForPoolManager);
        }
        BackgroundDilutionProcessCircle.CircleElement currentElement;
        if (contentMappedEntriesForPoolManager.containsKey(backgroundDilutionProcess.getPoolIdentifier())) {
            currentElement = contentMappedEntriesForPoolManager.get(backgroundDilutionProcess.getPoolIdentifier());
            CircleElement previousElement = currentElement.previousElement;
            CircleElement nextElement = currentElement.nextElement;
            if (previousElement != null) {
                previousElement.setNextElement(nextElement);
            }
            if (nextElement != null) {
                nextElement.setPreviousElement(previousElement);
            }
        } else {
            currentElement = new BackgroundDilutionProcessCircle.CircleElement(backgroundDilutionProcess);
            size++;
        }
        currentElement.setNextElement(null);
        if (lastElement == null) {
            currentElement.setPreviousElement(null);
            firstElement = currentElement;
            lastElement = firstElement;
        } else {
            lastElement.setNextElement(currentElement);
            currentElement.setPreviousElement(lastElement);
            lastElement = currentElement;
        }
        contentMappedEntriesForPoolManager.put(backgroundDilutionProcess.getPoolIdentifier(), lastElement);
        while (size > maxSize) {
            pop();
        }
    }

    public BackgroundDilutionProcess pop() {
        if (size == 0) {
            return null;
        }
//        int blameCounter = 0;
        BackgroundDilutionProcessCircle.CircleElement currentElement = firstElement;
//        BackgroundDilutionProcessCircle.CircleElement previousElement = null;
/*        while ((blameCounter < BLAME_PENALTY || !acceptBlamed) && currentElement != null && blameSet != null && blameSet.contains(currentElement.getKeyWithOrigin())) {
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

        BackgroundDilutionProcess toReturn = currentElement.getBackgroundDilutionProcess();
        ByteMap<BackgroundDilutionProcessCircle.CircleElement> contentMappedEntriesForPoolManager = contentMappedEntries.get(toReturn.getPoolManager().getPublicKey());
        if (contentMappedEntriesForPoolManager != null) {
            contentMappedEntriesForPoolManager.remove(toReturn.getPoolIdentifier());
            if (contentMappedEntriesForPoolManager.size() == 0) {
                contentMappedEntries.remove(toReturn.getPoolManager().getPublicKey());
            }
        }
//        contentMappedEntries.remove(toReturn.getPublicKey());
        size--;
        BackgroundDilutionProcessCircle.CircleElement nextElement = currentElement.getNextElement();
//        if (previousElement == null) {
            firstElement = nextElement;
//        } else {
//            previousElement.setNextElement(nextElement);
//        }
        if (firstElement == null) {
            lastElement = null;
        } else {
            firstElement.setPreviousElement(null);
        }
        return toReturn;
    }

    public boolean contains(byte[] poolManager, byte[] poolIdentifier) {
        if (!contentMappedEntries.containsKey(poolManager)) {
            return false;
        }
        ByteMap<BackgroundDilutionProcessCircle.CircleElement> contentMappedEntriesForPoolManager = contentMappedEntries.get(poolManager);
        return contentMappedEntriesForPoolManager.containsKey(poolIdentifier);
    }

    public BackgroundDilutionProcess get(byte[] poolManager, byte[] poolIdentifier) {
        if (!contentMappedEntries.containsKey(poolManager)) {
            return null;
        }
        ByteMap<BackgroundDilutionProcessCircle.CircleElement> contentMappedEntriesForPoolManager = contentMappedEntries.get(poolManager);
        if (!contentMappedEntriesForPoolManager.containsKey(poolIdentifier)) {
            return null;
        }
        return contentMappedEntriesForPoolManager.get(poolIdentifier).backgroundDilutionProcess;
    }

    public int getSize() {
        return size;
    }

    public void remove(byte[] poolManagerPublicKey, byte[] poolIdentifier) {
        ByteMap<BackgroundDilutionProcessCircle.CircleElement> contentMappedEntriesForPoolManager = contentMappedEntries.get(poolManagerPublicKey);
        if (contentMappedEntriesForPoolManager != null) {
            BackgroundDilutionProcessCircle.CircleElement elementToRemove = contentMappedEntriesForPoolManager.remove(poolIdentifier);
            if (elementToRemove != null) {
                size--;
                BackgroundDilutionProcessCircle.CircleElement previousElement = elementToRemove.getPreviousElement();
                BackgroundDilutionProcessCircle.CircleElement nextElement = elementToRemove.getNextElement();
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
    }
}
