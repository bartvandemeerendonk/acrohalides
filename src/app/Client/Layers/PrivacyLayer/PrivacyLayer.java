package app.Client.Layers.PrivacyLayer;

import app.Client.Datastructures.ByteMap;
import app.Client.Datastructures.DilutionApplicationCircle;
import app.Client.Layers.ApplicationLayer.ApplicationLayer;
import app.Client.Layers.ApplicationLayer.Candidate;
import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.ApplicationLayer.ElectionManagerRole;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.Block;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.DilutionBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.RegistrationBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Layers.BlockchainLayer.Vote;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;

import java.security.*;
import java.util.*;

public class PrivacyLayer {

    private final BlockchainLayer blockchainLayer;
    private final ApplicationLayer applicationLayer;
    private final IdentityStore identityStore;


    public PrivacyLayer(String name, ApplicationLayer applicationLayer) {
        this.applicationLayer = applicationLayer;
        identityStore = new IdentityStore();
        blockchainLayer = new BlockchainLayer(name, this);
    }

    public BlockchainLayer getBlockchainLayer() {
        return blockchainLayer;
    }

    public ApplicationLayer getApplicationLayer() {
        return applicationLayer;
    }

    public void connect(String ipAddress, int port) {
        blockchainLayer.connect(ipAddress, port);
    }

    public ElectionManagerRole createElectionManagerRole() {
        try {
            KeyPair keyPair = PrivacyUtils.generateKeyPair();
            return new ElectionManagerRole(keyPair);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("ElectionManagerRole couldn't be created due to a NoSuchAlgorithmException.");
            return null;
        } catch (InvalidAlgorithmParameterException exception) {
            System.err.println("ElectionManagerRole couldn't be created due to a InvalidAlgorithmParameterException.");
            return null;
        }
    }

    public void createElectionFromPublicKey(byte[] id, byte[] publicKey, ElectionManagerRole electionManagerRole) {
        applicationLayer.addElection(new Election(publicKey, id), electionManagerRole);
    }

    public void createElectionAsManager(ElectionManagerRole electionManagerRole) {
        try {
            KeyPair keyPair = electionManagerRole.getKeyPair();
            Signature signature = PrivacyUtils.createSignatureForSigning(keyPair);
            byte[] publicKey = PrivacyUtils.getPublicKeyBytesFromKeyPair(keyPair);
            byte[] id = applicationLayer.generateNewElectionId(electionManagerRole);
            createElectionFromPublicKey(id, publicKey, electionManagerRole);
            blockchainLayer.createInitializationBlock(id, signature, publicKey);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Election couldn't be created due to a NoSuchAlgorithmException.");
        } catch (InvalidKeyException exception) {
            System.err.println("Election couldn't be created due to a InvalidKeyException.");
        }
    }

    /**
     * Registers a voter with his public key for a given election. This method may only be called by the manager of
     * the election.
     *
     * @param id A piece of data that serves to uniquely identify the voter
     * @param voterPublicKey The public key the voter wishes to register
     * @param election The Election that the voter registers for
     * @param electionManagerRole The ElectionManagerRole that manages the Election
     */
    public void registerVoter(String id, byte[] voterPublicKey, Election election, ElectionManagerRole electionManagerRole) {
        try {
            byte[] electionMangerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            byte[] voterId = id.getBytes();
            Signature signature = PrivacyUtils.createSignatureForSigning(electionManagerRole.getKeyPair());
            blockchainLayer.createRegistrationBlock(electionMangerPublicKey, electionId, voterId, signature, voterPublicKey);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Voter couldn't be registered due to a NoSuchAlgorithmException");
        } catch (InvalidKeyException exception) {
            System.err.println("Voter couldn't be registered due to an InvalidKeyException");
        }
    }

    /**
     * Ends the registration period for a given election. This method may only be called by the manager of the election.
     *
     * @param election The Election of which the registration period must be ended
     * @param electionManagerRole The ElectionManagerRole that manages the Election
     */
    public void endRegistrationPeriod(Election election, ElectionManagerRole electionManagerRole) {
        try {
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            Signature signature = PrivacyUtils.createSignatureForSigning(electionManagerRole.getKeyPair());
            blockchainLayer.createDilutionStartBlock(electionManagerPublicKey, electionId, signature);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Registration period couldn't be ended due to a NoSuchAlgorithmException");
        } catch (InvalidKeyException exception) {
            System.err.println("Registration period couldn't be ended due to an InvalidKeyException");
        }
    }

