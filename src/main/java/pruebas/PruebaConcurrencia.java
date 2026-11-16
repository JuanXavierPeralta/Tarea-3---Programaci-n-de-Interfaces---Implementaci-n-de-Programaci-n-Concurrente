
package pruebas;

import modelo.persona;
import modelo.personaDAO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PruebaConcurrencia {

    public static void main(String[] args) throws Exception {

        // Utilizar una carpeta temporal para proteger los datos reales
        String directorioOriginal = System.getProperty("user.home");
        Path carpetaTemporal =
                Files.createTempDirectory("prueba-concurrencia-");

        ExecutorService ejecutor = Executors.newFixedThreadPool(2);

        try {
            System.setProperty("user.home",
                    carpetaTemporal.toString());

            // Ambos hilos utilizarán la misma instancia del DAO
            personaDAO dao = new personaDAO();

            // Crear un contacto de prueba
            persona original = new persona(
                    "Prueba Concurrente",
                    "0900000000",
                    "original@ejemplo.com",
                    "Trabajo",
                    false
            );

            dao.guardarSiNoExiste(original);

            // Dos versiones distintas del mismo contacto
            persona cambio1 = new persona(
                    "Prueba Concurrente",
                    "0900000001",
                    "hilo1@ejemplo.com",
                    "Trabajo",
                    false
            );

            persona cambio2 = new persona(
                    "Prueba Concurrente",
                    "0900000002",
                    "hilo2@ejemplo.com",
                    "Trabajo",
                    false
            );

            // Coordinar el inicio de ambos hilos
            CountDownLatch preparados = new CountDownLatch(2);
            CountDownLatch inicio = new CountDownLatch(1);

            Future<Boolean> resultado1 = ejecutor.submit(() -> {
                preparados.countDown();
                inicio.await();

                System.out.println("Hilo 1 intentando modificar...");

                return dao.modificarContactoSeguro(
                        0, original, cambio1);
            });

            Future<Boolean> resultado2 = ejecutor.submit(() -> {
                preparados.countDown();
                inicio.await();

                System.out.println("Hilo 2 intentando modificar...");

                return dao.modificarContactoSeguro(
                        0, original, cambio2);
            });

            // Esperar a que ambos estén preparados
            preparados.await();

            System.out.println("Iniciando modificaciones simultáneas...");

            // Permitir que ambos intenten modificar
            inicio.countDown();

            boolean exito1 = resultado1.get();
            boolean exito2 = resultado2.get();

            System.out.println("\nRESULTADOS:");
            System.out.println("Hilo 1: " + exito1);
            System.out.println("Hilo 2: " + exito2);

            // Comprobar los datos finales
            persona resultadoFinal = dao.leerArchivo().get(0);

            System.out.println("Teléfono final: "
                    + resultadoFinal.getTelefono());

            System.out.println("Contactos registrados: "
                    + dao.leerArchivo().size());

            // Solo una modificación debe tener éxito
            if (exito1 != exito2
                    && dao.leerArchivo().size() == 1) {

                System.out.println(
                        "PRUEBA EXITOSA: sincronización correcta.");

            } else {
                System.out.println(
                        "PRUEBA FALLIDA: revisar sincronización.");
            }

        } finally {

            ejecutor.shutdownNow();

            // Restaurar la ruta original de usuario
            System.setProperty("user.home", directorioOriginal);
        }
    }
}
