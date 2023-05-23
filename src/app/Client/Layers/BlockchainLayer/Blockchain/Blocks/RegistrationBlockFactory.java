package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.BlockchainLayer.ValidationStatus;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

public class RegistrationBlockFactory extends SuccessorBlockFactory {
    public RegistrationBlockFactory() {
        setPrefix("Registration block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength + Election.VOTER_ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH;
    }

    public byte[] getVoterId(byte[] message) {
        return ByteUtils.readByteSubstring(message, succesBlockHeaderLength, Election.VOTER_ID_LENGTH);
    }

    public byte[] getVoterPublicKey(byte[] message) {
        return ByteUtils.readByteSubstring(message, succesBlockHeaderLength + Election.VOTER_ID_LENGTH, PrivacyUtils.PUBLIC_KEY_LENGTH);
    }

    public byte[] getSignature(byte[] message) {
        int offset = minimumMessageLength;
        return ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
    }

    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        if (competingBlock.getPrefix()[0] == 'R') {
            RegistrationBlock competingRegistrationBlock = (RegistrationBlock) competingBlock;
            byte[] voterId = getVoterId(blockMessage);
            byte[] publicKey = getVoterPublicKey(blockMessage);
            byte[] thisRelativeVoterId;
            byte[] thisRelativePublicKey;
            byte[] competingBlockRelativeId;
            byte[] competingBlockRelativePublicKey;
            Block predecessorBlock = blockchain.getBlock(competingBlock.getIndex() - 1);
            if (predecessorBlock.getPrefix()[0] == 'R') {
                RegistrationBlock predecessorRegistrationBlock = (RegistrationBlock) predecessorBlock;
                thisRelativeVoterId = ByteUtils.subtractByteArrays(voterId, predecessorRegistrationBlock.getVoterId());
                thisRelativePublicKey = ByteUtils.subtractByteArrays(publicKey, predecessorRegistrationBlock.getPublicKey());
                competingBlockRelativeId = ByteUtils.subtractByteArrays(competingRegistrationBlock.getVoterId(), predecessorRegistrationBlock.getVoterId());
                competingBlockRelativePublicKey = ByteUtils.subtractByteArrays(competingRegistrationBlock.getPublicKey(), predecessorRegistrationBlock.getPublicKey());
            } else {
                thisRelativeVoterId = voterId;
                thisRelativePublicKey = publicKey;
                competingBlockRelativeId = competingRegistrationBlock.getVoterId();
                competingBlockRelativePublicKey = competingRegistrationBlock.getPublicKey();
            }
            int voterIdComparison = ByteUtils.compareByteArrays(thisRelativeVoterId, competingBlockRelativeId);
            if (voterIdComparison < 0) {
                return ValidationStatus.EQUAL_FORK;
            } else if (voterIdComparison > 0) {
                return ValidationStatus.LOSES_SECONDARY_CHECK;
            } else {
                int publicKeyComparison = ByteUtils.compareByteArrays(thisRelativePublicKey, competingBlockRelativePublicKey);
                if (publicKeyComparison < 0) {
                    return ValidationStatus.EQUAL_FORK;
                } else {
                    return ValidationStatus.LOSES_SECONDARY_CHECK;
                }
            }
        } else {
            return ValidationStatus.LOSES_PRIMARY_CHECK;
        }
    }

/*    public boolean doesBlockMessageWinFork(byte[] blockMessage, Blockchain blockchain) {
        Block lastBlock = blockchain.getLastBlock();
        if (lastBlock.getPrefix().toString().charAt(0) != 'R')
        lastBlock.getChainScore();
/*        if (lastBlock.getIndex() > getIndex(blockMessage)) {
            return false;
        } else if (lastBlock.getPrefix().toString().charAt(0) != 'R') {
            return false;
        } else {
            RegistrationBlock lastRegistrationBlock = (RegistrationBlock) lastBlock;
            byte[] predecessorHash = getPredecessorHash(blockMessage);
            if (ByteUtils.byteArraysAreEqual(lastRegistrationBlock.getPredecessorHash(), predecessorHash)) {
                byte[] voterId = getVoterId(blockMessage);
                byte[] thisRelativeVoterId;
                byte[] lastBlockRelativeVoterId;
                Block predecessorBlock = blockchain.getBlock(lastBlock.getIndex() - 1);
                if (predecessorBlock.getPrefix()[0] == 'R') {
                    RegistrationBlock predecessorRegistrationBlock = (RegistrationBlock) predecessorBlock;
                    thisRelativeVoterId = ByteUtils.subtractByteArrays(voterId, predecessorRegistrationBlock.getVoterId());
                    lastBlockRelativeVoterId = ByteUtils.subtractByteArrays(lastRegistrationBlock.getVoterId(), predecessorRegistrationBlock.getVoterId());
                } else {
                    thisRelativeVoterId = voterId;
                    lastBlockRelativeVoterId = lastRegistrationBlock.getVoterId();
                }
                return ByteUtils.compareByteArrays(thisRelativeVoterId, lastBlockRelativeVoterId) < 0;
            } else {
                return true;
            }
        }*/
//        return false;
//    }

