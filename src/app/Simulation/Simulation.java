package app.Simulation;

import app.Client.Layers.ApplicationLayer.ApplicationLayer;
import app.Client.Layers.ApplicationLayer.Candidate;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.Block;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.CommitmentBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Layers.NetworkLayer.LocalNexus;
import app.Client.Layers.NetworkLayer.NetworkLayer;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.TimeUtils;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Simulation {
    private int propagationDelay;
    private int depthIncreaseInterval = 5000;
    private int preDepthInterval = 100;
    private int desiredDilutionDepth;
    private ArrayList<Candidate> candidates;
    private ArrayList<byte[]> candidateBytes;
    private ApplicationLayer electionManager;
    private ArrayList<ApplicationLayer> voterNodes;
    private HashMap<ApplicationLayer, String> voterIds;
    private HashMap<ApplicationLayer, String> voters;
    private ArrayList<Spammer> spammerNodes;
    private ArrayList<Double> tally;
    private boolean resultsAreCorrect;
    private int blockchainLength;
    private double averageAnonymousBroadcastSentCounter;
    private double maxAnonymousBroadcastSentCounter;
    private double minAnonymousBroadcastSentCounter;
    private double averageAnonymousBroadcastReceivedCounter;
    private double maxAnonymousBroadcastReceivedCounter;
    private double minAnonymousBroadcastReceivedCounter;
    private double averageMessageCounter;
    private double maxMessageCounter;
    private double minMessageCounter;
    private boolean allNodesDilutedToDesiredDepth;

    public Simulation(int numberOfVoters, int desiredDilutionDepth, int numberOfCandidates, int propagationDelay, int numberOfSpammers) {
        LocalNexus.getInstance().reset();
        this.desiredDilutionDepth = desiredDilutionDepth;
        this.propagationDelay = propagationDelay;
        candidates = new ArrayList<>();
        candidateBytes = new ArrayList<>();
        electionManager = new ApplicationLayer("ElectionManager");
        voterNodes = new ArrayList<>();
        spammerNodes = new ArrayList<>();
        voterIds = new HashMap<>();
        voters = new HashMap<>();
        int tagLength = 10;
        for (int i = 0; i < numberOfCandidates; i++) {
            String tag = String.valueOf(i);
            while (tag.length() < tagLength) {
                tag = "_" + tag;
            }
            candidates.add(new Candidate(true, tag, tag));
            candidateBytes.add(tag.getBytes());
        }
        Random random = new Random();
        for (int i = 0; i < numberOfVoters; i++) {
            String voterName = "Voter" + String.valueOf(i);
            ApplicationLayer newNode = new ApplicationLayer(voterName);
            voterNodes.add(newNode);
            voterIds.put(newNode, voterName);
            int candidateIndex = random.nextInt(numberOfCandidates);
            String candidateTagString = new String(candidateBytes.get(candidateIndex));
            voters.put(newNode, candidateTagString);
            int connectedNodeIndex = random.nextInt(i + 1);
            ApplicationLayer connectedNode;
            if (connectedNodeIndex < i) {
                connectedNode = voterNodes.get(connectedNodeIndex);
            } else {
                connectedNode = electionManager;
            }
            newNode.connect(connectedNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), connectedNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
        }
        for (int i = 0; i < numberOfSpammers; i++) {
            String spammerName = "Spammer" + String.valueOf(i);
            Spammer newSpammer = new Spammer(spammerName, electionManager.getPrivacyLayer().getBlockchainLayer());
            spammerNodes.add(newSpammer);
            int connectedNodeIndex = random.nextInt(numberOfVoters + i + 1);
            if (connectedNodeIndex <= numberOfVoters) {
                ApplicationLayer connectedNode = electionManager;
                if (connectedNodeIndex < numberOfVoters) {
                    connectedNode = voterNodes.get(connectedNodeIndex);
                }
                newSpammer.connect(connectedNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), connectedNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
            } else {
                Spammer connectedSpammer = spammerNodes.get(connectedNodeIndex - 1 - numberOfVoters);
                newSpammer.connect(connectedSpammer.getNetworkLayer().getIpAddress(), connectedSpammer.getNetworkLayer().getPort());
            }
        }
        tally = new ArrayList<>();
        for (byte[] candidate: candidateBytes) {
            tally.add(0.0);
        }
        for (ApplicationLayer voter: voterNodes) {
            byte[] vote = voters.get(voter).getBytes();
            for (int i = 0; i < candidateBytes.size(); i++) {
                byte[] candidate = candidateBytes.get(i);
                if (ByteUtils.byteArraysAreEqual(vote, candidate)) {
                    tally.set(i, tally.get(i) + 1);
                }
            }
        }
        for (int i = 0; i < tally.size(); i++) {
            tally.set(i, tally.get(i) / voters.size());
        }
    }

    public Simulation(String fileName, int propagationDelay) {
        LocalNexus.getInstance().reset();
        this.desiredDilutionDepth = 2;
        this.propagationDelay = propagationDelay;
        candidates = new ArrayList<>();
        candidateBytes = new ArrayList<>();
        electionManager = new ApplicationLayer("ElectionManager");
        voterNodes = new ArrayList<>();
        voterIds = new HashMap<>();
        voters = new HashMap<>();
        int readPhase = 0;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(Paths.get(fileName))))) {
            String line = bufferedReader.readLine();
            while (line != null) {
                if (line.equals("Candidates:")) {
                    readPhase = 1;
                } else if (line.equals("Voters:")) {
                    readPhase = 2;
                } else if (line.equals("Connections:")) {
                    readPhase = 3;
                } else {
                    switch (readPhase) {
                        case 1:
                            if (line.charAt(0) == '"') {
                                int endIndex = line.indexOf('"', 1);
                                String candidateDescription = line.substring(1, endIndex);
                                String candidateTag = candidateDescription;
                                while (candidateTag.length() < 10) {
                                    candidateTag = candidateTag + "_";
                                }
                                if (candidateTag.length() > 10) {
                                    candidateTag = candidateTag.substring(0, 10);
                                }
                                Candidate newCandidate = new Candidate(true, candidateTag, candidateDescription);
                                candidates.add(newCandidate);
                                candidateBytes.add(newCandidate.getTag().getBytes());
                            }
                            break;
                        case 2:
                            if (line.charAt(0) == '"') {
                                int voterEndIndex = line.indexOf('"', 1);
                                int voteEndIndex = line.indexOf('"', voterEndIndex + 3);
                                ApplicationLayer newNode = new ApplicationLayer(line.substring(1, voterEndIndex));
                                voterNodes.add(newNode);
                                voterIds.put(newNode, line.substring(1, voterEndIndex));
                                voters.put(newNode, line.substring(voterEndIndex + 3, voteEndIndex));
                            }
                            break;
                        case 3:
                            int spaceIndex = line.indexOf(' ');
                            int startNodeIndex = Integer.parseInt(line.substring(0, spaceIndex));
                            int endNodeIndex = Integer.parseInt(line.substring(spaceIndex + 1));
                            ApplicationLayer startNode;
                            if (startNodeIndex == 0) {
                                startNode = electionManager;
                            } else {
                                startNode = voterNodes.get(startNodeIndex - 1);
                            }
                            ApplicationLayer endNode;
                            if (endNodeIndex == 0) {
                                endNode = electionManager;
                            } else {
                                endNode = voterNodes.get(endNodeIndex - 1);
                            }
                            startNode.connect(endNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), endNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
                            break;
                    }
                }
                line = bufferedReader.readLine();
            }
            tally = new ArrayList<>();
            for (byte[] candidate: candidateBytes) {
                tally.add(0.0);
            }
            for (ApplicationLayer voter: voterNodes) {
                byte[] vote = voters.get(voter).getBytes();
                for (int i = 0; i < candidateBytes.size(); i++) {
                    byte[] candidate = candidateBytes.get(i);
                    if (ByteUtils.byteArraysAreEqual(vote, candidate)) {
                        tally.set(i, tally.get(i) + 1);
                    }
                }
            }
            for (int i = 0; i < tally.size(); i++) {
                tally.set(i, tally.get(i) / voters.size());
            }
        } catch (IOException exception) {
            System.out.println("IO exception");
        }
    }

    public void setDepthIncreaseInterval(int depthIncreaseInterval) {
        this.depthIncreaseInterval = depthIncreaseInterval;
    }

    public void setPreDepthInterval(int preDepthInterval) {
        this.preDepthInterval = preDepthInterval;
    }

    public void run() {
        Random random = new Random();
        boolean electionIsRunning = true;
        boolean electionWasStarted = false;
        boolean diluted = false;
        boolean committed = false;
        int propagationCounter = 0;
        resultsAreCorrect = true;
        ArrayList<ApplicationLayer> votersToRegister = new ArrayList<>();
        HashMap<ApplicationLayer, Long> commitmentBlockIndices = new HashMap<>();
        for (ApplicationLayer node: voterNodes) {
            votersToRegister.add(node);
        }
        int depth = 0;
        boolean inPreDepthPhase = false;
        int dilutionCounter = 0;
        int commitCounter = 0;
        ArrayList<Double> resultDifferences = new ArrayList<>();
        ElectionPhase currentElectionPhase = ElectionPhase.REGISTRATION;
        while (electionIsRunning) {
            if (propagationCounter > 0) {
                propagationCounter--;
            } else if (!electionWasStarted) {
                electionManager.createElectionManagerRole();
                electionManager.createElection(0);
                byte[] electionManagerPublicKey = electionManager.getManagedElectionPublicKey(0);
                byte[] chainId = electionManager.getManagedElectionChainId(0, 0);
                for (Spammer spammer: spammerNodes) {
                    spammer.subscribeToElection(electionManagerPublicKey, chainId);
                }
                electionWasStarted = true;
                propagationCounter = propagationDelay;
//                System.out.println("Started election");
                System.out.print("se ");
            } else if (votersToRegister.size() > 0) {
                int nodeIndex = random.nextInt(votersToRegister.size());
                ApplicationLayer node = votersToRegister.get(nodeIndex);
                node.createKeyPair();
                electionManager.registerVoter(voterIds.get(node), node.getPublicKey(0), 0, 0);
                votersToRegister.remove(nodeIndex);
                propagationCounter = propagationDelay;
            } else if (electionManager.getElectionPhase(0) == ElectionPhase.REGISTRATION) {
                electionManager.endRegistrationPeriod(0, 0);
                propagationCounter = propagationDelay;
                System.out.print("ds ");
            } else if (!diluted) {
                if (currentElectionPhase == ElectionPhase.REGISTRATION) {
//                    System.out.println("Started Dilution phase");
                    currentElectionPhase = ElectionPhase.DILUTION;
                }
                diluted = true;
                ArrayList<ApplicationLayer> voterNodesStillDiluting = new ArrayList<>();
                for (ApplicationLayer node: voterNodes) {
                    int identityIndex = node.getCurrentIdentityIndex(0);
                    if (node.getDepth(identityIndex, 0) < desiredDilutionDepth) {
                        diluted = false;
                    }
                    if (node.getDepth(identityIndex, 0) <= depth) {
                        voterNodesStillDiluting.add(node);
                    }
                }
/*                if (voterNodesStillDiluting.size() > 0) {
                    diluted = false;
                }*/
                if (dilutionCounter == 10000) {
                    System.out.println("PROBLEM:");
                    System.out.println("--- diluted: " + diluted);
                    for (ApplicationLayer node: voterNodes) {
                        System.out.println("--- NODE: " + node.getName());
                        int identityIndex = node.getCurrentIdentityIndex(0);
                        if (node.getDepth(identityIndex, 0) < desiredDilutionDepth) {
                            BlockchainLayer blockchainLayer = node.getPrivacyLayer().getBlockchainLayer();
//                            System.out.println("--- STILL DILUTING (time switched: " + blockchainLayer.timesSwitchedBetweenPoolRole + ")");
                            System.out.println("--- STILL DILUTING (time switched: " + blockchainLayer.timesSwitchedBetweenPoolRole + ", updates: " + blockchainLayer.updateCounter + ", participating: " + blockchainLayer.updateParticipatingCounter + ", managed: " + blockchainLayer.updateManagedCounter + ", identityIndex: " + identityIndex + ")");
                        } else {
                            System.out.println("--- NO LONGER DILUTING (identityIndex: " + identityIndex + ")");
                        }
                        System.out.println("--- part of dilution process: " + node.isPartOfDilutionProcess(0));
                        System.out.println("--- owns dilution process: " + node.ownsDilutionProcess(0));
                        node.printBlockchain(0);
                        node.printIdentities();
                    }
                }
                if (!diluted) {
                    if (voterNodesStillDiluting.size() > 0) {
                        int dilutingVoterIndex = random.nextInt(voterNodesStillDiluting.size());
                        if (dilutionCounter == 10000) {
                            System.out.println("DILCOUNT100000 dilutingVoterIndex == " + dilutingVoterIndex);
                        }
                        ApplicationLayer dilutingVoterNode = voterNodesStillDiluting.get(dilutingVoterIndex);
                        if (dilutionCounter == 10000) {
                            System.out.println("DILCOUNT100000 dilutingVoterNode == " + dilutingVoterNode.getName());
                        }
                        int identityIndex = dilutingVoterNode.getCurrentIdentityIndex(0);
                        if (dilutionCounter == 10000) {
                            System.out.println("DILCOUNT100000 identityIndex == " + identityIndex);
                        }
                        if (random.nextInt(2) == 0) {
//                            System.out.println(dilutingVoterNode.getName() + " applies for dilution.");
                            dilutingVoterNode.startDiluting(identityIndex, 0);
//                            dilutingVoterNode.applyForDilution(identityIndex, 0);
                        } else {
//                            System.out.println(dilutingVoterNode.getName() + " starts dilution pool.");
                            dilutingVoterNode.startCreatingDilutionPools(identityIndex, 0);
//                            dilutingVoterNode.startDilutionPool(identityIndex, 0, true);
                        }
                    } else {
                        if (dilutionCounter == 10000) {
                            System.out.println("DILCOUNT100000 no voters diluting");
                        }
                    }
                        dilutionCounter++;
//                        System.out.println("c " + dilutionCounter);
//                        if (inPreDepthPhase || depth < desiredDilutionDepth) {
//                            if (dilutionCounter == depthIncreaseInterval - preDepthInterval) {
                            if (dilutionCounter % depthIncreaseInterval == depthIncreaseInterval - preDepthInterval) {
                                electionManager.preIncrementDilutionMaxDepth(0, 0);
                                System.out.print("pd ");
                                inPreDepthPhase = true;
//                                System.out.println("Preinc (" + dilutionCounter + ", " + depth + ")");
                                depth++;
//                                System.out.println("Preinc (" + dilutionCounter + ", " + depth + ")");
                            } else if (dilutionCounter % depthIncreaseInterval == depthIncreaseInterval - preDepthInterval + 2) {
                                for (ApplicationLayer node: voterNodes) {
                                    int identityIndex1 = node.getCurrentIdentityIndex(0);
                                    node.startDiluting(identityIndex1, 0);
                                }
                            } else if (dilutionCounter % depthIncreaseInterval == 0) {
//                                System.out.println("Depth++ (" + dilutionCounter + ")");
                                electionManager.incrementDilutionMaxDepth(0, 0);
                                System.out.print("dp ");
                                inPreDepthPhase = false;
                            }
//                        }
//                    }
                }
//                if (dilutionCounter % 10000 == 0) {
//                    System.out.println("Dilution counter: " + dilutionCounter);
//                }
                propagationCounter = propagationDelay;
            } else if (electionManager.getElectionPhase(0) == ElectionPhase.DILUTION) {
                electionManager.endDilutionPeriod(0, 0, candidates);
                propagationCounter = propagationDelay;
//                System.out.println("Started Commitment phase");
                System.out.print("de ");
                currentElectionPhase = ElectionPhase.COMMITMENT;
                for (ApplicationLayer node: voterNodes) {
                    int identityIndex = node.getCurrentIdentityIndex(0);
                    if (identityIndex == -1) {
                        System.out.println("\n" + node.getName() + " doesn't have valid identity");
                        node.printIdentities();
                        node.printBlockchain(0);
                    }
                }
            } else if (!committed) {
                committed = commitmentBlockIndices.size() == voterNodes.size();
                ArrayList<ApplicationLayer> voterNodesStillCommitting = new ArrayList<>();
                for (ApplicationLayer node: voterNodes) {
                    if (!commitmentBlockIndices.containsKey(node)) {
                        voterNodesStillCommitting.add(node);
                    } else {
                        long commitmentBlockIndex = commitmentBlockIndices.get(node);
                        int identityIndex = node.getCurrentIdentityIndex(0);
                        byte[] publicKey = node.getPublicKey(identityIndex);
                        Block block = node.getBlockchain(0).get((int) commitmentBlockIndex);
                        if (block.getPrefix()[0] != 'C') {
                            voterNodesStillCommitting.add(node);
                            commitmentBlockIndices.remove(node);
                        } else {
                            CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                            if (!ByteUtils.byteArraysAreEqual(commitmentBlock.getPublicKey(), publicKey)) {
                                voterNodesStillCommitting.add(node);
                                commitmentBlockIndices.remove(node);
                            }
                        }
                    }
                }
                if (voterNodesStillCommitting.size() > 0) {
                    committed = false;
                }

/*                System.out.println("                                                   COMMIT 1");
                if (committed) {
                    for (ApplicationLayer voterNode: commitmentBlockIndices.keySet()) {
                        long commitmentBlockIndex = commitmentBlockIndices.get(voterNode);
                        int identityIndex = voterNode.getCurrentIdentityIndex(0);
                        byte[] publicKey = voterNode.getPublicKey(identityIndex);
                        Block block = voterNode.getBlockchain(0).get((int) commitmentBlockIndex);
                        if (block.getPrefix()[0] != 'C') {
                            committed = false;
                        } else {
                            CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                            if (!ByteUtils.byteArraysAreEqual(commitmentBlock.getPublicKey(), publicKey)) {
                                committed = false;
                            }
                        }
                    }
                    System.out.println("                                                   COMMIT 2");
                }
                System.out.println("                                                   COMMIT 3");*/
                if (!committed) {
                    int indexOfVoterNode = random.nextInt(voterNodesStillCommitting.size());
/*                    if (commitCounter == 99) {
                        System.out.println("voterNodesStillCommitting.size() == " + voterNodesStillCommitting.size());
                        System.out.println("indexOfVoterNode == " + indexOfVoterNode);
                        for (ApplicationLayer voterNode: voterNodesStillCommitting) {
                            System.out.println(voterNode.getName());
                        }
                        System.out.println("commitmentBlockIndices.size() == " + commitmentBlockIndices.size());
                        for (ApplicationLayer voterNode: commitmentBlockIndices.keySet()) {
                            long index = commitmentBlockIndices.get(voterNode);
                            System.out.println(voterNode.getName() + " committed in block " + index);
                        }
                    }*/
                    ApplicationLayer voterNode = voterNodesStillCommitting.get(indexOfVoterNode);
/*                    ApplicationLayer voterNode = voterNodes.get(random.nextInt(voterNodes.size()));
                    while (commitmentBlockIndices.containsKey(voterNode)) {
                        voterNode = voterNodes.get(random.nextInt(voterNodes.size()));
                    }*/
//                    if (commitCounter == 99) {
//                        System.out.println("selected voterNode == " + voterNode.getName());
//                    }
                    int identityIndex = voterNode.getCurrentIdentityIndex(0);
                    long commitmentBlockIndex = -1;
/*                    if (commitCounter == 99) {
                        System.out.println("identityIndex == " + identityIndex);
                        System.out.println("key still valid: " + voterNode.isIdentityKeyStillValid(identityIndex, 0));
                        voterNode.printIdentities();
                        voterNode.getCurrentIdentityIndex2(0);
                        commitmentBlockIndex = voterNode.commit2(voters.get(voterNode), identityIndex, 0);
                        voterNode.printBlockchain(0);
                    } else {*/
                        commitmentBlockIndex = voterNode.commit(voters.get(voterNode), identityIndex, 0);
//                    }
//                    if (commitCounter == 99) {
//                        System.out.println("commitmentBlockIndices.put(" + voterNode.getName() + ", " + (voterNode.getBlockchain(0).size() - 1) + ")");
//                    }
                    if (commitmentBlockIndex != -1) {
                        commitmentBlockIndices.put(voterNode, commitmentBlockIndex);
                    }
                    propagationCounter = propagationDelay;
                }
                commitCounter++;
                if (commitCounter == 10000) {
//                    System.out.println("commitCounter == " + commitCounter);
                    System.out.println("PROBLEM (commit):");
                    for (ApplicationLayer node: voterNodes) {
                        System.out.println("--- " + node.getName());
                        if (voterNodesStillCommitting.contains(node)) {
                            System.out.println("--- STILL COMMITTING");
                            int identityIndex = node.getCurrentIdentityIndex(0);
                            System.out.println("--- IDENTITY INDEX: " + identityIndex);
                            byte[] publicKey = node.getPublicKey(identityIndex);
                            if (publicKey != null) {
                                System.out.println("--- " + Hex.toHexString(publicKey));
                            }
                            node.printIdentities();
                        } else {
                            System.out.println("--- FINISHED COMMITTING");
                        }
//                        node.printBlockchain(0);
                    }
                }
            } else if (electionManager.getElectionPhase(0) == ElectionPhase.COMMITMENT) {
                electionManager.endCommitmentPeriod(0, 0);
                propagationCounter = propagationDelay * 10;
//                System.out.println("Started Voting phase");
                System.out.print("ce ");
                currentElectionPhase = ElectionPhase.VOTING;
            } else {
                for (ApplicationLayer node : voterNodes) {
                    List<Double> tallies = node.tally(candidateBytes, 0);
                    for (int i = 0; i < tallies.size(); i++) {
                        double nodeTally = tallies.get(i).doubleValue();
                        double realTally = tally.get(i).doubleValue();
                        if (nodeTally > realTally + 0.0001 || nodeTally < realTally - 0.0001) {
                            resultsAreCorrect = false;
                            resultDifferences.add(tallies.get(i).doubleValue() - tally.get(i).doubleValue());
                        }
                    }
                }
                electionIsRunning = false;
            }

            TimeUtils.incrementTime();
            electionManager.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().handleMessages();
            for (ApplicationLayer node: voterNodes) {
                node.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().handleMessages();
            }
            for (Spammer spammer: spammerNodes) {
                spammer.getNetworkLayer().handleMessages();
            }
            for (ApplicationLayer node: voterNodes) {
                node.getPrivacyLayer().getBlockchainLayer().update();
            }
            for (Spammer spammer: spammerNodes) {
                for (int i = 0; i < 1000; i++) {
                    spammer.spam();
                }
            }
        }
        blockchainLength = electionManager.getBlockchain(0).size();
        maxAnonymousBroadcastSentCounter = electionManager.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getAnonymousBroadcastSentCounter();
        minAnonymousBroadcastSentCounter = maxAnonymousBroadcastSentCounter;
        averageAnonymousBroadcastSentCounter = maxAnonymousBroadcastSentCounter;
        maxAnonymousBroadcastReceivedCounter = electionManager.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getAnonymousBroadcastReceivedCounter();
        minAnonymousBroadcastReceivedCounter = maxAnonymousBroadcastReceivedCounter;
        averageAnonymousBroadcastReceivedCounter = maxAnonymousBroadcastReceivedCounter;
        maxMessageCounter = electionManager.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getMessageCounter();
        minMessageCounter = maxMessageCounter;
        averageMessageCounter = maxMessageCounter;
        allNodesDilutedToDesiredDepth = true;
        for (ApplicationLayer node: voterNodes) {
            NetworkLayer networkLayer = node.getPrivacyLayer().getBlockchainLayer().getNetworkLayer();
            double currentAnonymousBroadcastSentCounter = networkLayer.getAnonymousBroadcastSentCounter();
            double currentAnonymousBroadcastReceivedCounter = networkLayer.getAnonymousBroadcastReceivedCounter();
            double currentMessageCounter = networkLayer.getMessageCounter();
            averageAnonymousBroadcastSentCounter += currentAnonymousBroadcastSentCounter;
            if (currentAnonymousBroadcastSentCounter > maxAnonymousBroadcastSentCounter) {
                maxAnonymousBroadcastSentCounter = currentAnonymousBroadcastSentCounter;
            }
            if (currentAnonymousBroadcastSentCounter < minAnonymousBroadcastSentCounter) {
                minAnonymousBroadcastSentCounter = currentAnonymousBroadcastSentCounter;
            }
            averageAnonymousBroadcastReceivedCounter += currentAnonymousBroadcastReceivedCounter;
            if (currentAnonymousBroadcastReceivedCounter > maxAnonymousBroadcastReceivedCounter) {
                maxAnonymousBroadcastReceivedCounter = currentAnonymousBroadcastReceivedCounter;
            }
            if (currentAnonymousBroadcastReceivedCounter < minAnonymousBroadcastReceivedCounter) {
                minAnonymousBroadcastReceivedCounter = currentAnonymousBroadcastReceivedCounter;
            }
            averageMessageCounter += currentMessageCounter;
            if (currentMessageCounter > maxMessageCounter) {
                maxMessageCounter = currentMessageCounter;
            }
            if (currentMessageCounter < minMessageCounter) {
                minMessageCounter = currentMessageCounter;
            }
            int identityIndex = node.getCurrentIdentityIndex(0);
            if (node.getDepth(identityIndex, 0) < desiredDilutionDepth) {
                allNodesDilutedToDesiredDepth = false;
            }
        }
        averageAnonymousBroadcastSentCounter /= 1 + voterNodes.size();
        averageAnonymousBroadcastReceivedCounter /= 1 + voterNodes.size();
        averageMessageCounter /= 1 + voterNodes.size();

        if (!resultsAreCorrect) {
/*            System.out.println("Finished with correct result.");
            for (ApplicationLayer node: voterNodes) {
                node.printBlockchain(0);
            }
        } else {*/
            System.out.println("Finished with incorrect result.");
            System.out.println("Differences:");
            for (Double difference: resultDifferences) {
                System.out.println(difference);
            }
//            for (ApplicationLayer node: voterNodes) {
//                System.out.println(node.getName());
//                node.printBlockchain(0);
//            }
        }
        electionManager.exit();
        for (ApplicationLayer node: voterNodes) {
            node.exit();
        }
    }

    public boolean areResultsCorrect() {
        return resultsAreCorrect;
    }

    public int getBlockchainLength() {
        return blockchainLength;
    }

    public double getAverageAnonymousBroadcastSentCounter() {
        return averageAnonymousBroadcastSentCounter;
    }

    public double getMaxAnonymousBroadcastSentCounter() {
        return maxAnonymousBroadcastSentCounter;
    }

    public double getMinAnonymousBroadcastSentCounter() {
        return minAnonymousBroadcastSentCounter;
    }

    public double getAverageAnonymousBroadcastReceivedCounter() {
        return averageAnonymousBroadcastReceivedCounter;
    }

    public double getMaxAnonymousBroadcastReceivedCounter() {
        return maxAnonymousBroadcastReceivedCounter;
    }

    public double getMinAnonymousBroadcastReceivedCounter() {
        return minAnonymousBroadcastReceivedCounter;
    }

    public double getAverageMessageCounter() {
        return averageMessageCounter;
    }

    public double getMaxMessageCounter() {
        return maxMessageCounter;
    }

    public double getMinMessageCounter() {
        return minMessageCounter;
    }

    public boolean areAllNodesDilutedToDesiredDepth() {
        return allNodesDilutedToDesiredDepth;
    }
}
