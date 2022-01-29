import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;

interface Constants
{
    // building the network of routers
    String ROUTER_INPUT_FILE_PREFIX = "input_router_";
    double SELECT_NEIGHBOR_PROBABILITY = 0.2;
    double CHANGE_WEIGHT_PROBABILITY = 0.5;
    int NETWORK_SIZE = 30;
    int MAXIMUM_WEIGHT = 500;
    int SEED = 452369;

    // output files
    String ROUTER_TABLE_OUTPUT_FILE_PREFIX = "table_router_";
    String ROUTER_FORWARDING_OUTPUT_FILE_PREFIX = "forward_router_";

    // test
    int PRINT_FORWARDING_NUM = NETWORK_SIZE * 2;
    int TEST_SIZE = 20;

    // ports
    int FIRST_UPD_PORT = 30000;
    int FIRST_TCP_PORT = FIRST_UPD_PORT + NETWORK_SIZE;
    int PRINT_SENDER_FIRST_PORT = FIRST_TCP_PORT + NETWORK_SIZE;
    int FORWARDING_SENDER_FIRST_PORT = PRINT_SENDER_FIRST_PORT + PRINT_FORWARDING_NUM;
    int UPDATE_SENDER_FIRST_PORT = FORWARDING_SENDER_FIRST_PORT + PRINT_FORWARDING_NUM;

    // messages
    String UPDATE_MESSAGE = "UPDATE-ROUTING-TABLE";
    String PRINT_MESSAGE = "PRINT-ROUTING-TABLE";
    String SHUT_DOWN_MESSAGE = "SHUT-DOWN";
    String FORWARD_MESSAGE_PREFIX = "FORWARD";
    String FINISH_MESSAGE = "FINISH";
    int FORWARDING_MESSAGE_LENGTH = 85;
    int MAXIMUM_HOPS = 68;
    String alphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    char[] alphaNumericChars = alphaNumericString.toCharArray();
}

class Sender extends Thread
{
    private final String message;
    private final int udpPortToSend;
    private final String ipToSend;
    private final int portToListen;
    private final boolean waitForReplay;
    private final String replyMessage;

    public Sender(String message, int udpPortToSend, String ipToSend, int portToListen, boolean waitForReplay,
                  String replayMessage)
    {
        this.message = message;
        this.udpPortToSend = udpPortToSend;
        this.ipToSend = ipToSend;
        this.portToListen = portToListen;
        this.waitForReplay = waitForReplay;
        this.replyMessage = replayMessage;
    }
    @Override
    public void run()
    {
        DatagramPacket packetToSend;
        byte[] bytesToSend = this.message.getBytes(StandardCharsets.UTF_8);
        byte[] bytesReceived = new byte[4096];
        DatagramPacket packetReceived = new DatagramPacket(bytesReceived, bytesReceived.length);
        try
        {
            DatagramSocket socket = new DatagramSocket(this.portToListen);
            InetAddress address = InetAddress.getByName(this.ipToSend);
            packetToSend = new DatagramPacket(bytesToSend, bytesToSend.length, address, this.udpPortToSend);
            socket.send(packetToSend);
            if (this.waitForReplay)
            {
                socket.receive(packetReceived);
                String messageReceived = new String(packetReceived.getData(), 0, packetReceived.getLength(),
                        StandardCharsets.UTF_8);
                if (!messageReceived.equals(this.replyMessage))
                {
                    System.out.println("Error in received message form ip=" + this.ipToSend + " port=" + this.udpPortToSend
                            + ". Expected: " + this.replyMessage + "but got " + messageReceived);
                }
            }
            socket.close();
        }
        catch (SocketException ex)
        {
            System.out.println("Socket error: with message to send " + this.message + ex.getMessage());
        }
        catch (IOException ex)
        {
            System.out.println("I/O error2: " + ex.getMessage());
        }
    }
}

public class Test
{
    public static String generateRandomString(Random random, int length)
    {
        if (length < 1)
        {
            throw new IllegalArgumentException();
        }
        StringBuilder out = new StringBuilder();
        for(int i=0; i<length;i++)
        {
            out.append(Constants.alphaNumericChars[random.nextInt(Constants.alphaNumericChars.length)]);
        }
        return out.toString();
    }

    public static String generateForwarding(Random random)
    {
        // generate random message
        String message = generateRandomString(random, Constants.FORWARDING_MESSAGE_LENGTH);
        int destination = random.nextInt(Constants.NETWORK_SIZE) + 1;
        int hops = random.nextInt(Constants.MAXIMUM_HOPS) + 1;

        return Constants.FORWARD_MESSAGE_PREFIX + ";" + destination + ";" + hops + ";" + message;
    }

