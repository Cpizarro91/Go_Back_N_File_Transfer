import java.io.*;
import java.net.*;
import java.util.Vector;

public class windowServer { //server

    public static void main(String args[]) throws Exception {
        // Get the address, port and name of file to send over UDP
        final String hostName = "localhost";
        final int port = 2014;
        System.out.println("args: " + args[0]);
        final int RN = Integer.parseInt(args[0]);
        System.out.println("RN: " + RN);

        if(RN==0||RN==1||RN==2){
        int bytesRead;         
        ServerSocket serverSocket = null;  
        serverSocket = new ServerSocket(2014);  
        String fileName1 = null;
        
        //I am opening a socket just to get the filename 
        while(true) {  
            Socket clientSocket = null;  
            clientSocket = serverSocket.accept();  
               
            InputStream in = clientSocket.getInputStream();  
               
            DataInputStream clientData = new DataInputStream(in);   
               
            fileName1 = clientData.readUTF();     
            System.out.println("filename being requested: " + fileName1);
            OutputStream output = new FileOutputStream(fileName1);     
            long size = clientData.readLong();     
            byte[] buffer = new byte[1024];     
            while (size > 0 && (bytesRead = clientData.read(buffer, 0, (int)Math.min(buffer.length, size))) != -1)     
            {     
                output.write(buffer, 0, bytesRead);     
                size -= bytesRead;     
            }  
               
            // Closing the FileOutputStream handle
            in.close();
            clientData.close();
            output.close(); 
            break;
        }
        serverSocket.close();
        
            /////////////////////////////////
        createFileAndSend(hostName, port, fileName1, RN);
        }
        else{
        	System.out.println("RN number not supported!");
        }
    }
    

