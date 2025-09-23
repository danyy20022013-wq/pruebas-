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

    // ==========================
    // MÉTODOS AUXILIARES
    // ==========================

    private static synchronized boolean usuarioExiste(String username) {
        File f = new File(ARCHIVO_CREDENCIALES);
        if (!f.exists()) return false;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
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
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
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
            writer.write(destinatario + ":" + remitente + ":" + mensaje);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized List<String> obtenerMensajes(String username) {
        List<String> mensajes = new ArrayList<>();
        File f = new File(ARCHIVO_MENSAJES);
        if (!f.exists()) return mensajes;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String linea;
            int id = 1;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(":", 3);
                if (partes.length == 3) {
                    String destinatario = partes[0];
                    String remitente = partes[1];
                    String cuerpo = partes[2];
                    if (destinatario.equals(username)) {
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
                        borrado = true;
                    } else {
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

        archivo.delete();
        temp.renameTo(archivo);
        return borrado;
    }

    private static synchronized void eliminarUsuario(String username) {
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

    // ==========================
    // MANEJADOR CLIENTE
    // ==========================

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

                clientesConectados.put(username, out);

                mostrarMenu();

                String input;
                while ((input = in.readLine()) != null) {
                    if (jugando) {
                        procesarJuego(input);
                        continue;
                    }

                    switch (input) {
                        case "1":
                            enviarMensaje();
                            break;
                        case "2":
                            mostrarMensajesPaginados();
                            break;
                        case "3":
                            borrarMensajesPaginados();
                            break;
                        case "4":
                            eliminarUsuario(username);
                            out.println("Tu cuenta y mensajes fueron eliminados. Adiós.");
                            return;
                        case "5":
                            iniciarJuego();
                            break;
                        case "6":
                            mostrarMenu();
                            break;
                        default:
                            out.println("Opción inválida. Escribe un número del 1 al 6.");
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
                out.println("Ingresa tu usuario o escribe 'nuevo' para crear una cuenta:");
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
                            mostrarMensajesPaginados();
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
            out.println("1. Enviar mensaje privado");
            out.println("2. Ver bandeja de entrada");
            out.println("3. Borrar mensaje por ID");
            out.println("4. Eliminar mi cuenta");
            out.println("5. Jugar (adivina número)");
            out.println("6. Mostrar menú");
            out.println("=======================");
        }

        private void enviarMensaje() throws IOException {
            out.println("Ingresa el nombre de usuario destinatario:");
            String destino = in.readLine();
            out.println("Escribe el mensaje:");
            String mensaje = in.readLine();

            if (usuarioExiste(destino)) {
                guardarMensaje(username, destino, mensaje);
                out.println("Mensaje enviado a " + destino);
            } else {
                out.println("Usuario no existente.");
            }
        }

        private void mostrarMensajesPaginados() throws IOException {
            List<String> mensajes = obtenerMensajes(username);
            if (mensajes.isEmpty()) {
                out.println("No tienes mensajes nuevos.");
                return;
            }

            int pagina = 1;
            int totalPaginas = (int) Math.ceil(mensajes.size() / 10.0);

            while (true) {
                out.println("=== BANDEJA DE ENTRADA (Página " + pagina + " de " + totalPaginas + ") ===");
                int inicio = (pagina - 1) * 10;
                int fin = Math.min(inicio + 10, mensajes.size());
                for (int i = inicio; i < fin; i++) {
                    out.println(mensajes.get(i));
                }
                if (pagina < totalPaginas) {
                    out.println("Escribe 's' para siguiente página o 'x' para salir:");
                } else {
                    out.println("Escribe 'x' para salir:");
                }

                String opcion = in.readLine();
                if (opcion.equalsIgnoreCase("s") && pagina < totalPaginas) {
                    pagina++;
                } else {
                    break;
                }
            }
        }

        private void borrarMensajesPaginados() throws IOException {
            List<String> mensajes = obtenerMensajes(username);
            if (mensajes.isEmpty()) {
                out.println("No tienes mensajes para borrar.");
                return;
            }

            int pagina = 1;
            int totalPaginas = (int) Math.ceil(mensajes.size() / 10.0);

            while (true) {
                out.println("=== ELIMINAR MENSAJES (Página " + pagina + " de " + totalPaginas + ") ===");
                int inicio = (pagina - 1) * 10;
                int fin = Math.min(inicio + 10, mensajes.size());
                for (int i = inicio; i < fin; i++) {
                    out.println(mensajes.get(i));
                }
                out.println("Escribe el ID del mensaje a borrar, 's' para siguiente página o 'x' para salir:");

                String opcion = in.readLine();
                if (opcion.equalsIgnoreCase("s") && pagina < totalPaginas) {
                    pagina++;
                } else if (opcion.equalsIgnoreCase("x")) {
                    break;
                } else {
                    try {
                        int id = Integer.parseInt(opcion);
                        boolean ok = borrarMensaje(username, id);
                        if (ok) {
                            out.println("Mensaje borrado (id " + id + ").");
                        } else {
                            out.println("No se encontró un mensaje tuyo con ese ID.");
                        }
                        break;
                    } catch (NumberFormatException e) {
                        out.println("Entrada inválida.");
                    }
                }
            }
        }

        private void iniciarJuego() {
            jugando = true;
            numeroSecreto = new Random().nextInt(10) + 1;
            intentosRestantes = 3;
            out.println("Adivina un número del 1 al 10. Tienes 3 intentos.");
        }

        private void procesarJuego(String input) {
            try {
                int intento = Integer.parseInt(input);
                if (intento < 1 || intento > 10) {
                    out.println("Solo números entre 1 y 10.");
                    return;
                }
                intentosRestantes--;

                if (intento < numeroSecreto) {
                    out.println("El número es mayor. Te quedan " + intentosRestantes + " intentos.");
                } else if (intento > numeroSecreto) {
                    out.println("El número es menor. Te quedan " + intentosRestantes + " intentos.");
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
