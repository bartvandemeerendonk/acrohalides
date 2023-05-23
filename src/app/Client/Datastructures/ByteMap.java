package app.Client.Datastructures;

import app.Client.Utils.ByteUtils;

import java.util.*;

public class ByteMap<T> implements Map<byte[], T> {
    private static final int DIRECT_MAP_MAX_SIZE = 16;

    private int length;
    private HashMap<byte[], T> directMap;
    private HashMap<Byte, ByteMap<T>> recursiveMap;
//    private Map<Byte, Map<byte[], T>> map;
    private int depth;

    public ByteMap(int length) {
        directMap = new HashMap<>();
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
        for (ByteMap<T> submap: recursiveMap.values()) {
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
    public boolean containsKey(Object key) {
        boolean containsKeyByteString = false;
        if (key.getClass() == byte[].class) {
            byte[] keyByteString = (byte[]) key;
            if (keyByteString.length == length) {
                if (recursiveMap.isEmpty()) {
                    for (byte[] keyInMap: directMap.keySet()) {
                        if (!containsKeyByteString && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
                            containsKeyByteString = true;
                        }
                    }
                } else {
                    ByteMap<T> submap = recursiveMap.get(getByteStringHash(keyByteString));
                    if (submap != null) {
                        containsKeyByteString = submap.containsKey(key);
                    }
                }
/*                Map<byte[], T> submap = map.get(getByteStringHash(keyByteString));
                if (submap != null) {
                    for (byte[] keyInMap : submap.keySet()) {
                        if (!containsKeyByteString && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
                            containsKeyByteString = true;
                        }
                    }
                }*/
            }
        }
        return containsKeyByteString;
    }

    @Override
    public boolean containsValue(Object value) {
        if (recursiveMap.isEmpty()) {
            return directMap.containsValue(value);
        } else {
            for (ByteMap subMap: recursiveMap.values()) {
                if (subMap.containsValue(value)) {
                    return true;
                }
            }
            return false;
        }
//        return map.containsValue(value);
    }

    @Override
    public T get(Object key) {
        if (key.getClass() == byte[].class) {
            byte[] keyByteString = (byte[]) key;
            T toReturn = null;
            if (keyByteString.length == length) {
                if (recursiveMap.isEmpty()) {
                    for (byte[] keyInMap : directMap.keySet()) {
                        if (toReturn == null && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
                            toReturn = directMap.get(keyInMap);
                        }
                    }
                } else {
                    ByteMap<T> submap = recursiveMap.get(getByteStringHash(keyByteString));
                    if (submap != null) {
                        toReturn = submap.get(key);
                    }
                }
//                Map<byte[], T> submap = map.get(getByteStringHash(keyByteString));
//                if (submap != null) {
//                    for (byte[] keyInMap : submap.keySet()) {
//                        if (toReturn == null && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
//                            toReturn = submap.get(keyInMap);
//                        }
//                    }
//                }
            }
            return toReturn;
        } else {
            return null;
        }
    }

    @Override
    public T put(byte[] key, T value) {
        boolean containsKeyByteString = false;
        byte byteStringHash = getByteStringHash(key);
        if (key.length == length) {
            if (recursiveMap.isEmpty()) {
                for (byte[] keyInMap: directMap.keySet()) {
                    if (!containsKeyByteString && ByteUtils.byteArraysAreEqual(key, keyInMap)) {
                        containsKeyByteString = true;
                        return directMap.put(keyInMap, value);
                    }
                }
                if (!containsKeyByteString && directMap.size() > DIRECT_MAP_MAX_SIZE) {
                    for (byte[] keyInMap: directMap.keySet()) {
                        byte keyInMapHash = getByteStringHash(keyInMap);
                        if (!recursiveMap.containsKey(keyInMapHash)) {
                            ByteMap<T> submap = new ByteMap<T>(length);
                            submap.setDepth(depth + 1);
                            recursiveMap.put(keyInMapHash, submap);
                        }
                        recursiveMap.get(keyInMapHash).put(keyInMap, directMap.get(keyInMap));
                    }
                    if (!recursiveMap.containsKey(byteStringHash)) {
                        ByteMap<T> submap = new ByteMap<T>(length);
                        submap.setDepth(depth + 1);
                        recursiveMap.put(byteStringHash, submap);
                    }
                    directMap.clear();
                    return recursiveMap.get(byteStringHash).put(key, value);
                }
                return directMap.put(key, value);
            } else {
                if (!recursiveMap.containsKey(byteStringHash)) {
                    ByteMap<T> submap = new ByteMap<T>(length);
                    submap.setDepth(depth + 1);
                    recursiveMap.put(byteStringHash, submap);
                }
                return recursiveMap.get(byteStringHash).put(key, value);
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
            return null;
        }
    }

    @Override
    public T remove(Object key) {
//        System.out.println("ByteMap.remove() 1");
        if (key.getClass() == byte[].class) {
//            System.out.println("ByteMap.remove() 2");
            byte[] keyByteString = (byte[]) key;
            T toReturn = null;
            if (keyByteString.length == length) {
//                System.out.println("ByteMap.remove() 3");
                if (recursiveMap.isEmpty()) {
//                    System.out.println("ByteMap.remove() 4");
                    for (byte[] keyInMap : directMap.keySet()) {
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
                        if (toReturn == null && ByteUtils.byteArraysAreEqual(keyByteString, keyInMap)) {
//                            System.out.println("ByteMap.remove() 5");
                            return directMap.remove(keyInMap);
//                        } else {
//                            System.out.println("ByteMap.remove() 5x");
                        }
                    }
  //                  System.out.println("ByteMap.remove() 6");
                } else {
                    ByteMap<T> submap = recursiveMap.get(getByteStringHash(keyByteString));
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
            return null;
        }
    }

    @Override
    public void putAll(Map<? extends byte[], ? extends T> m) {
        for (byte[] key: m.keySet()) {
            put(key, m.get(key));
        }
    }

    @Override
    public void clear() {
        directMap.clear();
        recursiveMap.clear();
//        map.clear();
    }

    @Override
    public Set<byte[]> keySet() {
        if (recursiveMap.isEmpty()) {
            return directMap.keySet();
        } else {
            Set<byte[]> toReturn = new HashSet<>();
            for (ByteMap<T> submap: recursiveMap.values()) {
                toReturn.addAll(submap.keySet());
            }
/*        for (Map<byte[], T> submap: map.values()) {
            toReturn.addAll(submap.keySet());
        }*/
            return toReturn;
        }
    }

    @Override
    public Collection<T> values() {
        if (recursiveMap.isEmpty()) {
            return directMap.values();
        } else {
            Set<T> toReturn = new HashSet<>();
            for (ByteMap<T> submap : recursiveMap.values()) {
                toReturn.addAll(submap.values());
            }
//            for (Map<byte[], T> submap : map.values()) {
//                toReturn.addAll(submap.values());
//            }
            return toReturn;
        }
    }

    @Override
    public Set<Entry<byte[], T>> entrySet() {
        if (recursiveMap.isEmpty()) {
            return directMap.entrySet();
        } else {
            Set<Entry<byte[], T>> toReturn = new HashSet<>();
            for (ByteMap<T> submap : recursiveMap.values()) {
                toReturn.addAll(submap.entrySet());
            }
//            for (Map<byte[], T> submap : map.values()) {
//                toReturn.addAll(submap.entrySet());
//            }
            return toReturn;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() == ByteMap.class) {
            ByteMap oByteMap = (ByteMap) o;
            if (size() == oByteMap.size()) {
                boolean isEqual = true;
                for (Object key: oByteMap.keySet()) {
                    if (isEqual && get(key) != oByteMap.get(key)) {
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
}