    public static void createFileAndSend(String hostName, int port, String fileName, int RN) throws IOException {
        System.out.println("Sending the file");
        BufferedWriter logger = new BufferedWriter(new FileWriter("serverLog.txt")); //this is placed inside current directory
        logger.write("Received request for file " + fileName + "\n");
        // Create the socket, set the address and create the file to be sent
        DatagramSocket socket = new DatagramSocket();
        InetAddress address = InetAddress.getByName(hostName);
        File file = new File("directory where the file is" + fileName); //fileName	

        // Create a byte array to store the filestream
        InputStream inFromFile = new FileInputStream(file);
        byte[] fileByteArray = new byte[(int)file.length()];
        inFromFile.read(fileByteArray);

        // Start timer for calculating throughput
        StartTime timer = new StartTime(0);

        // Create a flag to indicate the last message and a 16-bit sequence number
        int sequenceNumber = 0;
        boolean lastMessageFlag = false;

        // Create a flag to indicate the last acknowledged message and a 16-bit sequence number
        int ackSequenceNumber = 0;
        int lastAckedSequenceNumber = 0;
        boolean lastAcknowledgedFlag = false;

        // Create a counter to count number of retransmissions and initialize window size
        int retransmissionCounter = 0;
        int windowSize = 4;
        int acksReceivedCounter = 0;
        
        // Vector to store the sent messages
        Vector <byte[]> sentMessageList = new Vector <byte[]>();

        // For as each message we will create
        for (int i=0; i < fileByteArray.length; i = i+1021 ) {

            // Increment sequence number
            sequenceNumber += 1;

            // Create new byte array for message
            byte[] message = new byte[1024];

            // Set the first and second bytes of the message to the sequence number
            message[0] = (byte)(sequenceNumber >> 8);
            message[1] = (byte)(sequenceNumber);

            // Set flag to 1 if packet is last packet and store it in third byte of header
            if ((i+1021) >= fileByteArray.length) {
                lastMessageFlag = true;
                message[2] = (byte)(1);
            } else { // If not last message store flag as 0
                lastMessageFlag = false;
                message[2] = (byte)(0);
            }

            // Copy the bytes for the message to the message array
            if (!lastMessageFlag) {
                for (int j=0; j != 1021; j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }
            else if (lastMessageFlag) { // If it is the last message
                for (int j=0;  j < (fileByteArray.length - i); j++) {
                    message[j+3] = fileByteArray[i+j];
                }
            }

            // Package the message
            DatagramPacket sendPacket = new DatagramPacket(message, message.length, address, port);

            // Add the message to the sent message list
            sentMessageList.add(message);
 
            while (true) {
                // If next sequence number is outside the window
                if ((sequenceNumber - windowSize) > lastAckedSequenceNumber) {

                    boolean ackRecievedCorrect = false;
                    boolean ackPacketReceived = false;

                    while (!ackRecievedCorrect) {
                        // Check for an ack
                        byte[] ack = new byte[2];
                        DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                        try {
                            socket.setSoTimeout(50);
                            socket.receive(ackpack);
                            ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                            ackPacketReceived = true;
                        } catch (SocketTimeoutException e) {
                            ackPacketReceived = false;
                        }

                        if (ackPacketReceived) {
                            if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                                lastAckedSequenceNumber = ackSequenceNumber;
                            }
                            ackRecievedCorrect = true;
                            System.out.println("Ack recieved1: Packet #: " + ackSequenceNumber);
                            logger.write("Ack received from client: Packet #: " + ackSequenceNumber + "\n");
                            acksReceivedCounter++;
                            break; 	// Break if there is an ack so the next packet can be sent
                        } else { // Resend the packet
                            int resendClient = sequenceNumber - windowSize;
                            logger.write("Resending packet to client: Packet #: " + resendClient + "\n");
                            // Resend the packet following the last acknowledged packet and all following that (cumulative acknowledgement)
                            int resendThis = sequenceNumber-lastAckedSequenceNumber;
                            int loopCounter = 1;
                            for (int y=0; y != resendThis  ; y++) {
                    
                                byte[] resendMessage = new byte[1024];
                                resendMessage = sentMessageList.get(y + lastAckedSequenceNumber);
                                
                                
                                //resendClient++;
                                DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                                socket.send(resendPacket);
                                retransmissionCounter += 1;
                            }
                        }
                    }
                } else { // Else pipeline is not full, break so we can send the message
                    break;
                }
            }
            if(RN==0){ //send all packets
            	socket.send(sendPacket);
            }
            // Send the message
            else if(RN==1){ //lose the first packet of each window
            	if(sequenceNumber%4==1){ 
            	System.out.println("Lost packet " + sequenceNumber);
            	System.out.println("Resending1: Packet #: " + sequenceNumber);
            	logger.write("Server has lost packet #: " + sequenceNumber + "\n");
            	}
            	else{ 
            		socket.send(sendPacket);
                    System.out.println("Sent: Packet #: " + sequenceNumber + ", Flag = " + lastMessageFlag);
                    logger.write("Sent packet to client: Packet #: " + sequenceNumber + "\n");
            	}
            }
            else{
            	System.out.println("Lost packet " + sequenceNumber);
            	System.out.println("Resending1: Packet #: " + sequenceNumber);
            	//maybe just resend here
            	logger.write("Server has lost packet #: " + sequenceNumber + "\n");
            }


            // Check for acknowledgements
            while (true) {
                boolean ackPacketReceived = false;
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(10);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    ackPacketReceived = false;
                    break;
                }

                // Note any acknowledgements and move window forward
                if (ackPacketReceived) {
                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                        System.out.println("Ack recieved2: Packet #: " + ackSequenceNumber);
                        logger.write("Ack received from client: Packet #: " + ackSequenceNumber + "\n");
                        acksReceivedCounter++;
                    }
                }
            }
        }

        // Continue to check and resend until we receive final ack
        while (!lastAcknowledgedFlag) {

            boolean ackRecievedCorrect = false;
            boolean ackPacketReceived = false;

            while (!ackRecievedCorrect) {
                // Check for an ack
                byte[] ack = new byte[2];
                DatagramPacket ackpack = new DatagramPacket(ack, ack.length);

                try {
                    socket.setSoTimeout(50);
                    socket.receive(ackpack);
                    ackSequenceNumber = ((ack[0] & 0xff) << 8) + (ack[1] & 0xff);
                    ackPacketReceived = true;
                } catch (SocketTimeoutException e) {
                    //System.out.println("Socket timed out waiting for an ack1");
                    ackPacketReceived = false;
                    //e.printStackTrace();
                }

                // If its the last packet
                if (lastMessageFlag) {
                    lastAcknowledgedFlag = true;
                    break;
                }	
                // Break if we receive acknowledgement so that we can send next packet
                    if (ackPacketReceived) {		
                    System.out.println("Ack recieved3: Packet #: " + ackSequenceNumber);
                    logger.write("Ack received from client: Packet #: " + ackSequenceNumber+ "\n");
                    acksReceivedCounter++;

                    if (ackSequenceNumber >= (lastAckedSequenceNumber + 1)) {
                        lastAckedSequenceNumber = ackSequenceNumber;
                    }
                    ackRecievedCorrect = true;
                    break; // Break if there is an ack so the next packet can be sent
                } else { // Resend the packet
                    // Resend the packet following the last acknowledged packet and all following that (cumulative acknowledgement)
                    for (int j=0; j != (sequenceNumber-lastAckedSequenceNumber); j++) {
                        byte[] resendMessage = new byte[1024];
                        resendMessage = sentMessageList.get(j + lastAckedSequenceNumber);
                        DatagramPacket resendPacket = new DatagramPacket(resendMessage, resendMessage.length, address, port);
                        socket.send(resendPacket);
                        int resendClient = sequenceNumber - windowSize;
                        System.out.println("Resending2: Packet #: " + resendClient);
                        logger.write("Resending Packet#: " + lastAckedSequenceNumber + "\n");
                        // Increment retransmission counter
                        retransmissionCounter += 1;
                    }
                }
            }
        }
        inFromFile.close();
        socket.close();
        System.out.println("File " + fileName + " has been sent");
        logger.write("File has been fully sent to client.\n");
        logger.write("File is saved in current directory on client local machine.\n");
        logger.close();
        // Calculate the average throughput
        int fileSizeKB = (fileByteArray.length) / 1024;
        int transferTime = timer.getTimeElapsed() / 1000;
        double throughput = (double) fileSizeKB / transferTime;
        System.out.println("File size: " + fileSizeKB + "KB, Transfer time: " + transferTime + " seconds. Throughput: " + throughput + "KBps");
        System.out.println("Number of retransmissions: " + retransmissionCounter);	
        System.out.println("Acknowledgements received: " + acksReceivedCounter);
    }
}