    /**
     * Increments the max depth of the signature dilution phase.
     *
     * @param election The Election in which the max depth must be incremented
     * @param electionManagerRole The ElectionManagerRole that manages the Election
     */
    public void preIncrementDilutionMaxDepth(Election election, ElectionManagerRole electionManagerRole) {
        try {
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            Signature signature = PrivacyUtils.createSignatureForSigning(electionManagerRole.getKeyPair());
            blockchainLayer.createPreDepthBlock(electionManagerPublicKey, electionId, signature);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Dilution depth couldn't be preincremented due to a NoSuchAlgorithmException");
        } catch (InvalidKeyException exception) {
            System.err.println("Dilution depth couldn't be preincremented due to an InvalidKeyException");
        }
    }

    /**
     * Increments the max depth of the signature dilution phase.
     *
     * @param election The Election in which the max depth must be incremented
     * @param electionManagerRole The ElectionManagerRole that manages the Election
     */
    public void incrementDilutionMaxDepth(Election election, ElectionManagerRole electionManagerRole) {
        try {
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            Signature signature = PrivacyUtils.createSignatureForSigning(electionManagerRole.getKeyPair());
            blockchainLayer.createDepthBlock(electionManagerPublicKey, electionId, signature);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Dilution depth couldn't be incremented due to a NoSuchAlgorithmException");
        } catch (InvalidKeyException exception) {
            System.err.println("Dilution depth couldn't be incremented due to an InvalidKeyException");
        }
    }

    /**
     * Ends the dilution period for a given election. This method may only be called by the manager of the election.
     *
     * @param election The Election of which the dilution period must be ended
     * @param electionManagerRole The ElectionManagerRole that manages the Election
     */
    public void endDilutionPeriod(Election election, ElectionManagerRole electionManagerRole, List<Candidate> candidates) {
        try {
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            Signature signature = PrivacyUtils.createSignatureForSigning(electionManagerRole.getKeyPair());
            HashMap<Candidate, Integer> candidateIndices = new HashMap<>();
            for (int i = 0; i < candidates.size(); i++) {
                candidateIndices.put(candidates.get(i), i);
            }
            ArrayList<byte[]> candidateStringParts = new ArrayList<>();
            candidateStringParts.add(new byte[]{(byte) candidates.get(0).getTag().length()});
            for (int i = 0; i < candidates.size(); i++) {
                Candidate candidate = candidates.get(i);
                candidateStringParts.add(candidate.getTag().getBytes());
                candidateStringParts.add(ByteUtils.encodeWithLengthByte(candidate.getDescription().getBytes()));
                if (candidate.isElectable()) {
                    candidateStringParts.add(new byte[]{-128});
                } else {
                    candidateStringParts.add(new byte[]{0});
                }
                Candidate parent = candidate.getParent();
                int parentIndex = i;
                if (parent != null) {
                    parentIndex = candidateIndices.get(parent);
                }
                candidateStringParts.add(ByteUtils.intToByteArray(parentIndex));
            }
            byte[] candidateString = ByteUtils.concatenateByteArrays(candidateStringParts);
            blockchainLayer.createDilutionEndBlock(electionManagerPublicKey, electionId, signature, candidateString);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Dilution period couldn't be ended due to a NoSuchAlgorithmException");
        } catch (InvalidKeyException exception) {
            System.err.println("Dilution period couldn't be ended due to an InvalidKeyException");
        }
    }

