package org.tarclient.addon.socket.infinitenametags;

import org.tarclient.addon.modules.InfiniteNameTags;
import org.tarclient.addon.modules.InfiniteNameTagsTeleporter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TeleporterServer {
    private final int port;
    private ServerSocket serverSocket;
    private Socket clientSocket;

    private PrintWriter out;
    private BufferedReader in;

    private volatile boolean running = true;

    private InfiniteNameTagsTeleporter instance;

    public TeleporterServer(int port, InfiniteNameTagsTeleporter instance) {
        this.port = port;
        this.instance = instance;
    }

    public void acceptClient() throws IOException {
        serverSocket = new ServerSocket(port);
        clientSocket = serverSocket.accept();
        out = new PrintWriter(clientSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    public void readLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                if (line.startsWith("ADDTRACK:")) {
                    String uuidString = line.substring(9);
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        mc.execute(() -> instance.handleAddTrack(uuid));
                    } catch (Exception ignored) {}
                }
                if (line.startsWith("FORCETRACK:")) {
                    String uuidString = line.substring(11);

                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        mc.execute(() -> instance.handleForceTrack(uuid));
                    } catch (Exception ignored) {}
                }
            }
        } catch (IOException exception) {

        }
    }

    public void sendUUIDXYZ(UUID uuid, double x, double y, double z) {
        sendCommand(String.format("TARGETINFO:%s;%.2f;%.2f;%.2f%n", uuid, x, y, z));
    }


    public void sendCommand(String command) {
        if (isConnected()) {
            out.println(command);
        }
    }

    public void stop() {
        running = false;
        close();
    }

    public void close() {
        try {
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }

    public boolean running() {
        return running;
    }

    public boolean isConnected() {
        return out != null && !clientSocket.isClosed();
    }
}
