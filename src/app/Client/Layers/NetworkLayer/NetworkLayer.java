package app.Client.Layers.NetworkLayer;

import app.Client.Datastructures.HashCircle;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Layers.BlockchainLayer.ValidationStatus;
import app.Client.Utils.PrivacyUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class NetworkLayer {
    private static final byte[] BLOCK_REQUEST_PREFIX = "block - ".getBytes();
    private static final byte[] REQUESTED_BLOCK_PREFIX = "answer - ".getBytes();
    private static final byte BLOCK_REQUEST_FIRST_CHAR = 'b';
    private static final byte REQUESTED_BLOCK_FIRST_CHAR = 'a';
    private String name;
    private BlockchainLayer blockchainLayer;
    private NetworkInterface networkInterface;
    private Set<Node> connectedNodes;
    private final HashCircle passedMessageHashes;
    private ArrayList<MessageWithSender> messagesBroadcast;
    private ArrayList<MessageWithSender> messagesUnicast;
    private boolean squeezed;
    private boolean exiting;

    private int anonymousBroadcastSentCounter = 0;
    private int anonymousBroadcastReceivedCounter = 0;
    private long messageCounter = 0;

    public NetworkLayer(String name, BlockchainLayer blockchainLayer) {
        connectedNodes = new HashSet<>();
        passedMessageHashes = new HashCircle(10000, PrivacyUtils.HASH_LENGTH);
        this.name = name;
        this.blockchainLayer = blockchainLayer;
        messagesBroadcast = new ArrayList<>();
        messagesUnicast = new ArrayList<>();
        networkInterface = new LocalNetworkInterface(name, 0, LocalNexus.getInstance(), this);
        squeezed = false;
        exiting = false;
    }

    public void receiveConnection(String ipAddress, int port) {
        connectedNodes.add(new Node(ipAddress, port));
    }

    public void connect(String ipAddress, int port) {
        receiveConnection(ipAddress, port);
        networkInterface.connect(ipAddress, port);
    }

    public void disconnect(String ipAddress, int port) {
        Node toRemove = null;
        for (Node connectedNode: connectedNodes) {
            if (connectedNode.getIpAddress().equals(ipAddress) && connectedNode.getPort() == port) {
                toRemove = connectedNode;
            }
        }
        if (toRemove != null) {
            connectedNodes.remove(toRemove);
        }
    }

    public void broadcastAnonymously(byte[] message) {
        anonymousBroadcastSentCounter++;
        messageCounter++;
        byte[] messageHash = PrivacyUtils.hash(message);
        passedMessageHashes.add(messageHash);
        if (!squeezed) {
            for (Node node : connectedNodes) {
                networkInterface.sendMessage(node.getIpAddress(), node.getPort(), message);
            }
        }
    }

    public void receiveAnonymousBroadcast(byte[] message, String ipAddress, int port) {
        Node broadcaster = findConnectedNode(ipAddress, port);
        if (broadcaster != null) {
            if (broadcaster.getSpamCounter() < 10 || broadcaster.getLastSpamTime() < System.currentTimeMillis() - 1000) {
                messagesBroadcast.add(new MessageWithSender(message, broadcaster));
            }
        }
    }

    public void handleAnonymousBroadcast(byte[] message, String ipAddress, int port) {
        byte[] messageHash = PrivacyUtils.hash(message);
        if (!passedMessageHashes.contains(messageHash)) {
            anonymousBroadcastReceivedCounter++;
            passedMessageHashes.add(messageHash);
            messageCounter++;
            switch (blockchainLayer.checkValidityOfMessage(message)) {
                case LOSES_SECONDARY_CHECK:
                    // check chain score and break if too low or equal
                    if (!blockchainLayer.isChainScoreHighEnough(message)) {
                        break;
                    }
                case VALID:
                case EQUAL_FORK:
                    if (!squeezed) {
                        for (Node node : connectedNodes) {
                            if (!node.getIpAddress().equals(ipAddress) || node.getPort() != port) {
                                networkInterface.sendMessage(node.getIpAddress(), node.getPort(), message);
                            }
                        }
                    }
                    blockchainLayer.receiveBroadcastMessage(message);
                    break;
                case NEED_PREDECESSOR:
                case NEED_PREDECESSOR_EQUAL_FORK:
                    if (!blockchainLayer.appendToPredecessorInHeadlessChains(message)) {
                        byte[] predecessorMessage = blockchainLayer.getPredecessorMessage(message);
                        if (predecessorMessage != null) {
                            if (!squeezed) {
                                messageCounter++;
                                networkInterface.sendUnicastMessage(ipAddress, port, predecessorMessage);
                            }
                        }
                    }
                    break;
                case EXISTING_CHAIN_REQUEST:
                    byte[] requestedBlock = blockchainLayer.processChainRequest(message);
                    if (!squeezed) {
                        networkInterface.sendMessage(ipAddress, port, requestedBlock);
                    }
                    break;
                case LOSES_PRIMARY_CHECK:
                case LOSING_FORK:
                case LOSING_FORK_LOSES_SECONDARY_CHECK:
                case NEED_PREDECESSOR_LOSING_FORK:
                    if (!squeezed) {
                        requestedBlock = blockchainLayer.getLastBlock(message);
                        if (requestedBlock != null) {
                            networkInterface.sendMessage(ipAddress, port, requestedBlock);
                        }
                    }
                    break;
                case INVALID:
                    Node sender = findConnectedNode(ipAddress, port);
                    if (sender != null) {
                        sender.incrementSpamCounter();
                    }
                    break;
            }
        }
    }

    public void receiveUnicast(byte[] message, String ipAddress, int port) {
        Node unicaster = findConnectedNode(ipAddress, port);
        if (unicaster != null) {
            messagesUnicast.add(new MessageWithSender(message, unicaster));
        }
    }

    public void handleUnicast(byte[] message, String ipAddress, int port) {
        messageCounter++;
        switch (message[0]) {
            case BLOCK_REQUEST_FIRST_CHAR:
                byte[] blockAnswer = blockchainLayer.getBlockFromRequestMessage(message);
                if (blockAnswer != null) {
                    if (!squeezed) {
                        messageCounter++;
                        networkInterface.sendUnicastMessage(ipAddress, port, blockAnswer);
                    }
                }
                break;
            case REQUESTED_BLOCK_FIRST_CHAR:
                byte[] blockMessage = new byte[message.length - REQUESTED_BLOCK_PREFIX.length - PrivacyUtils.HASH_LENGTH];
                for (int i = 0; i < blockMessage.length; i++) {
                    blockMessage[i] = message[i + REQUESTED_BLOCK_PREFIX.length + PrivacyUtils.HASH_LENGTH];
                }
                ValidationStatus validationStatus = blockchainLayer.checkValidityOfMessage(blockMessage);
                switch (validationStatus) {
                    case LOSES_SECONDARY_CHECK:
                    case LOSING_FORK_LOSES_SECONDARY_CHECK:
                        // check chain score and break if too low or equal
                        if (!blockchainLayer.isChainScoreHighEnough(blockMessage)) {
                            break;
                        }
                    case VALID:
                    case LOSING_FORK:
                    case EQUAL_FORK:
                        blockchainLayer.copyFromFork(blockMessage, validationStatus);
                        break;
                    case NEED_PREDECESSOR:
                    case NEED_PREDECESSOR_LOSING_FORK:
                    case NEED_PREDECESSOR_EQUAL_FORK:
                        byte[] predecessorMessage = blockchainLayer.getPredecessorMessage(blockMessage);
                        if (predecessorMessage != null) {
                            if (!squeezed) {
                                messageCounter++;
                                networkInterface.sendUnicastMessage(ipAddress, port, predecessorMessage);
                            }
                        }
                        break;
                    case LOSES_PRIMARY_CHECK:
                        blockchainLayer.deleteHeadlessChain(blockMessage);
                        break;
                    case INVALID:
                        Node sender = findConnectedNode(ipAddress, port);
                        if (sender != null) {
                            sender.incrementSpamCounter();
                        }
                        blockchainLayer.deleteHeadlessChain(blockMessage);
                        break;
                }
                break;
        }
    }

    public void handleMessages() {
        for (int i = 0; i < messagesUnicast.size(); i++) {
            MessageWithSender message = messagesUnicast.get(i);
            if (message != null) {
                handleUnicast(message.getMessage(), message.getSender().getIpAddress(), message.getSender().getPort());
            }
        }
        messagesUnicast.clear();
        for (int i = 0; i < messagesBroadcast.size(); i++) {
            MessageWithSender messageWithSender = messagesBroadcast.get(i);
            if (messageWithSender != null) {
                handleAnonymousBroadcast(messageWithSender.getMessage(), messageWithSender.getSender().getIpAddress(), messageWithSender.getSender().getPort());
            }
        }
        messagesBroadcast.clear();
    }

    public String getIpAddress() {
        return networkInterface.getIpAddress();
    }

    public int getPort() {
        return networkInterface.getPort();
    }

    public void squeeze() {
        squeezed = !squeezed;
    }

    public Node findConnectedNode(String ipAddress, int port) {
        Node toReturn = null;
        for (Node node: connectedNodes) {
            if (toReturn == null && node.equals(ipAddress, port)) {
                toReturn = node;
            }
        }
        return toReturn;
    }

    public int getAnonymousBroadcastSentCounter() {
        return anonymousBroadcastSentCounter;
    }

    public int getAnonymousBroadcastReceivedCounter() {
        return anonymousBroadcastReceivedCounter;
    }

    public long getMessageCounter() {
        return messageCounter;
    }

    public void exit() {
        exiting = true;
    }

    public boolean isExiting() {
        return exiting;
    }
}
