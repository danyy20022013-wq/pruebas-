import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class servidor {
    private static final int PUERTO = 5050;
    private static final String ARCHIVO_CREDENCIALES = "credenciales.txt";
    private static final String ARCHIVO_MENSAJES = "mensajes.txt";

    private static Map<String, String> usuarios = new HashMap<>();
    private static Map<String, PrintWriter> clientesConectados = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        cargarUsuarios();
        System.out.println("Servidor escuchando en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ManejadorCliente(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void cargarUsuarios() {
        usuarios.clear();
        File f = new File(ARCHIVO_CREDENCIALES);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":", 2);
                if (partes.length == 2) {
                    usuarios.put(partes[0], partes[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void guardarUsuario(String user, String pass) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCHIVO_CREDENCIALES, true))) {
            bw.write(user + ":" + pass);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        usuarios.put(user, pass);
    }

    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCHIVO_MENSAJES, true))) {
            bw.write(destinatario + ":" + remitente + ":" + mensaje);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized List<String> obtenerMensajes(String user, boolean recibidos) {
        List<String> mensajes = new ArrayList<>();
        File f = new File(ARCHIVO_MENSAJES);
        if (!f.exists()) return mensajes;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String linea;
            int id = 1;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(":", 3);
                if (partes.length == 3) {
                    String dest = partes[0];
                    String remit = partes[1];
                    String cuerpo = partes[2];
                    if ((recibidos && dest.equals(user)) || (!recibidos && remit.equals(user))) {
                        mensajes.add(id + " | " + remit + " -> " + dest + " | " + cuerpo);
                    }
                }
                id++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mensajes;
    }

    private static synchronized boolean borrarMensaje(String user, int id, boolean recibidos) {
        File archivo = new File(ARCHIVO_MENSAJES);
        if (!archivo.exists()) return false;
        File temp = new File("mensajes_temp.txt");
        boolean borrado = false;

        try (BufferedReader br = new BufferedReader(new FileReader(archivo));
             PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
            String linea;
            int contador = 1;
            while ((linea = br.readLine()) != null) {
                if (contador == id) {
                    String[] partes = linea.split(":", 3);
                    if (partes.length == 3) {
                        String dest = partes[0];
                        String remit = partes[1];
                        if ((recibidos && dest.equals(user)) || (!recibidos && remit.equals(user))) {
                            borrado = true;
                        } else {
                            pw.println(linea);
                        }
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
        }

        archivo.delete();
        temp.renameTo(archivo);
        return borrado;
    }

    private static synchronized void eliminarUsuario(String user) {
        // credenciales
        File f = new File(ARCHIVO_CREDENCIALES);
        if (f.exists()) {
            File temp = new File("credenciales_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(f));
                 PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    if (!linea.startsWith(user + ":")) {
                        pw.println(linea);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            f.delete();
            temp.renameTo(f);
        }
        usuarios.remove(user);

        // mensajes
        File m = new File(ARCHIVO_MENSAJES);
        if (m.exists()) {
            File temp = new File("mensajes_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(m));
                 PrintWriter pw = new PrintWriter(new FileWriter(temp))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] partes = linea.split(":", 3);
                    if (partes.length == 3) {
                        String dest = partes[0];
                        String remit = partes[1];
                        if (!dest.equals(user) && !remit.equals(user)) {
                            pw.println(linea);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            m.delete();
            temp.renameTo(m);
        }
    }

    private static class ManejadorCliente implements Runnable {
        private Socket socket;
        private BufferedReader entrada;
        private PrintWriter salida;
        private String usuario;
        private boolean jugando = false;
        private int numeroSecreto, intentos;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                if (!login()) return;
                clientesConectados.put(usuario, salida);

                while (true) {
                    mostrarMenu();
                    String opcion = entrada.readLine();
                    if (opcion == null) break;

                    if (jugando) {
                        procesarJuego(opcion);
                        continue;
                    }

                    switch (opcion) {
                        case "1":
                            salida.println("Destinatario:");
                            String dest = entrada.readLine();
                            salida.println("Mensaje:");
                            String msg = entrada.readLine();
                            if (usuarios.containsKey(dest)) {
                                guardarMensaje(usuario, dest, msg);
                                salida.println("Mensaje enviado.");
                            } else {
                                salida.println("El usuario no existe.");
                            }
                            break;
                        case "2":
                            mostrarPaginado(obtenerMensajes(usuario, true), true);
                            break;
                        case "3":
                            mostrarPaginado(obtenerMensajes(usuario, false), false);
                            break;
                        case "4":
                            eliminarUsuario(usuario);
                            salida.println("Cuenta eliminada. Reinicia sesión.");
                            return;
                        case "5":
                            iniciarJuego();
                            break;
                        case "6":
                            salida.println("Has cerrado sesión.");
                            return;
                        case "7":
                            salida.println("Has salido del servidor.");
                            return;
                        default:
                            salida.println("Opción no válida.");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (usuario != null) clientesConectados.remove(usuario);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private boolean login() throws IOException {
            while (true) {
                cargarUsuarios(); // 🔹 recargamos siempre
                salida.println("1. Ingresar");
                salida.println("2. Crear nuevo usuario");
                salida.println("Elige una opción:");
                String opcion = entrada.readLine();
                salida.println("Usuario:");
                String user = entrada.readLine();
                salida.println("Contraseña:");
                String pass = entrada.readLine();

                if ("1".equals(opcion)) {
                    if (usuarios.containsKey(user) && usuarios.get(user).equals(pass)) {
                        this.usuario = user;
                        salida.println("Bienvenido " + usuario);
                        return true;
                    } else {
                        salida.println("Usuario o contraseña incorrectos.");
                    }
                } else if ("2".equals(opcion)) {
                    if (usuarios.containsKey(user)) {
                        salida.println("El usuario ya existe.");
                    } else {
                        guardarUsuario(user, pass);
                        this.usuario = user;
                        salida.println("Usuario creado y logueado: " + usuario);
                        return true;
                    }
                } else {
                    salida.println("Opción no válida.");
                }
            }
        }

        private void mostrarMenu() {
            salida.println("=== MENÚ PRINCIPAL ===");
            salida.println("1. Enviar mensaje");
            salida.println("2. Ver bandeja de entrada");
            salida.println("3. Ver mensajes enviados");
            salida.println("4. Eliminar mi cuenta");
            salida.println("5. Jugar");
            salida.println("6. Cerrar sesión");
            salida.println("7. Salir");
            salida.println("======================");
        }

        private void mostrarPaginado(List<String> mensajes, boolean recibidos) throws IOException {
            if (mensajes.isEmpty()) {
                salida.println("No hay mensajes.");
                return;
            }
            int total = mensajes.size();
            int paginas = (int) Math.ceil(total / 10.0);
            int pagina = 1;
            while (true) {
                int inicio = (pagina - 1) * 10;
                int fin = Math.min(pagina * 10, total);
                salida.println("Página " + pagina + "/" + paginas);
                for (int i = inicio; i < fin; i++) salida.println(mensajes.get(i));
                salida.println("Opciones: n=next, p=prev, b=id borrar, q=salir");
                String cmd = entrada.readLine();
                if (cmd == null || cmd.equals("q")) break;
                if (cmd.equals("n") && pagina < paginas) pagina++;
                else if (cmd.equals("p") && pagina > 1) pagina--;
                else if (cmd.startsWith("b")) {
                    try {
                        int id = Integer.parseInt(cmd.substring(1));
                        if (borrarMensaje(usuario, id, recibidos)) {
                            salida.println("Mensaje borrado.");
                            mensajes = obtenerMensajes(usuario, recibidos);
                            total = mensajes.size();
                            paginas = (int) Math.ceil(total / 10.0);
                            if (pagina > paginas) pagina = paginas;
                        } else salida.println("No se pudo borrar.");
                    } catch (Exception e) {
                        salida.println("Comando inválido.");
                    }
                }
            }
        }

        private void iniciarJuego() {
            jugando = true;
            numeroSecreto = new Random().nextInt(10) + 1;
            intentos = 3;
            salida.println("Adivina un número del 1 al 10. Tienes 3 intentos.");
        }

        private void procesarJuego(String input) {
            try {
                int intento = Integer.parseInt(input);
                if (intento < 1 || intento > 10) {
                    salida.println("Número fuera de rango (1-10).");
                    return;
                }
                intentos--;
                if (intento == numeroSecreto) {
                    salida.println("¡Correcto! Era " + numeroSecreto);
                    jugando = false;
                } else if (intentos > 0) {
                    salida.println(intento < numeroSecreto ? "Mayor." : "Menor.");
                    salida.println("Intentos restantes: " + intentos);
                } else {
                    salida.println("Perdiste. El número era " + numeroSecreto);
                    jugando = false;
                }
            } catch (NumberFormatException e) {
                salida.println("Ingresa un número válido.");
            }
        }
    }
}
