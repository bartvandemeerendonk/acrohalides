package app.Dashboard;

import app.Client.Layers.ApplicationLayer.ApplicationLayer;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public class Logview {
    public VBox logVBox;
    private ApplicationLayer applicationLayer;

    public void initialize() {
        System.out.println("initialize()");
    }

    public void setApplicationLayer(ApplicationLayer applicationLayer) {
        this.applicationLayer = applicationLayer;
        List<String> log = applicationLayer.getLog();
        for (String logLine: log) {
            Label logLabel = new Label();
            logLabel.setText(logLine);
            logVBox.getChildren().add(logLabel);
        }
    }
}
