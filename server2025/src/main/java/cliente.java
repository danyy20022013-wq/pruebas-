import java.io.*;
import java.net.*;
import java.util.*;

public class cliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5050;

    public static void main(String[] args) {
        try (Socket socket = new Socket(HOST, PUERTO);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            // Hilo para escuchar al servidor
            Thread escuchar = new Thread(() -> {
                try {
                    String mensaje;
                    while ((mensaje = entrada.readLine()) != null) {
                        if (mensaje.startsWith("[NOTIFICACION]")) {
                            System.out.println("🔔 " + mensaje);
                        } else {
                            System.out.println(mensaje);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Desconectado del servidor.");
                }
            });
            escuchar.setDaemon(true);
            escuchar.start();

            // Entrada de usuario
            while (true) {
                String mensaje = scanner.nextLine();
                salida.println(mensaje);
                if (mensaje.equalsIgnoreCase("salir")) break;
            }

        } catch (IOException e) {
            System.out.println("No se pudo conectar al servidor.");
        }
    }
}
