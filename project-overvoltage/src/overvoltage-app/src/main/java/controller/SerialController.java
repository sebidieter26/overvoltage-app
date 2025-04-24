package controller;

import com.fazecast.jSerialComm.SerialPort;
import java.util.Arrays;

import java.util.Scanner;
import java.util.function.Consumer;

public class SerialController {
    private SerialPort serialPort;

    public String[] getAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName)
                .toArray(String[]::new);
    }

    public void connectToPort(String portName, Consumer<String> onDataReceived) {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }

        serialPort = SerialPort.getCommPort(portName);
        serialPort.setBaudRate(9600);

        if (serialPort.openPort()) {
            Thread thread = new Thread(() -> {
                Scanner scanner = new Scanner(serialPort.getInputStream());
                while (serialPort.isOpen()) {
                    if (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        onDataReceived.accept(line); // notifici UI-ul
                    }
                }
                scanner.close();
            });

            thread.setDaemon(true);
            thread.start();
        }
    }

    public void disconnect() {
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }
    }
}
