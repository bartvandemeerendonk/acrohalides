package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Utils.PrivacyUtils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class InitializationBlockFactory extends BlockFactory {
    public InitializationBlockFactory () {
        setPrefix("Initialization block - ".getBytes());
        minimumMessageLength = Election.ID_LENGTH + prefix.length + PrivacyUtils.PUBLIC_KEY_LENGTH;
    }

    public byte[] getSignature(byte[] message) {
        return ByteUtils.readByteSubstringWithLengthEncoded(message, minimumMessageLength);
    }

    public InitializationBlock createBlock(Blockchain blockchain, Signature signature) {
        InitializationBlock initializationBlock = new InitializationBlock(blockchain);
        try {
            byte[] unsignedBlockBytes = initializationBlock.getBytesWithoutValidation();
            signature.update(unsignedBlockBytes);
            byte[] signatureBytes = signature.sign();
//            System.out.println("Initialization block signature length: " + signatureBytes.length);
            initializationBlock.setSignature(signatureBytes);
            blockchain.addInitializationBlock(initializationBlock);
        } catch (SignatureException exception) {
            System.err.println("Couldn't create initialization block due to a SignatureException");
            initializationBlock = null;
        }
        return  initializationBlock;
    }

    public InitializationBlock createBlock(Blockchain blockchain, byte[] message) {
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        InitializationBlock initializationBlock = new InitializationBlock(blockchain);
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(initializationBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
                initializationBlock.setSignature(signatureBytes);
                blockchain.addInitializationBlock(initializationBlock);
//                System.out.println("Signature is valid");
            } else {
                System.out.println("Signature is NOT valid");
                initializationBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process initialization");
            initializationBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process initialization");
            initializationBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process initialization");
            initializationBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process initialization");
            initializationBlock = null;
        }
        return  initializationBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int j = 0; j < bytesWithoutValidation.length; j++) {
            bytesWithoutValidation[j] = message[j];
        }
        return bytesWithoutValidation;
    }
}
