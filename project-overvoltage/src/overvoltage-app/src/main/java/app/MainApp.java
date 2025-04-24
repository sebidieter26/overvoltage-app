package app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ui.MonitorPane;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        MonitorPane root = new MonitorPane();
        Scene scene = new Scene(root, 300, 150);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Monitor Tensiune Arduino");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
