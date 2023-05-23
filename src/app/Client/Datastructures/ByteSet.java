package app.Client.Datastructures;

import app.Client.Utils.ByteUtils;

import java.util.*;

public class ByteSet implements Set<byte[]> {
    private static class ByteSetIterator implements Iterator<byte[]> {
        private final int iteratorType;
        private Iterator<byte[]> directCursor;
        private Iterator<Byte> recursiveKeyCursor;
        private Iterator<byte[]> recursiveValueCursor;
        private HashMap<Byte, ByteSet> recursiveMap;

        public ByteSetIterator(ByteSet byteSet) {
            if (!byteSet.directMap.isEmpty()) {
                directCursor = byteSet.directMap.iterator();
                iteratorType = 0;
            } else if (!byteSet.recursiveMap.isEmpty()) {
                recursiveMap = byteSet.recursiveMap;
                recursiveKeyCursor = recursiveMap.keySet().iterator();
                if (recursiveKeyCursor.hasNext()) {
                    recursiveValueCursor = recursiveMap.get(recursiveKeyCursor.next()).iterator();
                }
                iteratorType = 1;
            } else {
                iteratorType = 2;
            }
        }

        @Override
        public boolean hasNext() {
            switch (iteratorType) {
                case 0:
                    return directCursor.hasNext();
                case 1:
                    if (recursiveValueCursor.hasNext()) {
                        return true;
                    } else {
                        return recursiveKeyCursor.hasNext();
                    }
            }
            return false;
        }

        @Override
        public byte[] next() {
            switch (iteratorType) {
                case 0:
                    return directCursor.next();
                case 1:
                    byte[] toReturn = recursiveValueCursor.next();
                    if (!recursiveValueCursor.hasNext() && recursiveKeyCursor.hasNext()) {
                        recursiveValueCursor = recursiveMap.get(recursiveKeyCursor.next()).iterator();
                    }
                    return toReturn;
            }
            return null;
        }
    }

    private static final int DIRECT_MAP_MAX_SIZE = 16;

    private int length;
    private HashSet<byte[]> directMap;
    private HashMap<Byte, ByteSet> recursiveMap;
    //    private Map<Byte, Map<byte[], T>> map;
    private int depth;

