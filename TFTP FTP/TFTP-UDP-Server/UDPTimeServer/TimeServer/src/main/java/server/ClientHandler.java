package server;

import java.io.*;
import java.net.*;
import java.util.Scanner;

class ClientHandler extends Thread {
    private DatagramSocket serverSocket;
    private DatagramPacket receivedPacket;
    private byte[] buffer;

    private static final int BUFFER_SIZE = 512;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;

    public ClientHandler(DatagramSocket serverSocket, DatagramPacket receivedPacket, byte[] buffer) {
        this.serverSocket = serverSocket;
        this.receivedPacket = receivedPacket;
        this.buffer = buffer;
    }

    @Override
    public void run() {
        try {
            if (receivedPacket.getData()[1] == OP_RRQ) {
                byte[] packetData = receivedPacket.getData();
                int fileNameEndPos = 2;
                for (; fileNameEndPos < receivedPacket.getLength(); fileNameEndPos++) {
                    if (packetData[fileNameEndPos] == 0) {
                        break;
                    }
                }
                String fileName = new String(packetData, 2, fileNameEndPos - 2);
                sendFile(serverSocket, receivedPacket.getAddress(), receivedPacket.getPort(), fileName);
            } else if (receivedPacket.getData()[1] == OP_WRQ) {
                byte[] packetData = receivedPacket.getData();
                int fileNameEndPos = 2;
                for (; fileNameEndPos < receivedPacket.getLength(); fileNameEndPos++) {
                    if (packetData[fileNameEndPos] == 0) {
                        break;
                    }
                }
                String fileName = new String(packetData, 2, fileNameEndPos - 2);
                sendInitialAck(serverSocket, receivedPacket.getAddress(), receivedPacket.getPort());
                receiveFile(serverSocket, fileName);
            } else {
                System.out.println("Invalid opcode received: " + receivedPacket.getData()[1]);
            }
        } catch (IOException e) {
            System.err.println("Error while handling the client request: " + e.getMessage());
        }
    }

    /**
     * Sends a file to the client using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param clientAddress The client's InetAddress.
     * @param clientPort The client's port number.
     * @param fileName The name of the file to send.
     * @throws IOException If an I/O error occurs.
     */

    private static void sendFile(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String fileName) throws IOException {
        String sendingFilesDirectory = "Sending Files"; // or "Retrieve Files"
        String filePath = System.getProperty("user.dir") + File.separator + sendingFilesDirectory + File.separator + fileName;
        File file = new File(filePath);
        //  System.out.println("File path: " + filePath);

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(filePath)) {
                short blockNumber = 1;
                int bytesRead;
                byte[] dataBuffer = new byte[BUFFER_SIZE];

                while ((bytesRead = fis.read(dataBuffer)) != -1) {
                    sendData(serverSocket, clientAddress, clientPort, blockNumber, dataBuffer, bytesRead);
                    receiveAcknowledgments(serverSocket, blockNumber);

                    blockNumber++;
                }
                System.out.println("File transfer completed for " + fileName);

            } catch (IOException e) {
                System.out.println("Error reading from file: " + e.getMessage());
            }
        } else {
            System.out.println("File does not exist: " + fileName);
            short errorCode = 1; // File not found error
            String errorMessage = "File not found: " + fileName;
            sendError(serverSocket, clientAddress, clientPort, errorCode, errorMessage);
        }
    }
    /**
     * Sends an error message to the client using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param clientAddress The client's InetAddress.
     * @param clientPort The client's port number.
     * @param errorCode The error code corresponding to the error message.
     * @param errorMessage The error message to send to the client.
     * @throws IOException If an I/O error occurs.
     */
    private static void sendError(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, short errorCode, String errorMessage) throws IOException {
        byte[] errorData = errorMessage.getBytes();
        byte[] errorPacketData = new byte[4 + errorData.length + 1];
        errorPacketData[0] = 0;
        errorPacketData[1] = OP_ERROR;
        errorPacketData[2] = (byte) (errorCode >> 8);
        errorPacketData[3] = (byte) (errorCode);
        System.arraycopy(errorData, 0, errorPacketData, 4, errorData.length);
        errorPacketData[errorPacketData.length - 1] = 0;

        DatagramPacket errorPacket = new DatagramPacket(errorPacketData, errorPacketData.length, clientAddress, clientPort);
        serverSocket.send(errorPacket);
    }


