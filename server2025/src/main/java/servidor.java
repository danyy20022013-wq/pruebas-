import java.io.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class servidor {

    enum Estado {
        INICIO,
        RECIBIR_USUARIO,
        RECIBIR_CONTRASENA,
        AUTENTICACION_EXITOSA,
        CREAR_CUENTA,
        AUTENTICACION_FALLIDA,
        // Nuevos estados para el juego
        INICIO_JUEGO,
        JUGANDO,
        GANASTE,
        PERDISTE,
        PREGUNTAR_REINICIO,
        FIN
    }

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

                    // ... (Casos de login y creacion de cuenta, sin cambios) ...
                    case RECIBIR_USUARIO:
                        usuarioActual = entrada.readLine();
                        if (usuarioActual == null) { estado = Estado.FIN; break; }
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
                        if (contrasena == null) { estado = Estado.FIN; break; }
                        if (credenciales.get(usuarioActual).equals(contrasena)) {
                            estado = Estado.AUTENTICACION_EXITOSA;
                        } else {
                            estado = Estado.AUTENTICACION_FALLIDA;
                        }
                        break;

                    case CREAR_CUENTA:
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
                        estado = Estado.INICIO_JUEGO;
                        break;

                    // Lógica del juego
                    case INICIO_JUEGO:
                        numeroSecreto = rand.nextInt(10) + 1;
                        intentos = 0;
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
                            salida.println("Caracter no valido. Ingresa un numero entre 1 y 10.");
                            continue;
                        }
                        if (numero < 1 || numero > 10) {
                            salida.println("El numero debe estar entre 1 y 10.");
                            continue;
                        }
                        intentos++;
                        if (numero == numeroSecreto) {
                            estado = Estado.GANASTE;
                        } else if (numero < numeroSecreto) {
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
                        salida.println("¡ADIVINASTE! El numero era: " + numeroSecreto);
                        salida.println("¿Quieres jugar otra vez? (si/no)");
                        estado = Estado.PREGUNTAR_REINICIO;
                        break;

                    case PERDISTE:
                        salida.println("NO ADIVINASTE. El numero correcto era: " + numeroSecreto);
                        salida.println("¿Quieres jugar otra vez? (si/no)");
                        estado = Estado.PREGUNTAR_REINICIO;
                        break;

                    case PREGUNTAR_REINICIO:
                        String respuestaReinicio = entrada.readLine();
                        if (respuestaReinicio == null || respuestaReinicio.equalsIgnoreCase("no")) {
                            salida.println("Adios, " + usuarioActual + ". Gracias por jugar.");
                            estado = Estado.FIN;
                        } else {
                            estado = Estado.INICIO_JUEGO;
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

    // Métodos cargarCredenciales() y guardarCredenciales() sin cambios
    private static void cargarCredenciales() {
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_CREDENCIALES))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":");
                if (partes.length == 2) {
                    credenciales.put(partes[0].trim(), partes[1].trim());
                }
            }
            System.out.println("Credenciales cargadas desde " + ARCHIVO_CREDENCIALES);
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de credenciales no encontrado. Se creara uno nuevo.");
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
