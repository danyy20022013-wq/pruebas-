import java.io.*;
import java.net.*;
import java.util.Random;

public class servidor {

    enum Estado {
        INICIO,
        JUGANDO,
        GANASTE,
        PERDISTE,
        PREGUNTAR_REINICIO,
        FIN
    }

    public static void main(String[] args) {
        int puerto = 5000;
        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado. Esperando cliente...");

            Socket socket = servidor.accept();
            System.out.println("Cliente conectado.");

            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);

            Random rand = new Random();
            boolean seguirJugando = true;

            while (seguirJugando) {
                int secreto = rand.nextInt(10) + 1;
                int intentos = 0;
                Estado estado = Estado.INICIO;

                while (estado != Estado.FIN && estado != Estado.PREGUNTAR_REINICIO) {
                    switch (estado) {
                        case INICIO:
                            salida.println("Bienvenido. Adivina un numero del 1 al 10. Tienes 3 intentos.");
                            estado = Estado.JUGANDO;
                            break;

                        case JUGANDO:
                            if (intentos >= 3) {
                                estado = Estado.PERDISTE;
                                break;
                            }

                            String mensaje = entrada.readLine();
                            if (mensaje == null) {
                                estado = Estado.FIN;
                                break;
                            }

                            int numero;
                            try {
                                numero = Integer.parseInt(mensaje);
                            } catch (NumberFormatException e) {
                                salida.println("Caracter no válido. Ingresa un numero entre 1 y 10.");
                                continue; // no gasta intento
                            }

                            if (numero < 1 || numero > 10) {
                                salida.println("El numero debe estar entre 1 y 10.");
                                continue; // no gasta intento
                            }

                            intentos++; // solo aquí gastamos intento válido

                            if (numero == secreto) {
                                estado = Estado.GANASTE;
                            } else if (numero < secreto) {
                                if (intentos < 3) {
                                    salida.println("El numero es mayor. Intentos restantes: " + (3 - intentos));
                                } else {
                                    estado = Estado.PERDISTE;
                                }
                            } else {
                                if (intentos < 3) {
                                    salida.println("El numero es menor. Intentos restantes: " + (3 - intentos));
                                } else {
                                    estado = Estado.PERDISTE;
                                }
                            }
                            break;

                        case GANASTE:
                            salida.println("¡ADIVINASTE! El numero era: " + secreto);
                            estado = Estado.PREGUNTAR_REINICIO;
                            break;

                        case PERDISTE:
                            salida.println("NO ADIVINASTE. El numero correcto era: " + secreto);
                            estado = Estado.PREGUNTAR_REINICIO;
                            break;

                        default:
                            estado = Estado.FIN;
                    }
                }

                salida.println("¿Quieres jugar otra vez? (si/no)");
                String respuesta = entrada.readLine();
                if (respuesta == null || respuesta.equalsIgnoreCase("no")) {
                    salida.println("Adiós, gracias por jugar.");
                    seguirJugando = false;
                }
            }

            socket.close();
            System.out.println("Conexión cerrada.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
