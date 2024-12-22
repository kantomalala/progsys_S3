import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class ClientConsole {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 5000;
    private static String currentDirectory;
    private static final String DOWNLOAD_DIR = System.getProperty("user.home") + File.separator + "Téléchargements";
    private static final Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        currentDirectory = System.getProperty("user.home");
        
        System.out.println("Client de transfert de fichiers démarré");
        System.out.println("Connecté à " + SERVER_IP + ":" + SERVER_PORT);
        
        boolean running = true;
        while (running) {
            System.out.print("clientconsole..." + currentDirectory + "> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) continue;
            
            // Vérifier si la commande se termine par un point-virgule
            if (!input.endsWith(";")) {
                System.out.println("Erreur: La commande doit se terminer par un point-virgule (;)");
                continue;
            }
            
            // Enlever le point-virgule
            String command = input.substring(0, input.length() - 1).trim();
            
            String[] parts = command.split("\\s+");
            switch (parts[0].toLowerCase()) {
                case "liste":
                    if (parts.length > 1 && parts[1].equals("stockage")) {
                        listServerFiles();
                    } else {
                        listLocalDirectory();
                    }
                    break;
                    
                case "put":
                    if (parts.length < 2) {
                        System.out.println("Usage: put <fichier>;");
                    } else {
                        uploadFile(parts[1]);
                    }
                    break;
                    
                case "get":
                    if (parts.length < 2) {
                        System.out.println("Usage: get <fichier>;");
                    } else {
                        downloadFile(parts[1]);
                    }
                    break;
                    
                case "rm":
                    if (parts.length < 2) {
                        System.out.println("Usage: rm <fichier>;");
                    } else {
                        if (deleteFile(parts[1])) {
                            System.out.println("Fichier supprimé avec succès: " + parts[1]);
                        }
                    }
                    break;
                    
                case "cd":
                    if (parts.length < 2) {
                        System.out.println("Usage: cd <répertoire>;");
                    } else {
                        changeDirectory(parts[1]);
                    }
                    break;
                    
                case "help":
                    showHelp();
                    break;
                    
                case "deconnexion":
                    running = false;
                    System.out.println("Au revoir!");
                    break;
                    
                default:
                    System.out.println("Commande inconnue. Tapez 'help;' pour voir les commandes disponibles.");
            }
        }
        scanner.close();
    }
    
    private static void changeDirectory(String dir) {
        String newPath;
        if (dir.equals("..")) {
            Path parent = Paths.get(currentDirectory).getParent();
            if (parent != null) {
                newPath = parent.toString();
            } else {
                System.out.println("Déjà à la racine");
                return;
            }
        } else {
            newPath = Paths.get(currentDirectory, dir).toString();
        }
        
        if (Files.isDirectory(Paths.get(newPath))) {
            currentDirectory = newPath;
        } else {
            System.out.println("Répertoire invalide: " + dir);
        }
    }

    private static void listServerFiles() {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("CLIENT");
            out.writeObject("LIST_FILES");
            
            @SuppressWarnings("unchecked")
            List<String> files = (List<String>) in.readObject();
            if (files.isEmpty()) {
                System.out.println("Aucun fichier sur le serveur");
            } else {
                System.out.println("Fichiers sur le serveur:");
                files.forEach(System.out::println);
            }
        } catch (Exception e) {
            System.out.println("Erreur de connexion au serveur: " + e.getMessage());
        }
    }
    private static void listLocalDirectory() {
        try {
            Files.list(Paths.get(currentDirectory))
                .forEach(path -> System.out.println(path.getFileName()));
        } catch (IOException e) {
            System.out.println("Erreur lors de la lecture du répertoire: " + e.getMessage());
        }
    }
    
    
    private static void uploadFile(String fileName) {
        Path filePath = Paths.get(currentDirectory, fileName);
        if (!Files.exists(filePath)) {
            System.out.println("Le fichier n'existe pas: " + fileName);
            return;
        }
        
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("CLIENT");
            out.writeObject("UPLOAD");
            out.writeObject(fileName);
            out.writeObject(Files.size(filePath));
            
            byte[] fileData = Files.readAllBytes(filePath);
            out.writeObject(fileData);
            
            System.out.println("Fichier envoyé avec succès: " + fileName);
        } catch (IOException e) {
            System.out.println("Erreur lors de l'envoi du fichier: " + e.getMessage());
        }
    }
    
    private static void downloadFile(String fileName) {
        try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("CLIENT");
            out.writeObject("DOWNLOAD");
            out.writeObject(fileName);
            
            byte[] fileData = (byte[]) in.readObject();
            if (fileData != null) {
                Path downloadPath = Paths.get(DOWNLOAD_DIR, fileName);
                Files.write(downloadPath, fileData);
                System.out.println("Fichier téléchargé avec succès dans: " + downloadPath);
            } else {
                System.out.println("Le fichier n'a pas pu être récupéré du serveur");
            }
        } catch (Exception e) {
            System.out.println("Erreur lors du téléchargement: " + e.getMessage());
        }
    }
    
    
    private static boolean deleteFile(String fileName) {
        Socket socket = null;
        ObjectOutputStream out = null;
        ObjectInputStream in = null;
        boolean success = false;
        
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            out.writeObject("CLIENT");
            out.writeObject("DELETE");
            out.writeObject(fileName);
            
            String response = (String) in.readObject();
            success = "SUCCESS".equals(response);
            
            if (!success) {
                System.out.println("Erreur lors de la suppression du fichier");
            }
        } catch (Exception e) {
            System.out.println("Erreur lors de la suppression: " + e.getMessage());
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                System.out.println("Erreur lors de la fermeture des connexions: " + e.getMessage());
            }
        }
        return success;
    }
    
    private static void showHelp() {
        System.out.println("Commandes disponibles (terminer chaque commande par ;):");
        System.out.println("  liste;            - Affiche le contenu du répertoire courant");
        System.out.println("  liste stockage;   - Affiche les fichiers stockés sur le serveur");
        System.out.println("  put <fichier>;    - Envoie un fichier vers le serveur");
        System.out.println("  get <fichier>;    - Télécharge un fichier du serveur");
        System.out.println("  rm <fichier>;     - Supprime un fichier du serveur");
        System.out.println("  cd <répertoire>;  - Change de répertoire");
        System.out.println("  cd ..;            - Retourne au répertoire parent");
        System.out.println("  help;             - Affiche cette aide");
        System.out.println("  deconnexion;      - Quitte le programme");
    }
}