package modelo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class personaDAO {

    // Ruta donde se almacenan los contactos
    private final Path archivo;

    // Encabezado del archivo CSV
    private static final String ENCABEZADO
            = "NOMBRE;TELEFONO;EMAIL;CATEGORIA;FAVORITO";

    // Constructor
    public personaDAO() throws IOException {

        Path carpeta = Paths.get(
                System.getProperty("user.home"),
                "gestionContactos"
        );

        Files.createDirectories(carpeta);

        archivo = carpeta.resolve("datosContactos.csv");

        prepararArchivo();
    }

    // Crear el archivo CSV si no existe
    private void prepararArchivo() throws IOException {

        if (Files.notExists(archivo)) {

            try (BufferedWriter escritor
                    = Files.newBufferedWriter(
                            archivo,
                            StandardCharsets.UTF_8)) {

                escritor.write(ENCABEZADO);
                escritor.newLine();
            }
        }
    }

    // Agregar un contacto al archivo
    public synchronized boolean escribirArchivo(persona contacto)
            throws IOException {

        validarContacto(contacto);

        try (BufferedWriter escritor
                = Files.newBufferedWriter(
                        archivo,
                        StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.APPEND)) {

            escritor.write(contacto.datosContacto());
            escritor.newLine();
        }

        return true;
    }

    // Validar duplicados antes de guardar
    public synchronized boolean guardarSiNoExiste(persona nuevo)
            throws IOException {

        validarContacto(nuevo);

        // Obtener contactos registrados
        List<persona> registrados = leerArchivo();

        // Comprobar si ya existe
        for (persona existente : registrados) {

            boolean mismoTelefono = existente.getTelefono().trim()
                    .equals(nuevo.getTelefono().trim());

            boolean mismoEmail = existente.getEmail().trim()
                    .equalsIgnoreCase(nuevo.getEmail().trim());

            if (mismoTelefono || mismoEmail) {
                return false;
            }
        }

        // Si no está duplicado, guardar
        escribirArchivo(nuevo);

        return true;
    }

    // Leer todos los contactos registrados
    public synchronized List<persona> leerArchivo() throws IOException {

        List<persona> personas = new ArrayList<>();

        try (BufferedReader lector
                = Files.newBufferedReader(
                        archivo,
                        StandardCharsets.UTF_8)) {

            String linea;

            // Omitir encabezado
            lector.readLine();

            while ((linea = lector.readLine()) != null) {

                if (linea.trim().isEmpty()) {
                    continue;
                }

                String[] datos = linea.split(";", -1);

                if (datos.length != 5) {
                    throw new IOException(
                            "Registro CSV incorrecto: " + linea);
                }

                persona contacto = new persona(
                        datos[0],
                        datos[1],
                        datos[2],
                        datos[3],
                        Boolean.parseBoolean(datos[4])
                );

                personas.add(contacto);
            }
        }

        return personas;
    }

    // Actualizar todos los contactos del archivo
    public synchronized void actualizarContactos(List<persona> personas)
            throws IOException {

        Path temporal = Files.createTempFile(
                archivo.getParent(),
                "contactos_",
                ".tmp"
        );

        try {

            try (BufferedWriter escritor
                    = Files.newBufferedWriter(
                            temporal,
                            StandardCharsets.UTF_8)) {

                escritor.write(ENCABEZADO);
                escritor.newLine();

                for (persona contacto : personas) {

                    validarContacto(contacto);

                    escritor.write(contacto.datosContacto());
                    escritor.newLine();
                }
            }

            // Reemplazar el archivo original
            Files.move(
                    temporal,
                    archivo,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } finally {
            Files.deleteIfExists(temporal);
        }
    }

    // MODIFICAR CONTACTO CON SINCRONIZACIÓN
    public synchronized boolean modificarContactoSeguro(
            int indice, persona original, persona nuevo)
            throws IOException {

        // Validar los datos nuevos
        validarContacto(nuevo);

        // Leer los contactos más recientes del archivo
        List<persona> registrados = leerArchivo();

        // Verificar que el índice sea válido
        if (indice < 0 || indice >= registrados.size()) {
            return false;
        }

        // Comprobar que el contacto no fue modificado
        // por otro hilo mientras estaba seleccionado
        persona actual = registrados.get(indice);

        if (!mismoContacto(actual, original)) {
            return false;
        }

        // Evitar duplicados con otros contactos
        for (int i = 0; i < registrados.size(); i++) {

            if (i == indice) {
                continue;
            }

            persona existente = registrados.get(i);

            boolean mismoTelefono = existente.getTelefono().trim()
                    .equals(nuevo.getTelefono().trim());

            boolean mismoEmail = existente.getEmail().trim()
                    .equalsIgnoreCase(nuevo.getEmail().trim());

            if (mismoTelefono || mismoEmail) {
                throw new IOException(
                        "El teléfono o correo ya pertenece a otro contacto.");
            }
        }

        // Modificar el contacto
        registrados.set(indice, nuevo);

        // Guardar los datos actualizados
        actualizarContactos(registrados);

        System.out.println("Contacto modificado en: "
                + Thread.currentThread().getName());

        return true;
    }

    // COMPROBAR SI EL CONTACTO SIGUE SIENDO EL MISMO
    private boolean mismoContacto(persona a, persona b) {

        if (a == null || b == null) {
            return false;
        }

        return java.util.Objects.equals(a.getNombre(), b.getNombre())
                && java.util.Objects.equals(a.getTelefono(), b.getTelefono())
                && java.util.Objects.equals(a.getEmail(), b.getEmail())
                && java.util.Objects.equals(a.getCategoria(), b.getCategoria())
                && a.isFavorito() == b.isFavorito();
    }

    // ELIMINAR CONTACTO CON SINCRONIZACIÓN
    public synchronized boolean eliminarContactoSeguro(
            int indice, persona original) throws IOException {

        // Leer los contactos más recientes del archivo
        List<persona> registrados = leerArchivo();

        // Verificar que el índice sea válido
        if (indice < 0 || indice >= registrados.size()) {
            return false;
        }

        // Comprobar que el contacto no haya cambiado
        persona actual = registrados.get(indice);

        if (!mismoContacto(actual, original)) {
            return false;
        }

        // Eliminar el contacto
        registrados.remove(indice);

        // Guardar la lista actualizada
        actualizarContactos(registrados);

        // Mostrar el hilo que realizó la eliminación
        System.out.println("Contacto eliminado en: "
                + Thread.currentThread().getName());

        return true;
    }

    // Validar datos antes de almacenarlos
    private void validarContacto(persona contacto)
            throws IOException {

        if (contacto == null) {
            throw new IOException("El contacto es nulo.");
        }

        String[] campos = {
            contacto.getNombre(),
            contacto.getTelefono(),
            contacto.getEmail(),
            contacto.getCategoria()
        };

        for (String campo : campos) {

            if (campo == null
                    || campo.contains(";")
                    || campo.contains("\n")
                    || campo.contains("\r")) {

                throw new IOException(
                        "El contacto contiene datos no válidos."
                );
            }
        }
    }

// EXPORTAR CONTACTOS A CSV CON SINCRONIZACIÓN
    public synchronized void exportarCSV(File destino)
            throws IOException {

        // Obtener los contactos registrados
        List<persona> personas = leerArchivo();

        // Mostrar el hilo que realiza la exportación
        System.out.println("Exportando CSV en: "
                + Thread.currentThread().getName());

        try (BufferedWriter escritor = Files.newBufferedWriter(
                destino.toPath(), StandardCharsets.UTF_8)) {

            escritor.write(ENCABEZADO);
            escritor.newLine();

            for (persona contacto : personas) {
                escritor.write(contacto.datosContacto());
                escritor.newLine();
            }
        }

        System.out.println("Exportación CSV finalizada.");
    }
}