    public ByteSet(int length) {
        directMap = new HashSet<>();
        recursiveMap = new HashMap<>();
//        map = new HashMap<>();
        this.length = length;
        depth = 0;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public byte getByteStringHash(byte[] byteString) {
        return byteString[depth];
    }

    @Override
    public int size() {
        int toReturn = directMap.size();
        for (ByteSet submap: recursiveMap.values()) {
            toReturn += submap.size();
        }
//        for (Map<byte[], T> submap: map.values()) {
//            toReturn += submap.size();
//        }
        return toReturn;
    }

    @Override
    public boolean isEmpty() {
        return directMap.isEmpty() && recursiveMap.isEmpty();
//        return map.isEmpty();
    }

    @Override
    public boolean contains(Object key) {
        boolean containsKeyByteString = false;
        if (key.getClass() == byte[].class) {
            byte[] keyByteString = (byte[]) key;
            if (keyByteString.length == length) {
                if (recursiveMap.isEmpty()) {
                    for (byte[] keyInMap: directMap) {
                        if (!containsKeyByteString && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
                            containsKeyByteString = true;
                        }
                    }
                } else {
                    ByteSet submap = recursiveMap.get(getByteStringHash(keyByteString));
                    if (submap != null) {
                        containsKeyByteString = submap.contains(key);
                    }
                }
            }
        }
        return containsKeyByteString;
    }

    @Override
    public boolean add(byte[] key) {
        boolean containsKeyByteString = false;
        byte byteStringHash = getByteStringHash(key);
        if (key.length == length) {
            if (recursiveMap.isEmpty()) {
                for (byte[] keyInMap: directMap) {
                    if (!containsKeyByteString && ByteUtils.byteArraysAreEqual(key, keyInMap)) {
                        containsKeyByteString = true;
                        return false;
                    }
                }
                if (!containsKeyByteString && directMap.size() >= DIRECT_MAP_MAX_SIZE) {
                    for (byte[] keyInMap: directMap) {
                        byte keyInMapHash = getByteStringHash(keyInMap);
                        if (!recursiveMap.containsKey(keyInMapHash)) {
                            ByteSet submap = new ByteSet(length);
                            submap.setDepth(depth + 1);
                            recursiveMap.put(keyInMapHash, submap);
                        }
                        recursiveMap.get(keyInMapHash).add(keyInMap);
                    }
                    if (!recursiveMap.containsKey(byteStringHash)) {
                        ByteSet submap = new ByteSet(length);
                        submap.setDepth(depth + 1);
                        recursiveMap.put(byteStringHash, submap);
                    }
                    directMap.clear();
                    return recursiveMap.get(byteStringHash).add(key);
                }
                return directMap.add(key);
            } else {
                if (!recursiveMap.containsKey(byteStringHash)) {
                    ByteSet submap = new ByteSet(length);
                    submap.setDepth(depth + 1);
                    recursiveMap.put(byteStringHash, submap);
                }
                return recursiveMap.get(byteStringHash).add(key);
            }
/*            Map<byte[], T> submap = map.get(getByteStringHash(key));
            if (submap == null) {
                submap = new HashMap<>();
                map.put(getByteStringHash(key), submap);
            } else {
                for (byte[] keyInMap : submap.keySet()) {
                    if (!containsKeyByteString && ByteUtils.byteArraysAreEqual(key, keyInMap)) {
                        key = keyInMap;
                        containsKeyByteString = true;
                    }
                }
            }
            return submap.put(key, value);*/
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(Object key) {
//        System.out.println("ByteMap.remove() 1");
        if (key.getClass() == byte[].class) {
//            System.out.println("ByteMap.remove() 2");
            byte[] keyByteString = (byte[]) key;
            boolean toReturn = false;
            if (keyByteString.length == length) {
//                System.out.println("ByteMap.remove() 3");
                if (recursiveMap.isEmpty()) {
//                    System.out.println("ByteMap.remove() 4");
                    for (byte[] keyInMap : directMap) {
/*                        if (toReturn == null) {
                            System.out.println("toReturn == null");
                            if (ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
                                System.out.println("byteArraysAreEqual");
                            } else {
                                System.out.println("!byteArraysAreEqual");
                            }
                        } else {
                            System.out.println("toReturn != null");
                        }*/
                        if (!toReturn && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
//                            System.out.println("ByteMap.remove() 5");
                            return directMap.remove(keyInMap);
//                        } else {
//                            System.out.println("ByteMap.remove() 5x");
                        }
                    }
                    //                  System.out.println("ByteMap.remove() 6");
                } else {
                    ByteSet submap = recursiveMap.get(getByteStringHash(keyByteString));
                    if (submap != null) {
                        toReturn = submap.remove(key);
                    }
                }
/*                Map<byte[], T> submap = map.get(getByteStringHash(keyByteString));
                if (submap != null) {
                    for (byte[] keyInMap : submap.keySet()) {
                        if (toReturn == null && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
                            toReturn = submap.remove(keyInMap);
                        }
                    }
                }*/
//                System.out.println("ByteMap.remove() 7");
            }
//            System.out.println("ByteMap.remove() 8");
            return toReturn;
        } else {
            return false;
        }
    }

/*    @Override
    public void putAll(Map<? extends byte[], ? extends T> m) {
        for (byte[] key: m.keySet()) {
            put(key, m.get(key));
        }
    }*/

    @Override
    public void clear() {
        directMap.clear();
        recursiveMap.clear();
//        map.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() == ByteSet.class) {
            ByteSet oByteSet = (ByteSet) o;
            if (size() == oByteSet.size()) {
                boolean isEqual = true;
                for (Object key: oByteSet) {
                    if (isEqual && !contains(key)) {
                        isEqual = false;
                    }
                }
                return isEqual;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        if (recursiveMap.isEmpty()) {
            return directMap.hashCode();
        } else {
            return recursiveMap.hashCode();
        }
//        return map.hashCode();
    }

    public int getLength() {
        return length;
    }

    @Override
    public Iterator<byte[]> iterator() {
        return new ByteSetIterator(this);
    }

    @Override
    public Object[] toArray() {
        if (recursiveMap.isEmpty()) {
            return directMap.toArray();
        } else {
            int arraySize = 0;
            for (Byte byteKey: recursiveMap.keySet()) {
                arraySize += recursiveMap.get(byteKey).size();
            }
            Object[] toReturn = new Object[arraySize];
            int arrayIndex = 0;
            for (Byte byteKey: recursiveMap.keySet()) {
                ByteSet byteSet = recursiveMap.get(byteKey);
                for (byte[] byteString: byteSet) {
                    toReturn[arrayIndex] = byteString;
                    arrayIndex++;
                }
            }
            return  toReturn;
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        if (recursiveMap.isEmpty()) {
            return directMap.toArray(a);
        } else {
            int arraySize = 0;
            for (Byte byteKey: recursiveMap.keySet()) {
                arraySize += recursiveMap.get(byteKey).size();
            }
            Object[] toReturn = new Object[arraySize];
            int arrayIndex = 0;
            for (Byte byteKey: recursiveMap.keySet()) {
                ByteSet byteSet = recursiveMap.get(byteKey);
                for (byte[] byteString: byteSet) {
                    toReturn[arrayIndex] = byteString;
                    arrayIndex++;
                }
            }
            //TODO: fix cast errors
            return (T[]) toReturn;
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        boolean toReturn = true;
        for (Object object: c) {
            if (!contains(object)) {
                toReturn = false;
            }
        }
        return toReturn;
    }

    @Override
    public boolean addAll(Collection<? extends byte[]> c) {
        boolean toReturn = false;
        for (byte[] byteString: c) {
            if (add(byteString)) {
                toReturn = true;
            }
        }
        return toReturn;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        ByteSet toRemove = new ByteSet(length);
        for (byte[] byteString: this) {
            boolean setToRemove = true;
            for (Object object: c) {
                if (ByteUtils.byteArraysAreEqual(byteString, (byte[]) object)) {
                    setToRemove = false;
                }
            }
            if (setToRemove) {
                toRemove.add(byteString);
            }
        }
        return removeAll(toRemove);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean removedOne = false;
        for (Object object: c) {
            if (remove(object)) {
                removedOne = true;
            }
        }
        return removedOne;
    }

    /*    private Set<byte[]> set;
    private int length;

    public ByteSet(int length) {
        set = new HashSet<>();
        this.length = length;
    }

    public boolean add(byte[] byteString) {
        if (byteString.length == length) {
            if (!contains(byteString)) {
                set.add(byteString);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(Object o) {
        boolean containsByteString = false;
        if (o.getClass() == byte[].class) {
            byte[] byteString = (byte[]) o;
            if (byteString.length == length) {
                for (byte[] byteStringInSet: set) {
                    if (!containsByteString && ByteUtils.byteArraysAreEqual(byteString, byteStringInSet)) {
                        byteString = byteStringInSet;
                        containsByteString = true;
                    }
                }
                set.remove(byteString);
            }
        }
        return containsByteString;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        if (c.getClass().componentType() == byte[].class) {
            Collection<? extends byte[]> byteStringCollection = (Collection<? extends byte[]>) c;
            boolean containsStrings = true;
            for (byte[] byteString : byteStringCollection) {
                if (containsStrings && !contains(byteString)) {
                    containsStrings = false;
                }
            }
            return containsStrings;
        } else {
            return false;
        }
    }

    @Override
    public boolean addAll(Collection<? extends byte[]> c) {
        boolean isChanged = false;
        for (byte[] byteString: c) {
            if (add(byteString)) {
                isChanged = true;
            }
        }
        return isChanged;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        if (c.getClass().componentType() == byte[].class) {
            Collection<? extends byte[]> byteStringCollection = (Collection<? extends byte[]>) c;
            HashSet<byte[]> toRetain = new HashSet<>();
            for (byte[] byteString : set) {
                for (byte[] byteStringInC: byteStringCollection) {
                    if (ByteUtils.byteArraysAreEqual(byteString, byteStringInC)) {
                        toRetain.add(byteString);
                    }
                }
            }
            return set.retainAll(toRetain);
        } else {
            return false;
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        if (c.getClass().componentType() == byte[].class) {
            Collection<? extends byte[]> byteStringCollection = (Collection<? extends byte[]>) c;
            boolean isChanged = false;
            for (byte[] byteString : byteStringCollection) {
                if (remove(byteString)) {
                    isChanged = true;
                }
            }
            return isChanged;
        } else {
            return false;
        }
    }

    @Override
    public void clear() {
        set.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() == ByteSet.class) {
            ByteSet otherByteSet = (ByteSet) o;
            boolean isEqual = true;
            for (byte[] byteString: otherByteSet) {
                if (isEqual && !contains(byteString)) {
                    isEqual = false;
                }
            }
            return isEqual;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return set.hashCode();
    }

    public int size() {
        return set.size();
    }

    public boolean isEmpty() {
        return set.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        boolean containsByteString = false;
        if (o.getClass() == byte[].class) {
            byte[] byteString = (byte[]) o;
            if (byteString.length == length) {
                for (byte[] byteStringInSet: set) {
                    if (!containsByteString && ByteUtils.byteArraysAreEqual(byteString, byteStringInSet)) {
                        containsByteString = true;
                    }
                }
            }
        }
        return containsByteString;
    }

    @Override
    public Iterator<byte[]> iterator() {
        return set.iterator();
    }

    @Override
    public Object[] toArray() {
        byte[][] array = new byte[set.size()][];
        int index = 0;
        for(byte[] byteString: set) {
            array[index] = byteString;
            index++;
        }
        return array;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return null;
    }
/*    private HashMap<Integer, Set<byte[]>> setsPerLength;

    public ByteSet() {
        setsPerLength = new HashMap<>();
    }

    public void add(byte[] byteString) {
        if (!setsPerLength.containsKey(byteString.length)) {
            setsPerLength.put(byteString.length, new HashSet<>());
        }
        Set<byte[]> setOfCurrentLength = setsPerLength.get(byteString.length);

    }*/
}
