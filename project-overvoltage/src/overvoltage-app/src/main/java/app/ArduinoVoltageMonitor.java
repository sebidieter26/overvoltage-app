package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ArduinoVoltageMonitor extends Application {

    private static final double THRESHOLD_VOLTAGE = 4.0;
    private static final int MAX_DATA_POINTS = 100;
    private SerialPort activePort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private XYChart.Series<Number, Number> voltageSeries;
    private Label voltageLabel;
    private Circle statusIndicator;
    private ComboBox<String> portSelector;
    private Button connectButton;
    private int xSeriesData = 0;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Monitor de Tensiune Arduino");

        // Configurare axe pentru grafic
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Timp");
        yAxis.setLabel("Tensiune (V)");
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(5.5);
        yAxis.setTickUnit(0.5);

        // Creare grafic
        LineChart<Number, Number> voltageChart = new LineChart<>(xAxis, yAxis);
        voltageChart.setTitle("Tensiune în timp real");
        voltageChart.setAnimated(false);
        voltageChart.setCreateSymbols(false);
        voltageSeries = new XYChart.Series<>();
        voltageSeries.setName("Tensiune");
        voltageChart.getData().add(voltageSeries);

        // Eticheta pentru afișarea tensiunii curente
        voltageLabel = new Label("0.00 V");
        voltageLabel.setFont(Font.font("Arial", 36));

        // Indicator de stare
        statusIndicator = new Circle(20);
        statusIndicator.setFill(Color.LIGHTGRAY);
        statusIndicator.setStroke(Color.BLACK);

        // Selector porturi
        portSelector = new ComboBox<>();
        updatePortList();

        // Buton de conectare
        connectButton = new Button("Conectare");
        connectButton.setOnAction(e -> toggleConnection());

        // Buton pentru reîmprospătarea listei de porturi
        Button refreshButton = new Button("Reîmprospătare porturi");
        refreshButton.setOnAction(e -> updatePortList());

        // Panel pentru controale
        HBox controlsBox = new HBox(10, portSelector, refreshButton, connectButton);
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPadding(new Insets(10));

        // Panel pentru afișarea tensiunii și a indicatorului
        HBox statusBox = new HBox(20, statusIndicator, voltageLabel);
        statusBox.setAlignment(Pos.CENTER);

        // Panou principal
        VBox topPanel = new VBox(10, controlsBox, statusBox);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topPanel);
        root.setCenter(voltageChart);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Inițializare executor pentru procesarea datelor seriale
        executor = Executors.newSingleThreadExecutor();

        // Curățare resurse la închidere
        primaryStage.setOnCloseRequest(e -> {
            running.set(false);
            if (activePort != null && activePort.isOpen()) {
                activePort.closePort();
            }
            executor.shutdownNow();
        });
    }

    // Actualizează lista de porturi disponibile
    private void updatePortList() {
        portSelector.getItems().clear();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            portSelector.getItems().add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
        }
        if (ports.length > 0) {
            portSelector.getSelectionModel().select(0);
        }
    }

    // Comută conexiunea
    private void toggleConnection() {
        if (running.get()) {
            // Deconectare
            running.set(false);
            if (activePort != null && activePort.isOpen()) {
                activePort.closePort();
                activePort = null;
            }
            connectButton.setText("Conectare");
            statusIndicator.setFill(Color.LIGHTGRAY);
        } else {
            // Conectare
            String selectedPort = portSelector.getValue();
            if (selectedPort != null) {
                String portName = selectedPort.split(" - ")[0];
                connectToPort(portName);
            } else {
                showAlert("Eroare", "Selectați un port serial disponibil.");
            }
        }
    }

    // Conectare la port
    private void connectToPort(String portName) {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            if (port.getSystemPortName().equals(portName)) {
                activePort = port;
                break;
            }
        }

        if (activePort == null) {
            showAlert("Eroare", "Nu s-a putut găsi portul selectat.");
            return;
        }

        // Configurare port serial
        activePort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        activePort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

        if (activePort.openPort()) {
            connectButton.setText("Deconectare");
            running.set(true);

            // Pornire fir de execuție pentru citirea datelor
            executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(activePort.getInputStream()))) {
                    String line;
                    double voltage = 0.0;
                    boolean readingVoltage = false;

                    while (running.get()) {
                        line = reader.readLine();
                        if (line != null) {
                            line = line.trim();
                            if (readingVoltage) {
                                try {
                                    voltage = Double.parseDouble(line);
                                    readingVoltage = false;
                                    updateUI(voltage);
                                } catch (NumberFormatException e) {
                                    // Ignoră liniile care nu pot fi convertite la numere
                                }
                            } else if (line.equals("Tensiunea de pe pin:")) {
                                readingVoltage = true;
                            }
                        }
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        Platform.runLater(() -> showAlert("Eroare", "Eroare la citirea datelor seriale: " + e.getMessage()));
                        running.set(false);
                        Platform.runLater(() -> {
                            connectButton.setText("Conectare");
                            statusIndicator.setFill(Color.LIGHTGRAY);
                        });
                    }
                }
            });
        } else {
            showAlert("Eroare", "Nu s-a putut deschide portul serial.");
        }
    }

    // Actualizare interfață cu noile date
    private void updateUI(double voltage) {
        Platform.runLater(() -> {
            // Actualizare grafic
            voltageSeries.getData().add(new XYChart.Data<>(xSeriesData++, voltage));

            // Limitare număr de puncte afișate
            if (voltageSeries.getData().size() > MAX_DATA_POINTS) {
                voltageSeries.getData().remove(0);
            }

            // Actualizare etichetă tensiune
            voltageLabel.setText(String.format("%.2f V", voltage));

            // Actualizare indicator
            if (voltage > THRESHOLD_VOLTAGE) {
                statusIndicator.setFill(Color.RED);
                // Afișare alertă doar la prima depășire a pragului
                if (voltageSeries.getData().size() <= 1 ||
                        (voltageSeries.getData().size() > 1 &&
                                (double)voltageSeries.getData().get(voltageSeries.getData().size() - 2).getYValue() <= THRESHOLD_VOLTAGE)) {
                    showAlert("Alertă", "Supraalimentare detectată! Tensiunea a depășit pragul de " + THRESHOLD_VOLTAGE + " V.");
                }
            } else {
                statusIndicator.setFill(Color.GREEN);
            }
        });
    }

    // Afișare mesaj de alertă
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}