    public static void main(String[] args) throws IOException
    {
        // fix the seed
        Random random = new Random();
        random.setSeed(Constants.SEED);

        // create the input for the routers
        CreateInput.createRouterInputAndWeights(Constants.ROUTER_INPUT_FILE_PREFIX,
                Constants.SELECT_NEIGHBOR_PROBABILITY, Constants.CHANGE_WEIGHT_PROBABILITY, Constants.FIRST_UPD_PORT,
                Constants.FIRST_TCP_PORT, Constants.MAXIMUM_WEIGHT, Constants.NETWORK_SIZE,
                Constants.TEST_SIZE + 1, random);

        // start all routers
        Router[] routers = new Router[Constants.NETWORK_SIZE + 1];
        for (int i = 1; i <= Constants.NETWORK_SIZE; i++)
        {
            routers[i] = new Router(i, Constants.ROUTER_INPUT_FILE_PREFIX, Constants.ROUTER_TABLE_OUTPUT_FILE_PREFIX,
                    Constants.ROUTER_FORWARDING_OUTPUT_FILE_PREFIX);
            routers[i].start();
        }
        // give all routers sufficient time to start listen
        try{
            TimeUnit.SECONDS.sleep(5);
        } catch (java.lang.InterruptedException e){
            System.out.println("ERROR"); }

        Sender[] printSenderArray = new Sender[Constants.PRINT_FORWARDING_NUM];
        Sender[] forwardingSenderArray = new Sender[Constants.PRINT_FORWARDING_NUM];
        Sender[] updateSenderArray = new Sender[Constants.NETWORK_SIZE];

        int printSenderPort = Constants.PRINT_SENDER_FIRST_PORT;
        int forwardingSenderPort = Constants.FORWARDING_SENDER_FIRST_PORT;
        int updateSenderPort = Constants.UPDATE_SENDER_FIRST_PORT;

        for (int j=0; j<Constants.TEST_SIZE; j++)
        {
            for (int i = 0; i < Constants.PRINT_FORWARDING_NUM; i++)
            {
                printSenderArray[i] = new Sender(Constants.PRINT_MESSAGE, Constants.FIRST_UPD_PORT + (i % Constants.NETWORK_SIZE),
                        "127.0.0.1", printSenderPort, true, Constants.FINISH_MESSAGE);
                printSenderPort += 1;

                String routingMessage = generateForwarding(random);
                String message = routingMessage.split(";")[3];
                int sendTo = random.nextInt(Constants.NETWORK_SIZE) + 1;
                routingMessage = routingMessage + ";" + "127.0.0.1" + ";" + forwardingSenderPort;
                forwardingSenderArray[i] = new Sender(routingMessage, Constants.FIRST_UPD_PORT + sendTo-1,
                        "127.0.0.1", forwardingSenderPort, true, message);
                forwardingSenderPort += 1;

            }
            for (int i = 0; i < Constants.PRINT_FORWARDING_NUM; i++)
            {
                printSenderArray[i].start();
                forwardingSenderArray[i].start();
            }

            for (int i = 0; i < Constants.PRINT_FORWARDING_NUM; i++)
            {
                try
                {
                    printSenderArray[i].join();
                    forwardingSenderArray[i].join();
                } catch (InterruptedException ex)
                {
                    System.out.println(ex.getMessage());
                }
            }

            printSenderPort = Constants.PRINT_SENDER_FIRST_PORT;
            forwardingSenderPort = Constants.FORWARDING_SENDER_FIRST_PORT;


            for (int i = 0; i < Constants.NETWORK_SIZE; i++)
            {
                updateSenderArray[i] = new Sender(Constants.UPDATE_MESSAGE, Constants.FIRST_UPD_PORT + i, "127.0.0.1",
                        updateSenderPort, true, Constants.FINISH_MESSAGE);
                updateSenderPort += 1;

            }

            for (int i = 0; i < Constants.NETWORK_SIZE; i++)
            {
                updateSenderArray[i].start();
            }

            for (int i = 0; i < Constants.NETWORK_SIZE; i++)
            {
                try
                {
                    updateSenderArray[i].join();
                } catch (InterruptedException ex)
                {
                    System.out.println(ex.getMessage());
                }
            }
            updateSenderPort = Constants.UPDATE_SENDER_FIRST_PORT;
        }

        int shutDownSenderPort = Constants.UPDATE_SENDER_FIRST_PORT;

        for (int i=0; i<Constants.NETWORK_SIZE;i++)
        {
            new Sender(Constants.SHUT_DOWN_MESSAGE,Constants.FIRST_UPD_PORT + i, "127.0.0.1",
                    shutDownSenderPort, false, null).start();
            shutDownSenderPort += 1;
        }
    }
}

