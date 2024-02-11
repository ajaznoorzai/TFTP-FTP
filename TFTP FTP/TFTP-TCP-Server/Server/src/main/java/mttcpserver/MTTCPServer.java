package mttcpserver;

import java.io.*;
import java.net.*;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MTTCPServer {
    // Define opcodes and packet size
    private static final byte OP_RRQ = 1;
    private static final byte OP_WRQ = 2;
    private static final byte OP_DATAPACKET = 3;
    private static final byte OP_ACK = 4;
    private static final byte OP_ERROR = 5;
    public final static int PACKET_SIZE = 512;


    // Server socket for accepting client connections
    private ServerSocket serverSocket;
    private int defaultPort = 2000;

    // Thread socket for managing client connections
    private ExecutorService executorService;

    //main method to start the server
    public static void main(String[] args) throws Exception {
        MTTCPServer tftpServer = new MTTCPServer();
        tftpServer.run();
    }
   // main server loop
    public void run() throws IOException {
        // Create server socket and thread pool
        serverSocket = new ServerSocket(defaultPort, 50, InetAddress.getLocalHost());
        executorService = Executors.newFixedThreadPool(5);

        System.out.println("TFTP-TCP-Server connected to port number " + serverSocket.getLocalPort());
        System.out.println(InetAddress.getLocalHost());


        // continuously accepts and handle client connections
        while (true) {
            System.out.println("Waiting for client connection...");
            Socket clientSocket = serverSocket.accept();
            executorService.submit(new ClientHandler(clientSocket));
        }
    }

    // class for handling each client connections
    class ClientHandler implements Runnable {
        // client socket
        private Socket clientSocket;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        // main method for handling client request
        @Override
        public void run() {
            try {
                // get input and output stream
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                // Read the request packet and determine the request type
                byte[] requestBuffer = new byte[PACKET_SIZE];
                int requestLength = inputStream.read(requestBuffer);
                byte pType = requestBuffer[1];

                // Process read or write request
                if (pType == OP_RRQ || pType == OP_WRQ) {
                    String fileName = getFileName(requestBuffer, requestLength);
                    System.out.println("Requested file name: " + fileName);
                    File file = new File(fileName);

                    if (pType == OP_WRQ) {
                        receiveFile(inputStream, outputStream, fileName);
                    } else if (pType == OP_RRQ) {
                        if (!file.exists()) {
                            sendErrorPacket(outputStream, fileName);
                            System.out.println("File not found");
                        } else {
                            sendFile(outputStream, fileName);
                        }
                    }
                }

                // close the client Socket
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Exception: " + e);
            }
        }


        // Extract file name from request buffer
        private String getFileName(byte[] requestBuffer, int requestLength) {
            return new String(requestBuffer, 2, requestLength - 3).trim();
        }


        // send the requested file to the client
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

        // Receive and save the file from client
        private void receiveFile(InputStream inputStream, OutputStream outputStream, String fileName) throws IOException {
            File file = new File(fileName);
            FileOutputStream fileOutput = new FileOutputStream(file);
            byte[] dataBuffer = new byte[PACKET_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(dataBuffer)) != -1) {
                fileOutput.write(dataBuffer, 0, bytesRead);
            }
            fileOutput.close();
            sendAckPacket(outputStream);
        }





        // send an error packet to the client
        private void sendErrorPacket(OutputStream outputStream, String fileName) throws IOException {
            byte[] errorPacket = createErrorPacket(OP_ERROR, fileName);
            outputStream.write(errorPacket);
        }

        private void sendAckPacket(OutputStream outputStream) throws IOException {
            byte[] ackPacket = new byte[4];
            ackPacket[0] = 0;
            ackPacket[1] = OP_ACK;
            ackPacket[2] = 0;
            ackPacket[3] = 0;
            outputStream.write(ackPacket);
        }



        // create an error packet to send tot he client
        private byte[] createErrorPacket(byte opCode, String fileName) {
            String errorMessage = "File not found";
            byte zeroByte = 0;
            int errorPacketLength = 4 + errorMessage.length() + 1;
            byte[] errorByteArray = new byte[errorPacketLength];

            int position = 0;
            // add the opcode and error message to the error packet
            errorByteArray[position++] = zeroByte;
            errorByteArray[position++] = opCode;
            for (int i = 0; i < errorMessage.length(); i++) {
                errorByteArray[position++] = (byte) errorMessage.charAt(i);
            }
            errorByteArray[position] = zeroByte;

            return errorByteArray;
        }

    }

}