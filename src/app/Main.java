package app;

import app.Client.Datastructures.BlameCircle;
import app.Client.Datastructures.ByteSet;
import app.Client.Datastructures.DilutionApplicationCircle;
import app.Client.Layers.ApplicationLayer.ApplicationLayer;
import app.Client.Layers.ApplicationLayer.Candidate;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.Block;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.CommitmentBlock;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.BlockchainLayer.BlockchainLayer;
import app.Client.Layers.BlockchainLayer.DilutionApplication;
import app.Client.Layers.BlockchainLayer.KeyWithOrigin;
import app.Client.Utils.ByteUtils;
import app.Client.Utils.PrivacyUtils;
import app.Client.Utils.TimeUtils;
import app.Simulation.Simulation;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.util.*;

public class Main extends Application {
    public static boolean EXITING = false;

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("Dashboard/main.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("Acrohalides");
        primaryStage.setScene(new Scene(root, 1200, 800));
//        Controller controller = loader.getController();
        primaryStage.show();
    }

    public static void simulate(String fileName, int propagationDelay) {
        int depthIncreaseInterval = 5000;
        int preDepthInterval = 100;
        int desiredDilutionDepth = 2;
        ArrayList<Candidate> candidates = new ArrayList<>();
        ArrayList<byte[]> candidateBytes = new ArrayList<>();
        ApplicationLayer electionManager = new ApplicationLayer("ElectionManager");
        ArrayList<ApplicationLayer> voterNodes = new ArrayList<>();
        HashMap<ApplicationLayer, String> voterIds = new HashMap<>();
        HashMap<ApplicationLayer, String> voters = new HashMap<>();
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
            ArrayList<Double> tally = new ArrayList<>();
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
            Random random = new Random();
            boolean electionIsRunning = true;
            boolean electionWasStarted = false;
            boolean diluted = false;
            boolean committed = false;
            int propagationCounter = 0;
            boolean resultsAreCorrect = true;
            ArrayList<ApplicationLayer> votersToRegister = new ArrayList<>();
            HashMap<ApplicationLayer, Long> commitmentBlockIndices = new HashMap<>();
            for (ApplicationLayer node: voterNodes) {
                votersToRegister.add(node);
            }
            int dilutionCounter = 0;
            while (electionIsRunning) {
                if (propagationCounter > 0) {
                    propagationCounter--;
                } else if (!electionWasStarted) {
                    electionManager.createElectionManagerRole();
                    electionManager.createElection(0);
                    electionWasStarted = true;
                    propagationCounter = propagationDelay;
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
                } else if (!diluted) {
                    diluted = true;
                    for (ApplicationLayer node: voterNodes) {
                        int identityIndex = node.getCurrentIdentityIndex(0);
                        if (node.getDepth(identityIndex, 0) < desiredDilutionDepth) {
                            diluted = false;
                        }
                    }
                    if (!diluted) {
                        int dilutingVoterIndex = random.nextInt(voterNodes.size());
                        ApplicationLayer dilutingVoterNode = voterNodes.get(dilutingVoterIndex);
                        int identityIndex = dilutingVoterNode.getCurrentIdentityIndex(0);
                        int nodeSelectCounter = 0;
                        while (dilutingVoterNode.getDepth(identityIndex, 0) >= desiredDilutionDepth && nodeSelectCounter < 100) { // or is in dilution process
                            dilutingVoterIndex = random.nextInt(voterNodes.size());
                            dilutingVoterNode = voterNodes.get(dilutingVoterIndex);
                            identityIndex = dilutingVoterNode.getCurrentIdentityIndex(0);
                            nodeSelectCounter++;
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
                        dilutionCounter++;
//                        System.out.println("c " + dilutionCounter);
                        if (dilutionCounter % depthIncreaseInterval == depthIncreaseInterval - preDepthInterval) {
                            System.out.println("Preinc (" + dilutionCounter + ")");
                            electionManager.preIncrementDilutionMaxDepth(0, 0);
                        } else if (dilutionCounter % depthIncreaseInterval == 0) {
                            System.out.println("Depth++ (" + dilutionCounter + ")");
                            electionManager.incrementDilutionMaxDepth(0, 0);
                        }
                    }
                    propagationCounter = propagationDelay;
                } else if (electionManager.getElectionPhase(0) == ElectionPhase.DILUTION) {
                    electionManager.endDilutionPeriod(0, 0, candidates);
                    propagationCounter = propagationDelay;
                } else if (!committed) {
                    committed = commitmentBlockIndices.size() == voterNodes.size();
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
                    }
                    if (!committed) {
                        ApplicationLayer voterNode = voterNodes.get(random.nextInt(voterNodes.size()));
                        while (commitmentBlockIndices.containsKey(voterNode)) {
                            voterNode = voterNodes.get(random.nextInt(voterNodes.size()));
                        }
                        int identityIndex = voterNode.getCurrentIdentityIndex(0);
                        voterNode.commit(voters.get(voterNode), identityIndex, 0);
                        commitmentBlockIndices.put(voterNode, (long) voterNode.getBlockchain(0).size() - 1);
                        propagationCounter = propagationDelay;
                    }
                } else if (electionManager.getElectionPhase(0) == ElectionPhase.COMMITMENT) {
                    electionManager.endCommitmentPeriod(0, 0);
                    propagationCounter = propagationDelay;
                } else {
                    for (ApplicationLayer node : voterNodes) {
                        List<Double> tallies = node.tally(candidateBytes, 0);
                        for (int i = 0; i < tallies.size(); i++) {
                            if (tallies.get(i).doubleValue() != tally.get(i).doubleValue()) {
                                resultsAreCorrect = false;
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
            }

            if (resultsAreCorrect) {
                System.out.println("Finished with correct result.");
                for (ApplicationLayer node: voterNodes) {
                    node.printBlockchain(0);
                }
            } else {
                System.out.println("Finished with incorrect result.");
                for (ApplicationLayer node: voterNodes) {
                    node.printBlockchain(0);
                }
            }
        } catch (IOException exception) {
            System.out.println("IO exception");
        }
    }

    public static void test() {
        ApplicationLayer node1 = new ApplicationLayer("Node1");
        ApplicationLayer node2 = new ApplicationLayer("Node2");
        ApplicationLayer node3 = new ApplicationLayer("Node3");
        ApplicationLayer node4 = new ApplicationLayer("Node4");
        node1.connect(node2.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), node2.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
        node2.connect(node3.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), node3.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
        node3.connect(node4.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), node4.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
        node2.connect(node4.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), node4.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
        node1.createElectionManagerRole();
        node1.createElection(0);
        for (int i = 0; i < 400; i++) {
            switch (i) {
                case 20:
                    node1.createKeyPair();
                    node1.registerVoter("Demanagervandeblockcha", node1.getPublicKey(0), 0, 0);
                    break;
                case 40:
                    node2.createKeyPair();
                    node1.registerVoter("Bart van de Meerendonk", node2.getPublicKey(0), 0, 0);
                    break;
                case 60:
                    node3.createKeyPair();
                    node1.registerVoter("Iemand anderssssssssss", node3.getPublicKey(0), 0, 0);
                    break;
                case 80:
                    node2.createKeyPair();
                    node1.registerVoter("Bart van de Meerendonk", node2.getPublicKey(1), 0, 0);
                    break;
                case 100:
                    node1.endRegistrationPeriod(0, 0);
                    break;
                case 120:
                    node1.registerVoter("Bart van de Meerendonk", new byte[PrivacyUtils.PUBLIC_KEY_LENGTH], 0, 0);
                    break;
                case 140:
                    node1.endRegistrationPeriod(0, 0);
                    break;
                case 160:
                    node2.applyForDilution(1, 0);
                    break;
                case 180:
                    node3.applyForDilution(0, 0);
                    break;
                case 200:
                    node1.startDilutionPool(0, 0, false);
                    break;
                case 220:
                    node1.preIncrementDilutionMaxDepth(0, 0);
                    break;
                case 240:
                    node1.incrementDilutionMaxDepth(0, 0);
                    break;
                case 260:
                    node2.applyForDilution(2, 0);
                    break;
                case 280:
                    node3.applyForDilution(1, 0);
                    break;
                case 300:
                    node1.startDilutionPool(0, 0, false);
                    break;
            }
            node1.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().handleMessages();
            node2.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().handleMessages();
            node3.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().handleMessages();
            node4.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().handleMessages();
        }
        node1.printBlockchain(0);
        node2.printBlockchain(0);
        node3.printBlockchain(0);
        node4.printBlockchain(0);
    }

    public static void testPrivateKeyFromBytes() {
        try {
            KeyPair keyPair = PrivacyUtils.generateKeyPair();
            byte[] publicKey = PrivacyUtils.getPublicKeyBytesFromKeyPair(keyPair);
            byte[] privateKey = PrivacyUtils.getPrivateKeyBytesFromKeyPair(keyPair);
            publicKey[0] = 75;
            publicKey[1] = 0;
            publicKey[2] = -82;
            publicKey[3] = 24;
            publicKey[4] = 17;
            publicKey[5] = -4;
            publicKey[6] = 71;
            publicKey[7] = 65;
            publicKey[8] = 57;
            publicKey[9] = -9;
            publicKey[10] = -4;
            publicKey[11] = -34;
            publicKey[12] = -114;
            publicKey[13] = 6;
            publicKey[14] = -30;
            publicKey[15] = 27;
            publicKey[16] = 39;
            publicKey[17] = -13;
            publicKey[18] = -28;
            publicKey[19] = -27;
            publicKey[20] = -4;
            publicKey[21] = 12;
            publicKey[22] = 2;
            publicKey[23] = -67;
            publicKey[24] = 80;
            publicKey[25] = -53;
            publicKey[26] = 124;
            publicKey[27] = 50;
            publicKey[28] = 39;
            publicKey[29] = -20;
            publicKey[30] = -66;
            publicKey[31] = 86;
            publicKey[32] = 106;
            publicKey[33] = 15;
            publicKey[34] = -50;
            publicKey[35] = 119;
            publicKey[36] = 101;
            publicKey[37] = 92;
            publicKey[38] = 57;
            publicKey[39] = 49;
            publicKey[40] = 96;
            publicKey[41] = 61;
            publicKey[42] = 54;
            publicKey[43] = -75;
            publicKey[44] = 101;
            publicKey[45] = 23;
            publicKey[46] = 36;
            publicKey[47] = 16;
            publicKey[48] = -80;
            publicKey[49] = 10;
            publicKey[50] = 123;
            publicKey[51] = -94;
            publicKey[52] = -27;
            publicKey[53] = 50;
            publicKey[54] = 1;
            publicKey[55] = -65;
            publicKey[56] = 37;
            publicKey[57] = 88;
            publicKey[58] = 118;
            publicKey[59] = -84;
            publicKey[60] = -12;
            publicKey[61] = -59;
            publicKey[62] = -93;
            publicKey[63] = -79;
//            Random newRandom = new Random();
//            newRandom.nextBytes(publicKey);
            System.out.println("Random bytes:");
            for (int i = 0; i < 64; i++) {
                System.out.println("publicKey[" + i + "] = " + publicKey[i] + ";");
            }
            if (PrivacyUtils.isValidKeyPair(publicKey, privateKey)) {
                System.out.println("The key pair is valid.");
            } else {
                System.out.println("The key pair is invalid.");
            }
        } catch (InvalidAlgorithmParameterException exception) {
            System.out.println("Invalid Algorithm Parameter Exception");
            System.out.println(exception);
        } catch (NoSuchAlgorithmException exception) {
            System.out.println("No Such Algorithm Parameter Exception");
            System.out.println(exception);
        }
    }

    public static void testHashCircle() {
        System.out.println("testHashCircle()");
        Random random = new Random();
        BlameCircle blameCircle = new BlameCircle(10, PrivacyUtils.PUBLIC_KEY_LENGTH, BlockchainLayer.POOL_IDENTIFIER_SIZE);
        DilutionApplicationCircle dilutionApplicationCircle = new DilutionApplicationCircle(10, PrivacyUtils.PUBLIC_KEY_LENGTH);
        ArrayList<byte[]> keys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] key = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
            for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
                key[j] = (byte) random.nextInt(256);
            }
            System.out.println("Key " + i + " - " + Hex.toHexString(key));
            keys.add(key);
        }
        for (int i = 0; i < random.nextInt(10); i++) {
            int keyIndex = random.nextInt(10);
            byte[] key = keys.get(keyIndex);
            System.out.println("Add key " + keyIndex + " to blameset");
            blameCircle.add(new KeyWithOrigin(key, 0), new KeyWithOrigin(key, 0), new byte[BlockchainLayer.POOL_IDENTIFIER_SIZE]);
        }
        for (int i = 0; i < 1000; i++) {
            int keyIndex = random.nextInt(10);
            byte[] key = keys.get(keyIndex);
            int action = random.nextInt(4);
            switch (action) {
                case 0:
                    System.out.println("add key " + keyIndex);
                    dilutionApplicationCircle.add(new DilutionApplication(new KeyWithOrigin(key, 0), new byte[72]));
                    System.out.println("Size: " + dilutionApplicationCircle.getSize());
                    break;
                case 1:
                    System.out.println("contains key " + keyIndex);
                    dilutionApplicationCircle.contains(new KeyWithOrigin(key, 0));
                    System.out.println("Size: " + dilutionApplicationCircle.getSize());
                    break;
                case 2:
                    System.out.println("pop key");
                    dilutionApplicationCircle.pop(true, blameCircle);
                    System.out.println("Size: " + dilutionApplicationCircle.getSize());
                    break;
                case 3:
                    System.out.println("remove key " + keyIndex);
                    dilutionApplicationCircle.remove(key);
                    System.out.println("Size: " + dilutionApplicationCircle.getSize());
                    break;
            }
        }
    }

