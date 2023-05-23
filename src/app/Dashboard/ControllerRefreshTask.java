package app.Dashboard;

import app.Main;
import javafx.application.Platform;
import javafx.concurrent.Task;

public class ControllerRefreshTask extends Task {
    private Controller controller;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    @Override
    protected Object call() throws Exception {
        while (!Main.EXITING) {
            ControllerRefreshOnceTask controllerRefreshOnceTask = new ControllerRefreshOnceTask();
            controllerRefreshOnceTask.setController(controller);
            Platform.runLater(controllerRefreshOnceTask);
            Thread.sleep(1000);
        }
        return null;
//        Platform.runLater(() -> controller.refresh());
//        System.out.println("Refresh");
/*        ControllerRefreshOnceTask controllerRefreshOnceTask = new ControllerRefreshOnceTask();
        controllerRefreshOnceTask.setController(controller);
        Thread refreshOnceThread = new Thread(controllerRefreshOnceTask);
        refreshOnceThread.start();
//        controller.refresh();
        Thread.sleep(100);
        if (!Main.EXITING) {
            ControllerRefreshTask controllerRefreshTask = new ControllerRefreshTask();
            controllerRefreshTask.setController(controller);
            Thread newThread = new Thread(controllerRefreshTask);
            newThread.start();
        }
        return null;*/
    }
}
