import java.io.*;
import java.net.*;
import java.util.*;

public class servidor {
    private static final int PUERTO = 12345;
    private static final Set<PrintWriter> clientes = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("Servidor iniciado en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Nuevo cliente conectado: " + socket);
                new Thread(new ManejadorCliente(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ManejadorCliente implements Runnable {
        private Socket socket;
        private PrintWriter salida;
        private BufferedReader entrada;
        private String nombre;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                salida.println("Bienvenido! Ingresa tu nombre:");
                nombre = entrada.readLine();

                synchronized (clientes) {
                    clientes.add(salida);
                }
                broadcast("[NOTIFICACION] " + nombre + " se ha unido al chat.");

                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.equalsIgnoreCase("salir")) {
                        break;
                    }
                    broadcast(nombre + ": " + mensaje);
                }
            } catch (IOException e) {
                System.err.println("Error con cliente: " + e.getMessage());
            } finally {
                try { socket.close(); } catch (IOException e) {}
                synchronized (clientes) {
                    clientes.remove(salida);
                }
                broadcast("[NOTIFICACION] " + nombre + " salió del chat.");
            }
        }

        private void broadcast(String mensaje) {
            synchronized (clientes) {
                for (PrintWriter cliente : clientes) {
                    cliente.println(mensaje);
                }
            }
        }
    }
}
