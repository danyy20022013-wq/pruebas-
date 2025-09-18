import java.io.*;
import java.net.*;
import java.util.Scanner;

public class cliente {
    public static void main(String[] args) {
        String host = "localhost";
        int puerto = 5050;

        try (Socket socket = new Socket(host, puerto)) {
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            Thread listener = new Thread(() -> {
                try {
                    String msg;
                    while ((msg = entrada.readLine()) != null) {
                        System.out.println(msg);
                    }
                } catch (IOException e) {
                    // conexión cerrada
                }
            });
            listener.setDaemon(true);
            listener.start();

            while (true) {
                String input = scanner.nextLine();
                salida.println(input);
                if (input.equalsIgnoreCase("/eliminarme")) {
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
