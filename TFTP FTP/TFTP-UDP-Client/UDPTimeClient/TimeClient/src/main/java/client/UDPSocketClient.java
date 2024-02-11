package client;


import java.io.*;
import java.net.*;
import java.util.Scanner;


public class UDPSocketClient {
    private static final int BUFFER_SIZE = 512;
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATA = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;

    // Main method to run the client
    public static void main(String[] args) throws IOException {
        // Request server IP address and port number from the user
        System.out.print("Enter the server IP address: ");
        try (Scanner scanner = new Scanner(System.in)) {
            InetAddress serverAddress = InetAddress.getByName(scanner.nextLine());
            System.out.print("Enter the server port number: ");
            int serverPort = scanner.nextInt();
            scanner.nextLine();

            System.out.print("Enter the operation (press 1 to store file (WRITE) or press 2 to retrieve file(READ)): ");
            String operation = scanner.nextLine().toUpperCase();

            System.out.print("Enter the file name: ");
            String fileName = scanner.nextLine();
       //     System.out.println("Client working directory: " + System.getProperty("user.dir"));
            try (DatagramSocket clientSocket = new DatagramSocket()) {
                if (operation.equals("1")) {
                    sendWriteRequest(clientSocket, serverAddress, serverPort, fileName);
                    receiveInAcknowledgments(clientSocket);
                    sendFile(clientSocket, serverAddress, serverPort, fileName);
                } else if (operation.equals("2")) {
                    sendReadRequest(clientSocket, serverAddress, serverPort, fileName);
                    receiveFile(clientSocket, fileName);
                } else {
                    System.out.println("Invalid operation.");
                    return;
                }
            }
        }
    }

    // Sends a file to the server
    private static void sendFile(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, String fileName) throws IOException {
        String sendingFilesDirectory = "Sending Files"; // or "Upload Files"
        String filePath = System.getProperty("user.dir") + File.separator + sendingFilesDirectory + File.separator + fileName;
        File file = new File(filePath);
//        System.out.println("file path" + filePath);

        if (file.exists()) {
            System.out.println("File exists: " + fileName);
//            System.out.println("1File name to send: " + fileName);
            try (FileInputStream fis = new FileInputStream(filePath)) {
                short blockNumber = 1;
                int bytesRead;
                byte[] dataBuffer = new byte[BUFFER_SIZE];

//                System.out.println("2File name to send: " + fileName);


                while ((bytesRead = fis.read(dataBuffer)) != -1) {
                    sendData(clientSocket, serverAddress, serverPort, blockNumber, dataBuffer, bytesRead);
                    receiveAcknowledgments(clientSocket, blockNumber);

                    blockNumber++;
                  //  System.out.println("File name to send: " + fileName);

                }
                System.out.println("File transfer completed for " + fileName);

            } catch (IOException e) {
                System.out.println("Error reading from file: " + e.getMessage());
            }
        } else {
            System.out.println("File does not exist: " + fileName);
        }
    }
    // Receives a file from the server
    private static void receiveFile(DatagramSocket clientSocket, String fileName) throws IOException {
        String receivingFilesDirectory = "Receiving Files"; // or "Retrieve Files"
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             FileOutputStream fos = new FileOutputStream(System.getProperty("user.dir") + File.separator + receivingFilesDirectory + File.separator + fileName)) {
            short blockNumber = 1;
            boolean done = false;

            while (!done) {
                byte[] dataBuffer = new byte[BUFFER_SIZE + 4];
                DatagramPacket dataPacket = new DatagramPacket(dataBuffer, dataBuffer.length);
                clientSocket.receive(dataPacket);

                short receivedOpcode = (short) (((dataPacket.getData()[0] & 0xFF) << 8) | (dataPacket.getData()[1] & 0xFF));

                if (receivedOpcode == OP_DATA) {
                    short receivedBlockNumber = (short) (((dataPacket.getData()[2] & 0xFF) << 8) | (dataPacket.getData()[3] & 0xFF));
                    if (receivedBlockNumber == blockNumber) {
                        int dataSize = dataPacket.getLength() - 4;
                        byteArrayOutputStream.write(dataBuffer, 4, dataSize);
                        sendAcknowledgments(clientSocket, dataPacket.getAddress(), dataPacket.getPort(), blockNumber);
                        blockNumber++;

                        if (dataSize < BUFFER_SIZE) {
                            done = true;
                        }
                    }
                } else if (receivedOpcode == OP_ERROR) {
                    short errorCode = (short) (((dataPacket.getData()[2] & 0xFF) << 8) | (dataPacket.getData()[3] & 0xFF));
                    String errorMessage = new String(dataBuffer, 4, dataPacket.getLength() - 5);
                    System.out.println("Error " + errorCode + ": " + errorMessage);
                    break; // Stop receiving
                }
            }
            fos.write(byteArrayOutputStream.toByteArray());
            if (done) {
                System.out.println("File received: " + fileName);
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
        }
    }