    public void setCandidates(byte[] electionManagerPublicKey, byte[] chainId, byte[] candidateString) {
        Election election = applicationLayer.getElection(electionManagerPublicKey, chainId);
        if (election != null) {
            int offset = 1;
            int tagLength = candidateString[0];
            if (tagLength < 0) {
                tagLength += 256;
            }
            ArrayList<Candidate> candidates = new ArrayList<>();
            ArrayList<Integer> parentIndices = new ArrayList<>();
            while (offset < candidateString.length) {
                byte[] tag = ByteUtils.readByteSubstring(candidateString, offset, tagLength);
                offset += tagLength;
                if (offset < candidateString.length) {
                    byte[] description = ByteUtils.readByteSubstringWithLengthEncoded(candidateString, offset);
                    offset += 1 + description.length;
                    boolean electable = candidateString[offset] == -128;
                    offset += 1;
                    int parentIndex = ByteUtils.intFromByteArray(ByteUtils.readByteSubstring(candidateString, offset, 4));
                    parentIndices.add(parentIndex);
                    offset += 4;
                    Candidate candidate = new Candidate(electable, new String(tag), new String(description));
                    candidates.add(candidate);
                }
            }
            for (int i = 0; i < parentIndices.size(); i++) {
                int parentIndex = parentIndices.get(i);
                if (i != parentIndex) {
                    candidates.get(i).setParent(candidates.get(parentIndex));
                }
            }
            election.setCandidates(candidates);
        }
    }

    public void addDisenfranchisedVoter(byte[] electionManagerPublicKey, byte[] chainId, byte[] voterId) {
        Election election = applicationLayer.getElection(electionManagerPublicKey, chainId);
        if (election != null) {
            election.addDisenfranchisedVoter(voterId);
        }
    }

    /**
     * Ends the commitment period for a given election. This method may only be called by the manager of the election.
     *
     * @param election The Election of which the commitment period must be ended
     * @param electionManagerRole The ElectionManagerRole that manages the Election
     */
    public void endCommitmentPeriod(Election election, ElectionManagerRole electionManagerRole) {
        try {
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            Signature signature = PrivacyUtils.createSignatureForSigning(electionManagerRole.getKeyPair());
            blockchainLayer.createCommitmentEndBlock(electionManagerPublicKey, electionId, signature);
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Commitment period couldn't be ended due to a NoSuchAlgorithmException");
        } catch (InvalidKeyException exception) {
            System.err.println("Commitment period couldn't be ended due to an InvalidKeyException");
        }
    }

    /**
     * Generate a new asymmetric keypair for digital signatures to be used in signature dilution or voting.
     */
    public void createIdentity() {
        try {
            KeyPair keyPair = PrivacyUtils.generateKeyPair();
            identityStore.addIdentity(new Identity(keyPair));
        } catch (NoSuchAlgorithmException exception) {
            System.err.println("Identity couldn't be created due to a NoSuchAlgorithmException.");
        } catch (InvalidAlgorithmParameterException exception) {
            System.err.println("Identity couldn't be created due to a InvalidAlgorithmParameterException.");
        }
    }

    /**
     * Set the index of the origin block of a public key in its Identity object
     *
     * @param publicKey A byte string representing the public key that originates in the block
     * @param keyOriginBlockIndex An integer representing the index of the public key in the blockchain
     */
    public void setKeyOriginBlockIndex(byte[] publicKey, long keyOriginBlockIndex, byte[] electionManagerPublicKey, byte[] chainId) {
        Identity identity = getIdentityForPublicKey(publicKey);
        Election election = applicationLayer.getElection(electionManagerPublicKey, chainId);
        if (identity != null) {
            identity.setKeyOriginBlockIndex(keyOriginBlockIndex, election);
        }
    }

    /**
     * Set the RegistrationBlock of the Identity belonging to a public key, which was registered in the block.
     *
     * @param publicKey A byte string representing the public key that was registered in the block
     * @param registrationBlock The RegistrationBlock to be set
     */
    public void setRegistrationBlock(byte[] publicKey, RegistrationBlock registrationBlock) {
        Identity identity = getIdentityForPublicKey(publicKey);
        if (identity != null) {
            identity.setRegistrationBlock(registrationBlock);
        }
    }

    /**
     * Set the index of the use block of a public key in its Identity object
     *
     * @param publicKey A byte string representing the public key that is used in the block
     * @param keyUseBlockIndex An integer representing the index of the public key in the blockchain
     */
    public void setKeyUseBlockIndex(byte[] publicKey, long keyUseBlockIndex) {
        Identity identity = getIdentityForPublicKey(publicKey);
        if (identity != null) {
            identity.setKeyUseBlockIndex(keyUseBlockIndex);
        }
    }

