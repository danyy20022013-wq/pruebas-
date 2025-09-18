import java.io.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class servidor {
    private static final int PUERTO = 5050;
    private static final String ARCHIVO_CREDENCIALES = "credenciales.txt";
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";

    private static Map<String, PrintWriter> clientesConectados = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Servidor iniciado en el puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ManejadorCliente(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized boolean usuarioExiste(String username) {
        File f = new File(ARCHIVO_CREDENCIALES);
        if (!f.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(ARCHIVO_CREDENCIALES))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":", 2);
                if (partes.length >= 1 && partes[0].trim().equals(username)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static synchronized void guardarCredencial(String username, String password) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ARCHIVO_CREDENCIALES, true))) {
            writer.write(username + ":" + password);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized boolean validarCredencial(String username, String password) {
        File f = new File(ARCHIVO_CREDENCIALES);
        if (!f.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(ARCHIVO_CREDENCIALES))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":", 2);
                if (partes.length == 2 && partes[0].equals(username) && partes[1].equals(password)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ARCHIVO_MENSAJES, true))) {
            // formato: destinatario:remitente:mensaje
            writer.write(destinatario + ":" + remitente + ":" + mensaje);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Devuelve la lista de mensajes dirigidos a username.
     * Cada elemento tiene el formato: ID | remitente | mensaje
     * ID corresponde a la línea (1-based) dentro de mensajes.txt
     */
    private static synchronized List<String> obtenerMensajes(String username) {
        List<String> mensajes = new ArrayList<>();
        File f = new File(ARCHIVO_MENSAJES);
        if (!f.exists()) return mensajes;
        try (BufferedReader reader = new BufferedReader(new FileReader(ARCHIVO_MENSAJES))) {
            String linea;
            int id = 1;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":", 3);
                if (partes.length == 3) {
                    String destinatario = partes[0];
                    String remitente = partes[1];
                    String cuerpo = partes[2];
                    if (destinatario.equals(username)) {
                        // formateamos para mostrar al usuario
                        mensajes.add(id + " | " + remitente + " | " + cuerpo);
                    }
                }
                id++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mensajes;
    }

    /**
     * Borra la línea ID (1-based) del archivo mensajes.txt si pertenece a username (destinatario).
     * Retorna true si borró algo.
     */
    private static synchronized boolean borrarMensaje(String username, int id) {
        File archivo = new File(ARCHIVO_MENSAJES);
        if (!archivo.exists()) return false;

        File temp = new File("mensajes_temp.txt");
        boolean borrado = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(archivo));
             PrintWriter pw = new PrintWriter(new FileWriter(temp))) {

            String linea;
            int contador = 1;
            while ((linea = reader.readLine()) != null) {
                if (contador == id) {
                    String[] partes = linea.split(":", 3);
                    if (partes.length == 3 && partes[0].equals(username)) {
                        // omitimos esta línea -> borrada
                        borrado = true;
                    } else {
                        // no es tu mensaje, lo conservamos
                        pw.println(linea);
                    }
                } else {
                    pw.println(linea);
                }
                contador++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        // reemplazar archivo
        if (!archivo.delete()) {
            // no se pudo borrar el original
            temp.delete();
            return false;
        }
        if (!temp.renameTo(archivo)) {
            return false;
        }
        return borrado;
    }

    /**
     * Elimina credencial y todos los mensajes donde aparezca como remitente o destinatario
     */
    private static synchronized void eliminarUsuario(String username) {
        // editar credenciales
        File cred = new File(ARCHIVO_CREDENCIALES);
        if (cred.exists()) {
            File temp = new File("cred_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(cred));
                 PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partes = linea.split(":", 2);
                    if (partes.length >= 1 && !partes[0].equals(username)) {
                        pw.println(linea);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            cred.delete();
            temp.renameTo(cred);
        }

        // editar mensajes: quitar líneas donde sea remitente O destinatario
        File msgs = new File(ARCHIVO_MENSAJES);
        if (msgs.exists()) {
            File temp = new File("mensajes_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(msgs));
                 PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partes = linea.split(":", 3);
                    if (partes.length == 3) {
                        String destinatario = partes[0];
                        String remitente = partes[1];
                        if (!destinatario.equals(username) && !remitente.equals(username)) {
                            pw.println(linea);
                        }
                    } else {
                        pw.println(linea);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            msgs.delete();
            temp.renameTo(msgs);
        }
    }

    private static class ManejadorCliente implements Runnable {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private boolean jugando = false;
        private int numeroSecreto;
        private int intentosRestantes;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                autenticar();

                // registrar canal de salida
                clientesConectados.put(username, out);

                mostrarMenu();

                String input;
                while ((input = in.readLine()) != null) {
                    if (jugando) {
                        procesarJuego(input);
                        continue;
                    }

                    if (input.equalsIgnoreCase("menu")) {
                        mostrarMenu();

                    } else if (input.equalsIgnoreCase("/inbox")) {
                        mostrarMensajesPendientes();

                    } else if (input.startsWith("/msg")) {
                        String[] partes = input.split(" ", 3);
                        if (partes.length < 3) {
                            out.println("Uso correcto: /msg usuario mensaje");
                        } else {
                            String destino = partes[1];
                            String mensaje = partes[2];
                            if (usuarioExiste(destino)) {
                                guardarMensaje(username, destino, mensaje);
                                out.println("Mensaje enviado a " + destino);
                            } else {
                                out.println("Usuario no existente.");
                            }
                        }

                    } else if (input.startsWith("/borrar")) {
                        String[] partes = input.split(" ");
                        if (partes.length != 2) {
                            out.println("Uso correcto: /borrar id");
                        } else {
                            try {
                                int id = Integer.parseInt(partes[1]);
                                boolean ok = borrarMensaje(username, id);
                                if (ok) out.println("Mensaje borrado (id " + id + ").");
                                else out.println("No se encontró un mensaje tuyo con ese ID.");
                            } catch (NumberFormatException e) {
                                out.println("ID inválido.");
                            }
                        }

                    } else if (input.equalsIgnoreCase("/eliminarme")) {
                        eliminarUsuario(username);
                        out.println("Tu cuenta y mensajes fueron eliminados. Adiós.");
                        break;

                    } else if (input.equalsIgnoreCase("jugar")) {
                        iniciarJuego();

                    } else {
                        out.println("Comando no reconocido. Usa /msg, /borrar, /inbox, /eliminarme, jugar, menu");
                    }
                }
            } catch (IOException e) {
                // cliente desconectado
            } finally {
                if (username != null) {
                    clientesConectados.remove(username);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void autenticar() throws IOException {
            while (true) {
                out.println("Bienvenido. Ingresa tu usuario o escribe 'nuevo' para crear una cuenta.");
                String entrada = in.readLine();
                if (entrada == null) return;

                if (entrada.equalsIgnoreCase("nuevo")) {
                    out.println("Ingresa tu nuevo nombre de usuario:");
                    String nuevoUsuario = in.readLine();
                    out.println("Ingresa tu nueva contraseña:");
                    String nuevaContrasena = in.readLine();

                    if (!usuarioExiste(nuevoUsuario)) {
                        guardarCredencial(nuevoUsuario, nuevaContrasena);
                        out.println("Cuenta creada exitosamente. Ahora inicia sesión.");
                    } else {
                        out.println("Error: el usuario ya existe.");
                    }
                } else {
                    if (usuarioExiste(entrada)) {
                        out.println("Ingresa tu contraseña:");
                        String pass = in.readLine();
                        if (validarCredencial(entrada, pass)) {
                            username = entrada;
                            out.println("Inicio de sesión exitoso. Bienvenido " + username);
                            // mostrar mensajes pendientes al inicio de sesión
                            mostrarMensajesPendientes();
                            break;
                        } else {
                            out.println("Contraseña incorrecta.");
                        }
                    } else {
                        out.println("Usuario no encontrado.");
                    }
                }
            }
        }

        private void mostrarMenu() {
            out.println("=== MENÚ PRINCIPAL ===");
            out.println("Comandos disponibles:");
            out.println("/msg usuario mensaje  -> Enviar mensaje privado");
            out.println("/inbox                -> Ver mensajes recibidos");
            out.println("/borrar id            -> Borrar mensaje por ID (usa el ID que ves en /inbox)");
            out.println("/eliminarme           -> Eliminar tu cuenta y mensajes");
            out.println("jugar                 -> Iniciar el juego de adivinar número (1-10, 3 intentos)");
            out.println("menu                  -> Mostrar este menú otra vez");
            out.println("=======================");
        }

        private void mostrarMensajesPendientes() {
            List<String> mensajes = obtenerMensajes(username);
            if (mensajes.isEmpty()) {
                out.println("No tienes mensajes nuevos.");
            } else {
                out.println("Tu bandeja de entrada:");
                for (String m : mensajes) {
                    out.println(m);
                }
            }
        }

        private void iniciarJuego() {
            jugando = true;
            numeroSecreto = new Random().nextInt(10) + 1; // número entre 1 y 10
            intentosRestantes = 3;
            out.println("Adivina un número del 1 al 10. Tienes 3 intentos.");
        }

        private void procesarJuego(String input) {
            try {
                int intento = Integer.parseInt(input);

                if (intento < 1 || intento > 10) {
                    out.println("Solo puedes elegir números entre 1 y 10.");
                    return;
                }

                intentosRestantes--;

                if (intento < numeroSecreto) {
                    out.println("El número es mayor. Te quedan " + intentosRestantes + " intentos");
                } else if (intento > numeroSecreto) {
                    out.println("El número es menor. Te quedan " + intentosRestantes + " intentos");
                } else {
                    out.println("Correcto. El número era " + numeroSecreto);
                    jugando = false;
                    return;
                }

                if (intentosRestantes == 0) {
                    out.println("No adivinaste. El número era " + numeroSecreto);
                    jugando = false;
                }

            } catch (NumberFormatException e) {
                out.println("Ingresa un número válido entre 1 y 10.");
            }
        }
    }
}
