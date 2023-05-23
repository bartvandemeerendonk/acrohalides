package app.Client.Layers.BlockchainLayer;

import app.Client.Datastructures.*;
import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.BlockchainLayer.Blockchain.Blockchain;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.*;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.DilutionStartBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.NetworkLayer.NetworkLayer;
import app.Client.Layers.PrivacyLayer.PrivacyLayer;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.PrivacyLayer.Identity;
import app.Client.Utils.TimeUtils;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.security.*;
import java.security.spec.*;
import java.util.*;

public class BlockchainLayer {
    private static final byte INITIALIZATION_BLOCK_FIRST_CHAR = 'I';
    private static final byte REGISTRATION_BLOCK_FIRST_CHAR = 'R';
    private static final byte DILUTION_START_BLOCK_FIRST_CHAR = 'd';
    private static final byte DILUTION_END_BLOCK_FIRST_CHAR = 'e';
    private static final byte DILUTION_APPLICATION_FIRST_CHAR = 'A';
    private static final byte INVITE_FIRST_CHAR = 'i';
    private static final byte RESPONSE_FIRST_CHAR = 'r';
    private static final byte POOL_MESSAGE_FIRST_CHAR = 'P';
    private static final byte NEW_KEY_MESSAGE_FIRST_CHAR = 'N';
    private static final byte UNVALIDATED_DILUTION_BLOCK_FIRST_CHAR = 'U';
    private static final byte SIGNATURE_MESSAGE_FIRST_CHAR = 'S';
    private static final byte POOL_RECEIPT_FIRST_CHAR = '4';
    private static final byte BLAME_MESSAGE_FIRST_CHAR = 'B';
    private static final byte DISENFRANCHISEMENT_MESSAGE_FIRST_CHAR = '2';
    private static final byte CHAIN_REQUEST_FIRST_CHAR = 'c';
    private static final byte DILUTION_BLOCK_FIRST_CHAR = 'D';
    private static final byte DEPTH_BLOCK_FIRST_CHAR = '0';
    private static final byte PRE_DEPTH_BLOCK_FIRST_CHAR = '1';
    private static final byte COMMITMENT_BLOCK_FIRST_CHAR = 'C';
    private static final byte COMMITMENT_END_BLOCK_FIRST_CHAR = '3';
    private static final byte VOTE_MESSAGE_FIRST_CHAR = 'v';
    private static final byte[] DILUTION_APPLICATION_PREFIX = "Application - ".getBytes();
    private static final byte[] INVITE_PREFIX = "invite - ".getBytes();
    private static final byte[] RESPONSE_PREFIX = "response - ".getBytes();
    private static final byte[] POOL_MESSAGE_PREFIX = "Pool message - ".getBytes();
    private static final byte[] NEW_KEY_MESSAGE_PREFIX = "New key message - ".getBytes();
    private static final byte[] UNVALIDATED_DILUTION_BLOCK_PREFIX = "Unvalidated - ".getBytes();
    private static final byte[] SIGNATURE_MESSAGE_PREFIX = "Signature - ".getBytes();
    private static final byte[] BLOCK_REQUEST_PREFIX = "block - ".getBytes();
    private static final byte[] REQUESTED_BLOCK_PREFIX = "answer - ".getBytes();
    private static final byte[] BLAME_MESSAGE_PREFIX = "Blame - ".getBytes();
    private static final byte[] POOL_RECEIPT_PREFIX = "4 Pool acknowledgement - ".getBytes();
    private static final byte[] DISENFRANCHISEMENT_MESSAGE_PREFIX = "2 disenfranchised - ".getBytes();
    private static final byte[] CHAIN_REQUEST_PREFIX = "chain - ".getBytes();
    private static final byte[] VOTE_MESSAGE_PREFIX = "vote - ".getBytes();
    public static final int POOL_IDENTIFIER_SIZE = 8;
    private static final int HASH_CIRCLE_SIZE = 10;
    private static final int MAX_NUMBER_BLAMED = 10;
    private static final int MAX_NUMBER_OF_BACKGROUND_DILUTION_PROCESSES = 100;
    private static final int MAX_NUMBER_OF_HEADLESS_CHAINS = 100;
    private static long DILUTION_PROCESS_TIMEOUT = 10000;
    private static int MAXIMUM_SWITCH_COUNTER_FROM_POOL = 10;
    private static int MAXIMUM_SWITCH_COUNTER_FROM_APPLICATION = 10;
    private final NetworkLayer networkLayer;
    private final PrivacyLayer privacyLayer;
    private final HashSet<Blockchain> blockchains;
    private final HashMap<Blockchain, BlockchainCircle> headlessChains;
    private final RegistrationBlockFactory registrationBlockFactory;
    private final InitializationBlockFactory initializationBlockFactory;
    private final DilutionStartBlockFactory dilutionStartBlockFactory;
    public final DilutionBlockFactory dilutionBlockFactory;
    private final DepthBlockFactory depthBlockFactory;
    private final PreDepthBlockFactory preDepthBlockFactory;
    private final DilutionEndBlockFactory dilutionEndBlockFactory;
    private final CommitmentBlockFactory commitmentBlockFactory;
    private final CommitmentEndBlockFactory commitmentEndBlockFactory;
    private final HashMap<Blockchain, ParticipatingDilutionProcess> dilutionProcesses;
    private final HashMap<Blockchain, HashSet<ParticipatingDilutionProcess>> completedDilutionProcesses;
    private final HashMap<Blockchain, ManagedDilutionProcess> managedDilutionProcesses;
    private final HashMap<Blockchain, HashSet<ManagedDilutionProcess>> completedManagedDilutionProcesses;
    private final HashMap<Blockchain, DilutionApplicationCircle> dilutionApplications;
    private final HashMap<Blockchain, BackgroundDilutionProcessCircle> backgroundDilutionProcesses;
    private final HashMap<Blockchain, ByteMap<Long>> poolManagerCounters;
    private final BlameCircle blameCircle;
    private double acceptableBlameIncrease = 0.0001;
    private ByteMap<Double> inviteAcceptableBlame;
    private final ArrayList<String> log;

    public int timesSwitchedBetweenPoolRole = 0;
    public int updateCounter = 0;
    public int updateParticipatingCounter = 0;
    public int updateManagedCounter = 0;

    public static void SET_DILUTION_PROCESS_TIMEOUT(long dilutionProcessTimeout) {
        BlockchainLayer.DILUTION_PROCESS_TIMEOUT = dilutionProcessTimeout;
    }

    public static void SET_MAXIMUM_SWITCH_COUNTER_FROM_POOL(int maximumSwitchCounterFromPool) {
        BlockchainLayer.MAXIMUM_SWITCH_COUNTER_FROM_POOL = maximumSwitchCounterFromPool;
    }

    public static void SET_MAXIMUM_SWITCH_COUNTER_FROM_APPLICATION(int maximumSwitchCounterFromApplication) {
        BlockchainLayer.MAXIMUM_SWITCH_COUNTER_FROM_APPLICATION = maximumSwitchCounterFromApplication;
    }

