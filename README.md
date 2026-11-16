
# Tarea 3 - Implementación de Programación Concurrente

## Descripción

Aplicación de gestión de contactos desarrollada en **Java Swing**, utilizando el patrón **Modelo-Vista-Controlador (MVC)** y almacenamiento persistente mediante archivos CSV.

Este proyecto corresponde a la **Unidad 3 de Programación de Interfaces Gráficas** de la Universidad Politécnica Salesiana.

El objetivo es implementar programación concurrente para mejorar la capacidad de respuesta de la interfaz gráfica y garantizar la integridad de los datos.

## Funcionalidades implementadas

- Validación de contactos duplicados mediante `SwingWorker`.
- Búsqueda concurrente de contactos.
- Exportación de archivos CSV en segundo plano.
- Notificaciones mediante `SwingUtilities.invokeLater()`.
- Sincronización de modificaciones y eliminaciones mediante `synchronized`.

## Tecnologías utilizadas

- **Lenguaje:** Java 27
- **Interfaz gráfica:** Java Swing
- **IDE:** Apache NetBeans
- **Gestor de proyectos:** Maven
- **Arquitectura:** MVC
- **Almacenamiento:** CSV

## Pruebas realizadas

- Validación de contactos duplicados.
- Búsqueda mediante hilos secundarios.
- Modificación simultánea de contactos con dos hilos.
- Exportación concurrente mediante cuatro hilos.
- Comparación de rendimiento con 20 000 contactos.

### Resultados de rendimiento

| Métrica | Sin concurrencia | Con concurrencia |
|---|---:|---:|
| Tiempo promedio de exportación | 42,628 ms | 41,067 ms |
| Tiempo promedio de respuesta | 42,687 ms | 1,514 ms |

La implementación concurrente permitió reducir aproximadamente un **96,45 %** el tiempo de respuesta del hilo gráfico durante las pruebas.

## Ejecución

1. Clonar o descargar el repositorio.
2. Abrir el proyecto en Apache NetBeans.
3. Compilar el proyecto con Maven.
4. Ejecutar la clase principal `vista.ventana`.

## Autor

**Juan Xavier Peralta León**

Universidad Politécnica Salesiana  
Ingeniería de Software  
Programación de Interfaces Gráficas - Unidad 3
