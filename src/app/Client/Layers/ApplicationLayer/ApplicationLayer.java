package app.Client.Layers.ApplicationLayer;

import app.Client.Datastructures.DilutionApplicationCircle;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.Block;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.DilutionBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.RegistrationBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.PrivacyLayer.Identity;
import app.Client.Layers.PrivacyLayer.PrivacyLayer;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;
import org.bouncycastle.util.encoders.Hex;

import java.util.*;

public class ApplicationLayer {
    private final PrivacyLayer privacyLayer;
    public final List<ElectionManagerRole> electionManagerRoles;
    private final List<Election> elections;
    public final Map<ElectionManagerRole, List<Election>> managedElections;
    private final String name;

    public void addElection(Election election, ElectionManagerRole electionManagerRole) {
        elections.add(election);
        if (electionManagerRole != null) {
            managedElections.get(electionManagerRole).add(election);
        }
    }

    public ApplicationLayer(String name) {
        this.name = name;
        privacyLayer = new PrivacyLayer(name, this);
        electionManagerRoles = new ArrayList<>();
        elections = new ArrayList<>();
        managedElections = new HashMap<>();
    }

    public PrivacyLayer getPrivacyLayer() {
        return privacyLayer;
    }

    public void createElectionManagerRole() {
        ElectionManagerRole electionManagerRole = privacyLayer.createElectionManagerRole();
        if (electionManagerRole != null) {
            electionManagerRoles.add(electionManagerRole);
            managedElections.put(electionManagerRole, new ArrayList<>());
        } else {
            System.out.println("Failed to add election manager role");
        }
    }

    public void createElection(int electionManagerIndex) {
        privacyLayer.createElectionAsManager(electionManagerRoles.get(electionManagerIndex));
    }

    /**
     * Generate an election ID that hasn't been used for by this ElectionManagerRole yet.
     *
     * @param electionManagerRole The ElectionManagerRole managing the Election
     * @return
     */
    public byte[] generateNewElectionId(ElectionManagerRole electionManagerRole) {
        boolean checkingId = true;
        byte[] newId = new byte[Election.ID_LENGTH];
        for (int i = 0; i < Election.ID_LENGTH; i++) {
            newId[i] = 0;
        }
        while (checkingId) {
            checkingId = false;
            for (Election election : managedElections.get(electionManagerRole)) {
                if (ByteUtils.byteArraysAreEqual(election.getId(), newId)) {
                    checkingId = true;
                    boolean incrementing = true;
                    int indexToIncrement = 0;
                    while (incrementing && indexToIncrement < Election.ID_LENGTH) {
                        incrementing = false;
                        newId[indexToIncrement]++;
                        if (newId[indexToIncrement] == 0) {
                            incrementing = true;
                            indexToIncrement++;
                        }
                    }
                }
            }
        }
        return newId;
    }

