package tcpclient;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {
    // Operation codes for read and write requests
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final int PACKET_SIZE = 512; // The packet size for the data buffer
    private static final int DEFAULT_PORT = 2000; // Default port number

    private Socket clientSocket; // Socket for client-server communication
    private InetAddress serverAddress; // Server IP address
    private int serverPort; // Server port number

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        // Get server IP and port number from user input
        System.out.println("Enter server IP:");
        String serverIP = scanner.next();
        System.out.println("Enter server port:");
        int serverPort = scanner.nextInt();

        InetAddress serverAddress = InetAddress.getByName(serverIP);
        TCPClient tftpClient = new TCPClient(serverAddress, serverPort);

        // Display menu and get command from user input
        System.out.println("Press 1: Send RRQ request");
        System.out.println("Press 2: Send WRQ request");
        System.out.println("Enter Your Command:");

        int command = scanner.nextInt();

        // Execute the appropriate command based on user input
        if (command == 1) {
            System.out.println("Enter file name:");
            String fileName = scanner.next();
            tftpClient.readFile(fileName);
        } else if (command == 2) {
            System.out.println("Enter file name:");
            String fileName = scanner.next();
            tftpClient.writeFile(fileName);
        } else {
            System.out.println("Invalid command. Press 1 for RRQ or 2 for WRQ.");
        }

        scanner.close();
    }

    // Constructor to initialize server address and port number
    public TCPClient(InetAddress serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    // Method to read file from the server
    public void readFile(String fileName) throws IOException {
        connect();

        OutputStream outputStream = clientSocket.getOutputStream();
        InputStream inputStream = clientSocket.getInputStream();

        // Send a read request to the server
        sendReadRequest(outputStream, fileName);
        // Receive and save the file
        receiveFile(inputStream, fileName);

        clientSocket.close();
    }

    // Method to write a file to the server
    public void writeFile(String fileName) throws IOException {
        connect();

        OutputStream outputStream = clientSocket.getOutputStream();
        InputStream inputStream = clientSocket.getInputStream();

        // Send a write request to the server
        sendWriteRequest(outputStream, fileName);
        // Send the file to the server
        sendFile(outputStream, fileName);

        clientSocket.close();
    }

    // Method to establish a connection with the server
    private void connect() throws IOException {
        clientSocket = new Socket(serverAddress, serverPort);
    }

    // Method to create and send a read request to the server
    private void sendReadRequest(OutputStream outputStream, String fileName) throws IOException {
        byte[] request = createRequest(OP_RRQ, fileName);
        outputStream.write(request);
    }

    // Method to create and send a write request to the server
    private void sendWriteRequest(OutputStream outputStream, String fileName) throws IOException {
        byte[] request = createRequest(OP_WRQ, fileName);
        outputStream.write(request);
    }

    // Method to create a request packet with the given opcode and filename
    private byte[] createRequest(byte opCode, String fileName) {
        byte[] fileNameBytes = fileName.getBytes();
        byte[] request = new byte[2 + fileNameBytes.length + 1 + 1];

        request[0] = 0;
        request[1] = opCode;
        System.arraycopy(fileNameBytes, 0, request, 2, fileNameBytes.length);
        request[request.length - 2] = 0;
        request[request.length - 1] = 0;

        return request;
    }

    // Method to receive a file from the server and save it locally
    private void receiveFile(InputStream inputStream, String fileName) throws IOException {

        File file = new File(fileName);
        FileOutputStream fileOutput = new FileOutputStream(file);
        byte[] dataBuffer = new byte[PACKET_SIZE];
        int bytesRead;

        // Read the incoming data and save it to the file
        while ((bytesRead = inputStream.read(dataBuffer)) != -1) {
            fileOutput.write(dataBuffer, 0, bytesRead);
        }

        fileOutput.close();
    }

    private void sendFile(OutputStream outputStream, String fileName) throws IOException {
        File file = new File(fileName);
        FileInputStream fileInput = new FileInputStream(file);
        byte[] dataBuffer = new byte[PACKET_SIZE];
        int bytesRead;

        while ((bytesRead = fileInput.read(dataBuffer)) != -1) {
            outputStream.write(dataBuffer, 0, bytesRead);
        }

        fileInput.close();
    }
}