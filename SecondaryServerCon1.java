
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;



public class SecondaryServerCon1 {
    private int port;
    private String storagePath;
    private String mainServerIp;
    private int mainServerPort;
    private ServerSocket serverSocket;
    private volatile boolean isRunning = true;

    public SecondaryServerCon1(int port, String storagePath, String mainServerIp, int mainServerPort) {
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
        System.out.println("╔════════════════════════════════════════");
        System.out.println("║ Serveur Secondaire - Configuration");
        System.out.println("╠════════════════════════════════════════");
        System.out.println("║ Port: " + port);
        System.out.println("║ Stockage: " + storagePath);
        System.out.println("║ Serveur Principal: " + mainServerIp + ":" + mainServerPort);
        System.out.println("╚════════════════════════════════════════");
    }

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
        // Thread pour gérer les commandes console
        startCommandListener();

        // Thread principal du serveur
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
//handleConnection(
    private void startCommandListener() {
        Thread commandThread = new Thread(() -> {
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            while (isRunning) {
                try {
                    String command = consoleReader.readLine();
                    handleCommand(command);
                } catch (IOException e) {
                    log("❌ Erreur de lecture console: " + e.getMessage());
                }
            }
        });
        commandThread.setDaemon(true);
        commandThread.start();

        // Afficher les commandes disponibles
        System.out.println("\nCommandes disponibles:");
        System.out.println("- help : Afficher cette aide");
        System.out.println("- status : Afficher l'état du serveur");
        System.out.println("- list : Lister les fichiers stockés");
        System.out.println("- stop : Arrêter le serveur");
    }

    private void handleCommand(String command) {
        switch (command.toLowerCase()) {
            case "help":
                System.out.println("\nCommandes disponibles:");
                System.out.println("- help : Afficher cette aide");
                System.out.println("- status : Afficher l'état du serveur");
                System.out.println("- list : Lister les fichiers stockés");
                System.out.println("- stop : Arrêter le serveur");
                break;

            case "status":
                System.out.println("\n=== État du Serveur ===");
                System.out.println("Port: " + port);
                System.out.println("Stockage: " + storagePath);
                System.out.println("Connexion au serveur principal: " + mainServerIp + ":" + mainServerPort);
                System.out.println("État: " + (isRunning ? "En cours d'exécution" : "Arrêté"));
                break;

            case "list":
                File storage = new File(storagePath);
                System.out.println("\n=== Fichiers Stockés ===");
                File[] files = storage.listFiles();
                if (files != null && files.length > 0) {
                    for (File file : files) {
                        System.out.println("- " + file.getName() + " (" + formatFileSize(file.length()) + ")");
                    }
                } else {
                    System.out.println("Aucun fichier stocké");
                }
                break;

            case "stop":
                stopServer();
                break;

            default:
                System.out.println("Commande inconnue. Tapez 'help' pour voir les commandes disponibles.");
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

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

    private void stopServer() {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            log("🛑 Serveur arrêté");
            System.exit(0);
        } catch (IOException e) {
            log("❌ Erreur lors de l'arrêt du serveur: " + e.getMessage());
        }
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java SecondaryServer <port> <storagePath> <mainServerIp> <mainServerPort>");
            System.out.println("Example: java SecondaryServer 5001 storage/secondary1 localhost 5000");
            return;
        }
//start
        try {
            int port = Integer.parseInt(args[0]);
            String storagePath = args[1];
            String mainServerIp = args[2];
            int mainServerPort = Integer.parseInt(args[3]);

            new SecondaryServerCon1(port, storagePath, mainServerIp, mainServerPort);
        } catch (NumberFormatException e) {
            System.out.println("Erreur: Les ports doivent être des nombres");
        }
    }
    
}