    /**
     * Registers a voter with his public key for a given election. This method may only be called by the manager of
     * the election.
     *
     * @param id A piece of data that serves to uniquely identify the voter
     * @param publicKey The public key the voter wishes to register
     * @param electionManagerIndex The local index of the ElectionManagerRole that manages the Election
     * @param electionIndex The local index of the Election that the voter registers for
     */
    public void registerVoter(String id, byte[] publicKey, int electionManagerIndex, int electionIndex) {
        String finalIdString = id;
        if (id.length() < Election.VOTER_ID_LENGTH) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(id);
            for (int i = id.length(); i < Election.VOTER_ID_LENGTH; i++) {
                stringBuilder.append(' ');
            }
            finalIdString = stringBuilder.toString();
        } else if (id.length() > Election.VOTER_ID_LENGTH) {
            finalIdString = id.substring(0, Election.VOTER_ID_LENGTH);
        }
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        Election election = managedElections.get(electionManagerRole).get(electionIndex);
        privacyLayer.registerVoter(finalIdString, publicKey, election, electionManagerRole);
    }

    /**
     * Ends the registration period for a given election. This method may only be called by the manager of the election.
     *
     * @param electionManagerIndex The local index of the ElectionManagerRole that manages the Election
     * @param electionIndex The local index of the Election of which the registration period must be ended
     */
    public void endRegistrationPeriod(int electionManagerIndex, int electionIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        Election election = managedElections.get(electionManagerRole).get(electionIndex);
        privacyLayer.endRegistrationPeriod(election, electionManagerRole);
    }

    /**
     * Increments the max depth of the signature dilution phase.
     *
     * @param electionManagerIndex The local index of the ElectionManagerRole that manages the Election
     * @param electionIndex The local index of the Election in which the max depth must be increased
     */
    public void preIncrementDilutionMaxDepth(int electionManagerIndex, int electionIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        Election election = managedElections.get(electionManagerRole).get(electionIndex);
        privacyLayer.preIncrementDilutionMaxDepth(election, electionManagerRole);
    }

    /**
     * Increments the max depth of the signature dilution phase.
     *
     * @param electionManagerIndex The local index of the ElectionManagerRole that manages the Election
     * @param electionIndex The local index of the Election in which the max depth must be increased
     */
    public void incrementDilutionMaxDepth(int electionManagerIndex, int electionIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        Election election = managedElections.get(electionManagerRole).get(electionIndex);
        privacyLayer.incrementDilutionMaxDepth(election, electionManagerRole);
    }

    public void createKeyPair() {
        privacyLayer.createIdentity();
    }

    public byte[] getPublicKey(int index) {
        return privacyLayer.getPublicKey(index);
    }

    public void applyForDilution(int index, int electionIndex) {
        privacyLayer.sendDilutionApplicationMessage(index, elections.get(electionIndex));
    }

    public void startDilutionPool(int index, int electionIndex, boolean includeSelf) {
        privacyLayer.startDilutionPool(index, elections.get(electionIndex), includeSelf);
    }

    /**
     * Ends the dilution period for a given election. This method may only be called by the manager of the election.
     *
     * @param electionManagerIndex The local index of the ElectionManagerRole that manages the Election
     * @param electionIndex The local index of the Election of which the dilution period must be ended
     */
    public void endDilutionPeriod(int electionManagerIndex, int electionIndex, List<Candidate> candidates) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        Election election = managedElections.get(electionManagerRole).get(electionIndex);
        privacyLayer.endDilutionPeriod(election, electionManagerRole, candidates);
    }

    /**
     * Ends the commitment period for a given election. This method may only be called by the manager of the election.
     *
     * @param electionManagerIndex The local index of the ElectionManagerRole that manages the Election
     * @param electionIndex The local index of the Election of which the commitment period must be ended
     */
    public void endCommitmentPeriod(int electionManagerIndex, int electionIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        Election election = managedElections.get(electionManagerRole).get(electionIndex);
        privacyLayer.endCommitmentPeriod(election, electionManagerRole);
    }

    public long commit(String candidate, int index, int electionIndex) {
        Election election = elections.get(electionIndex);
        if (election == null) {
            return -1;
        }
        return privacyLayer.commit(candidate.getBytes(), index, election);
    }

    public void connect(String ipAddress, int port) {
        privacyLayer.connect(ipAddress, port);
    }

    public void printBlockchain(int electionIndex) {
        privacyLayer.printBlockchain(elections.get(electionIndex));
    }

    public List<Block> getBlockchain(int electionIndex) {
        return privacyLayer.getBlockchain(elections.get(electionIndex));
    }

    public String getName() {
        return name;
    }

    public int getNumberOfElections() {
        return elections.size();
    }

    public int getNumberOfManagedElections() {
        return managedElections.size();
    }

    public int getNumberOfElectionsForManagerRole(int electionManagerIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        return managedElections.get(electionManagerRole).size();
    }

    public ElectionPhase getElectionPhase(int electionIndex) {
        return privacyLayer.getElectionPhase(elections.get(electionIndex));
    }

    public ElectionPhase getManagedElectionPhase(int electionManagerIndex, int electionIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        return privacyLayer.getElectionPhase(managedElections.get(electionManagerRole).get(electionIndex));
    }

    public DilutionApplicationCircle getDilutionApplications(int electionIndex) {
        return privacyLayer.getDilutionApplications(elections.get(electionIndex));
    }

    public boolean isPartOfDilutionProcess(int electionIndex) {
        return privacyLayer.isPartOfDilutionProcess(elections.get(electionIndex));
    }

    public boolean ownsDilutionProcess(int electionIndex) {
        return privacyLayer.ownsDilutionProcess(elections.get(electionIndex));
    }

    public void subscribeToElection(byte[] managerPublicKey, byte[] chainId) {
        privacyLayer.subscribeToElection(managerPublicKey, chainId);
    }

    public byte[] getManagedElectionPublicKey(int electionManagerIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        return PrivacyUtils.getPublicKeyBytesFromKeyPair(electionManagerRole.getKeyPair());
    }

    public byte[] getManagedElectionChainId(int electionManagerIndex, int electionIndex) {
        ElectionManagerRole electionManagerRole = electionManagerRoles.get(electionManagerIndex);
        return managedElections.get(electionManagerRole).get(electionIndex).getId();
    }

    public byte[] getElectionManagerPublicKey(int electionIndex) {
        return elections.get(electionIndex).getElectionManagerPublicKey();
    }

    public int getElectionManagerRoleOfElectionIndex(int electionIndex) {
        int toReturn = -1;
        Election election = elections.get(electionIndex);
        byte[] publicKey = election.getElectionManagerPublicKey();
        for (int i = 0; i < electionManagerRoles.size(); i++) {
            ElectionManagerRole electionManagerRole =  electionManagerRoles.get(i);
            if (ByteUtils.byteArraysAreEqual(publicKey, PrivacyUtils.getPublicKeyBytesFromKeyPair(electionManagerRole.getKeyPair()))) {
                toReturn = i;
            }
        }
        return toReturn;
    }

    public int getNumberOfIdentities() {
        return privacyLayer.getNumberOfIdentities();
    }

    public boolean isIdentityStillValid(int index) {
        return privacyLayer.getKeyOriginBlockIndex(index) != -1 && privacyLayer.isIdentityFinal(index);
    }

    public int getDepth(int identityIndex, int electionIndex) {
        Election election = elections.get(electionIndex);
        return privacyLayer.getIdentityDepth(identityIndex, election);
    }

    public int getCurrentIdentityIndex(int electionIndex) {
        Election election = elections.get(electionIndex);
        boolean lookingForIdentity = true;
        int i = 0;
        HashSet<Integer> unusedIdentityIndices = new HashSet<>();
        while (lookingForIdentity && i < getNumberOfIdentities()) {
            if (isIdentityStillValid(i)) {
                lookingForIdentity = false;
            } else {
                if (privacyLayer.getKeyUseBlockIndex(i) == -1) {
                    unusedIdentityIndices.add(i);
                }
                i++;
            }
        }
        if (i == getNumberOfIdentities()) {
            i = -1;
            for (Integer unusedIdentityIndex: unusedIdentityIndices) {
                Identity identity = privacyLayer.getIdentity(unusedIdentityIndex);
                List<Block> blocks = getBlockchain(electionIndex);
                for (Block block: blocks) {
                    switch (block.getPrefix()[0]) {
                        case 'R':
                            RegistrationBlock registrationBlock = (RegistrationBlock) block;
                            if (ByteUtils.byteArraysAreEqual(registrationBlock.getPublicKey(), identity.getPublicKey())) {
                                identity.setKeyOriginBlockIndex(registrationBlock.getIndex(), election);
                                i = unusedIdentityIndex;
                                System.out.println("_____ Set key origin index " + registrationBlock.getIndex());
                            }
                            break;
                        case 'D':
                            DilutionBlock dilutionBlock = (DilutionBlock) block;
                            for (byte[] newKey : dilutionBlock.getNewKeys()) {
                                if (ByteUtils.byteArraysAreEqual(newKey, identity.getPublicKey())) {
                                    identity.setKeyOriginBlockIndex(dilutionBlock.getIndex(), election);
                                    System.out.println("_____ Set key origin index " + dilutionBlock.getIndex());
                                    i = unusedIdentityIndex;
                                }
                            }
                            break;
                    }
                }
            }
        }
        return i;
    }

    public void printIdentities() {
        for (int i = 0; i < getNumberOfIdentities(); i++) {
            Identity identity = privacyLayer.getIdentity(i);
            System.out.println(" - ID orig " + identity.getKeyOriginBlockIndex() + " use " + identity.getKeyUseBlockIndex() + " - " + Hex.toHexString(identity.getPublicKey()));
        }
    }

    public List<Double> tally(List<byte[]> candidates, int electionIndex) {
        if (electionIndex < 0 || electionIndex >= elections.size()) {
            return null;
        } else {
            Election election = elections.get(electionIndex);
            return privacyLayer.tally(candidates, election);
        }
    }

    public Election getElection(byte[] electionManagerPublicKey, byte[] chainId) {
        Election election = null;
        for (Election currentElection: elections) {
            if (ByteUtils.byteArraysAreEqual(currentElection.getElectionManagerPublicKey(), electionManagerPublicKey) && ByteUtils.byteArraysAreEqual(currentElection.getId(), chainId)) {
                election = currentElection;
            }
        }
        return election;
    }

    public List<Candidate> getElectionCandidates(int electionIndex) {
        if (electionIndex < 0 || electionIndex >= elections.size()) {
            return null;
        } else {
            Election election = elections.get(electionIndex);
            return election.getCandidates();
        }
    }

    public boolean isInPreDepthPhase(int electionIndex) {
        if (electionIndex < 0 || electionIndex >= elections.size()) {
            return false;
        } else {
            Election election = elections.get(electionIndex);
            return privacyLayer.isInPreDepthPhase(election);
        }
    }

    public List<String> getLog() {
        return privacyLayer.getLog();
    }

    public void undoLastBlock(int electionIndex) {
        if (electionIndex >= 0 && electionIndex < elections.size()) {
            Election election = elections.get(electionIndex);
            privacyLayer.undoLastBlock(election);
        }
    }

    public void squeeze() {
        privacyLayer.squeeze();
    }

    public void startDiluting(int identityIndex, int electionIndex) {
        if (electionIndex >= 0 && electionIndex < elections.size()) {
            Election election = elections.get(electionIndex);
            privacyLayer.startDiluting(identityIndex, election);
        }
    }

    public void startCreatingDilutionPools(int identityIndex, int electionIndex) {
        if (electionIndex >= 0 && electionIndex < elections.size()) {
            Election election = elections.get(electionIndex);
            privacyLayer.startCreatingDilutionPools(identityIndex, election);
        }
    }

    public void exit() {
        privacyLayer.exit();
    }

    public boolean isIdentityKeyStillValid(int identityIndex, int electionIndex) {
        Election election = elections.get(electionIndex);
        return privacyLayer.isIdentityKeyStillValid(identityIndex, election);
    }
}