    /**
     * Get the index of the use block of a public key in its Identity object
     *
     * @param publicKey A byte string representing the public key that is used in the block
     * @return
     */
    public long getKeyUseBlockIndex(byte[] publicKey) {
        Identity identity = getIdentityForPublicKey(publicKey);
        if (identity == null) {
            return -1;
        } else {
            return identity.getKeyUseBlockIndex();
        }
    }

    /**
     * Get the index of the use block of a public key in its Identity object
     *
     * @param identityIndex The index of the Identity
     * @return
     */
    public long getKeyUseBlockIndex(int identityIndex) {
        Identity identity = getIdentity(identityIndex);
        if (identity == null) {
            return -1;
        } else {
            long keyUseBlockIndex = identity.getKeyUseBlockIndex();
            Election election = identity.getElection();
            if (election == null) {
                return -1;
            }
            if (!blockchainLayer.verifyKeyUse(keyUseBlockIndex, identity.getPublicKey(), election.getElectionManagerPublicKey(), election.getId())) {
                keyUseBlockIndex = 0;
                long blockchainSize = blockchainLayer.getBlockchainSize(election.getElectionManagerPublicKey(), election.getId());
                boolean stillLooking = true;
                while (keyUseBlockIndex < blockchainSize && stillLooking) {
                    if (blockchainLayer.verifyKeyUse(keyUseBlockIndex, identity.getPublicKey(), election.getElectionManagerPublicKey(), election.getId())) {
                        stillLooking = false;
                    } else {
                        keyUseBlockIndex++;
                    }
                }
                if (stillLooking) {
                    keyUseBlockIndex = -1;
                }
                identity.setKeyUseBlockIndex(keyUseBlockIndex);
            }
            return keyUseBlockIndex;
        }
    }

    public boolean isIdentityFinal(int identityIndex) {
        Identity identity = getIdentity(identityIndex);
        if (identity == null) {
            return false;
        } else {
            long keyUseBlockIndex = identity.getKeyUseBlockIndex();
            Election election = identity.getElection();
            if (election == null) {
                return false;
            }
            if (keyUseBlockIndex == -1) {
                return true;
            }
            return blockchainLayer.isBlockCommitmentBlock(keyUseBlockIndex, election.getElectionManagerPublicKey(), election.getId());
        }
    }

    /**
     * Get the index of the origin block of a public key in its Identity object
     *
     * @param identityIndex The index of the Identity
     * @return
     */
    public long getKeyOriginBlockIndex(int identityIndex) {
        Identity identity = getIdentity(identityIndex);
        if (identity == null) {
            return -1;
        } else {
            long keyOriginBlockIndex = identity.getKeyOriginBlockIndex();
            Election election = identity.getElection();
            if (election == null) {
                return -1;
            }
            if (!blockchainLayer.verifyKeyOrigin(keyOriginBlockIndex, identity.getPublicKey(), election.getElectionManagerPublicKey(), election.getId())) {
                keyOriginBlockIndex = 0;
                long blockchainSize = blockchainLayer.getBlockchainSize(election.getElectionManagerPublicKey(), election.getId());
                boolean stillLooking = true;
                while (keyOriginBlockIndex < blockchainSize && stillLooking) {
                    if (blockchainLayer.verifyKeyOrigin(keyOriginBlockIndex, identity.getPublicKey(), election.getElectionManagerPublicKey(), election.getId())) {
                        stillLooking = false;
                    } else {
                        keyOriginBlockIndex++;
                    }
                }
                if (stillLooking) {
                    keyOriginBlockIndex = -1;
                }
                identity.setKeyOriginBlockIndex(keyOriginBlockIndex, election);
            }
            return keyOriginBlockIndex;
        }
    }

    /**
     * Get the number of asymmetric keypairs at the user's disposal for signature dilution or voting.
     */
    public int getNumberOfIdentities() {
        return identityStore.getNumberOfIdentities();
    }

    /**
     * Get the asymmetric keypair at the user's disposal for signature dilution or voting, for a given index.
     * @param index The index of the keypair
     * @return An Identity containing the keypair and information about registration and use of the keys
     */
    public Identity getIdentity(int index) {
        return identityStore.getIdentity(index);
    }