//    private static void receiveFile(DatagramSocket serverSocket, String fileName) throws IOException {
//        String receivingFilesDirectory = "Receiving Files"; // or "Upload Files"
//        String userDir = System.getProperty("user.dir");
//        String dirPath = receivingFilesDirectory;
//
//        File file = new File(System.getProperty("user.dir") + File.separator + dirPath + File.separator + fileName);
//
//        System.out.println("Received file name: " + fileName);
//
//        System.out.println("User dir: " + userDir);
//        System.out.println("Receiving Files directory: " + receivingFilesDirectory);
//        System.out.println("Directory path: " + dirPath);
//
//        System.out.println("File path: " + file.getAbsolutePath());
//
//        System.out.println("File path: " + dirPath + File.separator + fileName);
//
//        File directory = new File(System.getProperty("user.dir") + File.separator + dirPath);
//        if (!directory.exists()) {
//            directory.mkdir();
//        }
//
//        System.out.println("Attempting to create FileOutputStream for file: " + file.getAbsolutePath());
//
//        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//             FileOutputStream fos = new FileOutputStream("\"" + file.getAbsolutePath() + "\"")) {
//
//            System.out.println("Created FileOutputStream for file: " + file.getAbsolutePath());
//            short blockNumber = 1;
//            boolean done = false;
//
//            while (!done) {
//                byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
//                DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
//                serverSocket.receive(dataPacket);
//
//                short receivedBlockNumber = (short) (((dataPacket.getData()[2] & 0xFF) << 8) | (dataPacket.getData()[3] & 0xFF));
//                if (receivedBlockNumber == blockNumber) {
//                    int dataSize = dataPacket.getLength() - 4;
//                    byteArrayOutputStream.write(dataBuffer, 4, dataSize);
//                    sendAck(serverSocket, dataPacket.getAddress(), dataPacket.getPort(), blockNumber);
//                    blockNumber++;
//
//                    if (dataSize < BUFFER_SIZE) {
//                        done = true;
//                    }
//                }
//            }
//            fos.write(byteArrayOutputStream.toByteArray());
//            System.out.println("File received: " + fileName);
//        } catch (IOException e) {
//            System.out.println("Error writing to file: " + e.getMessage());
//        }
//    }


    /**
     * Receives a file from the client using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param fileName The name of the file to receive.
     * @throws IOException If an I/O error occurs.
     */
    private static void receiveFile(DatagramSocket serverSocket, String fileName) throws IOException {
        String receivingFilesDirectory = "Receiving Files"; // or "Upload Files"
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             FileOutputStream fos = new FileOutputStream(System.getProperty("user.dir") + File.separator + receivingFilesDirectory + File.separator + fileName)) {
            short blockNumber = 1;
            boolean done = false;

            while (!done) {
                byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
                DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                serverSocket.receive(dataPacket);

                if (dataPacket.getData()[1] == OP_DATA) {
                    short receivedBlockNumber = (short) (((dataPacket.getData()[2] & 0xFF) << 8) | (dataPacket.getData()[3] & 0xFF));
                    if (receivedBlockNumber == blockNumber) {
                        int dataSize = dataPacket.getLength() - 4;
                        byteArrayOutputStream.write(dataBuffer, 4, dataSize);
                        sendAcknowledgments(serverSocket, dataPacket.getAddress(), dataPacket.getPort(), blockNumber);
                        blockNumber++;

                        if (dataSize < BUFFER_SIZE) {
                            done = true;
                        }
                    }
                } else {
                    System.out.println("Invalid opcode received: " + dataPacket.getData()[1]);
                    break;
                }
            }
            fos.write(byteArrayOutputStream.toByteArray());
            System.out.println("File received: " + fileName);
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    /**
     * Sends data to the client using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param clientAddress The client's InetAddress.
     * @param clientPort The client's port number.
     * @param blockNumber The current block number.
     * @param data The data to send.
     * @param dataLength The length of the data to send.
     * @throws IOException If an I/O error occurs.
     */
    private static void sendData(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, short blockNumber, byte[] data, int dataLength) throws IOException {
        byte[] sendData = new byte[dataLength + 4];
        sendData[0] = 0;
        sendData[1] = OP_DATA;
        sendData[2] = (byte) (blockNumber >> 8);
        sendData[3] = (byte) (blockNumber);
        System.arraycopy(data, 0, sendData, 4, dataLength);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, clientAddress, clientPort);
        serverSocket.send(sendPacket);
    }
    /**
     * Receives acknowledgments from the client using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param blockNumber The expected block number.
     * @throws IOException If an I/O error occurs or an invalid ACK is received.
     */

    private static void receiveAcknowledgments(DatagramSocket serverSocket, short blockNumber) throws IOException {
        byte[] ackBuffer = new byte[4];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        serverSocket.receive(ackPacket);

        if (ackPacket.getData()[1] != OP_ACK || (((ackPacket.getData()[2] & 0xFF) << 8) | (ackPacket.getData()[3] & 0xFF)) != blockNumber) {
            throw new IOException("Invalid ACK received");
        }
    }


    /**
     * Sends acknowledgments to the client using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param clientAddress The client's InetAddress.
     * @param clientPort The client's port number.
     * @param blockNumber The block number being acknowledged.
     * @throws IOException If an I/O error occurs.
     */

    private static void sendAcknowledgments(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, short blockNumber) throws IOException {
        byte[] ackData = new byte[4];
        ackData[0] = 0;
        ackData[1] = OP_ACK;
        ackData[2] = (byte) (blockNumber >> 8);
        ackData[3] = (byte) (blockNumber);

        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
        serverSocket.send(ackPacket);
    }

    /**
     * Sends the initial ACK for a write request (WRQ) using TFTP protocol.
     *
     * @param serverSocket The server socket used for communication.
     * @param clientAddress The client's InetAddress.
     * @param clientPort The client's port number.
     * @throws IOException If an I/O error occurs.
     */

    private static void sendInitialAck(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort) throws IOException {
        byte[] ackData = new byte[4];
        ackData[0] = 0;
        ackData[1] = OP_ACK;
        ackData[2] = 0;
        ackData[3] = 0;

        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
        serverSocket.send(ackPacket);
    }
}
