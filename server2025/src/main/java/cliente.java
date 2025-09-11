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

            // Bucle principal para manejar el menu y las opciones después del login
            while (true) {
                mensajeServidor = entrada.readLine();
                if (mensajeServidor == null || mensajeServidor.startsWith("Adios,")) break;
                System.out.println(mensajeServidor);

                if (mensajeServidor.startsWith("MENU PRINCIPAL")) {
                    System.out.print("> ");
                    String eleccion = scanner.nextLine();
                    salida.println(eleccion);

                    if (eleccion.equals("1")) {
                        handleGame(entrada, salida, scanner);
                    } else if (eleccion.equals("2")) {
                        handleChat(entrada, salida, scanner);
                    }
                }
            }

            System.out.println("Conexion terminada.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleGame(BufferedReader entrada, PrintWriter salida, Scanner scanner) throws IOException {
        String mensajeServidor;
        while (true) {
            mensajeServidor = entrada.readLine();
            if (mensajeServidor == null) return;
            System.out.println(mensajeServidor);

            if (mensajeServidor.startsWith("¡ADIVINASTE") || mensajeServidor.startsWith("NO ADIVINASTE")) {
                // Fin del juego, lee la pregunta para reiniciar y la maneja
                mensajeServidor = entrada.readLine();
                if (mensajeServidor == null) return;
                System.out.println(mensajeServidor);
                System.out.print("> ");
                String decision = scanner.nextLine();
                salida.println(decision);
                if (decision.equalsIgnoreCase("no")) {
                    // Lee el mensaje final del servidor y sale del bucle del juego
                    entrada.readLine();
                    return; // Salir al bucle principal
                } else {
                    return; // Salir al bucle principal
                }
            } else if (mensajeServidor.startsWith("INICIANDO JUEGO") || mensajeServidor.startsWith("El numero es") || mensajeServidor.startsWith("Caracter no valido") || mensajeServidor.startsWith("El numero debe estar")) {
                // El juego está en progreso, pide un número
                System.out.print("> ");
                String intento = scanner.nextLine();
                salida.println(intento);
            }
        }
    }


    private static void handleChat(BufferedReader entrada, PrintWriter salida, Scanner scanner) throws IOException {
        String mensajeServidor = entrada.readLine(); // Lee el mensaje "INICIANDO CHAT"
        if (mensajeServidor == null) return;
        System.out.println(mensajeServidor);

        // Hilo separado para escuchar mensajes del servidor
        Thread listenThread = new Thread(() -> {
            try {
                String serverMsg;
                while ((serverMsg = entrada.readLine()) != null) {
                    System.out.println(serverMsg);
                    if (serverMsg.startsWith("Servidor ha terminado el chat")) {
                        break;
                    }
                }
            } catch (IOException e) {
                // El hilo principal manejará el mensaje final.
            }
        });
        listenThread.setDaemon(true); // Permite que el programa se cierre si este hilo está en ejecución
        listenThread.start();

        // Hilo principal para leer la entrada del usuario y enviar mensajes al servidor
        while (true) {
            System.out.print("Tú: ");
            String mensajeAEnviar = scanner.nextLine();
            salida.println(mensajeAEnviar);
            if (mensajeAEnviar.equalsIgnoreCase("salir")) {
                System.out.println("Regresando al menu principal.");
                break;
            }
        }
    }
}