    /**
     * Check each Identity and invalidate key origin block indices and key use block indices after a certain index in
     * the blockchain. To be used when forking at that index.
     *
     * @param blockIndex Index in the blockchain after which indices should be invalidated
     */
    public void invalidateKeyBlockIndicesAfterIndex(long blockIndex, byte[] electionManagerPublicKey, byte[] chainId) {
        for (Identity identity: identityStore.getIdentities()) {
            Election election = identity.getElection();
            if (election == null || (ByteUtils.byteArraysAreEqual(election.getElectionManagerPublicKey(), electionManagerPublicKey) && ByteUtils.byteArraysAreEqual(election.getId(), chainId))) {
                if (identity.getKeyOriginBlockIndex() >= blockIndex) {
                    identity.setKeyOriginBlockIndex(-1, election);
                }
                if (identity.getKeyUseBlockIndex() >= blockIndex) {
                    identity.setKeyUseBlockIndex(-1);
                }
            }
        }
        Election election = applicationLayer.getElection(electionManagerPublicKey, chainId);
        if (election != null) {
            List<Block> blocksAfterIndex = blockchainLayer.getBlockchainFromIndex(electionManagerPublicKey, chainId, blockIndex);
            for (Block block : blocksAfterIndex) {
                switch (block.getPrefix()[0]) {
                    case 'R':
                        RegistrationBlock registrationBlock = (RegistrationBlock) block;
                        Identity identity = identityStore.getIdentityForPublicKey(registrationBlock.getPublicKey());
                        if (identity != null) {
                            identity.setKeyOriginBlockIndex(registrationBlock.getIndex(), election);
                        }
                        break;
                    case 'D':
                        DilutionBlock dilutionBlock = (DilutionBlock) block;
                        for (byte[] oldKey : dilutionBlock.getOldKeys()) {
                            Identity identity1 = identityStore.getIdentityForPublicKey(oldKey);
                            if (identity1 != null) {
                                identity1.setKeyUseBlockIndex(dilutionBlock.getIndex());
                            }
                        }
                        for (byte[] newKey : dilutionBlock.getNewKeys()) {
                            Identity identity1 = identityStore.getIdentityForPublicKey(newKey);
                            if (identity1 != null) {
                                identity1.setKeyOriginBlockIndex(dilutionBlock.getIndex(), election);
                            }
                        }
                        break;
                }
            }
        }
    }

    /**
     * Get the public key for an asymmetric keypair at the user's disposal for signature dilution or voting, for a given
     * index.
     * @param index The index of the keypair
     * @return A bytestring representing the public key
     */
    public byte[] getPublicKey(int index) {
        Identity identity = identityStore.getIdentity(index);
        if (identity == null) {
            return null;
        } else {
            return PrivacyUtils.getPublicKeyBytesFromKeyPair(identity.getKeyPair());
        }
    }

    /**
     * Send a dilution application message for an asymmetric keypair at the user's disposal for signature dilution or
     * voting, for a given index
     * @param index The index of the keypair
     */
    public void sendDilutionApplicationMessage(int index, Election election) {
        Identity identity = identityStore.getIdentity(index);
        if (identity != null) {
            KeyPair keyPair = identity.getKeyPair();
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            try {
                blockchainLayer.sendDilutionApplication(electionManagerPublicKey, electionId, PrivacyUtils.getPublicKeyBytesFromKeyPair(keyPair), identity.getKeyOriginBlockIndex(), PrivacyUtils.createSignatureForSigning(keyPair), identity);
            } catch (InvalidKeyException exception) {
                System.out.println("Couldn't send a dilution application message in the privacy layer due to an InvalidKeyException");
            } catch (NoSuchAlgorithmException exception) {
                System.out.println("Couldn't send a dilution application message in the privacy layer due to a NoSuchAlgorithmException");
            }
        }
    }

    /**
     * Start a dilution pool with an asymmetric keypair at the user's disposal for signature dilution or voting, for a
     * given index
     * @param index The index of the keypair
     */
    public void startDilutionPool(int index, Election election, boolean includeSelf) {
        Identity identity = identityStore.getIdentity(index);
        if (identity != null) {
            KeyPair keyPair = identity.getKeyPair();
            long keyOriginBlockIndex = identity.getKeyOriginBlockIndex();
            byte[] electionManagerPublicKey = election.getElectionManagerPublicKey();
            byte[] electionId = election.getId();
            try {
                Signature signature = PrivacyUtils.createSignatureForSigning(keyPair);
                blockchainLayer.attemptToStartDilutionPool(signature, PrivacyUtils.getPublicKeyBytesFromKeyPair(keyPair), keyOriginBlockIndex, identity, includeSelf, electionManagerPublicKey, electionId);
            } catch (NoSuchAlgorithmException exception) {
                System.out.println("Coudln't start dilution pool in privacy layer due to NoSuchAlgorithmException");
            } catch (InvalidKeyException exception) {
                System.out.println("Coudln't start dilution pool in privacy layer due to InvalidKeyException");
            }
        } else {
            System.out.println("Couldn't start a dilution pool with a nonexistent identity");
        }
    }

