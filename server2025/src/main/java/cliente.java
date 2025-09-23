

    import java.io.*;
import java.net.*;

        public class cliente {
            private static final String HOST = "127.0.0.1";
            private static final int PUERTO = 5050;

            public static void main(String[] args) {
                try (Socket socket = new Socket(HOST, PUERTO);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

                    Thread lector = new Thread(() -> {
                        String respuesta;
                        try {
                            while ((respuesta = in.readLine()) != null) {
                                System.out.println(respuesta);
                            }
                        } catch (IOException e) {
                            System.out.println("Conexión cerrada.");
                        }
                    });
                    lector.start();

                    String input;
                    while ((input = teclado.readLine()) != null) {
                        out.println(input);
                        if (input.equals("0") || input.equalsIgnoreCase("exit")) break;
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
