package app.Client.Utils;

import java.util.ArrayList;
import java.util.List;

public class ByteUtils {
    public static byte[] concatenateByteArrays(List<byte[]> byteArrays) {
        int length = 0;
        int byteArrayIndex = 0;
        for (byte[] byteArray: byteArrays) {
            if (byteArray == null) {
                System.out.println("Byte array index: " + byteArrayIndex);
            }
            length += byteArray.length;
            byteArrayIndex++;
        }
        byte[] concatenatedByteArray = new byte[length];
        int offsetInConcatenatedArray = 0;
        for (int i = 0; i < byteArrays.size(); i++) {
            byte[] currentByteArray = byteArrays.get(i);
            for (int j = 0; j < currentByteArray.length; j++) {
                concatenatedByteArray[offsetInConcatenatedArray + j] = currentByteArray[j];
            }
            offsetInConcatenatedArray += currentByteArray.length;
        }
        return concatenatedByteArray;
    }

    public static boolean byteArraysAreEqual(byte[] array1, byte[] array2) {
        if (array1 == null || array2 == null) {
            return false;
        } else {
            boolean areEqual = array1.length == array2.length;
            int i = 0;
            while (areEqual && i < array1.length) {
                if (array1[i] != array2[i]) {
                    areEqual = false;
                }
                i++;
            }
            return areEqual;
        }
    }

    public static byte[] encodeWithLengthByte(byte[] byteString) {
        byte[] lengthByte = new byte[]{(byte) byteString.length};
        ArrayList<byte[]> concatenatedBytes = new ArrayList<>();
        concatenatedBytes.add(lengthByte);
        concatenatedBytes.add(byteString);
        return ByteUtils.concatenateByteArrays(concatenatedBytes);
    }

    public static byte[] readByteSubstring(byte[] message, int startIndex, int length) {
        if (message.length < startIndex + length) {
            return null;
        }
        byte[] substring = new byte[length];
        for (int i = 0; i < length; i++) {
            substring[i] = message[i + startIndex];
        }
        return substring;
    }

    public static byte[] readByteSubstringWithLengthEncoded(byte[] message, int startIndex) {
        int substringLength = message[startIndex];
        if (substringLength < 0) {
            substringLength += 256;
        }
        startIndex++;
        if (message.length < startIndex + substringLength) {
            return null;
        }
        byte[] substring = new byte[substringLength];
        for (int i = 0; i < substringLength; i++) {
            substring[i] = message[i + startIndex];
        }
        return substring;
    }

    public static byte[] intToByteArray(int intToConvert) {
        byte[] intAsBytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            intAsBytes[i] = (byte) (intToConvert % 256);
            intToConvert /= 256;
        }
        return intAsBytes;
    }

    public static int intFromByteArray(byte[] byteArrayToConvert) {
        int bytesAsInt = 0;
        int multiplier = 1;
        for (int i = 0; i < 4; i++) {
            int byteAsInt = byteArrayToConvert[i];
            if (byteAsInt < 0) {
                byteAsInt += 256;
            }
            bytesAsInt += byteAsInt * multiplier;
            multiplier *= 256;
        }
        return bytesAsInt;
    }

    public static byte[] longToByteArray(long longToConvert) {
        byte[] longAsBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            longAsBytes[i] = (byte) (longToConvert % 256);
            longToConvert /= 256;
        }
        return longAsBytes;
    }

    public static long longFromByteArray(byte[] byteArrayToConvert) {
        long bytesAsLong = 0;
        long multiplier = 1;
        for (int i = 0; i < 8; i++) {
            long byteAsLong = byteArrayToConvert[i];
            if (byteAsLong < 0) {
                byteAsLong += 256;
            }
            bytesAsLong += byteAsLong * multiplier;
            multiplier *= 256;
        }
        return bytesAsLong;
    }

    public static byte[] subtractByteArrays(byte[] array1, byte[] array2) {
        byte[] difference = new byte[array1.length];
        int carryOver = 0;
        for (int i = 0; i < array1.length; i++) {
            int numberFromArray1 = array1[i];
            if (numberFromArray1 < 0) {
                numberFromArray1 += 256;
            }
            int numberFromArray2;
            if (i < array2.length) {
                numberFromArray2 = array2[i];
                if (numberFromArray2 < 0) {
                    numberFromArray2 += 256;
                }
            } else {
                numberFromArray2 = 0;
            }
            numberFromArray2 += carryOver;
            difference[i] = (byte) ((numberFromArray1 - numberFromArray2) % 256);
            if (numberFromArray2 > numberFromArray1) {
                carryOver = 1;
            } else {
                carryOver = 0;
            }
        }
        return difference;
    }

    public static int compareByteArrays(byte[] array1, byte[] array2) {
        int commparison = 0;
        int i = array1.length - 1;
        while (commparison == 0 && i >= 0) {
            int numberFromArray1 = array1[i];
            if (numberFromArray1 < 0) {
                numberFromArray1 += 256;
            }
            int numberFromArray2;
            if (i < array2.length) {
                numberFromArray2 = array2[i];
                if (numberFromArray2 < 0) {
                    numberFromArray2 += 256;
                }
            } else {
                numberFromArray2 = 0;
            }
            if (numberFromArray1 > numberFromArray2) {
                commparison = 1;
            } else if (numberFromArray1 < numberFromArray2) {
                commparison = -1;
            }
            i--;
        }
        return commparison;
    }

    public static boolean testSubtractBytes() {
        byte[] array1 = new byte[]{75, 2, 56};
        byte[] array2 = new byte[]{9, 58, 5};
        byte[] array3 = subtractByteArrays(array1, array2);
        System.out.println("{" + array3[0] + ", " + array3[1] + ", " + array3[2] + "}");
        return array3[0] == 66 && array3[1] == -56 && array3[2] == 50;
    }

    private static boolean testIntByteConversion(int intToTest) {
        return intToTest == intFromByteArray(intToByteArray(intToTest));
    }

    public static void testManyIntByteConversions() {
        if (!testIntByteConversion(66)) {
            System.out.println("Failed conversion 66");
        }
        if (!testIntByteConversion(1066)) {
            System.out.println("Failed conversion 1066");
        }
        if (!testIntByteConversion(2562566)) {
            System.out.println("Failed conversion 2562566");
        }
        if (!testIntByteConversion(242666383)) {
            System.out.println("Failed conversion 242666383");
        }
    }

    public static boolean isHex(String hexString) {
        boolean isStillHex = hexString.length() % 2 == 0;
        int i = 0;
        while (isStillHex && i < hexString.length()) {
            char hexChar = hexString.charAt(i);
            if (hexChar < '0') {
                isStillHex = false;
            } else if (hexChar > '9') {
                if (hexChar < 'A') {
                    isStillHex = false;
                } else if (hexChar > 'F') {
                    if (hexChar < 'a' || hexChar > 'f') {
                        isStillHex = false;
                    }
                }
            }
            i++;
        }
        return isStillHex;
    }
}
