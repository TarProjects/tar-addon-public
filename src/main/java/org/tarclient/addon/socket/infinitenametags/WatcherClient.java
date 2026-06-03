package org.tarclient.addon.socket.infinitenametags;

import org.tarclient.addon.modules.InfiniteNameTags;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class WatcherClient {
    private final String serverAddress;
    private final int port;

    private Socket socket;

    private PrintWriter out;
    private BufferedReader in;

    private volatile boolean running = true;

    private InfiniteNameTags instance;

    public WatcherClient(String serverAddress, int port, InfiniteNameTags instance) {
        this.serverAddress = serverAddress;
        this.port = port;
        this.instance = instance;
    }

    public void connect() throws IOException {
        socket = new Socket(serverAddress, port);

        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void readLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                if (line.startsWith("TARGETINFO:")) {
                    String[] parts = line.substring(11).split(";");

                    if (parts.length == 4) {
                        try {
                            UUID uuid = UUID.fromString(parts[0]);
                            double x = Double.parseDouble(parts[1]);
                            double y = Double.parseDouble(parts[2]);
                            double z = Double.parseDouble(parts[3]);
                            mc.execute(() -> instance.handleUUIDXYZ(uuid, x, y, z));
                        } catch (Exception ignored) {
                        }
                    }

                }
            }
        } catch (IOException exception) {

        }
    }

    public void addTrack(UUID uuid) {
        sendCommand("ADDTRACK:" + uuid.toString());
    }

    public void forceTrack(UUID uuid) {
        sendCommand("FORCETRACK:" + uuid.toString());
    }

    public void sendCommand(String command) {
        if (isConnected()) {
            out.println(command);
        }
    }

    public void stop() {
        new Thread(() -> {
            running = false;
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException ignored) {
            }
        }).start();
    }

    public boolean isConnected() {
        return out != null && !socket.isClosed();
    }
}