    public BlockchainLayer(String name, PrivacyLayer privacyLayer) {
        this.privacyLayer = privacyLayer;
        networkLayer = new NetworkLayer(name, this);
        blockchains = new HashSet<>();
        headlessChains = new HashMap<>();
        registrationBlockFactory = new RegistrationBlockFactory();
        initializationBlockFactory = new InitializationBlockFactory();
        dilutionStartBlockFactory = new DilutionStartBlockFactory();
        dilutionBlockFactory = new DilutionBlockFactory();
        depthBlockFactory = new DepthBlockFactory();
        preDepthBlockFactory = new PreDepthBlockFactory();
        dilutionEndBlockFactory = new DilutionEndBlockFactory();
        commitmentBlockFactory = new CommitmentBlockFactory();
        commitmentEndBlockFactory = new CommitmentEndBlockFactory();
        dilutionProcesses = new HashMap<>();
        completedDilutionProcesses = new HashMap<>();
        managedDilutionProcesses = new HashMap<>();
        completedManagedDilutionProcesses = new HashMap<>();
        backgroundDilutionProcesses = new HashMap<>();
        dilutionApplications = new HashMap<>();
        poolManagerCounters = new HashMap<>();
        blameCircle = new BlameCircle(MAX_NUMBER_BLAMED, PrivacyUtils.PUBLIC_KEY_LENGTH, POOL_IDENTIFIER_SIZE);
        log = new ArrayList<>();
        inviteAcceptableBlame = new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH);
        BlockchainLayerClockTask blockchainLayerClockTask = new BlockchainLayerClockTask(this);
        Thread blockchainLayerClockThread = new Thread(blockchainLayerClockTask);
        blockchainLayerClockThread.start();
    }

    public NetworkLayer getNetworkLayer() {
        return networkLayer;
    }

    public void connect(String ipAddress, int port) {
        networkLayer.connect(ipAddress, port);
    }

    public Blockchain getBlockchainByIdAndKey(byte[] id, byte[] publicKey) {
        Blockchain blockchain = null;
        for (Blockchain blockchainToCheck: blockchains) {
            if (blockchainToCheck.checkId(id) && blockchainToCheck.checkManagerPublicKey(publicKey)) {
                blockchain = blockchainToCheck;
            }
        }
        return blockchain;
    }

    /**
     * Start an election in the blockchain by creating its InitializationBlock.
     *
     * @param id Election ID of the new election.
     * @param signature A Signature for signing with the private key corresponding to publicKey
     * @param publicKey Public key of the election manager starting this blockchain.
     * @return
     */
    public boolean createInitializationBlock(byte[] id, Signature signature, byte[] publicKey) {
        log.add("createInitializationBlock()");
        log.add("  Manager: " + Hex.toHexString(publicKey));
        log.add("  Election ID: " + Hex.toHexString(id));
        if (id.length != Election.ID_LENGTH) {
            log.add("  Couldn't create initialization block because the election ID was of incorrect size.");
            return false;
        } else if (publicKey.length != PrivacyUtils.PUBLIC_KEY_LENGTH) {
            log.add("  Couldn't create initialization block because the manager public key was of incorrect size.");
            return false;
        } else if (getBlockchainByIdAndKey(id, publicKey) == null) {
            Blockchain blockchain = new Blockchain(id, publicKey);
            blockchain.setDilutionBlockFactory(dilutionBlockFactory);
            blockchain.setCommitmentBlockFactory(commitmentBlockFactory);
            blockchains.add(blockchain);
            InitializationBlock initializationBlock = initializationBlockFactory.createBlock(blockchain, signature);
            if (initializationBlock == null) {
                log.add("  InitializationBlockFactory returned a null block.");
                blockchains.remove(blockchain);
            } else {
                dilutionApplications.put(blockchain, new DilutionApplicationCircle(HASH_CIRCLE_SIZE, PrivacyUtils.PUBLIC_KEY_LENGTH));
                completedDilutionProcesses.put(blockchain, new HashSet<>());
                completedManagedDilutionProcesses.put(blockchain, new HashSet<>());
                headlessChains.put(blockchain, new BlockchainCircle(MAX_NUMBER_OF_HEADLESS_CHAINS, PrivacyUtils.HASH_LENGTH));
                backgroundDilutionProcesses.put(blockchain, new BackgroundDilutionProcessCircle(MAX_NUMBER_OF_BACKGROUND_DILUTION_PROCESSES));
                networkLayer.broadcastAnonymously(initializationBlock.getBytes());
                poolManagerCounters.put(blockchain, new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH));
                log.add("  Created initialization block.");
            }
            return initializationBlock != null;
        } else {
            log.add("  Couldn't create initialization block because an election with this election ID already existed for this manager public key.");
            return false;
        }
    }

    /**
     * Broadcast a Chain Request message, requesting that the last block of a blockchain be sent in response.
     *
     * @param managerPublicKey Public key of the election manager
     * @param chainId Election ID of the election
     */
    private void sendChainRequest(byte[] managerPublicKey, byte[] chainId) {
        log.add("sendChainRequest()");
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        log.add("  Election ID: " + Hex.toHexString(chainId));
        ArrayList<byte[]> messageParts = new ArrayList<>();
        messageParts.add(CHAIN_REQUEST_PREFIX);
        messageParts.add(managerPublicKey);
        messageParts.add(chainId);
        networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
        log.add("  Successfully sent chain request.");
    }

    /**
     * Validate a Chain Request message.
     *
     * @param message Bytes of the entire Chain Request message
     * @return If the blockchain exists and is known to this client: EXISTING_CHAIN_REQUEST; otherwise: INVALID
     */
    private ValidationStatus validateChainRequest(byte[] message) {
        log.add("validateChainRequest()");
        boolean isValid = message.length == CHAIN_REQUEST_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH;
        int i = 0;
        while (isValid && i < CHAIN_REQUEST_PREFIX.length) {
            if (CHAIN_REQUEST_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            byte[] electionManagerPublicKey = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
            for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
                electionManagerPublicKey[j] = message[j + CHAIN_REQUEST_PREFIX.length];
            }
            log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
            byte[] chainId = new byte[Election.ID_LENGTH];
            for (int j = 0; j < Election.ID_LENGTH; j++) {
                chainId[j] = message[j + PrivacyUtils.PUBLIC_KEY_LENGTH + CHAIN_REQUEST_PREFIX.length];
            }
            log.add("  Election ID: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                log.add("  The chain exists");
                return ValidationStatus.EXISTING_CHAIN_REQUEST;
            } else {
                log.add("  The chain doesn't exist");
            }
        }
        return ValidationStatus.INVALID;
    }

    /**
     * Process a Chain Request message after it has already been validated, and return the last block of the chain to
     * the sender.
     *
     * @param message Bytes of the entire Chain Request message
     * @return Bytes of the last block in the blockchain
     */
    public byte[] processChainRequest(byte[] message) {
        log.add("processChainRequest()");
        byte[] electionManagerPublicKey = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
        for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
            electionManagerPublicKey[j] = message[j + CHAIN_REQUEST_PREFIX.length];
        }
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = new byte[Election.ID_LENGTH];
        for (int j = 0; j < Election.ID_LENGTH; j++) {
            chainId[j] = message[j + PrivacyUtils.PUBLIC_KEY_LENGTH + CHAIN_REQUEST_PREFIX.length];
        }
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain == null) {
            log.add("  The chain doesn't exist");
            return null;
        } else {
            log.add("  The chain exists");
            return blockchain.getLastBlock().getBytes();
        }
    }

    /**
     * Start tracking the blockchain of an election and then send a Chain Request.
     *
     * @param managerPublicKey Public key of the election manager
     * @param chainId Election ID of the blockchain
     */
    public void subscribeToElection(byte[] managerPublicKey, byte[] chainId) {
        log.add("subscribeToElection()");
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Wasn't subscribed yet.");
            blockchain = new Blockchain(chainId, managerPublicKey);
            blockchain.setDilutionBlockFactory(dilutionBlockFactory);
            blockchain.setCommitmentBlockFactory(commitmentBlockFactory);
            blockchains.add(blockchain);
            privacyLayer.createElectionFromPublicKey(chainId, managerPublicKey, null);
            dilutionApplications.put(blockchain, new DilutionApplicationCircle(HASH_CIRCLE_SIZE, PrivacyUtils.PUBLIC_KEY_LENGTH));
            completedDilutionProcesses.put(blockchain, new HashSet<>());
            completedManagedDilutionProcesses.put(blockchain, new HashSet<>());
            headlessChains.put(blockchain, new BlockchainCircle(MAX_NUMBER_OF_HEADLESS_CHAINS, PrivacyUtils.HASH_LENGTH));
            backgroundDilutionProcesses.put(blockchain, new BackgroundDilutionProcessCircle(MAX_NUMBER_OF_BACKGROUND_DILUTION_PROCESSES));
            poolManagerCounters.put(blockchain, new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH));
            sendChainRequest(managerPublicKey, chainId);
        } else {
            log.add("  Was already subscribed.");
        }
    }

    /**
     * Process an InitializationBlock message after it has already been validated, either creating a new Blockchain
     * locally or adding the InitializationBlock to an already existing Blockchain.
     *
     * @param message Bytes of the entire InitializationBlock
     */
    private void processInitializationBlock(byte[] message) {
        log.add("processInitializationBlock()");
        byte[] managerPublicKey = initializationBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] chainId = initializationBlockFactory.getChainId(message);
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Initialization block for new blockchain.");
            blockchain = new Blockchain(chainId, managerPublicKey);
            blockchain.setDilutionBlockFactory(dilutionBlockFactory);
            blockchain.setCommitmentBlockFactory(commitmentBlockFactory);
            blockchains.add(blockchain);
            log.add("  Created new blockchain.");
        }
        if (blockchain.isHeadless()) {
            log.add("  Attemptin to add initialization block to blockchain.");
            InitializationBlock initializationBlock = initializationBlockFactory.createBlock(blockchain, message);
            if (initializationBlock == null) {
                log.add("  InitializationBlockFactory returned a null block.");
                blockchains.remove(blockchain);
            } else {
                privacyLayer.createElectionFromPublicKey(chainId, managerPublicKey, null);
                headlessChains.put(blockchain, new BlockchainCircle(MAX_NUMBER_OF_HEADLESS_CHAINS, PrivacyUtils.HASH_LENGTH));
                dilutionApplications.put(blockchain, new DilutionApplicationCircle(HASH_CIRCLE_SIZE, PrivacyUtils.PUBLIC_KEY_LENGTH));
                completedDilutionProcesses.put(blockchain, new HashSet<>());
                completedManagedDilutionProcesses.put(blockchain, new HashSet<>());
                backgroundDilutionProcesses.put(blockchain, new BackgroundDilutionProcessCircle(MAX_NUMBER_OF_BACKGROUND_DILUTION_PROCESSES));
                poolManagerCounters.put(blockchain, new ByteMap<>(PrivacyUtils.PUBLIC_KEY_LENGTH));
                log.add("  Added initialization block to chain.");
            }
        }
    }

    /**
     * Validate an InitializationBlock message by checking its signature.
     *
     * @param message Bytes of the entire InitializationBlock
     * @return VALID or INVALID
     */
    private ValidationStatus validateInitializationBlock(byte[] message) {
        log.add("validateInitializationBlock()");
        if (initializationBlockFactory.isValid(message)) {
            byte[] managerPublicKey = initializationBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] chainId = initializationBlockFactory.getChainId(message);
            log.add("  Election ID: " + Hex.toHexString(chainId));
            byte[] signatureBytes = initializationBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(initializationBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    log.add("  Signature is valid");
                    return ValidationStatus.VALID;
                } else {
                    log.add("  Signature is not valid");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        }
        return ValidationStatus.INVALID;
    }

    /**
     * Register a voter in the election by creating a RegistrationBlock. This can only be done by the election manager.
     *
     * @param managerPublicKey Public key of the election manager
     * @param chainId Election ID
     * @param voterId A bytestring identifying the voter (format not specified by protocol)
     * @param signature Signature of the election manager, for signing
     * @param publicKey Public key of the voter to be registered
     */
    public void createRegistrationBlock(byte[] managerPublicKey, byte[] chainId, byte[] voterId, Signature signature, byte[] publicKey) {
        log.add("createRegistrationBlock()");
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        log.add("  Election ID: " + Hex.toHexString(chainId));
        log.add("  Voter ID: " + Hex.toHexString(voterId));
        log.add("  Public key: " + Hex.toHexString(publicKey));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Failed to create registration block for unknown blockchain");
        } else {
            RegistrationBlock registrationBlock = registrationBlockFactory.createBlock(blockchain, voterId, publicKey, signature);
            if (registrationBlock != null) {
                privacyLayer.setKeyOriginBlockIndex(registrationBlock.getPublicKey(), registrationBlock.getIndex(), managerPublicKey, chainId);
                byte[] message = registrationBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
                log.add("  Created registration block");
            } else {
                log.add("  RegistrationBlockFactory returned a null block.");
            }
        }
    }

    /**
     * Process a RegistrationBlock message after it has already been validated, adding the block to the blockchain and,
     * if the public key belongs to an Identity in the PrivacyLayer, setting the key origin of that Identity to this
     * block.
     *
     * @param message Bytes of the entire RegistrationBlock
     */
    private void processRegistrationBlock(byte[] message) {
        log.add("processRegistrationBlock()");
        byte[] managerPublicKey = registrationBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] id = registrationBlockFactory.getChainId(message);
        log.add("  Election ID: " + Hex.toHexString(id));
        byte[] voterId = registrationBlockFactory.getVoterId(message);
        log.add("  Voter ID: " + Hex.toHexString(voterId));
        byte[] publicKey = registrationBlockFactory.getVoterPublicKey(message);
        log.add("  Public key: " + Hex.toHexString(publicKey));
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Registration block belongs to an unknown chain");
        } else {
            RegistrationBlock registrationBlock = (RegistrationBlock) registrationBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
            if (registrationBlock == null) {
                log.add("  RegistrationBlockFactory returned a null block.");
            } else {
                privacyLayer.setKeyOriginBlockIndex(registrationBlock.getPublicKey(), registrationBlock.getIndex(), managerPublicKey, id);
                privacyLayer.setRegistrationBlock(registrationBlock.getPublicKey(), registrationBlock);
                log.add("  Created registration block");
            }
        }
    }

    /**
     * Validate a RegistrationBlock by checking if the blockchain is known to this client, if it is in the Registration
     * phase and by checking the block's signature.
     *
     * @param message Byte of the entire RegistrationBlock
     * @return VALID if it is immediately clear the block is valid;
     *         INVALID if it is immediately clear the block is invalid;
     *         NEED_PREDECESSOR if the client doesn't yet have the predecessor block, and if the chain score at this
     *         block is higher than the chain score of the existing chain;
     *         NEED_PREDECESSOR_LOSING_FORK if the client doesn't yet have the predecessor block, and if the chain score
     *         at this block is lower than the chain score of the existing chain;
     *         EQUAL_FORK if the block wins a fork against another RegistrationBlock by comparing voter IDs;
     *         LOSES_SECONDARY_CHECK if the block loses a fork against another RegistrationBlock by comparing voter IDs;
     *         LOSING_FORK if the block loses a fork against another RegistrationBlock by comparing chain score, but it
     *         would win by comparing voter IDS;
     *         LOSING_FORK_LOSES_SECONDARY_CHECK if the block loses a fork against another RegistrationBlock by
     *         comparing chain score, and it would also lose by comparing voter IDS.
     *
     */
    private ValidationStatus validateRegistrationBlock(byte[] message) {
        log.add("validateRegistrationBlock()");
        if (registrationBlockFactory.isValid(message)) {
            byte[] managerPublicKey = registrationBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] id = registrationBlockFactory.getChainId(message);
            log.add("  Election ID: " + Hex.toHexString(id));
            byte[] voterId = registrationBlockFactory.getVoterId(message);
            log.add("  Voter ID: " + Hex.toHexString(voterId));
            byte[] publicKey = registrationBlockFactory.getVoterPublicKey(message);
            log.add("  Public key: " + Hex.toHexString(publicKey));
            byte[] signatureBytes = registrationBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(registrationBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    log.add("  Signature of registration block is valid");
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        log.add("  Registration block belongs to known blockchain");
                        ValidationStatus validationStatus = registrationBlockFactory.checkSuccession(blockchain, message);
                        if (validationStatus == ValidationStatus.VALID) {
                            long chainScore = registrationBlockFactory.getChainScore(message);
                            int blockIndex = registrationBlockFactory.getIndex(message);
                            Block predecessorBlock = blockchain.getBlock(blockIndex - 1);
                            if (chainScore != predecessorBlock.getChainScore() + 1) {
                                validationStatus = ValidationStatus.INVALID;
                            }
                        }
                        return validationStatus;
                    } else {
                        log.add("  Registration block belongs to unknown blockchain");
                    }
                } else {
                    log.add("  Signature of registration block is not valid");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        }
        return ValidationStatus.INVALID;
    }

    /**
     * End the Registration phase and start the Signature Dilution phase by adding a DilutionStartBlock to the
     * blockchain. Only the election manager can do this.
     *
     * @param managerPublicKey Public key of the election manager
     * @param chainId Election ID
     * @param signature Signature of the election manager, for signing
     */
    public void createDilutionStartBlock(byte[] managerPublicKey, byte[] chainId, Signature signature) {
        log.add("createDilutionStartBlock()");
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Failed to create dilution start block for unknown blockchain.");
        } else {
            log.add("  Blockchain is known.");
            DilutionStartBlock dilutionStartBlock = dilutionStartBlockFactory.createBlock(blockchain, signature);
            if (dilutionStartBlock != null) {
                log.add("  Created dilution start block.");
                byte[] message = dilutionStartBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
            } else {
                log.add("  DilutionStartBlockFactory returned null block.");
            }
        }
    }

    /**
     * Process a DilutionStartBlock after it has already been validated, ending the Registration phase and starting the
     * Signature Dilution phase.
     *
     * @param message Bytes of the entire DilutionStartBlock
     */
    private void processDilutionStartBlock(byte[] message) {
        log.add("processDilutionStartBlock()");
        byte[] managerPublicKey = dilutionStartBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] id = dilutionStartBlockFactory.getChainId(message);
        log.add("  Election ID: " + Hex.toHexString(id));
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Dilution start block belongs to an unknown chain");
        } else {
            log.add("  Blockchain is known.");
            dilutionStartBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
        }
    }

    /**
     * Validate a DilutionStartBlock by checking if the blockchain is known to this client, if it is in the Registration
     * phase and by checking the block's signature.
     *
     * @param message Byte of the entire DilutionStartBlock
     * @return VALID if it is immediately clear the block is valid;
     *         INVALID if it is immediately clear the block is invalid;
     *         NEED_PREDECESSOR if the client doesn't yet have the predecessor block, and if the chain score at this
     *         block is higher than the chain score of the existing chain;
     *         NEED_PREDECESSOR_LOSING_FORK if the client doesn't yet have the predecessor block, and if the chain score
     *         at this block is lower than the chain score of the existing chain;
     *
     */
    private ValidationStatus validateDilutionStartBlock(byte[] message) {
        log.add("validateDilutionStartBlock()");
        if (dilutionStartBlockFactory.isValid(message)) {
            byte[] managerPublicKey = dilutionStartBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] signatureBytes = dilutionStartBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(dilutionStartBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    byte[] id = dilutionStartBlockFactory.getChainId(message);
                    log.add("  Election ID: " + Hex.toHexString(id));
                    log.add("  Signature of dilution start block is valid.");
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        log.add("  Blockchain was known.");
                        ValidationStatus validationStatus = dilutionStartBlockFactory.checkSuccession(blockchain, message);
                        if (validationStatus == ValidationStatus.VALID) {
                            long chainScore = dilutionStartBlockFactory.getChainScore(message);
                            int blockIndex = dilutionStartBlockFactory.getIndex(message);
                            if (chainScore != blockchain.getBlock(blockIndex - 1).getChainScore()) {
                                validationStatus = ValidationStatus.INVALID;
                            }
                        }
                        return validationStatus;
                    } else {
                        log.add("  Dilution start block belongs to unknown blockchain.");
                    }
                } else {
                    log.add("  Signature of dilution start block is not valid.");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        }
        return ValidationStatus.INVALID;
    }

    /**
     * Broadcast a Dilution Application to signal that the client wishes to participate in signature dilution with a
     * given public key.
     *
     * @param managerPublicKey Public key of the election manager
     * @param chainId Election ID
     * @param publicKey Public key of the voter to dilute
     * @param keyOriginBlockIndex
     * @param signature Signature of the voter corresponding to publicKey, for signing
     * @param identity Identity corresponding to publicKey
     */
    public boolean sendDilutionApplication(byte[] managerPublicKey, byte[] chainId, byte[] publicKey, long keyOriginBlockIndex, Signature signature, Identity identity) {
        log.add("sendDilutionApplication()");
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        log.add("  Election ID: " + Hex.toHexString(chainId));
        log.add("  Public key: " + Hex.toHexString(publicKey));
        log.add("  Key origin block: " + String.valueOf(keyOriginBlockIndex));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Couldn't send a dilution application for a non-existing blockchain");
        } else {
            try {
                ArrayList<byte[]> messageParts = new ArrayList<>();
                messageParts.add(DILUTION_APPLICATION_PREFIX);
                messageParts.add(managerPublicKey);
                messageParts.add(chainId);
                byte[] message = ByteUtils.concatenateByteArrays(messageParts);
                signature.update(message);
                byte[] signatureBytes = ByteUtils.encodeWithLengthByte(signature.sign());
                ArrayList<byte[]> byteArrays = new ArrayList<>();
                byteArrays.add(message);
                if (blockchain.oldKeyIsStillValid(publicKey, keyOriginBlockIndex) && blockchain.oldKeyDepthIsOK(publicKey, keyOriginBlockIndex)) {
                    byteArrays.add(publicKey);
                    byteArrays.add(ByteUtils.longToByteArray(keyOriginBlockIndex));
                    byteArrays.add(signatureBytes);
                    byte[] dilutionApplication = ByteUtils.concatenateByteArrays(byteArrays);
                    ParticipatingDilutionProcess participatingDilutionProcess = new ParticipatingDilutionProcess();
                    participatingDilutionProcess.setPublicKeyForDilutionApplication(publicKey, keyOriginBlockIndex);
                    participatingDilutionProcess.setIdentityForPool(identity);
                    participatingDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                    dilutionProcesses.put(blockchain, participatingDilutionProcess);
                    networkLayer.broadcastAnonymously(dilutionApplication);
                    log.add("  Sent dilution application");
                    return true;
                } else if (!blockchain.oldKeyIsStillValid(publicKey, keyOriginBlockIndex)) {
                    log.add("  Couldn't send a dilution application for a public key that is no longer valid.");
                } else {
                    log.add("  Couldn't send a dilution application for a public key with a depth that is too high.");
                }
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        }
        return false;
    }

    /**
     * Process a Dilution Application after it has already been validated: if the client is trying to start a dilution
     * pool, add the public key of this Dilution Application to the pool.
     *
     * @param message Bytes of the entire Dilution Application
     */
    private void processDilutionApplication(byte[] message) {
        log.add("processDilutionApplication()");
        int offset = DILUTION_APPLICATION_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        byte[] publicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Public key: " + Hex.toHexString(publicKey));
        byte[] keyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
        offset += Block.INDEX_SIZE;
        long keyOriginBlockIndex = ByteUtils.longFromByteArray(keyOriginBlockIndexBytes);
        byte[] signature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
        log.add("  Key origin block: " + String.valueOf(keyOriginBlockIndex));
        DilutionApplicationCircle dilutionApplicationsOfChain = dilutionApplications.get(blockchain);
        if (!dilutionApplicationsOfChain.contains(new KeyWithOrigin(publicKey, keyOriginBlockIndex))) {
            dilutionApplicationsOfChain.add(new DilutionApplication(new KeyWithOrigin(publicKey, keyOriginBlockIndex), signature));
            if (managedDilutionProcesses.containsKey(blockchain)) {
                log.add("  Managed dilution process exists for this blockchain");
                ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
                if (dilutionProcess.isAssemblingDilutionPool()) {
                    log.add("  Managed dilution process was assembling dilution pool");
                    possiblyStartDilutionPool(blockchain);
                }
            }
        }
    }

    /** Validate a Dilution Application by checking if the blockchain is known to this client and if the old public key
     * is still valid and at a depth low enough to be diluted, and checking the signature.
     *
     * @param message Bytes of the entire Dilution Application
     * @return VALID or INVALID
     */
    public ValidationStatus validateDilutionApplication(byte[] message) {
        log.add("validateDilutionApplication()");
        boolean isValid = message.length > DILUTION_APPLICATION_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1;
        int i = 0;
        while (isValid && i < DILUTION_APPLICATION_PREFIX.length) {
            if (DILUTION_APPLICATION_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = DILUTION_APPLICATION_PREFIX.length;
            byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
            byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
            offset += Election.ID_LENGTH;
            log.add("  Election ID: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain == null) {
                log.add("  Dilution application for unknown blockchain.");
                return ValidationStatus.INVALID;
            } else {
                byte[] publicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                log.add("  Public key: " + Hex.toHexString(publicKey));
                byte[] keyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                offset += Block.INDEX_SIZE;
                long keyOriginBlockIndex = ByteUtils.longFromByteArray(keyOriginBlockIndexBytes);
                log.add("  Key origin block: " + String.valueOf(keyOriginBlockIndex));
                byte[] signature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                try {
                    byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, DILUTION_APPLICATION_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH);
                    if (PrivacyUtils.verifySignature(messageToVerify, publicKey, signature)) {
                        log.add("  Signature was valid");
                        if (blockchain.oldKeyIsStillValid(publicKey, keyOriginBlockIndex)) {
                            if (blockchain.oldKeyDepthIsOK(publicKey, keyOriginBlockIndex)) {
                                log.add("  Dilution application was valid");
                                return ValidationStatus.VALID;
                            } else {
                                log.add("  Key was not at the correct depth.");
                                return ValidationStatus.INVALID;
                            }
                        } else {
                            log.add("  Key was no longer valid.");
                            return ValidationStatus.INVALID;
                        }
                    } else {
                        log.add("  Signature wasn't valid.");
                        return ValidationStatus.INVALID;
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("  NoSuchAlgorithmException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (InvalidKeyException exception) {
                    log.add("  InvalidKeyException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (InvalidKeySpecException exception) {
                    log.add("  InvalidKeySpecException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (SignatureException exception) {
                    log.add("  SignatureException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                }
            }
        } else {
            log.add("  Failed initial validation check");
            return ValidationStatus.INVALID;
        }
    }

    /**
     * Generate a new pool identifier that wasn't used yet.
     *
     * @param blockchain The Blockchain of the dilution pool
     * @param poolManagerPublicKey The public key of the pool manager
     * @return A new pool identifier
     */
    private byte[] getNewPoolIdentifier(Blockchain blockchain, byte[] poolManagerPublicKey) {
        ByteMap<Long> poolManagerCountersForBlockchain = poolManagerCounters.get(blockchain);
        Long counter = null;
        for (byte[] indexingPublicKey: poolManagerCountersForBlockchain.keySet()) {
            if (ByteUtils.byteArraysAreEqual(poolManagerPublicKey, indexingPublicKey)) {
                counter = poolManagerCountersForBlockchain.get(indexingPublicKey);
                poolManagerPublicKey = indexingPublicKey;
            }
        }
        long newCounter = 0;
        if (counter != null) {
            newCounter = counter + 1;
        }
        poolManagerCountersForBlockchain.put(poolManagerPublicKey, newCounter);
        return ByteUtils.longToByteArray(newCounter);
    }

    public void startDilutionPool(Blockchain blockchain, DilutionApplicationCircle selectedApplications, ManagedDilutionProcess dilutionProcess) {
        log.add("startDilutionPool()");
        log.add("  Manager: " + Hex.toHexString(blockchain.getManagerPublicKey()));
        log.add("  Election ID: " + Hex.toHexString(blockchain.getId()));
        ArrayList<DilutionApplication> invitees = new ArrayList<>();
        while (invitees.size() + dilutionProcess.getSelfIncludingNumber() < dilutionProcess.getDesiredPoolSize() && selectedApplications.getSize() > 0) {
            DilutionApplication dilutionApplication = selectedApplications.pop(true, blameCircle);
            invitees.add(dilutionApplication);
        }
        if (invitees.size() + dilutionProcess.getSelfIncludingNumber() >= dilutionProcess.getDesiredPoolSize()) {
            dilutionProcess.setAssemblingDilutionPool(false);
            byte[] currentPoolIdentifier = getNewPoolIdentifier(blockchain, dilutionProcess.getPublicKeyAsPoolManager());
            dilutionProcess.setCurrentPoolIdentifier(currentPoolIdentifier);
            dilutionProcess.setPoolResponses(new HashSet<>());
            log.add("  Pool: " + Hex.toHexString(currentPoolIdentifier));
            log.add("  Number of invitees: " + invitees.size());
            for (DilutionApplication invitee : invitees) {
                ArrayList<byte[]> messageToSignParts = new ArrayList<>();
                messageToSignParts.add(INVITE_PREFIX);
                messageToSignParts.add(blockchain.getManagerPublicKey());
                messageToSignParts.add(blockchain.getId());
                messageToSignParts.add(invitee.getKeyWithOrigin().getPublicKey());
                messageToSignParts.add(ByteUtils.longToByteArray(invitee.getKeyWithOrigin().getKeyOriginBlockIndex()));
                messageToSignParts.add(currentPoolIdentifier);
                messageToSignParts.add(ByteUtils.encodeWithLengthByte(invitee.getSignature()));
                log.add("    Public key: " + Hex.toHexString(invitee.getKeyWithOrigin().getPublicKey()));
                log.add("    Key origin block: " + String.valueOf(invitee.getKeyWithOrigin().getKeyOriginBlockIndex()));
                byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
                try {
                    Signature signatureAsPoolManager = dilutionProcess.getSignatureAsPoolManager();
                    signatureAsPoolManager.update(messageToSign);
                    byte[] signature = ByteUtils.encodeWithLengthByte(signatureAsPoolManager.sign());
                    ArrayList<byte[]> messageParts = new ArrayList<>();
                    messageParts.add(messageToSign);
                    messageParts.add(dilutionProcess.getPublicKeyAsPoolManager());
                    messageParts.add(ByteUtils.longToByteArray(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                    messageParts.add(signature);
                    dilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                    networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
                    log.add("    Sent invite.");
                } catch (SignatureException exception) {
                    log.add("    Couldn't sign invite due to SignatureException");
                }
            }
        }
    }

    public boolean attemptToStartDilutionPool(Signature signature, byte[] publicKey, long keyOriginBlockIndex, Identity identity, boolean includeSelf, byte[] electionManagerPublicKey, byte[] chainId) {
        log.add("attemptToStartDilutionPool()");
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain != null && blockchain.oldKeyIsStillValid(publicKey, keyOriginBlockIndex) && blockchain.oldKeyDepthIsOK(publicKey, keyOriginBlockIndex)) {
            ManagedDilutionProcess dilutionProcess = new ManagedDilutionProcess(this, blockchain);
            dilutionProcess.setAssemblingDilutionPool(true);
            dilutionProcess.setPublicKeyAsPoolManager(publicKey);
            dilutionProcess.setIdentityForPool(identity);
            dilutionProcess.setKeyOriginBlockIndexAsPoolManager(keyOriginBlockIndex);
            dilutionProcess.setSignatureAsPoolManager(signature);
            dilutionProcess.setIncludeSelf(includeSelf);
            dilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
            dilutionProcess.setNameOfLastMessageReceived("none");
            managedDilutionProcesses.put(blockchain, dilutionProcess);
            possiblyStartDilutionPool(blockchain);
            return true;
        } else {
            return false;
        }
    }

    public void possiblyStartDilutionPool(Blockchain blockchain) {
        log.add("possiblyStartDilutionPool()");
        if (managedDilutionProcesses.containsKey(blockchain)) {
            ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
            DilutionApplicationCircle dilutionApplicationsOfChain = dilutionApplications.get(blockchain);
            if (dilutionApplicationsOfChain.getSize() + dilutionProcess.getSelfIncludingNumber() >= dilutionProcess.getDesiredPoolSize()) {
                DilutionApplicationCircle selectedApplications = new DilutionApplicationCircle(HASH_CIRCLE_SIZE, PrivacyUtils.PUBLIC_KEY_LENGTH);
                Block managerOriginBlock = blockchain.getBlock(dilutionProcess.getKeyOriginBlockIndexAsPoolManager());
                byte[] managerAnonymitySet = null;
                switch (managerOriginBlock.getPrefix()[0]) {
                    case REGISTRATION_BLOCK_FIRST_CHAR:
                        RegistrationBlock managerOriginRegistrationBlock = (RegistrationBlock) managerOriginBlock;
                        managerAnonymitySet = managerOriginRegistrationBlock.getPublicKey();
                        break;
                    case DILUTION_BLOCK_FIRST_CHAR:
                        DilutionBlock managerOriginDilutionBlock = (DilutionBlock) managerOriginBlock;
                        managerAnonymitySet = managerOriginDilutionBlock.getAnonymitySet();
                        break;
                }
                for (DilutionApplication dilutionApplication: dilutionApplicationsOfChain) {
                    boolean canAdd = blockchain.oldKeyIsStillValid(dilutionApplication.getKeyWithOrigin().getPublicKey(), dilutionApplication.getKeyWithOrigin().getKeyOriginBlockIndex());
                    byte[] inviteeAnonymitySet = null;
                    if (canAdd) {
                        Block inviteeOriginBlock = blockchain.getBlock(dilutionApplication.getKeyWithOrigin().getKeyOriginBlockIndex());
                        switch (inviteeOriginBlock.getPrefix()[0]) {
                            case REGISTRATION_BLOCK_FIRST_CHAR:
                                RegistrationBlock inviteeOriginRegistrationBlock = (RegistrationBlock) inviteeOriginBlock;
                                inviteeAnonymitySet = inviteeOriginRegistrationBlock.getPublicKey();
                                break;
                            case DILUTION_BLOCK_FIRST_CHAR:
                                DilutionBlock inviteeOriginDilutionBlock = (DilutionBlock) inviteeOriginBlock;
                                inviteeAnonymitySet = inviteeOriginDilutionBlock.getAnonymitySet();
                                break;
                        }
                        canAdd = managerAnonymitySet != null && inviteeAnonymitySet != null && !ByteUtils.byteArraysAreEqual(managerAnonymitySet, inviteeAnonymitySet);
                    }
                    for (DilutionApplication existingApplication: selectedApplications) {
                        if (existingApplication == dilutionApplication) {
                            canAdd = false;
                        }
                    }
                    if (canAdd) {
                        selectedApplications.add(dilutionApplication);
                    }
                }
                if (selectedApplications.getSize() + dilutionProcess.getSelfIncludingNumber() >= dilutionProcess.getDesiredPoolSize()) {
                    startDilutionPool(blockchain, selectedApplications, dilutionProcess);
                }
            }
        }
    }

    public void processInvite(byte[] message) {
        log.add("processInvite()");
        int offset = INVITE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain == null) {
            log.add("  Invite was for an unknown blockchain");
        } else if (!dilutionProcesses.containsKey(blockchain)) {
            log.add("  Invite was for a blockchain for which no dilution application was sent");
        } else {
            ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
            byte[] publicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Invited public key: " + Hex.toHexString(publicKey));
            byte[] keyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
            offset += Block.INDEX_SIZE;
            long keyOriginBlockIndex = ByteUtils.longFromByteArray(keyOriginBlockIndexBytes);
            log.add("  Key origin: " + String.valueOf(keyOriginBlockIndex));
            byte[] currentPoolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
            offset += POOL_IDENTIFIER_SIZE;
            byte[] dilutionApplicationSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            offset += 1 + dilutionApplicationSignature.length;
            if (keyOriginBlockIndex == participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication() && ByteUtils.byteArraysAreEqual(publicKey, participatingDilutionProcess.getPublicKeyForDilutionApplication())) {
                byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, Block.INDEX_SIZE + INVITE_PREFIX.length + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + POOL_IDENTIFIER_SIZE + 1 + dilutionApplicationSignature.length);
                byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                byte[] keyOriginBlockIndexOfPoolManagerBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                long keyOriginBlockIndexOfPoolManager = ByteUtils.longFromByteArray(keyOriginBlockIndexOfPoolManagerBytes);
                byte[] signature = ByteUtils.readByteSubstringWithLengthEncoded(message, Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + dilutionApplicationSignature.length + POOL_IDENTIFIER_SIZE + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + INVITE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE);
                try {
                    if (PrivacyUtils.verifySignature(messageToVerify, publicKeyOfPoolManager, signature)) {
                        if (participatingDilutionProcess.getCurrentPoolIdentifier() == null
                                && blameCircle.getBlameFactor(new KeyWithOrigin(publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager)) <= getInviteAcceptableBlame(publicKey)) {
                            participatingDilutionProcess.setCurrentPoolIdentifier(currentPoolIdentifier);
                            participatingDilutionProcess.setPublicKeyOfPoolManager(publicKeyOfPoolManager);
                            participatingDilutionProcess.setKeyOriginBlockIndexOfPoolManager(keyOriginBlockIndexOfPoolManager);
                            participatingDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                            log.add("  Signature was valid.");
                            sendPoolResponse(blockchain, currentPoolIdentifier, publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager, dilutionApplicationSignature, signature, participatingDilutionProcess);
                        } else {
                            participatingDilutionProcess.addBackupInvite(new Invite(currentPoolIdentifier, publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager, dilutionApplicationSignature, signature));
                        }
                    } else {
                        log.add("  Signature wasn't valid.");
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("  NoSuchAlgorithmException:");
                    log.add("  " + exception.getMessage());
                } catch (InvalidKeyException exception) {
                    log.add("  InvalidKeyException:");
                    log.add("  " + exception.getMessage());
                } catch (InvalidKeySpecException exception) {
                    log.add("  InvalidKeySpecException:");
                    log.add("  " + exception.getMessage());
                } catch (SignatureException exception) {
                    log.add("  SignatureException:");
                    log.add("  " + exception.getMessage());
                }
            } else {
                log.add("  Invite was not for this public key.");
            }

        }
    }

    public ValidationStatus validateInvite(byte[] message) {
        log.add("validateInvite()");
        boolean isValid = message.length > INVITE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1;
        int i = 0;
        while (isValid && i < INVITE_PREFIX.length) {
            if (INVITE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = INVITE_PREFIX.length;
            byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
            byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
            offset += Election.ID_LENGTH;
            log.add("  Election ID: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain == null) {
                log.add("  Invite was for an unknown blockchain");
                return ValidationStatus.INVALID;
            } else {
                byte[] publicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                log.add("  Invited public key: " + Hex.toHexString(publicKey));
                byte[] keyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                offset += Block.INDEX_SIZE;
                long keyOriginBlockIndex = ByteUtils.longFromByteArray(keyOriginBlockIndexBytes);
                offset += POOL_IDENTIFIER_SIZE;
                byte[] dilutionApplicationSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + dilutionApplicationSignature.length;
                ArrayList<byte[]> dilutionApplicationMessageParts = new ArrayList<>();
                dilutionApplicationMessageParts.add(DILUTION_APPLICATION_PREFIX);
                dilutionApplicationMessageParts.add(electionManagerPublicKey);
                dilutionApplicationMessageParts.add(chainId);
                byte[] dilutionApplicationMessage = ByteUtils.concatenateByteArrays(dilutionApplicationMessageParts);
                try {
                    if (PrivacyUtils.verifySignature(dilutionApplicationMessage, publicKey, dilutionApplicationSignature)) {
                        log.add("  RECONSTRUCTED DILUTION APPLICATION WAS VALID");
                log.add("  Key origin: " + String.valueOf(keyOriginBlockIndex));
                if (blockchain.oldKeyIsStillValid(publicKey, keyOriginBlockIndex) && blockchain.oldKeyDepthIsOK(publicKey, keyOriginBlockIndex)) {
                    byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, Block.INDEX_SIZE + INVITE_PREFIX.length + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + POOL_IDENTIFIER_SIZE + 1 + dilutionApplicationSignature.length);
                    byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                    offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                    byte[] keyOriginBlockIndexOfPoolManagerBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                    long keyOriginBlockIndexOfPoolManager = ByteUtils.longFromByteArray(keyOriginBlockIndexOfPoolManagerBytes);
                    log.add("  Pool manager: " + Hex.toHexString(publicKeyOfPoolManager));
                    if (blockchain.oldKeyIsStillValid(publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager) && blockchain.oldKeyDepthIsOK(publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager)) {
                        byte[] signature = ByteUtils.readByteSubstringWithLengthEncoded(message, Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + dilutionApplicationSignature.length + POOL_IDENTIFIER_SIZE + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + INVITE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE);
                        try {
                            if (PrivacyUtils.verifySignature(messageToVerify, publicKeyOfPoolManager, signature)) {
                                log.add("  Signature was valid.");
                                return ValidationStatus.VALID;
                            } else {
                                log.add("  Signature wasn't longer valid.");
                                return ValidationStatus.INVALID;
                            }
                        } catch (NoSuchAlgorithmException exception) {
                            log.add("  NoSuchAlgorithmException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeyException exception) {
                            log.add("  InvalidKeyException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeySpecException exception) {
                            log.add("  InvalidKeySpecException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (SignatureException exception) {
                            log.add("  SignatureException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        }
                    } else {
                        log.add("  Pool manager key was no longer valid.");
                        return  ValidationStatus.INVALID;
                    }
                } else {
                    log.add("  Key was no longer valid.");
                    return  ValidationStatus.INVALID;
                }
                    } else {
                        log.add("  RECONSTRUCTED DILUTION APPLICATION WAS INVALID");
                        return ValidationStatus.INVALID;
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("  NoSuchAlgorithmException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (InvalidKeyException exception) {
                    log.add("  InvalidKeyException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (InvalidKeySpecException exception) {
                    log.add("  InvalidKeySpecException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (SignatureException exception) {
                    log.add("  SignatureException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                }
            }
        } else {
            log.add("  Failed initial validation check.");
            return  ValidationStatus.INVALID;
        }
    }

    public void sendPoolResponse(Blockchain blockchain, byte[] poolIdentifier, byte[] publicKeyOfPoolManager, long keyOriginBlockIndexOfPoolManager, byte[] dilutionApplicationSignature, byte[] inviteSignature, ParticipatingDilutionProcess participatingDilutionProcess) {
        log.add("sendPoolResponse()");
        log.add("  Manager: " + Hex.toHexString(blockchain.getManagerPublicKey()));
        log.add("  Election ID: " + Hex.toHexString(blockchain.getId()));
        log.add("  Pool manager: " + Hex.toHexString(publicKeyOfPoolManager));
        log.add("  Key origin: " + String.valueOf(keyOriginBlockIndexOfPoolManager));
        log.add("  Pool: " + Hex.toHexString(poolIdentifier));
        ArrayList<byte[]> messageToSignParts = new ArrayList<>();
        messageToSignParts.add(RESPONSE_PREFIX);
        messageToSignParts.add(blockchain.getManagerPublicKey());
        messageToSignParts.add(blockchain.getId());
        messageToSignParts.add(publicKeyOfPoolManager);
        messageToSignParts.add(ByteUtils.longToByteArray(keyOriginBlockIndexOfPoolManager));
        messageToSignParts.add(poolIdentifier);

        try {
            KeyPair poolKeyPair = PrivacyUtils.generateKeyPair();
            byte[] poolPublicKey = PrivacyUtils.getPublicKeyBytesFromKeyPair(poolKeyPair);
            byte[] poolPrivateKey = PrivacyUtils.getPrivateKeyBytesFromKeyPair(poolKeyPair);
            messageToSignParts.add(poolPublicKey);
            //store new public key with private key

            Signature signature = privacyLayer.getSignatureForPublicKey(participatingDilutionProcess.getPublicKeyForDilutionApplication());
            try {
                signature.update(poolPublicKey);
                messageToSignParts.add(ByteUtils.encodeWithLengthByte(signature.sign()));
                messageToSignParts.add(ByteUtils.encodeWithLengthByte(dilutionApplicationSignature));
                messageToSignParts.add(ByteUtils.encodeWithLengthByte(inviteSignature));
                byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
                signature = privacyLayer.getSignatureForPublicKey(participatingDilutionProcess.getPublicKeyForDilutionApplication());
                signature.update(messageToSign);
                byte[] signatureBytes = ByteUtils.encodeWithLengthByte(signature.sign());
                participatingDilutionProcess.setPoolPrivateKey(poolPrivateKey);
                ArrayList<byte[]> messageParts = new ArrayList<>();
                messageParts.add(messageToSign);
                messageParts.add(participatingDilutionProcess.getPublicKeyForDilutionApplication());
                messageParts.add(ByteUtils.longToByteArray(participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication()));
                messageParts.add(signatureBytes);
                networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
                log.add("  Sent pool response.");
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        } catch (InvalidAlgorithmParameterException exception) {
            log.add("  InvalidAlgorithmParameterException:");
            log.add("  " + exception.getMessage());
        } catch (NoSuchAlgorithmException exception) {
            log.add("  NoSuchAlgorithmException:");
            log.add("  " + exception.getMessage());
        }
    }

    public void processPoolResponse(byte[] message) {
        log.add("processPoolResponse()");
        int poolKeyOffset = RESPONSE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE;
        byte[] poolKey = ByteUtils.readByteSubstring(message, poolKeyOffset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        int poolKeySignatureOffset = poolKeyOffset + PrivacyUtils.PUBLIC_KEY_LENGTH;
        byte[] poolKeySignatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, poolKeySignatureOffset);
        int dilutionApplicationSignatureOffset = poolKeySignatureOffset + 1 + poolKeySignatureBytes.length;
        byte[] dilutionApplicationSignatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, dilutionApplicationSignatureOffset);
        int inviteSignatureOffset = dilutionApplicationSignatureOffset + 1 + dilutionApplicationSignatureBytes.length;
        byte[] inviteSignatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, inviteSignatureOffset);
        int offset = 0;
        byte[] messageToVerify = ByteUtils.readByteSubstring(message, offset, inviteSignatureOffset + 1 + inviteSignatureBytes.length);
        offset += messageToVerify.length;
        byte[] publicKeyOfResponder = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Responder: " + Hex.toHexString(publicKeyOfResponder));
        byte[] keyOriginOfResponder = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
        offset = RESPONSE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain == null) {
            log.add("  Blockchain was null.");
        } else if (managedDilutionProcesses.containsKey(blockchain)) {
            ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
            byte[] publicKeyOfResponsePoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Manager: " + Hex.toHexString(publicKeyOfResponsePoolManager));
            if (ByteUtils.byteArraysAreEqual(publicKeyOfResponsePoolManager, dilutionProcess.getPublicKeyAsPoolManager())) {
                offset += Block.INDEX_SIZE;
                byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
                log.add("  Pool: " + Hex.toHexString(poolIdentifier));
                if (ByteUtils.byteArraysAreEqual(poolIdentifier, dilutionProcess.getCurrentPoolIdentifier())) {
                    log.add("  Same pool.");
                    // count pool responses, then create
                    byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, messageToVerify.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE);
                    HashSet<PoolResponse> keysWithOrigin = dilutionProcess.getPoolResponses();
                    keysWithOrigin.add(new PoolResponse(publicKeyOfResponder, ByteUtils.intFromByteArray(keyOriginOfResponder), poolKey, poolKeySignatureBytes, dilutionApplicationSignatureBytes, inviteSignatureBytes, signatureBytes));
                    dilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                    dilutionProcess.setNameOfLastMessageReceived("some pool responses");
                    if (keysWithOrigin.size() + dilutionProcess.getSelfIncludingNumber() >= dilutionProcess.getDesiredPoolSize()) {
                        dilutionProcess.setNameOfLastMessageReceived("all pool responses");
                        sendPoolMessage(blockchain, dilutionProcess);
                        log.add("  Sent pool message.");
                    }
                } else {
                    log.add("  Different pool.");
                }
            }
        } else {
            log.add("  No managed dilution process for blockchain.");
        }
    }

    public ValidationStatus validatePoolResponse(byte[] message) {
        log.add("validatePoolResponse()");
        boolean isValid = message.length > RESPONSE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1;
        int i = 0;
        while (isValid && i < RESPONSE_PREFIX.length) {
            if (RESPONSE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int poolKeyOffset = RESPONSE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE;
            byte[] poolKey = ByteUtils.readByteSubstring(message, poolKeyOffset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            int poolKeySignatureOffset = poolKeyOffset + PrivacyUtils.PUBLIC_KEY_LENGTH;
            byte[] poolKeySignatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, poolKeySignatureOffset);
            int dilutionApplicationSignatureOffset = poolKeySignatureOffset + 1 + poolKeySignatureBytes.length;
            byte[] dilutionApplicationSignatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, dilutionApplicationSignatureOffset);
            int inviteSignatureOffset = dilutionApplicationSignatureOffset + 1 + dilutionApplicationSignatureBytes.length;
            byte[] inviteSignatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, inviteSignatureOffset);
            int offset = 0;
            byte[] messageToVerify = ByteUtils.readByteSubstring(message, offset, inviteSignatureOffset + 1 + inviteSignatureBytes.length);
            offset += messageToVerify.length;
            byte[] publicKeyOfResponder = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Responder: " + Hex.toHexString(publicKeyOfResponder));
            byte[] keyOriginOfResponderBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
            long keyOriginOfResponder = ByteUtils.longFromByteArray(keyOriginOfResponderBytes);
            log.add("  Key origin: " + String.valueOf(keyOriginOfResponder));
            byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, messageToVerify.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE);
            try {
                if (PrivacyUtils.verifySignature(messageToVerify, publicKeyOfResponder, signatureBytes)) {
                    log.add("  Signature on message was valid.");
                    if (PrivacyUtils.verifySignature(poolKey, publicKeyOfResponder, poolKeySignatureBytes)) {
                        log.add("  Signature on pool key was valid.");
                        offset = RESPONSE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH;
                        byte[] blockAssembler = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                        byte[] keyOriginOfAssemblerBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                        long keyOriginOfAssembler = ByteUtils.longFromByteArray(keyOriginOfAssemblerBytes);
                        offset += Block.INDEX_SIZE;
                        byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
                        offset = RESPONSE_PREFIX.length;
                        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
                        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
                        log.add("  Election ID: " + Hex.toHexString(chainId));
                        ArrayList<byte[]> dilutionApplicationMessageParts = new ArrayList<>();
                        dilutionApplicationMessageParts.add(DILUTION_APPLICATION_PREFIX);
                        dilutionApplicationMessageParts.add(electionManagerPublicKey);
                        dilutionApplicationMessageParts.add(chainId);
                        byte[] dilutionApplicationMessage = ByteUtils.concatenateByteArrays(dilutionApplicationMessageParts);
                        try {
                            if (PrivacyUtils.verifySignature(dilutionApplicationMessage, publicKeyOfResponder, dilutionApplicationSignatureBytes)) {
                                log.add("  RECONSTRUCTED DILUTION APPLICATION WAS VALID");

                                ArrayList<byte[]> inviteMessageParts = new ArrayList<>();
                                inviteMessageParts.add(INVITE_PREFIX);
                                inviteMessageParts.add(electionManagerPublicKey);
                                inviteMessageParts.add(chainId);
                                inviteMessageParts.add(publicKeyOfResponder);
                                inviteMessageParts.add(keyOriginOfResponderBytes);
                                inviteMessageParts.add(poolIdentifier);
                                inviteMessageParts.add(ByteUtils.encodeWithLengthByte(dilutionApplicationSignatureBytes));
                                byte[] inviteMessage = ByteUtils.concatenateByteArrays(inviteMessageParts);
                                try {
                                    if (PrivacyUtils.verifySignature(inviteMessage, blockAssembler, inviteSignatureBytes)) {
                                        log.add("  RECONSTRUCTED DILUTION APPLICATION WAS VALID");


                                        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);

                                        if (blockchain != null) {
                                            if (blockchain.oldKeyIsStillValid(publicKeyOfResponder, keyOriginOfResponder) && blockchain.oldKeyDepthIsOK(publicKeyOfResponder, keyOriginOfResponder) && blockchain.oldKeyIsStillValid(blockAssembler, keyOriginOfAssembler)) {
                                                log.add("  Pool response was valid.");
                                                return ValidationStatus.VALID;
                                            } else {
                                                log.add("  Public key of responder was no longer valid or at wrong depth.");
                                                return ValidationStatus.INVALID;
                                            }
                                        } else {
                                            log.add("  Blockchain was null.");
                                            return ValidationStatus.INVALID;
                                        }
                                    } else {
                                        log.add("  RECONSTRUCTED INVITE WAS INVALID");
                                        return ValidationStatus.INVALID;
                                    }
                                } catch (NoSuchAlgorithmException exception) {
                                    log.add("  NoSuchAlgorithmException:");
                                    log.add("  " + exception.getMessage());
                                    return ValidationStatus.INVALID;
                                } catch (InvalidKeyException exception) {
                                    log.add("  InvalidKeyException:");
                                    log.add("  " + exception.getMessage());
                                    return ValidationStatus.INVALID;
                                } catch (InvalidKeySpecException exception) {
                                    log.add("  InvalidKeySpecException:");
                                    log.add("  " + exception.getMessage());
                                    return ValidationStatus.INVALID;
                                } catch (SignatureException exception) {
                                    log.add("  SignatureException:");
                                    log.add("  " + exception.getMessage());
                                    return ValidationStatus.INVALID;
                                }
                            } else {
                                log.add("  RECONSTRUCTED DILUTION APPLICATION WAS INVALID");
                                return ValidationStatus.INVALID;
                            }
                        } catch (NoSuchAlgorithmException exception) {
                            log.add("  NoSuchAlgorithmException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeyException exception) {
                            log.add("  InvalidKeyException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeySpecException exception) {
                            log.add("  InvalidKeySpecException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (SignatureException exception) {
                            log.add("  SignatureException:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        }
                    } else {
                        log.add("  Signature on pool key was invalid.");
                        return ValidationStatus.INVALID;
                    }
                } else {
                    log.add("  Signature on message was invalid.");
                    return ValidationStatus.INVALID;
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
                return ValidationStatus.INVALID;
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add("  " + exception.getMessage());
                return ValidationStatus.INVALID;
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
                return ValidationStatus.INVALID;
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
                return ValidationStatus.INVALID;
            }
        } else {
            log.add("  Failed initial validation check.");
            return ValidationStatus.INVALID;
        }
    }

    public void sendPoolMessage(Blockchain blockchain, ManagedDilutionProcess dilutionProcess) {
        log.add("sendPoolMessage()");
        try {
                int selfIncludingNumber = 0;
                if (dilutionProcess.isIncludeSelf()) {
                    log.add("  Dilution process includes manager");
                    privacyLayer.createIdentity();
                    int identityIndex = privacyLayer.getNumberOfIdentities() - 1;
                    dilutionProcess.setOwnNewKey(privacyLayer.getIdentity(identityIndex).getPublicKey());
                    selfIncludingNumber = 1;
                } else {
                    log.add("  Dilution process doesn't include manager");
                }
                KeyPair sessionKeyPair = PrivacyUtils.generateKeyPair();
                byte[] sessionPublicKey = PrivacyUtils.getPublicKeyBytesFromKeyPair(sessionKeyPair);
                dilutionProcess.setSessionPublicKey(sessionPublicKey);
                byte[] sessionPrivateKey = PrivacyUtils.getPrivateKeyBytesFromKeyPair(sessionKeyPair);
                dilutionProcess.setSessionPrivateKey(sessionPrivateKey);
                HashSet<PoolResponse> poolResponses = dilutionProcess.getPoolResponses();
                ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
                unsignedMessageParts.add(POOL_MESSAGE_PREFIX);
                unsignedMessageParts.add(blockchain.getManagerPublicKey());
                log.add("  Manager: " + Hex.toHexString(blockchain.getManagerPublicKey()));
                unsignedMessageParts.add(blockchain.getId());
                log.add("  Election ID: " + Hex.toHexString(blockchain.getId()));
                unsignedMessageParts.add(dilutionProcess.getPublicKeyAsPoolManager());
                log.add("  Pool manager: " + Hex.toHexString(dilutionProcess.getPublicKeyAsPoolManager()));
                unsignedMessageParts.add(ByteUtils.longToByteArray(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                log.add("  Key origin: " + String.valueOf(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                unsignedMessageParts.add(dilutionProcess.getCurrentPoolIdentifier());
                log.add("  Pool: " + Hex.toHexString(dilutionProcess.getCurrentPoolIdentifier()));
                unsignedMessageParts.add(sessionPublicKey);
                unsignedMessageParts.add(new byte[]{(byte) (poolResponses.size() + selfIncludingNumber)});
                try {
                    log.add("  Session public key: " + Hex.toHexString(sessionPublicKey));
                    log.add("  Members:");
                    for (PoolResponse poolResponse : poolResponses) {
                        byte[] poolMemberPublicKey = poolResponse.getOldPublicKey();
                        unsignedMessageParts.add(poolMemberPublicKey);
                        unsignedMessageParts.add(ByteUtils.longToByteArray(poolResponse.getOldKeyOriginBlockIndex()));
                        unsignedMessageParts.add(poolResponse.getPoolPublicKey());
                        unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(poolResponse.getPoolKeySignature()));
                        unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(poolResponse.getDilutionApplicationSignature()));
                        unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(poolResponse.getInviteSignature()));
                        unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(poolResponse.getSignature()));
                        Cipher memberEncryptionCipher = PrivacyUtils.getPublicKeyEncryption(poolResponse.getPoolPublicKey());
                        byte[] encryptedSessionKey = ByteUtils.encodeWithLengthByte(memberEncryptionCipher.doFinal(sessionPrivateKey));
                        unsignedMessageParts.add(encryptedSessionKey);
                        log.add("    Public key: " + Hex.toHexString(poolMemberPublicKey));
                        log.add("    Key origin: " + String.valueOf(poolResponse.getOldKeyOriginBlockIndex()));
                        log.add("    Pool key: " + Hex.toHexString(poolResponse.getPoolPublicKey()));
                        log.add("    Encrypted session key: " + Hex.toHexString(encryptedSessionKey));
                    }
                    if (dilutionProcess.isIncludeSelf()) {
                        unsignedMessageParts.add(dilutionProcess.getPublicKeyAsPoolManager());
                        log.add("    Public key: " + Hex.toHexString(dilutionProcess.getPublicKeyAsPoolManager()));
                        unsignedMessageParts.add(ByteUtils.longToByteArray(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                        log.add("    Key origin: " + String.valueOf(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                        KeyPair poolKeyPair = PrivacyUtils.generateKeyPair();
                        byte[] poolPublicKey = PrivacyUtils.getPublicKeyBytesFromKeyPair(poolKeyPair);
                        unsignedMessageParts.add(poolPublicKey);
                        log.add("    Pool key: " + Hex.toHexString(poolPublicKey));
                        try {
                            Signature poolKeySignature = PrivacyUtils.createSignatureForSigning(dilutionProcess.getIdentityForPool().getKeyPair());
                            poolKeySignature.update(poolPublicKey);
                            byte[] poolKeySignatureBytes = ByteUtils.encodeWithLengthByte(poolKeySignature.sign());
                            unsignedMessageParts.add(poolKeySignatureBytes);

                            ArrayList<byte[]> dilutionApplicationMessageParts = new ArrayList<>();
                            dilutionApplicationMessageParts.add(DILUTION_APPLICATION_PREFIX);
                            dilutionApplicationMessageParts.add(blockchain.getManagerPublicKey());
                            dilutionApplicationMessageParts.add(blockchain.getId());


                            Signature signatureAsPoolManager = dilutionProcess.getSignatureAsPoolManager();
                            signatureAsPoolManager.update(ByteUtils.concatenateByteArrays(dilutionApplicationMessageParts));
                            byte[] dilutionApplicationMessageSignature = ByteUtils.encodeWithLengthByte(signatureAsPoolManager.sign());

                            unsignedMessageParts.add(dilutionApplicationMessageSignature);

                            ArrayList<byte[]> inviteMessageParts = new ArrayList<>();
                            inviteMessageParts.add(INVITE_PREFIX);
                            inviteMessageParts.add(blockchain.getManagerPublicKey());
                            inviteMessageParts.add(blockchain.getId());
                            inviteMessageParts.add(dilutionProcess.getPublicKeyAsPoolManager());
                            inviteMessageParts.add(ByteUtils.longToByteArray(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                            inviteMessageParts.add(dilutionProcess.getCurrentPoolIdentifier());
                            inviteMessageParts.add(dilutionApplicationMessageSignature);

                            signatureAsPoolManager = dilutionProcess.getSignatureAsPoolManager();
                            signatureAsPoolManager.update(ByteUtils.concatenateByteArrays(inviteMessageParts));
                            byte[] inviteMessageSignature = ByteUtils.encodeWithLengthByte(signatureAsPoolManager.sign());

                            unsignedMessageParts.add(inviteMessageSignature);

                            ArrayList<byte[]> responseMessageParts = new ArrayList<>();
                            responseMessageParts.add(RESPONSE_PREFIX);
                            responseMessageParts.add(blockchain.getManagerPublicKey());
                            responseMessageParts.add(blockchain.getId());
                            responseMessageParts.add(dilutionProcess.getPublicKeyAsPoolManager());
                            responseMessageParts.add(ByteUtils.longToByteArray(dilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                            responseMessageParts.add(dilutionProcess.getCurrentPoolIdentifier());
                            responseMessageParts.add(poolPublicKey);
                            responseMessageParts.add(poolKeySignatureBytes);
                            responseMessageParts.add(dilutionApplicationMessageSignature);
                            responseMessageParts.add(inviteMessageSignature);

                            signatureAsPoolManager = dilutionProcess.getSignatureAsPoolManager();
                            signatureAsPoolManager.update(ByteUtils.concatenateByteArrays(responseMessageParts));

                            unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(signatureAsPoolManager.sign()));

                            Cipher memberEncryptionCipher = PrivacyUtils.getPublicKeyEncryption(poolPublicKey);
                            byte[] encryptedSessionKey = ByteUtils.encodeWithLengthByte(memberEncryptionCipher.doFinal(sessionPrivateKey));
                            unsignedMessageParts.add(encryptedSessionKey);
                            log.add("    Encrypted session key: " + Hex.toHexString(encryptedSessionKey));
                        } catch (InvalidKeyException exception) {
                            log.add("  InvalidKeyException:");
                            log.add("  " + exception.getMessage());
                        }
                    }
                    byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
                    Signature signatureAsPoolManager = dilutionProcess.getSignatureAsPoolManager();
                    signatureAsPoolManager.update(unsignedMessage);
                    byte[] signatureBytes = ByteUtils.encodeWithLengthByte(signatureAsPoolManager.sign());
                    ArrayList<byte[]> messageParts = new ArrayList<>();
                    messageParts.add(unsignedMessage);
                    messageParts.add(signatureBytes);
                    dilutionProcess.setReceivedNewKeys(new HashSet<>());
                    byte [] poolMessage = ByteUtils.concatenateByteArrays(messageParts);
                    dilutionProcess.setPoolMessage(poolMessage);
                    networkLayer.broadcastAnonymously(poolMessage);
                    log.add("  Sent pool message.");
                    if (dilutionProcess.isIncludeSelf()) {
                        sendPoolReceipt(blockchain, poolMessage, poolResponses.size(), dilutionProcess);
                    }
                } catch (IllegalBlockSizeException exception) {
                    log.add("  IllegalBlockSizeException:");
                    log.add("  " + exception.getMessage());
                } catch (BadPaddingException exception) {
                    log.add("  BadPaddingException:");
                    log.add("  " + exception.getMessage());
                } catch (SignatureException exception) {
                    log.add("  SignatureException:");
                    log.add("  " + exception.getMessage());
                }
        } catch(InvalidAlgorithmParameterException exception) {
            log.add("  InvalidAlgorithmParameterException:");
            log.add("  " + exception.getMessage());
        } catch(NoSuchAlgorithmException exception) {
            log.add("  NoSuchAlgorithmException:");
            log.add("  " + exception.getMessage());
        }
    }

    public void processPoolMessage(byte[] message) {
        log.add("processPoolMessage()");
        int offset = POOL_MESSAGE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain != null) {
            byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Pool manager: " + Hex.toHexString(publicKeyOfPoolManager));
            byte[] keyOriginBlockIndexOfPoolManagerBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
            offset += Block.INDEX_SIZE;
            long keyOriginBlockIndexOfPoolManager = ByteUtils.longFromByteArray(keyOriginBlockIndexOfPoolManagerBytes);
            log.add("  Key origin: " + String.valueOf(keyOriginBlockIndexOfPoolManager));
            byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
            offset += POOL_IDENTIFIER_SIZE;
            log.add("  Pool: " + Hex.toHexString(poolIdentifier));

            byte[] sessionPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Session public key: " + Hex.toHexString(sessionPublicKey));
            int poolSize = message[offset];
            if (poolSize < 0) {
                poolSize += 256;
            }
            offset = POOL_MESSAGE_PREFIX.length + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + 1;
            boolean poolMembersAreValid = true;
            ArrayList<KeyWithOrigin> poolMemberPublicKeys = new ArrayList<>();
            ArrayList<byte[]> poolMemberEncryptedMessages = new ArrayList<>();
            log.add("  Members:");
            int k = 0;
            while (poolMembersAreValid && k < poolSize) {
                byte[] poolMemberPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                if (poolMemberPublicKey == null) {
                    poolMembersAreValid = false;
                } else {
                    offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                    log.add("    Public key: " + Hex.toHexString(poolMemberPublicKey));
                    byte[] poolMemberKeyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                    if (poolMemberKeyOriginBlockIndexBytes == null) {
                        poolMembersAreValid = false;
                    } else {
                        long poolMemberKeyOriginBlockIndex = ByteUtils.longFromByteArray(poolMemberKeyOriginBlockIndexBytes);
                        offset += Block.INDEX_SIZE;
                        log.add("    Key origin: " + String.valueOf(poolMemberKeyOriginBlockIndex));
                        poolMemberPublicKeys.add(new KeyWithOrigin(poolMemberPublicKey, poolMemberKeyOriginBlockIndex));
                        byte[] poolKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                        log.add("    Pool key: " + Hex.toHexString(poolKey));
                        byte[] poolKeySignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                        offset += 1 + poolKeySignature.length;

                        byte[] dilutionApplicationSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                        offset += 1 + dilutionApplicationSignature.length;
                        byte[] inviteSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                        offset += 1 + inviteSignature.length;
                        byte[] poolResponseSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                        offset += 1 + poolResponseSignature.length;

                        byte[] encryptedMessage = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                        if (encryptedMessage == null) {
                            poolMembersAreValid = false;
                        } else {
                            log.add("    Encrypted session key: " + Hex.toHexString(encryptedMessage));
                            poolMemberEncryptedMessages.add(encryptedMessage);
                            offset += 1 + encryptedMessage.length;
                            k++;
                        }
                    }
                }
            }
            if (poolMembersAreValid) {
                log.add("  Pool members were all valid.");
                try {
                    int i = 0;
                    if (dilutionProcesses.containsKey(blockchain)) {
                        ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
                        if (ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getCurrentPoolIdentifier(), poolIdentifier) && ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getPublicKeyOfPoolManager(), publicKeyOfPoolManager)) {
                            log.add("  Participating in dilution process for this blockchain.");
                            while (i < poolMemberPublicKeys.size() && (participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication() != poolMemberPublicKeys.get(i).getKeyOriginBlockIndex() || !ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getPublicKeyForDilutionApplication(), poolMemberPublicKeys.get(i).getPublicKey()))) {
                                i++;
                            }
                            if (i < poolMemberPublicKeys.size() && participatingDilutionProcess.getPoolPrivateKey() != null) {
                                log.add("  Pool message was for this process.");
                                Cipher poolPrivateKeyDecryption = PrivacyUtils.getPrivateKeyDecryption(PrivacyUtils.getPrivateKeyFromBytes(participatingDilutionProcess.getPoolPrivateKey()));
                                boolean sessionKeyIsValid = true;
                                byte[] sessionPrivateKey = null;
                                try {
                                    sessionPrivateKey = poolPrivateKeyDecryption.doFinal(poolMemberEncryptedMessages.get(i));
                                    participatingDilutionProcess.setSessionPublicKey(sessionPublicKey);
                                    participatingDilutionProcess.setSessionPrivateKey(sessionPrivateKey);
                                    participatingDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                                    if (PrivacyUtils.isValidKeyPair(sessionPublicKey, sessionPrivateKey)) {
                                        log.add("  Session key pair was valid.");
                                        participatingDilutionProcess.setPoolMessage(message);
                                        sendPoolReceipt(blockchain, message, i, participatingDilutionProcess);
                                    } else {
                                        log.add("  Session key pair was invalid.");
                                        sessionKeyIsValid = false;
                                    }
                                } catch (IllegalBlockSizeException exception) {
                                    log.add("  IllegalBlockSizeException:");
                                    log.add("  " + exception.getMessage());
                                    sessionKeyIsValid = false;
                                } catch (BadPaddingException exception) {
                                    log.add("  BadPaddingException:");
                                    log.add("  " + exception.getMessage());
                                    sessionKeyIsValid = false;
                                }
                                if (!sessionKeyIsValid) {
                                    sendBlameMessage(blockchain, message, i);
                                }
                            }
                        }
                    }
                    if (!dilutionProcesses.containsKey(blockchain) || i >= poolMemberPublicKeys.size()) {
                        if (backgroundDilutionProcesses.containsKey(blockchain)) {
                            log.add("  Background dilution process");
                            BackgroundDilutionProcessCircle backgroundDilutionProcessCircle = backgroundDilutionProcesses.get(blockchain);
                            if (backgroundDilutionProcessCircle.contains(publicKeyOfPoolManager, poolIdentifier)) {
                                log.add("  Pool already existed in background.");
                            }
                            backgroundDilutionProcessCircle.add(new BackgroundDilutionProcess(new KeyWithOrigin(publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager), sessionPublicKey, poolIdentifier, poolMemberPublicKeys));
                        }
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("  NoSuchAlgorithmException:");
                    log.add("  " + exception.getMessage());
                } catch (InvalidKeySpecException exception) {
                    log.add("  InvalidKeySpecException:");
                    log.add("  " + exception.getMessage());
                }
            } else {
                log.add("  The pool size is larger than the data in the pool message.");
            }
        }
    }

    public ValidationStatus validatePoolMessage(byte[] message) {
        log.add("validatePoolMessage()");
        boolean isValid = message.length > POOL_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + 1;
        int i = 0;
        while (isValid && i < POOL_MESSAGE_PREFIX.length) {
            if (POOL_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = POOL_MESSAGE_PREFIX.length;
            byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
            offset += Election.ID_LENGTH;
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                byte[] keyOriginBlockIndexOfPoolManagerBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                offset += Block.INDEX_SIZE;
                long keyOriginBlockIndexOfPoolManager = ByteUtils.longFromByteArray(keyOriginBlockIndexOfPoolManagerBytes);
                byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
                offset += POOL_IDENTIFIER_SIZE;
                if (blockchain.oldKeyIsStillValid(publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager)) {
                    offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                    int poolSize = message[offset];
                    if (poolSize < 0) {
                        poolSize += 256;
                    }
                    if (message.length > POOL_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + 1 + poolSize * (PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + 1)) {
                        offset = POOL_MESSAGE_PREFIX.length + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + 1;
                        boolean poolMembersAreValid = true;
                        boolean thereIsAMemberWithHigherDepth = false;
                        boolean thereIsAMemberWithLowerDepth = false;
                        ArrayList<KeyWithOrigin> poolMemberPublicKeys = new ArrayList<>();
                        int k = 0;
                        while (poolMembersAreValid && k < poolSize) {
                            byte[] poolMemberPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                            if (poolMemberPublicKey == null) {
                                log.add("  poolMemberPublicKey == null");
                                poolMembersAreValid = false;
                            } else {
                                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                                byte[] poolMemberKeyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                                if (poolMemberKeyOriginBlockIndexBytes == null) {
                                    log.add("  poolMemberKeyOriginBlockIndexBytes == null");
                                    poolMembersAreValid = false;
                                } else {
                                    long poolMemberKeyOriginBlockIndex = ByteUtils.longFromByteArray(poolMemberKeyOriginBlockIndexBytes);
                                    offset += Block.INDEX_SIZE;
                                    if (blockchain.oldKeyIsStillValid(poolMemberPublicKey, poolMemberKeyOriginBlockIndex)) {
                                        if (!blockchain.oldKeyDepthIsOK(poolMemberPublicKey, poolMemberKeyOriginBlockIndex)) {
                                            thereIsAMemberWithHigherDepth = true;
                                        } else if (blockchain.oldKeyDepthIsLower(poolMemberPublicKey, poolMemberKeyOriginBlockIndex)) {
                                            thereIsAMemberWithLowerDepth = true;
                                        }
                                        poolMemberPublicKeys.add(new KeyWithOrigin(poolMemberPublicKey, poolMemberKeyOriginBlockIndex));
                                        byte[] poolKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                                        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                                        byte[] poolKeySignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                                        offset += 1 + poolKeySignature.length;

                                        byte[] dilutionApplicationSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                                        offset += 1 + dilutionApplicationSignature.length;

                                        ArrayList<byte[]> dilutionApplicationMessageParts = new ArrayList<>();
                                        dilutionApplicationMessageParts.add(DILUTION_APPLICATION_PREFIX);
                                        dilutionApplicationMessageParts.add(electionManagerPublicKey);
                                        dilutionApplicationMessageParts.add(chainId);
                                        byte[] dilutionApplicationMessage = ByteUtils.concatenateByteArrays(dilutionApplicationMessageParts);
                                        try {
                                            if (PrivacyUtils.verifySignature(dilutionApplicationMessage, poolMemberPublicKey, dilutionApplicationSignature)) {
                                                log.add("  RECONSTRUCTED DILUTION APPLICATION WAS VALID");
                                            } else {
                                                log.add("  RECONSTRUCTED DILUTION APPLICATION WAS INVALID");
                                                poolMembersAreValid = false;
                                            }
                                        } catch (NoSuchAlgorithmException exception) {
                                            log.add("  NoSuchAlgorithmException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (InvalidKeyException exception) {
                                            log.add("  InvalidKeyException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (InvalidKeySpecException exception) {
                                            log.add("  InvalidKeySpecException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (SignatureException exception) {
                                            log.add("  SignatureException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        }

                                        byte[] inviteSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                                        offset += 1 + inviteSignature.length;

                                        ArrayList<byte[]> inviteMessageParts = new ArrayList<>();
                                        inviteMessageParts.add(INVITE_PREFIX);
                                        inviteMessageParts.add(electionManagerPublicKey);
                                        inviteMessageParts.add(chainId);
                                        inviteMessageParts.add(poolMemberPublicKey);
                                        inviteMessageParts.add(poolMemberKeyOriginBlockIndexBytes);
                                        inviteMessageParts.add(poolIdentifier);
                                        inviteMessageParts.add(ByteUtils.encodeWithLengthByte(dilutionApplicationSignature));
                                        byte[] inviteMessage = ByteUtils.concatenateByteArrays(inviteMessageParts);
                                        try {
                                            if (PrivacyUtils.verifySignature(inviteMessage, publicKeyOfPoolManager, inviteSignature)) {
                                                log.add("  RECONSTRUCTED INVITE WAS VALID");
                                            } else {
                                                log.add("  RECONSTRUCTED INVITE WAS INVALID");
                                                poolMembersAreValid = false;
                                            }
                                        } catch (NoSuchAlgorithmException exception) {
                                            log.add("  NoSuchAlgorithmException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (InvalidKeyException exception) {
                                            log.add("  InvalidKeyException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (InvalidKeySpecException exception) {
                                            log.add("  InvalidKeySpecException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (SignatureException exception) {
                                            log.add("  SignatureException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        }

                                        byte[] poolResponseSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                                        offset += 1 + poolResponseSignature.length;

                                        ArrayList<byte[]> poolResponseMessageParts = new ArrayList<>();
                                        poolResponseMessageParts.add(RESPONSE_PREFIX);
                                        poolResponseMessageParts.add(electionManagerPublicKey);
                                        poolResponseMessageParts.add(chainId);
                                        poolResponseMessageParts.add(publicKeyOfPoolManager);
                                        poolResponseMessageParts.add(keyOriginBlockIndexOfPoolManagerBytes);
                                        poolResponseMessageParts.add(poolIdentifier);
                                        poolResponseMessageParts.add(poolKey);
                                        poolResponseMessageParts.add(ByteUtils.encodeWithLengthByte(poolKeySignature));
                                        poolResponseMessageParts.add(ByteUtils.encodeWithLengthByte(dilutionApplicationSignature));
                                        poolResponseMessageParts.add(ByteUtils.encodeWithLengthByte(inviteSignature));

                                        byte[] poolResponseMessage = ByteUtils.concatenateByteArrays(poolResponseMessageParts);
                                        try {
                                            if (PrivacyUtils.verifySignature(poolResponseMessage, poolMemberPublicKey, poolResponseSignature)) {
                                                log.add("  RECONSTRUCTED POOL RESPONSE WAS VALID");
                                            } else {
                                                log.add("  RECONSTRUCTED POOL RESPONSE WAS INVALID");
                                                poolMembersAreValid = false;
                                            }
                                        } catch (NoSuchAlgorithmException exception) {
                                            log.add("  NoSuchAlgorithmException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (InvalidKeyException exception) {
                                            log.add("  InvalidKeyException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (InvalidKeySpecException exception) {
                                            log.add("  InvalidKeySpecException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        } catch (SignatureException exception) {
                                            log.add("  SignatureException:");
                                            log.add("  " + exception.getMessage());
                                            poolMembersAreValid = false;
                                        }

                                        byte[] encryptedMessage = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                                        if (encryptedMessage == null) {
                                            poolMembersAreValid = false;
                                        } else {
                                            offset += 1 + encryptedMessage.length;
                                            k++;
                                        }
                                    } else {
                                        poolMembersAreValid = false;
                                    }
                                }
                            }
                        }
                        if (poolMembersAreValid && thereIsAMemberWithHigherDepth) {
                            if (blockchain.isInPreDepthPhase()) {
                                poolMembersAreValid = thereIsAMemberWithLowerDepth;
                            } else {
                                poolMembersAreValid = false;
                            }
                        }
                        if (poolMembersAreValid) {
                            byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, offset);
                            byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                            try {
                                if (PrivacyUtils.verifySignature(messageToVerify, publicKeyOfPoolManager, signatureBytes)) {
                                    for (KeyWithOrigin poolMember: poolMemberPublicKeys) {
                                        blameCircle.add(poolMember, new KeyWithOrigin(publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager), poolIdentifier);
                                    }
                                    log.add("  Pool message was valid.");
                                    return ValidationStatus.VALID;
                                } else {
                                    log.add("  Signature of pool message was invalid.");
                                    return ValidationStatus.INVALID;
                                }
                            } catch (NoSuchAlgorithmException exception) {
                                log.add("  NoSuchAlgorithmException:");
                                log.add("  " + exception.getMessage());
                                return ValidationStatus.INVALID;
                            } catch (InvalidKeyException exception) {
                                log.add("  InvalidKeyException:");
                                log.add("  " + exception.getMessage());
                                return ValidationStatus.INVALID;
                            } catch (InvalidKeySpecException exception) {
                                log.add("  InvalidKeySpecException:");
                                log.add("  " + exception.getMessage());
                                return ValidationStatus.INVALID;
                            } catch (SignatureException exception) {
                                log.add("  SignatureException:");
                                log.add("  " + exception.getMessage());
                                return ValidationStatus.INVALID;
                            }
                        } else {
                            log.add("  Not all pool members were valid.");
                            return ValidationStatus.INVALID;
                        }
                    } else {
                        log.add("  Pool message was too short.");
                        return ValidationStatus.INVALID;
                    }
                } else {
                    log.add("  Pool manager was no longer valid.");
                    return ValidationStatus.INVALID;
                }
            } else {
                log.add("  Blockchain was null.");
                return ValidationStatus.INVALID;
            }
        } else {
            log.add("  Failed initial validation check.");
            return ValidationStatus.INVALID;
        }
    }

    public void sendNewKeyMessage(Blockchain blockchain, DilutionProcess dilutionProcess) {
        if (dilutionProcess != null) {
            if (dilutionProcess.getOwnNewKey() == null) {
                privacyLayer.createIdentity();
                int newIdentityIndex = privacyLayer.getNumberOfIdentities() - 1;
                dilutionProcess.setOwnNewKey(privacyLayer.getPublicKey(newIdentityIndex));
            }
            ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
            unsignedMessageParts.add(NEW_KEY_MESSAGE_PREFIX);
            unsignedMessageParts.add(dilutionProcess.getOwnNewKey());
            unsignedMessageParts.add(dilutionProcess.getPoolMessage());
            byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
            try {
                byte[] signatureBytes = PrivacyUtils.sign(unsignedMessage, dilutionProcess.getSessionPrivateKey());
                ArrayList<byte[]> messageParts = new ArrayList<>();
                messageParts.add(unsignedMessage);
                messageParts.add(ByteUtils.encodeWithLengthByte(signatureBytes));
                networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
            } catch (InvalidKeySpecException exception) {
                log.add("Couldn't send new key message due to InvalidKeySpecException");
            } catch (InvalidKeyException exception) {
                log.add("Couldn't send new key message due to InvalidKeyException");
            } catch (NoSuchAlgorithmException exception) {
                log.add("Couldn't send new key message due to NoSuchAlgorithmException");
            } catch (SignatureException exception) {
                log.add("Couldn't send new key message due to SignatureException");
            }

        }
    }

    public void processNewKeyMessage(byte[] message) {
        log.add("  processNewKeyMessage()");
        int offset = NEW_KEY_MESSAGE_PREFIX.length;
        byte[] newKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH + POOL_MESSAGE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        byte[] poolManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE;
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain != null && managedDilutionProcesses.containsKey(blockchain)) {
            ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
            byte[] poolIdentifierOfNewKeyMessage = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
            if (dilutionProcess.getCurrentPoolIdentifier() != null && ByteUtils.byteArraysAreEqual(dilutionProcess.getCurrentPoolIdentifier(), poolIdentifierOfNewKeyMessage) && ByteUtils.byteArraysAreEqual(dilutionProcess.getPublicKeyAsPoolManager(), poolManagerPublicKey)) {
                int numberOfNewKeys = dilutionProcess.getReceivedNewKeys().size();
                if (dilutionProcess.isIncludeSelf()) {
                    numberOfNewKeys++;
                }
                HashSet<byte[]> newKeys = dilutionProcess.getReceivedNewKeys();
                if (newKeys.stream().filter(key -> ByteUtils.byteArraysAreEqual(key, newKey)).count() == 0) {
                    newKeys.add(newKey);
                    dilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                    dilutionProcess.setNameOfLastMessageReceived("some new key messages");
                    if (numberOfNewKeys == dilutionProcess.getPoolResponses().size()) {
                        dilutionProcess.setNameOfLastMessageReceived("all new key messages");
                        createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 7);
                    }
                }
            }
        }
    }

    public ValidationStatus validateNewKeyMessage(byte[] message) {
        log.add("validateNewKeyMessage()");
        boolean isValid = message.length > NEW_KEY_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1;
        int i = 0;
        while (isValid && i < NEW_KEY_MESSAGE_PREFIX.length) {
            if (NEW_KEY_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = NEW_KEY_MESSAGE_PREFIX.length;
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;

            offset += POOL_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH;
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE;
            byte[] sessionPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            int numberOfMembers = message[offset];
            if (numberOfMembers < 0) {
                numberOfMembers += 256;
            }
            offset++;
            for (i = 0; i < numberOfMembers; i++) {
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH;
                byte[] signature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + signature.length;
                signature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + signature.length;
                signature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + signature.length;
                signature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + signature.length;
                byte[] encryptedSessionKey = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + encryptedSessionKey.length;
            }
            byte[] poolMessageSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            offset += 1 + poolMessageSignature.length;
            int poolMessageLength = offset - NEW_KEY_MESSAGE_PREFIX.length - PrivacyUtils.PUBLIC_KEY_LENGTH;
            byte[] newKeyMessageSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            offset = NEW_KEY_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH;

            byte[] poolMessage = ByteUtils.readByteSubstring(message, offset, poolMessageLength);
            if (validatePoolMessage(poolMessage) == ValidationStatus.VALID) {
                byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, offset + poolMessageLength);
                log.add("  Internal pool message was valid.");
                try {
                    if (PrivacyUtils.verifySignature(messageToVerify, sessionPublicKey, newKeyMessageSignature)) {
                        return ValidationStatus.VALID;
                    } else {
                        log.add("  Signature was invalid.");
                        return ValidationStatus.INVALID;
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("  NoSuchAlgorithmException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (InvalidKeySpecException exception) {
                    log.add("  InvalidKeySpecException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (InvalidKeyException exception) {
                    log.add(" InvalidKeyException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                } catch (SignatureException exception) {
                    log.add("  SignatureException:");
                    log.add("  " + exception.getMessage());
                    return ValidationStatus.INVALID;
                }
            } else {
                log.add("  Internal pool message was invalid.");
                return ValidationStatus.INVALID;
            }
        } else {
            log.add("  Failed initial validation check.");
            return ValidationStatus.INVALID;
        }
    }

    public void createUnvalidatedDilutionBlock(Blockchain blockchain, ManagedDilutionProcess dilutionProcess, int callTag) {
        log.add("createUnvalidatedDilutionBlock()");
        log.add("  Manager: " + Hex.toHexString(blockchain.getManagerPublicKey()));
        log.add("  Chain: " + Hex.toHexString(blockchain.getId()));
        if (dilutionProcess != null) {
            ArrayList<byte[]> oldKeys = new ArrayList<>();
            ArrayList<Long> keyOriginBlockIndices = new ArrayList<>();
            log.add("  Members:");
            for (PoolResponse poolResponse : dilutionProcess.getPoolResponses()) {
                oldKeys.add(poolResponse.getOldPublicKey());
                keyOriginBlockIndices.add(poolResponse.getOldKeyOriginBlockIndex());
                log.add("    Public key: " + Hex.toHexString(poolResponse.getOldPublicKey()));
                log.add("    Key origin: " + String.valueOf(poolResponse.getOldKeyOriginBlockIndex()));
            }
            if (dilutionProcess.isIncludeSelf()) {
                log.add("  Manager is included in pool.");
                oldKeys.add(dilutionProcess.getPublicKeyAsPoolManager());
                keyOriginBlockIndices.add(dilutionProcess.getKeyOriginBlockIndexAsPoolManager());
            }
            ArrayList<byte[]> newKeys = new ArrayList<>();
            SecureRandom secureRandom = new SecureRandom();
            int indexOfSelf = secureRandom.nextInt() % (dilutionProcess.getReceivedNewKeys().size() + 1);
            while (indexOfSelf < 0) {
                indexOfSelf += dilutionProcess.getReceivedNewKeys().size() + 1;
            }
            int indexInNewKeys = 0;
            boolean addedOwnKey = false;
            for (byte[] newKey : dilutionProcess.getReceivedNewKeys()) {
                if (indexInNewKeys + dilutionProcess.getSelfIncludingNumber() < oldKeys.size()) {
                    if (dilutionProcess.isIncludeSelf() && indexInNewKeys == indexOfSelf && !addedOwnKey) {
                        newKeys.add(dilutionProcess.getOwnNewKey());
                        addedOwnKey = true;
                    }
                    newKeys.add(newKey);
                    indexInNewKeys++;
                }
            }
            if (dilutionProcess.isIncludeSelf() && !addedOwnKey) {
                newKeys.add(dilutionProcess.getOwnNewKey());
            }
            DilutionBlock unvalidatedDilutionBlock = dilutionBlockFactory.createUnvalidatedBlock(blockchain, oldKeys, keyOriginBlockIndices, newKeys, dilutionProcess.getCurrentPoolIdentifier(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager());
            if (unvalidatedDilutionBlock != null) {
                if (dilutionProcess.isIncludeSelf()) {
                    Signature signature = privacyLayer.getSignatureForPublicKey(dilutionProcess.getPublicKeyAsPoolManager());
                    try {
                        signature.update(unvalidatedDilutionBlock.getBytesWithoutValidation());
                        byte[] signatureBytes = signature.sign();
                        unvalidatedDilutionBlock.addSignature(dilutionProcess.getPublicKeyAsPoolManager(), signatureBytes);
                    } catch (SignatureException exception) {
                        log.add("  SignatureException when adding own signature to the unvalidated dilution block:");
                        log.add("  " + exception.getMessage());
                    }
                }
                dilutionProcess.setUnvalidatedDilutionBlock(unvalidatedDilutionBlock);
                Signature signature = privacyLayer.getSignatureForPublicKey(dilutionProcess.getPublicKeyAsPoolManager());
                ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
                unsignedMessageParts.add(UNVALIDATED_DILUTION_BLOCK_PREFIX);
                unsignedMessageParts.add(unvalidatedDilutionBlock.getBytesWithoutValidation());
                byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
                try {
                    ArrayList<byte[]> messageParts = new ArrayList<>();
                    messageParts.add(unsignedMessage);
                    signature.update(unsignedMessage);
                    byte[] signatureBytes = signature.sign();
                    messageParts.add(ByteUtils.encodeWithLengthByte(signatureBytes));
                    networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
                } catch (SignatureException exception) {
                    log.add("  SignatureException when signing unvalidated dilution block:");
                    log.add("  " + exception.getMessage());
                }
            }
        }
    }

    public void processUnvalidatedDilutionBlock(byte[] message) {
        log.add("processUnvalidatedDilutionBlock()");
        int offset = UNVALIDATED_DILUTION_BLOCK_PREFIX.length;
        byte[] unvalidatedBlockBytes = ByteUtils.readByteSubstring(message, offset, message.length - UNVALIDATED_DILUTION_BLOCK_PREFIX.length);
        offset = dilutionBlockFactory.getPrefix().length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH + Block.INDEX_SIZE + 8 + PrivacyUtils.HASH_LENGTH;
        log.add("  Chain: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        byte[] poolIdentifierOfNewKeyMessage = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, POOL_IDENTIFIER_SIZE);
        log.add("  Pool: " + Hex.toHexString(poolIdentifierOfNewKeyMessage));
        if (blockchain != null) {
            log.add("  Found blockchain.");
            ParticipatingDilutionProcess participatingDilutionProcess = null;
            if (dilutionProcesses.containsKey(blockchain)) {
                participatingDilutionProcess = dilutionProcesses.get(blockchain);
            }
            boolean foundRightDilutionProcess = false;
            if (participatingDilutionProcess != null && participatingDilutionProcess.getCurrentPoolIdentifier() != null && ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getCurrentPoolIdentifier(), poolIdentifierOfNewKeyMessage)) {
                foundRightDilutionProcess = true;
                log.add("  Found uncompleted dilution process.");
            } else {
                if (completedDilutionProcesses.containsKey(blockchain)) {
                    for (ParticipatingDilutionProcess completedParticipatingDilutionProcess : completedDilutionProcesses.get(blockchain)) {
                        if (completedParticipatingDilutionProcess.getCurrentPoolIdentifier() != null && ByteUtils.byteArraysAreEqual(completedParticipatingDilutionProcess.getCurrentPoolIdentifier(), poolIdentifierOfNewKeyMessage)) {
                            foundRightDilutionProcess = true;
                            participatingDilutionProcess = completedParticipatingDilutionProcess;
                            log.add("  Found completed dilution process.");
                        }
                    }
                }
            }
            if (foundRightDilutionProcess) {
                log.add("  Found dilution process for pool identifier.");
                // check for old key, find index
                    byte[] anonymitySet = dilutionBlockFactory.getAnonymitySet(unvalidatedBlockBytes);
                    int numberOfKeys = dilutionBlockFactory.getNumberOfKeys(unvalidatedBlockBytes);
                    List<byte[]> oldKeys = dilutionBlockFactory.getOldKeys(unvalidatedBlockBytes, numberOfKeys);
                    List<Long> keyOriginBlockIndices = dilutionBlockFactory.getKeyOriginBlockIndices(unvalidatedBlockBytes, numberOfKeys);
                    int indexInOldKeys = -1;
                    for (int j = 0; j < oldKeys.size(); j++) {
                        if (keyOriginBlockIndices.get(j).intValue() == participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication() && ByteUtils.byteArraysAreEqual(oldKeys.get(j), participatingDilutionProcess.getPublicKeyForDilutionApplication())) {
                            indexInOldKeys = j;
                        }
                    }
                    if (indexInOldKeys != -1) {
                        log.add("  Found old key at index: " + indexInOldKeys);
                        byte[] correctAnonymitySet = null;
                        for (int j = 0; j < oldKeys.size(); j++) {
                            Long keyOriginBlockIndex = keyOriginBlockIndices.get(j);
                            Block originBlock = blockchain.getBlock(keyOriginBlockIndex);
                            byte[] otherAnonymitySet = oldKeys.get(j);
                            if (originBlock.getPrefix()[0] == 'D') {
                                DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                                otherAnonymitySet = originDilutionBlock.getAnonymitySet();
                            }
                            if (correctAnonymitySet == null || ByteUtils.compareByteArrays(correctAnonymitySet, otherAnonymitySet) > 0) {
                                correctAnonymitySet = otherAnonymitySet;
                            }
                        }
                        if (ByteUtils.byteArraysAreEqual(correctAnonymitySet, anonymitySet)) {
                            log.add("  The anonymity set is correct.");
                            List<byte[]> newKeys = dilutionBlockFactory.getNewKeys(unvalidatedBlockBytes, numberOfKeys);
                            boolean foundNewKey = false;
                            int i = 0;
                            while (!foundNewKey && i < newKeys.size()) {
                                if (ByteUtils.byteArraysAreEqual(newKeys.get(i), participatingDilutionProcess.getOwnNewKey())) {
                                    foundNewKey = true;
                                }
                                i++;
                            }
                            if (foundNewKey) {
                                log.add("  Found new key.");
                                int unvalidatedBlockLength = dilutionBlockFactory.getSuccesBlockHeaderLength() + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + (PrivacyUtils.PUBLIC_KEY_LENGTH * 2 + Block.INDEX_SIZE) * numberOfKeys + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
                                byte[] unsignedUnvalidatedBlockBytes = ByteUtils.readByteSubstring(unvalidatedBlockBytes, 0, unvalidatedBlockLength);
                                boolean poolHasBeenTamperedWith = false;
                                ByteSet previousOldKeys = participatingDilutionProcess.getOldKeys();
                                ByteSet previousNewKeys = participatingDilutionProcess.getNewKeys();
                                if (previousOldKeys.isEmpty() && previousNewKeys.isEmpty()) {
                                    for (int j = 0; j < oldKeys.size(); j++) {
                                        participatingDilutionProcess.addOldKey(oldKeys.get(j), keyOriginBlockIndices.get(j));
                                        participatingDilutionProcess.addNewKey(newKeys.get(j));
                                    }
                                } else {
                                    if (previousOldKeys.size() != oldKeys.size() || previousNewKeys.size() != newKeys.size()) {
                                        poolHasBeenTamperedWith = true;
                                    }
                                    if (!poolHasBeenTamperedWith) {
                                        for (byte[] oldKey: oldKeys) {
                                            if (!previousOldKeys.contains(oldKey)) {
                                                poolHasBeenTamperedWith = true;
                                            }
                                        }
                                        for (byte[] newKey: newKeys) {
                                            if (!previousNewKeys.contains(newKey)) {
                                                poolHasBeenTamperedWith = true;
                                            }
                                        }
                                    }
                                }
                                if (!poolHasBeenTamperedWith) {
                                    participatingDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                                    log.add("  Processed unvalidated dilution block.");
                                    byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(unvalidatedBlockBytes, unvalidatedBlockLength);
                                    createSignatureMessage(participatingDilutionProcess, unsignedUnvalidatedBlockBytes, poolIdentifierOfNewKeyMessage, signatureBytes);
                                } else {
                                    log.add("  Pool has been tampered with.");
                                }
                            }
                        } else {
                            log.add("  The anonymity set is not correct.");
                        }
                    }
                }
        } else {
            log.add("  Blockchain was null.");
        }
    }

    public ValidationStatus validateUnvalidatedDilutionBlock(byte[] message) {
        log.add("validateUnvalidatedDilutionBlock()");
        boolean isValid = message.length > UNVALIDATED_DILUTION_BLOCK_PREFIX.length + dilutionBlockFactory.getMinimumMessageLength() + 1;
        int i = 0;
        while (isValid && i < UNVALIDATED_DILUTION_BLOCK_PREFIX.length) {
            if (UNVALIDATED_DILUTION_BLOCK_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            byte[] unvalidatedBlockBytes = ByteUtils.readByteSubstring(message, UNVALIDATED_DILUTION_BLOCK_PREFIX.length, message.length - UNVALIDATED_DILUTION_BLOCK_PREFIX.length);
            int offset = dilutionBlockFactory.getPrefix().length;
            byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
            byte[] chainId = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, Election.ID_LENGTH);
            offset += Election.ID_LENGTH;
            log.add("  Chain: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                offset += 8 + PrivacyUtils.HASH_LENGTH + Block.INDEX_SIZE;
                log.add("  Found blockchain");
                byte[] poolIdentifierOfUnvalidatedDilutionBlock = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, POOL_IDENTIFIER_SIZE);
                offset += POOL_IDENTIFIER_SIZE;
                log.add("  Pool: " + Hex.toHexString(poolIdentifierOfUnvalidatedDilutionBlock));
                byte[] poolManagerOfUnvalidatedDilutionBlock = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                log.add("  Pool manager: " + Hex.toHexString(poolManagerOfUnvalidatedDilutionBlock));
                byte[] keyOriginIndexOfNewKeyMessageBytes = ByteUtils.readByteSubstring(unvalidatedBlockBytes, offset, Block.INDEX_SIZE);
                offset += Block.INDEX_SIZE;
                long keyOriginIndexOfNewKeyMessage = ByteUtils.longFromByteArray(keyOriginIndexOfNewKeyMessageBytes);
                log.add("  Key origin: " + String.valueOf(keyOriginIndexOfNewKeyMessage));
                byte[] anonymitySet = dilutionBlockFactory.getAnonymitySet(unvalidatedBlockBytes);
                log.add("  Anonymity set: " + Hex.toHexString(anonymitySet));
                int numberOfKeys = dilutionBlockFactory.getNumberOfKeys(unvalidatedBlockBytes);
                log.add("  Number of keys: " + String.valueOf(numberOfKeys));
                List<byte[]> oldKeys = dilutionBlockFactory.getOldKeys(unvalidatedBlockBytes, numberOfKeys);
                List<Long> keyOriginBlockIndices = dilutionBlockFactory.getKeyOriginBlockIndices(unvalidatedBlockBytes, numberOfKeys);
                byte[] correctAnonymitySet = null;
                i = 0;
                ValidationStatus validationStatus = ValidationStatus.VALID;
                while (validationStatus == ValidationStatus.VALID && i < oldKeys.size()) {
                    Long keyOriginBlockIndex = keyOriginBlockIndices.get(i);
                    Block originBlock = blockchain.getBlock(keyOriginBlockIndex);
                    if (originBlock == null) {
                        log.add("  Old key was not found in block " + String.valueOf(keyOriginBlockIndex) + ": " + Hex.toHexString(oldKeys.get(i)));
                        validationStatus = ValidationStatus.INVALID;
                    } else if (blockchain.oldKeyIsStillValid(oldKeys.get(i), keyOriginBlockIndex) && blockchain.oldKeyDepthIsOK(oldKeys.get(i), keyOriginBlockIndex)) {
                        byte[] otherAnonymitySet = oldKeys.get(i);
                        if (originBlock.getPrefix()[0] == DILUTION_BLOCK_FIRST_CHAR) {
                            DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                            otherAnonymitySet = originDilutionBlock.getAnonymitySet();
                        }
                        if (correctAnonymitySet == null || ByteUtils.compareByteArrays(correctAnonymitySet, otherAnonymitySet) > 0) {
                            correctAnonymitySet = otherAnonymitySet;
                        }
                    } else if (!blockchain.oldKeyIsStillValid(oldKeys.get(i), keyOriginBlockIndex)) {
                        log.add("  Old key was no longer valid: " + Hex.toHexString(oldKeys.get(i)));
                        validationStatus = ValidationStatus.INVALID;
                    } else {
                        log.add("  Old key was at wrong depth: " + Hex.toHexString(oldKeys.get(i)));
                        validationStatus = ValidationStatus.INVALID;
                    }
                    i++;
                }
                if (validationStatus == ValidationStatus.VALID && ByteUtils.byteArraysAreEqual(correctAnonymitySet, anonymitySet)) {
                    if (blockchain.oldKeyIsStillValid(poolManagerOfUnvalidatedDilutionBlock, keyOriginIndexOfNewKeyMessage)) {
                        int unvalidatedBlockLength = dilutionBlockFactory.getUnvalidatedBlockLength(unvalidatedBlockBytes);
                        byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, UNVALIDATED_DILUTION_BLOCK_PREFIX.length + unvalidatedBlockLength);
                        byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(unvalidatedBlockBytes, unvalidatedBlockLength);
                        try {
                            if (PrivacyUtils.verifySignature(messageToVerify, poolManagerOfUnvalidatedDilutionBlock, signatureBytes)) {
                                KeyWithOrigin poolManagerKeyWithOrigin = new KeyWithOrigin(poolManagerOfUnvalidatedDilutionBlock, keyOriginIndexOfNewKeyMessage);
                                blameCircle.remove(poolManagerKeyWithOrigin, poolManagerKeyWithOrigin, poolIdentifierOfUnvalidatedDilutionBlock);
                                    for (int j = 0; j < oldKeys.size(); j++) {
                                        byte[] memberKey = oldKeys.get(j);
                                        long keyOriginBlockIndex = keyOriginBlockIndices.get(j);
                                        blameCircle.add(new KeyWithOrigin(memberKey, keyOriginBlockIndex), poolManagerKeyWithOrigin, poolIdentifierOfUnvalidatedDilutionBlock);
                                    }
                                    log.add("  Validated unvalidated block.");
                                    return ValidationStatus.VALID;
                            } else {
                                log.add("  Signature was not valid.");
                                return ValidationStatus.INVALID;
                            }
                        } catch (NoSuchAlgorithmException exception) {
                            log.add("  NoSuchAlgorithmException:");
                            log.add(exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeySpecException exception) {
                            log.add("  InvalidKeySpecException:");
                            log.add(exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeyException exception) {
                            log.add("  InvalidKeyException:");
                            log.add(exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (SignatureException exception) {
                            log.add("  SignatureException:");
                            log.add(exception.getMessage());
                            return ValidationStatus.INVALID;
                        }
                    } else {
                        log.add("  The pool manager is no longer valid.");
                        return ValidationStatus.INVALID;
                    }
                } else {
                    log.add("  The anonymity set is not correct.");
                    return ValidationStatus.INVALID;
                }
            } else {
                log.add("  Blockchain was null.");
                return ValidationStatus.INVALID;
            }
        } else {
            log.add("  Failed initial validation check.");
            return ValidationStatus.INVALID;
        }
    }

    public void createSignatureMessage(ParticipatingDilutionProcess participatingDilutionProcess, byte[] unvalidatedBlockBytes, byte[] poolIdentifier, byte[] unvalidatedBlockSignature) {
        log.add("createSignatureMessage()");
        Signature signature = privacyLayer.getSignatureForPublicKey(participatingDilutionProcess.getPublicKeyForDilutionApplication());
        try {
            signature.update(unvalidatedBlockBytes);
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(signature.sign());
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(SIGNATURE_MESSAGE_PREFIX);
            messageParts.add(poolIdentifier);
            log.add("  Pool: " + Hex.toHexString(poolIdentifier));
            messageParts.add(participatingDilutionProcess.getPublicKeyOfPoolManager());
            log.add("  Manager: " + Hex.toHexString(participatingDilutionProcess.getPublicKeyOfPoolManager()));
            messageParts.add(ByteUtils.longToByteArray(participatingDilutionProcess.getKeyOriginBlockIndexOfPoolManager()));
            log.add("  Key origin: " + String.valueOf(participatingDilutionProcess.getKeyOriginBlockIndexOfPoolManager()));
            messageParts.add(participatingDilutionProcess.getPublicKeyForDilutionApplication());
            log.add("  Public key: " + Hex.toHexString(participatingDilutionProcess.getPublicKeyForDilutionApplication()));
            messageParts.add(ByteUtils.longToByteArray(participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication()));
            log.add("  Key origin: " + String.valueOf(participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication()));
            messageParts.add(signatureBytes);
            messageParts.add(unvalidatedBlockBytes);
            messageParts.add(ByteUtils.encodeWithLengthByte(unvalidatedBlockSignature));
            networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
        } catch (SignatureException exception) {
            log.add("  SignatureException:");
            log.add("  " + exception.getMessage());
        }
    }

    public void processSignatureMessage(byte[] message) {
        log.add("processSignatureMessage()");
        boolean isValid = true;
        int i = 0;
        while (isValid && i < SIGNATURE_MESSAGE_PREFIX.length) {
            if (SIGNATURE_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            byte[] poolIdentifierOfSignatureMessage = new byte[POOL_IDENTIFIER_SIZE];
            for (int j = 0; j < POOL_IDENTIFIER_SIZE; j++) {
                poolIdentifierOfSignatureMessage[j] = message[j + SIGNATURE_MESSAGE_PREFIX.length];
            }
            log.add("  Pool: " + Hex.toHexString(poolIdentifierOfSignatureMessage));
            byte[] oldKey = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
            for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
                oldKey[j] = message[j + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + SIGNATURE_MESSAGE_PREFIX.length];
            }
            log.add("  Public key: " + Hex.toHexString(oldKey));
            byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + POOL_IDENTIFIER_SIZE + SIGNATURE_MESSAGE_PREFIX.length);
            log.add("  Signature size: " + signatureBytes.length);


            int unvalidatedBlockOffset = SIGNATURE_MESSAGE_PREFIX.length + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + signatureBytes.length;
            int offset = unvalidatedBlockOffset + dilutionBlockFactory.getPrefix().length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + Block.INDEX_SIZE + PrivacyUtils.HASH_LENGTH + 8 + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
            int numberOfPoolMembers = message[offset];
            if (numberOfPoolMembers < 0) {
                numberOfPoolMembers += 256;
            }
            offset += 1 + numberOfPoolMembers * (2 * PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE);
            byte[] unvalidatedBlockBytesFromMessage = ByteUtils.readByteSubstring(message, unvalidatedBlockOffset, offset - unvalidatedBlockOffset);

            log.add("  Unvalidated block bytes: " + Hex.toHexString(unvalidatedBlockBytesFromMessage));
            byte[] chainId = dilutionBlockFactory.getChainId(unvalidatedBlockBytesFromMessage);
            byte[] publicKeyOfManager = dilutionBlockFactory.getManagerPublicKey(unvalidatedBlockBytesFromMessage);
            log.add("  Manager: " + Hex.toHexString(publicKeyOfManager));
            log.add("  Election ID: " + Hex.toHexString(chainId));
            byte[] poolIdentifier = dilutionBlockFactory.getPoolIdentifier(unvalidatedBlockBytesFromMessage);
            log.add("  Pool2: " + Hex.toHexString(poolIdentifier));
            byte[] blockAssembler = dilutionBlockFactory.getBlockAssembler(unvalidatedBlockBytesFromMessage);
            log.add("  Block assembler: " + Hex.toHexString(blockAssembler));
            long blockAssemblerKeyOriginBlockIndex = dilutionBlockFactory.getBlockAssemblerOriginIndex(unvalidatedBlockBytesFromMessage);
            log.add("  Key origin: " + String.valueOf(blockAssemblerKeyOriginBlockIndex));
            int depth = dilutionBlockFactory.getDepth(unvalidatedBlockBytesFromMessage);
            log.add("  Depth: " + depth);
            byte[] anonymitySet = dilutionBlockFactory.getAnonymitySet(unvalidatedBlockBytesFromMessage);
            log.add("  Anonymity set: " + Hex.toHexString(anonymitySet));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, publicKeyOfManager);
            if (managedDilutionProcesses.containsKey(blockchain)) {
                ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
                DilutionBlock unvalidatedDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                if (unvalidatedDilutionBlock != null && ByteUtils.byteArraysAreEqual(dilutionProcess.getCurrentPoolIdentifier(), poolIdentifierOfSignatureMessage) && ByteUtils.byteArraysAreEqual(unvalidatedBlockBytesFromMessage, unvalidatedDilutionBlock.getBytesWithoutValidation())) {
                    log.add("  Signature was for managed pool.");
                    try {
                        if (PrivacyUtils.verifySignature(unvalidatedBlockBytesFromMessage, oldKey, signatureBytes)) {
                            unvalidatedDilutionBlock.addSignature(oldKey, signatureBytes);
                            dilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                            dilutionProcess.setNameOfLastMessageReceived("some signature messages");
                            if (unvalidatedDilutionBlock.isValidated()) {
                                dilutionProcess.setNameOfLastMessageReceived("all signature messages");
                                if (!blockchain.appendBlock(unvalidatedDilutionBlock)) {
                                    log.add("  Trying to append a block that is no longer valid in this blockchain.");
                                } else {
                                    for (byte[] newKey : unvalidatedDilutionBlock.getNewKeys()) {
                                        privacyLayer.setKeyOriginBlockIndex(newKey, unvalidatedDilutionBlock.getIndex(), publicKeyOfManager, chainId);
                                    }
                                    removeDilutionApplications(blockchain, unvalidatedDilutionBlock.getOldKeys());
                                    for (byte[] oldKeyInBlock: unvalidatedDilutionBlock.getOldKeys()) {
                                        privacyLayer.setKeyUseBlockIndex(oldKeyInBlock, unvalidatedDilutionBlock.getIndex());
                                    }
                                    networkLayer.broadcastAnonymously(unvalidatedDilutionBlock.getBytes());
                                    removePool(blockchain);
                                }
                            }
                        } else {
                            log.add("  Signature is not valid.");
                        }
                    } catch (NoSuchAlgorithmException exception) {
                        log.add("  NoSuchAlgorithmException:");
                        log.add("  " + exception.getMessage());
                    } catch (InvalidKeySpecException exception) {
                        log.add("  InvalidKeySpecException:");
                        log.add("  " + exception.getMessage());
                    } catch (InvalidKeyException exception) {
                        log.add("  InvalidKeyException:");
                        log.add("  " + exception.getMessage());
                    } catch (SignatureException exception) {
                        log.add("  SignatureException:");
                        log.add("  " + exception.getMessage());
                    }
                } else if (!ByteUtils.byteArraysAreEqual(dilutionProcess.getCurrentPoolIdentifier(), poolIdentifierOfSignatureMessage)) {
                    log.add("  Signature was for different pool.");
                } else if (unvalidatedDilutionBlock != null) {
                    log.add("  Unvalidated block bytes were not accurate.");
                    log.add(Hex.toHexString(unvalidatedBlockBytesFromMessage));
                    log.add(Hex.toHexString(unvalidatedDilutionBlock.getBytesWithoutValidation()));
                }
            }
            boolean goingToRemovePool = false;
            for (ManagedDilutionProcess dilutionProcess: completedManagedDilutionProcesses.get(blockchain)) {
                DilutionBlock unvalidatedDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                if (unvalidatedDilutionBlock != null && ByteUtils.byteArraysAreEqual(dilutionProcess.getCurrentPoolIdentifier(), poolIdentifierOfSignatureMessage) && ByteUtils.byteArraysAreEqual(unvalidatedBlockBytesFromMessage, unvalidatedDilutionBlock.getBytesWithoutValidation())) {
                    log.add("  Signature was for managed pool.");
                    try {
                        if (PrivacyUtils.verifySignature(unvalidatedBlockBytesFromMessage, oldKey, signatureBytes)) {
                            unvalidatedDilutionBlock.addSignature(oldKey, signatureBytes);
                            dilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                            dilutionProcess.setNameOfLastMessageReceived("some signature messages");
                            if (unvalidatedDilutionBlock.isValidated()) {
                                dilutionProcess.setNameOfLastMessageReceived("all signature messages");
                                if (!blockchain.appendBlock(unvalidatedDilutionBlock)) {
                                    log.add("  Trying to append a block that is no longer valid in this blockchain.");
                                } else {
                                    for (byte[] newKey : unvalidatedDilutionBlock.getNewKeys()) {
                                        privacyLayer.setKeyOriginBlockIndex(newKey, unvalidatedDilutionBlock.getIndex(), publicKeyOfManager, chainId);
                                    }
                                    removeDilutionApplications(blockchain, unvalidatedDilutionBlock.getOldKeys());
                                    for (byte[] oldKeyInBlock: unvalidatedDilutionBlock.getOldKeys()) {
                                        privacyLayer.setKeyUseBlockIndex(oldKeyInBlock, unvalidatedDilutionBlock.getIndex());
                                    }
                                    networkLayer.broadcastAnonymously(unvalidatedDilutionBlock.getBytes());
                                    goingToRemovePool = true;
                                }
                            }
                        } else {
                            log.add("  Signature is not valid.");
                        }
                    } catch (NoSuchAlgorithmException exception) {
                        log.add("  NoSuchAlgorithmException:");
                        log.add("  " + exception.getMessage());
                    } catch (InvalidKeySpecException exception) {
                        log.add("  InvalidKeySpecException:");
                        log.add("  " + exception.getMessage());
                    } catch (InvalidKeyException exception) {
                        log.add("  InvalidKeyException:");
                        log.add("  " + exception.getMessage());
                    } catch (SignatureException exception) {
                        log.add("  SignatureException:");
                        log.add("  " + exception.getMessage());
                    }
                } else {
                    log.add("  Signature was for different pool.");
                }
            }
            if (goingToRemovePool) {
                removePool(blockchain);
            }
        } else {
            log.add("  Failed initial validation check.");
        }
    }

    public ValidationStatus validateSignatureMessage(byte[] message) {
        log.add("validateSignatureMessage()");
        boolean isValid = message.length > SIGNATURE_MESSAGE_PREFIX.length + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + dilutionBlockFactory.getMinimumMessageLength();
        int i = 0;
        while (isValid && i < SIGNATURE_MESSAGE_PREFIX.length) {
            if (SIGNATURE_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = SIGNATURE_MESSAGE_PREFIX.length;
            byte[] poolIdentifierOfSignatureMessage = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
            offset += POOL_IDENTIFIER_SIZE;
            byte[] blockAssemblerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            byte[] blockAssemblerKeyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
            offset += Block.INDEX_SIZE;
            long blockAssemblerKeyOriginBlockIndex = ByteUtils.longFromByteArray(blockAssemblerKeyOriginBlockIndexBytes);
            byte[] oldKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            byte[] keyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
            offset += Block.INDEX_SIZE;
            long keyOriginBlockIndex = ByteUtils.longFromByteArray(keyOriginBlockIndexBytes);
            byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            offset += 1 + signatureBytes.length;

            int unvalidatedBlockOffset = offset;
            offset += dilutionBlockFactory.getPrefix().length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + Block.INDEX_SIZE + PrivacyUtils.HASH_LENGTH + 8 + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH;
            int numberOfPoolMembers = message[offset];
            if (numberOfPoolMembers < 0) {
                numberOfPoolMembers += 256;
            }
            offset += 1 + numberOfPoolMembers * (2 * PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE);

            byte[] unvalidatedBlockBytesFromMessage = ByteUtils.readByteSubstring(message, unvalidatedBlockOffset, offset - unvalidatedBlockOffset);



            byte[] chainId = dilutionBlockFactory.getChainId(unvalidatedBlockBytesFromMessage);
            byte[] publicKeyOfManager = dilutionBlockFactory.getManagerPublicKey(unvalidatedBlockBytesFromMessage);
            log.add("  Manager: " + Hex.toHexString(publicKeyOfManager));
            log.add("  Election ID: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, publicKeyOfManager);
            if (blockchain == null) {
                log.add("  Blockchain was null.");
                return ValidationStatus.INVALID;
            } else {
                if (blockchain.oldKeyIsStillValid(oldKey, keyOriginBlockIndex) && blockchain.oldKeyDepthIsOK(oldKey, keyOriginBlockIndex)) {
                    try {
                        if (PrivacyUtils.verifySignature(unvalidatedBlockBytesFromMessage, oldKey, signatureBytes)) {
                            KeyWithOrigin oldKeyWithOrigin = new KeyWithOrigin(oldKey, keyOriginBlockIndex);
                            KeyWithOrigin blockAssemblerKeyWithOrigin = new KeyWithOrigin(blockAssemblerPublicKey, blockAssemblerKeyOriginBlockIndex);
                            blameCircle.remove(oldKeyWithOrigin, blockAssemblerKeyWithOrigin, poolIdentifierOfSignatureMessage);
                            BackgroundDilutionProcessCircle backgroundDilutionProcessCircle = backgroundDilutionProcesses.get(blockchain);
                            offset = POOL_IDENTIFIER_SIZE + SIGNATURE_MESSAGE_PREFIX.length;
                            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                            log.add("  Manager: " + Hex.toHexString(blockAssemblerPublicKey));
                            offset += Block.INDEX_SIZE;
                            log.add("  Key origin: " + String.valueOf(blockAssemblerKeyOriginBlockIndex));
                                BackgroundDilutionProcess backgroundDilutionProcess = backgroundDilutionProcessCircle.get(blockAssemblerPublicKey, poolIdentifierOfSignatureMessage);
                                if (backgroundDilutionProcess != null) {
                                    backgroundDilutionProcess.decrementSignatures();
                                    if (backgroundDilutionProcess.getSignatureCounter() <= 0) {
                                        blameCircle.add(blockAssemblerKeyWithOrigin, blockAssemblerKeyWithOrigin, poolIdentifierOfSignatureMessage);
                                    }
                                }
                            return ValidationStatus.VALID;
                        } else {
                            log.add("  Signature is not valid.");
                            return ValidationStatus.INVALID;
                        }
                    } catch (NoSuchAlgorithmException exception) {
                        log.add("  NoSuchAlgorithmException:");
                        log.add("  " + exception.getMessage());
                        return ValidationStatus.INVALID;
                    } catch (InvalidKeySpecException exception) {
                        log.add("  InvalidKeySpecException:");
                        log.add("  " + exception.getMessage());
                        return ValidationStatus.INVALID;
                    } catch (InvalidKeyException exception) {
                        log.add("  InvalidKeyException:");
                        log.add("  " + exception.getMessage());
                        return ValidationStatus.INVALID;
                    } catch (SignatureException exception) {
                        log.add("  SignatureException:");
                        log.add("  " + exception.getMessage());
                        return ValidationStatus.INVALID;
                    }
                } else {
                    log.add("  Old key was no longer valid.");
                    return ValidationStatus.INVALID;
                }
            }
        } else {
            log.add("  Failed initial validation check.");
            return ValidationStatus.INVALID;
        }
    }

    public void sendBlameMessage(Blockchain blockchain, byte[] poolMessage, int indexInPool) {
        log.add("sendBlameMessage()");
        ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
        ArrayList<byte[]> messageParts = new ArrayList<>();
        messageParts.add(BLAME_MESSAGE_PREFIX);
        messageParts.add(new byte[]{(byte) indexInPool});
        messageParts.add(participatingDilutionProcess.getPoolPrivateKey());
        log.add("  Private key: " + Hex.toHexString(participatingDilutionProcess.getPoolPrivateKey()));
        messageParts.add(poolMessage);
        networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
    }

    public ValidationStatus validateBlameMessage(byte[] message) {
        log.add("validateBlameMessage()");
        boolean isValid = message.length > BLAME_MESSAGE_PREFIX.length + 1 + PrivacyUtils.PUBLIC_KEY_LENGTH + POOL_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + 1 + 1;
        int i = 0;
        while (isValid && i < BLAME_MESSAGE_PREFIX.length) {
            if (BLAME_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            byte[] poolMessage = ByteUtils.readByteSubstring(message, BLAME_MESSAGE_PREFIX.length + 1 + PrivacyUtils.PRIVATE_KEY_LENGTH, message.length - BLAME_MESSAGE_PREFIX.length - 1 - PrivacyUtils.PRIVATE_KEY_LENGTH);
            if (validatePoolMessage(poolMessage) == ValidationStatus.VALID) {
                log.add("  Internal pool message is valid");
                int offset = BLAME_MESSAGE_PREFIX.length;
                int indexInPool = message[BLAME_MESSAGE_PREFIX.length];
                if (indexInPool < 0) {
                    indexInPool += 256;
                }
                offset += 1;
                byte[] poolPrivateKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PRIVATE_KEY_LENGTH);
                offset = Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_MESSAGE_PREFIX.length;
                log.add("  Pool private key: " + Hex.toHexString(poolPrivateKey));
                byte[] sessionPublicKey = ByteUtils.readByteSubstring(poolMessage, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                log.add("  Session public key: " + Hex.toHexString(sessionPublicKey));
                int poolSize = poolMessage[offset];
                if (poolSize < 0) {
                    poolSize += 256;
                }
                offset += 1;
                byte[] blamingPublicKey = null;
                byte[] blamingPoolKey = null;
                byte[] blamedEncryptedMessage = null;
                for (int k = 0; k <= indexInPool && k < poolSize; k++) {
                    byte[] poolMemberPublicKey = ByteUtils.readByteSubstring(poolMessage, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                    if (poolMemberPublicKey != null) {
                        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                        offset += Block.INDEX_SIZE;
                        byte[] poolPublicKey = ByteUtils.readByteSubstring(poolMessage, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                        byte[] poolKeySignature = ByteUtils.readByteSubstringWithLengthEncoded(poolMessage, offset);
                        offset += 1 + poolKeySignature.length;
                        byte[] encryptedMessage = ByteUtils.readByteSubstringWithLengthEncoded(poolMessage, offset);
                        if (indexInPool == k) {
                            log.add("  Found the blaming public key and encrypted message.");
                            blamingPublicKey = poolMemberPublicKey;
                            log.add("  Blaming public key: " + Hex.toHexString(blamingPublicKey));
                            blamingPoolKey = poolPublicKey;
                            log.add("  Blaming pool key: " + Hex.toHexString(blamingPoolKey));
                            blamedEncryptedMessage = encryptedMessage;
                        }
                        if (encryptedMessage != null) {
                            offset += 1 + encryptedMessage.length;
                        }
                    }
                }
                if (blamingPublicKey == null) {
                    log.add("  Blaming public key wasn't in pool message.");
                } else {
                    if (PrivacyUtils.isValidKeyPair(blamingPoolKey, poolPrivateKey)) {
                        try {
                            Cipher poolPrivateKeyDecryption = PrivacyUtils.getPrivateKeyDecryption(PrivacyUtils.getPrivateKeyFromBytes(poolPrivateKey));
                            byte[] fakePrivateKey = poolPrivateKeyDecryption.doFinal(blamedEncryptedMessage);
                            log.add("  Fake private key: " + Hex.toHexString(fakePrivateKey));
                            if (PrivacyUtils.isValidKeyPair(sessionPublicKey, fakePrivateKey)) {
                                log.add("  The blamed session key was valid after all.");
                                return ValidationStatus.INVALID;
                            } else {
                                log.add("  Blamed session key was indeed invalid.");
                                return ValidationStatus.VALID;
                            }
                        } catch (NoSuchAlgorithmException exception) {
                            log.add("  NoSuchAlgorithmException when setting up private key decryption:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (InvalidKeySpecException exception) {
                            log.add("  InvalidKeySpecException when setting up private key decryption:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.INVALID;
                        } catch (IllegalBlockSizeException exception) {
                            log.add("  IllegalBlockSizeException when decrypting fake private key:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.VALID;
                        } catch (BadPaddingException exception) {
                            log.add("  BadPaddingException when decrypting fake private key:");
                            log.add("  " + exception.getMessage());
                            return ValidationStatus.VALID;
                        }
                    } else {
                        log.add("  Pool private key doesn't correspond to blaming public key.");
                        return ValidationStatus.INVALID;
                    }
                }
            }
        } else {
            log.add("  Failed initial validation check.");
        }
        return ValidationStatus.INVALID;
    }

    public void processBlameMessage(byte[] message) {
        log.add("processBlameMessage()");
        int offset = BLAME_MESSAGE_PREFIX.length + 1 + PrivacyUtils.PRIVATE_KEY_LENGTH + POOL_MESSAGE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Pool manager: " + Hex.toHexString(publicKeyOfPoolManager));
        byte[] managerKeyOriginBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
        long managerKeyOriginBlockIndex = ByteUtils.longFromByteArray(managerKeyOriginBytes);
        KeyWithOrigin managerKeyWithOrigin = new KeyWithOrigin(publicKeyOfPoolManager, managerKeyOriginBlockIndex);
        offset += Block.INDEX_SIZE;
        log.add("  Key origin: " + String.valueOf(managerKeyOriginBlockIndex));
        byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
        log.add("  Pool: " + Hex.toHexString(poolIdentifier));
        BackgroundDilutionProcessCircle backgroundDilutionProcessCircle = backgroundDilutionProcesses.get(blockchain);
        if (backgroundDilutionProcessCircle != null) {
            BackgroundDilutionProcess backgroundDilutionProcess = backgroundDilutionProcessCircle.get(publicKeyOfPoolManager, poolIdentifier);
            if (backgroundDilutionProcess != null) {
                for (KeyWithOrigin member: backgroundDilutionProcess.getMembers()) {
                    blameCircle.remove(member, managerKeyWithOrigin, poolIdentifier);
                }
            }
        }
        blameCircle.add(managerKeyWithOrigin, managerKeyWithOrigin, poolIdentifier);
    }

    public void sendPoolReceipt(Blockchain blockchain, byte[] poolMessage, int indexInPool, DilutionProcess dilutionProcess) {
        log.add("sendPoolReceipt()");
        ArrayList<byte[]> messageToSignParts = new ArrayList<>();
        messageToSignParts.add(POOL_RECEIPT_PREFIX);
        messageToSignParts.add(new byte[]{(byte) indexInPool});
        messageToSignParts.add(poolMessage);
        try {
            ArrayList<byte[]> messageParts = new ArrayList<>();
            byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
            messageParts.add(messageToSign);
            log.add("  Public key: " + Hex.toHexString(dilutionProcess.getPublicKeyForDilutionApplication()));
            Signature signature = privacyLayer.getSignatureForPublicKey(dilutionProcess.getPublicKeyForDilutionApplication());
            signature.update(messageToSign);
            messageParts.add(ByteUtils.encodeWithLengthByte(signature.sign()));
            networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
            dilutionProcess.addReceipt(dilutionProcess.getPublicKeyForDilutionApplication());
        } catch (SignatureException exception) {
            log.add("  SignatureException:");
            log.add("  " + exception.getMessage());
        }
    }

    public ValidationStatus validatePoolReceipt(byte[] message) {
        log.add("validatePoolReceipt()");
        boolean isValid = message.length > POOL_RECEIPT_PREFIX.length + 1 + 1;
        int i = 0;
        while (isValid && i < POOL_RECEIPT_PREFIX.length) {
            if (POOL_RECEIPT_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = POOL_RECEIPT_PREFIX.length;
            int indexInPool = message[offset];
            if (indexInPool < 0) {
                indexInPool += 256;
            }
            offset += 1 + POOL_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH;
            int numberOfMembersInPool = message[offset];
            if (numberOfMembersInPool < 0) {
                numberOfMembersInPool += 256;
            }
            offset++;
            byte[] publicKey = null;
            for (i = 0; i < numberOfMembersInPool; i++) {
                if (i == indexInPool) {
                    publicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                }
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH;
                byte[] poolPublicKey = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + poolPublicKey.length;
                byte[] dilutionApplicationSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + dilutionApplicationSignature.length;
                byte[] inviteSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + inviteSignature.length;
                byte[] poolResponseSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + poolResponseSignature.length;
                byte[] encryptedSessionPrivateKey = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + encryptedSessionPrivateKey.length;
            }
            byte[] poolMessageSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            offset += 1 + poolMessageSignature.length;
            int poolMessageSize = offset - 1 - POOL_RECEIPT_PREFIX.length;
            byte[] poolMessage = ByteUtils.readByteSubstring(message, 1 + POOL_RECEIPT_PREFIX.length, poolMessageSize);
            byte[] poolReceiptSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
            if (validatePoolMessage(poolMessage) == ValidationStatus.VALID && publicKey != null) {
                byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, offset);
                try {
                    if (PrivacyUtils.verifySignature(messageToVerify, publicKey, poolReceiptSignature)) {
                        return ValidationStatus.VALID;
                    } else {
                        log.add("  Signature was invalid.");
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("  NoSuchAlgorithmException when checking signature of pool receipt:");
                    log.add("  " + exception.getMessage());
                } catch (InvalidKeySpecException exception) {
                    log.add("  InvalidKeySpecException when checking signature of pool receipt:");
                    log.add("  " + exception.getMessage());
                } catch (InvalidKeyException exception) {
                    log.add("  InvalidKeyException when checking signature of pool receipt:");
                    log.add("  " + exception.getMessage());
                } catch (SignatureException exception) {
                    log.add("  SignatureException when checking signature of pool receipt:");
                    log.add("  " + exception.getMessage());
                }
            }
        }
        return ValidationStatus.INVALID;
    }

    public ValidationStatus validatePoolReceipt2(byte[] message) {
        log.add("validatePoolReceipt()");
        boolean isValid = message.length > POOL_RECEIPT_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + POOL_IDENTIFIER_SIZE + PrivacyUtils.HASH_LENGTH + 1;
        int i = 0;
        while (isValid && i < POOL_RECEIPT_PREFIX.length) {
            if (POOL_RECEIPT_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = POOL_RECEIPT_PREFIX.length;
            byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
            byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
            offset += Election.ID_LENGTH;
            log.add("  Election ID: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                log.add("  Pool manager: " + Hex.toHexString(publicKeyOfPoolManager));
                byte[] managerKeyOriginBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                long managerKeyOriginBlockIndex = ByteUtils.longFromByteArray(managerKeyOriginBytes);
                offset += Block.INDEX_SIZE;
                log.add("  Key origin: " + String.valueOf(managerKeyOriginBlockIndex));
                byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
                offset += POOL_IDENTIFIER_SIZE;
                log.add("  Pool: " + Hex.toHexString(poolIdentifier));
                offset += PrivacyUtils.HASH_LENGTH;
                byte[] messageToVerify = ByteUtils.readByteSubstring(message, 0, offset);
                byte[] oldKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
                log.add("  Public key: " + Hex.toHexString(oldKey));
                byte[] keyOriginBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                offset += Block.INDEX_SIZE;
                long keyOriginBlockIndex = ByteUtils.longFromByteArray(keyOriginBlockIndexBytes);
                log.add("  Key origin: " + String.valueOf(keyOriginBlockIndex));
                if (blockchain.oldKeyIsStillValid(oldKey, keyOriginBlockIndex) && blockchain.oldKeyDepthIsOK(oldKey, keyOriginBlockIndex)) {
                    byte[] signatureBytes = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                    try {
                        if (PrivacyUtils.verifySignature(messageToVerify, oldKey, signatureBytes)) {
                            return ValidationStatus.VALID;
                        } else {
                            log.add("  Signature was invalid.");
                        }
                    } catch (NoSuchAlgorithmException exception) {
                        log.add("  NoSuchAlgorithmException:");
                        log.add("  " + exception.getMessage());
                    } catch (InvalidKeySpecException exception) {
                        log.add("  InvalidKeySpecException:");
                        log.add("  " + exception.getMessage());
                    } catch (InvalidKeyException exception) {
                        log.add("  InvalidKeyException:");
                        log.add("  " + exception.getMessage());
                    } catch (SignatureException exception) {
                        log.add("  SignatureException:");
                        log.add("  " + exception.getMessage());
                    }
                } else {
                    log.add("  Public key was no longer valid or at wrong depth.");
                }
            }
        }
        return ValidationStatus.INVALID;
    }

    public void processPoolReceipt(byte[] message) {
        log.add("processPoolReceipt()");
        int offset = POOL_RECEIPT_PREFIX.length;
        int indexInPool = message[offset];
        if (indexInPool < 0) {
            indexInPool += 256;
        }
        offset += 1 + POOL_MESSAGE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Election ID: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        if (blockchain != null) {
            byte[] publicKeyOfPoolManager = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE;
            byte[] poolIdentifier = ByteUtils.readByteSubstring(message, offset, POOL_IDENTIFIER_SIZE);
            offset += POOL_IDENTIFIER_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH;
            int numberOfMembersInPool = message[offset];
            if (numberOfMembersInPool < 0) {
                numberOfMembersInPool += 256;
            }
            offset++;
            byte[] publicKey = null;
            for (int i = 0; i < numberOfMembersInPool; i++) {
                if (i == indexInPool) {
                    publicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
                }
                offset += PrivacyUtils.PUBLIC_KEY_LENGTH + Block.INDEX_SIZE + PrivacyUtils.PUBLIC_KEY_LENGTH;
                byte[] poolPublicKey = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + poolPublicKey.length;
                byte[] dilutionApplicationSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + dilutionApplicationSignature.length;
                byte[] inviteSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + inviteSignature.length;
                byte[] poolResponseSignature = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + poolResponseSignature.length;
                byte[] encryptedSessionPrivateKey = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + encryptedSessionPrivateKey.length;
            }

            if (publicKey != null) {
                if (dilutionProcesses.containsKey(blockchain)) {
                    ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
                    if (participatingDilutionProcess.getCurrentPoolIdentifier() != null && ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getCurrentPoolIdentifier(), poolIdentifier) && ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getPublicKeyOfPoolManager(), publicKeyOfPoolManager) && participatingDilutionProcess.getPoolMessage() != null) {
                        log.add("  Found pool as member.");
                        participatingDilutionProcess.addReceipt(publicKey);
                        if (participatingDilutionProcess.getNumberOfPoolReceipts() >= participatingDilutionProcess.getOldKeys().size()) {
                            //Then send new keys
                            log.add("  Received all the pool acknowledgements.");
                            sendNewKeyMessage(blockchain, participatingDilutionProcess);
                        }
                    }
                }

                if (managedDilutionProcesses.containsKey(blockchain)) {
                    ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
                    if (dilutionProcess.isIncludeSelf() && dilutionProcess.getCurrentPoolIdentifier() != null && ByteUtils.byteArraysAreEqual(dilutionProcess.getCurrentPoolIdentifier(), poolIdentifier) && ByteUtils.byteArraysAreEqual(dilutionProcess.getPublicKeyOfPoolManager(), publicKeyOfPoolManager) && dilutionProcess.getPoolMessage() != null) {
                        log.add("  Found managed pool.");
                        dilutionProcess.addReceipt(publicKey);
                        if (dilutionProcess.getNumberOfPoolReceipts() >= dilutionProcess.getPoolResponses().size()) {
                            log.add("  Received all the pool acknowledgements.");
                            sendNewKeyMessage(blockchain, dilutionProcess);
                        }
                    }
                }
            }
        }
    }

    public void removeDilutionApplications(Blockchain blockchain, List<byte[]> oldKeys) {
        DilutionApplicationCircle dilutionApplicationsForChain = dilutionApplications.get(blockchain);
        for (byte[] oldKey : oldKeys) {
            dilutionApplicationsForChain.remove(oldKey);
        }
    }

    public void processDilutionBlock(byte[] message) {
        log.add("processDilutionBlock()");
        byte[] managerPublicKey = dilutionBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] id = dilutionBlockFactory.getChainId(message);
        log.add("  Chain: " + Hex.toHexString(id));
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Dilution block belongs to an unknown chain.");
        } else {
            DilutionBlock dilutionBlock = (DilutionBlock) dilutionBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
            if (dilutionBlock != null) {
                log.add("  Got dilution block from factory.");
                for (byte[] oldKey : dilutionBlock.getOldKeys()) {
                    privacyLayer.setKeyUseBlockIndex(oldKey, dilutionBlock.getIndex());
                }
                for (byte[] newKey : dilutionBlock.getNewKeys()) {
                    privacyLayer.setKeyOriginBlockIndex(newKey, dilutionBlock.getIndex(), managerPublicKey, id);
                }
                removeDilutionApplications(blockchain, dilutionBlock.getOldKeys());
                if (dilutionProcesses.containsKey(blockchain)) {
                    log.add("  There was a dilution process for this blockchain.");
                    ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
                    for (byte[] newKey : dilutionBlock.getNewKeys()) {
//                        privacyLayer.setKeyOriginBlockIndex(newKey, dilutionBlock.getIndex(), managerPublicKey, id);
                        if (participatingDilutionProcess.getOwnNewKey() != null && ByteUtils.byteArraysAreEqual(newKey, participatingDilutionProcess.getOwnNewKey())) {
                            log.add("  Found diluted key.");
                        }
                    }
                    for (byte[] oldKey: dilutionBlock.getOldKeys()) {
                        if (ByteUtils.byteArraysAreEqual(oldKey, participatingDilutionProcess.getPublicKeyForDilutionApplication())) {
                            removeOwnDilutionApplication(blockchain);
                        }
                    }
                }
                if (managedDilutionProcesses.containsKey(blockchain)) {
                    ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
                    DilutionBlock ownDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                    if (ownDilutionBlock == null) {
                        if (!blockchain.isPoolStillValid(dilutionProcess) || dilutionProcess.getPoolResponses() == null || dilutionProcess.getPoolResponses().size() == 0 || dilutionProcess.getReceivedNewKeys().size() + dilutionProcess.getSelfIncludingNumber() != dilutionProcess.getPoolResponses().size()) {
                            managedDilutionProcesses.remove(blockchain);
                            attemptToStartDilutionPool(dilutionProcess.getSignatureAsPoolManager(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager(), dilutionProcess.getIdentityForPool(), dilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                        }
                    } else if (!ownDilutionBlock.isValidated() || (!ownDilutionBlock.isEqual(dilutionBlock) && !blockchain.isBlockPresent(ownDilutionBlock))) {
                        if (blockchain.isPoolStillValid(dilutionProcess)) {
                            createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 1);
                        } else {
                            managedDilutionProcesses.remove(blockchain);
                            attemptToStartDilutionPool(dilutionProcess.getSignatureAsPoolManager(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager(), dilutionProcess.getIdentityForPool(), dilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                        }
                    }
                }
                HashSet<ManagedDilutionProcess> processesToRemove = new HashSet<>();
                for (ManagedDilutionProcess dilutionProcess: completedManagedDilutionProcesses.get(blockchain)) {
                    DilutionBlock ownDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                    if (ownDilutionBlock == null) {
                        if (!blockchain.isPoolStillValid(dilutionProcess)) {
                            processesToRemove.add(dilutionProcess);
                        }
                    } else if (!ownDilutionBlock.isValidated() || (!ownDilutionBlock.isEqual(dilutionBlock) && !blockchain.isBlockPresent(ownDilutionBlock))) {
                        if (blockchain.isPoolStillValid(dilutionProcess)) {
                            log.add("  Redo last steps of dilution process.");
                            createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 2);
                        } else {
                            processesToRemove.add(dilutionProcess);
                        }
                    }
                }
                for (ManagedDilutionProcess dilutionProcess: processesToRemove) {
                    completedManagedDilutionProcesses.get(blockchain).remove(dilutionProcess);
                }
            } else {
                log.add("  Not a valid dilutionblock according to dilutionBlockFactory.");
            }
        }
    }

    private ValidationStatus validateDilutionBlock(byte[] message) {
        log.add("validateDilutionBlock()");
        if (dilutionBlockFactory.isValid(message)) {
            byte[] managerPublicKey = dilutionBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] id = dilutionBlockFactory.getChainId(message);
            log.add("  Chain: " + Hex.toHexString(id));
            Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
            if (blockchain != null) {
                log.add("  Blockchain was not null.");
                int numberOfKeys = dilutionBlockFactory.getNumberOfKeys(message);
                int depth = dilutionBlockFactory.getDepth(message);
                if (depth > blockchain.getMaxDepth()) {
                    log.add("  Dilution block depth was too high.");
                    return ValidationStatus.INVALID;
                }
                byte[] anonymitySet = dilutionBlockFactory.getAnonymitySet(message);
                List<byte[]> oldKeys = dilutionBlockFactory.getOldKeys(message, numberOfKeys);
                List<Long> keyOriginBlockIndices = dilutionBlockFactory.getKeyOriginBlockIndices(message, numberOfKeys);
                List<byte[]> signatures = dilutionBlockFactory.getSignatures(message, numberOfKeys);
                byte[] poolIdentifier = dilutionBlockFactory.getPoolIdentifier(message);
                byte[] blockAssembler = dilutionBlockFactory.getBlockAssembler(message);
                long blockAssemblerOriginIndex = dilutionBlockFactory.getBlockAssemblerOriginIndex(message);
                KeyWithOrigin blockAssemblerKeyWithOrigin = new KeyWithOrigin(blockAssembler, blockAssemblerOriginIndex);
                int blockindex = dilutionBlockFactory.getIndex(message);

                if (blockchain.oldKeyIsStillValid(blockAssembler, blockAssemblerOriginIndex, blockindex)) {
                    log.add("  Pool manager key was still valid.");
                    byte[] bytesWithoutValidation = new byte[dilutionBlockFactory.getMinimumMessageLength() + (PrivacyUtils.PUBLIC_KEY_LENGTH * 2 + Block.INDEX_SIZE) * numberOfKeys];
                    for (int i = 0; i < bytesWithoutValidation.length; i++) {
                        bytesWithoutValidation[i] = message[i];
                    }

                    int correctDepth = 0;
                    byte[] correctAnonymitySet = null;
                    if (blockchain.isHeadless()) {
                        correctDepth = depth;
                        correctAnonymitySet = anonymitySet;
                    } else {
                        log.add("  Checking depth and anonymity set.");
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
                        log.add("  Depth is correct.");
                        long dilutionFactor = dilutionBlockFactory.getChainScore(message);
                        if (blockchain.isHeadless() || blockchain.getBlock(blockindex - 1) == null || dilutionFactor == dilutionBlockFactory.getChainScore(keyOriginBlockIndices, blockchain, blockindex)) {
                            log.add("  Dilution factor is correct.");
                            try {
                                boolean signaturesAreValid = true;
                                int i = 0;
                                while (signaturesAreValid && i < oldKeys.size()) {
                                    if (blockchain.isHeadless() || blockchain.oldKeyIsStillValid(oldKeys.get(i), keyOriginBlockIndices.get(i), blockindex)) {
                                        Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(oldKeys.get(i)));
                                        signature.update(bytesWithoutValidation);
                                        if (!signature.verify(signatures.get(i))) {
                                            signaturesAreValid = false;
                                        }
                                    } else {
                                        signaturesAreValid = false;
                                    }
                                    i++;
                                }
                                if (signaturesAreValid) {
                                    log.add("  Signatures are valid.");
                                    ValidationStatus validationStatus = dilutionBlockFactory.checkSuccession(blockchain, message);
                                    if (validationStatus == ValidationStatus.VALID) {
                                        log.add("  Remove blame from pool manager.");
                                        blameCircle.remove(blockAssemblerKeyWithOrigin, blockAssemblerKeyWithOrigin, poolIdentifier);
                                    }
                                    return validationStatus;
                                } else {
                                    log.add("  There is an invalid signature.");
                                }
                            } catch (NoSuchAlgorithmException exception) {
                                log.add("  NoSuchAlgorithmException:");
                                log.add("  " + exception.getMessage());
                            } catch (InvalidKeySpecException exception) {
                                log.add("  InvalidKeySpecException:");
                                log.add("  " + exception.getMessage());
                            } catch (InvalidKeyException exception) {
                                log.add("  InvalidKeyException:");
                                log.add("  " + exception.getMessage());
                            } catch (SignatureException exception) {
                                log.add("  SignatureException:");
                                log.add("  " + exception.getMessage());
                            }
                        }
                    }
                }
            }
        } else {
            log.add("  Failed initial validation check.");
        }
        return ValidationStatus.INVALID;
    }

    private void removeOwnDilutionApplication(Blockchain blockchain) {
        ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
        if (participatingDilutionProcess != null) {
            dilutionProcesses.remove(blockchain);
            completedDilutionProcesses.get(blockchain).add(participatingDilutionProcess);
        }
    }

    private void removePool(Blockchain blockchain) {
        ManagedDilutionProcess managedDilutionProcess = managedDilutionProcesses.get(blockchain);
        if (managedDilutionProcess != null) {
            managedDilutionProcesses.remove(blockchain);
            completedManagedDilutionProcesses.get(blockchain).add(managedDilutionProcess);
        }
    }

    public void createDepthBlock(byte[] managerPublicKey, byte[] chainId, Signature signature) {
        log.add("createDepthBlock()");
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Blockchain was null.");
        } else {
            DepthBlock depthBlock = depthBlockFactory.createBlock(blockchain, signature);
            if (depthBlock != null) {
                byte[] message = depthBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
                log.add("  Created depth block.");
            } else {
                log.add("  DepthBlockFactory returned null block.");
            }
        }
    }

    private void processDepthBlock(byte[] message) {
        log.add("processDepthBlock()");
        byte[] managerPublicKey = depthBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] id = depthBlockFactory.getChainId(message);
        log.add("  Chain: " + Hex.toHexString(id));
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Blockchain was null.");
        } else {
            SuccessorBlock successorBlock = depthBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
            log.add("  Processed depth block.");
            if (successorBlock != null) {
                if (managedDilutionProcesses.containsKey(blockchain)) {
                    ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
                    DilutionBlock ownDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                    if (ownDilutionBlock == null || dilutionProcess.getPoolResponses() == null || dilutionProcess.getPoolResponses().size() == 0 || dilutionProcess.getReceivedNewKeys().size() + dilutionProcess.getSelfIncludingNumber() != dilutionProcess.getPoolResponses().size()) {
                        if (!blockchain.isPoolStillValid(dilutionProcess)) {
                            managedDilutionProcesses.remove(blockchain);
                            attemptToStartDilutionPool(dilutionProcess.getSignatureAsPoolManager(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager(), dilutionProcess.getIdentityForPool(), dilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                        }
                    } else if (!blockchain.isBlockPresent(ownDilutionBlock)) {
                        if (blockchain.isPoolStillValid(dilutionProcess)) {
                            log.add("  Redo last steps of dilution process.");
                            createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 3);
                        } else {
                            managedDilutionProcesses.remove(blockchain);
                            attemptToStartDilutionPool(dilutionProcess.getSignatureAsPoolManager(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager(), dilutionProcess.getIdentityForPool(), dilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                        }
                    }
                }
                HashSet<ManagedDilutionProcess> processesToRemove = new HashSet<>();
                for (ManagedDilutionProcess dilutionProcess: completedManagedDilutionProcesses.get(blockchain)) {
                    DilutionBlock ownDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                    if (ownDilutionBlock == null) {
                        if (!blockchain.isPoolStillValid(dilutionProcess)) {
                            processesToRemove.add(dilutionProcess);
                        }
                    } else if (!blockchain.isBlockPresent(ownDilutionBlock)) {
                        if (blockchain.isPoolStillValid(dilutionProcess)) {
                            log.add("  Redo last steps of dilution process.");
                            createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 4);
                        } else {
                            processesToRemove.add(dilutionProcess);
                        }
                    }
                }
                for (ManagedDilutionProcess dilutionProcess: processesToRemove) {
                    completedManagedDilutionProcesses.get(blockchain).remove(dilutionProcess);
                }
            }
        }
    }

    private ValidationStatus validateDepthBlock(byte[] message) {
        log.add("validateDepthBlock()");
        if (depthBlockFactory.isValid(message)) {
            byte[] managerPublicKey = depthBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] signatureBytes = depthBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(depthBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    byte[] id = depthBlockFactory.getChainId(message);
                    log.add("  Chain: " + Hex.toHexString(id));
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        ValidationStatus validationStatus = depthBlockFactory.checkSuccession(blockchain, message);
                        if (validationStatus == ValidationStatus.VALID) {
                            long chainScore = depthBlockFactory.getChainScore(message);
                            int blockIndex = depthBlockFactory.getIndex(message);
                            if (chainScore != blockchain.getBlock(blockIndex - 1).getChainScore()) {
                                validationStatus = ValidationStatus.INVALID;
                            }
                        }
                        return validationStatus;
                    } else {
                        log.add("  Blockchain was null.");
                    }
                } else {
                    log.add("  Signature of depth block is not valid.");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add(exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add(exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add(exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add(exception.getMessage());
            }
        } else {
            log.add("  Failed initial validation check.");
        }
        return ValidationStatus.INVALID;
    }

    public void createPreDepthBlock(byte[] managerPublicKey, byte[] chainId, Signature signature) {
        log.add("createPreDepthBlock()");
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Failed to create pre-depth block for unknown blockchain");
        } else {
            PreDepthBlock preDepthBlock = preDepthBlockFactory.createBlock(blockchain, signature);
            if (preDepthBlock != null) {
                byte[] message = preDepthBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
                log.add("  Created pre-depth block.");
            } else {
                log.add("  PreDepthBlockFactory returned a null block.");
            }
        }
    }

    private void processPreDepthBlock(byte[] message) {
        log.add("processPreDepthBlock()");
        byte[] managerPublicKey = preDepthBlockFactory.getManagerPublicKey(message);
        byte[] id = preDepthBlockFactory.getChainId(message);
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Blockchain was null.");
        } else {
            SuccessorBlock successorBlock = preDepthBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);


            if (successorBlock != null) {
                if (managedDilutionProcesses.containsKey(blockchain)) {
                    ManagedDilutionProcess dilutionProcess = managedDilutionProcesses.get(blockchain);
                    DilutionBlock ownDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                    if (ownDilutionBlock == null || dilutionProcess.getPoolResponses() == null || dilutionProcess.getPoolResponses().size() == 0 || dilutionProcess.getReceivedNewKeys().size() + dilutionProcess.getSelfIncludingNumber() != dilutionProcess.getPoolResponses().size()) {
                        if (!blockchain.isPoolStillValid(dilutionProcess)) {
                            managedDilutionProcesses.remove(blockchain);
                            attemptToStartDilutionPool(dilutionProcess.getSignatureAsPoolManager(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager(), dilutionProcess.getIdentityForPool(), dilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                        }
                    } else if (!blockchain.isBlockPresent(ownDilutionBlock)) {
                        if (blockchain.isPoolStillValid(dilutionProcess)) {
                            log.add("  Redo last steps of dilution process.");
                            createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 5);
                        } else {
                            managedDilutionProcesses.remove(blockchain);
                            attemptToStartDilutionPool(dilutionProcess.getSignatureAsPoolManager(), dilutionProcess.getPublicKeyAsPoolManager(), dilutionProcess.getKeyOriginBlockIndexAsPoolManager(), dilutionProcess.getIdentityForPool(), dilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                        }
                    }
                }
                HashSet<ManagedDilutionProcess> processesToRemove = new HashSet<>();
                for (ManagedDilutionProcess dilutionProcess: completedManagedDilutionProcesses.get(blockchain)) {
                    DilutionBlock ownDilutionBlock = dilutionProcess.getUnvalidatedDilutionBlock();
                    if (ownDilutionBlock == null) {
                        if (!blockchain.isPoolStillValid(dilutionProcess)) {
                            processesToRemove.add(dilutionProcess);
                        }
                    } else if (!blockchain.isBlockPresent(ownDilutionBlock)) {
                        if (blockchain.isPoolStillValid(dilutionProcess)) {
                            log.add("  Redo last steps of dilution process.");
                            createUnvalidatedDilutionBlock(blockchain, dilutionProcess, 6);
                        } else {
                            processesToRemove.add(dilutionProcess);
                        }
                    }
                }
                for (ManagedDilutionProcess dilutionProcess: processesToRemove) {
                    completedManagedDilutionProcesses.get(blockchain).remove(dilutionProcess);
                }
            }
        }
    }

    private ValidationStatus validatePreDepthBlock(byte[] message) {
        log.add("validatePreDepthBlock()");
        if (preDepthBlockFactory.isValid(message)) {
            byte[] managerPublicKey = preDepthBlockFactory.getManagerPublicKey(message);
            byte[] signatureBytes = preDepthBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(preDepthBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    byte[] id = preDepthBlockFactory.getChainId(message);
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        ValidationStatus validationStatus = preDepthBlockFactory.checkSuccession(blockchain, message);
                        log.add(" Validation status: " + validationStatus.name());
                        if (validationStatus == ValidationStatus.VALID) {
                            long chainScore = preDepthBlockFactory.getChainScore(message);
                            int blockIndex = preDepthBlockFactory.getIndex(message);
                            if (chainScore != blockchain.getBlock(blockIndex - 1).getChainScore()) {
                                validationStatus = ValidationStatus.INVALID;
                            }
                        }
                        log.add(" Validation status: " + validationStatus.name());
                        return validationStatus;
                    }
                } else {
                    log.add("  Signature of pre-depth block is not valid.");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        } else {
            log.add(" Failed initial validation check.");
        }
        return ValidationStatus.INVALID;
    }

    public void createDilutionEndBlock(byte[] managerPublicKey, byte[] chainId, Signature signature, byte[] candidateString) {
        log.add("createDilutionEndBlock()");
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Blockchain was null");
        } else {
            DilutionEndBlock dilutionEndBlock = dilutionEndBlockFactory.createBlock(blockchain, signature, candidateString);
            if (dilutionEndBlock != null) {
                byte[] message = dilutionEndBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
                log.add("  Dilution block was sent.");
            } else {
                log.add("  DilutionEndBlockFactory returned a null block.");
            }
        }
    }

    private void processDilutionEndBlock(byte[] message) {
        log.add("processDilutionEndBlock()");
        byte[] managerPublicKey = dilutionEndBlockFactory.getManagerPublicKey(message);
        byte[] id = dilutionEndBlockFactory.getChainId(message);
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Dilution end block belongs to an unknown chain");
        } else {
            SuccessorBlock successorBlock = dilutionEndBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
            if (successorBlock == null) {
                log.add("  Dilution end block was null after processing");
            } else {
                log.add("  Dilution end block successfully created.");
                DilutionEndBlock dilutionEndBlock = (DilutionEndBlock) successorBlock;
                privacyLayer.setCandidates(managerPublicKey, id, dilutionEndBlock.getCandidateString());
                log.add("  Set candidates on the basis of the dilution end block.");
            }
        }
    }

    private ValidationStatus validateDilutionEndBlock(byte[] message) {
        log.add("validateDilutionEndBlock()");
        if (dilutionEndBlockFactory.isValid(message)) {
            log.add("  Initial validation check.");
            byte[] managerPublicKey = dilutionEndBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] signatureBytes = dilutionEndBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(dilutionEndBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    byte[] id = dilutionEndBlockFactory.getChainId(message);
                    log.add("  Chain: " + Hex.toHexString(id));
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        log.add("  Blockchain was not null");
                        ValidationStatus validationStatus = dilutionEndBlockFactory.checkSuccession(blockchain, message);
                        if (validationStatus == ValidationStatus.VALID) {
                            long chainScore = dilutionEndBlockFactory.getChainScore(message);
                            int blockIndex = dilutionEndBlockFactory.getIndex(message);
                            if (chainScore != blockchain.getBlock(blockIndex - 1).getChainScore() + dilutionEndBlockFactory.getChainScoreIncrease()) {
                                validationStatus = ValidationStatus.INVALID;
                            }
                        }
                        return validationStatus;
                    } else {
                        log.add("  Blockchain was null");
                    }
                } else {
                    log.add("  Signature of dilution end block was not valid");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException");
                log.add("  " + exception.getMessage());
            }
        } else {
            log.add("  Failed initial validation check.");
        }
        return ValidationStatus.INVALID;
    }

    public long createCommitmentBlock(byte[] managerPublicKey, byte[] chainId, Signature signature, byte[] voteHash, byte[] publicKey, long keyOriginBlockIndex) {
        log.add("createCommitmentBlock()");
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        log.add("  Chain: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            System.out.println("createCommitmentBlock()  Failed to create commitment block for unknown blockchain");
            log.add("  Failed to create commitment block for unknown blockchain");
        } else {
            CommitmentBlock commitmentBlock = commitmentBlockFactory.createBlock(blockchain, signature, voteHash, publicKey, keyOriginBlockIndex);
            if (commitmentBlock != null) {
                byte[] message = commitmentBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
                log.add("  Created commitment block.");
                return commitmentBlock.getIndex();
            } else {
                log.add("  CommitmentBlockFactory returned a null block.");
            }
        }
        return -1;
    }

    private void processCommitmentBlock(byte[] message) {
        log.add("processCommitmentBlock()");
        byte[] managerPublicKey = commitmentBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] id = commitmentBlockFactory.getChainId(message);
        log.add("  Chain: " + Hex.toHexString(id));
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("Commitment block belongs to an unknown chain");
        } else {
            commitmentBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
        }
    }

    private ValidationStatus validateCommitmentBlock(byte[] message) {
        log.add("validateCommitmentBlock()");
        if (commitmentBlockFactory.isValid(message)) {
            byte[] publicKey = commitmentBlockFactory.getPublicKey(message);
            log.add("  Voter public key: " + Hex.toHexString(publicKey));
            byte[] signatureBytes = commitmentBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(publicKey));
                signature.update(commitmentBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    byte[] managerPublicKey = commitmentBlockFactory.getManagerPublicKey(message);
                    byte[] id = commitmentBlockFactory.getChainId(message);
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        log.add("  Validated commitment block.");
                        ValidationStatus validationStatus = commitmentBlockFactory.checkSuccession(blockchain, message);
                        if (validationStatus == ValidationStatus.VALID) {
                            long keyOriginBlockIndex = commitmentBlockFactory.getKeyOriginBlockIndex(message);
                            long chainScore = commitmentBlockFactory.getChainScore(message);
                            int blockIndex = commitmentBlockFactory.getIndex(message);
                            long previousChainScore = blockchain.getBlock(blockIndex - 1).getChainScore();
                            if (blockchain.keyWasUsedInCommitment(publicKey, keyOriginBlockIndex, blockchain.getNumberOfBlocks())) {
                                if (chainScore != previousChainScore) {
                                    validationStatus = ValidationStatus.INVALID;
                                }
                            } else {
                                if (chainScore != previousChainScore + 1) {
                                    validationStatus = ValidationStatus.INVALID;
                                }
                            }
                        }
                        return validationStatus;
                    } else {
                        log.add("  Blockchain is null.");
                    }
                } else {
                    log.add("  Signature of commitment block is not valid.");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException:");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        } else {
            log.add("  Initial validation check failed.");
        }
        return ValidationStatus.INVALID;
    }

    public void createCommitmentEndBlock(byte[] managerPublicKey, byte[] chainId, Signature signature) {
        log.add("createCommitmentEndBlock()");
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Failed to create commitment end block for unknown blockchain.");
        } else {
            CommitmentEndBlock commitmentEndBlock = commitmentEndBlockFactory.createBlock(blockchain, signature);
            if (commitmentEndBlock != null) {
                log.add("  commitmentEndBlock != null");
                byte[] message = commitmentEndBlock.getBytes();
                networkLayer.broadcastAnonymously(message);
            } else {
                log.add("  CommitmentEndBlockFactory returned a null block.");
            }
        }
    }

    private void processCommitmentEndBlock(byte[] message) {
        log.add("processCommitmentEndBlock()");
        byte[] managerPublicKey = commitmentEndBlockFactory.getManagerPublicKey(message);
        log.add("  Manager: " + Hex.toHexString(managerPublicKey));
        byte[] id = commitmentEndBlockFactory.getChainId(message);
        log.add("  Chain: " + Hex.toHexString(id));
        Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
        if (blockchain == null) {
            log.add("  Commitment end block belongs to an unknown chain");
        } else {
            log.add("  Found blockchain of commitment end block.");
            SuccessorBlock commitmentEndBlock = commitmentEndBlockFactory.handleSuccessionAndCreateBlock(blockchain, message, privacyLayer);
            if (commitmentEndBlock != null) {
                log.add("  Processed commitment end block.");
                privacyLayer.sendVoteMessages(managerPublicKey, id);
            } else {
                log.add("  CommitmentEndBlockFactory returned a null block.");
            }
        }
    }

    private ValidationStatus validateCommitmentEndBlock(byte[] message) {
        log.add("validateCommitmentEndBlock()");
        if (commitmentEndBlockFactory.isValid(message)) {
            log.add("  Passed first validation check.");
            byte[] managerPublicKey = commitmentEndBlockFactory.getManagerPublicKey(message);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            byte[] signatureBytes = commitmentEndBlockFactory.getSignature(message);
            try {
                Signature signature = PrivacyUtils.createSignatureForVerifying(PrivacyUtils.getPublicKeyFromBytes(managerPublicKey));
                signature.update(commitmentEndBlockFactory.getBytesWithoutValidation(message));
                if (signature.verify(signatureBytes)) {
                    byte[] id = commitmentEndBlockFactory.getChainId(message);
                    log.add("  Chain: " + Hex.toHexString(id));
                    Blockchain blockchain = getBlockchainByIdAndKey(id, managerPublicKey);
                    if (blockchain != null) {
                        log.add("  Blockchain is not null");
                        return commitmentEndBlockFactory.checkSuccession(blockchain, message);
                    } else {
                        log.add("  Blockchain was null");
                    }
                } else {
                    log.add("  Signature of commitment end block is not valid.");
                }
            } catch (NoSuchAlgorithmException exception) {
                log.add("  NoSuchAlgorithmException");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeySpecException exception) {
                log.add("  InvalidKeySpecException");
                log.add("  " + exception.getMessage());
            } catch (InvalidKeyException exception) {
                log.add("  InvalidKeyException:");
                log.add("  " + exception.getMessage());
            } catch (SignatureException exception) {
                log.add("  SignatureException:");
                log.add("  " + exception.getMessage());
            }
        } else {
            log.add("  Failed first validation check.");
        }
        return ValidationStatus.INVALID;
    }

    public void sendVoteMessage(byte[] managerPublicKey, byte[] chainId, byte[] voteContent, byte[] salt, long commitmentBlockIndex) {
        log.add("sendVoteMessage()");
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            log.add("  Blockchain was null.");
        } else {
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(VOTE_MESSAGE_PREFIX);
            messageParts.add(managerPublicKey);
            log.add("  Manager: " + Hex.toHexString(managerPublicKey));
            messageParts.add(chainId);
            log.add("  Chain: " + Hex.toHexString(chainId));
            messageParts.add(ByteUtils.encodeWithLengthByte(voteContent));
            log.add("  Vote: " + Hex.toHexString(voteContent));
            messageParts.add(salt);
            log.add("  Salt: " + Hex.toHexString(salt));
            messageParts.add(ByteUtils.longToByteArray(commitmentBlockIndex));
            log.add("  Commitment block index: " + String.valueOf(commitmentBlockIndex));
            byte[] message = ByteUtils.concatenateByteArrays(messageParts);
            Block originBlock = blockchain.getBlock(commitmentBlockIndex);
            if (originBlock == null) {
                blockchain.print();
            } else if (originBlock.getPrefix()[0] == COMMITMENT_BLOCK_FIRST_CHAR) {
                CommitmentBlock originCommitmentBlock = (CommitmentBlock) originBlock;
                networkLayer.broadcastAnonymously(message);
                log.add("  Sent vote message.");
                privacyLayer.captureVote(voteContent, commitmentBlockIndex, originCommitmentBlock.getPublicKey(), message, managerPublicKey, chainId);
            } else {
                log.add("  Couldn't send vote message because it refers to a block that isn't a commitment block.");
            }
        }
    }

    private void processVoteMessage(byte[] message) {
        log.add("processVoteMessage()");
        int offset = VOTE_MESSAGE_PREFIX.length;
        byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
        offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
        offset += Election.ID_LENGTH;
        log.add("  Chain: " + Hex.toHexString(chainId));
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
        byte[] vote = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
        offset += 1 + vote.length;
        offset += 16;
        byte[] commitmentBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
        long commitmentBlockIndex = ByteUtils.longFromByteArray(commitmentBlockIndexBytes);
        CommitmentBlock originCommitmentBlock = (CommitmentBlock) blockchain.getBlock(commitmentBlockIndex);
        privacyLayer.captureVote(vote, commitmentBlockIndex, originCommitmentBlock.getPublicKey(), message, electionManagerPublicKey, chainId);
    }

    public ValidationStatus validateVoteMessage(byte[] message) {
        log.add("validateVoteMessage()");
        boolean isValid = message.length > VOTE_MESSAGE_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + 1 + 16 + Block.INDEX_SIZE;
        int i = 0;
        while (isValid && i < VOTE_MESSAGE_PREFIX.length) {
            if (VOTE_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            int offset = VOTE_MESSAGE_PREFIX.length;
            byte[] electionManagerPublicKey = ByteUtils.readByteSubstring(message, offset, PrivacyUtils.PUBLIC_KEY_LENGTH);
            offset += PrivacyUtils.PUBLIC_KEY_LENGTH;
            log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
            byte[] chainId = ByteUtils.readByteSubstring(message, offset, Election.ID_LENGTH);
            offset += Election.ID_LENGTH;
            log.add("  Chain: " + Hex.toHexString(chainId));
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                byte[] vote = ByteUtils.readByteSubstringWithLengthEncoded(message, offset);
                offset += 1 + vote.length;
                log.add("  Vote: " + Hex.toHexString(vote));
                byte[] salt = ByteUtils.readByteSubstring(message, offset, 16);
                offset += 16;
                log.add("  Salt: " + Hex.toHexString(salt));
                byte[] commitmentBlockIndexBytes = ByteUtils.readByteSubstring(message, offset, Block.INDEX_SIZE);
                offset += Block.INDEX_SIZE;
                long commitmentBlockIndex = ByteUtils.longFromByteArray(commitmentBlockIndexBytes);
                Block originBlock = blockchain.getBlock(commitmentBlockIndex);
                if (originBlock.getPrefix()[0] == COMMITMENT_BLOCK_FIRST_CHAR) {
                    CommitmentBlock originCommitmentBlock = (CommitmentBlock) originBlock;
                    byte[] correctVoteHash = originCommitmentBlock.getVoteHash();
                    if (ByteUtils.byteArraysAreEqual(PrivacyUtils.hashVote(vote, salt), correctVoteHash)) {
                        return ValidationStatus.VALID;
                    } else {
                        log.add("  Hash was invalid.");
                        return ValidationStatus.INVALID;
                    }
                } else {
                    log.add("  Origin block was not a commitment block.");
                    return ValidationStatus.INVALID;
                }
            } else {
                log.add("  Blockchain was null.");
                return ValidationStatus.INVALID;
            }
        } else {
            log.add("  Failed initial validation check.");
            return ValidationStatus.INVALID;
        }
    }

    public void receiveBroadcastMessage(byte[] message) {
        switch(message[0]) {
            case INITIALIZATION_BLOCK_FIRST_CHAR:
                processInitializationBlock(message);
                break;
            case REGISTRATION_BLOCK_FIRST_CHAR:
                processRegistrationBlock(message);
                break;
            case DISENFRANCHISEMENT_MESSAGE_FIRST_CHAR:
                processDisenfranchisementMessage(message);
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                processDilutionStartBlock(message);
                break;
            case DILUTION_APPLICATION_FIRST_CHAR:
                processDilutionApplication(message);
                break;
            case INVITE_FIRST_CHAR:
                processInvite(message);
                break;
            case RESPONSE_FIRST_CHAR:
                processPoolResponse(message);
                break;
            case POOL_MESSAGE_FIRST_CHAR:
                processPoolMessage(message);
                break;
            case NEW_KEY_MESSAGE_FIRST_CHAR:
                processNewKeyMessage(message);
                break;
            case UNVALIDATED_DILUTION_BLOCK_FIRST_CHAR:
                processUnvalidatedDilutionBlock(message);
                break;
            case SIGNATURE_MESSAGE_FIRST_CHAR:
                processSignatureMessage(message);
                break;
            case BLAME_MESSAGE_FIRST_CHAR:
                processBlameMessage(message);
                break;
            case POOL_RECEIPT_FIRST_CHAR:
                processPoolReceipt(message);
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                processDilutionBlock(message);
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                processDepthBlock(message);
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                processPreDepthBlock(message);
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                processDilutionEndBlock(message);
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                processCommitmentBlock(message);
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                processCommitmentEndBlock(message);
                break;
            case VOTE_MESSAGE_FIRST_CHAR:
                processVoteMessage(message);
                break;
        }
    }

    public ValidationStatus checkValidityOfMessage(byte[] message) {
        log.add("VALIDATE MESSAGE STARTING WITH: " + (char) message[0]);
        ValidationStatus validationStatus = ValidationStatus.INVALID;
        switch(message[0]) {
            case INITIALIZATION_BLOCK_FIRST_CHAR:
                validationStatus = validateInitializationBlock(message);
                break;
            case REGISTRATION_BLOCK_FIRST_CHAR:
                validationStatus =  validateRegistrationBlock(message);
                break;
            case DISENFRANCHISEMENT_MESSAGE_FIRST_CHAR:
                validationStatus =  validateDisenfranchisementMessage(message);
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                validationStatus =  validateDilutionStartBlock(message);
                break;
            case DILUTION_APPLICATION_FIRST_CHAR:
                validationStatus =  validateDilutionApplication(message);
                break;
            case INVITE_FIRST_CHAR:
                validationStatus =  validateInvite(message);
                break;
            case RESPONSE_FIRST_CHAR:
                validationStatus =  validatePoolResponse(message);
                break;
            case POOL_MESSAGE_FIRST_CHAR:
                validationStatus =  validatePoolMessage(message);
                break;
            case NEW_KEY_MESSAGE_FIRST_CHAR:
                validationStatus =  validateNewKeyMessage(message);
                break;
            case UNVALIDATED_DILUTION_BLOCK_FIRST_CHAR:
                validationStatus =  validateUnvalidatedDilutionBlock(message);
                break;
            case SIGNATURE_MESSAGE_FIRST_CHAR:
                validationStatus =  validateSignatureMessage(message);
                break;
            case BLAME_MESSAGE_FIRST_CHAR:
                validationStatus =  validateBlameMessage(message);
                break;
            case POOL_RECEIPT_FIRST_CHAR:
                validationStatus =  validatePoolReceipt(message);
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                validationStatus =  validateDilutionBlock(message);
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                validationStatus =  validateDepthBlock(message);
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                validationStatus =  validatePreDepthBlock(message);
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                validationStatus =  validateDilutionEndBlock(message);
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                validationStatus =  validateCommitmentBlock(message);
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                validationStatus =  validateCommitmentEndBlock(message);
                break;
            case CHAIN_REQUEST_FIRST_CHAR:
                validationStatus =  validateChainRequest(message);
                break;
            case VOTE_MESSAGE_FIRST_CHAR:
                validationStatus =  validateVoteMessage(message);
                break;
        }
        return validationStatus;
    }

    public boolean isChainScoreHighEnough(byte[] message) {
        SuccessorBlockFactory successorBlockFactory = null;
        switch (message[0]) {
            case REGISTRATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = registrationBlockFactory;
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionStartBlockFactory;
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionBlockFactory;
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = depthBlockFactory;
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = preDepthBlockFactory;
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionEndBlockFactory;
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentBlockFactory;
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentEndBlockFactory;
                break;
        }
        if (successorBlockFactory == null) {
            return false;
        } else {
            byte[] electionManagerPublicKey = successorBlockFactory.getManagerPublicKey(message);
            byte[] chainId = successorBlockFactory.getChainId(message);
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain == null) {
                return false;
            } else {
                long chainScore = successorBlockFactory.getChainScore(message);
                if (headlessChains.containsKey(blockchain)) {
                    BlockchainCircle headlessChainsForBlockchain = headlessChains.get(blockchain);
                    byte[] hash = null;
                    try {
                        MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
                        hash = messageDigest.digest(message);
                    } catch (NoSuchAlgorithmException exception) {
                        log.add("  NoSuchAlgorithmException when hashing in isChainScoreHighEnough()");
                        log.add("  " + exception);
                    }
                    if (hash != null) {
                        Blockchain headlessChain = headlessChainsForBlockchain.get(hash);
                        if (headlessChain != null) {
                            chainScore = headlessChain.getLastBlock().getChainScore();
                        }
                    }
                }
                if (chainScore > blockchain.getLastBlock().getChainScore()) {
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    public byte[] getPredecessorMessage(byte[] message) {
        SuccessorBlockFactory successorBlockFactory = null;
        switch (message[0]) {
            case REGISTRATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = registrationBlockFactory;
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionStartBlockFactory;
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionBlockFactory;
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = depthBlockFactory;
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = preDepthBlockFactory;
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionEndBlockFactory;
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentBlockFactory;
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentEndBlockFactory;
                break;
        }
        if (successorBlockFactory == null) {
            return null;
        } else {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
                byte[] hash = messageDigest.digest(message);
                byte[] electionManagerPublicKey = successorBlockFactory.getManagerPublicKey(message);
                byte[] chainId = successorBlockFactory.getChainId(message);
                Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
                if (blockchain == null) {
                    return null;
                }
                byte[] predecessorHash = successorBlockFactory.getPredecessorHash(message);
                byte[] predecessorIndex = ByteUtils.longToByteArray(successorBlockFactory.getIndex(message) - 1);
                BlockchainCircle headlessChainsForThisBlockchain = headlessChains.get(blockchain);
                Blockchain headlessChain = headlessChainsForThisBlockchain.get(hash);
                if (headlessChain == null) {
                    headlessChain = new Blockchain(chainId, electionManagerPublicKey);
                    headlessChain.setDilutionBlockFactory(dilutionBlockFactory);
                    headlessChain.setCommitmentBlockFactory(commitmentBlockFactory);
                } else {
                    //TODO: Remove at hash?
                }
                SuccessorBlock block = successorBlockFactory.createBlock(headlessChain, message);
                headlessChain.prependBlock(block);
                headlessChainsForThisBlockchain.add(predecessorHash, headlessChain);
                ArrayList<byte[]> messageParts = new ArrayList<>();
                messageParts.add(BLOCK_REQUEST_PREFIX);
                messageParts.add(electionManagerPublicKey);
                messageParts.add(chainId);
                messageParts.add(predecessorIndex);
                messageParts.add(predecessorHash);
                return ByteUtils.concatenateByteArrays(messageParts);
            } catch (NoSuchAlgorithmException exception) {
                log.add("No such algorithm for hash in get predecessor");
                return null;
            }
        }
    }

    public byte[] getBlockFromRequestMessage(byte[] requestMessage) {
        boolean isValid = requestMessage.length == BLOCK_REQUEST_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + Block.INDEX_SIZE + PrivacyUtils.HASH_LENGTH;
        int i = 0;
        while (isValid && i < BLOCK_REQUEST_PREFIX.length) {
            if (BLOCK_REQUEST_PREFIX[i] != requestMessage[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            byte[] electionManagerPublicKey = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
            for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
                electionManagerPublicKey[j] = requestMessage[BLOCK_REQUEST_PREFIX.length + j];
            }
            byte[] chainId = new byte[Election.ID_LENGTH];
            for (int j = 0; j < Election.ID_LENGTH; j++) {
                chainId[j] = requestMessage[BLOCK_REQUEST_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + j];
            }
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain == null) {
                return null;
            } else {
                byte[] blockIndexBytes = new byte[Block.INDEX_SIZE];
                for (int j = 0; j < Block.INDEX_SIZE; j++) {
                    blockIndexBytes[j] = requestMessage[BLOCK_REQUEST_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + j];
                }
                long blockIndex = ByteUtils.longFromByteArray(blockIndexBytes);
                Block block = blockchain.getBlock(blockIndex);
                if (block == null) {
                    return null;
                } else {
                    byte[] blockHash = new byte[PrivacyUtils.HASH_LENGTH];
                    for (int j = 0; j < PrivacyUtils.HASH_LENGTH; j++) {
                        blockHash[j] = requestMessage[BLOCK_REQUEST_PREFIX.length + PrivacyUtils.PUBLIC_KEY_LENGTH + Election.ID_LENGTH + Block.INDEX_SIZE + j];
                    }
                    if (ByteUtils.byteArraysAreEqual(blockHash, block.getHash())) {
                        ArrayList<byte[]> messageParts = new ArrayList<>();
                        messageParts.add(REQUESTED_BLOCK_PREFIX);
                        messageParts.add(blockHash);
                        messageParts.add(block.getBytes());
                        return ByteUtils.concatenateByteArrays(messageParts);
                    } else {
                        return null;
                    }
                }
            }
        } else {
            return null;
        }
    }

    public void copyFromFork(byte[] message, ValidationStatus validationStatus) {
        BlockFactory successorBlockFactory = null;
        switch (message[0]) {
            case REGISTRATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = registrationBlockFactory;
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionStartBlockFactory;
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionBlockFactory;
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = depthBlockFactory;
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = preDepthBlockFactory;
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionEndBlockFactory;
                break;
            case INITIALIZATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = initializationBlockFactory;
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentBlockFactory;
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentEndBlockFactory;
                break;
        }
        if (successorBlockFactory != null) {
            byte[] electionManagerPublicKey = successorBlockFactory.getManagerPublicKey(message);
            byte[] chainId = successorBlockFactory.getChainId(message);
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                try {
                    MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
                    byte[] hash = messageDigest.digest(message);
                    BlockchainCircle headlessChainsForBlockchain = headlessChains.get(blockchain);
                    Blockchain headlessChain = headlessChainsForBlockchain.get(hash);
                    if (headlessChain != null) {
                        headlessChainsForBlockchain.remove(hash);
                        Block block = successorBlockFactory.createBlock(headlessChain, message);
                        headlessChain.prependBlock(block);
                        long forkIndex = block.getIndex();
                        Block forkBlock = blockchain.getBlock(forkIndex);
                        boolean commitmentBlockFork = false;
                        if (forkBlock != null) {
                            commitmentBlockFork = block.getPrefix()[0] == 'C' && forkBlock.getPrefix()[0] == 'C';
                        }
                        boolean goingToCopy = true;
                        long headlessChainScore = headlessChain.getLastBlock().getChainScore();
                        long currentChainScore = blockchain.getLastBlock().getChainScore();
                        switch (validationStatus) {
                            case LOSING_FORK:
                            case EQUAL_FORK:
                                if (headlessChainScore < currentChainScore) {
                                    goingToCopy = false;
                                } else if (headlessChainScore == currentChainScore) {
                                    // if blocks are commitment blocks: if headless chain length < current chain length, false
                                    if (commitmentBlockFork && headlessChain.getLastBlock().getIndex() < blockchain.getLastBlock().getIndex()) {
                                        goingToCopy = false;
                                    }
                                }
                                break;
                            case LOSING_FORK_LOSES_SECONDARY_CHECK:
                            case LOSES_SECONDARY_CHECK:
                                if (headlessChainScore < currentChainScore) {
                                    goingToCopy = false;
                                } else if (headlessChainScore == currentChainScore) {
                                    if (commitmentBlockFork) {
                                        if (headlessChain.getLastBlock().getIndex() <= blockchain.getLastBlock().getIndex()) {
                                            goingToCopy = false;
                                        }
                                    } else {
                                        goingToCopy = false;
                                    }
                                }
                        }
                        if (goingToCopy) {
                            if (blockchain.copyFromFork(headlessChain)) {
                                privacyLayer.invalidateKeyBlockIndicesAfterIndex(forkIndex, electionManagerPublicKey, chainId);
                                networkLayer.broadcastAnonymously(headlessChain.getLastBlock().getBytes());
                                privacyLayer.checkRegistrations(electionManagerPublicKey, chainId, forkIndex);
                            }
                        }
                    }
                } catch (NoSuchAlgorithmException exception) {
                    log.add("No such algorithm for hash in get copyFromFork");
                }
            }
        }
    }

    public void deleteHeadlessChain(byte[] message) {
        SuccessorBlockFactory successorBlockFactory = null;
        switch (message[0]) {
            case REGISTRATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = registrationBlockFactory;
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionStartBlockFactory;
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionBlockFactory;
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = depthBlockFactory;
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = preDepthBlockFactory;
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionEndBlockFactory;
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentBlockFactory;
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentEndBlockFactory;
                break;
        }
        if (successorBlockFactory != null) {
            byte[] electionManagerPublicKey = successorBlockFactory.getManagerPublicKey(message);
            byte[] chainId = successorBlockFactory.getChainId(message);
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                try {
                    MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
                    byte[] hash = messageDigest.digest(message);
                    BlockchainCircle headlessChainsForBlockchain = headlessChains.get(blockchain);
                    headlessChainsForBlockchain.remove(hash);
                } catch (NoSuchAlgorithmException exception) {
                    log.add("No such algorithm for hash in get deleteHeadlessChain");
                }
            }
        }
    }

    public void printBlockchain(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            blockchain.print();
        }
    }

    public void printAllBlockchains() {
        for (Blockchain blockchain: blockchains) {
            blockchain.print();
        }
    }

    public List<Block> getBlockchain(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        } else {
            return blockchain.getBlocks();
        }
    }

    public List<Block> getBlockchainFromIndex(byte[] managerPublicKey, byte[] chainId, long startIndex) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        } else {
            return blockchain.getBlocksFromIndex(startIndex);
        }
    }

    public ElectionPhase getElectionPhase(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        } else {
            return blockchain.getElectionPhase();
        }
    }

    public void printLog() {
        System.out.println("Size: " + log.size());
        for (String message: log) {
            System.out.println(message);
        }
    }

    private byte[] getAnonymitySet(long blockIndex, Blockchain blockchain) {
        byte[] anonymitySet = null;
        Block block = blockchain.getBlock(blockIndex);
        switch (block.getPrefix()[0]) {
            case DILUTION_BLOCK_FIRST_CHAR:
                DilutionBlock dilutionBlock = (DilutionBlock) block;
                anonymitySet = dilutionBlock.getAnonymitySet();
                break;
            case REGISTRATION_BLOCK_FIRST_CHAR:
                RegistrationBlock registrationBlock = (RegistrationBlock) block;
                anonymitySet = registrationBlock.getPublicKey();
                break;
        }
        return anonymitySet;
    }


    public synchronized void update() {
        Random random = new Random();
        for (Blockchain blockchain: blockchains) {
            updateCounter++;
            ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
            if (participatingDilutionProcess != null) {
                incrementInviteAcceptableBlame(participatingDilutionProcess.getPublicKeyForDilutionApplication());
                boolean isLeftOver = false;
                if (blockchain.isInPreDepthPhase()) {
                    Block originBlock = blockchain.getBlock(participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication());
                    switch (originBlock.getPrefix()[0]) {
                        case REGISTRATION_BLOCK_FIRST_CHAR:
                            isLeftOver = true;
                            break;
                        case DILUTION_BLOCK_FIRST_CHAR:
                            DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                            if (originDilutionBlock.getDepth() < blockchain.getMaxDepth() - 1) {
                                isLeftOver = true;
                            }
                            break;
                    }
                }
                if (isLeftOver || TimeUtils.getMillis() > participatingDilutionProcess.getLastModifiedTime() + DILUTION_PROCESS_TIMEOUT) {
                    updateParticipatingCounter++;
                    byte[] ownAnonymitySet = getAnonymitySet(participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication(), blockchain);
                    Invite backupInvite = null;
                    if (!isLeftOver) {
                        backupInvite = participatingDilutionProcess.popBackupInvite();
                        boolean foundBackupInvite = false;
                        while (backupInvite != null && ownAnonymitySet != null && !foundBackupInvite) {
                            byte[] anonymitySet = getAnonymitySet(backupInvite.getKeyOriginBlockIndexOfPoolManager(), blockchain);
                            if (anonymitySet != null
                                    && !ByteUtils.byteArraysAreEqual(ownAnonymitySet, anonymitySet)
                                    && blameCircle.getBlameFactor(new KeyWithOrigin(backupInvite.getPublicKeyOfPoolManager(), backupInvite.getKeyOriginBlockIndexOfPoolManager())) <= getInviteAcceptableBlame(participatingDilutionProcess.getPublicKeyForDilutionApplication())) {
                                foundBackupInvite = true;
                            } else {
                                backupInvite = participatingDilutionProcess.popBackupInvite();
                            }
                        }
                    }
                    if (backupInvite != null) {
                        byte[] currentPoolIdentifier = backupInvite.getPoolIdentifier();
                        byte[] publicKeyOfPoolManager = backupInvite.getPublicKeyOfPoolManager();
                        long keyOriginBlockIndexOfPoolManager = backupInvite.getKeyOriginBlockIndexOfPoolManager();
                        participatingDilutionProcess.setCurrentPoolIdentifier(currentPoolIdentifier);
                        participatingDilutionProcess.setPublicKeyOfPoolManager(publicKeyOfPoolManager);
                        participatingDilutionProcess.setKeyOriginBlockIndexOfPoolManager(keyOriginBlockIndexOfPoolManager);
                        participatingDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                        sendPoolResponse(blockchain, currentPoolIdentifier, publicKeyOfPoolManager, keyOriginBlockIndexOfPoolManager, backupInvite.getDilutionApplicationSignature(), backupInvite.getInviteSignature(), participatingDilutionProcess);
                    } else if (isLeftOver || participatingDilutionProcess.getSwitchCounter() >= MAXIMUM_SWITCH_COUNTER_FROM_APPLICATION || random.nextInt(MAXIMUM_SWITCH_COUNTER_FROM_APPLICATION) == 0) {
                        dilutionProcesses.remove(blockchain);
                        attemptToStartDilutionPool(privacyLayer.getSignatureForPublicKey(participatingDilutionProcess.getPublicKeyForDilutionApplication()), participatingDilutionProcess.getPublicKeyForDilutionApplication(), participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication(), participatingDilutionProcess.getIdentityForPool(), true, blockchain.getManagerPublicKey(), blockchain.getId());
                        timesSwitchedBetweenPoolRole++;
                    } else {
                        ArrayList<byte[]> messageParts = new ArrayList<>();
                        messageParts.add(DILUTION_APPLICATION_PREFIX);
                        messageParts.add(blockchain.getManagerPublicKey());
                        messageParts.add(blockchain.getId());
                        byte[] message = ByteUtils.concatenateByteArrays(messageParts);
                        byte[] keyToDilute = participatingDilutionProcess.getPublicKeyForDilutionApplication();
                        long keyOriginBlockIndexToDilute = participatingDilutionProcess.getKeyOriginBlockIndexForDilutionApplication();
                        Signature signature = privacyLayer.getSignatureForPublicKey(keyToDilute);
                        try {
                            signature.update(message);
                            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(signature.sign());
                            ArrayList<byte[]> byteArrays = new ArrayList<>();
                            byteArrays.add(message);
                            byteArrays.add(keyToDilute);
                            byteArrays.add(ByteUtils.longToByteArray(keyOriginBlockIndexToDilute));
                            byteArrays.add(signatureBytes);
                            participatingDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                            participatingDilutionProcess.setCurrentPoolIdentifier(null);
                            participatingDilutionProcess.setPublicKeyOfPoolManager(null);
                            participatingDilutionProcess.setKeyOriginBlockIndexOfPoolManager(0);
                            networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(byteArrays));
                        } catch (SignatureException exception) {
                            log.add("SignatureException when resending dilution application.");
                        }
                        participatingDilutionProcess.incrementSwitchCounter();
                    }
                }
            }
            ManagedDilutionProcess managedDilutionProcess = managedDilutionProcesses.get(blockchain);
            if (managedDilutionProcess != null) {
                boolean isLeftOver = false;
                if (blockchain.isInPreDepthPhase()) {
                    Block originBlock = blockchain.getBlock(managedDilutionProcess.getKeyOriginBlockIndexAsPoolManager());
                    switch (originBlock.getPrefix()[0]) {
                        case REGISTRATION_BLOCK_FIRST_CHAR:
                            isLeftOver = true;
                            break;
                        case DILUTION_BLOCK_FIRST_CHAR:
                            DilutionBlock originDilutionBlock = (DilutionBlock) originBlock;
                            if (originDilutionBlock.getDepth() < blockchain.getMaxDepth() - 1) {
                                isLeftOver = true;
                            }
                            break;
                    }
                }
                if (TimeUtils.getMillis() > managedDilutionProcess.getLastModifiedTime() + DILUTION_PROCESS_TIMEOUT) {
                    updateManagedCounter++;
                    if ((blockchain.isInPreDepthPhase() && !isLeftOver) || (!blockchain.isInPreDepthPhase() && (managedDilutionProcess.getSwitchCounter() >= MAXIMUM_SWITCH_COUNTER_FROM_POOL || random.nextInt(MAXIMUM_SWITCH_COUNTER_FROM_POOL) == 0))) {
                        managedDilutionProcesses.remove(blockchain);
                        sendDilutionApplication(blockchain.getManagerPublicKey(), blockchain.getId(), managedDilutionProcess.getPublicKeyForDilutionApplication(), managedDilutionProcess.getKeyOriginBlockIndexAsPoolManager(), managedDilutionProcess.getSignatureAsPoolManager(), managedDilutionProcess.getIdentityForPool());
                        timesSwitchedBetweenPoolRole++;
                    } else {
                        HashSet<PoolResponse> poolResponses = managedDilutionProcess.getPoolResponses();
                        managedDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                        if (poolResponses != null) {
                            DilutionApplicationCircle selectedApplications = new DilutionApplicationCircle(HASH_CIRCLE_SIZE, PrivacyUtils.PUBLIC_KEY_LENGTH);
                            Block managerOriginBlock = blockchain.getBlock(managedDilutionProcess.getKeyOriginBlockIndexAsPoolManager());
                            byte[] managerAnonymitySet = null;
                            switch (managerOriginBlock.getPrefix()[0]) {
                                case REGISTRATION_BLOCK_FIRST_CHAR:
                                    RegistrationBlock managerOriginRegistrationBlock = (RegistrationBlock) managerOriginBlock;
                                    managerAnonymitySet = managerOriginRegistrationBlock.getPublicKey();
                                    break;
                                case DILUTION_BLOCK_FIRST_CHAR:
                                    DilutionBlock managerOriginDilutionBlock = (DilutionBlock) managerOriginBlock;
                                    managerAnonymitySet = managerOriginDilutionBlock.getAnonymitySet();
                                    break;
                            }
                            DilutionApplicationCircle dilutionApplicationsForChain = dilutionApplications.get(blockchain);
                            for (DilutionApplication dilutionApplication : dilutionApplicationsForChain) {
                                Block inviteeOriginBlock = blockchain.getBlock(dilutionApplication.getKeyWithOrigin().getKeyOriginBlockIndex());
                                byte[] inviteeAnonymitySet = null;
                                switch (inviteeOriginBlock.getPrefix()[0]) {
                                    case REGISTRATION_BLOCK_FIRST_CHAR:
                                        RegistrationBlock inviteeOriginRegistrationBlock = (RegistrationBlock) inviteeOriginBlock;
                                        inviteeAnonymitySet = inviteeOriginRegistrationBlock.getPublicKey();
                                        break;
                                    case DILUTION_BLOCK_FIRST_CHAR:
                                        DilutionBlock inviteeOriginDilutionBlock = (DilutionBlock) inviteeOriginBlock;
                                        inviteeAnonymitySet = inviteeOriginDilutionBlock.getAnonymitySet();
                                        break;
                                }
                                boolean canAdd = managerAnonymitySet != null && inviteeAnonymitySet != null && !ByteUtils.byteArraysAreEqual(managerAnonymitySet, inviteeAnonymitySet);
                                for (PoolResponse poolResponse : poolResponses) {
                                    // TODO: check anonymity set
                                    if (dilutionApplication.getKeyWithOrigin().getKeyOriginBlockIndex() == poolResponse.getOldKeyOriginBlockIndex()) {
                                        canAdd = false;
                                    }
                                }
                                for (DilutionApplication existingApplication : selectedApplications) {
                                    if (existingApplication == dilutionApplication) {
                                        canAdd = false;
                                    }
                                }
                                if (canAdd) {
                                    selectedApplications.add(dilutionApplication);
                                }
                            }

                            if (poolResponses.size() + managedDilutionProcess.getSelfIncludingNumber() < managedDilutionProcess.getDesiredPoolSize()) {
                                managedDilutionProcess.setAssemblingDilutionPool(true);
                                ArrayList<DilutionApplication> invitees = new ArrayList<>();
                                while (selectedApplications.getSize() > 0 && invitees.size() + managedDilutionProcess.getSelfIncludingNumber() + poolResponses.size() < managedDilutionProcess.getDesiredPoolSize()) {
                                    DilutionApplication dilutionApplication = selectedApplications.pop(true, blameCircle);
                                    dilutionApplicationsForChain.remove(dilutionApplication.getKeyWithOrigin().getPublicKey());
                                    invitees.add(dilutionApplication);
                                }

                                for (DilutionApplication invitee : invitees) {
                                    ArrayList<byte[]> messageToSignParts = new ArrayList<>();
                                    messageToSignParts.add(INVITE_PREFIX);
                                    messageToSignParts.add(blockchain.getManagerPublicKey());
                                    messageToSignParts.add(blockchain.getId());
                                    messageToSignParts.add(invitee.getKeyWithOrigin().getPublicKey());
                                    messageToSignParts.add(ByteUtils.longToByteArray(invitee.getKeyWithOrigin().getKeyOriginBlockIndex()));
                                    messageToSignParts.add(managedDilutionProcess.getCurrentPoolIdentifier());
                                    messageToSignParts.add(ByteUtils.encodeWithLengthByte(invitee.getSignature()));
                                    log.add("    Public key: " + Hex.toHexString(invitee.getKeyWithOrigin().getPublicKey()));
                                    log.add("    Key origin block: " + String.valueOf(invitee.getKeyWithOrigin().getKeyOriginBlockIndex()));
                                    byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
                                    try {
                                        Signature signatureAsPoolManager = managedDilutionProcess.getSignatureAsPoolManager();
                                        signatureAsPoolManager.update(messageToSign);
                                        byte[] signature = ByteUtils.encodeWithLengthByte(signatureAsPoolManager.sign());
                                        ArrayList<byte[]> messageParts = new ArrayList<>();
                                        messageParts.add(messageToSign);
                                        messageParts.add(managedDilutionProcess.getPublicKeyAsPoolManager());
                                        messageParts.add(ByteUtils.longToByteArray(managedDilutionProcess.getKeyOriginBlockIndexAsPoolManager()));
                                        messageParts.add(signature);
                                        managedDilutionProcess.setLastModifiedTime(TimeUtils.getMillis());
                                        networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
                                        log.add("    Sent invite.");
                                    } catch (SignatureException exception) {
                                        log.add("    Couldn't sign invite due to SignatureException");
                                    }
                                }
                            } else {
                                attemptToStartDilutionPool(managedDilutionProcess.getSignatureAsPoolManager(), managedDilutionProcess.getPublicKeyAsPoolManager(), managedDilutionProcess.getKeyOriginBlockIndexAsPoolManager(), managedDilutionProcess.getIdentityForPool(), managedDilutionProcess.isIncludeSelf(), blockchain.getManagerPublicKey(), blockchain.getId());
                            }
                        }
                        managedDilutionProcess.incrementSwitchCounter();
                    }
                }
            }
        }
    }

    public DilutionApplicationCircle getDilutionApplications(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        } else {
            return dilutionApplications.get(blockchain);
        }
    }

    public boolean isPartOfDilutionProcess(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return false;
        } else {
            return dilutionProcesses.containsKey(blockchain);
        }
    }

    public boolean ownsDilutionProcess(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return false;
        } else {
            return managedDilutionProcesses.containsKey(blockchain);
        }
    }

    public RegistrationBlock getRegistrationBlock(byte[] managerPublicKey, byte[] chainId, byte[] publicKey, long startIndex) {
        RegistrationBlock registrationBlock = null;
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            long i = startIndex;
            while (registrationBlock == null && i < blockchain.getNumberOfBlocks()) {
                Block block = blockchain.getBlock(i);
                if (block.getPrefix()[0] == REGISTRATION_BLOCK_FIRST_CHAR) {
                    RegistrationBlock currentRegistrationBlock = (RegistrationBlock) block;
                    if (ByteUtils.byteArraysAreEqual(currentRegistrationBlock.getPublicKey(), publicKey)) {
                        registrationBlock = currentRegistrationBlock;
                    }
                }
                i++;
            }
        }
        return registrationBlock;
    }

    public void sendDisenfranchisementMessage(byte[] managerPublicKey, byte[] chainId, RegistrationBlock registrationBlock) {
        log.add("sendDisenfranchisementMessage()");
        ArrayList<byte[]> messageParts = new ArrayList<>();
        messageParts.add(DISENFRANCHISEMENT_MESSAGE_PREFIX);
        messageParts.add(registrationBlock.getBytes());
        networkLayer.broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
    }

    public ValidationStatus validateDisenfranchisementMessage(byte[] message) {
        log.add("validateDisenfranchisementMessage()");
        boolean isValid = message.length > DISENFRANCHISEMENT_MESSAGE_PREFIX.length + registrationBlockFactory.getMinimumMessageLength();
        int i = 0;
        while (isValid && i < DISENFRANCHISEMENT_MESSAGE_PREFIX.length) {
            if (DISENFRANCHISEMENT_MESSAGE_PREFIX[i] != message[i]) {
                isValid = false;
            }
            i++;
        }
        if (isValid) {
            byte[] registrationBlockMessage = ByteUtils.readByteSubstring(message, DISENFRANCHISEMENT_MESSAGE_PREFIX.length, message.length - DISENFRANCHISEMENT_MESSAGE_PREFIX.length);
            ValidationStatus registrationBlockValidation = validateRegistrationBlock(registrationBlockMessage);
            switch (registrationBlockValidation) {
                case VALID:
                case LOSING_FORK:
                case LOSES_PRIMARY_CHECK:
                    //Check if the same voter id wasn't registered later
                    byte[] voterId = registrationBlockFactory.getVoterId(registrationBlockMessage);
                    log.add("  Voter: " + Hex.toHexString(voterId));
                    int blockIndex = registrationBlockFactory.getIndex(registrationBlockMessage);
                    log.add("  Original block index: " + String.valueOf(blockIndex));
                    byte[] electionManagerPublicKey = registrationBlockFactory.getManagerPublicKey(registrationBlockMessage);
                    log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
                    byte[] chainId = registrationBlockFactory.getChainId(registrationBlockMessage);
                    log.add("  Chain: " + Hex.toHexString(chainId));
                    Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
                    if (blockchain != null && !blockchain.voterIdIsRegistered(voterId)) {
                        return ValidationStatus.VALID;
                    } else {
                        log.add("  Disenfranchisement message was invalid.");
                    }
                    break;
                case NEED_PREDECESSOR:
                    //TODO: Request predecessors
                case INVALID:
                    log.add("  Internal registration block was invalid.");
            }
        } else {
            log.add("  Failed initial validation check.");
        }
        return ValidationStatus.INVALID;
    }

    public void processDisenfranchisementMessage(byte[] message) {
        log.add("processDisenfranchisementMessage()");
        byte[] registrationBlockMessage = ByteUtils.readByteSubstring(message, DISENFRANCHISEMENT_MESSAGE_PREFIX.length, message.length - DISENFRANCHISEMENT_MESSAGE_PREFIX.length);
        byte[] voterId = registrationBlockFactory.getVoterId(registrationBlockMessage);
        log.add("  Voter: " + Hex.toHexString(voterId));
        byte[] electionManagerPublicKey = registrationBlockFactory.getManagerPublicKey(registrationBlockMessage);
        log.add("  Manager: " + Hex.toHexString(electionManagerPublicKey));
        byte[] chainId = registrationBlockFactory.getChainId(registrationBlockMessage);
        log.add("  Chain: " + Hex.toHexString(chainId));
        privacyLayer.addDisenfranchisedVoter(electionManagerPublicKey, chainId, voterId);
    }

    public byte[] getLastBlock(byte[] message) {
        SuccessorBlockFactory successorBlockFactory = null;
        switch (message[0]) {
            case REGISTRATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = registrationBlockFactory;
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionStartBlockFactory;
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionBlockFactory;
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = depthBlockFactory;
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = preDepthBlockFactory;
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionEndBlockFactory;
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentBlockFactory;
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentEndBlockFactory;
                break;
        }
        if (successorBlockFactory != null) {
            byte[] electionManagerPublicKey = successorBlockFactory.getManagerPublicKey(message);
            byte[] chainId = successorBlockFactory.getChainId(message);
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                return blockchain.getLastBlock().getBytes();
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean isInPredepthPhase(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return false;
        } else {
            return blockchain.isInPreDepthPhase();
        }
    }

    public void undoLastBlock(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            blockchain.discardLastBlock();
        }
    }

    public List<String> getLog() {
        return log;
    }

    public void squeeze() {
        networkLayer.squeeze();
    }

    public int getBlockDilutionDepth(long blockIndex, byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return -1;
        }
        Block block = blockchain.getBlock(blockIndex);
        if (block != null && block.getPrefix()[0] == DILUTION_BLOCK_FIRST_CHAR) {
            DilutionBlock dilutionBlock = (DilutionBlock) block;
            return dilutionBlock.getDepth();
        } else {
            return -1;
        }
    }

    public boolean appendToPredecessorInHeadlessChains(byte[] message) {
        SuccessorBlockFactory successorBlockFactory = null;
        switch (message[0]) {
            case REGISTRATION_BLOCK_FIRST_CHAR:
                successorBlockFactory = registrationBlockFactory;
                break;
            case DILUTION_START_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionStartBlockFactory;
                break;
            case DILUTION_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionBlockFactory;
                break;
            case DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = depthBlockFactory;
                break;
            case PRE_DEPTH_BLOCK_FIRST_CHAR:
                successorBlockFactory = preDepthBlockFactory;
                break;
            case DILUTION_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = dilutionEndBlockFactory;
                break;
            case COMMITMENT_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentBlockFactory;
                break;
            case COMMITMENT_END_BLOCK_FIRST_CHAR:
                successorBlockFactory = commitmentEndBlockFactory;
                break;
        }
        if (successorBlockFactory != null) {
            byte[] electionManagerPublicKey = successorBlockFactory.getManagerPublicKey(message);
            byte[] chainId = successorBlockFactory.getChainId(message);
            Blockchain blockchain = getBlockchainByIdAndKey(chainId, electionManagerPublicKey);
            if (blockchain != null) {
                int index = successorBlockFactory.getIndex(message);
                byte[] predecessorHash = successorBlockFactory.getPredecessorHash(message);
                    BlockchainCircle headlessChainsForBlockchain = headlessChains.get(blockchain);
                    Iterator<Blockchain> iterator = headlessChainsForBlockchain.iterator();
                    Blockchain headlessChain = null;
                    while (iterator.hasNext() && headlessChain == null) {
                        Blockchain currentHeadlessChain = iterator.next();
                        Block lastBlock = currentHeadlessChain.getLastBlock();
                        if (lastBlock.getIndex() == index - 1 && ByteUtils.byteArraysAreEqual(lastBlock.getHash(), predecessorHash)) {
                            headlessChain = currentHeadlessChain;
                        }
                    }
                    if (headlessChain != null) {
                        return successorBlockFactory.handleSuccessionAndCreateBlock(headlessChain, message, null) != null;
                    }
            }
        }
        return false;
    }

    public boolean verifyKeyOrigin(long blockIndex, byte[] publicKey, byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            Block block = blockchain.getBlock(blockIndex);
            if (block != null) {
                switch (block.getPrefix()[0]) {
                    case REGISTRATION_BLOCK_FIRST_CHAR:
                        RegistrationBlock registrationBlock = (RegistrationBlock) block;
                        return ByteUtils.byteArraysAreEqual(publicKey, registrationBlock.getPublicKey());
                    case DILUTION_BLOCK_FIRST_CHAR:
                        DilutionBlock dilutionBlock = (DilutionBlock) block;
                        boolean foundNewKey = false;
                        for (byte[] newKey : dilutionBlock.getNewKeys()) {
                            if (ByteUtils.byteArraysAreEqual(publicKey, newKey)) {
                                foundNewKey = true;
                            }
                        }
                        return foundNewKey;
                }
            }
        }
        return false;
    }

    public boolean verifyKeyUse(long blockIndex, byte[] publicKey, byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            Block block = blockchain.getBlock(blockIndex);
            if (block != null) {
                switch (block.getPrefix()[0]) {
                    case COMMITMENT_BLOCK_FIRST_CHAR:
                        CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                        return ByteUtils.byteArraysAreEqual(publicKey, commitmentBlock.getPublicKey());
                    case DILUTION_BLOCK_FIRST_CHAR:
                        DilutionBlock dilutionBlock = (DilutionBlock) block;
                        boolean foundOldKey = false;
                        for (byte[] oldKey : dilutionBlock.getOldKeys()) {
                            if (ByteUtils.byteArraysAreEqual(publicKey, oldKey)) {
                                foundOldKey = true;
                            }
                        }
                        return foundOldKey;
                }
            }
        }
        return false;
    }

    public long getBlockchainSize(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            return blockchain.getNumberOfBlocks();
        }
        return 0;
    }

    public boolean isBlockCommitmentBlock(long keyUseBlockIndex, byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return false;
        }
        return blockchain.getBlock(keyUseBlockIndex).getPrefix()[0] == COMMITMENT_BLOCK_FIRST_CHAR;
    }

    public void exit() {
        networkLayer.exit();
    }

    public double getInviteAcceptableBlame(byte[] publicKey) {
        if (!inviteAcceptableBlame.containsKey(publicKey)) {
            inviteAcceptableBlame.put(publicKey, 0.0);
        }
        return inviteAcceptableBlame.get(publicKey);
    }

    public void incrementInviteAcceptableBlame(byte[] publicKey) {
        double newAcceptableBlame = inviteAcceptableBlame.getOrDefault(publicKey, 0.0) + acceptableBlameIncrease;
        inviteAcceptableBlame.put(publicKey, newAcceptableBlame);
    }

    public Block getLastBlock(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        }
        return blockchain.getLastBlock();
    }

    public DilutionBlock getLastDilutionBlock(byte[] managerPublicKey, byte[] chainId) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        }
        return blockchain.getLastDilutionBlock();
    }

    public List<KeyWithOrigin> getValidKeys(byte[] managerPublicKey, byte[] chainId, int numberOfKeys) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain == null) {
            return null;
        }
        return blockchain.getValidKeys(numberOfKeys);
    }

    public boolean isDiluting(byte[] managerPublicKey, byte[] chainId, byte[] publicKey) {
        Blockchain blockchain = getBlockchainByIdAndKey(chainId, managerPublicKey);
        if (blockchain != null) {
            ParticipatingDilutionProcess participatingDilutionProcess = dilutionProcesses.get(blockchain);
            if (participatingDilutionProcess != null) {
                if (ByteUtils.byteArraysAreEqual(participatingDilutionProcess.getPublicKeyForDilutionApplication(), publicKey)) {
                    return true;
                }
            }
            ManagedDilutionProcess managedDilutionProcess = managedDilutionProcesses.get(blockchain);
            if (managedDilutionProcess != null) {
                if (ByteUtils.byteArraysAreEqual(managedDilutionProcess.getPublicKeyAsPoolManager(), publicKey)) {
                    return true;
                }
            }
        }
        return false;
    }
}
