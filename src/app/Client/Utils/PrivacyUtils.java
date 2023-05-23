package app.Client.Utils;

import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECNamedCurveSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;

public class PrivacyUtils {
    public static final String SIGNATURE_NAME ="SHA256withECDSA";
    public static final String ASYMMETRIC_ALGORITHM = "ECDH";
    public static final String CURVE_NAME = "secp256r1";
    public static final int PUBLIC_KEY_LENGTH = 64;
    public static final int PUBLIC_KEY_LENGTH_X509 = 91;
    public static final int PRIVATE_KEY_LENGTH = 32;
    public static final int HASH_LENGTH = 64;
    public static final int VOTE_HASH_LENGTH = 24;

    public static Signature createSignatureForSigning(KeyPair keyPair) throws InvalidKeyException, NoSuchAlgorithmException {
        Signature signature = Signature.getInstance(SIGNATURE_NAME);
        signature.initSign(keyPair.getPrivate());
        return signature;
    }

    public static Signature createSignatureForVerifying(PublicKey publicKey) throws InvalidKeyException, NoSuchAlgorithmException {
        Signature signature = Signature.getInstance(SIGNATURE_NAME);
        signature.initVerify(publicKey);
        return signature;
    }

    public static PublicKey getPublicKeyFromBytesX509(byte[] bytes) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeyFactory keyFactory = KeyFactory.getInstance(ASYMMETRIC_ALGORITHM);
        return keyFactory.generatePublic(new X509EncodedKeySpec(bytes));
    }

    public static byte[] getPublicKeyBytesFromKeyPairX509(KeyPair keyPair) {
        return keyPair.getPublic().getEncoded();
    }

    public static byte[] to32ByteArray(byte[] inputArray) {
        byte[] toReturn = new byte[32];
        int offset = toReturn.length - inputArray.length;
        if (offset < 0) {
            offset = 0;
        }
        for (int i = 0; i < offset; i++) {
            toReturn[i] = 0;
        }
        int offset2 = inputArray.length - toReturn.length;
        if (offset2 < 0) {
            offset2 = 0;
        }
        for (int i = 0; i < toReturn.length - offset; i++) {
            toReturn[i + offset] = inputArray[i + offset2];
        }
        return toReturn;
    }

    public static PublicKey getPublicKeyFromBytes(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] xBytes = new byte[33];
        for (int i = 0; i < 32; i++) {
            xBytes[i+1] = bytes[i];
        }
        xBytes[0] = 0;
        byte[] yBytes = new byte[33];
        for (int i = 0; i < 32; i++) {
            yBytes[i+1] = bytes[i + 32];
        }
        yBytes[0] = 0;
        BigInteger x = new BigInteger(xBytes);
        BigInteger y = new BigInteger(yBytes);
        ECPoint point = new ECPoint(x, y);
        KeyFactory eckf = KeyFactory.getInstance("EC");
        ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec(PrivacyUtils.CURVE_NAME);
        ECParameterSpec spec = new ECNamedCurveSpec(PrivacyUtils.CURVE_NAME, parameterSpec.getCurve(), parameterSpec.getG(), parameterSpec.getN(), parameterSpec.getH(), parameterSpec.getSeed());
        return eckf.generatePublic(new ECPublicKeySpec(point, spec));
    }

    public static byte[] getPublicKeyBytesFromKeyPair(KeyPair keyPair) {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        ECPoint wPoint = publicKey.getW();
        BigInteger affineX = wPoint.getAffineX();
        BigInteger affineY = wPoint.getAffineY();
        byte[] xBytes = to32ByteArray(affineX.toByteArray());
        byte[] yBytes = to32ByteArray(affineY.toByteArray());
        byte[] publicKeyBytes = new byte[64];
        for (int i = 0; i < 32; i++) {
            publicKeyBytes[i] = xBytes[i];
            publicKeyBytes[i + 32] = yBytes[i];
        }
        return publicKeyBytes;
    }

    public static PrivateKey getPrivateKeyFromBytesPKCS8(byte[] bytes) throws InvalidKeySpecException, NoSuchAlgorithmException {
        KeyFactory keyFactory = KeyFactory.getInstance(ASYMMETRIC_ALGORITHM);
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    public static byte[] getPrivateKeyBytesFromKeyPairPKCS8(KeyPair keyPair) {
        return keyPair.getPrivate().getEncoded();
    }

    public static byte[] getPrivateKeyBytesFromKeyPair(KeyPair keyPair) {
        ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
        BigInteger s = privateKey.getS();
        return to32ByteArray(s.toByteArray());
    }

    public static PrivateKey getPrivateKeyFromBytes(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException{
        BigInteger finalS = new BigInteger(bytes);
        KeyFactory eckf = KeyFactory.getInstance("EC");
        ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec(PrivacyUtils.CURVE_NAME);
        ECParameterSpec spec = new ECNamedCurveSpec(PrivacyUtils.CURVE_NAME, parameterSpec.getCurve(), parameterSpec.getG(), parameterSpec.getN(), parameterSpec.getH(), parameterSpec.getSeed());
        PrivateKey privateKey = eckf.generatePrivate(new ECPrivateKeySpec(finalS, spec));
        return privateKey;
    }

    public static byte[] sign(byte[] messageToSign, byte[] privateKeyBytes) throws InvalidKeySpecException, InvalidKeyException, NoSuchAlgorithmException, SignatureException {
        PrivateKey privateKey = getPrivateKeyFromBytes(privateKeyBytes);
        Signature signature = Signature.getInstance(SIGNATURE_NAME);
        signature.initSign(privateKey);
        signature.update(messageToSign);
        return signature.sign();
    }

    public static KeyPair generateKeyPair() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ASYMMETRIC_ALGORITHM);
//        keyPairGenerator.initialize(new ECGenParameterSpec("ed448-goldilocks"));
/*        BigInteger p = new BigInteger(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        BigInteger a = new BigInteger(new byte[]{1});
        BigInteger d = new BigInteger(new byte[]{(byte) 0x56, (byte) 0x67, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xfe, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        BigInteger gx = new BigInteger(new byte[]{(byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0x55, (byte) 0xa9, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa, (byte) 0xaa});
        BigInteger gy = new BigInteger(new byte[]{(byte) 0xed, (byte) 0x86, (byte) 0x93, (byte) 0xea, (byte) 0xcd, (byte) 0xfb, (byte) 0xea, (byte) 0xda, (byte) 0x6b, (byte) 0xa0, (byte) 0xcd, (byte) 0xd1, (byte) 0xbe, (byte) 0xb2, (byte) 0xbc, (byte) 0xbb, (byte) 0x98, (byte) 0x30, (byte) 0x2a, (byte) 0x3a, (byte) 0x83, (byte) 0x65, (byte) 0x65, (byte) 0x0d, (byte) 0xb8, (byte) 0xc4, (byte) 0xd8, (byte) 0x8a, (byte) 0x72, (byte) 0x6d, (byte) 0xe3, (byte) 0xb7, (byte) 0xd7, (byte) 0x4d, (byte) 0x88, (byte) 0x35, (byte) 0xa0, (byte) 0xd7, (byte) 0x6e, (byte) 0x03, (byte) 0xb0, (byte) 0xc2, (byte) 0x86, (byte) 0x50, (byte) 0x20, (byte) 0xd6, (byte) 0x59, (byte) 0xb3, (byte) 0x8d, (byte) 0x04, (byte) 0xd7, (byte) 0x4a, (byte) 0x63, (byte) 0xe9, (byte) 0x05, (byte) 0xae});
        BigInteger n = new BigInteger(new byte[]{(byte) 0xf3, (byte) 0x44, (byte) 0x58, (byte) 0xab, (byte) 0x92, (byte) 0xc2, (byte) 0x78, (byte) 0x23, (byte) 0x55, (byte) 0x8f, (byte) 0xc5, (byte) 0x8d, (byte) 0x72, (byte) 0xc2, (byte) 0x6c, (byte) 0x21, (byte) 0x90, (byte) 0x36, (byte) 0xd6, (byte) 0xae, (byte) 0x49, (byte) 0xdb, (byte) 0x4e, (byte) 0xc4, (byte) 0xe9, (byte) 0x23, (byte) 0xca, (byte) 0x7c, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x3f});
        int h = 4;
        ECField ecField = new ECFieldFp(p);
        EllipticCurve ed448goldilocks = new EllipticCurve(ecField,a,a);
        new ECParameterSpec(ed448goldilocks,);*/
//        new BigInteger(0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffffffffffffffffffffffffffffffffffffffffffffffffffff)
//        new ECFieldFp();
//        new EllipticCurve();
//        long goldilockprime = 0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffffffffffffffffffffffffffffffffffffffffffffffffffff;
//        new ECParameterSpec()
        keyPairGenerator.initialize(new ECGenParameterSpec(CURVE_NAME));
        keyPairGenerator.initialize(256);
        KeyPair keyPair = keyPairGenerator.genKeyPair();
        if (isValidKeyPair(getPublicKeyBytesFromKeyPair(keyPair), getPrivateKeyBytesFromKeyPair(keyPair))) {
            return keyPair;
        } else {
            return generateKeyPair();
        }
    }

    public static byte[] appendPublicKeyAndSign(byte[] messageToSign, byte[] publicKey, Signature signature) throws SignatureException {
        signature.update(messageToSign);
        byte[] signatureBytes = signature.sign();
        byte[] signedMessage = new byte[messageToSign.length + publicKey.length + signatureBytes.length];
        for (int i = 0; i < messageToSign.length; i++) {
            signedMessage[i] = messageToSign[i];
        }
        for (int i = 0; i < publicKey.length; i++) {
            signedMessage[i + messageToSign.length] = publicKey[i];
        }
        for (int i = 0; i < signatureBytes.length; i++) {
            signedMessage[i + messageToSign.length + publicKey.length] = signatureBytes[i];
        }
        return signedMessage;
    }

    public static byte[] sign(byte[] messageToSign, Signature signature) throws SignatureException {
        signature.update(messageToSign);
        byte[] signatureBytes = signature.sign();
        byte[] signedMessage = new byte[messageToSign.length + signatureBytes.length];
        for (int i = 0; i < messageToSign.length; i++) {
            signedMessage[i] = messageToSign[i];
        }
        for (int i = 0; i < signatureBytes.length; i++) {
            signedMessage[i + messageToSign.length] = signatureBytes[i];
        }
        return signedMessage;
    }

    public static boolean verifySignature(byte[] message, byte[] publicKeyBytes, byte[] signatureBytes) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException, SignatureException {
        PublicKey publicKey = PrivacyUtils.getPublicKeyFromBytes(publicKeyBytes);
        Signature signature = PrivacyUtils.createSignatureForVerifying(publicKey);
        signature.update(message);
        return signature.verify(signatureBytes);
    }

    public static Cipher getPublicKeyEncryption(byte[] publicKeyBytes) {
        try {
            PublicKey publicKey = getPublicKeyFromBytes(publicKeyBytes);
            Cipher eciesEncrypt = Cipher.getInstance("ECIES");
            eciesEncrypt.init(Cipher.ENCRYPT_MODE, publicKey);
            return eciesEncrypt;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidKeySpecException | IllegalArgumentException exception) {
            return null;
        }
    }

    public static Cipher getPrivateKeyDecryption(PrivateKey privateKey) {
        try {
            Cipher eciesDecrypt = Cipher.getInstance("ECIES");
            eciesDecrypt.init(Cipher.DECRYPT_MODE, privateKey);
            return eciesDecrypt;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalArgumentException exception) {
            return null;
        }
    }

    public static boolean isValidKeyPair(byte[] publicKeyBytes, byte[] privateKeyBytes) {
        try {
            PrivateKey privateKey = getPrivateKeyFromBytes(privateKeyBytes);
            if (privateKey == null) {
                return false;
            }
            Cipher publicKeyEncryption = getPublicKeyEncryption(publicKeyBytes);
            if (publicKeyEncryption == null) {
                return false;
            }
            Cipher privateKeyDecryption = getPrivateKeyDecryption(privateKey);
            if (privateKeyDecryption == null) {
                return false;
            }

            byte[] messageToEncrypt = new byte[29];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(messageToEncrypt);

            byte[] encryptedMessage = publicKeyEncryption.doFinal(messageToEncrypt);
            byte[] decryptedMessage = privateKeyDecryption.doFinal(encryptedMessage);
            return ByteUtils.byteArraysAreEqual(messageToEncrypt, decryptedMessage);

        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalBlockSizeException | BadPaddingException exception) {
            return false;
        }
    }

    public static byte[] hash(byte[] bytes) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
            return messageDigest.digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            System.out.println("No such algorithm for hash");
            return new byte[0];
        }
    }

    public static byte[] hashVote(byte[] vote, byte[] salt) {
        return BCrypt.generate(vote, salt, 10);
    }
}
