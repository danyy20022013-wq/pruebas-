import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class servidor {
    private static final int PUERTO = 5050;
    private static final String ARCH_CRED = "credenciales.txt";   // formato: usuario:contrasena
    private static final String ARCH_MSG  = "mensajes.txt";       // formato: destinatario:remitente:mensaje
    private static final String ARCH_BLOQ = "bloqueos.txt";      // formato: quienBloquea:bloqueado

    private static final Map<String, PrintWriter> clientesConectados = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        System.out.println("Iniciando servidor en puerto " + PUERTO);
        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            while (true) {
                Socket s = ss.accept();
                new Thread(new HandlerCliente(s)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================= UTILS (synchronized para operaciones con archivos) =========================

    // recarga y valida credenciales
    private static synchronized Map<String, String> cargarCredenciales() {
        Map<String, String> map = new HashMap<>();
        File f = new File(ARCH_CRED);
        if (!f.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":", 2);
                if (p.length == 2) map.put(p[0], p[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    private static synchronized boolean guardarCredencial(String usuario, String pass) {
        // evita duplicados
        Map<String,String> creds = cargarCredenciales();
        if (creds.containsKey(usuario)) return false;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCH_CRED, true))) {
            bw.write(usuario + ":" + pass);
            bw.newLine();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static synchronized boolean validarCredencial(String usuario, String pass) {
        Map<String,String> creds = cargarCredenciales();
        return creds.containsKey(usuario) && creds.get(usuario).equals(pass);
    }

    private static synchronized List<String> listarUsuarios() {
        List<String> res = new ArrayList<>();
        File f = new File(ARCH_CRED);
        if (!f.exists()) return res;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":",2);
                if (p.length >= 1) res.add(p[0]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    // bloqueo: añadir y quitar
    private static synchronized void agregarBloqueo(String quien, String bloqueado) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCH_BLOQ, true))) {
            bw.write(quien + ":" + bloqueado);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static synchronized void quitarBloqueo(String quien, String bloqueado) {
        File f = new File(ARCH_BLOQ);
        if (!f.exists()) return;
        File tmp = new File("bloqueos_temp.txt");
        try (BufferedReader br = new BufferedReader(new FileReader(f));
             PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":",2);
                if (p.length == 2 && p[0].equals(quien) && p[1].equals(bloqueado)) {
                    // skip
                } else {
                    pw.println(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        f.delete();
        tmp.renameTo(f);
    }

    private static synchronized Set<String> obtenerBloqueadosPor(String usuario) {
        Set<String> s = new HashSet<>();
        File f = new File(ARCH_BLOQ);
        if (!f.exists()) return s;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":",2);
                if (p.length == 2 && p[0].equals(usuario)) s.add(p[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s;
    }

    // Comprueba si destinatario ha bloqueado remitente
    private static synchronized boolean destinatarioHaBloqueado(String remitente, String destinatario) {
        Set<String> bloqueados = obtenerBloqueadosPor(destinatario);
        return bloqueados.contains(remitente);
    }

    // ========================= Mensajes (line-based, linea == ID global 1-based) =========================

    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCH_MSG, true))) {
            bw.write(destinatario + ":" + remitente + ":" + mensaje);
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // devuelve lista de pares (id, display) según recibidos=true => destinatario==usuario, else remitente==usuario (mensajes enviados)
    private static synchronized List<MessageEntry> listarMensajesConId(String usuario, boolean recibidos) {
        List<MessageEntry> out = new ArrayList<>();
        File f = new File(ARCH_MSG);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int id = 1;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":",3);
                if (p.length == 3) {
                    String dest = p[0], remit = p[1], cuerpo = p[2];
                    if (recibidos && dest.equals(usuario)) {
                        out.add(new MessageEntry(id, remit + " | " + cuerpo));
                    } else if (!recibidos && remit.equals(usuario)) {
                        out.add(new MessageEntry(id, "Para " + dest + " | " + cuerpo));
                    }
                }
                id++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }

    // Borra línea con id si la linea corresponde a un mensaje del usuario (recibido o enviado según flag)
    private static synchronized boolean borrarMensajePorId(String usuario, int id, boolean recibidos) {
        File f = new File(ARCH_MSG);
        if (!f.exists()) return false;
        File tmp = new File("mensajes_temp.txt");
        boolean eliminado = false;
        try (BufferedReader br = new BufferedReader(new FileReader(f));
             PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
            String line;
            int ln = 1;
            while ((line = br.readLine()) != null) {
                if (ln == id) {
                    String[] p = line.split(":",3);
                    if (p.length == 3) {
                        String dest = p[0], remit = p[1];
                        if ((recibidos && dest.equals(usuario)) || (!recibidos && remit.equals(usuario))) {
                            eliminado = true; // omitimos esta linea
                        } else {
                            pw.println(line);
                        }
                    } else {
                        pw.println(line);
                    }
                } else {
                    pw.println(line);
                }
                ln++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        f.delete();
        tmp.renameTo(f);
        return eliminado;
    }

    // eliminar usuario: credenciales, mensajes y bloqueos relacionados
    private static synchronized void eliminarCuentaCompleta(String usuario) {
        // credenciales
        File cred = new File(ARCH_CRED);
        if (cred.exists()) {
            File tmp = new File("cred_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(cred));
                 PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(":",2);
                    if (!(p.length >=1 && p[0].equals(usuario))) pw.println(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            cred.delete();
            tmp.renameTo(cred);
        }
        // mensajes
        File msgs = new File(ARCH_MSG);
        if (msgs.exists()) {
            File tmp = new File("mens_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(msgs));
                 PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(":",3);
                    if (p.length == 3) {
                        String dest = p[0], remit = p[1];
                        if (!dest.equals(usuario) && !remit.equals(usuario)) pw.println(line);
                    } else {
                        pw.println(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            msgs.delete();
            tmp.renameTo(msgs);
        }
        // bloqueos
        File blq = new File(ARCH_BLOQ);
        if (blq.exists()) {
            File tmp = new File("blq_temp.txt");
            try (BufferedReader br = new BufferedReader(new FileReader(blq));
                 PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(":",2);
                    if (p.length == 2) {
                        if (!p[0].equals(usuario) && !p[1].equals(usuario)) pw.println(line);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            blq.delete();
            tmp.renameTo(blq);
        }
    }

    // ========================= Handler por cliente =========================

    private static class MessageEntry {
        int id;
        String display;
        MessageEntry(int id, String display) { this.id = id; this.display = display; }
    }

    private static class HandlerCliente implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String usuario = null;
        private boolean jugando = false;
        private int numeroSecreto = -1;
        private int intentos = 0;

        HandlerCliente(Socket s) { this.socket = s; }

        @Override
        public void run() {
            try {
                in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Autenticación: pide usuario o 'nuevo'
                while (usuario == null) {
                    out.println("Ingresa tu usuario o escribe 'nuevo' para crear una cuenta:");
                    String linea = in.readLine();
                    if (linea == null) return;
                    if (linea.equalsIgnoreCase("nuevo")) {
                        out.println("Nuevo usuario (nombre):");
                        String nuevo = in.readLine();
                        out.println("Nueva contraseña:");
                        String pass = in.readLine();
                        if (nuevo == null || pass == null) return;
                        synchronized (servidor.class) {
                            boolean ok = guardarCredencial(nuevo, pass);
                            if (ok) out.println("Cuenta creada. Inicia sesión con tu usuario.");
                            else out.println("Error: usuario ya existe.");
                        }
                    } else {
                        out.println("Ingresa tu contraseña:");
                        String pass = in.readLine();
                        if (pass == null) return;
                        // recargar y validar
                        if (validarCredencial(linea, pass)) {
                            usuario = linea;
                            clientesConectados.put(usuario, out);
                            out.println("Inicio de sesión exitoso. Bienvenido " + usuario);
                        } else {
                            out.println("Usuario/contraseña incorrectos.");
                        }
                    }
                }

                // Loop principal del usuario
                mostrarMenu();
                String cmd;
                while ((cmd = in.readLine()) != null) {
                    if (jugando) {
                        procesarJuego(cmd);
                        if (!jugando) mostrarMenu();
                        continue;
                    }
                    switch (cmd) {
                        case "1":
                            opciónMandarMensaje();
                            break;
                        case "2":
                            opciónVerMensajes();
                            break;
                        case "3":
                            opciónBorrarMensajes();
                            break;
                        case "4":
                            // eliminar cuenta completa y desconectar -> reinicio del cliente
                            synchronized (servidor.class) {
                                eliminarCuentaCompleta(usuario);
                            }
                            out.println("Tu cuenta y mensajes fueron eliminados. Se cerrará la sesión.");
                            clientesConectados.remove(usuario);
                            usuario = null;
                            return;
                        case "5":
                            opciónBloquear();
                            break;
                        case "6":
                            opciónDesbloquear();
                            break;
                        case "7":
                            out.println("Has cerrado sesión. Vuelve a iniciar sesión si deseas.");
                            clientesConectados.remove(usuario);
                            usuario = null;
                            return;
                        default:
                            out.println("Opción inválida. Escribe un número del menú.");
                    }
                    if (usuario != null) mostrarMenu();
                }

            } catch (IOException e) {
                // cliente desconectado
            } finally {
                if (usuario != null) clientesConectados.remove(usuario);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void mostrarMenu() {
            out.println(" MENÚ PRINCIPAL ");
            out.println("1. Mandar mensaje ");
            out.println("2. Ver mensajes recibidos ");
            out.println("3. Borrar mensaje por ID ");
            out.println("4. Eliminar mi cuenta ");
            out.println("5. Bloquear usuario");
            out.println("6. Desbloquear usuario");
            out.println("7. Cerrar sesión");

        }

        // ---------- Opción 1: mandar mensaje mostrando usuarios y su estado ----------
        private void opciónMandarMensaje() throws IOException {
            List<String> usuarios = listarUsuarios();
            Set<String> misBloqueados = obtenerBloqueadosPor(usuario);

            out.println("Usuarios registrados (excluye a ti):");
            for (String u : usuarios) {
                if (u.equals(usuario)) continue;
                boolean yoBloqueo = misBloqueados.contains(u);
                boolean meBloquea = destinatarioHaBloqueado(usuario, u); // u ha bloqueado a usuario ?
                String tag = "";
                if (yoBloqueo) tag = " (bloqueado por ti)";
                else if (meBloquea) tag = " (te ha bloqueado)";
                out.println("- " + u + tag);
            }
            out.println("Escribe el nombre del usuario destino (o 'salir' para cancelar):");
            String destino = in.readLine();
            if (destino == null || destino.equalsIgnoreCase("salir")) return;

            if (!usuarioExiste(destino)) {
                out.println("Usuario no encontrado.");
                return;
            }
            // no permitimos enviar si has bloqueado al destinatario
            if (misBloqueados.contains(destino)) {
                out.println("No puedes enviar mensajes a " + destino + " porque lo tienes bloqueado. Desbloquealo primero.");
                return;
            }
            // ni si el destinatario te ha bloqueado
            if (destinatarioHaBloqueado(usuario, destino)) {
                out.println("No puedes enviar mensajes a " + destino + " porque te ha bloqueado.");
                return;
            }

            out.println("Escribe tu mensaje:");
            String texto = in.readLine();
            if (texto == null) return;
            synchronized (servidor.class) { guardarMensaje(usuario, destino, texto); }
            out.println("Mensaje enviado a " + destino + ".");
        }

        // ---------- Opción 2: ver mensajes recibidos con paginación 10 ----------
        private void opciónVerMensajes() throws IOException {
            List<MessageEntry> msgs = listarMensajesConId(usuario, true); // recibidos
            if (msgs.isEmpty()) { out.println("No tienes mensajes."); return; }
            final int PAGE = 10;
            int pagina = 0;
            int totalPaginas = (int)Math.ceil(msgs.size() / (double)PAGE);
            while (true) {
                int inicio = pagina * PAGE;
                int fin = Math.min(inicio + PAGE, msgs.size());
                out.println("=== Bandeja de entrada (página " + (pagina+1) + " / " + totalPaginas + ") ===");
                for (int i = inicio; i < fin; i++) {
                    MessageEntry e = msgs.get(i);
                    out.println(e.id + " | " + e.display);
                }
                out.println("[n] siguiente | [p] anterior | [q] salir");
                String cmd = in.readLine();
                if (cmd == null) return;
                if (cmd.equalsIgnoreCase("n")) {
                    if (pagina+1 < totalPaginas) pagina++;
                    else out.println("No hay más páginas.");
                } else if (cmd.equalsIgnoreCase("p")) {
                    if (pagina > 0) pagina--;
                    else out.println("Ya estás en la primera página.");
                } else if (cmd.equalsIgnoreCase("q")) {
                    break;
                } else {
                    out.println("Comando inválido.");
                }
            }
        }

        // ---------- Opción 3: borrar mensajes (te pedirá ID global) ----------
        private void opciónBorrarMensajes() throws IOException {
            out.println("Para borrar, primero verás tus mensajes (IDs).");
            List<MessageEntry> msgs = listarMensajesConId(usuario, true);
            if (msgs.isEmpty()) { out.println("No tienes mensajes para borrar."); return; }
            final int PAGE = 10;
            int pagina = 0;
            int totalPaginas = (int)Math.ceil(msgs.size() / (double)PAGE);
            while (true) {
                int inicio = pagina*PAGE;
                int fin = Math.min(inicio + PAGE, msgs.size());
                out.println("=== Mensajes (página " + (pagina+1) + " / " + totalPaginas + ") ===");
                for (int i = inicio; i < fin; i++) {
                    MessageEntry e = msgs.get(i);
                    out.println(e.id + " | " + e.display);
                }
                out.println("Escribe ID a borrar, 'n' siguiente, 'p' anterior, 'q' salir:");
                String cmd = in.readLine();
                if (cmd == null) return;
                if (cmd.equalsIgnoreCase("n")) {
                    if (pagina+1 < totalPaginas) pagina++; else out.println("No hay más páginas.");
                } else if (cmd.equalsIgnoreCase("p")) {
                    if (pagina > 0) pagina--; else out.println("Ya en la primera página.");
                } else if (cmd.equalsIgnoreCase("q")) {
                    break;
                } else {
                    try {
                        int id = Integer.parseInt(cmd);
                        boolean ok = borrarMensajePorId(usuario, id, true);
                        if (ok) {
                            out.println("Mensaje borrado (ID " + id + ").");
                            // refresh list
                            msgs = listarMensajesConId(usuario, true);
                            if (msgs.isEmpty()) { out.println("No tienes más mensajes."); break; }
                            totalPaginas = (int)Math.ceil(msgs.size() / (double)PAGE);
                            if (pagina >= totalPaginas) pagina = Math.max(0, totalPaginas-1);
                        } else out.println("No se encontró mensaje con ese ID o no es tuyo.");
                    } catch (NumberFormatException e) {
                        out.println("Entrada inválida.");
                    }
                }
            }
        }

        // ---------- Opción 5 y 6: bloquear / desbloquear ----------
        private void opciónBloquear() throws IOException {
            out.println("Escribe el usuario a bloquear:");
            String quien = in.readLine();
            if (quien == null) return;
            if (!usuarioExiste(quien)) { out.println("Usuario no existe."); return; }
            if (quien.equals(usuario)) { out.println("No puedes bloquearte a ti mismo."); return; }
            synchronized (servidor.class) { agregarBloqueo(usuario, quien); }
            out.println("Has bloqueado a " + quien + ".");
        }

        private void opciónDesbloquear() throws IOException {
            out.println("Escribe el usuario a desbloquear:");
            String quien = in.readLine();
            if (quien == null) return;
            synchronized (servidor.class) { quitarBloqueo(usuario, quien); }
            out.println("Has desbloqueado a " + quien + ".");
        }

        // ---------- ayuda ----------
        private boolean usuarioExiste(String nombre) {
            List<String> u = listarUsuarios();
            return u.contains(nombre);
        }

        // ---------- Juego ----------
        private void iniciarJuego() {
            this.numeroSecreto = new Random().nextInt(10) + 1;
            this.intentos = 3;
            this.jugando = true;
            out.println("INICIANDO JUEGO: Adivina número entre 1 y 10 (3 intentos). Escribe el número:");
        }

        private void procesarJuego(String entradaStr) {
            try {
                int intento = Integer.parseInt(entradaStr);
                if (intento < 1 || intento > 10) { out.println("Número fuera de rango (1-10)."); return; }
                if (intento == numeroSecreto) {
                    out.println("¡ADIVINASTE! El número era " + numeroSecreto);
                    jugando = false;
                } else {
                    intentos--;
                    if (intentos == 0) {
                        out.println("NO ADIVINASTE. El número era: " + numeroSecreto);
                        jugando = false;
                    } else {
                        out.println(intento < numeroSecreto ? "El número es mayor." : "El número es menor.");
                        out.println("Intentos restantes: " + intentos);
                    }
                }
            } catch (NumberFormatException e) {
                out.println("Entrada inválida. Ingresa un número.");
            }
        }
    }
}
