import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;



public class SecondaryServer2 {
    private int port;
    private String storagePath;
    private String mainServerIp;
    private int mainServerPort;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = true;

    // Constructeur avec les paramètres prédéfinis
    public SecondaryServer2() {
        this.port = 5002;  // Port prédéfini
        this.storagePath = "storage/secondary2";  // Chemin prédéfini
        this.mainServerIp = "localhost";  // IP prédéfinie
        this.mainServerPort = 5000;  // Port du serveur principal prédéfini
        
        displayServerInfo();
        createStorageDirectory();
        registerWithMainServer();
        startServer();
    }

    // Constructeur avec paramètres (pour la flexibilité)
    public SecondaryServer2(int port, String storagePath, String mainServerIp, int mainServerPort) {
        this.port = port;
        this.storagePath = storagePath;
        this.mainServerIp = mainServerIp;
        this.mainServerPort = mainServerPort;
        
        displayServerInfo();
        createStorageDirectory();
        registerWithMainServer();
        startServer();
    }

    private void displayServerInfo() {
        System.out.println("\n╔════════════════════════════════════════");
        System.out.println("║ SERVEUR SECONDAIRE - DÉMARRAGE");
        System.out.println("╠════════════════════════════════════════");
        System.out.println("║ Port: " + port);
        System.out.println("║ Stockage: " + storagePath);
        System.out.println("║ Serveur Principal: " + mainServerIp + ":" + mainServerPort);
        System.out.println("╚════════════════════════════════════════\n");
    }

    // [Le reste des méthodes reste identique au code précédent]
    private void createStorageDirectory() {
        File storage = new File(storagePath);
        if (!storage.exists()) {
            storage.mkdirs();
            log("📁 Répertoire de stockage créé: " + storagePath);
        }
    }

    private void registerWithMainServer() {
        try (Socket socket = new Socket(mainServerIp, mainServerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            
            out.writeObject("SECONDARY_SERVER");
            
            SecondaryServerInfo info = new SecondaryServerInfo(
                InetAddress.getLocalHost().getHostAddress(),
                port,
                storagePath
            );
            
            out.writeObject(info);
            log("✅ Enregistré auprès du serveur principal");
            
        } catch (IOException e) {
            log("❌ Erreur d'enregistrement: " + e.getMessage());
            System.exit(1);
        }
    }

    private void startServer() {
        startCommandListener();

        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                log("🚀 Serveur secondaire démarré sur le port " + port);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleConnection(clientSocket);
                    } catch (SocketException e) {
                        if (!isRunning) break;
                        log("❌ Erreur de connexion: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                log("❌ Erreur serveur fatale: " + e.getMessage());
            }
        });
        serverThread.start();
    }

    // [Autres méthodes identiques...]
    // Pour simplifier, j'inclus uniquement les méthodes essentielles

    private void handleConnection(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            String action = (String) in.readObject();
            
            if (action.equals("STORE_PARTITION")) {
                String partitionName = (String) in.readObject();
                byte[] partitionData = (byte[]) in.readObject();
                
                File partitionFile = new File(storagePath, partitionName);
                Files.write(partitionFile.toPath(), partitionData);
                
                log("📦 Partition reçue et stockée: " + partitionName);
            } else if (action.equals("DELETE_PARTITION")) {
                String partitionPath = (String) in.readObject();
                File partitionFile = new File(partitionPath);
                if (partitionFile.delete()) {
                    log("🗑️ Partition supprimée: " + partitionPath);
                }
            }
        } catch (Exception e) {
            log("❌ Erreur de traitement: " + e.getMessage());
        }
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    private void startCommandListener() {
        Thread commandThread = new Thread(() -> {
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            while (isRunning) {
                try {
                    String command = consoleReader.readLine();
                    if (command != null) handleCommand(command);
                } catch (IOException e) {
                    log("❌ Erreur de lecture console: " + e.getMessage());
                }
            }
        });
        commandThread.setDaemon(true);
        commandThread.start();

        System.out.println("\nCommandes disponibles:");
        System.out.println("- help    : Afficher l'aide");
        System.out.println("- status  : État du serveur");
        System.out.println("- list    : Lister les fichiers");
        System.out.println("- stop    : Arrêter le serveur\n");
    }

    private void handleCommand(String command) {
        switch (command.toLowerCase()) {
            case "help":
                System.out.println("\nCommandes disponibles:");
                System.out.println("- help    : Afficher l'aide");
                System.out.println("- status  : État du serveur");
                System.out.println("- list    : Lister les fichiers");
                System.out.println("- stop    : Arrêter le serveur");
                break;
            case "status":
                displayServerInfo();
                break;
            case "list":
                listFiles();
                break;
            case "stop":
                stopServer();
                break;
            default:
                System.out.println("Commande inconnue. Tapez 'help' pour l'aide.");
        }
    }

    private void listFiles() {
        File storage = new File(storagePath);
        System.out.println("\n=== Fichiers Stockés ===");
        File[] files = storage.listFiles();
        if (files != null && files.length > 0) {
            for (File file : files) {
                System.out.printf("- %s (%d bytes)%n", file.getName(), file.length());
            }
        } else {
            System.out.println("Aucun fichier stocké");
        }
    }

    private void stopServer() {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            log("🛑 Serveur arrêté");
            System.exit(0);
        } catch (IOException e) {
            log("❌ Erreur lors de l'arrêt: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new SecondaryServer2();
    }
}