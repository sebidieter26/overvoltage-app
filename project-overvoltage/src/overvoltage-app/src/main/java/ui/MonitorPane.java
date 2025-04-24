package ui;

import controller.SerialController;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class MonitorPane extends VBox {
    private final Label voltageLabel = new Label("Tensiune: --- V");
    private final ComboBox<String> portComboBox = new ComboBox<>();
    private final Button connectButton = new Button("Conectează");
    private final SerialController controller = new SerialController();
    private boolean connected = false;

    public MonitorPane() {
        setSpacing(10);
        setStyle("-fx-padding: 20; -fx-font-size: 16;");

        portComboBox.getItems().addAll(controller.getAvailablePorts());

        connectButton.setOnAction(e -> {
            if (!connected) {
                String selectedPort = portComboBox.getValue();
                if (selectedPort != null) {
                    controller.connectToPort(selectedPort, this::handleSerialData);
                    connectButton.setText("Deconectează");
                    connected = true;
                }
            } else {
                controller.disconnect();
                connectButton.setText("Conectează");
                connected = false;
            }
        });

        getChildren().addAll(portComboBox, connectButton, voltageLabel);
    }
    private void handleSerialData(String line) {
        Platform.runLater(() -> {
            voltageLabel.setText("Tensiune: " + line + " V");
        });
    }

    private void updateVoltage(String voltage) {
        Platform.runLater(() -> voltageLabel.setText("Tensiune: " + voltage + " V"));
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
