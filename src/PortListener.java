import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

abstract class PortListener extends Thread{
    int port;
    protected boolean isRunning;
    Router router;

    PortListener(int port, Router router){
        this.port = port;
        this.router = router;
        this.isRunning = true;
    }

    public void kill(){
        this.isRunning = false;
    }

}

class TCPListener extends PortListener {

    TCPListener(int port, Router router) {
        super(port, router);
    }

    @Override
    public void run(){
        try {
            ServerSocket serverSocket = new ServerSocket(this.port);
            while(this.isRunning) {
                    System.out.println("router: " + router.name + " TCP listeneing on: " + this.port);

                    Socket server = serverSocket.accept();

                    System.out.println("Just connected to " + server.getRemoteSocketAddress());
                    TCPHandler requestHandler = new TCPHandler(server, this.router);
                    requestHandler.start();

//                    DataInputStream input = new DataInputStream(server.getInputStream());
//                    String msg = input.readUTF();
//                    if (msg.equals("SHUT-DOWN")) {
//                        this.isRunning = false;
//                    }
            }
        } catch (SocketTimeoutException s) {
            System.out.println("Socket timed out!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

class UDPListener extends PortListener {

    UDPListener(int port, Router router) {
        super(port, router);
    }

    @Override
    public void run() {
        try {
            DatagramSocket socket = new DatagramSocket(this.port);
            while (this.isRunning) {

                System.out.println("router: " + router.name + " UDP listeneing on: " + this.port);

                UDPHandler handler = new UDPHandler(socket, this.router);
                DatagramPacket packet = new DatagramPacket(handler.buf, handler.buf.length);
                socket.receive(packet);


                String msg = new String(packet.getData(), 0, packet.getLength());
                if (msg.equals("SHUT-DOWN")){
                    this.router.udpListener.kill();
                    this.router.tcpListener.kill();
                    Socket client = new Socket("localhost", this.router.tcpListener.port);
                    OutputStream outToServer = client.getOutputStream();
                    DataOutputStream out = new DataOutputStream(outToServer);
                    out.writeUTF("SHUT-DOWN");
                    client.close();
                }
                else {
                    handler.updatePacket(packet);
                    handler.start();
                }

//            InetAddress address = packet.getAddress();
//            int port = packet.getPort();
//            packet = new DatagramPacket(buf, buf.length, address, port);
//            String received = new String(packet.getData(), 0, packet.getLength());
//
//            if (received.equals("end")) {
//                running = false;
//                continue;
//            }
//            socket.send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

//abstract class Handler extends Thread{
//    Socket socket;
//    Handler(Socket socket){
//        this.socket = socket;
//    }
//}

class TCPHandler extends Thread {
    Socket socket;
    Router router;
    TCPHandler(Socket socket, Router router) {
        this.socket = socket;
        this.router = router;
    }

    public void run(){
        try {
            DataInputStream input = new DataInputStream(this.socket.getInputStream());
            DataOutputStream out = new DataOutputStream(this.socket.getOutputStream());
            int num = Integer.parseInt(input.readUTF());
//            String[] requestedTable = Router.readRoutingTable(this.router.tableFilePrefix,
//                    this.router.networkSize,this.router.name, num);
            List<Integer> requestedTable = this.router.distanceVectors.get(num);
//            String test = requestedTable.toString();
            out.writeUTF(requestedTable.toString());
            input.close();
            out.close();
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e){
            System.out.println("Shutting down TCP port "+this.router.tcpListener.port);
        }
    }
}

class UDPHandler extends Thread {
    byte[] buf;
    Router router;
    DatagramPacket packet;
    DatagramSocket socket;
    UDPHandler(DatagramSocket socket, Router router) {
        this.router = router;
        this.buf = new byte[4096];
        this.socket = socket;
    }

    public void run() {
        String msg = new String(this.packet.getData(), 0, this.packet.getLength());
        InetAddress address = this.packet.getAddress();
        int port = this.packet.getPort();
        try {
            if (msg.equals("PRINT-ROUTING-TABLE")){
                this.router.printRoutingTable(this.socket, address, port);
            }
            if (msg.equals("UPDATE-ROUTING-TABLE")){
                this.router.updateRoutingTable(this.socket, address, port);
            }
            if (msg.startsWith("FORWARD")){
                this.router.forwardMessage(this.socket, msg);
            }
            if (msg.equals("SHUT-DOWN")){
                this.router.tcpListener.kill();
                this.router.udpListener.kill();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updatePacket(DatagramPacket packet){
        this.packet = packet;
    }
}