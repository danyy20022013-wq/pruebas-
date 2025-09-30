import java.io.*;
import java.net.*;
import java.util.Scanner;

public class cliente2 {
    private static final String HOST = "localhost";
    private static final int PUERTO = 5050;
    private static String usuarioActual = null;

    public static void main(String[] args) {
        String rutaActual = new File(".").getAbsolutePath();
        System.out.println("--- DEBUG: Mi directorio de trabajo actual es: " + rutaActual);

        System.out.println("Intentando conectar al servidor en " + HOST + ":" + PUERTO);
        while (true) {
            try (Socket socket = new Socket(HOST, PUERTO);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 Scanner scanner = new Scanner(System.in)) {

                System.out.println("¡Conexión exitosa! Escribe /ayuda para ver los comandos locales.");

                Thread lector = new Thread(() -> {
                    String linea;
                    FileOutputStream fos = null;
                    String receivingFileName = null;
                    try {
                        while ((linea = in.readLine()) != null) {

                            if (linea.startsWith("LOGIN_SUCCESS:")) {
                                usuarioActual = linea.split(":", 2)[1];
                                continue;
                            }

                            if (linea.startsWith("FILE_TRANSFER_START:")) {
                                try {
                                    String serverFileName = linea.split(":", 2)[1];
                                    if (serverFileName.contains("_DE_")) {
                                        receivingFileName = "De_" + serverFileName.substring(serverFileName.indexOf("_DE_") + 4);
                                    } else {
                                        receivingFileName = "copia_" + serverFileName;
                                    }

                                    String rutaDeGuardado = receivingFileName;
                                    if (usuarioActual != null) {
                                        File userDir = new File(usuarioActual);
                                        userDir.mkdirs();
                                        rutaDeGuardado = userDir.getName() + File.separator + receivingFileName;
                                    }

                                    System.out.println("\nIniciando descarga de '" + receivingFileName + "' en la carpeta '" + usuarioActual + "'...");
                                    fos = new FileOutputStream(rutaDeGuardado);
                                } catch (IOException e) {
                                    System.out.println("\n❌ Error crítico al crear el archivo local: " + e.getMessage());
                                    if (fos != null) fos.close();
                                    fos = null;
                                }
                            }
                            else if (linea.equals("FILE_TRANSFER_END")) {
                                if (fos != null) {
                                    fos.close();
                                    fos = null;
                                    System.out.println("✅ Descarga de '" + receivingFileName + "' completa.");
                                    System.out.print("> ");
                                }
                            }
                            else if (fos != null) {
                                try {
                                    fos.write((linea + System.lineSeparator()).getBytes());
                                } catch (IOException e) {
                                    System.out.println("\n❌ Error durante la escritura del archivo: " + e.getMessage());
                                    fos.close();
                                    fos = null;
                                }
                            }
                            else if (linea.startsWith("🔔 Notificación:")) {
                                System.out.println("\n" + linea);
                                System.out.print("> ");
                            }
                            else {
                                if (linea.endsWith(": ") || linea.endsWith(":")) {
                                    System.out.print(linea);
                                } else {
                                    System.out.println(linea);
                                }
                                if (linea.contains("Para descargar un archivo, usa la opción 7")) {
                                    System.out.print("> ");
                                }
                            }
                        }
                    } catch (IOException e) {
                    } finally {
                        try { if (fos != null) fos.close(); } catch (IOException e) {}
                    }
                });
                lector.setDaemon(true);
                lector.start();

                while (socket.isConnected()) {
                    System.out.print("> ");
                    String entrada = scanner.nextLine();

                    if (entrada == null) break;

                    if (entrada.startsWith("/")) {
                        manejarComandoLocal(entrada, scanner, out);
                    } else {
                        out.println(entrada);
                    }
                }
            } catch (IOException e) {
                System.out.println("\nConexión con el servidor cerrada. Reintentando en 3 segundos...");
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void manejarComandoLocal(String entrada, Scanner scanner, PrintWriter out) {
        String[] partes = entrada.split(" ", 2);
        String comando = partes[0];
        switch (comando) {
            case "/crear":
                if (partes.length < 2) {
                    System.out.println("Uso: /crear miarchivo.txt");
                } else {
                    crearArchivo(partes[1]);
                }
                break;
            case "/editar":
                if (partes.length < 2) {
                    System.out.println("Uso: /editar miarchivo.txt");
                } else {
                    editarArchivo(partes[1], scanner);
                }
                break;
            case "/subir":
                if (partes.length < 2) {
                    System.out.println("Uso: /subir <archivo_local.txt>");
                } else {
                    subirArchivo(partes[1], out);
                }
                break;
            case "/ayuda":
                System.out.println("--- Comandos Locales ---");
                System.out.println("/crear <nombre.txt>   - Crea un archivo local.");
                System.out.println("/editar <nombre.txt>  - Edita un archivo local.");
                System.out.println("/subir <nombre.txt>   - Sube un archivo a tu carpeta del servidor.");
                System.out.println("/ayuda                  - Muestra esta ayuda.");
                break;
            default:
                System.out.println("Comando local desconocido.");
        }
    }

    private static void subirArchivo(String nombreArchivo, PrintWriter out) {
        File archivo = new File(nombreArchivo);
        if (!archivo.exists()) {
            System.out.println("❌ El archivo local '" + nombreArchivo + "' no existe.");
            return;
        }
        System.out.println("Subiendo archivo al servidor...");
        out.println("START_UPLOAD:" + nombreArchivo);
        try (BufferedReader fileReader = new BufferedReader(new FileReader(archivo))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                out.println(line);
            }
            out.println("FILE_UPLOAD_END");
        } catch (IOException e) {
            System.out.println("❌ Error al leer el archivo local.");
        }
    }

    private static void crearArchivo(String nombreArchivo) {
        if (!nombreArchivo.endsWith(".txt")) {
            System.out.println("Error: El archivo debe terminar en .txt");
            return;
        }
        File archivo = new File(nombreArchivo);
        try {
            if (archivo.createNewFile()) {
                System.out.println("✅ Archivo '" + nombreArchivo + "' creado.");
            } else {
                System.out.println("⚠️ El archivo '" + nombreArchivo + "' ya existe.");
            }
        } catch (IOException e) {
            System.out.println("❌ Error al crear el archivo: " + e.getMessage());
        }
    }

    private static void editarArchivo(String nombreArchivo, Scanner scanner) {
        File archivo = new File(nombreArchivo);
        if (!archivo.exists() || !nombreArchivo.endsWith(".txt")) {
            System.out.println("Error: El archivo '" + nombreArchivo + "' no existe o no es un .txt");
            return;
        }

        System.out.println("--- Editando " + nombreArchivo + " ---");
        System.out.println("(Escribe tu texto. Para finalizar, escribe /guardar en una nueva línea y presiona Enter)");

        StringBuilder contenido = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                contenido.append(currentLine).append(System.lineSeparator());
            }
            System.out.println("--- Contenido Actual --- \n" + (contenido.length() == 0 ? "(Archivo vacío)\n" : contenido.toString()) + "------------------------");
        } catch (IOException e) {
            System.out.println("❌ No se pudo leer el contenido actual del archivo.");
            return;
        }

        while (true) {
            String linea = scanner.nextLine();
            if (linea.equals("/guardar")) {
                break;
            }
            contenido.append(linea).append(System.lineSeparator());
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(archivo))) {
            writer.print(contenido.toString());
            System.out.println("✅ Archivo '" + nombreArchivo + "' guardado con éxito.");
        } catch (IOException e) {
            System.out.println("❌ Error al guardar el archivo: " + e.getMessage());
        }
    }
}