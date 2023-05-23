package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Utils.ByteUtils;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.BlockchainLayer.ValidationStatus;

import java.security.*;
import java.security.spec.InvalidKeySpecException;

public class DilutionEndBlockFactory extends SuccessorBlockFactory {
    private final long chainScoreIncrease = 32;

    public DilutionEndBlockFactory () {
        setPrefix("end block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength;
    }

    public byte[] getSignature(byte[] message) {
        int candidateStringLength = ByteUtils.intFromByteArray(ByteUtils.readByteSubstring(message, minimumMessageLength, 4));
        return ByteUtils.readByteSubstringWithLengthEncoded(message, minimumMessageLength + 4 + candidateStringLength);
    }

    public byte[] getCandidateString(byte[] message) {
        int candidateStringLength = ByteUtils.intFromByteArray(ByteUtils.readByteSubstring(message, minimumMessageLength, 4));
        return ByteUtils.readByteSubstring(message, minimumMessageLength + 4, candidateStringLength);
    }

    @Override
    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        switch (competingBlock.getPrefix()[0]) {
            case 'I':
            case 'R':
            case 'd':
                return ValidationStatus.VALID;
            case 'D':
            case '0':
            case '1':
                return ValidationStatus.EQUAL_FORK;
            case 'e':
                DilutionEndBlock competingDilutionEndBlock = (DilutionEndBlock) competingBlock;
                try {
                    MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
                    byte[] hashOfMessage = messageDigest.digest(blockMessage);
                    byte[] hashOfCompetingBlock = competingDilutionEndBlock.getHash();
                    int hashComparison = ByteUtils.compareByteArrays(hashOfMessage, hashOfCompetingBlock);
                    if (hashComparison < 0) {
                        return ValidationStatus.EQUAL_FORK;
                    } else if (hashComparison > 0) {
                        return ValidationStatus.LOSES_SECONDARY_CHECK;
                    } else {
                        byte[] competingBlockBytes = competingDilutionEndBlock.getBytes();
                        if (blockMessage.length < competingBlockBytes.length) {
                            return ValidationStatus.EQUAL_FORK;
                        } else if (blockMessage.length > competingBlockBytes.length) {
                            return ValidationStatus.LOSES_SECONDARY_CHECK;
                        } else {
                            int blockComparison = ByteUtils.compareByteArrays(blockMessage, competingBlockBytes);
                            if (blockComparison < 0) {
                                return ValidationStatus.EQUAL_FORK;
                            } else {
                                return ValidationStatus.LOSES_SECONDARY_CHECK;
                            }
                        }
                    }
                } catch (NoSuchAlgorithmException exception) {
                    System.out.println("No such algorithm for hash");
                    return ValidationStatus.LOSES_PRIMARY_CHECK;
                }
            default:
                return ValidationStatus.LOSES_SECONDARY_CHECK;
        }
    }

    public DilutionEndBlock createBlock(Blockchain blockchain, Signature signature, byte[] candidateString) {
        DilutionEndBlock dilutionEndBlock = new DilutionEndBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash());
        dilutionEndBlock.setChainScore(blockchain.getLastBlock().getChainScore() + chainScoreIncrease);
        dilutionEndBlock.setCandidateString(candidateString);
        try {
            byte[] unsignedBlockBytes = dilutionEndBlock.getBytesWithoutValidation();
            signature.update(unsignedBlockBytes);
            byte[] signatureBytes = signature.sign();
            dilutionEndBlock.setSignature(signatureBytes);
            if (!blockchain.appendBlock(dilutionEndBlock)) {
                dilutionEndBlock = null;
            }
        } catch (SignatureException exception) {
            System.err.println("Couldn't create dilution end block due to a SignatureException");
            dilutionEndBlock = null;
        }
        return dilutionEndBlock;
    }

    public DilutionEndBlock createBlock(Blockchain blockchain, byte[] message) {
        DilutionEndBlock dilutionEndBlock = new DilutionEndBlock(blockchain, getIndex(message), getPredecessorHash(message));
        dilutionEndBlock.setChainScore(getChainScore(message));
        dilutionEndBlock.setCandidateString(getCandidateString(message));
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(dilutionEndBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of dilution end block is valid");
                dilutionEndBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of dilution end block is not valid");
                dilutionEndBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process dilution end");
            dilutionEndBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process dilution end");
            dilutionEndBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process dilution end");
            dilutionEndBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process dilution end");
            dilutionEndBlock = null;
        }
        return dilutionEndBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        int candidateStringLength = ByteUtils.intFromByteArray(ByteUtils.readByteSubstring(message, minimumMessageLength, 4));
        byte[] bytesWithoutValidation = new byte[minimumMessageLength + 4 + candidateStringLength];
        for (int i = 0; i < bytesWithoutValidation.length; i++) {
            bytesWithoutValidation[i] = message[i];
        }
        return bytesWithoutValidation;
    }

    public long getChainScoreIncrease() {
        return chainScoreIncrease;
    }
}
