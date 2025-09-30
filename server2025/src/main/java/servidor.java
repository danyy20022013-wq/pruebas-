import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class servidor {
    // --- Configuración Principal ---
    private static final int PUERTO = 5050;
    private static final String ARCH_CRED = "credenciales.txt";
    private static final String ARCH_MSG  = "mensajes.txt";
    private static final String ARCH_BLOQ = "bloqueos.txt";
    private static final String DIR_USUARIOS = "user_data";
    private static final String DIR_DESCARGAS = "shared_downloads";

    // --- Estructuras de Datos en Memoria ---
    private static final Map<String, HandlerCliente> clientesConectados = new ConcurrentHashMap<>();
    private static final Map<String, List<PendingRequest>> solicitudesPendientes = new ConcurrentHashMap<>();

    // Clase interna para almacenar los detalles de una solicitud pendiente
    private static class PendingRequest {
        String solicitante;
        String tipo;

        PendingRequest(String solicitante, String tipo) {
            this.solicitante = solicitante;
            this.tipo = tipo;
        }
    }

    public static void main(String[] args) {
        System.out.println("Iniciando servidor en puerto " + PUERTO);
        new File(DIR_USUARIOS).mkdirs();
        new File(DIR_DESCARGAS).mkdirs();
        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            while (true) {
                Socket s = ss.accept();
                new Thread(new HandlerCliente(s)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ========================= MÉTODOS DE UTILIDAD (ARCHIVOS) =========================

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
        } catch (IOException e) { e.printStackTrace(); }
        return map;
    }

    private static synchronized boolean guardarCredencial(String usuario, String pass) {
        if (cargarCredenciales().containsKey(usuario)) return false;
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCH_CRED, true))) {
            bw.write(usuario + ":" + pass);
            bw.newLine();
            // Crear ambas carpetas personales para el nuevo usuario
            new File(DIR_USUARIOS + "/" + usuario).mkdirs();
            new File(DIR_DESCARGAS + "/" + usuario).mkdirs();
            return true;
        } catch (IOException e) { e.printStackTrace(); return false; }
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
        } catch (IOException e) { e.printStackTrace(); }
        return res;
    }

    private static synchronized void agregarBloqueo(String quien, String bloqueado) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCH_BLOQ, true))) {
            bw.write(quien + ":" + bloqueado);
            bw.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static synchronized void quitarBloqueo(String quien, String bloqueado) {
        File f = new File(ARCH_BLOQ);
        if (!f.exists()) return;
        File tmp = new File(f.getName() + ".tmp");
        try (BufferedReader br = new BufferedReader(new FileReader(f));
             PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":",2);
                if (!(p.length == 2 && p[0].equals(quien) && p[1].equals(bloqueado))) {
                    pw.println(line);
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        f.delete();
        tmp.renameTo(f);
    }

    private static synchronized boolean destinatarioHaBloqueado(String remitente, String destinatario) {
        File f = new File(ARCH_BLOQ);
        if(!f.exists()) return false;
        try(BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while((line = br.readLine()) != null) {
                String[] p = line.split(":", 2);
                if(p.length == 2 && p[0].equals(destinatario) && p[1].equals(remitente)) {
                    return true;
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
        return false;
    }

    private static synchronized void guardarMensaje(String remitente, String destinatario, String mensaje) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(ARCH_MSG, true))) {
            bw.write(destinatario + ":" + remitente + ":" + mensaje);
            bw.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static synchronized List<MessageEntry> listarMensajesConId(String usuario) {
        List<MessageEntry> out = new ArrayList<>();
        File f = new File(ARCH_MSG);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int id = 1;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(":",3);
                if (p.length == 3 && p[0].equals(usuario)) {
                    out.add(new MessageEntry(id, p[1] + ": " + p[2]));
                }
                id++;
            }
        } catch (IOException e) { e.printStackTrace(); }
        return out;
    }

    private static synchronized boolean borrarMensajePorId(String usuario, int id) {
        File f = new File(ARCH_MSG);
        if (!f.exists()) return false;
        File tmp = new File(f.getName() + ".tmp");
        boolean eliminado = false;
        try (BufferedReader br = new BufferedReader(new FileReader(f));
             PrintWriter pw = new PrintWriter(new FileWriter(tmp))) {
            String line;
            int ln = 1;
            while ((line = br.readLine()) != null) {
                if (ln == id) {
                    String[] p = line.split(":",3);
                    if (p.length == 3 && p[0].equals(usuario)) {
                        eliminado = true;
                    } else {
                        pw.println(line);
                    }
                } else {
                    pw.println(line);
                }
                ln++;
            }
        } catch (IOException e) { e.printStackTrace(); return false; }
        f.delete();
        tmp.renameTo(f);
        return eliminado;
    }

    private static synchronized void eliminarCuentaCompleta(String usuario) {
        // Eliminar de credenciales.txt
        File credFile = new File(ARCH_CRED);
        if (credFile.exists()) {
            File credTmp = new File(credFile.getName() + ".tmp");
            try (BufferedReader br = new BufferedReader(new FileReader(credFile));
                 PrintWriter pw = new PrintWriter(new FileWriter(credTmp))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.startsWith(usuario + ":")) {
                        pw.println(line);
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
            credFile.delete();
            credTmp.renameTo(credFile);
        }

        // Eliminar mensajes
        File msgFile = new File(ARCH_MSG);
        if(msgFile.exists()) {
            File msgTmp = new File(msgFile.getName() + ".tmp");
            try (BufferedReader br = new BufferedReader(new FileReader(msgFile));
                 PrintWriter pw = new PrintWriter(new FileWriter(msgTmp))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(":", 3);
                    if (p.length == 3 && (p[0].equals(usuario) || p[1].equals(usuario))) {
                        continue;
                    }
                    pw.println(line);
                }
            } catch (IOException e) { e.printStackTrace(); }
            msgFile.delete();
            msgTmp.renameTo(msgFile);
        }

        // Eliminar bloqueos
        File blqFile = new File(ARCH_BLOQ);
        if (blqFile.exists()) {
            File blqTmp = new File(blqFile.getName() + ".tmp");
            try (BufferedReader br = new BufferedReader(new FileReader(blqFile));
                 PrintWriter pw = new PrintWriter(new FileWriter(blqTmp))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.split(":", 2);
                    if (p.length == 2 && (p[0].equals(usuario) || p[1].equals(usuario))) {
                        continue;
                    }
                    pw.println(line);
                }
            } catch (IOException e) { e.printStackTrace(); }
            blqFile.delete();
            blqTmp.renameTo(blqFile);
        }

        // Eliminar carpetas de usuario
        deleteDirectory(new File(DIR_USUARIOS + "/" + usuario));
        deleteDirectory(new File(DIR_DESCARGAS + "/" + usuario));
    }

    private static void deleteDirectory(File directory) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            directory.delete();
        }
    }

    // ========================= CLASES INTERNAS Y HANDLER =========================

    private static class MessageEntry {
        int id; String display;
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

        private enum InteractionState { NORMAL, RECEIVING_FILE }
        private InteractionState state = InteractionState.NORMAL;
        private FileOutputStream fileOutputStream = null;
        private String receivingFileName = null;

        HandlerCliente(Socket s) { this.socket = s; }

        public PrintWriter getOut() { return out; }

        public synchronized boolean startReceivingFile(String fileName) {
            if (state != InteractionState.NORMAL) return false;
            this.state = InteractionState.RECEIVING_FILE;
            this.receivingFileName = new File(fileName).getName();
            try {
                this.fileOutputStream = new FileOutputStream(DIR_USUARIOS + "/" + this.usuario + "/" + this.receivingFileName);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                resetState();
                return false;
            }
        }

        public synchronized void resetState() {
            try { if (fileOutputStream != null) fileOutputStream.close(); } catch (IOException e) {}
            this.state = InteractionState.NORMAL;
            this.fileOutputStream = null;
            this.receivingFileName = null;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                while (!socket.isClosed()) {
                    this.usuario = null;
                    while (this.usuario == null) {
                        out.println("Ingresa tu usuario o escribe 'nuevo' para crear una cuenta:");
                        String linea = in.readLine();
                        if (linea == null) return;

                        if (linea.equalsIgnoreCase("nuevo")) {
                            out.println("Nuevo usuario (nombre):");
                            String nuevo = in.readLine();
                            out.println("Nueva contraseña:");
                            String pass = in.readLine();
                            if (nuevo == null || pass == null || nuevo.trim().isEmpty() || pass.trim().isEmpty()) {
                                out.println("El nombre de usuario y la contraseña no pueden estar vacíos.");
                                continue;
                            }
                            if (guardarCredencial(nuevo, pass)) {
                                out.println("✅ Cuenta creada. Inicia sesión.");
                            } else {
                                out.println("❌ Error: usuario ya existe.");
                            }
                        } else {
                            out.println("Ingresa tu contraseña:");
                            String pass = in.readLine();
                            if (pass == null) return;
                            if (validarCredencial(linea, pass)) {
                                this.usuario = linea;
                                clientesConectados.put(this.usuario, this);

                                out.println("LOGIN_SUCCESS:" + this.usuario);

                                out.println("✅ Inicio de sesión exitoso. Bienvenido " + this.usuario);
                                List<PendingRequest> misSolicitudes = solicitudesPendientes.get(this.usuario);
                                if (misSolicitudes != null && !misSolicitudes.isEmpty()) {
                                    out.println("🔔 Tienes " + misSolicitudes.size() + " solicitud(es) de archivos pendientes. Usa la opción 10 para verlas.");
                                }
                            } else {
                                out.println("❌ Usuario/contraseña incorrectos.");
                            }
                        }
                    }

                    mostrarMenu();
                    String cmd;
                    while ((cmd = in.readLine()) != null) {
                        if (state == InteractionState.RECEIVING_FILE) {
                            try {
                                if (cmd.equals("FILE_UPLOAD_END")) {
                                    fileOutputStream.close();
                                    out.println("✅ Archivo '" + receivingFileName + "' subido a tu carpeta del servidor.");
                                    resetState();
                                    mostrarMenu();
                                } else {
                                    fileOutputStream.write((cmd + System.lineSeparator()).getBytes());
                                }
                            } catch (IOException e) {
                                out.println("❌ Error al subir el archivo.");
                                resetState();
                            }
                            continue;
                        }

                        if (jugando) {
                            procesarJuego(cmd);
                            if (!jugando) mostrarMenu();
                            continue;
                        }

                        if (cmd.startsWith("START_UPLOAD:")) {
                            String fileName = cmd.split(":", 2)[1];
                            startReceivingFile(fileName);
                            continue;
                        }

                        switch (cmd) {
                            case "1": opciónMandarMensaje(); break;
                            case "2": opciónVerMensajes(); break;
                            case "3": opciónBorrarMensajes(); break;
                            case "4":
                                eliminarCuentaCompleta(this.usuario);
                                out.println("Tu cuenta ha sido eliminada. Se cerrará la conexión.");
                                return;
                            case "5": opciónBloquear(); break;
                            case "6": opciónDesbloquear(); break;
                            case "7": opciónInteractuar(); break;
                            case "8": iniciarJuego(); break;
                            case "9":
                                clientesConectados.remove(this.usuario);
                                out.println("Sesión cerrada. Serás redirigido al menú de inicio.");
                                break;
                            case "10": opcionGestionarSolicitudes(); break;
                            case "11": opcionDescargarArchivos(); break;
                            default:
                                out.println("Opción inválida.");
                        }

                        if (cmd.equals("9")) break;

                        if (!jugando && state == InteractionState.NORMAL) {
                            mostrarMenu();
                        }
                    }
                    if (cmd == null) return;
                }
            } catch (IOException e) {
            } finally {
                if (usuario != null) clientesConectados.remove(usuario);
                solicitudesPendientes.values().forEach(list -> list.removeIf(req -> req.solicitante.equals(usuario)));
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("Cliente " + (usuario != null ? usuario : "desconocido") + " desconectado.");
            }
        }

        private void mostrarMenu() {
            out.println("\n===== MENÚ PRINCIPAL =====");
            out.println("1. Mandar mensaje");
            out.println("2. Ver mensajes recibidos");
            out.println("3. Borrar mensaje por ID");
            out.println("4. Eliminar mi cuenta");
            out.println("5. Bloquear usuario");
            out.println("6. Desbloquear usuario");
            out.println("7. Interactuar con usuarios (Archivos)");
            out.println("8. Jugar a Adivina el Número");
            out.println("9. Cerrar sesión");
            out.println("10. Gestionar solicitudes de archivos");
            out.println("11. Descargar archivos compartidos");
            out.print("Elige una opción: ");
        }

        private void opciónMandarMensaje() throws IOException {
            out.println("--- Enviar Mensaje ---");
            List<String> usuarios = listarUsuarios();
            if (usuarios.size() <= 1) {
                out.println("No hay otros usuarios registrados.");
                return;
            }
            out.println("Usuarios registrados:");
            usuarios.stream().filter(u -> !u.equals(this.usuario)).forEach(u -> out.println("- " + u));

            out.print("Destinatario: ");
            String destinatario = in.readLine();
            if (destinatario == null || destinatario.isBlank() || !usuarios.contains(destinatario)) {
                out.println("Usuario no válido o no encontrado.");
                return;
            }
            if (destinatarioHaBloqueado(this.usuario, destinatario)) {
                out.println("Este usuario te ha bloqueado, no puedes enviarle mensajes.");
                return;
            }
            out.print("Mensaje: ");
            String mensaje = in.readLine();
            if (mensaje == null || mensaje.isBlank()) return;

            guardarMensaje(this.usuario, destinatario, mensaje);
            out.println("✅ Mensaje enviado a " + destinatario + ".");
        }

        private void opciónVerMensajes() {
            out.println("--- Tus Mensajes ---");
            List<MessageEntry> mensajes = listarMensajesConId(this.usuario);
            if(mensajes.isEmpty()) {
                out.println("(Bandeja de entrada vacía)");
            } else {
                mensajes.forEach(m -> out.println("ID " + m.id + " | " + m.display));
            }
        }

        private void opciónBorrarMensajes() throws IOException {
            opciónVerMensajes();
            if (listarMensajesConId(this.usuario).isEmpty()) return;
            out.print("ID del mensaje a borrar: ");
            String idStr = in.readLine();
            try {
                int id = Integer.parseInt(idStr);
                if(borrarMensajePorId(this.usuario, id)) {
                    out.println("Mensaje borrado.");
                } else {
                    out.println("No se encontró un mensaje con ese ID.");
                }
            } catch (NumberFormatException e) {
                out.println("ID no válido.");
            }
        }

        private void opciónBloquear() throws IOException {
            out.print("Usuario a bloquear: ");
            String target = in.readLine();
            if(target != null && !target.isBlank() && !target.equals(this.usuario) && listarUsuarios().contains(target)) {
                agregarBloqueo(this.usuario, target);
                out.println(target + " ha sido bloqueado.");
            } else {
                out.println("Usuario no válido.");
            }
        }

        private void opciónDesbloquear() throws IOException {
            out.print("Usuario a desbloquear: ");
            String target = in.readLine();
            if(target != null && !target.isBlank()) {
                quitarBloqueo(this.usuario, target);
                out.println(target + " ha sido desbloqueado.");
            } else {
                out.println("Entrada inválida.");
            }
        }

        private void opciónInteractuar() throws IOException {
            out.println("--- Interacción de Archivos con Otros Usuarios ---");
            List<String> usuarios = listarUsuarios();
            if (usuarios.size() <= 1) {
                out.println("No hay otros usuarios registrados.");
                return;
            }
            out.println("Usuarios registrados:");
            usuarios.stream().filter(u -> !u.equals(usuario)).forEach(u -> out.println("- " + u));

            out.print("¿Con qué usuario quieres interactuar? (o 'salir'): ");
            String targetUser = in.readLine();
            if (targetUser == null || targetUser.equalsIgnoreCase("salir") || !usuarios.contains(targetUser)) {
                out.println("Usuario inválido o no encontrado.");
                return;
            }

            out.println("\n--- ¿Qué quieres solicitarle a '" + targetUser + "'? ---");
            out.println("1. Ver su lista de archivos");
            out.println("2. Un archivo específico (por nombre)");
            out.println("3. Volver al menú principal");
            out.print("Elige una opción: ");
            String choice = in.readLine();
            if (choice == null) return;

            PendingRequest nuevaSolicitud;
            switch (choice) {
                case "1":
                    nuevaSolicitud = new PendingRequest(this.usuario, "list_files");
                    out.println("✅ Solicitud para ver la lista enviada a " + targetUser + ".");
                    break;
                case "2":
                    out.print("Escribe el nombre del archivo que quieres pedirle: ");
                    String filename = in.readLine();
                    if (filename == null || filename.trim().isEmpty()) return;
                    nuevaSolicitud = new PendingRequest(this.usuario, "get_file:" + filename);
                    out.println("✅ Solicitud para obtener el archivo '" + filename + "' enviada a " + targetUser + ".");
                    break;
                case "3":
                    return;
                default:
                    out.println("Opción inválida.");
                    return;
            }

            solicitudesPendientes.computeIfAbsent(targetUser, k -> new ArrayList<>()).add(nuevaSolicitud);
            HandlerCliente targetHandler = clientesConectados.get(targetUser);
            if (targetHandler != null) {
                targetHandler.getOut().println("🔔 Notificación: El usuario '" + this.usuario + "' te ha enviado una solicitud.");
            }
        }

        private void opcionGestionarSolicitudes() throws IOException {
            out.println("--- Gestionar Solicitudes Pendientes ---");
            List<PendingRequest> misSolicitudes = solicitudesPendientes.get(this.usuario);

            if (misSolicitudes == null || misSolicitudes.isEmpty()) {
                out.println("No tienes solicitudes pendientes.");
                return;
            }

            out.println("Solicitudes pendientes:");
            for (int i = 0; i < misSolicitudes.size(); i++) {
                PendingRequest req = misSolicitudes.get(i);
                String accion = req.tipo.equals("list_files") ? "ver tu lista de archivos" : "descargar el archivo " + req.tipo.split(":",2)[1];
                out.println((i + 1) + ". " + req.solicitante + " quiere " + accion);
            }

            out.print("Elige el NÚMERO de la solicitud a ACEPTAR (o 'salir'): ");
            String seleccion = in.readLine();
            if (seleccion == null || seleccion.equalsIgnoreCase("salir")) return;

            try {
                int index = Integer.parseInt(seleccion) - 1;
                if (index < 0 || index >= misSolicitudes.size()) {
                    out.println("Número de solicitud inválido.");
                    return;
                }

                PendingRequest req = misSolicitudes.get(index);
                HandlerCliente solicitanteHandler = clientesConectados.get(req.solicitante);

                if ("list_files".equals(req.tipo)) {
                    if (solicitanteHandler == null) {
                        out.println("Error: El solicitante debe estar conectado para recibir la lista en tiempo real.");
                        return;
                    }
                    out.println("Aceptando... Enviando lista de archivos a '" + req.solicitante + "'...");
                    File userDir = new File(DIR_USUARIOS + "/" + this.usuario);
                    File[] archivos = userDir.listFiles((d, name) -> name.endsWith(".txt"));
                    PrintWriter solicitanteOut = solicitanteHandler.getOut();
                    solicitanteOut.println("\n--- Archivos de '" + this.usuario + "' ---");
                    if (archivos != null && archivos.length > 0) {
                        for(File archivo : archivos) solicitanteOut.println("- " + archivo.getName());
                    } else {
                        solicitanteOut.println("(Este usuario no tiene archivos para compartir)");
                    }
                    solicitanteOut.println("------------------------------------");
                    solicitanteOut.println("Para descargar un archivo, usa la opción 7 de nuevo.");
                    out.println("✅ Lista de archivos enviada.");
                }
                else if (req.tipo.startsWith("get_file:")) {
                    out.println("Aceptando y preparando archivo para " + req.solicitante + "...");
                    String nombreArchivoOriginal = req.tipo.split(":", 2)[1];
                    File archivoFuente = new File(DIR_USUARIOS + "/" + this.usuario + "/" + nombreArchivoOriginal);

                    if (!archivoFuente.exists()) {
                        out.println("❌ Error: No tienes el archivo '" + nombreArchivoOriginal + "' en tu carpeta del servidor.");
                        if(solicitanteHandler != null) solicitanteHandler.getOut().println("❌ '" + this.usuario + "' aceptó tu solicitud, pero el archivo no fue encontrado.");
                        misSolicitudes.remove(req);
                        return;
                    }

                    File archivoDestino = new File(DIR_USUARIOS+ "/" + req.solicitante + "/" + archivoFuente.getName());
                    Files.copy(archivoFuente.toPath(), archivoDestino.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    guardarMensaje("Servidor", req.solicitante, "Archivo listo: '" + nombreArchivoOriginal + "' de '" + this.usuario + "'. Usa la opción 11.");
                    if (solicitanteHandler != null) solicitanteHandler.getOut().println("📥 Un archivo para ti está listo para descargar (opción 11).");
                    out.println("✅ Archivo compartido con éxito.");
                }
                misSolicitudes.remove(req);
            } catch (NumberFormatException e) {
                out.println("Entrada inválida. Por favor, escribe el NÚMERO de la solicitud.");
            } catch (IOException e) {
                out.println("Error al procesar la solicitud: " + e.getMessage());
            }
        }

        private void opcionDescargarArchivos() throws IOException {
            out.println("--- Archivos en tu Bandeja de Descargas ---");
            File dirUsuario = new File(DIR_DESCARGAS + "/" + this.usuario);
            File[] archivos = dirUsuario.listFiles();

            if (archivos == null || archivos.length == 0) {
                out.println("No hay archivos para ti en este momento.");
                return;
            }

            Map<Integer, File> mapaArchivos = new HashMap<>();
            out.println("Archivos disponibles:");
            for (int i = 0; i < archivos.length; i++) {
                out.println((i + 1) + ". " + archivos[i].getName());
                mapaArchivos.put(i + 1, archivos[i]);
            }

            out.print("Elige el número del archivo a descargar (o 'salir'): ");
            String seleccion = in.readLine();
            try {
                int index = Integer.parseInt(seleccion);
                File archivoADescargar = mapaArchivos.get(index);
                if (archivoADescargar != null) {
                    out.println("FILE_TRANSFER_START:" + archivoADescargar.getName());
                    try (BufferedReader fileReader = new BufferedReader(new FileReader(archivoADescargar))) {
                        String line;
                        while ((line = fileReader.readLine()) != null) out.println(line);
                    }
                    out.println("FILE_TRANSFER_END");
                    archivoADescargar.delete();
                } else {
                    out.println("Número inválido.");
                }
            } catch (NumberFormatException e) {}
        }

        private void iniciarJuego() {
            this.jugando = true;
            this.numeroSecreto = new Random().nextInt(10) + 1;
            this.intentos = 3;
            out.println("\n--- ¡Adivina el Número! ---");
            out.println("He pensado en un número entre 1 y 10. Tienes " + this.intentos + " intentos.");
            out.print("Escribe tu primer intento: ");
        }

        private void procesarJuego(String entradaStr) {
            int intentoNum;
            try {
                intentoNum = Integer.parseInt(entradaStr);
            } catch (NumberFormatException e) {
                out.print("Entrada inválida. Ingresa solo números. Intenta de nuevo: ");
                return;
            }
            if (intentoNum < 1 || intentoNum > 10) {
                out.print("Número fuera de rango (1-10). Intenta de nuevo: ");
                return;
            }
            if (intentoNum == numeroSecreto) {
                out.println("🎉 ¡FELICIDADES! Adivinaste el número: " + numeroSecreto);
                jugando = false;
            } else {
                intentos--;
                if (intentos > 0) {
                    out.println(intentoNum < numeroSecreto ? "El número es MÁS ALTO." : "El número es MÁS BAJO.");
                    out.println("Te quedan " + intentos + " intentos.");
                    out.print("Siguiente intento: ");
                } else {
                    out.println("❌ ¡Se acabaron los intentos! El número secreto era " + numeroSecreto);
                    jugando = false;
                }
            }
        }
    }
}