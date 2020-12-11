package app.broker;

import org.zeromq.*;

import app.*;
import app.central.BrokerProtocol;
import org.apache.commons.lang3.SerializationUtils;

public class DynamicBrokerLayer2 {

    static ConfigReader config = new ConfigReader();

    public static BrokerProtocol requestDynamicBrokerLayer2(ZMQ.Socket socket_req){
        
        //send : layer2
        //rcv  : object - BrokerProtocol

        //request to central server port
        String port = config.getPort("ports", "CENTRAL_SERVER_REP");
        socket_req.connect("tcp://*:" + port);

        //create request message
        socket_req.send(new String("layer2"));
        byte[] msg_recv = socket_req.recv();
        BrokerProtocol message = SerializationUtils.deserialize(msg_recv);
        return message;
        
    }

    public static void main(String[] args) {
        
        try (
                ZContext context = new ZContext();
                //to request a dynamic layer 1 broker
                ZMQ.Socket socket_req = context.createSocket(SocketType.REQ);
                ZMQ.Socket XSUBSocket = context.createSocket(SocketType.XSUB);
                ZMQ.Socket XPUBSocket = context.createSocket(SocketType.XPUB);
                ZMQ.Socket SUBSocket = context.createSocket(SocketType.SUB))
            {
                //receive new layer 1 brokers
                new HandleXPubs(SUBSocket).start();

                BrokerProtocol generated = requestDynamicBrokerLayer2(socket_req);
            
                //broker was requested successfully

                int xpub = generated.getXPUB_PORT();
                XPUBSocket.bind("tcp://*:" + xpub);

                System.out.println("[Broker - layer2] XPUB port = " + xpub);

                for(Integer xsub: generated.getXSUB_PORTS()){
                    XSUBSocket.connect("tcp://*:" + xsub);
                }
                System.out.println("[Broker - layer2] XSUB port = " + generated.toString());
                                
                ZMQ.proxy(XSUBSocket, XPUBSocket, null);
            }
    }

    public static class HandleXPubs extends Thread {

        private ZMQ.Socket subSocket;

        public HandleXPubs(ZMQ.Socket sub) {
            this.subSocket = sub;
        }        

        @Override
        public void run() {

            int pub = Integer.parseInt(config.getPort("ports", "CENTRAL_SERVER_PUB"));

            this.subSocket.subscribe("layer2_".getBytes());
            
            this.subSocket.connect("tcp://*:"+pub);
            
            while(true) {
                System.out.println("[Broker - layer2, notifier] waiting for new notifications...");
                byte[] resBytes = this.subSocket.recv();
                String resMsg = new String(resBytes);
                String[] parts = resMsg.split("_");
                System.out.println("[Broker] received notification: " + (resMsg));
                int newXPUB = Integer.parseInt(parts[1]);
                this.subSocket.connect("tcp://*:" + newXPUB);
            }
        }        
    }
}