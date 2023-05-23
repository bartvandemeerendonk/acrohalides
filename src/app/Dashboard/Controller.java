package app.Dashboard;

import app.Client.Layers.ApplicationLayer.Candidate;
import app.Client.Layers.ApplicationLayer.ApplicationLayer;
import app.Client.Layers.BlockchainLayer.Blockchain.Blocks.*;
import app.Client.Layers.BlockchainLayer.Blockchain.ElectionPhase;
import app.Client.Layers.NetworkLayer.NetworkingTask;
import app.Client.Utils.ByteUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Controller {
    public Button addNodeButton;
    public GridPane nodesGridPane;
    private ArrayList<ApplicationLayer> nodes;
    private ApplicationLayer nodeToConnect;
    private HashMap<ApplicationLayer, Node> nodeViews;
    private HashMap<ApplicationLayer, Boolean> inManagedElections;
    private HashMap<ApplicationLayer, Integer> selectedIdentityIndices;
    private HashMap<ApplicationLayer, byte[]> selectedIdentities;
    private HashMap<ApplicationLayer, Integer> selectedElections;
    private HashMap<ApplicationLayer, Integer> selectedManagerRoles;
    private ArrayList<String> candidates;

    private static Controller instance;
    public static Controller getInstance() {
        return instance;
    }

    public void initialize() {
        nodes = new ArrayList<>();
        nodeToConnect = null;
        nodeViews = new HashMap<>();
        inManagedElections = new HashMap<>();
        selectedIdentityIndices = new HashMap<>();
        selectedIdentities = new HashMap<>();
        selectedElections = new HashMap<>();
        selectedManagerRoles = new HashMap<>();
        candidates = new ArrayList<>();
        ControllerRefreshTask controllerRefreshTask = new ControllerRefreshTask();
        controllerRefreshTask.setController(this);
        Thread thread = new Thread(controllerRefreshTask);
        thread.start();
        instance = this;
    }

    private String getNewNodeName() {
        return "Node " + String.valueOf(nodes.size());
    }

    public synchronized void refresh() {
        for (int i = 0; i < nodes.size(); i++) {
            updateNodeView(nodes.get(i));
            updateTally(nodes.get(i));
        }
    }

    private javafx.scene.Node getBlockchainView(ApplicationLayer node) {
        HBox hBox = new HBox();
/*        if (node.getNumberOfElections() > 0) {
            List<Block> blocks = node.getBlockchain(selectedElections.get(node));
            for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i);
                Label blockLabel = new Label();
                blockLabel.setText(new String(block.getPrefix()));
                hBox.getChildren().add(blockLabel);
            }
        }*/
        return hBox;
    }

    public void clickCreateIdentityButton(ApplicationLayer node) {
        node.createKeyPair();
        refresh();
    }

    public void clickRegisterButton(ApplicationLayer node, TextField voterIdToRegisterTextField, TextField keyToRegisterTextField) {
        String keyString = keyToRegisterTextField.getText();
        if (ByteUtils.isHex(keyString)) {
            byte[] publicKey = Hex.decode(keyString);
            node.registerVoter(voterIdToRegisterTextField.getText(), publicKey, selectedManagerRoles.get(node), selectedElections.get(node));
            refresh();
        }
    }

    public void clickEndRegistrationButton(ApplicationLayer node) {
        node.endRegistrationPeriod(selectedManagerRoles.get(node), selectedElections.get(node));
        refresh();
    }

    public void clickDilutionApplication(ApplicationLayer node) {
        node.applyForDilution(selectedIdentityIndices.get(node),selectedElections.get(node));
        refresh();
    }

    public void clickDilutionPool(ApplicationLayer node) {
        node.startDilutionPool(selectedIdentityIndices.get(node),selectedElections.get(node), true);
        refresh();
    }

    public void clickPreDepthButton(ApplicationLayer node) {
        node.preIncrementDilutionMaxDepth(selectedManagerRoles.get(node), selectedElections.get(node));
        refresh();
    }

    public void clickDepthButton(ApplicationLayer node) {
        node.incrementDilutionMaxDepth(selectedManagerRoles.get(node), selectedElections.get(node));
        refresh();
    }

    public void clickEndDilutionButton(ApplicationLayer node) {
        ArrayList<Candidate> candidateArrayList = new ArrayList<>();
        Candidate pvda = new Candidate(false, "PvdA______", "PvdA");
        Candidate pvdaPerson = new Candidate(true, "PvdAPerson", "PvdA");
        pvdaPerson.setParent(pvda);
        candidateArrayList.add(pvda);
        candidateArrayList.add(pvdaPerson);
        candidateArrayList.add(new Candidate(true, "Vlaams Bel", "Vlaams Belang"));
        node.endDilutionPeriod(selectedManagerRoles.get(node), selectedElections.get(node), candidateArrayList);
        refresh();
    }

    public void clickVoteButton(ApplicationLayer node, ListView<javafx.scene.Node> candidatesListView) {
        int selectedIndex = -1;
        for (int candidateIndex: candidatesListView.getSelectionModel().getSelectedIndices()) {
            selectedIndex = candidateIndex;
        }
        if (selectedIndex != -1) {
            List<Candidate> candidates2 = node.getElectionCandidates(selectedElections.get(node));
            String candidate = candidates2.get(selectedIndex).getTag();
            node.commit(candidate, selectedIdentityIndices.get(node), selectedElections.get(node));
            refresh();
        }
    }

    public void clickEndCommitmentButton(ApplicationLayer node) {
        node.endCommitmentPeriod(selectedManagerRoles.get(node), selectedElections.get(node));
        refresh();
    }

    public void updateTally(ApplicationLayer node) {
        List<Candidate> candidates2 = node.getElectionCandidates(selectedElections.get(node));
        if (candidates2 != null && node.getElectionPhase(selectedElections.get(node)) == ElectionPhase.VOTING) {
            ArrayList<byte[]> candidateBytes = new ArrayList<>();
            for (int i = 0; i < candidates2.size(); i++) {
                candidateBytes.add(candidates2.get(i).getTag().getBytes());
            }
            List<Double> tallies = node.tally(candidateBytes, selectedElections.get(node));
            VBox vBox = (VBox) nodeViews.get(node);
            HBox bodyHBox = (HBox) vBox.getChildren().get(1);
            Pane phasePane = (Pane) bodyHBox.getChildren().get(2);
            for (int i = phasePane.getChildren().size() - 1; i >= 16; i--) {
                phasePane.getChildren().remove(i);
            }
//            for (int i = 0; i < candidates2.size(); i++) {

//                Label tallyLabel = (Label) phasePane.getChildren().get(15 + 2 * i);
//                tallyLabel.setText("" + tallies.get(i).doubleValue() * 100 + "%");
//            }
            for (int i = 0; i < candidates2.size(); i++) {
                Label candidateLabel = new Label();
                candidateLabel.setLayoutY(30 * i);
                candidateLabel.setLayoutX(0);
                candidateLabel.setText(candidates2.get(i).getDescription());
                phasePane.getChildren().add(candidateLabel);

                Label tallyLabel = new Label();
                tallyLabel.setLayoutY(30 * i);
                tallyLabel.setLayoutX(100);
                tallyLabel.setText("" + tallies.get(i).doubleValue() * 100 + "%");
                phasePane.getChildren().add(tallyLabel);
            }

        }
    }

    private void clickSubscribeButton(ApplicationLayer node, TextField managerTextField, TextField chainIdTextField) {
//        System.out.println("Try to subscribe " + managerTextField.getText() + " and " + chainIdTextField.getText());
//        System.out.println("Decoded:");
//        System.out.println(Hex.decode(managerTextField.getText()));
//        System.out.println(Hex.decode(chainIdTextField.getText()));
//        System.out.println("end decoding");
        String managerString = managerTextField.getText();
        String chainIdString = chainIdTextField.getText();
        if (ByteUtils.isHex(managerString) && ByteUtils.isHex(chainIdString)) {
            node.subscribeToElection(Hex.decode(managerString), Hex.decode(chainIdString));
            refresh();
        }
    }

    private void clickPrintBlockchainButton(ApplicationLayer node) {
        node.printBlockchain(selectedElections.get(node));
    }

    private void clickUndoBlockButton(ApplicationLayer node) {
        node.undoLastBlock(selectedElections.get(node));
    }

    private javafx.scene.Node getElectionsPane(ApplicationLayer node) {
        Pane electionsPane = new Pane();
        electionsPane.setPrefWidth(200);
        electionsPane.setPrefHeight(200);

        Label electionsLabel = new Label();
        electionsLabel.setText("Elections");
        electionsLabel.setLayoutX(30);
        electionsLabel.setLayoutY(0);
        electionsPane.getChildren().add(electionsLabel);

/*        Button managedElectionsTab = new Button();
        managedElectionsTab.setText("Managed");
        managedElectionsTab.setLayoutX(0);
        managedElectionsTab.setLayoutY(20);
        managedElectionsTab.setOnMouseClicked(event -> {inManagedElections.put(node, true); refresh();});
        electionsPane.getChildren().add(managedElectionsTab);

        Button joinedElectionsTab = new Button();
        joinedElectionsTab.setText("Joined");
        joinedElectionsTab.setLayoutX(100);
        joinedElectionsTab.setLayoutY(20);
        joinedElectionsTab.setOnMouseClicked(event -> {inManagedElections.put(node, false); refresh();});
        electionsPane.getChildren().add(joinedElectionsTab);*/

        ListView<javafx.scene.Node> electionsListView = new ListView<>();
        electionsListView.setLayoutY(30);
        electionsListView.setLayoutX(10);
        electionsListView.setPrefHeight(100);
        electionsListView.setPrefWidth(180);
        electionsListView.setOnMouseClicked(event -> refresh());

        Button addElectionButton = new Button();
        addElectionButton.setText("New");
        addElectionButton.setOnMouseClicked(event -> {clickCreateElectionButton(node); electionsListView.getSelectionModel().select(electionsListView.getItems().size() - 2); refresh();});
        electionsListView.getItems().add(addElectionButton);

        electionsPane.getChildren().add(electionsListView);
        return electionsPane;
    }

    private javafx.scene.Node getIdentitiesPane(ApplicationLayer node) {
        Pane identitiesPane = new Pane();
        identitiesPane.setPrefWidth(200);
        identitiesPane.setPrefHeight(200);

        Label identitiesLabel = new Label();
        identitiesLabel.setText("Identities");
        identitiesLabel.setLayoutX(30);
        identitiesLabel.setLayoutY(0);
        identitiesPane.getChildren().add(identitiesLabel);

        ListView<javafx.scene.Node> identitiesListView = new ListView<>();
        identitiesListView.setLayoutY(30);
        identitiesListView.setLayoutX(10);
        identitiesListView.setPrefHeight(100);
        identitiesListView.setPrefWidth(180);
        identitiesListView.setOnMouseClicked(event -> refresh());

        Button addIdentityButton = new Button();
        addIdentityButton.setText("New");
        addIdentityButton.setOnMouseClicked(event -> {clickCreateIdentityButton(node); identitiesListView.getSelectionModel().select(identitiesListView.getItems().size() - 2); refresh();});
        identitiesListView.getItems().add(addIdentityButton);

        identitiesPane.getChildren().add(identitiesListView);

        TextField publicKeyTextField = new TextField();
        publicKeyTextField.setLayoutX(0);
        publicKeyTextField.setLayoutY(150);
        publicKeyTextField.setVisible(false);
        identitiesPane.getChildren().add(publicKeyTextField);

        return identitiesPane;
    }

    private javafx.scene.Node getPhasePane(ApplicationLayer node) {
        Pane phasePane = new Pane();
        phasePane.setPrefWidth(500);
        phasePane.setPrefHeight(200);

        Label registrationLabel = new Label();
        registrationLabel.setText("Registration");
        registrationLabel.setLayoutX(30);
        registrationLabel.setLayoutY(0);
        registrationLabel.setVisible(false);

        phasePane.getChildren().add(registrationLabel);

        Label voterIdToRegisterLabel = new Label();
        voterIdToRegisterLabel.setText("Voter ID:");
        voterIdToRegisterLabel.setLayoutX(0);
        voterIdToRegisterLabel.setLayoutY(30);
        voterIdToRegisterLabel.setVisible(false);

        phasePane.getChildren().add(voterIdToRegisterLabel);

        TextField voterIdToRegisterTextField = new TextField();
        voterIdToRegisterTextField.setLayoutX(100);
        voterIdToRegisterTextField.setLayoutY(30);
        voterIdToRegisterTextField.setVisible(false);

        phasePane.getChildren().add(voterIdToRegisterTextField);

        Label keyToRegisterLabel = new Label();
        keyToRegisterLabel.setText("Key:");
        keyToRegisterLabel.setLayoutX(0);
        keyToRegisterLabel.setLayoutY(60);
        keyToRegisterLabel.setVisible(false);

        phasePane.getChildren().add(keyToRegisterLabel);

        TextField keyToRegisterTextField = new TextField();
        keyToRegisterTextField.setLayoutX(100);
        keyToRegisterTextField.setLayoutY(60);
        keyToRegisterTextField.setVisible(false);

        phasePane.getChildren().add(keyToRegisterTextField);

        Button registerButton = new Button();
        registerButton.setText("Register");
        registerButton.setLayoutX(0);
        registerButton.setLayoutY(90);
        registerButton.setOnMouseClicked(event -> clickRegisterButton(node, voterIdToRegisterTextField, keyToRegisterTextField));
        registerButton.setVisible(false);
        phasePane.getChildren().add(registerButton);

        Button endRegistrationButton = new Button();
        endRegistrationButton.setText("End registration period");
        endRegistrationButton.setLayoutX(0);
        endRegistrationButton.setLayoutY(120);
        endRegistrationButton.setOnMouseClicked(event -> clickEndRegistrationButton(node));
        endRegistrationButton.setVisible(false);
        phasePane.getChildren().add(endRegistrationButton);

//        if (electionPhase == ElectionPhase.REGISTRATION) {
/*            TextField voterIdToRegisterTextField = new TextField();
            TextField keyToRegisterTextField = new TextField();
            Button registerButton = new Button();
            registerButton.setText("Register");
            registerButton.setOnMouseClicked(event -> clickRegisterButton(node, voterIdToRegisterTextField, keyToRegisterTextField));
            electionHBox.getChildren().add(registerButton);
            Label voterIdToRegisterLabel = new Label();
            voterIdToRegisterLabel.setText("Voter ID:");
            electionHBox.getChildren().add(voterIdToRegisterLabel);
            electionHBox.getChildren().add(voterIdToRegisterTextField);
            Label keyToRegisterLabel = new Label();
            keyToRegisterLabel.setText("Key:");
            electionHBox.getChildren().add(keyToRegisterLabel);
            electionHBox.getChildren().add(keyToRegisterTextField);
            Button endRegistrationButton = new Button();
            endRegistrationButton.setText("End registration period");
            endRegistrationButton.setOnMouseClicked(event -> clickEndRegistrationButton(node));
            electionHBox.getChildren().add(endRegistrationButton);*/
//        }

        Button applyDilutionButton = new Button();
        applyDilutionButton.setText("Apply for dilution");
        applyDilutionButton.setOnMouseClicked(event -> clickDilutionApplication(node));
        applyDilutionButton.setLayoutX(30);
        applyDilutionButton.setLayoutY(0);
        applyDilutionButton.setVisible(false);
        phasePane.getChildren().add(applyDilutionButton);

        Button dilutionPoolButton = new Button();
        dilutionPoolButton.setText("Dilution pool");
        dilutionPoolButton.setOnMouseClicked(event -> clickDilutionPool(node));
        dilutionPoolButton.setLayoutX(30);
        dilutionPoolButton.setLayoutY(30);
        dilutionPoolButton.setVisible(false);
        phasePane.getChildren().add(dilutionPoolButton);

        Button preDepthButton = new Button();
        preDepthButton.setText("Pre-depth");
        preDepthButton.setOnMouseClicked(event -> clickPreDepthButton(node));
        preDepthButton.setLayoutX(30);
        preDepthButton.setLayoutY(30);
        preDepthButton.setVisible(false);
        phasePane.getChildren().add(preDepthButton);

        Button depthButton = new Button();
        depthButton.setText("Depth");
        depthButton.setOnMouseClicked(event -> clickDepthButton(node));
        depthButton.setLayoutX(30);
        depthButton.setLayoutY(30);
        depthButton.setVisible(false);
        phasePane.getChildren().add(depthButton);

        Button endDilutionButton = new Button();
        endDilutionButton.setText("End dilution");
        endDilutionButton.setOnMouseClicked(event -> clickEndDilutionButton(node));
        endDilutionButton.setLayoutX(30);
        endDilutionButton.setLayoutY(60);
        endDilutionButton.setVisible(false);
        phasePane.getChildren().add(endDilutionButton);

        Label candidatesLabel = new Label();
        candidatesLabel.setText("Choose a candidate");
        candidatesLabel.setLayoutX(30);
        candidatesLabel.setLayoutY(0);
        candidatesLabel.setVisible(false);
        phasePane.getChildren().add(candidatesLabel);

        ListView<javafx.scene.Node> candidatesListView = new ListView<>();
        candidatesListView.setLayoutY(30);
        candidatesListView.setLayoutX(10);
        candidatesListView.setPrefHeight(100);
        candidatesListView.setPrefWidth(180);
        candidatesListView.setVisible(false);
        for (String candidate: candidates) {
            Label candidateLabel = new Label();
            candidateLabel.setText(candidate);
            candidatesListView.getItems().add(candidateLabel);
        }
        candidatesListView.setOnMouseClicked(event -> refresh());

        phasePane.getChildren().add(candidatesListView);

        Button voteButton = new Button();
        voteButton.setLayoutX(30);
        voteButton.setLayoutY(140);
        voteButton.setText("Vote");
        voteButton.setOnMouseClicked(event -> clickVoteButton(node, candidatesListView));
        voteButton.setVisible(false);
        phasePane.getChildren().add(voteButton);

        Button endCommitmentButton = new Button();
        endCommitmentButton.setText("End commitment");
        endCommitmentButton.setOnMouseClicked(event -> clickEndCommitmentButton(node));
        endCommitmentButton.setLayoutX(30);
        endCommitmentButton.setLayoutY(60);
        endCommitmentButton.setVisible(false);
        phasePane.getChildren().add(endCommitmentButton);

/*        for (int i = 0; i < candidates.size(); i++) {
            Label candidateLabel = new Label();
            candidateLabel.setLayoutY(30 * i);
            candidateLabel.setLayoutX(0);
            candidateLabel.setText(candidates.get(i));
            phasePane.getChildren().add(candidateLabel);

            Label tallyLabel = new Label();
            tallyLabel.setLayoutY(30 * i);
            tallyLabel.setLayoutX(100);
            tallyLabel.setText("-");
            phasePane.getChildren().add(tallyLabel);
        }*/

        return phasePane;
    }

    public void clickViewLogButton(ApplicationLayer node) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("logview.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root, 600, 550));
            stage.setResizable(false);
            stage.setTitle("Log");
            Logview logview = loader.getController();
            logview.setApplicationLayer(node);
//            LoginController controller = loader.getController();
//            stage.setOnCloseRequest(event -> controller.finish());
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clickSqueezeButton(ApplicationLayer node) {
//        System.out.println("Exsqueeze me.");
        node.squeeze();
    }

    private javafx.scene.Node getNodeView(ApplicationLayer node) {
        VBox vBox = new VBox();

        HBox hBox = new HBox();
        Label nameLabel = new Label();
        nameLabel.setText(node.getName());
        hBox.getChildren().add(nameLabel);
        Button connectButton = new Button();
        connectButton.setText("Connect");
        connectButton.setOnMouseClicked(event -> clickConnectNodeButton(node));
        hBox.getChildren().add(connectButton);
        Button viewLogButton = new Button();
        viewLogButton.setText("Log");
        viewLogButton.setOnMouseClicked(event -> clickViewLogButton(node));
        hBox.getChildren().add(viewLogButton);
        Button squeezeButton = new Button();
        squeezeButton.setText("Squeeze");
        squeezeButton.setOnMouseClicked(event -> clickSqueezeButton(node));
        hBox.getChildren().add(squeezeButton);
//        Button createElectionButton = new Button();
//        createElectionButton.setText("Create election");
//        createElectionButton.setOnMouseClicked(event -> clickCreateElectionButton(node));
//        hBox.getChildren().add(createElectionButton);
        vBox.getChildren().add(hBox);

        HBox bodyHBox = new HBox();
        vBox.getChildren().add(bodyHBox);

        bodyHBox.getChildren().add(getElectionsPane(node));
        bodyHBox.getChildren().add(getIdentitiesPane(node));
        bodyHBox.getChildren().add(getPhasePane(node));

        javafx.scene.Node blockchainView = getBlockchainView(node);
        vBox.getChildren().add(blockchainView);
        nodeViews.put(node, vBox);
        inManagedElections.put(node, true);
        selectedIdentityIndices.put(node, -1);
        selectedIdentities.put(node, null);
        selectedElections.put(node, -1);
        selectedManagerRoles.put(node, -1);

        return vBox;
    }

    public synchronized void updateNodeView(ApplicationLayer node) {
        VBox vBox = (VBox) nodeViews.get(node);

        HBox hBox = (HBox) vBox.getChildren().get(0);
//        hBox.getChildren().clear();
/*        Label nameLabel = new Label();
        nameLabel.setText(node.getName());
        hBox.getChildren().add(nameLabel);
        Button connectButton = new Button();
        connectButton.setText("Connect");
        connectButton.setOnMouseClicked(event -> clickConnectNodeButton(node));
        hBox.getChildren().add(connectButton);
        Button createElectionButton = new Button();
        createElectionButton.setText("Create election");
        createElectionButton.setOnMouseClicked(event -> clickCreateElectionButton(node));
        hBox.getChildren().add(createElectionButton);*/

//        if (hBox.getChildren().size() > 3) {
//            hBox.getChildren().remove(5);
//            hBox.getChildren().remove(4);
//            hBox.getChildren().remove(3);
//        }
        ElectionPhase electionPhase = ElectionPhase.REGISTRATION;
        if (node.getNumberOfManagedElections() > 0) {
            electionPhase = node.getManagedElectionPhase(0,0);
        } else if (node.getNumberOfElections() > 0) {
            electionPhase = node.getElectionPhase(0);
//        } else {
//            Button subscribeToElectionButton = new Button();
//            subscribeToElectionButton.setText("Subscribe");
//            TextField managerTextField = new TextField();
//            hBox.getChildren().add(managerTextField);
//            TextField chainIdTextField = new TextField();
//            hBox.getChildren().add(chainIdTextField);
//            subscribeToElectionButton.setOnMouseClicked(event -> clickSubscribeButton(node, managerTextField, chainIdTextField));
//            hBox.getChildren().add(subscribeToElectionButton);
        }

        HBox bodyHBox = (HBox) vBox.getChildren().get(1);

        Pane electionsPane = (Pane) bodyHBox.getChildren().get(0);
        ListView<javafx.scene.Node> electionsListView = (ListView<javafx.scene.Node>) electionsPane.getChildren().get(1);

        javafx.scene.Node newElectionButton = electionsListView.getItems().get(electionsListView.getItems().size() - 1);

        int i;
/*        if (inManagedElections.get(node).booleanValue()) {
            for (i = 0; i < node.getNumberOfManagedElections(); i++) {
                Label election1Label = new Label();
                election1Label.setText(new String(Hex.encode(node.getManagedElectionPublicKey(i))));
                if (i < electionsListView.getItems().size()) {
                    electionsListView.getItems().set(i, election1Label);
                } else {
                    electionsListView.getItems().add(election1Label);
                }
            }
            newElectionButton.setVisible(true);
        } else {*/
            for (i = 0; i < node.getNumberOfElections(); i++) {
                Label election1Label = new Label();
                election1Label.setText(new String(Hex.encode(node.getElectionManagerPublicKey(i))));
                if (i < electionsListView.getItems().size()) {
                    electionsListView.getItems().set(i, election1Label);
                } else {
                    electionsListView.getItems().add(election1Label);
                }
            }
            newElectionButton.setVisible(true);
//        }
        if (i < electionsListView.getItems().size()) {
            electionsListView.getItems().set(i, newElectionButton);
        } else {
            electionsListView.getItems().add(newElectionButton);
        }
        i++;
        while(i < electionsListView.getItems().size()) {
            electionsListView.getItems().remove(i);
            i++;
        }
        int electionIndex = -1;
        for (int index: electionsListView.getSelectionModel().getSelectedIndices()) {
            electionIndex = index;
        }
        selectedElections.put(node, electionIndex);

        int electionManagerIndex = -1;

        if (electionIndex != -1 && electionIndex < node.getNumberOfElections()) {
            electionManagerIndex = node.getElectionManagerRoleOfElectionIndex(electionIndex);
//            if (electionManagerIndex != -1) {
//                electionPhase = node.getManagedElectionPhase(electionIndex, 0);
//            } else {
                electionPhase = node.getElectionPhase(electionIndex);
//            }
        }
        selectedManagerRoles.put(node, electionManagerIndex);

        Pane identitiesPane = (Pane) bodyHBox.getChildren().get(1);
        ListView<javafx.scene.Node> identitiesListView = (ListView<javafx.scene.Node>) identitiesPane.getChildren().get(1);

        javafx.scene.Node newIdentityButton = identitiesListView.getItems().get(identitiesListView.getItems().size() - 1);

        for (i = 0; i < node.getPrivacyLayer().getNumberOfIdentities(); i++) {
            Label identityLabel = new Label();
            identityLabel.setText(new String(Hex.encode(node.getPublicKey(i))));
            if (i < identitiesListView.getItems().size()) {
                identitiesListView.getItems().set(i, identityLabel);
            } else {
                identitiesListView.getItems().add(identityLabel);
            }
        }
        if (i < identitiesListView.getItems().size()) {
            identitiesListView.getItems().set(i, newIdentityButton);
        } else {
            identitiesListView.getItems().add(newIdentityButton);
        }
        i++;
        while(i < identitiesListView.getItems().size()) {
            identitiesListView.getItems().remove(i);
            i++;
        }
        int identityIndex = -1;
        for (int index: identitiesListView.getSelectionModel().getSelectedIndices()) {
            identityIndex = index;
        }
        selectedIdentityIndices.put(node, identityIndex);
        TextField publicKeyTextField = (TextField) identitiesPane.getChildren().get(2);
        if (identityIndex == -1 || identityIndex >= node.getNumberOfIdentities()) {
            publicKeyTextField.setVisible(false);
            selectedIdentities.put(node, null);
        } else {
            publicKeyTextField.setVisible(true);
            byte[] publicKey = node.getPublicKey(identityIndex);
            if (!ByteUtils.byteArraysAreEqual(publicKey, selectedIdentities.get(node))) {
                publicKeyTextField.setText(new String(Hex.encode(publicKey)));
                selectedIdentities.put(node, publicKey);
            }
        }

        Pane phasePane = (Pane) bodyHBox.getChildren().get(2);

        Label registrationLabel = (Label) phasePane.getChildren().get(0);
        Label voterIdToRegisterLabel = (Label) phasePane.getChildren().get(1);
        TextField voterIdToRegisterTextField = (TextField) phasePane.getChildren().get(2);
        Label keyToRegisterLabel = (Label) phasePane.getChildren().get(3);
        TextField keyToRegisterTextField = (TextField) phasePane.getChildren().get(4);
        Button registerButton = (Button) phasePane.getChildren().get(5);
        Button endRegistrationButton = (Button) phasePane.getChildren().get(6);
        if (electionIndex != -1 && electionManagerIndex != -1 && electionPhase == ElectionPhase.REGISTRATION) {
            registrationLabel.setVisible(true);
            voterIdToRegisterLabel.setVisible(true);
            voterIdToRegisterTextField.setVisible(true);
            keyToRegisterLabel.setVisible(true);
            keyToRegisterTextField.setVisible(true);
            registerButton.setVisible(true);
            endRegistrationButton.setVisible(true);
        } else {
            registrationLabel.setVisible(false);
            voterIdToRegisterLabel.setVisible(false);
            voterIdToRegisterTextField.setVisible(false);
            keyToRegisterLabel.setVisible(false);
            keyToRegisterTextField.setVisible(false);
            registerButton.setVisible(false);
            endRegistrationButton.setVisible(false);
        }

        Button applyDilutionButton = (Button) phasePane.getChildren().get(7);
        Button dilutionPoolButton = (Button) phasePane.getChildren().get(8);
        Button preDepthButton = (Button) phasePane.getChildren().get(9);
        Button depthButton = (Button) phasePane.getChildren().get(10);
        Button endDilutionButton = (Button) phasePane.getChildren().get(11);
        if (electionIndex != -1 && electionPhase == ElectionPhase.DILUTION) {
            if (identityIndex != -1) {
                applyDilutionButton.setVisible(true);
                dilutionPoolButton.setVisible(true);
            } else {
                applyDilutionButton.setVisible(false);
                dilutionPoolButton.setVisible(false);
            }
            if (electionManagerIndex != -1) {
                endDilutionButton.setVisible(true);
                if (node.isInPreDepthPhase(electionIndex)) {
                    depthButton.setVisible(true);
                    preDepthButton.setVisible(false);
                } else {
                    depthButton.setVisible(false);
                    preDepthButton.setVisible(true);
                }
            } else {
                depthButton.setVisible(false);
                preDepthButton.setVisible(false);
                endDilutionButton.setVisible(false);
            }
        } else {
            applyDilutionButton.setVisible(false);
            dilutionPoolButton.setVisible(false);
            depthButton.setVisible(false);
            preDepthButton.setVisible(false);
            endDilutionButton.setVisible(false);
        }

        Label candidatesLabel = (Label) phasePane.getChildren().get(12);
        ListView<javafx.scene.Node> candidatesListView = (ListView<javafx.scene.Node>) phasePane.getChildren().get(13);
        Button voteButton = (Button) phasePane.getChildren().get(14);
        Button endCommitmentButton = (Button) phasePane.getChildren().get(15);
        if (electionIndex != -1 && electionPhase == ElectionPhase.COMMITMENT) {
            if (identityIndex != -1 && node.isIdentityStillValid(identityIndex)) {
                List<Candidate> candidates2 = node.getElectionCandidates(electionIndex);
                boolean needToUpdateCandidates = candidatesListView.getItems().size() != candidates2.size();
                int j = 0;
                while (!needToUpdateCandidates && j < candidates2.size()) {
                    Label currentCandidateLabel = (Label) candidatesListView.getItems().get(j);
                    if (!currentCandidateLabel.getText().substring(4).equals(candidates2.get(j).getTag())) {
                        needToUpdateCandidates = true;
                    }
                    j++;
                }
                if (needToUpdateCandidates) {
                    candidatesListView.getItems().clear();
                    for (Candidate candidate : candidates2) {
                        Label currentCandidateLabel = new Label();
                        String labelText;
                        if (candidate.isElectable()) {
                            labelText = "(E) ";
                        } else {
                            labelText = "(U) ";
                        }
                        labelText = labelText + candidate.getTag();
                        currentCandidateLabel.setText(labelText);
                        candidatesListView.getItems().add(currentCandidateLabel);
                    }
                }
                candidatesLabel.setVisible(true);
                candidatesListView.setVisible(true);
                voteButton.setVisible(true);
            } else {
                candidatesLabel.setVisible(false);
                candidatesListView.setVisible(false);
                voteButton.setVisible(false);
            }
            if (electionManagerIndex != -1) {
                endCommitmentButton.setVisible(true);
            } else {
                endCommitmentButton.setVisible(false);
            }
        } else {
            candidatesLabel.setVisible(false);
            candidatesListView.setVisible(false);
            voteButton.setVisible(false);
            endCommitmentButton.setVisible(false);
        }

        if (electionIndex != -1 && electionPhase == ElectionPhase.VOTING) {
            for (i = 0; i < candidates.size(); i++) {
                phasePane.getChildren().get(16 + 2 * i).setVisible(true);
                phasePane.getChildren().get(17 + 2 * i).setVisible(true);
            }
        } else {
            for (i = 0; i < candidates.size(); i++) {
                phasePane.getChildren().get(16 + 2 * i).setVisible(false);
                phasePane.getChildren().get(17 + 2 * i).setVisible(false);
            }
        }

        HBox blockchainViewHBox = (HBox) vBox.getChildren().get(2);
        i = 0;
        if (electionIndex != -1 && electionIndex < node.getNumberOfElections()) {
            List<Block> blockchain = node.getBlockchain(electionIndex);
            while (i < blockchain.size()) {
                Block block = blockchain.get(i);
                Pane blockPane = getBlockPane(block);
//                Label blockLabel = new Label();
//                blockLabel.setText(new String(block.getPrefix()));
                if (i < blockchainViewHBox.getChildren().size()) {
                    blockchainViewHBox.getChildren().set(i, blockPane);
                } else {
                    blockchainViewHBox.getChildren().add(blockPane);
                }
                i++;
            }
            Button undoButton = new Button();
            undoButton.setText("Undo");
            undoButton.setOnMouseClicked(event -> clickUndoBlockButton(node));
            if (i < blockchainViewHBox.getChildren().size()) {
                blockchainViewHBox.getChildren().set(i, undoButton);
            } else {
                blockchainViewHBox.getChildren().add(undoButton);
            }
            i++;
        }
        while (i < blockchainViewHBox.getChildren().size()) {
            blockchainViewHBox.getChildren().remove(i);
        }
    }

    public String toAbbreviatedString(byte[] key) {
        return Hex.toHexString(key).substring(0, 10) + "...";
    }

    public Pane getBlockPane(Block block) {
        Pane marginPane = new Pane();
        Pane blockPane = new Pane();
        Label blockTypeLabel = new Label();
        switch (block.getPrefix()[0]) {
            case 'I':
                InitializationBlock initializationBlock = (InitializationBlock) block;
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(50);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(60);
                blockTypeLabel.setText("Initialization");
                Label managerLabel = new Label();
                managerLabel.setText(toAbbreviatedString(initializationBlock.getManagerPublicKey()));
                managerLabel.setLayoutX(0);
                managerLabel.setLayoutY(20);
                blockPane.getChildren().add(managerLabel);
                break;
            case 'R':
                RegistrationBlock registrationBlock = (RegistrationBlock) block;
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(70);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(80);
                blockTypeLabel.setText("Registration");
                Label voterIdLabel = new Label();
                voterIdLabel.setText(new String(registrationBlock.getVoterId()));
                voterIdLabel.setLayoutX(0);
                voterIdLabel.setLayoutY(20);
                blockPane.getChildren().add(voterIdLabel);
                Label registeredKeyLabel = new Label();
                registeredKeyLabel.setText(toAbbreviatedString(registrationBlock.getPublicKey()));
                registeredKeyLabel.setLayoutX(0);
                registeredKeyLabel.setLayoutY(40);
                blockPane.getChildren().add(registeredKeyLabel);
                break;
            case 'd':
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(30);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(40);
                blockTypeLabel.setText("Dilution start");
                break;
            case 'D':
                DilutionBlock dilutionBlock = (DilutionBlock) block;
                blockPane.setPrefWidth(160);
                List<byte[]> oldKeys = dilutionBlock.getOldKeys();
                List<byte[]> newKeys = dilutionBlock.getNewKeys();
                blockPane.setPrefHeight(30 + 20 * oldKeys.size());
                marginPane.setPrefWidth(170);
                marginPane.setPrefHeight(40 + 20 * oldKeys.size());
                blockTypeLabel.setText("Dilution");
                for (int i = 0; i < oldKeys.size(); i++) {
                    Label oldKeyLabel = new Label();
                    oldKeyLabel.setText(toAbbreviatedString(oldKeys.get(i)));
                    oldKeyLabel.setLayoutX(0);
                    oldKeyLabel.setLayoutY(20 * (1 + i));
                    blockPane.getChildren().add(oldKeyLabel);
                    Label newKeyLabel = new Label();
                    newKeyLabel.setText(toAbbreviatedString(newKeys.get(i)));
                    newKeyLabel.setLayoutX(80);
                    newKeyLabel.setLayoutY(20 * (1 + i));
                    blockPane.getChildren().add(newKeyLabel);
                }
                break;
            case '0':
                DepthBlock depthBlock = (DepthBlock) block;
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(30);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(40);
                blockTypeLabel.setText("Depth");
                break;
            case '1':
                PreDepthBlock preDepthBlock = (PreDepthBlock) block;
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(30);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(40);
                blockTypeLabel.setText("Pre-depth");
                break;
            case 'e':
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(30);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(40);
                blockTypeLabel.setText("Dilution end");
                break;
            case 'C':
                CommitmentBlock commitmentBlock = (CommitmentBlock) block;
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(70);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(80);
                blockTypeLabel.setText("Commitment");
                Label commitmentKeyLabel = new Label();
                commitmentKeyLabel.setText(toAbbreviatedString(commitmentBlock.getPublicKey()));
                commitmentKeyLabel.setLayoutX(0);
                commitmentKeyLabel.setLayoutY(20);
                blockPane.getChildren().add(commitmentKeyLabel);
                Label voteHashLabel = new Label();
                voteHashLabel.setText(toAbbreviatedString(commitmentBlock.getVoteHash()));
                voteHashLabel.setLayoutX(0);
                voteHashLabel.setLayoutY(40);
                blockPane.getChildren().add(voteHashLabel);
                break;
            case '3':
                blockPane.setPrefWidth(100);
                blockPane.setPrefHeight(30);
                marginPane.setPrefWidth(110);
                marginPane.setPrefHeight(40);
                blockTypeLabel.setText("Commitment end");
                break;
        }
        blockTypeLabel.setLayoutY(0);
        blockTypeLabel.setLayoutX(0);
        blockPane.getChildren().add(blockTypeLabel);
        BorderStroke[] borderStrokes = new BorderStroke[4];
        borderStrokes[0] = new BorderStroke(Paint.valueOf("black"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT);
        borderStrokes[1] = new BorderStroke(Paint.valueOf("black"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT);
        borderStrokes[2] = new BorderStroke(Paint.valueOf("black"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT);
        borderStrokes[3] = new BorderStroke(Paint.valueOf("black"), BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths.DEFAULT);
        blockPane.setBorder(new Border(borderStrokes));
        blockPane.setLayoutX(5);
        blockPane.setLayoutY(5);
        marginPane.getChildren().add(blockPane);
        return marginPane;
    }

    public void clickAddNodeButton() {
        String nodeName = getNewNodeName();
        ApplicationLayer newNode = new ApplicationLayer(nodeName);
        NetworkingTask nextTask = new NetworkingTask();
        nextTask.setNetworkLayer(newNode.getPrivacyLayer().getBlockchainLayer().getNetworkLayer());
        Thread thread = new Thread(nextTask);
        thread.start();
        nodes.add(newNode);
        javafx.scene.Node nodeView = getNodeView(newNode);
        nodeViews.put(newNode, nodeView);
        nodesGridPane.addRow(nodeViews.size() - 1, nodeView);
        refresh();
    }

    public void clickConnectNodeButton(ApplicationLayer node) {
        if (nodeToConnect == null) {
            nodeToConnect = node;
        } else {
            if (nodeToConnect != node) {
                System.out.println("Connecting nodes: " + node.getName() + " & " + nodeToConnect.getName());
                nodeToConnect.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().connect(node.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getIpAddress(), node.getPrivacyLayer().getBlockchainLayer().getNetworkLayer().getPort());
            }
            nodeToConnect = null;
        }
    }

    public void clickCreateElectionButton(ApplicationLayer node) {
        node.createElectionManagerRole();
        node.createElection(0);
        refresh();
    }

    public void clickPrintLog() {
        for (ApplicationLayer node: nodes) {
            System.out.println("Log for node " + node.getName());
            node.getPrivacyLayer().getBlockchainLayer().printLog();
            System.out.println("------------------------------------------------------");
        }
        System.out.flush();
    }
}