    public RegistrationBlock createBlock(Blockchain blockchain, byte[] voterId, byte[] voterPublicKey, Signature signature) {
        if (voterId.length == Election.VOTER_ID_LENGTH && voterPublicKey.length == PrivacyUtils.PUBLIC_KEY_LENGTH) {
            RegistrationBlock registrationBlock = new RegistrationBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash(), voterId, voterPublicKey);
            registrationBlock.setChainScore(blockchain.getLastBlock().getChainScore() + 1);
            try {
                byte[] unsignedBlockBytes = registrationBlock.getBytesWithoutValidation();
                signature.update(unsignedBlockBytes);
                byte[] signatureBytes = signature.sign();
//                System.out.println("Registration block signature length: " + signatureBytes.length);
                registrationBlock.setSignature(signatureBytes);
                if (!blockchain.appendBlock(registrationBlock)) {
                    registrationBlock = null;
                }
            } catch (SignatureException exception) {
                System.err.println("Couldn't create registration block due to a SignatureException");
                registrationBlock = null;
            }
            return registrationBlock;
        } else {
            System.err.println("Couldn't create registration block because the input byte strings were of incorrect size.");
            return null;
        }
    }

    public RegistrationBlock createBlock(Blockchain blockchain, byte[] message) {
        byte[] voterId = getVoterId(message);
        byte[] voterPublicKey = getVoterPublicKey(message);
        RegistrationBlock registrationBlock = new RegistrationBlock(blockchain, getIndex(message), getPredecessorHash(message), voterId, voterPublicKey);
        registrationBlock.setChainScore(getChainScore(message));
        byte[] managerPublicKey = blockchain.getManagerPublicKey();
        byte[] signatureBytes = getSignature(message);
        try {
            Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
            signature.update(registrationBlock.getBytesWithoutValidation());
            if (signature.verify(signatureBytes)) {
//                System.out.println("Signature of registration block is valid");
                registrationBlock.setSignature(signatureBytes);
            } else {
                System.out.println("Signature of registration block is not valid");
                registrationBlock = null;
            }
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("No such algorithm in process registration");
            registrationBlock = null;
        } catch (InvalidKeySpecException exception) {
            System.err.println("Invalid key spec in process registration");
            registrationBlock = null;
        } catch (InvalidKeyException exception) {
            System.err.println("Invalid key in process registration");
            registrationBlock = null;
        } catch (SignatureException exception) {
            System.err.println("Signature exception in process registration");
            registrationBlock = null;
        }
        return registrationBlock;
    }

    public byte[] getBytesWithoutValidation(byte[] message) {
        byte[] bytesWithoutValidation = new byte[minimumMessageLength];
        for (int j = 0; j < bytesWithoutValidation.length; j++) {
            bytesWithoutValidation[j] = message[j];
        }
        return bytesWithoutValidation;
    }
}
