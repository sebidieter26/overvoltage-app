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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class OvervoltageApp extends Application {

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
    private TextArea logArea;
    private TextField baudRateField;
    private int xSeriesData = 0;
    private boolean receiveRawData = false;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Aplicatie Supraalimentare");

        // grafic
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Timp");
        yAxis.setLabel("Tensiune (V)");
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(5.5);
        yAxis.setTickUnit(0.5);
        LineChart<Number, Number> voltageChart = new LineChart<>(xAxis, yAxis);
        voltageChart.setTitle("Tensiune în timp real");
        voltageChart.setAnimated(false);
        voltageChart.setCreateSymbols(false);
        voltageSeries = new XYChart.Series<>();
        voltageSeries.setName("Tensiune");
        voltageChart.getData().add(voltageSeries);

        voltageLabel = new Label("0.00 V");
        voltageLabel.setFont(Font.font("Arial", 36));

        // stare
        statusIndicator = new Circle(20);
        statusIndicator.setFill(Color.LIGHTGRAY);
        statusIndicator.setStroke(Color.BLACK);

        portSelector = new ComboBox<>();
        updatePortList();

        baudRateField = new TextField("9600");
        baudRateField.setPrefWidth(80);

        connectButton = new Button("Conectare");
        connectButton.setOnAction(e -> toggleConnection());

        Button refreshButton = new Button("Refresh porturi");
        refreshButton.setOnAction(e -> updatePortList());

        Button testButton = new Button("Test Comunicare");
        testButton.setOnAction(e -> sendTestCommand());

        Button toggleModeButton = new Button("Comutare mod date brute");
        toggleModeButton.setOnAction(e -> {
            receiveRawData = !receiveRawData;
            log("Mod de citire setat la: " + (receiveRawData ? "Date brute" : "Format Arduino standard"));
        });

        //log
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);

        GridPane settingsGrid = new GridPane();
        settingsGrid.setHgap(5);
        settingsGrid.setVgap(5);
        settingsGrid.addRow(0, new Label("Rata Baud:"), baudRateField);

        HBox controlsBox = new HBox(10, portSelector, refreshButton, settingsGrid, connectButton);
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPadding(new Insets(10));

        //butoane
        HBox extraControlsBox = new HBox(10, testButton, toggleModeButton);
        extraControlsBox.setAlignment(Pos.CENTER);
        extraControlsBox.setPadding(new Insets(5));

        HBox statusBox = new HBox(20, statusIndicator, voltageLabel);
        statusBox.setAlignment(Pos.CENTER);

        //panou
        VBox topPanel = new VBox(10, controlsBox, extraControlsBox, statusBox);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setPadding(new Insets(10));

        //log
        VBox.setVgrow(logArea, Priority.ALWAYS);
        VBox bottomPanel = new VBox(5, new Label("Log conexiune:"), logArea);
        bottomPanel.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topPanel);
        root.setCenter(voltageChart);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        executor = Executors.newSingleThreadExecutor();

        //inchide toate resursele
        primaryStage.setOnCloseRequest(e -> {
            cleanupResources();
        });

        log("Aplicatie pornita. Selectati un port si apasati \"Conectare\".");
    }

    private void cleanupResources() {
        running.set(false);
        if (activePort != null) {
            if (activePort.isOpen()) {
                activePort.closePort();
                log("Port inchis la inchiderea aplicatiei: " + activePort.getSystemPortName());
            }
            activePort = null;
        }
        executor.shutdownNow();
    }

    //test
    private void sendTestCommand() {
        if (activePort != null && activePort.isOpen()) {
            try {
                log("Test...");
                OutputStream out = activePort.getOutputStream();
                out.write('T');  // Trimitem litera T ca test
                out.flush();
                log("Test trimis! Astept raspuns...");
            } catch (IOException e) {
                log("EROARE TEST: " + e.getMessage());
            }
        } else {
            log("Nu se poate testa, portul nu este deschis!");
            showAlert("Eroare", "Conectati-va la un port disponibil.");
        }
    }

    //lista porturi
    private void updatePortList() {
        String selectedPort = portSelector.getValue();
        portSelector.getItems().clear();

        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            log("Nu s-au gasit porturi seriale disponibile!");
        } else {
            log("Porturi disponibile: " + ports.length);
            for (SerialPort port : ports) {
                String portInfo = port.getSystemPortName() + " - " + port.getDescriptivePortName();
                portSelector.getItems().add(portInfo);
                log("Port gasit: " + portInfo);
            }

            if (selectedPort != null && !selectedPort.isEmpty() &&
                    portSelector.getItems().contains(selectedPort)) {
                portSelector.setValue(selectedPort);
            } else if (ports.length > 0) {
                portSelector.getSelectionModel().select(0);
            }
        }
    }

    private void toggleConnection() {
        if (running.get()) {
            disconnectPort();
        } else {
            String selectedPort = portSelector.getValue();
            if (selectedPort != null) {
                String portName = selectedPort.split(" - ")[0];
                connectToPort(portName);
            } else {
                log("EROARE: Selectati un port serial dispoibil!");
                showAlert("Eroare", "Selectati un port serial dispoibil!");
            }
        }
    }

    //deconectare port
    private void disconnectPort() {
        running.set(false);

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (activePort != null) {
            if (activePort.isOpen()) {
                boolean closed = activePort.closePort();
                if (closed) {
                    log("Port inchis cu succes: " + activePort.getSystemPortName());
                } else {
                    log("EROARE la inchiderea portului: " + activePort.getSystemPortName());

                    log("Inchidere fortata...");
                    activePort = null;
                    System.gc();
                }
            }
            activePort = null;
        }
        connectButton.setText("Conectare");
        statusIndicator.setFill(Color.LIGHTGRAY);
    }

    //conectare la port
    private void connectToPort(String portName) {
        log("Conectare la port: " + portName);

        //verif status
        SerialPort[] portsBeforeOpen = SerialPort.getCommPorts();
        boolean portExists = false;
        for (SerialPort port : portsBeforeOpen) {
            if (port.getSystemPortName().equals(portName)) {
                portExists = true;
                break;
            }
        }

        if (!portExists) {
            log("EROARE: Portul " + portName + " nu a fost gasit in lista");
            showAlert("Eroare", "Portul " + portName + " nu este disponibil");
            return;
        }

        SerialPort[] allPorts = SerialPort.getCommPorts();
        for (SerialPort port : allPorts) {
            if (port.getSystemPortName().equals(portName)) {
                if (port.isOpen()) {
                    log("Portul " + portName + " este deja deschis. Inchidere...");
                    port.closePort();

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                break;
            }
        }

        //refresh porturi
        SerialPort[] refreshedPorts = SerialPort.getCommPorts();
        activePort = null;
        for (SerialPort port : refreshedPorts) {
            if (port.getSystemPortName().equals(portName)) {
                activePort = port;
                break;
            }
        }

        if (activePort == null) {
            log("EROARE: Nu s-a putut obtine referinta la portul " + portName + " dupa actualizare");
            showAlert("Eroare", "Nu s-a putut gasi portul selectat: " + portName);
            return;
        }

        //baud rate
        int baudRate;
        try {
            baudRate = Integer.parseInt(baudRateField.getText().trim());
        } catch (NumberFormatException e) {
            log("Rata baud incorecta. Foloseste 9600");
            baudRate = 9600;
            baudRateField.setText("9600");
        }

        log("Configurare port cu rata baud: " + baudRate);
        activePort.setComPortParameters(baudRate, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        activePort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 3000, 0);

        log("Deschidere port " + portName + "...");
        if (activePort.openPort()) {
            log("Port deschis cu succes: " + portName);
            connectButton.setText("Deconectare");
            statusIndicator.setFill(Color.GRAY);
            running.set(true);

        //citire date
            executor.submit(() -> {
                try {

                    Thread.sleep(2000);
                    log("Citire date de pe portul serial...");

                    InputStream in = activePort.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));

                    String line;
                    double voltage = 0.0;
                    boolean readingVoltage = false;
                    byte[] buffer = new byte[1024];

                    while (running.get()) {
                        try {
                            if (receiveRawData) {
                                //debug
                                if (in.available() > 0) {
                                    int numBytes = in.read(buffer);
                                    if (numBytes > 0) {
                                        String data = new String(buffer, 0, numBytes);
                                        log("Date brute: " + data);


                                        try {

                                            String[] tokens = data.split("[^0-9.]");
                                            for (String token : tokens) {
                                                if (token.matches("\\d*\\.?\\d+")) {
                                                    voltage = Double.parseDouble(token);
                                                    if (voltage >= 0 && voltage <= 5.5) {
                                                        log("Posibila tensiune gasita: " + voltage + "V");
                                                        updateUI(voltage);
                                                        break;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            log("Nu s-au putut extrage date din șirul brut: " + e.getMessage());
                                        }
                                    }
                                } else {
                                    Thread.sleep(100);
                                }
                            } else {

                                if (reader.ready() || activePort.bytesAvailable() > 0) {
                                    line = reader.readLine();
                                    if (line != null) {
                                        line = line.trim();
                                        log("Date primite: " + line);

                                        if (readingVoltage) {
                                            try {
                                                voltage = Double.parseDouble(line);
                                                readingVoltage = false;
                                                log("Tensiune citita: " + voltage + "V");
                                                updateUI(voltage);
                                            } catch (NumberFormatException e) {
                                                log("Nu s-a putut converti la numar: " + line);
                                            }
                                        } else if (line.contains("Tensiunea de pe pin:")) {
                                            readingVoltage = true;
                                        } else {
                                            // Încercăm să extragem direct o valoare numerică
                                            try {
                                                if (line.matches("\\d*\\.?\\d+")) {
                                                    voltage = Double.parseDouble(line);
                                                    if (voltage >= 0 && voltage <= 5.5) {
                                                        log("Valoare numerica detectata direct: " + voltage + "V");
                                                        updateUI(voltage);
                                                    }
                                                }
                                            } catch (NumberFormatException e) {

                                            }
                                        }
                                    }
                                } else {

                                    Thread.sleep(100);
                                }
                            }
                        } catch (IOException e) {
                            if (running.get()) {
                                log("EROARE la citirea datelor: " + e.getMessage());
                                Platform.runLater(() -> {
                                    showAlert("Eroare", "Eroare la citirea datelor seriale: " + e.getMessage());
                                });
                                break;
                            }
                        } catch (InterruptedException e) {
                            log("Fir de execuție întrerupt");
                            break;
                        }
                    }
                } catch (Exception e) {
                    log("EROARE generala: " + e.getClass().getName() + " - " + e.getMessage());
                    if (running.get()) {
                        Platform.runLater(() -> {
                            showAlert("Eroare", "Eroare la citirea datelor seriale: " + e.getMessage());
                        });
                    }
                } finally {
                    running.set(false);
                    if (activePort != null && activePort.isOpen()) {
                        activePort.closePort();
                        log("Port inchis în finally block: " + activePort.getSystemPortName());
                    }
                    Platform.runLater(() -> {
                        connectButton.setText("Conectare");
                        statusIndicator.setFill(Color.LIGHTGRAY);
                    });
                }
            });
        } else {
            int errorCode = activePort.getLastErrorCode();
            String errorLocation = String.valueOf(activePort.getLastErrorLocation());
            log("EROARE: Nu s-a putut deschide portul " + portName +
                    " (Cod eroare: " + errorCode + ", Location: " + errorLocation + ")");

            // Verificări suplimentare pentru cauze comune
            String errorMessage = "Nu s-a putut deschide portul " + portName;
            if (errorCode == 5) {
                errorMessage += ". Portul este deja folosit de alt program.";
            } else if (errorCode == 13) {
                errorMessage += ". Nu aveti permisiune(incercati run as administrator).";
            }

            showAlert("Eroare", errorMessage);

            activePort = null;
            System.gc();
        }
    }

    //actualizare interfata
    private void updateUI(double voltage) {
        Platform.runLater(() -> {
            voltageSeries.getData().add(new XYChart.Data<>(xSeriesData++, voltage));

            if (voltageSeries.getData().size() > MAX_DATA_POINTS) {
                voltageSeries.getData().remove(0);
            }

            voltageLabel.setText(String.format("%.2f V", voltage));

            if (voltage > THRESHOLD_VOLTAGE) {
                statusIndicator.setFill(Color.RED);

                if (voltageSeries.getData().size() <= 1 ||
                        (voltageSeries.getData().size() > 1 &&
                                (double)voltageSeries.getData().get(voltageSeries.getData().size() - 2).getYValue() <= THRESHOLD_VOLTAGE)) {
                    showAlert("ALERTA", "Supraalimentare! Tensiunea a depasit pragul de " + THRESHOLD_VOLTAGE + " V.");
                }
            } else {
                statusIndicator.setFill(Color.GREEN);
            }
        });
    }

    //afisare alerta
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();
    }

    //log
    private void log(String message) {
        Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            // Auto-scroll la ultima linie
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}