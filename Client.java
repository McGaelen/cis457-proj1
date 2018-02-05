import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

class Client {
    // Packet layout:
    // 4 byte hashcode | 8 byte packet number | 8 byte total number of packets | 1024 bytes packet data
    static final int PACKET_SIZE = 1044;
    static final int PAYLOAD_SIZE = 1024;

    // Generates a hashcode based on the content of the given ByteBuffer.
    public static int packetHashCode(ByteBuffer packet) {
        int hashcode = 0;
        while (packet.hasRemaining()) {
            hashcode += packet.get();
        }
        packet.flip();
        return hashcode;
    }

    public static void main(String args[]) {

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
            long packetNum; // A number to uniquely identify a packet in a sequence.
            long numPackets = 0; // The total number of packets in this transmission.
            int hashcode; // Used to verify the integrity of the packet received.

            // get filename to transfer and send it to server
            String filename = cons.readLine("Enter a filename: ");
            ByteBuffer filenameBuf = ByteBuffer.wrap(filename.getBytes());
            dg.send(filenameBuf, new InetSocketAddress(ipAddr, portNum));

            // create output file
            String fileExtension = filename.substring(filename.indexOf("."));
            FileOutputStream outFile = new FileOutputStream("outFile" + fileExtension, false);

            // Keeps track of which packets sent from the server have already been written.
            ArrayList<Integer> noDups = new ArrayList();

            // ByteBuffer for the acknowledgement packet.
            ByteBuffer ack = ByteBuffer.allocate(12);

            // recieve rest of packets in loop
            boolean sendingInProcess = true;
            while (sendingInProcess) {
                packet = ByteBuffer.allocate(PACKET_SIZE);

                // recieve packet, then get the hash code from it
                dg.receive(packet);
                packet.flip();
                hashcode = packet.getInt();
                packet.rewind();
                // Zero out the section of the packet where the hash code was,
                // so we can accurately compute the hash for it.
                packet.putInt(0);
                packet.rewind();

                // Check and see if the supplied hashcode matches our computed hashcode.
                // Only continue if they match.
                if (hashcode == Client.packetHashCode(packet)) {

                    // get the number identifying this packet.
                    packetNum = packet.getLong(4);

                    // if we're on the first iteration of the loop, we need the total
                    // number of packets in this transmission
                    if (numPackets == 0) {
                        numPackets = packet.getLong(12);
                        System.out.println("Total number of packets: " + numPackets);
                        for (int i = 0; i < numPackets; i++) {
                            noDups.add(0);
                        }
                    }

                    // Now send an acknowledgement that we received the packet.
                    // The acknowledgement contains the packet number identifying
                    // the packet that we received.
                    ack.rewind();
                    ack.putLong(packetNum);
                    ack.putInt(0); // Put zeros at the end of the ack packet for computing hashcode
                    ack.rewind();

                    // Compute a hash code for this acknowledgement packet,
                    // and add it to the end.
                    int ackHashcode = Client.packetHashCode(ack);
                    ack.position(8);
                    ack.putInt(ackHashcode);
                    ack.rewind();
                    dg.send(ack, new InetSocketAddress(ipAddr, portNum));

                    // Then write the received packet's data to disk if we haven't
                    // already written this packet in the past.
                    if (noDups.get((int)packetNum) == 0) {
                        System.out.println("Writing payload " + (packetNum+1) + " of " + numPackets);
                        packet.position(20);
                        outFile.getChannel().write(packet, (1024 * packetNum));
                        noDups.set((int)packetNum, 1); // keeps a record that we wrote this packet to disk.
                    }

                    // If there are no more zeroes in noDups, it means we have
                    // successfully written every packet. Then we jump out of the loop.
                    if (noDups.indexOf(0) == -1) {
                        sendingInProcess = false;
                    }
                }
            }

            System.out.println("File transfer finished");
            outFile.close();
            dg.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
