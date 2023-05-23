package app.Client.Layers.BlockchainLayer.Blockchain.Blocks;

import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Layers.BlockchainLayer.ValidationStatus;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

public class DilutionBlockFactory extends SuccessorBlockFactory {

    public DilutionBlockFactory () {
        setPrefix("Dilution block - ".getBytes());
        initialize();
        minimumMessageLength = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH + 1;
    }

    public byte[] getPoolIdentifier(byte[] message) {
        int offset = succesBlockHeaderLength;
        byte[] poolIdentifier = new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE];
        for (int j = 0; j < BlockchainLayer.POOL_IDENTIFIER_SIZE; j++) {
            poolIdentifier[j] = message[offset + j];
        }
        return poolIdentifier;
    }

    public byte[] getBlockAssembler(byte[] message) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE;
        byte[] blockAssembler = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
        for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
            blockAssembler[j] = message[offset + j];
        }
        return blockAssembler;
    }

    public long getBlockAssemblerOriginIndex(byte[] message) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH;
        byte[] blockAssemblerOriginIndex = new byte[Block.INDEX_SIZE];
        for (int j = 0; j < Block.INDEX_SIZE; j++) {
            blockAssemblerOriginIndex[j] = message[offset + j];
        }
        return ByteUtils.longFromByteArray(blockAssemblerOriginIndex);
    }

    public int getDepth(byte[] message) {
        int depth = message[BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + succesBlockHeaderLength];
        if (depth < 0) {
            depth += 256;
        }
        return depth;
    }

    public byte[] getAnonymitySet(byte[] message) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1;
        byte[] anonymitySet = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
        for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
            anonymitySet[j] = message[offset + j];
        }
        return anonymitySet;
    }