    /**
     * Retrieve the Identity that contains a given public key, if it is owned by this user
     * @param publicKey A byte string representing the public key to search for
     * @return An Identity that contains the public key, or null if this user doesn't have an Identity with the given
     * public key
     */
    private Identity getIdentityForPublicKey(byte[] publicKey) {
        return identityStore.getIdentityForPublicKey(publicKey);
    }

    /**
     * Get a Signature for signing using the private key that corresponds to publicKey.
     *
     * @param publicKey The public key that belongs to the private key used for signing
     * @return A Signature for signing
     */
    public Signature getSignatureForPublicKey(byte[] publicKey) {
        Identity identity = getIdentityForPublicKey(publicKey);
        if (identity == null) {
            return null;
        } else {
            byte[] identityPublicKey = identity.getPublicKey();
            Signature signature = null;
            if (ByteUtils.byteArraysAreEqual(publicKey, identityPublicKey)) {
                try {
                    signature = PrivacyUtils.createSignatureForSigning(identity.getKeyPair());
                } catch (InvalidKeyException exception) {
                    System.out.println("Couldn't retrieve signature due to InvalidKeyException");
                } catch (NoSuchAlgorithmException exception) {
                    System.out.println("Couldn't retrieve signature due to NoSuchAlgorithmException");
                }
            }
            return  signature;

        }
    }

    /**
     * Print the Blockchain belonging to an Election.
     *
     * @param election
     */
    public void printBlockchain(Election election) {
        blockchainLayer.printBlockchain(election.getElectionManagerPublicKey(), election.getId());
    }

    public List<Block> getBlockchain(Election election) {
        return blockchainLayer.getBlockchain(election.getElectionManagerPublicKey(), election.getId());
    }

    public ElectionPhase getElectionPhase(Election election) {
        return blockchainLayer.getElectionPhase(election.getElectionManagerPublicKey(), election.getId());
    }

    public DilutionApplicationCircle getDilutionApplications(Election election) {
        return blockchainLayer.getDilutionApplications(election.getElectionManagerPublicKey(), election.getId());
    }

    public boolean isPartOfDilutionProcess(Election election) {
        return blockchainLayer.isPartOfDilutionProcess(election.getElectionManagerPublicKey(), election.getId());
    }

    public boolean ownsDilutionProcess(Election election) {
        return blockchainLayer.ownsDilutionProcess(election.getElectionManagerPublicKey(), election.getId());
    }

    public void checkRegistrations(byte[] electionManagerPublicKey, byte[] chainId, long forkIndex) {
        Election election = applicationLayer.getElection(electionManagerPublicKey, chainId);
        for (Identity identity: identityStore.getIdentities()) {
            if (identity.getRegistrationBlock() != null && identity.getKeyOriginBlockIndex() >= forkIndex) {
                RegistrationBlock registrationBlock = blockchainLayer.getRegistrationBlock(electionManagerPublicKey, chainId, identity.getPublicKey(), forkIndex);
                if (registrationBlock == null) {
                    blockchainLayer.sendDisenfranchisementMessage(electionManagerPublicKey, chainId, identity.getRegistrationBlock());
                } else {
                    identity.setKeyOriginBlockIndex(registrationBlock.getIndex(), election);
                    identity.setRegistrationBlock(registrationBlock);
                }
            }
        }
    }

    public void subscribeToElection(byte[] electionManagerPublicKey, byte[] chainId) {
        blockchainLayer.subscribeToElection(electionManagerPublicKey, chainId);
    }

