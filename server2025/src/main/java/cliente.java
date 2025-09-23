import java.io.*;
import java.net.*;
import java.util.Scanner;

public class cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5050;

    public static void main(String[] args) {
        while (true) { // permite reconectar/volver a login cuando el servidor cierre la sesión
            try (Socket socket = new Socket(HOST, PUERTO);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 Scanner scanner = new Scanner(System.in)) {

                Thread lector = new Thread(() -> {
                    try {
                        String linea;
                        while ((linea = in.readLine()) != null) {
                            System.out.println(linea);
                        }
                    } catch (IOException e) {
                        // desconectado
                    }
                });
                lector.setDaemon(true);
                lector.start();

                // leer de teclado y enviar
                while (true) {
                    String entrada = scanner.nextLine();
                    if (entrada == null) break;
                    out.println(entrada);
                    // si el servidor cierra la sesión, el servidor cerrará el socket y el lector terminará.
                }
            } catch (IOException e) {
                System.out.println("Conexión con servidor cerrada. Reintentando en 1s...");
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
        }
    }
}
