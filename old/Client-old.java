import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

class Client {
	public static void main(String args[]) {
        final int PACKET_SIZE = 1040; // 1024 + 16 byte header
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
            long packetNum, numPackets;

            // get filename to transfer and send it to server
			String filename = cons.readLine("Enter a filename: ");
			ByteBuffer filenameBuf = ByteBuffer.wrap(filename.getBytes());
			dg.send(filenameBuf, new InetSocketAddress(ipAddr, portNum));

            // create output file
            String fileExtension = filename.substring(filename.indexOf("."));
            FileOutputStream outFile = new FileOutputStream("outFile" + fileExtension, false);

            // recieve initial packet and set total number of packets
		    dg.receive(packet);
            packetNum = packet.getLong(0);
            numPackets = packet.getLong(8);
            System.out.println("Total packets: " + packet.getLong(8));

            int[] noDups = new int[(int)numPackets];

            // write initial payload
            System.out.println("Writing payload " + (packetNum+1) + " of " + numPackets + " Packets received: 1");
            outFile.write(packet.array(), 16, PAYLOAD_SIZE);

            ByteBuffer ack = ByteBuffer.allocate(8);
            ack.putLong(packetNum);
            ack.flip();
            dg.send(ack, new InetSocketAddress(ipAddr, portNum));

            // recieve rest of packets in loop
            while (noDups[(int)numPackets-1] == 0) {
                packet = ByteBuffer.allocate(PACKET_SIZE);

                // recieve next packet and get it's number
                dg.receive(packet);
                packetNum = packet.getLong(0);

                ack.flip();
                ack.putLong(packetNum);
                ack.flip();
                dg.send(ack, new InetSocketAddress(ipAddr, portNum));

                if (noDups[(int)packetNum] == 0) {
                    // write next payload
                    System.out.println("Writing payload " + (packetNum+1) + " of " + numPackets);
                    outFile.write(packet.array(), 16, packet.array().length - 16);
                    noDups[(int)packetNum] = 1;
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
