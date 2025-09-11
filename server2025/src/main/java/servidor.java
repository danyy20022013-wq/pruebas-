import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

public class servidor {

    // Enum para controlar los estados del servidor
    enum Estado {
        INICIO,
        RECIBIR_USUARIO,
        RECIBIR_CONTRASENA,
        AUTENTICACION_EXITOSA,
        CREAR_CUENTA,
        AUTENTICACION_FALLIDA,
        MENU_PRINCIPAL,
        INICIO_JUEGO,
        JUGANDO,
        GANASTE,
        PERDISTE,
        PREGUNTAR_REINICIO,
        CHAT,
        FIN
    }

    // Archivo donde se guardan las credenciales
    private static final String ARCHIVO_CREDENCIALES = "credenciales.txt";
    private static final Map<String, String> credenciales = new HashMap<>();

    public static void main(String[] args) {
        cargarCredenciales();
        int puerto = 5050;

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado. Esperando cliente...");

            Socket socket = servidor.accept();
            System.out.println("Cliente conectado.");

            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            Scanner scannerServidor = new Scanner(System.in); // Para que el servidor pueda escribir mensajes

            Estado estado = Estado.INICIO;
            String usuarioActual = "";
            int numeroSecreto = 0;
            int intentos = 0;
            Random rand = new Random();

            while (estado != Estado.FIN) {
                switch (estado) {
                    case INICIO:
                        salida.println("Bienvenido. Ingresa tu usuario o escribe 'nuevo' para crear una cuenta.");
                        estado = Estado.RECIBIR_USUARIO;
                        break;

                    case RECIBIR_USUARIO:
                        usuarioActual = entrada.readLine();
                        if (usuarioActual == null) {
                            estado = Estado.FIN;
                            break;
                        }
                        if (usuarioActual.equalsIgnoreCase("nuevo")) {
                            salida.println("Ingresa tu nuevo nombre de usuario:");
                            estado = Estado.CREAR_CUENTA;
                        } else if (credenciales.containsKey(usuarioActual)) {
                            salida.println("Usuario recibido. Ahora, ingresa tu contrasena.");
                            estado = Estado.RECIBIR_CONTRASENA;
                        } else {
                            salida.println("Usuario no encontrado. Por favor, intenta de nuevo.");
                        }
                        break;

                    case RECIBIR_CONTRASENA:
                        String contrasena = entrada.readLine();
                        if (contrasena == null) {
                            estado = Estado.FIN;
                            break;
                        }
                        if (credenciales.get(usuarioActual).equals(contrasena)) {
                            estado = Estado.AUTENTICACION_EXITOSA;
                        } else {
                            estado = Estado.AUTENTICACION_FALLIDA;
                        }
                        break;

                    case CREAR_CUENTA:
                        salida.println("Ingresa tu nuevo nombre de usuario:");
                        String nuevoUsuario = entrada.readLine();
                        salida.println("Ingresa la contrasena para " + nuevoUsuario + ":");
                        String nuevaContrasena = entrada.readLine();

                        if (nuevoUsuario != null && nuevaContrasena != null && !credenciales.containsKey(nuevoUsuario)) {
                            credenciales.put(nuevoUsuario, nuevaContrasena);
                            guardarCredenciales();
                            salida.println("¡Cuenta creada exitosamente! Ahora puedes iniciar sesion.");
                            estado = Estado.INICIO;
                        } else {
                            salida.println("Error al crear la cuenta. El usuario ya existe o los datos son invalidos.");
                            estado = Estado.INICIO;
                        }
                        break;

                    case AUTENTICACION_FALLIDA:
                        salida.println("Contrasena incorrecta. Intentalo de nuevo.");
                        estado = Estado.INICIO;
                        break;

                    case AUTENTICACION_EXITOSA:
                        salida.println("¡Inicio de sesion exitoso! Bienvenido, " + usuarioActual + ".");
                        estado = Estado.MENU_PRINCIPAL;
                        break;

                    case MENU_PRINCIPAL:
                        salida.println("MENU PRINCIPAL\n1. Jugar a adivinar el numero\n2. Chatear\n\nIngresa tu eleccion (1 o 2):");
                        String eleccion = entrada.readLine();
                        if (eleccion == null) {
                            estado = Estado.FIN;
                            break;
                        }

                        if (eleccion.equals("1")) {
                            estado = Estado.INICIO_JUEGO;
                        } else if (eleccion.equals("2")) {
                            estado = Estado.CHAT;
                        } else {
                            salida.println("Opcion no valida. Por favor, elige 1 o 2.");
                        }
                        break;

                    // Lógica del juego
                    case INICIO_JUEGO:
                        numeroSecreto = rand.nextInt(10) + 1;
                        intentos = 0;
                        salida.println("INICIANDO JUEGO\nAdivina un numero del 1 al 10. Tienes 3 intentos.");
                        estado = Estado.JUGANDO;
                        break;

                    case JUGANDO:
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
                            continue; // No consume un intento si el caracter es inválido
                        }
                        if (numero < 1 || numero > 10) {
                            salida.println("El número debe estar entre 1 y 10.");
                            continue; // No consume un intento si el número está fuera de rango
                        }
                        intentos++;
                        if (numero == numeroSecreto) {
                            estado = Estado.GANASTE;
                        } else if (intentos >= 3) {
                            estado = Estado.PERDISTE;
                        } else if (numero < numeroSecreto) {
                            salida.println("El número es mayor. Te quedan " + (3 - intentos) + " intentos.");
                        } else { // numero > numeroSecreto
                            salida.println("El número es menor. Te quedan " + (3 - intentos) + " intentos.");
                        }
                        break;

                    case GANASTE:
                        salida.println("¡ADIVINASTE! El numero era: " + numeroSecreto);
                        estado = Estado.PREGUNTAR_REINICIO;
                        break;

                    case PERDISTE:
                        salida.println("NO ADIVINASTE. El numero correcto era: " + numeroSecreto);
                        estado = Estado.PREGUNTAR_REINICIO;
                        break;

                    case PREGUNTAR_REINICIO:
                        salida.println("¿Quieres volver al menu principal? (si/no)");
                        String respuestaReinicio = entrada.readLine();
                        if (respuestaReinicio == null || respuestaReinicio.equalsIgnoreCase("no")) {
                            salida.println("Adios, " + usuarioActual + ". Gracias por jugar.");
                            estado = Estado.FIN;
                        } else {
                            estado = Estado.MENU_PRINCIPAL;
                        }
                        break;

                    // Lógica del chat
                    case CHAT:
                        salida.println("INICIANDO CHAT\nEscribe tus mensajes. Escribe 'salir' para volver al menu.");
                        boolean chatActivo = true;
                        while(chatActivo) {
                            try {
                                // Leer mensaje del cliente
                                if (entrada.ready()) {
                                    String mensajeCliente = entrada.readLine();
                                    if (mensajeCliente == null || mensajeCliente.equalsIgnoreCase("salir")) {
                                        salida.println("Regresando al menu principal.");
                                        chatActivo = false;
                                        estado = Estado.MENU_PRINCIPAL;
                                        break;
                                    }
                                    System.out.println("Cliente: " + mensajeCliente);
                                }

                                // Leer mensaje del servidor para enviar
                                if (System.in.available() > 0) {
                                    String mensajeServidor = scannerServidor.nextLine();
                                    if (mensajeServidor.equalsIgnoreCase("salir")) {
                                        salida.println("Servidor ha terminado el chat. Regresando al menu principal.");
                                        chatActivo = false;
                                        estado = Estado.MENU_PRINCIPAL;
                                        break;
                                    }
                                    salida.println("Servidor: " + mensajeServidor);
                                }

                                Thread.sleep(100); // Pausa para evitar un bucle de CPU intensivo
                            } catch (IOException | InterruptedException e) {
                                chatActivo = false;
                                estado = Estado.FIN;
                                System.err.println("Error en el chat: " + e.getMessage());
                            }
                        }
                        break;

                    default:
                        estado = Estado.FIN;
                }
            }
            socket.close();
            System.out.println("Conexion cerrada.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void cargarCredenciales() {
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_CREDENCIALES))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.trim().isEmpty()) continue; // evita errores por líneas vacías
                String[] partes = linea.split(":");
                if (partes.length == 2) {
                    credenciales.put(partes[0].trim(), partes[1].trim());
                }
            }
            System.out.println("Credenciales cargadas desde " + ARCHIVO_CREDENCIALES);
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de credenciales no encontrado. Se creará uno nuevo.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void guardarCredenciales() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCHIVO_CREDENCIALES))) {
            for (Map.Entry<String, String> entry : credenciales.entrySet()) {
                bw.write(entry.getKey() + ":" + entry.getValue());
                bw.newLine();
            }
            System.out.println("Credenciales guardadas en " + ARCHIVO_CREDENCIALES);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
