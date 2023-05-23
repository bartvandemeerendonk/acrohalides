package app.Client.Datastructures;

import app.Client.Datastructures.ByteSet;
import app.Client.Utils.ByteUtils;

import java.util.*;

public class VariableLengthByteSet implements Set<byte[]> {
    private ArrayList<byte[]> set;

    public VariableLengthByteSet() {
        set = new ArrayList<>();
    }

    public boolean add(byte[] byteString) {
        if (!contains(byteString)) {
            set.add(byteString);
            return true;
        }
        return false;
    }

    @Override
    public boolean remove(Object o) {
        boolean containsByteString = false;
        if (o.getClass() == byte[].class) {
            byte[] byteString = (byte[]) o;
            for (byte[] byteStringInSet: set) {
                if (!containsByteString && ByteUtils.byteArraysAreEqual(byteString, byteStringInSet)) {
                    byteString = byteStringInSet;
                    containsByteString = true;
                }
            }
            set.remove(byteString);
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
//            Object[] array = new Object[set.size()];
//            int i = 0;
//            Iterator<byte[]> setIterator = set.iterator();
//            while (setIterator.hasNext()) {
//                array[i] = setIterator.next();
//                i++;
//            }
//            for (Object byteStringInSetObject: set) {
//                array[i] = byteStringInSetObject;
//                i++;
//            }
//            containsByteString = set.stream().filter(byteStringInSet -> ByteUtils.byteArraysAreEqual(byteString, byteStringInSet)).count() > 0;
            for (Object byteStringInSetObject: set) {
                byte[] byteStringInSet = (byte[]) byteStringInSetObject;
                if (ByteUtils.byteArraysAreEqual(byteString, byteStringInSet)) {
                    containsByteString = true;
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
}