    public static void testHashCircle1() {
        Random random = new Random();
        BlameCircle blameCircle = new BlameCircle(10, PrivacyUtils.PUBLIC_KEY_LENGTH, BlockchainLayer.POOL_IDENTIFIER_SIZE);
        DilutionApplicationCircle dilutionApplicationCircle = new DilutionApplicationCircle(10, PrivacyUtils.PUBLIC_KEY_LENGTH);
        byte[] key1 = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
        for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
            key1[j] = (byte) random.nextInt(256);
        }
        byte[] key2 = new byte[PrivacyUtils.PUBLIC_KEY_LENGTH];
        for (int j = 0; j < PrivacyUtils.PUBLIC_KEY_LENGTH; j++) {
            key2[j] = (byte) random.nextInt(256);
        }
        dilutionApplicationCircle.add(new DilutionApplication(new KeyWithOrigin(key1, 0), new byte[72]));
        System.out.println("Size: " + dilutionApplicationCircle.getSize());
        dilutionApplicationCircle.pop(true, blameCircle);
        System.out.println("Size: " + dilutionApplicationCircle.getSize());
        dilutionApplicationCircle.add(new DilutionApplication(new KeyWithOrigin(key2, 0), new byte[72]));
        System.out.println("Size: " + dilutionApplicationCircle.getSize());
        dilutionApplicationCircle.add(new DilutionApplication(new KeyWithOrigin(key1, 0), new byte[72]));
        System.out.println("Size: " + dilutionApplicationCircle.getSize());
    }

    public static void runSimulationSuite() {
//        Set<Integer> depthIncreaseIntervals = Set.of(1000, 2000, 5000, 7000, 10000);
//        Set<Integer> depthIncreaseIntervals = Set.of(7000);
//        Set<Integer> preDepthIntervals = Set.of(20, 50, 100, 200, 500);
//        Set<Integer> preDepthIntervals = Set.of(200);
//        Set<Integer> propagationDelays = Set.of(2, 3, 4, 5, 6);
//        Set<Integer> propagationDelays = Set.of(2);
//        Set<Integer> maximumPoolSwitchCounters = Set.of(5, 10, 20);
        Set<Integer> maximumPoolSwitchCounters = Set.of(5, 10, 20);
        Set<Integer> maximumApplicationSwitchCounters = Set.of(30);
//        Set<Integer> numberOfVotersExponents = Set.of(2,3,4,5);
        Set<Integer> numberOfVotersExponents = Set.of(3);
        int numberOfSimulationsPerValues = 10;
//        int numberOfSimulationsPerValues = 1;
        double minimumBlockchainLength = -1;
        double minimumNumberOfMessages = -1;
        int idealDepthIncreaseInterval = -1;
        int idealPreDepthInterval = -1;
        int idealPropagationDelay = -1;
        int depthIncreaseInterval = 1000;
        int preDepthInterval = 100;
        int propagationDelay = 5;
        System.out.println("voters,dilution_depth,maximum_switch_counter_from_pool,maximum_switch_counter_from_application,depth_increase_interval,pre_depth_interval,results_correct,all_nodes_diluted_to_desired_depth,blockchain_length,average_anonymous_broadcasts_sent,max_anonymous_broadcasts_sent,min_anonymous_broadcasts_sent,average_anonymous_broadcasts_received,max_anonymous_broadcasts_received,min_anonymous_broadcasts_received,average_messages,max_messages,min_messages");
//        for (int j = 0; j < 100; j++) {
//            for (Integer depthIncreaseInterval : depthIncreaseIntervals) {
//                for (Integer preDepthInterval : preDepthIntervals) {
//                    for (Integer propagationDelay : propagationDelays) {
        for (Integer numberOfVotersExponent: numberOfVotersExponents) {
            int numberOfVoters = 1;
            for (int i = 0; i < numberOfVotersExponent; i++) {
                numberOfVoters *= 2;
            }
//            for (int desiredDilutionDepth = 1; desiredDilutionDepth < numberOfVotersExponent; desiredDilutionDepth++) {
            for (int desiredDilutionDepth = 1; desiredDilutionDepth < 2; desiredDilutionDepth++) {
                for (Integer maximumPoolSwitchCounter : maximumPoolSwitchCounters) {
                    BlockchainLayer.SET_MAXIMUM_SWITCH_COUNTER_FROM_POOL(maximumPoolSwitchCounter);
                    for (Integer maximumApplicationSwitchCounter: maximumApplicationSwitchCounters) {
                        BlockchainLayer.SET_MAXIMUM_SWITCH_COUNTER_FROM_APPLICATION(maximumApplicationSwitchCounter);
                        double averageBlockchainLength = 0;
                        double averageNumberOfMessages = 0;
                        double percentageAllNodesDiluted = 0;
                        for (int i = 0; i < numberOfSimulationsPerValues; i++) {
                            Simulation simulation = new Simulation(numberOfVoters, desiredDilutionDepth, 2, propagationDelay, 0);
                            simulation.setDepthIncreaseInterval(depthIncreaseInterval);
                            simulation.setPreDepthInterval(preDepthInterval);
                            simulation.run();
                            int areResultsCorrectNumber = 0;
                            if (simulation.areResultsCorrect()) {
                                areResultsCorrectNumber = 1;
                            }
                            int areAllNodesDilutedToDesiredDepthNumber = 0;
                            if (simulation.areAllNodesDilutedToDesiredDepth()) {
                                areAllNodesDilutedToDesiredDepthNumber = 1;
                            }
                            System.out.println("" + numberOfVoters + "," + desiredDilutionDepth + "," + maximumPoolSwitchCounter + "," + maximumApplicationSwitchCounter + "," + depthIncreaseInterval + "," + preDepthInterval + "," + areResultsCorrectNumber + "," + areAllNodesDilutedToDesiredDepthNumber + "," + simulation.getBlockchainLength() + "," + simulation.getAverageAnonymousBroadcastSentCounter() + "," + simulation.getMaxAnonymousBroadcastSentCounter() + "," + simulation.getMinAnonymousBroadcastSentCounter() + "," + simulation.getAverageAnonymousBroadcastReceivedCounter() + "," + simulation.getMaxAnonymousBroadcastReceivedCounter() + "," + simulation.getMinAnonymousBroadcastReceivedCounter() + "," + simulation.getAverageMessageCounter() + "," + simulation.getMaxMessageCounter() + "," + simulation.getMinMessageCounter());
                            averageBlockchainLength += simulation.getBlockchainLength();
                            averageNumberOfMessages += simulation.getAverageAnonymousBroadcastSentCounter();
                            if (simulation.areAllNodesDilutedToDesiredDepth()) {
                                percentageAllNodesDiluted += 100;
                            }
                        }
                        averageBlockchainLength /= numberOfSimulationsPerValues;
                        averageNumberOfMessages /= numberOfSimulationsPerValues;
                        percentageAllNodesDiluted /= numberOfSimulationsPerValues;
//                        if (minimumBlockchainLength == -1 || minimumBlockchainLength > averageBlockchainLength) {
//                            minimumBlockchainLength = averageBlockchainLength;
                        if (minimumNumberOfMessages == -1 || minimumNumberOfMessages > averageNumberOfMessages) {
                            minimumNumberOfMessages = averageNumberOfMessages;
                            idealDepthIncreaseInterval = depthIncreaseInterval;
                            idealPreDepthInterval = preDepthInterval;
                            idealPropagationDelay = propagationDelay;
                        }
                    }
                }
            }
        }
//                        System.out.println("Average chain length " + averageBlockchainLength + " average number of messages " + averageNumberOfMessages + " all nodes diluted to desired depth in " + percentageAllNodesDiluted + "% of cases (depthIncreaseInterval: " + depthIncreaseInterval + ", preDepthInterval: " + preDepthInterval + ", propagationDelay: " + propagationDelay + ")");
//                    }
//                }
//            }
//        }
        System.out.println("Finished all simulations.");
        System.out.println("Ideal depthIncreaseInterval: " + idealDepthIncreaseInterval + ", ideal preDepthInterval: " + idealPreDepthInterval + ", ideal propagationDelay: " + idealPropagationDelay + ", averageNumberOfMessages: " + minimumNumberOfMessages);
    }

    public static void testByteSet() {
        SecureRandom secureRandom = new SecureRandom();
        ArrayList<byte[]> availableByteStrings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            byte[] byteString = new byte[64];
            secureRandom.nextBytes(byteString);
            availableByteStrings.add(byteString);
        }
        ByteSet byteSet = new ByteSet(64);
        HashSet<byte[]> controlSet = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            byte[] byteString = availableByteStrings.get(secureRandom.nextInt(100));
            if (secureRandom.nextBoolean()) {
                byteSet.add(byteString);
                controlSet.add(byteString);
            } else {
                byteSet.remove(byteString);
                controlSet.remove(byteString);
            }
        }
        int errorCount = 0;
        for (int i = 0; i < 100; i++) {
            byte[] byteString = availableByteStrings.get(i);
            if (byteSet.contains(byteString) != controlSet.contains(byteString)) {
                errorCount++;
            }
        }
        if (errorCount > 0) {
            System.out.println("ERRORS: " + errorCount);
        }
    }

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
//        testHashCircle1();
//        for (int i = 0; i < 100000; i++) {
//            testHashCircle();
//        }
//        System.out.println("All tests succeeded");
//        simulate("D:\\Studie\\masterproef\\election2.txt", 3);
//        Simulation simulation = new Simulation("D:\\Studie\\masterproef\\election2.txt", 3);
//        for (int i = 0; i < 1000; i++) {
//            testByteSet();
//        }
//        System.out.println("Finished testing byteset");
        runSimulationSuite();
//        test();
//        launch(args);
//        testPrivateKeyFromBytes();
        EXITING = true;
    }
}
