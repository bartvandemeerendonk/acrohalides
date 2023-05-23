package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.BlockchainLayer.ValidationStatus;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class DepthBlockFactory extends SuccessorBlockFactory {

    public DepthBlockFactory () {
        setPrefix("0 Depth block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength;
    }

    public byte[] getSignature(byte[] message) {
        return ByteUtils.readByteSubstringWithLengthEncoded(message, minimumMessageLength);
    }

    @Override
    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        switch (competingBlock.getPrefix()[0]) {
            case 'I':
            case 'R':
            case 'd':
            case 'D':
                return ValidationStatus.VALID;
            case '1':
                return ValidationStatus.EQUAL_FORK;
            default:
                return ValidationStatus.LOSES_SECONDARY_CHECK;
        }
    }

    public DepthBlock createBlock(Blockchain blockchain, Signature signature) {
        DepthBlock depthBlock = new DepthBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash());
        depthBlock.setChainScore(blockchain.getLastBlock().getChainScore());
        try {
            byte[] unsignedBlockBytes = depthBlock.getBytesWithoutValidation();
            signature.update(unsignedBlockBytes);
            byte[] signatureBytes = signature.sign();
            depthBlock.setSignature(signatureBytes);
            if (!blockchain.appendBlock(depthBlock)) {
                depthBlock = null;
            }
        } catch (SignatureException exception) {
            System.err.println("Couldn't create depth block due to a SignatureException");
            depthBlock = null;
        }
        return depthBlock;
    }

    public DepthBlock createBlock(Blockchain blockchain, byte[] message) {
        DepthBlock depthBlock = new DepthBlock(blockchain, getIndex(message), getPredecessorHash(message));
        depthBlock.setChainScore(getChainScore(message));
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(depthBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of depth block is valid");
                depthBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of depth block is not valid");
                depthBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process depth");
            depthBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process depth");
            depthBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process depth");
            depthBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process depth");
            depthBlock = null;
        }
        return depthBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int i = 0; i < bytesWithoutValidation.length; i++) {
            bytesWithoutValidation[i] = message[i];
        }
        return bytesWithoutValidation;
    }
}
