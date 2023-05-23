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

public class PreDepthBlockFactory extends SuccessorBlockFactory {

    public PreDepthBlockFactory () {
        setPrefix("1 Pre-depth block - ".getBytes());
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
            default:
                return ValidationStatus.LOSES_SECONDARY_CHECK;
        }
    }

    public PreDepthBlock createBlock(Blockchain blockchain, Signature signature) {
        PreDepthBlock preDepthBlock = new PreDepthBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash());
        preDepthBlock.setChainScore(blockchain.getLastBlock().getChainScore());
        try {
            byte[] unsignedBlockBytes = preDepthBlock.getBytesWithoutValidation();
            signature.update(unsignedBlockBytes);
            byte[] signatureBytes = signature.sign();
            preDepthBlock.setSignature(signatureBytes);
            if (!blockchain.appendBlock(preDepthBlock)) {
                preDepthBlock = null;
            }
        } catch (SignatureException exception) {
            System.err.println("Couldn't create pre-depth block due to a SignatureException");
            preDepthBlock = null;
        }
        return preDepthBlock;
    }

    public PreDepthBlock createBlock(Blockchain blockchain, byte[] message) {
        PreDepthBlock preDepthBlock = new PreDepthBlock(blockchain, getIndex(message), getPredecessorHash(message));
        preDepthBlock.setChainScore(getChainScore(message));
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(preDepthBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of pre-depth block is valid");
                preDepthBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of pre-depth block is not valid");
                preDepthBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process pre-depth");
            preDepthBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process pre-depth");
            preDepthBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process pre-depth");
            preDepthBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process pre-depth");
            preDepthBlock = null;
        }
        return preDepthBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int i = 0; i < bytesWithoutValidation.length; i++) {
            bytesWithoutValidation[i] = message[i];
        }
        return bytesWithoutValidation;
    }
}
