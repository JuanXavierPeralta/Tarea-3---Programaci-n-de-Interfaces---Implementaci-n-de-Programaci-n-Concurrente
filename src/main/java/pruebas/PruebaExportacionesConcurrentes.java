
package pruebas;

import modelo.persona;
import modelo.personaDAO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PruebaExportacionesConcurrentes {

    public static void main(String[] args) throws Exception {

        // Guardar la ruta original del usuario
        String directorioOriginal = System.getProperty("user.home");

        // Crear una carpeta temporal para no afectar los contactos reales
        Path carpetaTemporal =
                Files.createTempDirectory("prueba-exportaciones-");

        // Crear cuatro hilos
        ExecutorService ejecutor = Executors.newFixedThreadPool(4);

        try {

            // Usar almacenamiento temporal
            System.setProperty("user.home",
                    carpetaTemporal.toString());

            // Crear una única instancia compartida del DAO
            personaDAO dao = new personaDAO();

            // Registrar contactos de prueba
            dao.guardarSiNoExiste(new persona(
                    "Contacto Uno",
                    "0900000001",
                    "uno@ejemplo.com",
                    "Familia",
                    false
            ));

            dao.guardarSiNoExiste(new persona(
                    "Contacto Dos",
                    "0900000002",
                    "dos@ejemplo.com",
                    "Amigos",
                    true
            ));

            dao.guardarSiNoExiste(new persona(
                    "Contacto Tres",
                    "0900000003",
                    "tres@ejemplo.com",
                    "Trabajo",
                    false
            ));

            // Archivo de destino compartido por todos los hilos
            Path destino =
                    carpetaTemporal.resolve("exportacion_compartida.csv");

            // Leer el contenido original para compararlo al final
            Path archivoOriginal = carpetaTemporal.resolve(
                    "gestionContactos/datosContactos.csv");

            List<String> contenidoOriginal = Files.readAllLines(
                    archivoOriginal, StandardCharsets.UTF_8);

            // Coordinar el inicio simultáneo
            CountDownLatch preparados = new CountDownLatch(4);
            CountDownLatch inicio = new CountDownLatch(1);

            List<Future<?>> resultados = new ArrayList<>();

            // Crear cuatro tareas de exportación
            for (int i = 1; i <= 4; i++) {

                final int numeroHilo = i;

                resultados.add(ejecutor.submit(() -> {

                    preparados.countDown();
                    inicio.await();

                    System.out.println("Hilo " + numeroHilo
                            + " intentando exportar...");

                    // Método sincronizado del DAO
                    dao.exportarCSV(destino.toFile());

                    return null;
                }));
            }

            // Esperar hasta que todos estén preparados
            preparados.await();

            System.out.println(
                    "Iniciando exportaciones simultáneas...");

            // Liberar los cuatro hilos
            inicio.countDown();

            // Esperar a que terminen las exportaciones
            for (Future<?> resultado : resultados) {
                resultado.get();
            }

            // Comprobar que el archivo existe
            if (!Files.exists(destino)) {
                throw new Exception("No se generó el archivo CSV.");
            }

            // Leer el archivo exportado
            List<String> contenidoExportado = Files.readAllLines(
                    destino, StandardCharsets.UTF_8);

            // Comparar el contenido original y el exportado
            boolean correcto =
                    contenidoOriginal.equals(contenidoExportado);

            System.out.println("\nRESULTADOS:");
            System.out.println("Exportaciones solicitadas: 4");
            System.out.println("Exportaciones finalizadas: 4");

            System.out.println("Contactos originales: "
                    + (contenidoOriginal.size() - 1));

            System.out.println("Contactos exportados: "
                    + (contenidoExportado.size() - 1));

            System.out.println("Archivo generado: " + destino);

            if (correcto) {

                System.out.println(
                        "PRUEBA EXITOSA: CSV íntegro y sincronización correcta.");

            } else {

                System.out.println(
                        "PRUEBA FALLIDA: el contenido del CSV es diferente.");
            }

        } finally {

            // Finalizar los hilos
            ejecutor.shutdownNow();

            // Restaurar la ruta original del usuario
            System.setProperty("user.home", directorioOriginal);
        }
    }
}