    public void sendVoteMessages(byte[] electionManagerPublicKey, byte[] chainId) {
        Set<Identity> identitiesForElection = identityStore.getIdentitiesForElection(electionManagerPublicKey, chainId);
        if (identitiesForElection != null) {
            for (Identity identity: identitiesForElection) {
                long commitmentBlockIndex = identity.getCommitBlockIndex();
                if (commitmentBlockIndex != -1) {
                    blockchainLayer.sendVoteMessage(electionManagerPublicKey, chainId, identity.getVote(), identity.getVoteSalt(), commitmentBlockIndex);
                }
            }
        }
    }

    /**
     * During the Commitment phase of an Election, commit the vote as a voter.
     *
     * @param voteContent The content of the vote that will be committed.
     * @param index The index of the Identity used to vote.
     * @param election The Election to vote in.
     */
    public long commit(byte[] voteContent, int index, Election election) {
        Identity identity = identityStore.getIdentity(index);
        if (identity != null) {
            try {
                Signature signature = PrivacyUtils.createSignatureForSigning(identity.getKeyPair());
                byte[] salt = new byte[16];
                for (int i = 0; i < 16; i++) {
                    salt[i] = 0;
                }
                byte[] hash = PrivacyUtils.hashVote(voteContent, salt);
                long commitBlockIndex = blockchainLayer.createCommitmentBlock(election.getElectionManagerPublicKey(), election.getId(), signature, hash, identity.getPublicKey(), identity.getKeyOriginBlockIndex());
                if (commitBlockIndex != -1) {
                    identity.setVote(voteContent);
                    identity.setVoteSalt(salt);
                    identity.setCommitBlockIndex(commitBlockIndex);
                    identityStore.addIdentityToElection(election, identity);
                    return commitBlockIndex;
                }
            } catch (InvalidKeyException exception) {
                System.out.println("Invalid key exception in commit");
            } catch (NoSuchAlgorithmException exception) {
                System.out.println("No such algorithm exception in commit");
            }
        }
        return -1;
    }

    public long commit2(byte[] voteContent, int index, Election election) {
        Identity identity = identityStore.getIdentity(index);
        if (identity != null) {
            try {
                Signature signature = PrivacyUtils.createSignatureForSigning(identity.getKeyPair());
                byte[] salt = new byte[16];
                for (int i = 0; i < 16; i++) {
                    salt[i] = 0;
                }
                byte[] hash = PrivacyUtils.hashVote(voteContent, salt);
                long commitBlockIndex = blockchainLayer.createCommitmentBlock(election.getElectionManagerPublicKey(), election.getId(), signature, hash, identity.getPublicKey(), identity.getKeyOriginBlockIndex());
                System.out.println("commitBlockIndex == " + commitBlockIndex);
                if (commitBlockIndex != -1) {
                    identity.setVote(voteContent);
                    identity.setVoteSalt(salt);
                    identity.setCommitBlockIndex(commitBlockIndex);
                    identityStore.addIdentityToElection(election, identity);
                    return commitBlockIndex;
                }
            } catch (InvalidKeyException exception) {
                System.out.println("Invalid key exception in commit");
            } catch (NoSuchAlgorithmException exception) {
                System.out.println("No such algorithm exception in commit");
            }
        } else {
            System.out.println("identity == null");
        }
        return -1;
    }

    /**
     * During the Voting phase of an Election, capture the vote as a voter.
     *
     * @param voteContent The content of the vote that was committed earlier.
     * @param commitmentBlockIndex The index of the CommitmentBlock in which voteContent was committed.
     * @param publicKey The public key of the voter.
     * @param voteMessage The vote message that was broadcast.
     */
    public void captureVote(byte[] voteContent, long commitmentBlockIndex, byte[] publicKey, byte[] voteMessage, byte[] electionManagerPublicKey, byte[] chainId) {
        Election election = applicationLayer.getElection(electionManagerPublicKey, chainId);
        ByteMap<Vote> votesPerKey = identityStore.getVotesPerForElection(election);
        boolean canCaptureVote = true;
        Vote olderVote = votesPerKey.get(publicKey);
        if (olderVote != null) {
            if (olderVote.getCommitmentBlockIndex() >= commitmentBlockIndex) {
                canCaptureVote = false;
            }
        }
        if (canCaptureVote) {
            votesPerKey.put(publicKey, new Vote(voteContent, commitmentBlockIndex, voteMessage));
        }
    }

