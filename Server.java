import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.nio.channels.*;
import java.util.*;
import java.lang.Thread;

class Server {
    // Packet layout:
    // 4 byte hashcode | 8 byte packet number | 8 byte total number of packets | 1024 bytes packet data
    static final int PACKET_SIZE = 1044;
    static final int PAYLOAD_SIZE = 1024;
    public static volatile int[] noDups; // keeps a record of packets already sent

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

        if (args.length < 1) {
            System.out.println("Must supply a port number.");
            System.exit(1);
        }

        int portNum = Integer.parseInt(args[0]);

        try {
            final DatagramChannel c = DatagramChannel.open();
            c.bind(new InetSocketAddress(portNum));
            while (true) {
                ByteBuffer filenameBuf = ByteBuffer.allocate(PACKET_SIZE);

                // blocking call
                System.out.println("Waiting to receive...");
                SocketAddress clientaddr = c.receive(filenameBuf);

                // capture file name
                String filename = new String(filenameBuf.array());
                filename = filename.trim();
                System.out.println("Searching for file: " + filename);

                // open file and get the number of packets it will need
                FileInputStream file = new FileInputStream(filename);
                final long numPackets = (file.getChannel().size() / PAYLOAD_SIZE) + 1;
                System.out.println("File is " + numPackets + " packets long");

                // Initialize noDups size to the number of packets it will take to send this file.
                Server.noDups = new int[(int)numPackets];

                // The purpose of this thread is to concurrently listen for acknowledgements
                // coming back from the client. It will set the index of noDups corresponding
                // to the acknowledgement to a '1' when it receives an ack.
                // It will also check the hash of ack packet.
                Thread ackThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        ByteBuffer ack = ByteBuffer.allocate(12);
                        long acknowledgedPackets = 0;
                        // Loop to constantly receive acks.
                        while (true) {
                            try {
                                // If we receive something
                                if (c.receive(ack) != null) {
                                    // get the supplied hashcode off of it
                                    ack.flip();
                                    int hashcode = ack.getInt(8);
                                    ack.position(8);
                                    // Zero out the section of the packet where the hash code was,
                                    // so we can accurately compute the hash for it.
                                    ack.putInt(0);
                                    ack.rewind();
                                    System.out.println("Received Ack Hash = " + hashcode);

                                    // Check and see if the supplied hashcode matches our computed hashcode.
                                    // Only continue if they match.
                                    if (hashcode == Server.packetHashCode(ack)) {
                                        long ackNum = ack.getLong();
                                        System.out.println("Received acknowledgement: " + ackNum);
                                        // keep a record that we successfully sent this packet
                                        Server.noDups[(int)ackNum] = 1;
                                        acknowledgedPackets++;
                                        ack.rewind();
                                    }
                                }
                                // Count the number of ones in noDups. We'll use this to figure
                                // out if the transmission has completed.
                                int numberOfOnes = 0;
                                for (int x = 0; x < Server.noDups.length; x++) {
                                    if (Server.noDups[x] == 1) {
                                        numberOfOnes++;
                                    }
                                }
                                // Break if the number of ones in noDups is equal to the
                                // size of noDups.
                                if (numberOfOnes == numPackets) {
                                    break;
                                }
                            } catch (IOException e) {
                                System.out.println(e.getMessage());
                            }
                        }
                    }
                });

                ackThread.start();

                // send rest of packets in loop
                long i = 0;
                boolean sendingInProcess = true;
                while (sendingInProcess) {
                    // clear entire window
                    ArrayList<ByteBuffer> window = new ArrayList(5);
                    for (int z = 0; z < 5; z++) {
                        window.add(ByteBuffer.allocate(PAYLOAD_SIZE));
                    }

                    // re-populate window shifted forward by 1024 bytes
                    file.getChannel().read(window.get(0), ((i)*1024));
                    file.getChannel().read(window.get(1), ((i+1)*1024));
                    file.getChannel().read(window.get(2), ((i+2)*1024));
                    file.getChannel().read(window.get(3), ((i+3)*1024));
                    file.getChannel().read(window.get(4), ((i+4)*1024));

                    // loop through window to send each individual buffer
                    for (long j = 0; j < 5; j++) {
                        // only send the packet if we haven't sent it in the past
                        if(Server.noDups[(int)(i+j)] == 0) {
                            ByteBuffer packet = ByteBuffer.allocate(PACKET_SIZE);

                            // add everything to the packet in order
                            window.get((int)j).flip();
                            packet.putInt(0);
                            packet.putLong(i+j);           // first half of header
                            packet.putLong(numPackets);    // 2nd half of header
                            packet.put(window.get((int)j));  // data
                            packet.flip();

                            packet.putInt(Server.packetHashCode(packet));
                            packet.rewind();

                            c.send(packet, clientaddr);
                            System.out.println("Sending packet " + (i+j));
                        }
                    }

                    // We set this to false initially. It will be reset to true
                    // if we find that we're not done.
                    sendingInProcess = false;

                    // Wait for a short time for the client to receive.
                    if (Server.noDups[(int)i] == 0) {
                        try {
                            Thread.sleep(1);
                        } catch(InterruptedException e) {
                            System.out.println("server got interrupted");
                        }
                    }

                    // ??
                    for (int x = 0; x < Server.noDups.length; x++) {
                        if (Server.noDups[x] == 0) {
                            sendingInProcess = true;
                        }
                        if (Server.noDups[x] == 0 && i < (numPackets-3)) {
                            i = x;
                            break;
                        }
                    }

                    // ??
                    if (i == numPackets-5) {
                        int counter = 0;
                        for (int x = Server.noDups.length-5; x < Server.noDups.length; x++) {
                            if (Server.noDups[x] == 1) {
                                counter++;
                            }
                        }
                        if (counter < 5) {
                            i--;
                        }
                    }

                    if (i >= (numPackets-4)) {
                        i = (numPackets-5);
                    }
                }
                file.close();
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
