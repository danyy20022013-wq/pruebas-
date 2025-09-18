import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class servidor {

    // =====================
    // ENUM DE ESTADOS
    // =====================
    enum Estado {
        INICIO,
        RECIBIR_USUARIO,
        RECIBIR_CONTRASENA,
        CREAR_CUENTA,
        AUTENTICACION_EXITOSA,
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

    // =====================
    // VARIABLES GLOBALES
    // =====================
    private static final String ARCHIVO_CREDENCIALES = "credenciales.txt";
    private static final Map<String, String> credenciales = new HashMap<>();
    private static final int PUERTO = 5050;

    // Chat multicliente
    private static final Set<PrintWriter> clientes = Collections.synchronizedSet(new HashSet<>());
    private static final List<String> historial = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        cargarCredenciales();
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("Servidor iniciado en puerto " + PUERTO);
            while (true) {
                Socket socket = servidor.accept();
                System.out.println("Cliente conectado: " + socket);
                new Thread(new ManejadorSesion(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // =====================
    // MANEJADOR DE SESIÓN (LOGIN, JUEGO, MENÚ)
    // =====================
    private static class ManejadorSesion implements Runnable {
        private Socket socket;
        private BufferedReader entrada;
        private PrintWriter salida;
        private String usuarioActual = "";
        private Estado estado = Estado.INICIO;

        private int numeroSecreto;
        private int intentos;

        public ManejadorSesion(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

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
                            String nuevoUsuario = usuarioActual;
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
                                salida.println("Opcion no valida.");
                            }
                            break;

                        case INICIO_JUEGO:
                            numeroSecreto = rand.nextInt(10) + 1;
                            intentos = 0;
                            salida.println("INICIANDO JUEGO\nAdivina un numero del 1 al 10. Tienes 3 intentos.");
                            estado = Estado.JUGANDO;
                            break;

                        case JUGANDO:
                            String msg = entrada.readLine();
                            if (msg == null) {
                                estado = Estado.FIN;
                                break;
                            }
                            int numero;
                            try {
                                numero = Integer.parseInt(msg);
                            } catch (NumberFormatException e) {
                                salida.println("Caracter no válido. Ingresa un numero entre 1 y 10.");
                                continue;
                            }
                            if (numero < 1 || numero > 10) {
                                salida.println("El número debe estar entre 1 y 10.");
                                continue;
                            }
                            intentos++;
                            if (numero == numeroSecreto) {
                                estado = Estado.GANASTE;
                            } else if (intentos >= 3) {
                                estado = Estado.PERDISTE;
                            } else if (numero < numeroSecreto) {
                                salida.println("El número es mayor. Te quedan " + (3 - intentos) + " intentos.");
                            } else {
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
                            String resp = entrada.readLine();
                            if (resp == null || resp.equalsIgnoreCase("no")) {
                                salida.println("Adios, " + usuarioActual + ". Gracias por jugar.");
                                estado = Estado.FIN;
                            } else {
                                estado = Estado.MENU_PRINCIPAL;
                            }
                            break;

                        case CHAT:
                            manejarChat();
                            estado = Estado.MENU_PRINCIPAL;
                            break;

                        default:
                            estado = Estado.FIN;
                            break;
                    }
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // =====================
        // CHAT MULTICLIENTE
        // =====================
        private void manejarChat() {
            salida.println("INICIANDO CHAT\nEscribe mensajes. Usa 'borrar <id>' para eliminar uno o 'salir' para volver al menu.");

            synchronized (clientes) {
                clientes.add(salida);
            }
            broadcast("[NOTIFICACION] " + usuarioActual + " se unió al chat.");

            // enviar historial
            for (int i = 0; i < historial.size(); i++) {
                salida.println("#" + i + " " + historial.get(i));
            }

            try {
                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.equalsIgnoreCase("salir")) {
                        break;
                    }

                    if (mensaje.toLowerCase().startsWith("borrar")) {
                        String[] partes = mensaje.split(" ");
                        if (partes.length == 2) {
                            try {
                                int id = Integer.parseInt(partes[1]);
                                if (id >= 0 && id < historial.size()) {
                                    String borrado = historial.remove(id);
                                    broadcast("[NOTIFICACION] " + usuarioActual + " borró el mensaje #" + id + ": " + borrado);
                                } else {
                                    salida.println("[NOTIFICACION] ID inválido.");
                                }
                            } catch (NumberFormatException e) {
                                salida.println("[NOTIFICACION] Uso correcto: borrar <id>");
                            }
                        } else {
                            salida.println("[NOTIFICACION] Uso correcto: borrar <id>");
                        }
                    } else {
                        String msgCompleto = usuarioActual + ": " + mensaje;
                        historial.add(msgCompleto);
                        broadcast("#" + (historial.size() - 1) + " " + msgCompleto);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                synchronized (clientes) {
                    clientes.remove(salida);
                }
                broadcast("[NOTIFICACION] " + usuarioActual + " salió del chat.");
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

    // =====================
    // MANEJO DE CREDENCIALES
    // =====================
    private static void cargarCredenciales() {
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_CREDENCIALES))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                if (linea.trim().isEmpty()) continue;
                String[] partes = linea.split(":");
                if (partes.length == 2) {
                    credenciales.put(partes[0].trim(), partes[1].trim());
                }
            }
            System.out.println("Credenciales cargadas desde " + ARCHIVO_CREDENCIALES);
        } catch (FileNotFoundException e) {
            System.out.println("Archivo de credenciales no encontrado, se creará nuevo.");
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
            System.out.println("Credenciales guardadas.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

