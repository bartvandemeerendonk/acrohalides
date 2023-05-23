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

    public static void runSimulationSuite() {
        Set<Integer> maximumPoolSwitchCounters = Set.of(5, 10, 20);
        Set<Integer> maximumApplicationSwitchCounters = Set.of(5, 10, 20, 30);
        Set<Integer> numberOfVotersExponents = Set.of(2,3,4,5);
        int numberOfSimulationsPerValues = 10;
        double minimumBlockchainLength = -1;
        double minimumNumberOfMessages = -1;
        int idealDepthIncreaseInterval = -1;
        int idealPreDepthInterval = -1;
        int idealPropagationDelay = -1;
        int depthIncreaseInterval = 1000;
        int preDepthInterval = 100;
        int propagationDelay = 5;
        System.out.println("voters,dilution_depth,maximum_switch_counter_from_pool,maximum_switch_counter_from_application,depth_increase_interval,pre_depth_interval,results_correct,all_nodes_diluted_to_desired_depth,blockchain_length,average_anonymous_broadcasts_sent,max_anonymous_broadcasts_sent,min_anonymous_broadcasts_sent,average_anonymous_broadcasts_received,max_anonymous_broadcasts_received,min_anonymous_broadcasts_received,average_messages,max_messages,min_messages");
        for (Integer numberOfVotersExponent: numberOfVotersExponents) {
            int numberOfVoters = 1;
            for (int i = 0; i < numberOfVotersExponent; i++) {
                numberOfVoters *= 2;
            }
            for (int desiredDilutionDepth = 1; desiredDilutionDepth < 3; desiredDilutionDepth++) {
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
        System.out.println("Finished all simulations.");
        System.out.println("Ideal depthIncreaseInterval: " + idealDepthIncreaseInterval + ", ideal preDepthInterval: " + idealPreDepthInterval + ", ideal propagationDelay: " + idealPropagationDelay + ", averageNumberOfMessages: " + minimumNumberOfMessages);
    }

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());
        runSimulationSuite();
//        launch(args);
        EXITING = true;
    }
}
