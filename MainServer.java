import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.nio.file.*;
import java.text.SimpleDateFormat;



public class MainServer extends JFrame {
    private List<SecondaryServerInfo> secondaryServers;
    private JTextArea serverLog;
    private JTable serversTable;
    private DefaultTableModel tableModel;
    private JButton controlButton;
    private int mainServerPort = 5000;
    private ServerSocket serverSocket;
    private File storageFile;
    private boolean isServerRunning = false;
    private Thread serverThread;

    public MainServer() {
        secondaryServers = new ArrayList<>();
        storageFile = new File("storage.txt");
        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        setupGUI();
    }

    private void setupGUI() {
        setTitle("Serveur Principal");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Panneau principal
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panneau de contrôle
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlButton = new JButton("Démarrer le serveur");
        controlButton.setPreferredSize(new Dimension(150, 40));
        updateControlButton();
        controlButton.addActionListener(e -> toggleServer());
        controlPanel.add(controlButton);

        // Panneau central
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 10));

        // Configuration du tableau
        String[] columns = {"ID", "Adresse IP", "Port", "Chemin de stockage", "État"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        serversTable = new JTable(tableModel);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Serveurs Secondaires Connectés"));
        tablePanel.add(new JScrollPane(serversTable), BorderLayout.CENTER);

        // Zone de logs
        serverLog = new JTextArea(10, 40);
        serverLog.setEditable(false);
        serverLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Journal des Opérations"));
        logPanel.add(new JScrollPane(serverLog), BorderLayout.CENTER);

        centerPanel.add(tablePanel);
        centerPanel.add(logPanel);

        mainPanel.add(controlPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        add(mainPanel);
        setLocationRelativeTo(null);
    }

    private void updateControlButton() {
        if (isServerRunning) {
            controlButton.setText("Arrêter le serveur");
            controlButton.setBackground(new Color(255, 77, 77));
            controlButton.setForeground(Color.WHITE);
        } else {
            controlButton.setText("Démarrer le serveur");
            controlButton.setBackground(new Color(77, 255, 77));
            controlButton.setForeground(Color.BLACK);
        }
        controlButton.setFocusPainted(false);
        controlButton.setOpaque(true);
        controlButton.setBorderPainted(false);
    }

    private void toggleServer() {
        if (isServerRunning) {
            stopServer();
        } else {
            startServer();
        }
        isServerRunning = !isServerRunning;
        updateControlButton();
    }

    private void setupServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(mainServerPort);
                updateLog("🚀 Serveur démarré sur le port " + mainServerPort);

                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    handleNewConnection(clientSocket);
                }
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    updateLog("❌ Erreur serveur: " + e.getMessage());
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void startServer() {
        if (serverSocket == null || serverSocket.isClosed()) {
            setupServer();
        }
    }

    private void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                updateLog("🛑 Serveur arrêté");
                clearServersTable();
            }
        } catch (IOException e) {
            updateLog("❌ Erreur lors de l'arrêt du serveur: " + e.getMessage());
        }
    }

    private void clearServersTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            secondaryServers.clear();
        });
    }

    private void handleNewConnection(Socket socket) {
        Thread connectionHandler = new Thread(() -> {
            try {
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

                String connectionType = (String) in.readObject();

                if (connectionType.equals("SECONDARY_SERVER")) {
                    handleSecondaryServer(socket, in, out);
                } else if (connectionType.equals("CLIENT")) {
                    handleClient(socket, in, out);
                }
            } catch (Exception e) {
                updateLog("❌ Erreur de connexion: " + e.getMessage());
            }
        });
        connectionHandler.start();
    }

    private void handleSecondaryServer(Socket socket, ObjectInputStream in, ObjectOutputStream out) 
            throws IOException, ClassNotFoundException {
        SecondaryServerInfo serverInfo = (SecondaryServerInfo) in.readObject();
        serverInfo.setId(secondaryServers.size() + 1);
        secondaryServers.add(serverInfo);
        updateServersTable();
        updateLog("🖥️ Nouveau serveur secondaire connecté: ID=" + serverInfo.getId() + 
                 "\n   └─ IP: " + serverInfo.getIp() + 
                 "\n   └─ Port: " + serverInfo.getPort() + 
                 "\n   └─ Stockage: " + serverInfo.getStoragePath());
    }

    private void handleClient(Socket socket, ObjectInputStream in, ObjectOutputStream out) 
            throws IOException, ClassNotFoundException {
        String action = (String) in.readObject();

        switch (action) {
            case "UPLOAD":
                handleFileUpload(in);
                break;
            case "DOWNLOAD":
                handleFileDownload(out, (String) in.readObject());
                break;
            case "DELETE":
                handleFileDelete((String) in.readObject());
                break;
            case "LIST_FILES":
                sendFileList(out);
                break;
        }
    }

    private void handleFileUpload(ObjectInputStream in) throws IOException, ClassNotFoundException {
        String fileName = (String) in.readObject();
        long fileSize = (Long) in.readObject();
        byte[] fileData = (byte[]) in.readObject();
        updateLog("📤 Téléchargement du fichier: " + fileName);

        int numPartitions = secondaryServers.size();
        if (numPartitions == 0) {
            updateLog("❌ Aucun serveur secondaire disponible pour le stockage");
            return;
        }

        int partitionSize = (int) Math.ceil(fileData.length / (double) numPartitions);
        
        for (int i = 0; i < numPartitions; i++) {
            SecondaryServerInfo server = secondaryServers.get(i);
            int start = i * partitionSize;
            int end = Math.min(start + partitionSize, fileData.length);
            byte[] partition = Arrays.copyOfRange(fileData, start, end);
            
            sendPartitionToSecondary(server, fileName + ".part" + (i+1), partition);
        }

        saveToStorage(fileName, fileSize, numPartitions);
        updateLog("✅ Fichier " + fileName + " téléchargé et partitionné avec succès");
    }
    private void handleFileDownload(ObjectOutputStream out, String fileName) throws IOException {
        try {
            List<String> partitionPaths = new ArrayList<>();
            int numPartitions = 0;
            updateLog("📥 Téléchargement demandé: " + fileName);

            try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
                String line;
                boolean foundFile = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(fileName + ";")) {
                        foundFile = true;
                        String[] parts = line.split(";");
                        numPartitions = Integer.parseInt(parts[2]);
                        
                        for (int i = 0; i < numPartitions; i++) {
                            partitionPaths.add(reader.readLine());
                        }
                        break;
                    }
                }
                
                if (!foundFile) {
                    throw new IOException("Fichier non trouvé");
                }
            }

            ByteArrayOutputStream combinedFile = new ByteArrayOutputStream();
            for (String partitionPath : partitionPaths) {
                byte[] partitionData = Files.readAllBytes(Paths.get(partitionPath));
                combinedFile.write(partitionData);
            }

            out.writeObject(combinedFile.toByteArray());
            updateLog("✅ Fichier " + fileName + " envoyé avec succès");
        } catch (IOException e) {
            updateLog("❌ Erreur lors du téléchargement: " + e.getMessage());
            out.writeObject(null);
        }
    }

    private void handleFileDelete(String fileName) {
        try {
            List<String> lines = new ArrayList<>(Files.readAllLines(storageFile.toPath()));
            List<String> updatedLines = new ArrayList<>();
            boolean found = false;
            updateLog("🗑️ Suppression demandée: " + fileName);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith(fileName + ";")) {
                    found = true;
                    String[] parts = line.split(";");
                    int numPartitions = Integer.parseInt(parts[2]);
                    i++; // Skip the next numPartitions lines
                    for (int j = 0; j < numPartitions && i < lines.size(); j++, i++) {
                        String partitionPath = lines.get(i);
                        Files.deleteIfExists(Paths.get(partitionPath));
                        replicateDeletion(partitionPath);
                    }
                    i--; // Adjust for the loop increment
                } else if (!found || !line.contains(fileName)) {
                    updatedLines.add(line);
                }
            }

            Files.write(storageFile.toPath(), updatedLines);
            updateLog("✅ Fichier supprimé avec succès: " + fileName);
        } catch (IOException e) {
            updateLog("❌ Erreur lors de la suppression: " + e.getMessage());
        }
    }

    private void sendPartitionToSecondary(SecondaryServerInfo server, String partitionName, byte[] partitionData) {
        try (Socket socket = new Socket(server.getIp(), server.getPort());
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            
            out.writeObject("STORE_PARTITION");
            out.writeObject(partitionName);
            out.writeObject(partitionData);
            
            updateLog("📦 Partition " + partitionName + " envoyée au serveur " + server.getId());
        } catch (IOException e) {
            updateLog("❌ Erreur lors de l'envoi de la partition au serveur " + server.getId() + ": " + e.getMessage());
        }
    }

    private void replicateDeletion(String partitionPath) {
        for (SecondaryServerInfo server : secondaryServers) {
            try (Socket socket = new Socket(server.getIp(), server.getPort());
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                out.writeObject("DELETE_PARTITION");
                out.writeObject(partitionPath);
                updateLog("🗑️ Réplication de suppression sur le serveur " + server.getId());

            } catch (IOException e) {
                updateLog("❌ Erreur de réplication de suppression: " + e.getMessage());
            }
        }
    }

    private void saveToStorage(String fileName, long fileSize, int numPartitions) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(storageFile, true))) {
            writer.println(fileName + ";" + fileSize + ";" + numPartitions);
            for (int i = 0; i < numPartitions; i++) {
                writer.println(secondaryServers.get(i).getStoragePath() + "/" + fileName + ".part" + (i+1));
            }
        } catch (IOException e) {
            updateLog("❌ Erreur d'enregistrement dans storage.txt: " + e.getMessage());
        }
    }

    private void sendFileList(ObjectOutputStream out) throws IOException {
        List<String> fileNames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(storageFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(";")) {
                    String fileName = line.split(";")[0];
                    fileNames.add(fileName);
                }
            }
        }
        out.writeObject(fileNames);
        updateLog("📋 Liste des fichiers envoyée au client");
    }

    private void updateServersTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (SecondaryServerInfo server : secondaryServers) {
                tableModel.addRow(new Object[]{
                    server.getId(),
                    server.getIp(),
                    server.getPort(),
                    server.getStoragePath(),
                    "Connecté ✅"
                });
            }
        });
    }

    private void updateLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            serverLog.append("[" + timestamp + "] " + message + "\n");
            serverLog.setCaretPosition(serverLog.getDocument().getLength());
        });
    }

    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        super.processWindowEvent(e);
    }

    public static void main(String[] args) {
        try {
            // Set System L&F
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            MainServer server = new MainServer();
            server.setVisible(true);
        });
    }
}

// Classe pour lancer le serveur
class ServerLauncher {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new MainServer().setVisible(true);
        });
    }
}