
package pruebas;

import modelo.persona;
import modelo.personaDAO;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class PruebaRendimiento {

    public static void main(String[] args) throws Exception {

        // Número de contactos utilizados en la prueba
        final int CANTIDAD = 20000;

        // Guardar la ruta original del usuario
        String usuarioOriginal = System.getProperty("user.home");

        // Crear directorio temporal
        Path temporal = Files.createTempDirectory(
                "prueba-rendimiento-");

        try {

            // Evitar modificar los contactos reales
            System.setProperty("user.home", temporal.toString());

            personaDAO dao = new personaDAO();

            // Crear contactos de prueba
            List<persona> datos = new ArrayList<>();

            for (int i = 0; i < CANTIDAD; i++) {

                datos.add(new persona(
                        "Contacto " + i,
                        "09" + String.format("%08d", i),
                        "contacto" + i + "@ejemplo.com",
                        "Trabajo",
                        false
                ));
            }

            dao.actualizarContactos(datos);

            File archivoSin = temporal.resolve(
                    "exportacion_sin_concurrencia.csv").toFile();

            File archivoCon = temporal.resolve(
                    "exportacion_con_concurrencia.csv").toFile();

            // ==========================================
            // PRUEBA 1: SIN PROGRAMACIÓN CONCURRENTE
            // ==========================================

            AtomicLong tiempoSin = new AtomicLong();
            AtomicLong respuestaSin = new AtomicLong();
            AtomicReference<IOException> errorSin =
                    new AtomicReference<>();

            System.out.println("\nPRUEBA SIN CONCURRENCIA");

            SwingUtilities.invokeAndWait(() -> {

                long inicio = System.nanoTime();

                // Solicitar una actualización de la interfaz
                SwingUtilities.invokeLater(() -> {
                    respuestaSin.set(
                            System.nanoTime() - inicio);
                });

                try {

                    // Exportar directamente en el hilo gráfico
                    dao.exportarCSV(archivoSin);

                } catch (IOException ex) {
                    errorSin.set(ex);
                }

                tiempoSin.set(System.nanoTime() - inicio);
            });

            // Esperar a que la actualización gráfica se ejecute
            SwingUtilities.invokeAndWait(() -> {});

            if (errorSin.get() != null) {
                throw errorSin.get();
            }

            // ==========================================
            // PRUEBA 2: CON PROGRAMACIÓN CONCURRENTE
            // ==========================================

            AtomicLong inicioCon = new AtomicLong();
            AtomicLong tiempoCon = new AtomicLong();
            AtomicLong respuestaCon = new AtomicLong();

            System.out.println("\nPRUEBA CON CONCURRENCIA");

            SwingWorker<Void, Void> trabajador =
                    new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground()
                        throws Exception {

                    // Exportar en un hilo secundario
                    dao.exportarCSV(archivoCon);

                    tiempoCon.set(
                            System.nanoTime() - inicioCon.get());

                    return null;
                }
            };

            SwingUtilities.invokeAndWait(() -> {

                long inicio = System.nanoTime();
                inicioCon.set(inicio);

                // Solicitar una actualización de la interfaz
                SwingUtilities.invokeLater(() -> {
                    respuestaCon.set(
                            System.nanoTime() - inicio);
                });

                // Ejecutar exportación en segundo plano
                trabajador.execute();
            });

            // Esperar a que termine la exportación
            trabajador.get();

            // Esperar a que la interfaz procese su actualización
            SwingUtilities.invokeAndWait(() -> {});

            // ==========================================
            // VERIFICACIÓN DE RESULTADOS
            // ==========================================

            boolean archivosIguales = Files.mismatch(
                    archivoSin.toPath(),
                    archivoCon.toPath()) == -1;

            System.out.println("\n===== RESULTADOS =====");

            System.out.println("Contactos procesados: "
                    + CANTIDAD);

            System.out.printf(
                    "Exportación sin concurrencia: %.3f ms%n",
                    tiempoSin.get() / 1_000_000.0);

            System.out.printf(
                    "Exportación con concurrencia: %.3f ms%n",
                    tiempoCon.get() / 1_000_000.0);

            System.out.printf(
                    "Respuesta interfaz sin concurrencia: %.3f ms%n",
                    respuestaSin.get() / 1_000_000.0);

            System.out.printf(
                    "Respuesta interfaz con concurrencia: %.3f ms%n",
                    respuestaCon.get() / 1_000_000.0);

            System.out.println("Archivos CSV idénticos: "
                    + archivosIguales);

            if (archivosIguales) {
                System.out.println(
                        "PRUEBA EXITOSA: datos íntegros.");
            } else {
                System.out.println(
                        "PRUEBA FALLIDA: archivos diferentes.");
            }

        } finally {

            // Restaurar la ruta original del usuario
            System.setProperty(
                    "user.home", usuarioOriginal);
        }
    }
}
