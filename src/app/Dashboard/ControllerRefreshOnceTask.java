package app.Dashboard;

import javafx.concurrent.Task;

public class ControllerRefreshOnceTask extends Task {
    private Controller controller;

    public void setController(Controller controller) {
        this.controller = controller;
    }

    @Override
    protected Object call() throws Exception {
//        System.out.println("Start refresh");
                controller.refresh();
//        System.out.println("End refresh");
        return null;
    }
}