    // Sends a Read Request (RRQ) to the server
    private static void sendReadRequest(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, String fileName) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        dataOutputStream.writeShort(OP_RRQ); // Write the RRQ opcode (1)
        dataOutputStream.writeBytes(fileName); // Write the file name
        dataOutputStream.writeByte(0); // Write a null byte to separate the filename and mode
        dataOutputStream.writeBytes("octet"); // Write the transfer mode (octet)
        dataOutputStream.writeByte(0); // Write a null byte to terminate the mode

        byte[] requestPacketData = byteArrayOutputStream.toByteArray();
        DatagramPacket requestPacket = new DatagramPacket(requestPacketData, requestPacketData.length, serverAddress, serverPort);
        clientSocket.send(requestPacket);
    }

    // Sends a Write Request (WRQ) to the server
    private static void sendWriteRequest(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, String fileName) throws IOException {
        byte[] wrqData = createRrqWrqData(OP_WRQ, fileName);
        DatagramPacket wrqPacket = new DatagramPacket(wrqData, wrqData.length, serverAddress, serverPort);
        clientSocket.send(wrqPacket);
    }
    // Creates the data for a Read Request (RRQ) or Write Request (WRQ)
    private static byte[] createRrqWrqData(byte opcode, String fileName) {
        byte[] fileNameBytes = fileName.getBytes();
        byte[] data = new byte[2 + fileNameBytes.length + 1 + 4 + 1];

        data[0] = 0;
        data[1] = opcode;
        System.arraycopy(fileNameBytes, 0, data, 2, fileNameBytes.length);
        data[2 + fileNameBytes.length] = 0;
        System.arraycopy("octet".getBytes(), 0, data, 3 + fileNameBytes.length, 5);
        data[data.length - 1] = 0;

        return data;
    }
    // Sends a DATA packet to the server
    private static void sendData(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, short blockNumber, byte[] data, int dataLength) throws IOException {
        byte[] sendData = new byte[dataLength + 4];
        sendData[0] = 0;
        sendData[1] = OP_DATA;
        sendData[2] = (byte) (blockNumber >> 8);
        sendData[3] = (byte) (blockNumber);
        System.arraycopy(data, 0, sendData, 4, dataLength);

        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
        clientSocket.send(sendPacket);
    }
    // Receives an ACK packet from the server
    private static void receiveAcknowledgments(DatagramSocket clientSocket, short blockNumber) throws IOException {
        byte[] ackBuffer = new byte[4];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        clientSocket.receive(ackPacket);

        short receivedOpcode = (short) (((ackPacket.getData()[0] & 0xFF) << 8) | (ackPacket.getData()[1] & 0xFF));

        if (receivedOpcode == OP_ACK) {
            if ((((ackPacket.getData()[2] & 0xFF) << 8) | (ackPacket.getData()[3] & 0xFF)) != blockNumber) {
                throw new IOException("Invalid ACK received");
            }
        } else if (receivedOpcode == OP_ERROR) {
            short errorCode = (short) (((ackPacket.getData()[2] & 0xFF) << 8) | (ackPacket.getData()[3] & 0xFF));
            String errorMessage = new String(ackBuffer, 4, ackPacket.getLength() - 5);
            System.out.println("Error " + errorCode + ": " + errorMessage);
            throw new IOException("Error from server: " + errorMessage);
        }
    }


    // Sends an ACK packet to the server
    private static void sendAcknowledgments(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, short blockNumber) throws IOException {
        byte[] ackData = new byte[4];
        ackData[0] = 0;
        ackData[1] = OP_ACK;
        ackData[2] = (byte) (blockNumber >> 8);
        ackData[3] = (byte) (blockNumber);

        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, serverAddress, serverPort);
        clientSocket.send(ackPacket);
    }

    // Receives the initial ACK packet from the server after sending a Write Request (WRQ)
    private static void receiveInAcknowledgments(DatagramSocket clientSocket) throws IOException {
        byte[] ackBuffer = new byte[4];
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
        clientSocket.receive(ackPacket);

        if (ackPacket.getData()[1] != OP_ACK || (((ackPacket.getData()[2] & 0xFF) << 8) | (ackPacket.getData()[3] & 0xFF)) != 0) {
            throw new IOException("Invalid initial ACK received");
        }
    }
}




