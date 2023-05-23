package app.Simulation;

import app.Client.Layers.ApplicationLayer.Election;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.Block;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.DilutionBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Layers.BlockchainLayer.KeyWithOrigin;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;
import app.Client.Layers.BlockchainLayer.ValidationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Spammer extends BlockchainLayer {
    private final BlockchainLayer electionManager;
    private final Random random;
    private byte[] managerPublicKey;
    private byte[] chainId;

    public Spammer(String name,  BlockchainLayer electionManager) {
        super(name, null);
        this.electionManager = electionManager;
        this.random = new Random();
    }

    public ElectionPhase getElectionPhase() {
        return electionManager.getElectionPhase(managerPublicKey,chainId);
    }

    @Override
    public void subscribeToElection(byte[] managerPublicKey, byte[] chainId) {
        this.managerPublicKey = managerPublicKey;
        this.chainId = chainId;
    }

    @Override
    public void receiveBroadcastMessage(byte[] message) {

    }

    @Override
    public ValidationStatus checkValidityOfMessage(byte[] message) {
        return ValidationStatus.VALID;
    }

    public void spamRegistrationBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("Registration block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore() + 1));

            byteArrays.add(new byte[Election.VOTER_ID_LENGTH]);
            byteArrays.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);

            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
            byteArrays.add(signatureBytes);
            byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(message);
        }
    }

    public void spamDilutionStartBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("dilution start block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore()));

            byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(message);
        }
    }

    public void spamDilutionApplication() {
        ArrayList<byte[]> messageParts = new ArrayList<>();
        messageParts.add("Application - ".getBytes());
        messageParts.add(managerPublicKey);
        messageParts.add(chainId);
        byte[] message = ByteUtils.concatenateByteArrays(messageParts);
        byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
        ArrayList<byte[]> byteArrays = new ArrayList<>();
        byteArrays.add(message);
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 1);
        if (poolMembers != null && !poolMembers.isEmpty()) {
            byteArrays.add(poolMembers.get(0).getPublicKey());
            byteArrays.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            byteArrays.add(signatureBytes);
            byte[] dilutionApplication = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(dilutionApplication);
        }
    }

    public void spamInvite() {
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 2);
        if (poolMembers != null && poolMembers.size() >= 2) {
            ArrayList<byte[]> messageToSignParts = new ArrayList<>();
            messageToSignParts.add("invite - ".getBytes());
            messageToSignParts.add(managerPublicKey);
            messageToSignParts.add(chainId);
            messageToSignParts.add(poolMembers.get(0).getPublicKey());
            messageToSignParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            messageToSignParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
            messageToSignParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
            byte[] signature = ByteUtils.encodeWithLengthByte(new byte[71]);
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(messageToSign);
            messageParts.add(poolMembers.get(1).getPublicKey());
            messageParts.add(ByteUtils.longToByteArray(poolMembers.get(1).getKeyOriginBlockIndex()));
            messageParts.add(signature);
            getNetworkLayer().broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
        }
    }

    public void spamPoolResponse() {
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 2);
        if (poolMembers != null && poolMembers.size() >= 2) {
            ArrayList<byte[]> messageToSignParts = new ArrayList<>();
            messageToSignParts.add("response - ".getBytes());
            messageToSignParts.add(managerPublicKey);
            messageToSignParts.add(chainId);
            messageToSignParts.add(poolMembers.get(0).getPublicKey());
            messageToSignParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            messageToSignParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
            messageToSignParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);

            messageToSignParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            messageToSignParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            messageToSignParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(messageToSign);
            messageParts.add(poolMembers.get(0).getPublicKey());
            messageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            messageParts.add(signatureBytes);
            getNetworkLayer().broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
        }
    }

    public void spamPoolMessage() {
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

        if (poolMembers != null && !poolMembers.isEmpty()) {
            ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
            unsignedMessageParts.add("Pool message - ".getBytes());
            unsignedMessageParts.add(managerPublicKey);
            unsignedMessageParts.add(chainId);
            unsignedMessageParts.add(poolMembers.get(0).getPublicKey());
            unsignedMessageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            unsignedMessageParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
            unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
            unsignedMessageParts.add(new byte[]{(byte) (poolMembers.size())});
            for (KeyWithOrigin poolResponse : poolMembers) {
                byte[] poolMemberPublicKey = poolResponse.getPublicKey();
                unsignedMessageParts.add(poolMemberPublicKey);
                unsignedMessageParts.add(ByteUtils.longToByteArray(poolResponse.getKeyOriginBlockIndex()));
                unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                byte[] encryptedSessionKey = ByteUtils.encodeWithLengthByte(new byte[71]);
                unsignedMessageParts.add(encryptedSessionKey);
            }
            byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(unsignedMessage);
            messageParts.add(signatureBytes);
            byte[] poolMessage = ByteUtils.concatenateByteArrays(messageParts);
            getNetworkLayer().broadcastAnonymously(poolMessage);
        }
    }

    public void spamPoolReceipt() {
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

        if (poolMembers != null && !poolMembers.isEmpty()) {
            ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
            unsignedMessageParts.add("Pool message - ".getBytes());
            unsignedMessageParts.add(managerPublicKey);
            unsignedMessageParts.add(chainId);
            unsignedMessageParts.add(poolMembers.get(0).getPublicKey());
            unsignedMessageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            unsignedMessageParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
            unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
            unsignedMessageParts.add(new byte[]{(byte) (poolMembers.size())});
            for (KeyWithOrigin poolResponse : poolMembers) {
                byte[] poolMemberPublicKey = poolResponse.getPublicKey();
                unsignedMessageParts.add(poolMemberPublicKey);
                unsignedMessageParts.add(ByteUtils.longToByteArray(poolResponse.getKeyOriginBlockIndex()));
                unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                byte[] encryptedSessionKey = ByteUtils.encodeWithLengthByte(new byte[71]);
                unsignedMessageParts.add(encryptedSessionKey);
            }
            byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(unsignedMessage);
            messageParts.add(signatureBytes);
            byte[] poolMessage = ByteUtils.concatenateByteArrays(messageParts);

            ArrayList<byte[]> messageToSignParts = new ArrayList<>();
            messageToSignParts.add("4 Pool acknowledgement - ".getBytes());
            messageToSignParts.add(new byte[]{(byte) 0});
            messageToSignParts.add(poolMessage);

            ArrayList<byte[]> acknowledgementParts = new ArrayList<>();
            byte[] messageToSign = ByteUtils.concatenateByteArrays(messageToSignParts);
            acknowledgementParts.add(messageToSign);
            acknowledgementParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            getNetworkLayer().broadcastAnonymously(ByteUtils.concatenateByteArrays(acknowledgementParts));
        }
    }

    public void spamBlameMessage() {
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

        if (poolMembers != null && !poolMembers.isEmpty()) {
            ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
            unsignedMessageParts.add("Pool message - ".getBytes());
            unsignedMessageParts.add(managerPublicKey);
            unsignedMessageParts.add(chainId);
            unsignedMessageParts.add(poolMembers.get(0).getPublicKey());
            unsignedMessageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            unsignedMessageParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
            unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
            unsignedMessageParts.add(new byte[]{(byte) (poolMembers.size())});
            for (KeyWithOrigin poolResponse : poolMembers) {
                byte[] poolMemberPublicKey = poolResponse.getPublicKey();
                unsignedMessageParts.add(poolMemberPublicKey);
                unsignedMessageParts.add(ByteUtils.longToByteArray(poolResponse.getKeyOriginBlockIndex()));
                unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                byte[] encryptedSessionKey = ByteUtils.encodeWithLengthByte(new byte[71]);
                unsignedMessageParts.add(encryptedSessionKey);
            }
            byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(unsignedMessage);
            messageParts.add(signatureBytes);
            byte[] poolMessage = ByteUtils.concatenateByteArrays(messageParts);

            ArrayList<byte[]> blameMessageParts = new ArrayList<>();
            blameMessageParts.add("Blame - ".getBytes());
            blameMessageParts.add(new byte[]{(byte) 0});
            blameMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
            blameMessageParts.add(poolMessage);
            getNetworkLayer().broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
        }
    }

    public void spamNewKeyMessage() {
        List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

        if (poolMembers != null && !poolMembers.isEmpty()) {
            ArrayList<byte[]> unsignedMessageParts = new ArrayList<>();
            unsignedMessageParts.add("Pool message - ".getBytes());
            unsignedMessageParts.add(managerPublicKey);
            unsignedMessageParts.add(chainId);
            unsignedMessageParts.add(poolMembers.get(0).getPublicKey());
            unsignedMessageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
            unsignedMessageParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
            unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
            unsignedMessageParts.add(new byte[]{(byte) (poolMembers.size())});
            for (KeyWithOrigin poolResponse : poolMembers) {
                byte[] poolMemberPublicKey = poolResponse.getPublicKey();
                unsignedMessageParts.add(poolMemberPublicKey);
                unsignedMessageParts.add(ByteUtils.longToByteArray(poolResponse.getKeyOriginBlockIndex()));
                unsignedMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                unsignedMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                byte[] encryptedSessionKey = ByteUtils.encodeWithLengthByte(new byte[71]);
                unsignedMessageParts.add(encryptedSessionKey);
            }
            byte[] unsignedMessage = ByteUtils.concatenateByteArrays(unsignedMessageParts);
            byte[] signatureBytes = ByteUtils.encodeWithLengthByte(new byte[71]);
            ArrayList<byte[]> messageParts = new ArrayList<>();
            messageParts.add(unsignedMessage);
            messageParts.add(signatureBytes);
            byte[] poolMessage = ByteUtils.concatenateByteArrays(messageParts);

            ArrayList<byte[]> unsignedNewKeyMessageParts = new ArrayList<>();
            unsignedNewKeyMessageParts.add("New key message - ".getBytes());
            unsignedNewKeyMessageParts.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
            unsignedNewKeyMessageParts.add(poolMessage);
            byte[] unsignedNewKeyMessage = ByteUtils.concatenateByteArrays(unsignedNewKeyMessageParts);
            ArrayList<byte[]> newKeyMessageParts = new ArrayList<>();
            newKeyMessageParts.add(unsignedNewKeyMessage);
            newKeyMessageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            getNetworkLayer().broadcastAnonymously(ByteUtils.concatenateByteArrays(newKeyMessageParts));
        }
    }

    public void spamUnvalidatedDilutionBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("Unvalidated - ".getBytes());
            byteArrays.add("Dilution block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore() + 1));

            List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

            if (poolMembers != null && !poolMembers.isEmpty()) {
                byte[] depthByte = new byte[]{(byte) 1};
                byte[] numberOfKeys = new byte[]{(byte) poolMembers.size()};
                ArrayList<byte[]> oldKeysWithOriginalBlocks = new ArrayList<>();
                ArrayList<byte[]> newKeys = new ArrayList<>();
                for (KeyWithOrigin poolMember : poolMembers) {
                    ArrayList<byte[]> currentOldKeyWithOriginalBlock = new ArrayList<>();
                    currentOldKeyWithOriginalBlock.add(poolMember.getPublicKey());
                    currentOldKeyWithOriginalBlock.add(ByteUtils.longToByteArray(poolMember.getKeyOriginBlockIndex()));
                    oldKeysWithOriginalBlocks.add(ByteUtils.concatenateByteArrays(currentOldKeyWithOriginalBlock));
                    newKeys.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                }
                byteArrays.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
                byteArrays.add(poolMembers.get(0).getPublicKey());
                byteArrays.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
                byteArrays.add(depthByte);
                byteArrays.add(poolMembers.get(0).getPublicKey());
                byteArrays.add(numberOfKeys);
                byte[] concatenatedOldKeys = ByteUtils.concatenateByteArrays(oldKeysWithOriginalBlocks);
                byteArrays.add(concatenatedOldKeys);
                byte[] concatenatedNewKeys = ByteUtils.concatenateByteArrays(newKeys);
                byteArrays.add(concatenatedNewKeys);

                byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
                getNetworkLayer().broadcastAnonymously(message);
            }
        }
    }

    public void spamSignatureMessage() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("Dilution block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore() + 1));

            List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

            if (poolMembers != null && !poolMembers.isEmpty()) {
                byte[] depthByte = new byte[]{(byte) 1};
                byte[] numberOfKeys = new byte[]{(byte) poolMembers.size()};
                ArrayList<byte[]> oldKeysWithOriginalBlocks = new ArrayList<>();
                ArrayList<byte[]> newKeys = new ArrayList<>();
                for (KeyWithOrigin poolMember : poolMembers) {
                    ArrayList<byte[]> currentOldKeyWithOriginalBlock = new ArrayList<>();
                    currentOldKeyWithOriginalBlock.add(poolMember.getPublicKey());
                    currentOldKeyWithOriginalBlock.add(ByteUtils.longToByteArray(poolMember.getKeyOriginBlockIndex()));
                    oldKeysWithOriginalBlocks.add(ByteUtils.concatenateByteArrays(currentOldKeyWithOriginalBlock));
                    newKeys.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                }
                byteArrays.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
                byteArrays.add(poolMembers.get(0).getPublicKey());
                byteArrays.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
                byteArrays.add(depthByte);
                byteArrays.add(poolMembers.get(0).getPublicKey());
                byteArrays.add(numberOfKeys);
                byte[] concatenatedOldKeys = ByteUtils.concatenateByteArrays(oldKeysWithOriginalBlocks);
                byteArrays.add(concatenatedOldKeys);
                byte[] concatenatedNewKeys = ByteUtils.concatenateByteArrays(newKeys);
                byteArrays.add(concatenatedNewKeys);

                for (KeyWithOrigin poolMember : poolMembers) {
                    byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                }
                byte[] unvalidatedBlockBytes = ByteUtils.concatenateByteArrays(byteArrays);

                ArrayList<byte[]> messageParts = new ArrayList<>();
                messageParts.add("Signature - ".getBytes());
                messageParts.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
                messageParts.add(poolMembers.get(0).getPublicKey());
                messageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
                messageParts.add(poolMembers.get(1).getPublicKey());
                messageParts.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
                messageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                messageParts.add(unvalidatedBlockBytes);
                messageParts.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                getNetworkLayer().broadcastAnonymously(ByteUtils.concatenateByteArrays(messageParts));
            }
        }
    }

    public void spamDilutionBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("Dilution block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore() + 1));

            List<KeyWithOrigin> poolMembers = electionManager.getValidKeys(managerPublicKey, chainId, 8);

            if (poolMembers != null && !poolMembers.isEmpty()) {
                byte[] depthByte = new byte[]{(byte) 1};
                byte[] numberOfKeys = new byte[]{(byte) poolMembers.size()};
                ArrayList<byte[]> oldKeysWithOriginalBlocks = new ArrayList<>();
                ArrayList<byte[]> newKeys = new ArrayList<>();
                for (KeyWithOrigin poolMember : poolMembers) {
                    ArrayList<byte[]> currentOldKeyWithOriginalBlock = new ArrayList<>();
                    currentOldKeyWithOriginalBlock.add(poolMember.getPublicKey());
                    currentOldKeyWithOriginalBlock.add(ByteUtils.longToByteArray(poolMember.getKeyOriginBlockIndex()));
                    oldKeysWithOriginalBlocks.add(ByteUtils.concatenateByteArrays(currentOldKeyWithOriginalBlock));
                    newKeys.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                }
                byteArrays.add(new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
                byteArrays.add(poolMembers.get(0).getPublicKey());
                byteArrays.add(ByteUtils.longToByteArray(poolMembers.get(0).getKeyOriginBlockIndex()));
                byteArrays.add(depthByte);
                byteArrays.add(poolMembers.get(0).getPublicKey());
                byteArrays.add(numberOfKeys);
                byte[] concatenatedOldKeys = ByteUtils.concatenateByteArrays(oldKeysWithOriginalBlocks);
                byteArrays.add(concatenatedOldKeys);
                byte[] concatenatedNewKeys = ByteUtils.concatenateByteArrays(newKeys);
                byteArrays.add(concatenatedNewKeys);

                for (KeyWithOrigin poolMember : poolMembers) {
                    byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
                }
                byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
                getNetworkLayer().broadcastAnonymously(message);
            }
        }
    }

    public void spamDepthBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("0 Depth block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore()));

            byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(message);
        }
    }

    public void spamPreDepthBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("1 Pre-depth block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore()));

            byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(message);
        }
    }

    public void spamCommitmentBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("Commitment block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore() + 1));

            byteArrays.add(new byte[PrivacyUtils.VOTE_HASH_LENGTH]);
            if (random.nextInt(2) == 0) {
                byteArrays.add(new byte[PrivacyUtils.PUBLIC_KEY_LENGTH]);
                byteArrays.add(ByteUtils.longToByteArray(3));
            } else {
                DilutionBlock dilutionBlock = electionManager.getLastDilutionBlock(managerPublicKey, chainId);
                byteArrays.add(dilutionBlock.getNewKeys().get(0));
                byteArrays.add(ByteUtils.longToByteArray(dilutionBlock.getIndex()));
            }

            byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(message);
        }
    }

    public void spamCommitmentEndBlock() {
        long blockchainSize = electionManager.getBlockchainSize(managerPublicKey, chainId);
        Block lastBlock = electionManager.getLastBlock(managerPublicKey, chainId);
        if (lastBlock != null) {
            ArrayList<byte[]> byteArrays = new ArrayList<>();
            byteArrays.add("3 Commitment end block - ".getBytes());
            byteArrays.add(managerPublicKey);
            byteArrays.add(chainId);
            byteArrays.add(ByteUtils.longToByteArray(blockchainSize)); // Block index
            byteArrays.add(lastBlock.getHash());
            byteArrays.add(ByteUtils.longToByteArray(lastBlock.getChainScore()));

            byteArrays.add(ByteUtils.encodeWithLengthByte(new byte[71]));
            byte[] message = ByteUtils.concatenateByteArrays(byteArrays);
            getNetworkLayer().broadcastAnonymously(message);
        }
    }

    public void spam() {
        if (random.nextBoolean()) {
            byte[] message = new byte[500];
            getNetworkLayer().broadcastAnonymously(message);
        } else {
            switch (getElectionPhase()) {
                case REGISTRATION:
                    if (random.nextBoolean()) {
                        spamRegistrationBlock();
                    } else {
                        spamDilutionStartBlock();
                    }
                    break;
                case DILUTION:
                    switch(random.nextInt(12)) {
                        case 0:
                            spamDilutionApplication();
                            break;
                        case 1:
                            spamInvite();
                            break;
                        case 2:
                            spamPoolResponse();
                            break;
                        case 3:
                            spamPoolMessage();
                            break;
                        case 4:
                            spamPoolReceipt();
                            break;
                        case 5:
                            spamBlameMessage();
                            break;
                        case 6:
                            spamNewKeyMessage();
                            break;
                        case 7:
                            spamUnvalidatedDilutionBlock();
                            break;
                        case 8:
                            spamSignatureMessage();
                            break;
                        case 9:
                            spamPreDepthBlock();
                            break;
                        case 10:
                            spamDepthBlock();
                        default:
                            spamDilutionBlock();
                            break;
                    }
                    break;
                case COMMITMENT:
                    if (random.nextBoolean()) {
                        spamCommitmentBlock();
                    } else {
                        spamCommitmentEndBlock();
                    }
                    break;
                case VOTING:
                    break;
            }
        }
    }
}
