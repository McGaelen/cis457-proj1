import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

class Client {
	public static void main(String args[]) {
        final int PACKET_SIZE = 1044; // 1024 + 20 byte header
        final int PAYLOAD_SIZE = 1024;

        if (args.length < 2) {
            System.out.println("Must supply a port number and IP address.");
            System.exit(1);
        }

        Console cons = System.console();
	int portNum = Integer.parseInt(args[0]);
	String ipAddr = args[1];

	try {
	    DatagramChannel dg = DatagramChannel.open();
            ByteBuffer packet = ByteBuffer.allocate(PACKET_SIZE);
            long packetNum;
            long numPackets = 0;
            int hashcode;

            // get filename to transfer and send it to server
	    String filename = cons.readLine("Enter a filename: ");
	    ByteBuffer filenameBuf = ByteBuffer.wrap(filename.getBytes());
	    dg.send(filenameBuf, new InetSocketAddress(ipAddr, portNum));

            // create output file
            String fileExtension = filename.substring(filename.indexOf("."));
            FileOutputStream outFile = new FileOutputStream("outFile" + fileExtension, false);

            ArrayList<Integer> noDups = new ArrayList();

            ByteBuffer ack = ByteBuffer.allocate(8);
       
            // recieve rest of packets in loop
            boolean sendingInProcess = true;
            while (sendingInProcess) {
                packet = ByteBuffer.allocate(PACKET_SIZE);

                // recieve next packet and get it's number
                dg.receive(packet);
                packet.flip();
                hashcode = packet.getInt();
                packet.flip();
                packet.putInt(0);
                packet.flip();
                
                System.out.println("Receieved hash: " + hashcode + "  Computed hash: " + Arrays.hashCode(packet.array()));
                // If the packet is not corrupted
                if (hashcode == Arrays.hashCode(packet.array())) {
                    packetNum = packet.getLong(4);

                    // if we're on the first iteration of the loop
                    if (numPackets == 0) {
                        numPackets = packet.getLong(12);
                        System.out.println("Total number of packets: " + numPackets);
                        for (int i = 0; i < numPackets; i++) {
                            noDups.add(0);
                        }
                    }
                    
                    ack.flip();
                    ack.putLong(packetNum);
                    ack.flip();
                    dg.send(ack, new InetSocketAddress(ipAddr, portNum));

                    if (noDups.get((int)packetNum) == 0) {
                        // write next payload
                        System.out.println("Writing payload " + (packetNum+1) + " of " + numPackets);
                        outFile.getChannel().write(packet, (1024 * packetNum));
                        noDups.set((int)packetNum, 1);
                    }
                    
                    if (noDups.indexOf(0) == -1) {
                        sendingInProcess = false;
                    }
                }
            }

            System.out.println("File transfer finished");
            outFile.close();
	    dg.close();
	} catch (IOException e) {
	    System.out.println("error");
	}
    }
}