    /**
     * Tally the votes received for a collection of candidates.
     *
     * @param candidates The candidates to tally for
     * @param election The Election to tally for
     * @return A list of tallies for each candidate in candidates
     */
    public List<Double> tally(List<byte[]> candidates, Election election) {
        ArrayList<Double> toReturn = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            toReturn.add(0D);
        }
        ByteMap<Vote> votesPerKey = identityStore.getVotesPerForElection(election);
        if (votesPerKey != null) {
            for (Vote vote : votesPerKey.values()) {
                for (int i = 0; i < candidates.size(); i++) {
                    byte[] candidate = candidates.get(i);
                    if (ByteUtils.byteArraysAreEqual(vote.getVoteContent(), candidate)) {
                        toReturn.set(i, toReturn.get(i) + 1);
                    }
                }
            }
            for (int i = 0; i < candidates.size(); i++) {
                toReturn.set(i, toReturn.get(i) / votesPerKey.size());
            }
        }
        return toReturn;
    }

    public boolean isInPreDepthPhase(Election election) {
        return blockchainLayer.isInPredepthPhase(election.getElectionManagerPublicKey(), election.getId());
    }

    public List<String> getLog() {
        return blockchainLayer.getLog();
    }

    public void undoLastBlock(Election election) {
        blockchainLayer.undoLastBlock(election.getElectionManagerPublicKey(), election.getId());
    }

    public void squeeze() {
        blockchainLayer.squeeze();
    }

    public int getIdentityDepth(int index, Election election) {
        long blockIndex = getKeyOriginBlockIndex(index);
        return blockchainLayer.getBlockDilutionDepth(blockIndex, election.getElectionManagerPublicKey(), election.getId());
    }

    public void startDiluting(int index, Election election) {
        Identity identityToDilute = getIdentity(index);
        if (identityToDilute != null) {
            HashMap<Identity, Boolean> electionDilutingIdentities = identityStore.getDilutingIdentitiesForElection(election);
//            if (!electionDilutingIdentities.containsKey(identityToDilute)) {
            if (!blockchainLayer.isDiluting(election.getElectionManagerPublicKey(), election.getId(), identityToDilute.getPublicKey())) {
                try {
                    if (blockchainLayer.sendDilutionApplication(election.getElectionManagerPublicKey(), election.getId(), identityToDilute.getPublicKey(), identityToDilute.getKeyOriginBlockIndex(), PrivacyUtils.createSignatureForSigning(identityToDilute.getKeyPair()), identityToDilute)) {
                        electionDilutingIdentities.put(identityToDilute, false);
                    }
                } catch (InvalidKeyException | NoSuchAlgorithmException exception) {

                }
            }
        }
    }

    public void startCreatingDilutionPools(int index, Election election) {
        Identity identityToDilute = getIdentity(index);
        if (identityToDilute != null) {
            HashMap<Identity, Boolean> electionDilutingIdentities = identityStore.getDilutingIdentitiesForElection(election);
//            if (!electionDilutingIdentities.containsKey(identityToDilute)) {
            if (!blockchainLayer.isDiluting(election.getElectionManagerPublicKey(), election.getId(), identityToDilute.getPublicKey())) {
                try {
                    if (blockchainLayer.attemptToStartDilutionPool(PrivacyUtils.createSignatureForSigning(identityToDilute.getKeyPair()), identityToDilute.getPublicKey(), identityToDilute.getKeyOriginBlockIndex(), identityToDilute, true, election.getElectionManagerPublicKey(), election.getId())) {
                        electionDilutingIdentities.put(identityToDilute, true);
                    }
                } catch (InvalidKeyException | NoSuchAlgorithmException exception) {

                }
            }
        }
    }

    public void exit() {
        blockchainLayer.exit();
    }

    public boolean isIdentityKeyStillValid(int identityIndex, Election election) {
        Identity identity = identityStore.getIdentity(identityIndex);
        if (identity == null) {
            return false;
        }
        Blockchain blockchain = blockchainLayer.getBlockchainByIdAndKey(election.getId(), election.getElectionManagerPublicKey());
        return blockchain.oldKeyIsStillValid2(identity.getPublicKey(), identity.getKeyOriginBlockIndex());
    }
}
