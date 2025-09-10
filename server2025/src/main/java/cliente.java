import java.io.;
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

            String mensajeServidor;

            // Bucle para el proceso de login
            while (true) {
                mensajeServidor = entrada.readLine();
                if (mensajeServidor == null) break;
                System.out.println(mensajeServidor);

                if (mensajeServidor.startsWith("¡Inicio de sesion exitoso!")) {
                    break; // Salir del bucle de login
                }

                System.out.print("> ");
                String respuesta = scanner.nextLine();
                salida.println(respuesta);

                // Lógica para crear una cuenta
                if (respuesta.equalsIgnoreCase("nuevo")) {
                    mensajeServidor = entrada.readLine();
                    if (mensajeServidor == null) break;
                    System.out.println(mensajeServidor);
                    System.out.print("> ");
                    String nuevoUsuario = scanner.nextLine();
                    salida.println(nuevoUsuario);

                    mensajeServidor = entrada.readLine();
                    if (mensajeServidor == null) break;
                    System.out.println(mensajeServidor);
                    System.out.print("> ");
                    String nuevaContrasena = scanner.nextLine();
                    salida.println(nuevaContrasena);

                    mensajeServidor = entrada.readLine();
                    if (mensajeServidor == null) break;
                    System.out.println(mensajeServidor);
                }
            }

            // Bucle para el juego de adivinanza
            while ((mensajeServidor = entrada.readLine()) != null) {
                System.out.println(mensajeServidor);

                if (mensajeServidor.startsWith("¡ADIVINASTE") || mensajeServidor.startsWith("NO ADIVINASTE")) {
                    String preguntaReinicio = entrada.readLine();
                    if (preguntaReinicio == null) break;
                    System.out.println(preguntaReinicio);
                    System.out.print("> ");
                    String decision = scanner.nextLine();
                    salida.println(decision);
                    if (decision.equalsIgnoreCase("no")) {
                        String mensajeFinal = entrada.readLine();
                        if (mensajeFinal != null) {
                            System.out.println(mensajeFinal);
                        }
                        break;
                    }
                } else if (mensajeServidor.startsWith("El numero es") || mensajeServidor.startsWith("Bienvenido. Adivina") || mensajeServidor.startsWith("Caracter no valido")) {
                    System.out.print("> ");
                    String intento = scanner.nextLine();
                    salida.println(intento);
                }
            }

            System.out.println("Conexion terminada.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