//    public long getDilutionFactor(byte[] message) {
//        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
//        byte[] dilutionFactorBytes = new byte[8];
//        for (int j = 0; j < 8; j++) {
//            dilutionFactorBytes[j] = message[offset + j];
//        }
//        return ByteUtils.longFromByteArray(dilutionFactorBytes);
//    }

    public long getChainScore(List<Long> keyOriginBlockIndices, Blockchain blockchain, long blockIndex) {
        ArrayList<byte[]> anonymitySets = new ArrayList<>();
        for (long keyOriginBlockIndex: keyOriginBlockIndices) {
            Block keyOriginBlock = blockchain.getBlock(keyOriginBlockIndex);
            byte[] currentAnonymitySet = null;
            switch(keyOriginBlock.getPrefix()[0]) {
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    currentAnonymitySet = dilutionBlock.getAnonymitySet();
                    break;
                case 'R':
                    RegistrationBlock registrationBlock = (RegistrationBlock) keyOriginBlock;
                    currentAnonymitySet = registrationBlock.getPublicKey();
                    break;
            }
            if (currentAnonymitySet != null) {
                boolean anonymitySetIsStillUnique = true;
                int i = 0;
                while (anonymitySetIsStillUnique && i < anonymitySets.size()) {
                    if (ByteUtils.byteArraysAreEqual(currentAnonymitySet, anonymitySets.get(i))) {
                        anonymitySetIsStillUnique = false;
//                        System.out.println("Double anonymity set:");
//                        System.out.println(Hex.toHexString(currentAnonymitySet));
//                        System.out.println(Hex.toHexString(anonymitySets.get(i)));
                    }
                    i++;
                }
                if (anonymitySetIsStillUnique) {
                    anonymitySets.add(currentAnonymitySet);
                }
//            } else {
//                System.out.println("Null anonymity set");
            }
        }
//        int lastBlockIndex = blockchain.getNumberOfBlocks() - 1;
        Block lastBlock = blockchain.getBlock(blockIndex - 1);
        long lastBlockIndex = lastBlock.getIndex();
        while (lastBlock.getPrefix()[0] == '0') {
            lastBlockIndex--;
            lastBlock = blockchain.getBlock(lastBlockIndex);
        }
        long previousChainScore = lastBlock.getChainScore();
//        if (lastBlock.getPrefix()[0] == 'D') {
//            DilutionBlock dilutionBlock = (DilutionBlock) lastBlock;
//            previousDilutionFactor = dilutionBlock.getDilutionFactor();
//            previousDilutionFactor = lastBlock.getChainScore();
//        }
        return previousChainScore + anonymitySets.size() - 1;
    }

    public int getNumberOfKeys(byte[] message) {
        int numberOfKeys = message[succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH];
        if (numberOfKeys < 0) {
            numberOfKeys += 256;
        }
        return numberOfKeys;
    }

    public List<byte[]> getOldKeys(byte[] message, int numberOfKeys) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH + 1;
        ArrayList<byte[]> oldKeys = new ArrayList<>();
        for (int i = 0; i < numberOfKeys; i++) {
            byte[] currentOldKey = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
            for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
                currentOldKey[j] = message[offset + j];
            }
            oldKeys.add(currentOldKey);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE;
        }
        return oldKeys;
    }

    public List<Long> getKeyOriginBlockIndices(byte[] message, int numberOfKeys) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
        ArrayList<Long> blockIndices = new ArrayList<>();
        for (int i = 0; i < numberOfKeys; i++) {
            byte[] currentBlockIndex = new byte[Block.INDEX_SIZE];
            for (int j = 0; j < Block.INDEX_SIZE; j++) {
                currentBlockIndex[j] = message[offset + j];
            }
            blockIndices.add(ByteUtils.longFromByteArray(currentBlockIndex));
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE;
        }
        return blockIndices;
    }

    public List<byte[]> getNewKeys(byte[] message, int numberOfKeys) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + (PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE) * numberOfKeys + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
        ArrayList<byte[]> newKeys = new ArrayList<>();
        for (int i = 0; i < numberOfKeys; i++) {
            byte[] currentNewKey = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
            for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
                currentNewKey[j] = message[offset + j];
            }
            newKeys.add(currentNewKey);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        }
        return newKeys;
    }

    public List<byte[]> getSignatures(byte[] message, int numberOfKeys) {
        int offset = succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + (PrivacyUtils.PUBLIC_KEY_LENGTH * 2 + Block.INDEX_SIZE) * numberOfKeys + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
        ArrayList<byte[]> signatures = new ArrayList<>();
        for (int i = 0; i < numberOfKeys; i++) {
            byte[] currentSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            signatures.add(currentSignature);
            offset += currentSignature.length + 1;
        }
        return signatures;
    }

    public int getUnvalidatedBlockLength(byte[] unvalidatedBlockBytes) {
        int numberOfKeys = getNumberOfKeys(unvalidatedBlockBytes);
        return succesBlockHeaderLength + BlockchainLayer.POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + (PrivacyUtils.PUBLIC_KEY_LENGTH * 2 + Block.INDEX_SIZE) * numberOfKeys + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
    }

    @Override
    public ValidationStatus doesBlockMessageWinFork(byte[] blockMessage, Block competingBlock, Blockchain blockchain) {
        switch (competingBlock.getPrefix()[0]) {
            case 'R':
            case 'I':
            case 'd':
                return ValidationStatus.VALID;
            case 'D':
                DilutionBlock competingDilutionBlock = (DilutionBlock) competingBlock;
                Block predecessorBlock = blockchain.getBlock(competingBlock.getIndex() - 1);
                int numberOfKeys = getNumberOfKeys(blockMessage);
                int smallestNumberOfKeys = numberOfKeys;
                if (competingDilutionBlock.getOldKeys().size() < smallestNumberOfKeys) {
                    smallestNumberOfKeys = competingDilutionBlock.getOldKeys().size();
                }
                int keyIndex = 0;
                while (keyIndex < smallestNumberOfKeys) {
                    byte[] oldKeyOfMessage = getOldKeys(blockMessage, numberOfKeys).get(keyIndex);
                    byte[] oldKeyOfCompetingBlock = competingDilutionBlock.getOldKeys().get(keyIndex);
                    if (!ByteUtils.byteArraysAreEqual(oldKeyOfMessage, oldKeyOfCompetingBlock)) {
                        byte[] thisRelativeOldKey;
                        byte[] competingBlockRelativeOldKey;
                        if (predecessorBlock.getPrefix()[0] == 'D') {
                            DilutionBlock predecessorDilutionBlock = (DilutionBlock) predecessorBlock;
                            byte[] oldKeyOfPredecessor = predecessorDilutionBlock.getOldKeys().get(keyIndex);
                            thisRelativeOldKey = ByteUtils.subtractByteArrays(oldKeyOfMessage, oldKeyOfPredecessor);
                            competingBlockRelativeOldKey = ByteUtils.subtractByteArrays(oldKeyOfCompetingBlock, oldKeyOfPredecessor);
                        } else {
                            thisRelativeOldKey = oldKeyOfMessage;
                            competingBlockRelativeOldKey = oldKeyOfCompetingBlock;
                        }
                        if (ByteUtils.compareByteArrays(thisRelativeOldKey, competingBlockRelativeOldKey) < 0) {
                            return ValidationStatus.EQUAL_FORK;
                        } else {
                            return ValidationStatus.LOSES_SECONDARY_CHECK;
                        }
                    }
                    keyIndex++;
                }
                if (competingDilutionBlock.getOldKeys().size() < numberOfKeys) {
                    return ValidationStatus.EQUAL_FORK;
                } else if (competingDilutionBlock.getOldKeys().size() > numberOfKeys) {
                    return ValidationStatus.LOSES_SECONDARY_CHECK;
                } else {
                    keyIndex = 0;
                    while (keyIndex < numberOfKeys) {
                        byte[] newKeyOfMessage = getNewKeys(blockMessage, numberOfKeys).get(keyIndex);
                        byte[] newKeyOfCompetingBlock = competingDilutionBlock.getNewKeys().get(keyIndex);
                        if (!ByteUtils.byteArraysAreEqual(newKeyOfMessage, newKeyOfCompetingBlock)) {
                            byte[] thisRelativeNewKey;
                            byte[] competingBlockRelativeNewKey;
                            if (predecessorBlock.getPrefix()[0] == 'D') {
                                DilutionBlock predecessorDilutionBlock = (DilutionBlock) predecessorBlock;
                                byte[] newKeyOfPredecessor = predecessorDilutionBlock.getNewKeys().get(keyIndex);
                                thisRelativeNewKey = ByteUtils.subtractByteArrays(newKeyOfMessage, newKeyOfPredecessor);
                                competingBlockRelativeNewKey = ByteUtils.subtractByteArrays(newKeyOfCompetingBlock, newKeyOfPredecessor);
                            } else {
                                thisRelativeNewKey = newKeyOfMessage;
                                competingBlockRelativeNewKey = newKeyOfCompetingBlock;
                            }
                            if (ByteUtils.compareByteArrays(thisRelativeNewKey, competingBlockRelativeNewKey) < 0) {
                                return ValidationStatus.EQUAL_FORK;
                            } else {
                                return ValidationStatus.LOSES_SECONDARY_CHECK;
                            }
                        }
                        keyIndex++;
                    }
                }
                return ValidationStatus.LOSES_SECONDARY_CHECK;
            case '0':
            case '1':
                return ValidationStatus.LOSES_PRIMARY_CHECK;
            default:
                return ValidationStatus.LOSES_SECONDARY_CHECK;
        }
    }

    public DilutionBlock createUnvalidatedBlock(Blockchain blockchain, List<byte[]> oldKeys, List<Long> keyOriginBlockIndices, List<byte[]> newKeys, byte[] poolIdentifier, byte[] poolManager, long poolManagerKeyOriginBlockIndex) {
        if (oldKeys.size() != keyOriginBlockIndices.size() || oldKeys.size() != newKeys.size()) {
            return null;
        }
        DilutionBlock dilutionBlock = new DilutionBlock(blockchain, blockchain.getLastBlock().getIndex() + 1, blockchain.getLastBlock().getHash());
        for (int i = 0; i < oldKeys.size(); i++) {
            dilutionBlock.addOldKey(oldKeys.get(i), keyOriginBlockIndices.get(i));
            dilutionBlock.addNewKey(newKeys.get(i));
//            System.out.println("................OLD KEY: " + Hex.toHexString(oldKeys.get(i)));
//            Block block = blockchain.getBlock(keyOriginBlockIndices.get(i));
//            if (block.getPrefix()[0] == 'D') {
//                DilutionBlock dilutionBlock1 = (DilutionBlock) block;
//                System.out.println("................ANON SET: " + Hex.toHexString(dilutionBlock1.getAnonymitySet()));
//            }
        }
//        dilutionBlock.setDilutionFactor(getDilutionFactor(keyOriginBlockIndices, blockchain));
/*        long chainScoreDifference = getChainScore(keyOriginBlockIndices, blockchain, blockchain.getLastBlock().getIndex() + 1) - blockchain.getLastBlock().getChainScore();
        if (chainScoreDifference < keyOriginBlockIndices.size() - 1) {
//            System.out.println("Chain score difference " + chainScoreDifference + " for pool members " + keyOriginBlockIndices.size());
//            System.out.println("Key origin block indices:");
            for (int i = 0; i < keyOriginBlockIndices.size(); i++) {
                long keyOriginBlockIndex = keyOriginBlockIndices.get(i);
//                System.out.println(keyOriginBlockIndex);
                Block block = blockchain.getBlock(keyOriginBlockIndex);
                switch (block.getPrefix()[0]) {
                    case 'R':
                        RegistrationBlock registrationBlock = (RegistrationBlock) block;
                        System.out.println(Hex.toHexString(registrationBlock.getPublicKey()));
                        break;
                    case 'D':
                        DilutionBlock dilutionBlock1 = (DilutionBlock) block;
                        System.out.println(Hex.toHexString(dilutionBlock1.getAnonymitySet()));
                        break;
                }
//                System.out.println("Key: " + Hex.toHexString(oldKeys.get(i)));
            }
            blockchain.print();
        }*/
//        System.out.println("Nr key orgs: " + keyOriginBlockIndices.size());
        dilutionBlock.setChainScore(getChainScore(keyOriginBlockIndices, blockchain, blockchain.getLastBlock().getIndex() + 1));
        dilutionBlock.setBlockAssembler(poolManager);
        dilutionBlock.setPoolIdentifier(poolIdentifier);
        dilutionBlock.setBlockAssemblerKeyOriginBlockIndex(poolManagerKeyOriginBlockIndex);
        return dilutionBlock;
    }

    public byte[] createSignatureForOldKey(DilutionBlock dilutionBlock, byte[] oldKey, Signature signature) {
        byte[] signatureWithKey = null;
        try {
            signature.update(dilutionBlock.getBytesWithoutValidation());
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(signature.sign());
            ArrayList<byte[]> bytesToConcatenate = new ArrayList<>();
            bytesToConcatenate.add(oldKey);
            bytesToConcatenate.add(signatureBytes);
            signatureWithKey = ByteUtils.concatenateByteArrays(bytesToConcatenate);
        } catch (SignatureException exception) {
            System.err.println("Signature exception in signing dilution");
        }
        return signatureWithKey;
    }

    public DilutionBlock createUnvalidatedBlock(Blockchain blockchain, byte[] message) {
        int numberOfKeys = getNumberOfKeys(message);
        List<byte[]> oldKeys = getOldKeys(message, numberOfKeys);
        List<Long> keyOriginBlockIndices = getKeyOriginBlockIndices(message, numberOfKeys);
        List<byte[]> newKeys = getNewKeys(message, numberOfKeys);
//        List<byte[]> signatures = getSignatures(message, numberOfKeys);
        DilutionBlock dilutionBlock = new DilutionBlock(blockchain, getIndex(message), getPredecessorHash(message));
        for (int i = 0; i < oldKeys.size(); i++) {
            dilutionBlock.addOldKey(oldKeys.get(i), keyOriginBlockIndices.get(i));
            dilutionBlock.addNewKey(newKeys.get(i));
        }
        return dilutionBlock;
    }

    public DilutionBlock createBlock(Blockchain blockchain, byte[] message) {
        int numberOfKeys = getNumberOfKeys(message);
        int depth = getDepth(message);
        byte[] anonymitySet = getAnonymitySet(message);
        List<byte[]> oldKeys = getOldKeys(message, numberOfKeys);
        List<Long> keyOriginBlockIndices = getKeyOriginBlockIndices(message, numberOfKeys);
        List<byte[]> newKeys = getNewKeys(message, numberOfKeys);
        List<byte[]> signatures = getSignatures(message, numberOfKeys);
        byte[] poolIdentifier = getPoolIdentifier(message);
        byte[] blockAssembler = getBlockAssembler(message);
        long blockAssemblerOriginIndex = getBlockAssemblerOriginIndex(message);

        int correctDepth = 0;
        byte[] correctAnonymitySet = null;
        if (blockchain.isHeadless()) {
            correctDepth = depth;
            correctAnonymitySet = anonymitySet;
        } else {
            for (int j = 0; j < oldKeys.size(); j++) {
                Long keyOriginBlockIndex = keyOriginBlockIndices.get(j);
                Block originBlock = blockchain.getBlock(keyOriginBlockIndex);
                int otherDepth = 0;
                byte[] otherAnonymitySet = oldKeys.get(j);
                if (originBlock.getPrefix()[0] == 'D') {
                    DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                    otherAnonymitySet = originDilutionBlock.getAnonymitySet();
                    otherDepth = originDilutionBlock.getDepth();
                }
                if (correctAnonymitySet == null || ByteUtils.compareByteArrays(correctAnonymitySet, otherAnonymitySet) > 0) {
                    correctAnonymitySet = otherAnonymitySet;
                }
                if (otherDepth > correctDepth) {
                    correctDepth = otherDepth;
                }
            }
            correctDepth++;
        }
        if (depth == correctDepth && ByteUtils.byteArraysAreEqual(correctAnonymitySet, anonymitySet)) {
            DilutionBlock dilutionBlock = new DilutionBlock(blockchain, getIndex(message), getPredecessorHash(message));
            dilutionBlock.setPoolIdentifier(poolIdentifier);
            dilutionBlock.setBlockAssembler(blockAssembler);
            dilutionBlock.setBlockAssemblerKeyOriginBlockIndex(blockAssemblerOriginIndex);
            for (int i = 0; i < oldKeys.size(); i++) {
                dilutionBlock.addOldKey(oldKeys.get(i), keyOriginBlockIndices.get(i));
                dilutionBlock.addNewKey(newKeys.get(i));
            }
            long chainScore = getChainScore(message);
            if (blockchain.isHeadless() || chainScore == getChainScore(keyOriginBlockIndices, blockchain, getIndex(message))) {
//                dilutionBlock.setDilutionFactor(dilutionFactor);
                dilutionBlock.setChainScore(chainScore);
                if (blockchain.isHeadless()) {
                    dilutionBlock.setAnonymitySet(getAnonymitySet(message));
                    dilutionBlock.setDepth(getDepth(message));
                }
                try {
                    boolean signaturesAreValid = true;
                    int i = 0;
                    while (i < oldKeys.size()) {
//                        System.out.println("Signature on dilution block:");
//                    System.out.println(Hex.toHexString(signatures.get(i)));
//                    System.out.println(Hex.toHexString(oldKeys.get(i)));
//                    System.out.println(Hex.toHexString(dilutionBlock.getBytesWithoutValidation()));
                        Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(oldKeys.get(i)));
//                        System.out.println("Created signature for verifying");
                        signature.update(dilutionBlock.getBytesWithoutValidation());
//                        System.out.println("Updated signature");
                        if (!signature.verify(signatures.get(i))) {
//                            System.out.println("This one is invalid");
                            signaturesAreValid = false;
                        }
                        i++;
                    }
                    if (signaturesAreValid) {
//                        System.out.println("All signatures are valid");
                        for (int j = 0; j < oldKeys.size(); j++) {
                            dilutionBlock.addSignature(oldKeys.get(j), signatures.get(j));
                        }
                    } else {
                        System.err.println("There is an invalid signature");
                        dilutionBlock = null;
                    }
                } catch (NoSuchAlgorithmException exception) {
                    System.err.println("No such algorithm in process dilution");
                    dilutionBlock = null;
                } catch (InvalidKeySpecException exception) {
                    System.err.println("Invalid key spec in process dilution");
                    dilutionBlock = null;
                } catch (InvalidKeyException exception) {
                    System.err.println("Invalid key in process dilution");
                    dilutionBlock = null;
                } catch (SignatureException exception) {
                    System.err.println("Signature exception in process dilution");
                    System.err.println(exception);
                    dilutionBlock = null;
                }
                return dilutionBlock;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }
}
