package app.Client.Layers.BlockchainLayer.Blockchain;

import app.Client.Datastructures.ByteMap;
import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.*;
import app.Client.Layers.BlockchainLayer.KeyWithOrigin;
import app.Client.Layers.BlockchainLayer.ManagedDilutionProcess;
import app.Client.Layers.BlockchainLayer.PoolResponse;
import app.Client.Utils.ByteUtils;
import org.bouncycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Blockchain {
    private ArrayList<Block> blocks;
    private byte[] id;
    private byte[] managerPublicKey;
    private ElectionPhase electionPhase;
    private ByteMap<Long> voterRegistrationIndices;
    private HashMap<ElectionPhase, Long> phaseStartIndices;
    private ArrayList<Long> preDepthBlockIndices;
    private ArrayList<Long> depthBlockIndices;
    private int maxDepth;
    private boolean inPreDepthPhase;
    private boolean headless;
    private DilutionBlockFactory dilutionBlockFactory;
    private CommitmentBlockFactory commitmentBlockFactory;

    public Blockchain(byte[] id, byte[] managerPublicKey) {
        blocks = new ArrayList<>();
        this.id = id;
        this.managerPublicKey = managerPublicKey;
        electionPhase = ElectionPhase.REGISTRATION;
        voterRegistrationIndices = new ByteMap<>(Election.VOTER_ID_LENGTH);
        phaseStartIndices = new HashMap<>();
        preDepthBlockIndices = new ArrayList<>();
        depthBlockIndices = new ArrayList<>();
        maxDepth = 1;
        inPreDepthPhase = false;
        headless = true;
    }

    public boolean addInitializationBlock(InitializationBlock initializationBlock) {
        if (blocks.isEmpty()) {
            blocks.add(initializationBlock);
            phaseStartIndices.put(ElectionPhase.REGISTRATION, 0L);
            headless = false;
            return true;
        } else {
            return false;
        }
    }

    public Block getLastBlock() {
        if (blocks.isEmpty()) {
            return null;
        }
        return blocks.get(blocks.size() - 1);
    }

    public boolean keyWasUsedInCommitment (byte[] oldKey, long keyOriginBlockIndex, long blockIndexToCheckAt) {
        if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex >= blocks.size()) {
            return false;
        } else {
            boolean isUsedInCommit = false;
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    RegistrationBlock registrationBlock = (RegistrationBlock) keyOriginBlock;
                    if (ByteUtils.byteArraysAreEqual(registrationBlock.getPublicKey(), oldKey)) {
                        for (long blockUseIndex : registrationBlock.getBlockUseIndices()) {
                            Block usingBlock = getBlock(blockUseIndex);
                            if (usingBlock.getIndex() < blockIndexToCheckAt) {
                                switch (usingBlock.getPrefix()[0]) {
                                    case 'C':
                                        CommitmentBlock usingCommitmentBlock = (CommitmentBlock) usingBlock;
                                        isUsedInCommit = ByteUtils.byteArraysAreEqual(usingCommitmentBlock.getPublicKey(), registrationBlock.getPublicKey());
                                        break;
                                }
                            }
                        }
                    }
                    break;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    for (byte[] newKey: dilutionBlock.getNewKeys()) {
                        if (!isUsedInCommit) {
                            if (ByteUtils.byteArraysAreEqual(newKey, oldKey)) {
                                for (long blockUseIndex : dilutionBlock.getBlockUseIndices()) {
                                    Block usingBlock = getBlock(blockUseIndex);
                                    if (usingBlock.getIndex() < blockIndexToCheckAt) {
                                        switch (usingBlock.getPrefix()[0]) {
                                            case 'C':
                                                CommitmentBlock usingCommitmentBlock = (CommitmentBlock) usingBlock;
                                                if (ByteUtils.byteArraysAreEqual(usingCommitmentBlock.getPublicKey(), newKey)) {
                                                    isUsedInCommit = true;
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
            }
            return isUsedInCommit;
        }
    }

    public boolean oldKeyIsStillValid(byte[] oldKey, long keyOriginBlockIndex, long blockIndexToCheckAt) {
        if (blocks.size() == 0) {
            return false;
        } else if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex > getLastBlock().getIndex()) {
            return false;
        } else {
            boolean isValid = false;
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    RegistrationBlock registrationBlock = (RegistrationBlock) keyOriginBlock;
                    isValid = ByteUtils.byteArraysAreEqual(registrationBlock.getPublicKey(), oldKey);
                    if (isValid) {
                        for (long blockUseIndex : registrationBlock.getBlockUseIndices()) {
                            Block usingBlock = getBlock(blockUseIndex);
//                            Block usingBlock = blocks.get((int) blockUseIndex);
                            if (usingBlock != null && usingBlock.getIndex() < blockIndexToCheckAt) {
                                switch (usingBlock.getPrefix()[0]) {
                                    case 'R':
                                        RegistrationBlock usingRegistrationBlock = (RegistrationBlock) usingBlock;
                                        if (ByteUtils.byteArraysAreEqual(registrationBlock.getVoterId(), usingRegistrationBlock.getVoterId())) {
                                            isValid = false;
                                        }
                                        break;
                                    case 'D':
                                        DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                        for (byte[] dilutedKey : usingDilutionBlock.getOldKeys()) {
                                            if (ByteUtils.byteArraysAreEqual(dilutedKey, registrationBlock.getPublicKey())) {
                                                isValid = false;
                                            }
                                        }
                                        break;
                                }
                            }
                        }
                    }
                    break;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    for (byte[] newKey: dilutionBlock.getNewKeys()) {
                        if (!isValid) {
                            isValid = ByteUtils.byteArraysAreEqual(newKey, oldKey);
                            if (isValid) {
                                for (long blockUseIndex : dilutionBlock.getBlockUseIndices()) {
                                    Block usingBlock = getBlock(blockUseIndex);
//                                    Block usingBlock = blocks.get((int) blockUseIndex);
                                    if (usingBlock != null && usingBlock.getIndex() < blockIndexToCheckAt) {
                                        switch (usingBlock.getPrefix()[0]) {
                                            case 'D':
                                                DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                                for (byte[] dilutedKey : usingDilutionBlock.getOldKeys()) {
                                                    if (ByteUtils.byteArraysAreEqual(dilutedKey, newKey)) {
                                                        isValid = false;
                                                    }
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
            }
            return isValid;
        }
    }

    public boolean oldKeyIsStillValid(byte[] oldKey, long keyOriginBlockIndex) {
        if (blocks.size() == 0) {
            return false;
        }
        return oldKeyIsStillValid(oldKey, keyOriginBlockIndex, getLastBlock().getIndex() + 1);
    }

    public boolean oldKeyIsStillValid2(byte[] oldKey, long keyOriginBlockIndex, long blockIndexToCheckAt) {
        if (blocks.size() == 0) {
            System.out.println("OLD KEY INVALID because block size 0");
            return false;
        } else if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex > getLastBlock().getIndex()) {
            System.out.println("OLD KEY INVALID because block index " + keyOriginBlockIndex);
            return false;
        } else {
            boolean isValid = false;
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    RegistrationBlock registrationBlock = (RegistrationBlock) keyOriginBlock;
                    isValid = ByteUtils.byteArraysAreEqual(registrationBlock.getPublicKey(), oldKey);
                    if (isValid) {
                        for (long blockUseIndex : registrationBlock.getBlockUseIndices()) {
                            Block usingBlock = getBlock(blockUseIndex);
//                            Block usingBlock = blocks.get((int) blockUseIndex);
                            if (usingBlock != null && usingBlock.getIndex() < blockIndexToCheckAt) {
                                switch (usingBlock.getPrefix()[0]) {
                                    case 'R':
                                        RegistrationBlock usingRegistrationBlock = (RegistrationBlock) usingBlock;
                                        if (ByteUtils.byteArraysAreEqual(registrationBlock.getVoterId(), usingRegistrationBlock.getVoterId())) {
                                            isValid = false;
                                            System.out.println("OLD KEY INVALID because voter ID " + Hex.toHexString(registrationBlock.getVoterId()) + " was equal to " + Hex.toHexString(usingRegistrationBlock.getVoterId()) + " used in registration block " + usingRegistrationBlock.getIndex() + " from origin block " + keyOriginBlockIndex);
                                        }
                                        break;
                                    case 'D':
                                        DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                        for (byte[] dilutedKey : usingDilutionBlock.getOldKeys()) {
                                            if (ByteUtils.byteArraysAreEqual(dilutedKey, registrationBlock.getPublicKey())) {
                                                isValid = false;
                                                System.out.println("OLD KEY INVALID because key " + Hex.toHexString(registrationBlock.getPublicKey()) + " was equal to " + Hex.toHexString(dilutedKey) + " used in dilution block " + usingDilutionBlock.getIndex() + " from origin block " + keyOriginBlockIndex);
                                            }
                                        }
                                        break;
                                }
                            }
                        }
                    }
                    break;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    for (byte[] newKey: dilutionBlock.getNewKeys()) {
                        if (!isValid) {
                            isValid = ByteUtils.byteArraysAreEqual(newKey, oldKey);
                            if (isValid) {
                                for (long blockUseIndex : dilutionBlock.getBlockUseIndices()) {
                                    Block usingBlock = getBlock(blockUseIndex);
//                                    Block usingBlock = blocks.get((int) blockUseIndex);
                                    if (usingBlock != null && usingBlock.getIndex() < blockIndexToCheckAt) {
                                        switch (usingBlock.getPrefix()[0]) {
                                            case 'D':
                                                DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                                for (byte[] dilutedKey : usingDilutionBlock.getOldKeys()) {
                                                    if (ByteUtils.byteArraysAreEqual(dilutedKey, newKey)) {
                                                        isValid = false;
                                                        System.out.println("OLD KEY INVALID because key " + Hex.toHexString(newKey) + " was equal to " + Hex.toHexString(dilutedKey) + " used in dilution block " + usingDilutionBlock.getIndex() + " from origin block " + keyOriginBlockIndex);
                                                    }
                                                }
                                                break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
            }
            return isValid;
        }
    }

    public boolean oldKeyIsStillValid2(byte[] oldKey, long keyOriginBlockIndex) {
        if (blocks.size() == 0) {
            return false;
        }
        return oldKeyIsStillValid2(oldKey, keyOriginBlockIndex, getLastBlock().getIndex() + 1);
    }

    public boolean oldKeyDepthIsOK(byte[] oldKey, long keyOriginBlockIndex, long blockIndexToCheckAt) {
        if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex >= blocks.size()) {
            return false;
        } else {
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
//            Block keyOriginBlock = blocks.get((int) keyOriginBlockIndex);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    return true;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    DepthInfo depthInfo = getDepthInfoAtIndex(blockIndexToCheckAt);
                    return dilutionBlock.getDepth() < depthInfo.getMaxDepth();
                default:
                    return false;
            }
        }
    }

    public boolean oldKeyDepthIsOK(byte[] oldKey, long keyOriginBlockIndex) {
        if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex >= blocks.size()) {
            return false;
        } else {
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
//            Block keyOriginBlock = blocks.get((int) keyOriginBlockIndex);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    return true;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    return dilutionBlock.getDepth() < maxDepth;
                default:
                    return false;
            }
        }
    }

    public boolean oldKeyDepthIsLower(byte[] oldKey, long keyOriginBlockIndex, long blockIndexToCheckAt) {
        if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex >= blocks.size()) {
            return false;
        } else {
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
//            Block keyOriginBlock = blocks.get((int) keyOriginBlockIndex);
            DepthInfo depthInfo = getDepthInfoAtIndex(blockIndexToCheckAt);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    return 0 < depthInfo.getMaxDepth() - 1;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    return dilutionBlock.getDepth() < depthInfo.getMaxDepth() - 1;
                default:
                    return false;
            }
        }
    }

    public boolean oldKeyDepthIsLower(byte[] oldKey, long keyOriginBlockIndex) {
        if (keyOriginBlockIndex <= 0 || keyOriginBlockIndex >= blocks.size()) {
            return false;
        } else {
            Block keyOriginBlock = getBlock(keyOriginBlockIndex);
//            Block keyOriginBlock = blocks.get((int) keyOriginBlockIndex);
            switch (keyOriginBlock.getPrefix()[0]) {
                case 'R':
                    return 0 < maxDepth - 1;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) keyOriginBlock;
                    return dilutionBlock.getDepth() < maxDepth - 1;
                default:
                    return false;
            }
        }
    }

    public ElectionPhase getElectionPhaseAtIndex(long index) {
        if (!phaseStartIndices.containsKey(ElectionPhase.DILUTION) || index <= phaseStartIndices.get(ElectionPhase.DILUTION)) {
            return ElectionPhase.REGISTRATION;
        } else if (!phaseStartIndices.containsKey(ElectionPhase.COMMITMENT) || index <= phaseStartIndices.get(ElectionPhase.COMMITMENT)) {
            return ElectionPhase.DILUTION;
        } else if (!phaseStartIndices.containsKey(ElectionPhase.VOTING) || index <= phaseStartIndices.get(ElectionPhase.VOTING)) {
            return ElectionPhase.COMMITMENT;
        } else {
            return ElectionPhase.VOTING;
        }
    }

    public static class DepthInfo {
        private int maxDepth;
        private boolean inPreDepthPhase;

        public DepthInfo(int maxDepth, boolean inPreDepthPhase) {
            this.maxDepth = maxDepth;
            this.inPreDepthPhase = inPreDepthPhase;
        }

        public int getMaxDepth() {
            return maxDepth;
        }

        public boolean isInPreDepthPhase() {
            return inPreDepthPhase;
        }
    }

    public DepthInfo getDepthInfoAtIndex(long index) {
        int indexInIndices = 0;
        while (indexInIndices < preDepthBlockIndices.size() && preDepthBlockIndices.get(indexInIndices) < index) {
            indexInIndices++;
        }
        indexInIndices--;
        int maxDepthAtIndex = 2 + indexInIndices;
        if (indexInIndices >= 0 && (depthBlockIndices.size() <= indexInIndices || depthBlockIndices.get(indexInIndices) >= index)) {
            return new DepthInfo(maxDepthAtIndex, true);
        } else {
            return new DepthInfo(maxDepthAtIndex, false);
        }
    }

    public boolean checkIfCanAppendBlockMessage(byte[] message, long index) {
        boolean mayAppendInThisPhase;
        ElectionPhase phaseAtIndex = getElectionPhaseAtIndex(index);
        switch (message[0]) {
            case 'd':
            case 'R':
                return phaseAtIndex == ElectionPhase.REGISTRATION;
            case 'e':
                return phaseAtIndex == ElectionPhase.DILUTION;
            case 'D':
                mayAppendInThisPhase = phaseAtIndex == ElectionPhase.DILUTION;
                int i;
                int numberOfKeys = dilutionBlockFactory.getNumberOfKeys(message);
                List<byte[]> oldKeys = dilutionBlockFactory.getOldKeys(message, numberOfKeys);
                List<Long> keyOriginBlockIndices = dilutionBlockFactory.getKeyOriginBlockIndices(message, numberOfKeys);
                if (mayAppendInThisPhase) {
                    DepthInfo depthAtIndex = getDepthInfoAtIndex(index);
                    mayAppendInThisPhase = dilutionBlockFactory.getDepth(message) <= depthAtIndex.getMaxDepth();
                    if (mayAppendInThisPhase && depthAtIndex.isInPreDepthPhase()) {
                        mayAppendInThisPhase = false;
                        i = 0;
                        while (i < numberOfKeys && !mayAppendInThisPhase) {
                            Block originBlock = getBlock(keyOriginBlockIndices.get(i));
//                            Block originBlock = blocks.get(keyOriginBlockIndices.get(i).intValue());
                            if (originBlock.getPrefix()[0] == 'D') {
                                DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                                if (originDilutionBlock.getDepth() < depthAtIndex.getMaxDepth() - 1) {
                                    mayAppendInThisPhase = true;
                                }
                            } else {
                                //Keys originating in a registration block will be of depth 0, which is always low enough
                                mayAppendInThisPhase = true;
                            }
                            i++;
                        }
                    }
                }
                i = 0;
                while (mayAppendInThisPhase && i < numberOfKeys) {
                    mayAppendInThisPhase = oldKeyIsStillValid(oldKeys.get(i), keyOriginBlockIndices.get(i), index);
                    i++;
                }
                return mayAppendInThisPhase;
            case '0':
                if (phaseAtIndex == ElectionPhase.DILUTION) {
                    DepthInfo depthInfo = getDepthInfoAtIndex(index);
                    return depthInfo.isInPreDepthPhase();
                } else {
                    return false;
                }
            case '1':
                if (phaseAtIndex == ElectionPhase.DILUTION) {
                    DepthInfo depthInfo = getDepthInfoAtIndex(index);
                    return !depthInfo.isInPreDepthPhase();
                } else {
                    return false;
                }
            case '3':
                return phaseAtIndex == ElectionPhase.COMMITMENT;
            case 'C':
                return phaseAtIndex == ElectionPhase.COMMITMENT && oldKeyIsStillValid(commitmentBlockFactory.getPublicKey(message), commitmentBlockFactory.getKeyOriginBlockIndex(message));
            default:
                return false;
        }
    }

    public boolean appendBlock(Block block) {
        if (blocks.isEmpty()) {
            System.out.println("...................... couldn't append block because blocks array is empty");
            return false;
        }
        boolean mayAppendInThisPhase = false;
        switch (block.getPrefix()[0]) {
            case 'd':
            case 'R':
                mayAppendInThisPhase = electionPhase == ElectionPhase.REGISTRATION;
                break;
            case 'D':
                mayAppendInThisPhase = electionPhase == ElectionPhase.DILUTION;
                if (mayAppendInThisPhase) {
                    DilutionBlock dilutionBlock = (DilutionBlock) block;
                    mayAppendInThisPhase = dilutionBlock.getDepth() <= maxDepth;
                    if (mayAppendInThisPhase && inPreDepthPhase) {
                        mayAppendInThisPhase = false;
                        int i = 0;
                        while (i < dilutionBlock.getKeyOriginBlockIndices().size() && !mayAppendInThisPhase) {
                            Block originBlock = getBlock(dilutionBlock.getKeyOriginBlockIndices().get(i));
//                            Block originBlock = blocks.get(dilutionBlock.getKeyOriginBlockIndices().get(i).intValue());
                            if (originBlock.getPrefix()[0] == 'D') {
                                DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                                if (originDilutionBlock.getDepth() < maxDepth - 1) {
                                    mayAppendInThisPhase = true;
                                }
                            } else {
                                //Keys originating in a registration block will be of depth 0, which is always low enough
                                mayAppendInThisPhase = true;
                            }
                            i++;
                        }
                    }
                }
                break;
            case 'e':
                mayAppendInThisPhase = electionPhase == ElectionPhase.DILUTION;
                break;
            case '0':
                mayAppendInThisPhase = electionPhase == ElectionPhase.DILUTION && inPreDepthPhase;
                break;
            case '1':
                mayAppendInThisPhase = electionPhase == ElectionPhase.DILUTION && !inPreDepthPhase;
                break;
            case '3':
            case 'C':
                mayAppendInThisPhase = electionPhase == ElectionPhase.COMMITMENT;
                if (!mayAppendInThisPhase) {
                    CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                    System.out.println("...................... couldn't append commitment block because not in commitmentment phase (" + electionPhase + ", " + Hex.toHexString(commitmentBlock.getPublicKey()) + ", " + commitmentBlock.getIndex() + ")");
                    print();
                }
                break;
        }
        if (mayAppendInThisPhase) {
            switch (block.getPrefix()[0]) {
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) block;
                    int i = 0;
                    while (mayAppendInThisPhase && i < dilutionBlock.getOldKeys().size()) {
                        mayAppendInThisPhase = oldKeyIsStillValid(dilutionBlock.getOldKeys().get(i), dilutionBlock.getKeyOriginBlockIndices().get(i));
                        i++;
                    }
                    break;
                case 'C':
                    CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                    mayAppendInThisPhase = oldKeyIsStillValid(commitmentBlock.getPublicKey(), commitmentBlock.getKeyOriginBlockIndex());
                    if (!mayAppendInThisPhase) {
                        System.out.println("...................... couldn't append commitment block because old key not valid (" + commitmentBlock.getPublicKey() + ", " + commitmentBlock.getKeyOriginBlockIndex() + ")");
                        print();
                    }
                    break;
            }
            if (mayAppendInThisPhase) {
                HashSet<Long> indicesOfBlocksUsed = new HashSet<>();
                long blockIndex = block.getIndex();
                switch (block.getPrefix()[0]) {
                    case 'd':
                        electionPhase = ElectionPhase.DILUTION;
                        phaseStartIndices.put(ElectionPhase.DILUTION, block.getIndex());
                        break;
                    case 'R':
                        RegistrationBlock registrationBlock = (RegistrationBlock) block;
                        byte[] voterId = registrationBlock.getVoterId();
                        Long voterRegistrationIndex = voterRegistrationIndices.get(voterId);
                        if (voterRegistrationIndex != null) {
                            indicesOfBlocksUsed.add(voterRegistrationIndex);
                        }
                        voterRegistrationIndices.put(voterId, blockIndex);
                        break;
                    case 'D':
                        DilutionBlock dilutionBlock = (DilutionBlock) block;
                        for (Long keyOriginBlockIndex: dilutionBlock.getKeyOriginBlockIndices()) {
                            if (keyOriginBlockIndex != null) {
                                getBlock(keyOriginBlockIndex).addBlockUseIndex(blockIndex);
                            }
                        }
                        break;
                    case '0':
                        depthBlockIndices.add(block.getIndex());
                        inPreDepthPhase = false;
                        break;
                    case '1':
                        preDepthBlockIndices.add(block.getIndex());
                        maxDepth++;
                        inPreDepthPhase = true;
                        break;
                    case 'e':
                        electionPhase = ElectionPhase.COMMITMENT;
                        phaseStartIndices.put(ElectionPhase.COMMITMENT, block.getIndex());
                        break;
                    case 'C':
                        CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                        Long keyOriginBlockIndex = commitmentBlock.getKeyOriginBlockIndex();
                        if (keyOriginBlockIndex != null) {
                            getBlock(keyOriginBlockIndex).addBlockUseIndex(blockIndex);
                        }
                        break;
                    case '3':
                        electionPhase = ElectionPhase.VOTING;
                        phaseStartIndices.put(ElectionPhase.VOTING, block.getIndex());
                        break;
                }
                blocks.add(block);
                for (Long indexOfBlockUsed: indicesOfBlocksUsed) {
                    Block usedBlock = getBlock(indexOfBlockUsed);
                    usedBlock.addBlockUseIndex(blockIndex);
                }
                return true;
            } else {
//                System.out.println("...................... couldn't append block because secondary check failed (prefix: " + new String(block.getPrefix()) + ")");
                return false;
            }
        } else {
//            System.out.println("...................... couldn't append block because initial check failed (prefix: " + new String(block.getPrefix()) + ")");
            return false;
        }
    }

    public void prependBlock(Block block) {
        if (headless) {
            blocks.add(0, block);
            if (block.getPrefix()[0] == 'I') {
                headless = false;
            }
        }
    }

    public InitializationBlock getInitializationBlock() {
        if (blocks.isEmpty() || headless) {
            return null;
        }
        return (InitializationBlock) blocks.get(0);
    }

    public byte[] getId() {
        return id;
    }

    public boolean checkId(byte[] idToCheck) {
        if (idToCheck.length != id.length) {
            return false;
        }
        boolean isEqual = true;
        for (int i = 0; i < id.length; i++) {
            if (id[i] != idToCheck[i]) {
                isEqual = false;
            }
        }
        return isEqual;
    }

    public byte[] getManagerPublicKey() {
        return managerPublicKey;
    }

    public boolean checkManagerPublicKey(byte[] managerPublicKeyToCheck) {
        if (managerPublicKeyToCheck.length != managerPublicKey.length) {
            return false;
        }
        boolean isEqual = true;
        for (int i = 0; i < managerPublicKey.length; i++) {
            if (managerPublicKey[i] != managerPublicKeyToCheck[i]) {
                isEqual = false;
            }
        }
        return isEqual;
    }

    public RegistrationBlock getVoterRegistrationBlock(byte[] voterId) {
        RegistrationBlock registrationBlock = null;
        int i = blocks.size() - 1;
        while (registrationBlock == null && i >= 0) {
            Block currentBlock = getBlock(i);
//            Block currentBlock = blocks.get(i);
            if (currentBlock.getPrefix()[0] == 'R') {
                RegistrationBlock currentRegistrationBlock = (RegistrationBlock) currentBlock;
                if (ByteUtils.byteArraysAreEqual(voterId, currentRegistrationBlock.getVoterId())) {
                    registrationBlock = currentRegistrationBlock;
                }
            }
            i--;
        }
        return registrationBlock;
    }

    public Block getBlock(long index) {
        if (blocks.size() == 0) {
            return null;
        } else {
            long startIndex = blocks.get(0).getIndex();
            if (index >= startIndex && index < blocks.size() + startIndex) {
                return blocks.get((int) (index - startIndex));
            } else {
                return null;
            }
        }
    }

    public long getInitialBlockIndex() {
        return blocks.get(0).getIndex();
    }

    public int getNumberOfBlocks() {
        return blocks.size();
    }

    public boolean isHeadless() {
        return headless;
    }

    public boolean copyFromFork(Blockchain forkedChain) {
        long blockIndex = -1;
        boolean forkIsValid = true;
        long startIndex = forkedChain.getInitialBlockIndex();
        ElectionPhase electionPhaseInFork = getElectionPhaseAtIndex(startIndex);
        DepthInfo depthInfo = getDepthInfoAtIndex(startIndex);
        boolean isInPredepthPhase = depthInfo.isInPreDepthPhase();
        int maxDepthInFork = depthInfo.getMaxDepth();
        HashMap<byte[], Long> forkedVoterRegistrationIndices = new HashMap<>();
        HashMap<byte[], Long> forkedKeyUses = new HashMap<>();
        HashMap<ElectionPhase, Long> forkedPhaseStartIndices = new HashMap<>();
        ArrayList<Long> forkedPredepthBlockIndices = new ArrayList<>();
        ArrayList<Long> forkedDepthBlockIndices = new ArrayList<>();
        int i = 0;
        while (forkIsValid && i < forkedChain.getNumberOfBlocks()) {
            Block block = forkedChain.getBlock(i + startIndex);
            blockIndex = block.getIndex();
            switch (block.getPrefix()[0]) {
                case 'D':
                    if (electionPhaseInFork != ElectionPhase.DILUTION) {
                        forkIsValid = false;
                    }
                    DilutionBlock dilutionBlock = (DilutionBlock) block;
                    for (int j = 0; j < dilutionBlock.getOldKeys().size(); j++) {
                        byte[] oldKey = dilutionBlock.getOldKeys().get(j);
                        long keyOriginBlockIndex = dilutionBlock.getKeyOriginBlockIndices().get(j);
                        forkIsValid = false;

                        if (keyOriginBlockIndex > 0 && keyOriginBlockIndex < blockIndex) {
                            Blockchain chainToGetBlockFrom = this;
                            if (keyOriginBlockIndex >= startIndex) {
                                chainToGetBlockFrom = forkedChain;
//                                keyOriginBlockIndex -= startIndex;
                            }
                            Block keyOriginBlock = chainToGetBlockFrom.getBlock(keyOriginBlockIndex);
                            switch (keyOriginBlock.getPrefix()[0]) {
                                case 'R':
                                    RegistrationBlock keyOriginRegistrationBlock = (RegistrationBlock) keyOriginBlock;
                                    forkIsValid = ByteUtils.byteArraysAreEqual(keyOriginRegistrationBlock.getPublicKey(), oldKey);
                                    if (forkIsValid) {
                                        for (long blockUseIndex : keyOriginRegistrationBlock.getBlockUseIndices()) {
                                            if (blockUseIndex < startIndex) {
                                                Block usingBlock = getBlock(blockUseIndex);
                                                switch (usingBlock.getPrefix()[0]) {
                                                    case 'R':
                                                        RegistrationBlock usingRegistrationBlock = (RegistrationBlock) usingBlock;
                                                        forkIsValid = !ByteUtils.byteArraysAreEqual(keyOriginRegistrationBlock.getVoterId(), usingRegistrationBlock.getVoterId());
                                                        break;
                                                    case 'D':
                                                        DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                                        for (byte[] dilutedKey : usingDilutionBlock.getOldKeys()) {
                                                            if (ByteUtils.byteArraysAreEqual(dilutedKey, keyOriginRegistrationBlock.getPublicKey())) {
                                                                forkIsValid = false;
                                                            }
                                                        }
                                                        break;
                                                }
                                            }
                                        }
                                        for (byte[] registeredVoterId : forkedVoterRegistrationIndices.keySet()) {
                                            if (forkIsValid && ByteUtils.byteArraysAreEqual(registeredVoterId, keyOriginRegistrationBlock.getVoterId())) {
                                                forkIsValid = false;
                                            }
                                        }
                                    }
                                    break;
                                case 'D':
                                    DilutionBlock keyOriginDilutionBlock = (DilutionBlock) keyOriginBlock;
                                    for (byte[] newKey: keyOriginDilutionBlock.getNewKeys()) {
                                        if (!forkIsValid) {
                                            forkIsValid = ByteUtils.byteArraysAreEqual(newKey, oldKey);
                                            if (forkIsValid) {
                                                for (long blockUseIndex : keyOriginDilutionBlock.getBlockUseIndices()) {
                                                    if (blockUseIndex < startIndex) {
                                                        Block usingBlock = getBlock(blockUseIndex);
                                                        switch (usingBlock.getPrefix()[0]) {
                                                            case 'D':
                                                                DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                                                for (byte[] dilutedKey : usingDilutionBlock.getOldKeys()) {
                                                                    if (ByteUtils.byteArraysAreEqual(dilutedKey, newKey)) {
                                                                        forkIsValid = false;
                                                                    }
                                                                }
                                                                break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    break;
                            }
                            for (byte[] forkedOldKey : forkedKeyUses.keySet()) {
                                if (ByteUtils.byteArraysAreEqual(forkedOldKey, oldKey)) {
                                    forkIsValid = false;
                                }
                            }
                        }
                        forkedKeyUses.put(oldKey, blockIndex);
                    }
                    break;
                case 'R':
                    if (electionPhaseInFork != ElectionPhase.REGISTRATION) {
                        forkIsValid = false;
                    }
                    RegistrationBlock registrationBlock = (RegistrationBlock) block;
                    forkedVoterRegistrationIndices.put(registrationBlock.getVoterId(), blockIndex);
                    break;
                case 'd':
                    if (electionPhaseInFork != ElectionPhase.REGISTRATION) {
                        forkIsValid = false;
                    }
                    electionPhaseInFork = ElectionPhase.DILUTION;
                    forkedPhaseStartIndices.put(ElectionPhase.DILUTION, blockIndex);
                    break;
                case '0':
                    if (electionPhaseInFork != ElectionPhase.DILUTION || !isInPredepthPhase) {
                        forkIsValid = false;
                    }
                    isInPredepthPhase = false;
                    forkedDepthBlockIndices.add(blockIndex);
                    break;
                case '1':
                    if (electionPhaseInFork != ElectionPhase.DILUTION || isInPredepthPhase) {
                        forkIsValid = false;
                    }
                    isInPredepthPhase = true;
                    maxDepthInFork++;
                    forkedPredepthBlockIndices.add(blockIndex);
                    break;
                case 'e':
                    if (electionPhaseInFork != ElectionPhase.DILUTION) {
                        forkIsValid = false;
                    }
                    electionPhaseInFork = ElectionPhase.COMMITMENT;
                    forkedPhaseStartIndices.put(ElectionPhase.COMMITMENT, blockIndex);
                    break;
                case '3':
                    if (electionPhaseInFork != ElectionPhase.COMMITMENT) {
                        forkIsValid = false;
                    }
                    electionPhaseInFork = ElectionPhase.VOTING;
                    forkedPhaseStartIndices.put(ElectionPhase.VOTING, blockIndex);
                    break;
            }
            i++;
        }
        if (forkIsValid) {
            for (int j = 0; j < forkedChain.getNumberOfBlocks(); j++) {
//                System.out.println("Forked block");
                Block block = forkedChain.getBlock(j + startIndex);
//                System.out.println(new String(block.getPrefix()));
                blockIndex = block.getIndex();
                if (blocks.size() > blockIndex) {
                    blocks.set((int) blockIndex, block);
                } else {
//                    System.out.println("add forked block");
                    blocks.add(block);
                }
            }
//            System.out.println("block index: " + blockIndex);
            if (blockIndex != -1) {
                for (int j = blocks.size() - 1; j > blockIndex; j--) {
//                    System.out.println("remove at " + j);
                    blocks.remove(j);
                }
            }
            i = preDepthBlockIndices.size() - 1;
            boolean removingPredepthIndex = true;
            while (removingPredepthIndex && i >= 0) {
                if (preDepthBlockIndices.get(i) >= startIndex) {
                    preDepthBlockIndices.remove(i);
                    removingPredepthIndex = false;
                }
                i--;
            }
            i = depthBlockIndices.size() - 1;
            boolean removingDepthIndex = true;
            while (removingDepthIndex && i >= 0) {
                if (depthBlockIndices.get(i) >= startIndex) {
                    depthBlockIndices.remove(i);
                    removingDepthIndex = false;
                }
                i--;
            }
            for (Long predepthIndex : forkedPredepthBlockIndices) {
                preDepthBlockIndices.add(predepthIndex);
            }
            for (Long depthIndex : forkedDepthBlockIndices) {
                depthBlockIndices.add(depthIndex);
            }
            for (ElectionPhase electionPhase1: forkedPhaseStartIndices.keySet()) {
                phaseStartIndices.put(electionPhase1, forkedPhaseStartIndices.get(electionPhase1));
                switch (electionPhase) {
                    case REGISTRATION:
                        if (electionPhase1 == ElectionPhase.DILUTION) {
                            electionPhase = ElectionPhase.DILUTION;
                        }
                    case DILUTION:
                        if (electionPhase1 == ElectionPhase.COMMITMENT) {
                            electionPhase = ElectionPhase.COMMITMENT;
                        }
                    case COMMITMENT:
                        if (electionPhase1 == ElectionPhase.VOTING) {
                            electionPhase = ElectionPhase.VOTING;
                        }
                }
            }
            if (blocks.get(0).getPrefix()[0] == 'I') {
//                System.out.println("Headless is false");
                headless = false;
            }
//        print();
        }
        return forkIsValid;
    }

    public void print() {
        System.out.println("______________");
        System.out.println("__Blockchain__");
        System.out.println("______________");
        for (Block block: blocks) {
            switch(block.getPrefix()[0]) {
                case 'I':
                    System.out.println(block.getIndex() + " Initialization block");
                    break;
                case 'R':
                    RegistrationBlock registrationBlock = (RegistrationBlock) block;
                    System.out.println(block.getIndex() + " Registration block");
                    System.out.println(" - voterId: " + new String(registrationBlock.getVoterId()));
                    System.out.println(" - key: " + Hex.toHexString(registrationBlock.getPublicKey()));
                    break;
                case 'd':
                    System.out.println(block.getIndex() + " Dilution start block");
                    break;
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) block;
                    System.out.println(block.getIndex() + " Dilution block");
                    System.out.println("Old keys:");
                    List<byte[]> oldKeys = dilutionBlock.getOldKeys();
                    List<Long> keyOriginBlockIndices = dilutionBlock.getKeyOriginBlockIndices();
                    for (int i = 0; i < oldKeys.size(); i++) {
                        if (i < keyOriginBlockIndices.size()) {
                            byte[] oldKey = oldKeys.get(i);
                            long keyOriginBlockIndex = keyOriginBlockIndices.get(i);
                            System.out.println(" - " + Hex.toHexString(oldKey) + " :: " + keyOriginBlockIndex);
                        }
                    }
                    System.out.println("New keys:");
                    for (byte[] newKey: dilutionBlock.getNewKeys()) {
                        System.out.println(" - " + Hex.toHexString(newKey));
                    }
                    break;
                case '1':
                    System.out.println(block.getIndex() + " Pre-depth block");
                    break;
                case '0':
                    System.out.println(block.getIndex() + " Depth block");
                    break;
                case 'e':
                    System.out.println(block.getIndex() + " Dilution end block");
                    break;
                case 'C':
                    CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                    System.out.println(block.getIndex() + " Commitment block");
                    System.out.println(" - " + Hex.toHexString(commitmentBlock.getPublicKey()));
                    System.out.println(" - " + Hex.toHexString(commitmentBlock.getVoteHash()));
                    break;
                case '3':
                    System.out.println(block.getIndex() + " Commitment end block");
                    break;
                default:
                    System.out.println(block.getIndex() + " Unknown block");
            }
        }
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public List<Block> getBlocksFromIndex(long index) {
        ArrayList<Block> toReturn = new ArrayList<>();
        if (blocks.isEmpty()) {
            return toReturn;
        }
        long indexInList = index - getInitialBlockIndex();
        if (indexInList < 0) {
            return toReturn;
        }
        while (indexInList < blocks.size()) {
            toReturn.add(blocks.get((int) indexInList));
            indexInList++;
        }
        return toReturn;
    }

    public ElectionPhase getElectionPhase() {
        return electionPhase;
    }

    public boolean isBlockPresent(Block blockToCheck) {
        long indexToCheck = blockToCheck.getIndex();
        if (indexToCheck < 0 || indexToCheck >= blocks.size()) {
            return false;
        }
        Block presentBlock = getBlock(indexToCheck);
        return presentBlock.isEqual(blockToCheck);
    }

    public boolean isPoolStillValid(ManagedDilutionProcess dilutionProcess) {
        boolean oldKeysAreStillValid = true;
        HashSet<PoolResponse> keysWithOrigin = dilutionProcess.getPoolResponses();
        if (keysWithOrigin != null) {
            for (PoolResponse keyWithOrigin : keysWithOrigin) {
                if (oldKeysAreStillValid) {
                    if (!oldKeyIsStillValid(keyWithOrigin.getOldPublicKey(), keyWithOrigin.getOldKeyOriginBlockIndex())) {
                        oldKeysAreStillValid = false;
                    }
                }
            }
        }
        return oldKeysAreStillValid;
    }

    public boolean voterIdIsRegistered(byte[] voterId) {
        return voterRegistrationIndices.containsKey(voterId);
    }

    public void discardLastBlock() {
        Block block = blocks.get(blocks.size() - 1);
        blocks.remove(blocks.size() - 1);
        //TODO reverse any changes done by block
        switch (block.getPrefix()[0]) {
            case 'R':
                RegistrationBlock registrationBlock = (RegistrationBlock) block;
                byte[] voterId = registrationBlock.getVoterId();
                voterRegistrationIndices.remove(voterId);
                for (int i = blocks.size() - 1; i >= 0; i--) {
                    Block currentBlock = blocks.get(i);
                    if (currentBlock.getPrefix()[0] == 'R') {
                        RegistrationBlock currentRegistrationBlock = (RegistrationBlock) currentBlock;
                        if (ByteUtils.byteArraysAreEqual(currentRegistrationBlock.getVoterId(), voterId)) {
                            voterRegistrationIndices.put(voterId, currentRegistrationBlock.getIndex());
                        }
                    }
                }
                break;
        }
    }

    public void setDilutionBlockFactory(DilutionBlockFactory dilutionBlockFactory) {
        this.dilutionBlockFactory = dilutionBlockFactory;
    }

    public void setCommitmentBlockFactory(CommitmentBlockFactory commitmentBlockFactory) {
        this.commitmentBlockFactory = commitmentBlockFactory;
    }

    public boolean isInPreDepthPhase() {
        return inPreDepthPhase;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public DilutionBlock getLastDilutionBlock() {
        if (!phaseStartIndices.containsKey(ElectionPhase.DILUTION)) {
            return null;
        } else {
            long lastBlockIndex = blocks.size() - 1;
            if (phaseStartIndices.containsKey(ElectionPhase.COMMITMENT)) {
                lastBlockIndex = phaseStartIndices.get(ElectionPhase.COMMITMENT) - 1;
            }
            while (blocks.get((int) lastBlockIndex).getPrefix()[0] != 'D' && blocks.get((int) lastBlockIndex).getPrefix()[0] != 'd' && lastBlockIndex > 0) {
                lastBlockIndex--;
            }
            Block block = blocks.get((int) lastBlockIndex);
            if (block.getPrefix()[0] == 'D') {
                return (DilutionBlock) block;
            } else {
                return null;
            }
        }
    }

    public List<KeyWithOrigin> getValidKeys(int numberOfKeys) {
        ArrayList<KeyWithOrigin> keyWithOrigins = new ArrayList<>();
        int lastBlockIndex = blocks.size() - 1;
        while (lastBlockIndex >= 0 && keyWithOrigins.size() < numberOfKeys) {
            Block block = blocks.get(lastBlockIndex);
            switch(block.getPrefix()[0]) {
                case 'D':
                    DilutionBlock dilutionBlock = (DilutionBlock) block;
                    for (byte[] newKey: dilutionBlock.getNewKeys()) {
                        if (keyWithOrigins.size() < numberOfKeys) {
                            boolean keyIsValid = true;
                            for (Long blockUseIndex : dilutionBlock.getBlockUseIndices()) {
                                Block usingBlock = getBlock(blockUseIndex);
                                if (usingBlock != null) {
                                    switch (usingBlock.getPrefix()[0]) {
                                        case 'D':
                                            DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                            for (byte[] oldKey : usingDilutionBlock.getOldKeys()) {
                                                if (ByteUtils.byteArraysAreEqual(newKey, oldKey)) {
                                                    keyIsValid = false;
                                                }
                                            }
                                            break;
                                        case 'C':
                                            CommitmentBlock usingCommitmentBlock = (CommitmentBlock) usingBlock;
                                            if (ByteUtils.byteArraysAreEqual(newKey, usingCommitmentBlock.getPublicKey())) {
                                                keyIsValid = false;
                                            }
                                            break;
                                    }
                                }
                            }
                            if (keyIsValid) {
                                keyWithOrigins.add(new KeyWithOrigin(newKey, lastBlockIndex));
                            }
                        }
                    }
                    break;
                case 'R':
                    RegistrationBlock registrationBlock = (RegistrationBlock) block;
                    boolean keyIsValid = true;
                    for (Long blockUseIndex: registrationBlock.getBlockUseIndices()) {
                        Block usingBlock = getBlock(blockUseIndex);
                        if (usingBlock != null) {
                            switch (usingBlock.getPrefix()[0]) {
                                case 'D':
                                    DilutionBlock usingDilutionBlock = (DilutionBlock) usingBlock;
                                    for (byte[] oldKey : usingDilutionBlock.getOldKeys()) {
                                        if (ByteUtils.byteArraysAreEqual(registrationBlock.getPublicKey(), oldKey)) {
                                            keyIsValid = false;
                                        }
                                    }
                                    break;
                                case 'C':
                                    CommitmentBlock usingCommitmentBlock = (CommitmentBlock) usingBlock;
                                    if (ByteUtils.byteArraysAreEqual(registrationBlock.getPublicKey(), usingCommitmentBlock.getPublicKey())) {
                                        keyIsValid = false;
                                    }
                                    break;
                            }
                        }
                    }
                    if (keyIsValid) {
                        keyWithOrigins.add(new KeyWithOrigin(registrationBlock.getPublicKey(), lastBlockIndex));
                    }
                    break;
            }
            lastBlockIndex--;
        }
        return  keyWithOrigins;
    }
}
