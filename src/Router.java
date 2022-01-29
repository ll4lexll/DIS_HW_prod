import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;


public class Router extends Thread {
    int networkSize;
    PortListener udpListener;
    PortListener tcpListener;
    private int udpPort;
    private int tcpPort;
    int name;
    int round;
    final String tableFilePrefix;
    private final String forwardingFilePrefix;
    private final HashMap<String, List<String>> neighbors = new HashMap<>();
    private final List<List<Integer>> routingTable = new ArrayList<>();
    HashMap<Integer, List<Integer>> distanceVectors = new HashMap<>();

    public Router(int name, String inputFilePrefix, String tableFilePrefix, String forwardingFilePrefix){
        this.name = name;
        this.tableFilePrefix = tableFilePrefix;
        this.forwardingFilePrefix = forwardingFilePrefix;
        this.round = 1;

        try {
            String fileName = inputFilePrefix + this.name + ".txt";
            File inputFile = new File(fileName);
            Scanner inputScan = new Scanner(inputFile);

            int diameterBound;
            String neighborName;

            this.udpPort = Integer.parseInt(inputScan.nextLine());
            this.tcpPort = Integer.parseInt(inputScan.nextLine());
            this.networkSize = Integer.parseInt(inputScan.nextLine());
            String nextLine = inputScan.nextLine();
            int firstNeighbor = Integer.parseInt(nextLine);

            while (!nextLine.equals("*")) {
                neighborName = nextLine;
                List<String> neighborDetails = new ArrayList<>();
                neighborDetails.add(inputScan.nextLine());
                neighborDetails.add(inputScan.nextLine());
                neighborDetails.add(inputScan.nextLine());
                neighborDetails.add(inputScan.nextLine());
                this.neighbors.put(neighborName, neighborDetails);
                nextLine = inputScan.nextLine();
            }

            diameterBound = Integer.parseInt(inputScan.nextLine());
            for (int i = 1; i <= this.networkSize; i++) {
                List<Integer> neighborRouting = new ArrayList<>();
                if (i != name) {
                    neighborRouting.add(diameterBound);
                    neighborRouting.add(firstNeighbor);
                } else {
                    neighborRouting.add(0);
                    neighborRouting.add(null);
                }

                this.routingTable.add(neighborRouting);
            }

            inputScan.close();
        } catch (Exception e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

        List<Integer> distanceVector = new ArrayList<>();
        Set<String> routerNeighborsNames = this.neighbors.keySet();

        for (int i = 1; i <= this.networkSize; i++) {

                if (i == this.name) {
                    distanceVector.add(0);
                }
                else {

                    for (String neighbor : routerNeighborsNames) {
                        int newWeight = CreateInput.weightsMatrix[this.name][Integer.parseInt(neighbor)][this.round];
                        Integer oldWeight = Integer.valueOf(this.neighbors.get(neighbor).get(3));
                        List<Integer> router = this.routingTable.get(i - 1);
                        if (router.get(1) == Integer.parseInt(neighbor)) {
                            if (newWeight != -1) {
                                distanceVector.add(router.get(0) - oldWeight + newWeight);
                            } else {
                                distanceVector.add(router.get(0));
                            }
                        }
                    }
                }
        }

        for (String neighbor : routerNeighborsNames) {
            Integer newWeight = CreateInput.weightsMatrix[this.name][Integer.parseInt(neighbor)][this.round];;
            if (newWeight != -1) {
                this.neighbors.get(neighbor).set(3, String.valueOf(newWeight));
            }

        }
        this.distanceVectors.put(this.round, distanceVector);
    }

    public void printRoutingTable(DatagramSocket socket, InetAddress addressToSend, int port) throws IOException {
        FileWriter tableFile = new FileWriter(this.tableFilePrefix + this.name + ".txt", true);
        for (int router = 1; router <= this.networkSize; router++) {
            StringBuilder stringToPrint = new StringBuilder();
            List<Integer> neighbor = this.routingTable.get(router - 1);
            stringToPrint.append(neighbor.get(0));
            stringToPrint.append(";");
            if (neighbor.get(1) == null) {
                stringToPrint.append("None");
            } else {
                stringToPrint.append(neighbor.get(1));
            }

            tableFile.write(stringToPrint + System.lineSeparator());
        }

        tableFile.close();
        String message = "FINISH";
        byte[] bytesToSend = message.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packetToSend = new DatagramPacket(bytesToSend, bytesToSend.length, addressToSend, port);
        socket.send(packetToSend);
    }


//    public static String[] readRoutingTable(String tableFilePrefix, int networkSize, int name, int updateNum) throws FileNotFoundException {
//        String fileName = tableFilePrefix + name + ".txt";
//        File inputFile = new File(fileName);
//        Scanner tableScan = new Scanner(inputFile);
//
//        for (int i = 1; i <= networkSize * updateNum; ++i) {
//            tableScan.nextLine();
//        }
//
//        String[] table = new String[networkSize];
//
//        for (int i = 0; i < networkSize; ++i) {
//            table[i] = tableScan.nextLine();
//        }
//
//        return table;
//    }

    public void updateRoutingTable(DatagramSocket socket, InetAddress addressToSend, int port) throws IOException {
        HashMap<Integer, List<Integer>> neighborsDistanceVector = new HashMap<Integer, List<Integer>>();
        Set<String> routerNeighborsNames = this.neighbors.keySet();
        for (String neighbor : routerNeighborsNames) {
            String neighborIp = this.neighbors.get(neighbor).get(0);
            Integer neighborTcp = Integer.parseInt(this.neighbors.get(neighbor).get(2));

            List<Integer> distVec = getDistanceVectorFromRouter(neighborIp, neighborTcp, this.round);
            neighborsDistanceVector.put(Integer.valueOf(neighbor), distVec);

        }
        synchronized (this.routingTable) {
            for (int i = 1; i <= this.networkSize; i++) {
                if (i != this.name) {
                    int minDistance = Integer.MAX_VALUE;
                    int closestNeighbor = -1;
                    List<Integer> neighborList = new ArrayList<>();
                    for (String neighbor : routerNeighborsNames){
                        neighborList.add(Integer.valueOf(neighbor));
                    }
                    Collections.sort(neighborList);
                    for (Integer currNeighbor : neighborList) {
                        Integer weight = Integer.valueOf(this.neighbors.get(currNeighbor.toString()).get(3));
                        int currDistance = neighborsDistanceVector.get(currNeighbor).get(i - 1) + weight;
//                        Collections.sort((List)routerNeighborsNames);
                        if (currDistance < minDistance) {
                            minDistance = currDistance;
                            closestNeighbor = currNeighbor;
                        }
                    }

                    List<Integer> neighborRouting = new ArrayList<>();
                    neighborRouting.add(minDistance);
                    neighborRouting.add(closestNeighbor);
                    this.routingTable.set(i-1, neighborRouting);

                }
            }
            this.round = this.round + 1;
            List<Integer> distanceVector = new ArrayList<>();
            List<List<Integer>> routingTableCopy = new ArrayList<>(this.routingTable);
            for (int i = 1; i <= this.networkSize; i++) {
                if (i == this.name) {
                    distanceVector.add(0);
                }
                else {
                    for (String neighbor : routerNeighborsNames) {

                        int newWeight = CreateInput.weightsMatrix[this.name][Integer.parseInt(neighbor)][this.round];
                        Integer oldWeight = Integer.valueOf(this.neighbors.get(neighbor).get(3));
                        List<Integer> router = routingTableCopy.get(i - 1);
                        if (router.get(1) == Integer.parseInt(neighbor)) {
                            if (newWeight != -1) {
                                distanceVector.add(router.get(0) - oldWeight + newWeight);
                            } else {
                                distanceVector.add(router.get(0));
                            }
                        }
                    }
                }
            }
            for (String neighbor : routerNeighborsNames) {
                int newWeight = CreateInput.weightsMatrix[this.name][Integer.parseInt(neighbor)][this.round];
                if (newWeight != -1) {
                    this.neighbors.get(neighbor).set(3, String.valueOf(newWeight));
                }

            }
            this.distanceVectors.put(this.round, distanceVector);
        }
        try{
            String message = "FINISH";
            byte[] bytesToSend = message.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packetToSend = new DatagramPacket(bytesToSend, bytesToSend.length, addressToSend, port);
            socket.send(packetToSend);
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public synchronized void forwardMessage(DatagramSocket socket, String message) throws IOException{
        FileWriter forwardFile = new FileWriter(this.forwardingFilePrefix + this.name + ".txt", true);
        forwardFile.write(message + System.lineSeparator());
        forwardFile.close();
        String[] messageInfo = message.split(";");
        int destination = Integer.parseInt(messageInfo[1]);
        int hops = Integer.parseInt(messageInfo[2]);
        String messageContent = messageInfo[3];
        String ip = messageInfo[4];
        InetAddress addressToSend = InetAddress.getByName(ip);
        int port = Integer.parseInt(messageInfo[5]);

        if (destination == this.name || hops == 0){
            byte[] bytesToSend = messageContent.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packetToSend = new DatagramPacket(bytesToSend, bytesToSend.length, addressToSend, port);
            socket.send(packetToSend);

        }
        else{
            List<Integer> router = this.routingTable.get(destination - 1);
            int next = router.get(1);
            List<String> nextDetails = this.neighbors.get(String.valueOf(next));
            InetAddress addressSend = InetAddress.getByName(nextDetails.get(0));
            int nextPort = Integer.parseInt(nextDetails.get(1));

            String messageToSend = "FORWARD;" + destination + ";" + (hops - 1) + ";" + messageContent + ";" + ip + ";" + port;
            byte[] bytesToSend = messageToSend.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packetToSend = new DatagramPacket(bytesToSend, bytesToSend.length, addressSend, nextPort);
            socket.send(packetToSend);
        }
    }



    private List<Integer> getDistanceVectorFromRouter(String ip, int port, int num) throws IOException {
        Socket client = new Socket(ip, port);
//        System.out.println("Just connected to " + client.getRemoteSocketAddress());
        OutputStream outToServer = client.getOutputStream();
        DataOutputStream out = new DataOutputStream(outToServer);
        out.writeUTF(Integer.toString(num));
        InputStream inFromServer = client.getInputStream();
        DataInputStream in = new DataInputStream(inFromServer);
        String msg = in.readUTF().replace("[", "").replace("]", "").replace(" ", "");
        client.close();
        List<Integer> distVector = new ArrayList<>();
        String[] tempDistVec = msg.split(",");
        for(String dist : tempDistVec){
            distVector.add(Integer.parseInt(dist));
        }

        return distVector;
    }


    @Override
    public void run() {
        try {
            this.udpListener = new UDPListener(this.udpPort, this);
            this.tcpListener = new TCPListener(this.tcpPort, this);
//        start listening on ports
            udpListener.start();
            tcpListener.start();
//        wait till the threads finish
            udpListener.join();
            tcpListener.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}



