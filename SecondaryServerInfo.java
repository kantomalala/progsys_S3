import java.io.Serializable;
import java.net.Socket;

public class SecondaryServerInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private String ip;
    private int port;
    private String storagePath;
    private transient Socket socket;

    public SecondaryServerInfo(String ip, int port, String storagePath) {
        this.ip = ip;
        this.port = port;
        this.storagePath = storagePath;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Socket getSocket() {
        return socket;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }
    public SecondaryServerInfo() {

    }
}