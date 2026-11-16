package controlador;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import idiomas.Idioma;
import java.text.MessageFormat;
import modelo.persona;
import modelo.personaDAO;
import vista.ventana;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.InputMap;
import javax.swing.ActionMap;
import javax.swing.SwingWorker;
import javax.swing.JProgressBar;
import java.util.concurrent.ExecutionException;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import javax.swing.SwingUtilities;

public class logica_ventana implements ActionListener,
        ListSelectionListener {

    // Referencia a la interfaz gráfica
    private final ventana delegado;

    // Acceso a los datos
    private personaDAO dao;

    // Lista de contactos registrados
    private List<persona> contactos = new ArrayList<>();

    // Modelo de la tabla
    private DefaultTableModel modeloTabla;

    // Ordenamiento y filtrado
    private TableRowSorter<DefaultTableModel> ordenador;

    // Control de las búsquedas concurrentes
    private SwingWorker<Set<Integer>, Void> busquedaActual;

// Identificador de la última búsqueda solicitada
    private long numeroBusqueda = 0;

    // Constructor del controlador
    public logica_ventana(ventana delegado) {

        this.delegado = delegado;

        // Preparar tabla
        configurarTabla();

        // Registrar eventos de botones
        delegado.getBtnAgregar().addActionListener(this);
        delegado.getBtnModificar().addActionListener(this);
        delegado.getBtnEliminar().addActionListener(this);
        delegado.getBtnExportar().addActionListener(this);

        // Evento de selección de contactos
        delegado.getTblContactos()
                .getSelectionModel()
                .addListSelectionListener(this);

// Configurar menú contextual del mouse
        configurarMenuContextual();

// Configurar búsqueda
        configurarBusqueda();

// Configurar atajos de teclado
        configurarAtajosTeclado();
        // Inicializar acceso a datos y cargar contactos
        try {
            dao = new personaDAO();
            cargarContactosRegistrados();

        } catch (IOException ex) {
            mostrarErrorConDetalle("mensaje.error.inicializar", ex);
        }
    }

    // CONFIGURACIÓN DE LA TABLA
    private void configurarTabla() {

        String[] columnas = {
            "Nombre",
            "Teléfono",
            "Email",
            "Categoría",
            "Favorito"
        };

        modeloTabla = new DefaultTableModel(columnas, 0) {

            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == 4
                        ? Boolean.class
                        : String.class;
            }
        };

        delegado.getTblContactos().setModel(modeloTabla);

        ordenador = new TableRowSorter<>(modeloTabla);
        delegado.getTblContactos().setRowSorter(ordenador);
    }

    // CONFIGURAR MENÚ CONTEXTUAL CON CLIC DERECHO
    private void configurarMenuContextual() {

        // Crear menú emergente
        JPopupMenu menu = new JPopupMenu();

        // Crear las opciones
        JMenuItem itemEditar = new JMenuItem();
        JMenuItem itemEliminar = new JMenuItem();

        // Agregar opciones al menú
        menu.add(itemEditar);
        menu.addSeparator();
        menu.add(itemEliminar);

        // Evento para editar un contacto
        itemEditar.addActionListener(e -> {

            int indice = obtenerIndiceSeleccionado();

            if (indice != -1) {

                // Los datos ya se cargan al seleccionar la fila.
                // Dar el foco al campo de nombres.
                delegado.getTxtNombres().requestFocusInWindow();

                delegado.getTxtNombres().selectAll();
            }
        });

        // Evento para eliminar un contacto
        itemEliminar.addActionListener(e -> {
            eliminarContacto();
        });

        // Obtener referencia a la tabla
        JTable tabla = delegado.getTblContactos();

        // Registrar evento de mouse
        tabla.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                mostrarMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                mostrarMenu(e);
            }

            private void mostrarMenu(MouseEvent e) {

                // Comprobar si es un clic contextual
                if (e.isPopupTrigger()) {

                    // Identificar la fila bajo el cursor
                    int fila = tabla.rowAtPoint(e.getPoint());

                    if (fila >= 0) {

                        // Seleccionar la fila correspondiente
                        tabla.setRowSelectionInterval(fila, fila);

                        // Traducir el menú cada vez que se abre.
                        itemEditar.setText(Idioma.obtener("menu.editar"));
                        itemEliminar.setText(Idioma.obtener("menu.eliminar"));

                        // Mostrar el menú en la posición del mouse
                        menu.show(
                                tabla,
                                e.getX(),
                                e.getY()
                        );
                    }
                }
            }
        });
    }

    // CONFIGURAR ATAJOS DE TECLADO
    private void configurarAtajosTeclado() {

        // Obtener los mapas de acciones de la ventana
        InputMap mapaTeclas = delegado.getRootPane()
                .getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);

        ActionMap mapaAcciones = delegado.getRootPane()
                .getActionMap();

        // CTRL + N: NUEVO CONTACTO
        mapaTeclas.put(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_N,
                        InputEvent.CTRL_DOWN_MASK
                ),
                "nuevoContacto"
        );

        mapaAcciones.put("nuevoContacto", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                limpiarCampos();
                delegado.getTxtNombres().requestFocusInWindow();
            }
        });

        // CTRL + E: EXPORTAR CSV
        mapaTeclas.put(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_E,
                        InputEvent.CTRL_DOWN_MASK
                ),
                "exportarCSV"
        );

        mapaAcciones.put("exportarCSV", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                delegado.getBtnExportar().doClick();
            }
        });

        // CTRL + F: BUSCAR CONTACTO
        mapaTeclas.put(
                KeyStroke.getKeyStroke(
                        KeyEvent.VK_F,
                        InputEvent.CTRL_DOWN_MASK
                ),
                "buscarContacto"
        );

        mapaAcciones.put("buscarContacto", new AbstractAction() {

            @Override
            public void actionPerformed(ActionEvent e) {
                delegado.getTxtBuscarContactos()
                        .requestFocusInWindow();

                delegado.getTxtBuscarContactos().selectAll();
            }
        });

        // SUPR: ELIMINAR CONTACTO SELECCIONADO
        // Solo funciona cuando la tabla tiene el foco.
        InputMap mapaTabla = delegado.getTblContactos()
                .getInputMap(JComponent.WHEN_FOCUSED);

        mapaTabla.put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                "eliminarContacto"
        );

        delegado.getTblContactos()
                .getActionMap()
                .put("eliminarContacto", new AbstractAction() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        eliminarContacto();
                    }
                });
    }

    // CARGAR CONTACTOS DESDE CSV
    // CARGAR CONTACTOS CON BARRA DE PROGRESO
    private void cargarContactosRegistrados() {

        // Obtener la barra de progreso
        JProgressBar barra = delegado.getPrgCarga();

        // Configuración inicial
        barra.setMinimum(0);
        barra.setMaximum(100);
        barra.setValue(0);
        barra.setStringPainted(true);
        barra.setString(Idioma.obtener("carga.cargando"));
        barra.setIndeterminate(true);

        // Evitar operaciones mientras se cargan los datos
        delegado.getBtnAgregar().setEnabled(false);
        delegado.getBtnModificar().setEnabled(false);
        delegado.getBtnEliminar().setEnabled(false);
        delegado.getBtnExportar().setEnabled(false);

        // Crear tarea en segundo plano
        SwingWorker<List<persona>, Void> trabajador
                = new SwingWorker<List<persona>, Void>() {

            @Override
            protected List<persona> doInBackground()
                    throws Exception {

                // Leer los datos desde el DAO
                return dao.leerArchivo();
            }

            @Override
            protected void done() {

                barra.setIndeterminate(false);

                try {
                    // Obtener los contactos cargados
                    List<persona> datosCargados = get();

                    // Quitar selección anterior
                    // Obtener la tabla
                    JTable tabla = delegado.getTblContactos();

                    // Cancelar búsquedas anteriores
                    if (busquedaActual != null) {
                        busquedaActual.cancel(true);
                    }
                    numeroBusqueda++;

                    // Limpiar selección y desconectar el ordenador
                    tabla.clearSelection();
                    tabla.setRowSorter(null);

                    // Actualizar lista de contactos
                    contactos = datosCargados;

                    // Reconstruir la tabla
                    modeloTabla.setRowCount(0);

                    for (persona contacto : contactos) {

                        Object[] fila = {
                            contacto.getNombre(),
                            contacto.getTelefono(),
                            contacto.getEmail(),
                            contacto.getCategoria(),
                            contacto.isFavorito()
                        };

                        modeloTabla.addRow(fila);
                    }

                    // Crear nuevamente el ordenador
                    ordenador = new TableRowSorter<>(modeloTabla);
                    tabla.setRowSorter(ordenador);

                    // Actualizar estadísticas
                    actualizarEstadisticas();

                    // Aplicar nuevamente la búsqueda
                    filtrarContactos();

                    // Carga completada
                    barra.setValue(100);
                    barra.setString(MessageFormat.format(
                            Idioma.obtener("carga.detalle"), contactos.size()));

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();

                    barra.setValue(0);
                    barra.setString(Idioma.obtener("carga.interrumpida"));
                    mostrarAviso("mensaje.carga.interrumpida");

                } catch (ExecutionException ex) {

                    barra.setValue(0);
                    barra.setString(Idioma.obtener("carga.error"));
                    mostrarErrorConDetalle("mensaje.error.cargar", ex.getCause());

                } finally {

                    // Habilitar nuevamente los botones
                    delegado.getBtnAgregar().setEnabled(true);
                    delegado.getBtnModificar().setEnabled(true);
                    delegado.getBtnEliminar().setEnabled(true);
                    delegado.getBtnExportar().setEnabled(true);
                }
            }
        };

        // Iniciar la tarea
        trabajador.execute();
    }

    // LEER DATOS DE LA INTERFAZ
    private persona obtenerContactoFormulario() {

        String nombre = delegado.getTxtNombres()
                .getText().trim();

        String telefono = delegado.getTxtTelefono()
                .getText().trim();

        String email = delegado.getTxtEmail()
                .getText().trim();

        String categoria = String.valueOf(
                delegado.getCmbCategorias().getSelectedItem()
        );

        boolean favorito = delegado.getChbFavorito()
                .isSelected();

        if (nombre.isEmpty()
                || telefono.isEmpty()
                || email.isEmpty()) {

            mostrarAviso("mensaje.campos.obligatorios");

            return null;
        }

        if (delegado.getCmbCategorias().getSelectedIndex() == 0
                || categoria.trim().isEmpty()) {

            mostrarAviso("mensaje.categoria.requerida");

            return null;
        }

        return new persona(
                nombre,
                telefono,
                email,
                categoria,
                favorito
        );
    }

    // MANEJO DE LOS BOTONES
    @Override
    public void actionPerformed(ActionEvent e) {

        if (dao == null) {
            mostrarAviso("mensaje.almacenamiento.noDisponible");
            return;
        }

        Object origen = e.getSource();

        if (origen == delegado.getBtnAgregar()) {
            agregarContacto();

        } else if (origen == delegado.getBtnModificar()) {
            modificarContacto();

        } else if (origen == delegado.getBtnEliminar()) {
            eliminarContacto();

        } else if (origen == delegado.getBtnExportar()) {
            exportarContactos();
        }
    }

    // AGREGAR CONTACTO
    // AGREGAR CONTACTO CON VALIDACIÓN EN SEGUNDO PLANO
    private void agregarContacto() {

        // Obtener los datos del formulario
        persona contacto = obtenerContactoFormulario();

        if (contacto == null) {
            return;
        }

        // Evitar múltiples pulsaciones mientras se valida
        delegado.getBtnAgregar().setEnabled(false);

        // Crear un hilo secundario mediante SwingWorker
        SwingWorker<Boolean, Void> trabajador
                = new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() throws Exception {

                System.out.println("Validando contacto en: "
                        + Thread.currentThread().getName());

                // Notificar el inicio de la validación
                notificarEstadoDesdeHilo("notificacion.validando");

                // Validar duplicados y guardar el contacto
                boolean guardado = dao.guardarSiNoExiste(contacto);

                // Notificar el resultado de la operación
                if (guardado) {
                    notificarEstadoDesdeHilo("mensaje.contacto.agregado");
                } else {
                    notificarEstadoDesdeHilo("mensaje.contacto.duplicado");
                }

                return guardado;
            }

            @Override
            protected void done() {

                // La operación terminó: habilitar el botón
                delegado.getBtnAgregar().setEnabled(true);

                try {

                    // Obtener el resultado del hilo secundario
                    boolean guardado = get();

                    if (guardado) {

                        // Actualizar la interfaz gráfica
                        limpiarCampos();
                        cargarContactosRegistrados();

                        mostrarInformacion("mensaje.contacto.agregado");

                    } else {

                        // Notificar que el contacto ya existe
                        mostrarAviso("mensaje.contacto.duplicado");
                    }

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();
                    mostrarErrorConDetalle(
                            "mensaje.error.almacenamiento", ex);

                } catch (ExecutionException ex) {

                    mostrarErrorConDetalle(
                            "mensaje.error.almacenamiento",
                            ex.getCause());
                }
            }
        };

        // Iniciar el hilo secundario
        trabajador.execute();
    }

    // MODIFICAR CONTACTO EN SEGUNDO PLANO CON SINCRONIZACIÓN
    private void modificarContacto() {

        // Obtener la fila seleccionada
        int fila = obtenerIndiceSeleccionado();

        if (fila == -1) {
            mostrarAviso("mensaje.contacto.seleccionarModificar");
            return;
        }

        // Obtener los nuevos datos del formulario
        persona contactoNuevo = obtenerContactoFormulario();

        if (contactoNuevo == null) {
            return;
        }

        // Obtener los datos originales para detectar cambios
        persona contactoOriginal = contactos.get(fila);

        // Evitar modificaciones repetidas durante el proceso
        delegado.getBtnModificar().setEnabled(false);

        // Crear trabajador en segundo plano
        SwingWorker<Boolean, Void> trabajador
                = new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() throws Exception {

                System.out.println("Modificando contacto en: "
                        + Thread.currentThread().getName());

                // Modificar mediante el método sincronizado del DAO
                return dao.modificarContactoSeguro(
                        fila,
                        contactoOriginal,
                        contactoNuevo
                );
            }

            @Override
            protected void done() {

                // Habilitar nuevamente el botón
                delegado.getBtnModificar().setEnabled(true);

                try {

                    // Obtener el resultado de la modificación
                    boolean modificado = get();

                    if (modificado) {

                        // Actualizar los contactos
                        limpiarCampos();
                        cargarContactosRegistrados();

                        // Mostrar mensaje de éxito
                        mostrarInformacion("mensaje.contacto.modificado");

                    } else {

                        // Otro hilo pudo haber modificado el contacto
                        cargarContactosRegistrados();

                        mostrarAviso("mensaje.contacto.conflicto");
                    }

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();

                    mostrarErrorConDetalle(
                            "mensaje.error.almacenamiento", ex);

                } catch (ExecutionException ex) {

                    mostrarErrorConDetalle(
                            "mensaje.error.almacenamiento",
                            ex.getCause());
                }
            }
        };

        // Iniciar la modificación en segundo plano
        trabajador.execute();
    }

    // ELIMINAR CONTACTO EN SEGUNDO PLANO CON SINCRONIZACIÓN
    private void eliminarContacto() {

        // Evitar operaciones mientras el botón esté deshabilitado
        if (!delegado.getBtnEliminar().isEnabled()) {
            return;
        }

        // Obtener la fila seleccionada
        int fila = obtenerIndiceSeleccionado();

        if (fila == -1) {
            mostrarAviso("mensaje.contacto.seleccionarEliminar");
            return;
        }

        // Obtener el contacto original
        persona contactoOriginal = contactos.get(fila);

        // Confirmar la eliminación
        if (!confirmar("mensaje.contacto.confirmarEliminar",
                "dialogo.confirmarEliminar")) {
            return;
        }

        // Evitar eliminaciones repetidas durante el proceso
        delegado.getBtnEliminar().setEnabled(false);

        // Crear trabajador en segundo plano
        SwingWorker<Boolean, Void> trabajador
                = new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() throws Exception {

                System.out.println("Eliminando contacto en: "
                        + Thread.currentThread().getName());

                // Eliminar mediante el método sincronizado del DAO
                return dao.eliminarContactoSeguro(
                        fila,
                        contactoOriginal
                );
            }

            @Override
            protected void done() {

                // Habilitar nuevamente el botón
                delegado.getBtnEliminar().setEnabled(true);

                try {

                    // Obtener el resultado de la eliminación
                    boolean eliminado = get();

                    if (eliminado) {

                        // Limpiar formulario y actualizar tabla
                        limpiarCampos();
                        cargarContactosRegistrados();

                        // Mostrar mensaje de éxito
                        mostrarInformacion("mensaje.contacto.eliminado");

                    } else {

                        // El contacto pudo haber cambiado
                        cargarContactosRegistrados();

                        mostrarAviso("mensaje.contacto.conflicto");
                    }

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();

                    mostrarErrorConDetalle(
                            "mensaje.error.almacenamiento", ex);

                } catch (ExecutionException ex) {

                    mostrarErrorConDetalle(
                            "mensaje.error.almacenamiento",
                            ex.getCause());
                }
            }
        };

        // Iniciar la eliminación en segundo plano
        trabajador.execute();
    }

    // EXPORTAR CONTACTOS A CSV
    private void exportarContactos() {

        // Crear ventana para seleccionar archivo
        JFileChooser selector = new JFileChooser();

        selector.setDialogTitle(Idioma.obtener("exportar.titulo"));
        selector.setApproveButtonText(Idioma.obtener("exportar.guardar"));

        // Filtrar archivos CSV
        selector.setFileFilter(
                new FileNameExtensionFilter(Idioma.obtener("exportar.filtroCsv"), "csv")
        );

        // Nombre predeterminado
        selector.setSelectedFile(new File("contactos.csv"));

        // Mostrar ventana para guardar archivo
        int resultado = selector.showSaveDialog(delegado);

        if (resultado == JFileChooser.APPROVE_OPTION) {

            File destino = selector.getSelectedFile();

            // Agregar extensión CSV si no existe
            if (!destino.getName().toLowerCase().endsWith(".csv")) {
                destino = new File(destino.getAbsolutePath() + ".csv");
            }

            try {
                // Evitar sobrescribir el archivo original
                File original = new File(
                        System.getProperty("user.home"),
                        "gestionContactos/datosContactos.csv"
                );

                if (destino.getCanonicalFile()
                        .equals(original.getCanonicalFile())) {

                    mostrarAviso("mensaje.exportar.noOriginal");
                    return;
                }

                // Confirmar si el archivo de destino ya existe
                if (destino.exists()) {

                    if (!confirmar("mensaje.exportar.sobrescribir",
                            "dialogo.confirmarExportar")) {
                        return;
                    }
                }

                // Guardar la referencia al archivo seleccionado
                final File archivoDestino = destino;

                // Crear el trabajador para exportar en segundo plano
                SwingWorker<Void, Void> trabajador
                        = new SwingWorker<Void, Void>() {

                    @Override
                    protected Void doInBackground() throws Exception {

                        // Notificar el inicio de la exportación
                        notificarEstadoDesdeHilo("notificacion.exportando");

                        // Exportar contactos en segundo plano
                        dao.exportarCSV(archivoDestino);

                        // Notificar que la exportación finalizó
                        notificarEstadoDesdeHilo("mensaje.exportar.correcto");

                        return null;
                    }

                    @Override
                    protected void done() {

                        try {

                            // Comprobar que la exportación terminó correctamente
                            get();

                            // Notificar al usuario en la interfaz gráfica
                            mostrarInformacion("mensaje.exportar.correcto");

                        } catch (InterruptedException ex) {

                            Thread.currentThread().interrupt();

                            mostrarErrorConDetalle(
                                    "mensaje.error.almacenamiento", ex);

                        } catch (ExecutionException ex) {

                            mostrarErrorConDetalle(
                                    "mensaje.error.almacenamiento",
                                    ex.getCause());
                        }
                    }
                };

                // Iniciar la exportación en segundo plano
                trabajador.execute();

            } catch (IOException ex) {
                mostrarError(ex);
            }
        }
    }

    // OBTENER FILA SELECCIONADA
    private int obtenerIndiceSeleccionado() {

        int filaVista = delegado.getTblContactos()
                .getSelectedRow();

        if (filaVista == -1) {
            return -1;
        }

        // Convertir índice visual a índice del modelo
        return delegado.getTblContactos()
                .convertRowIndexToModel(filaVista);
    }

    // SELECCIONAR CONTACTO EN JTABLE
    @Override
    public void valueChanged(ListSelectionEvent e) {

        if (e.getValueIsAdjusting()) {
            return;
        }

        int indice = obtenerIndiceSeleccionado();

        if (indice >= 0 && indice < contactos.size()) {

            persona contacto = contactos.get(indice);

            delegado.getTxtNombres()
                    .setText(contacto.getNombre());

            delegado.getTxtTelefono()
                    .setText(contacto.getTelefono());

            delegado.getTxtEmail()
                    .setText(contacto.getEmail());

            delegado.getCmbCategorias()
                    .setSelectedItem(contacto.getCategoria());

            delegado.getChbFavorito()
                    .setSelected(contacto.isFavorito());
        }
    }

    // LIMPIAR CAMPOS
    private void limpiarCampos() {

        delegado.getTblContactos().clearSelection();

        delegado.getTxtNombres().setText("");
        delegado.getTxtTelefono().setText("");
        delegado.getTxtEmail().setText("");

        delegado.getCmbCategorias().setSelectedIndex(0);
        delegado.getChbFavorito().setSelected(false);
    }

    // CONFIGURAR BÚSQUEDA
    private void configurarBusqueda() {

        delegado.getTxtBuscarContactos()
                .getDocument()
                .addDocumentListener(new DocumentListener() {

                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        filtrarContactos();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        filtrarContactos();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        filtrarContactos();
                    }
                });
    }

    // FILTRAR CONTACTOS POR NOMBRE
    // FILTRAR CONTACTOS EN SEGUNDO PLANO
    private void filtrarContactos() {

        // Obtener el texto introducido por el usuario
        String texto = delegado.getTxtBuscarContactos()
                .getText().trim().toLowerCase(Locale.ROOT);

        // Identificar esta nueva búsqueda
        long solicitud = ++numeroBusqueda;

        // Cancelar la búsqueda anterior si todavía está activa
        if (busquedaActual != null) {
            busquedaActual.cancel(true);
        }

        // Mostrar todos los contactos cuando el campo está vacío
        if (texto.isEmpty()) {
            ordenador.setRowFilter(null);
            return;
        }

        // Tomar una copia de los contactos para el hilo
        List<persona> listaOriginal = contactos;
        List<persona> copiaContactos = new ArrayList<>(listaOriginal);

        // Crear el trabajador de búsqueda
        busquedaActual = new SwingWorker<Set<Integer>, Void>() {

            @Override
            protected Set<Integer> doInBackground() {

                System.out.println("Buscando contactos en: "
                        + Thread.currentThread().getName());

                Set<Integer> coincidencias = new HashSet<>();

                // Buscar coincidencias por nombre
                for (int i = 0; i < copiaContactos.size(); i++) {

                    if (isCancelled()) {
                        return coincidencias;
                    }

                    String nombre = copiaContactos.get(i).getNombre();

                    if (nombre != null
                            && nombre.toLowerCase(Locale.ROOT)
                                    .contains(texto)) {

                        coincidencias.add(i);
                    }
                }

                return coincidencias;
            }

            @Override
            protected void done() {

                // Ignorar resultados de búsquedas antiguas
                if (isCancelled()
                        || solicitud != numeroBusqueda
                        || contactos != listaOriginal) {
                    return;
                }

                try {

                    // Obtener los índices encontrados
                    Set<Integer> coincidencias = get();

                    // Mostrar únicamente los contactos encontrados
                    ordenador.setRowFilter(
                            new javax.swing.RowFilter<DefaultTableModel, Integer>() {

                        @Override
                        public boolean include(
                                Entry<? extends DefaultTableModel, ? extends Integer> fila) {

                            return coincidencias.contains(
                                    fila.getIdentifier());
                        }
                    });

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();

                } catch (ExecutionException ex) {

                    mostrarErrorConDetalle(
                            "mensaje.error.cargar", ex.getCause());
                }
            }
        };

        // Ejecutar la búsqueda en segundo plano
        busquedaActual.execute();
    }

    // ACTUALIZAR ESTADÍSTICAS
    private void actualizarEstadisticas() {

        int familia = 0;
        int amigos = 0;
        int trabajo = 0;
        int favoritos = 0;

        for (persona contacto : contactos) {

            switch (contacto.getCategoria()) {

                case "Familia":
                    familia++;
                    break;

                case "Amigos":
                    amigos++;
                    break;

                case "Trabajo":
                    trabajo++;
                    break;
            }

            if (contacto.isFavorito()) {
                favoritos++;
            }
        }

        delegado.getLblTotalNumero()
                .setText(String.valueOf(contactos.size()));

        delegado.getLblFamiliaNumero()
                .setText(String.valueOf(familia));

        delegado.getLblAmigosNumero()
                .setText(String.valueOf(amigos));

        delegado.getLblTrabajoNumero()
                .setText(String.valueOf(trabajo));

        delegado.getLblFavoritosNumero()
                .setText(String.valueOf(favoritos));
    }

    // MENSAJES EN EL IDIOMA SELECCIONADO
    private void mostrarInformacion(String clave) {
        mostrarDialogo(Idioma.obtener(clave), "dialogo.informacion",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // NOTIFICACIONES DESDE HILOS SECUNDARIOS
    private void notificarEstadoDesdeHilo(String clave) {

        // Solicitar una actualización segura de la interfaz
        SwingUtilities.invokeLater(() -> {

            // Obtener el mensaje en el idioma seleccionado
            String mensaje = Idioma.obtener(clave);

            // Actualizar el texto de estado de la aplicación
            delegado.getPrgCarga().setString(mensaje);

            // Comprobar en consola el hilo de la interfaz
            System.out.println("Notificación mostrada en: "
                    + Thread.currentThread().getName()
                    + " - " + mensaje);
        });
    }

    private void mostrarAviso(String clave) {
        mostrarDialogo(Idioma.obtener(clave), "dialogo.advertencia",
                JOptionPane.WARNING_MESSAGE);
    }

    private void mostrarErrorConDetalle(String clave, Throwable ex) {
        String detalle = ex == null ? "" : String.valueOf(ex.getMessage());
        mostrarDialogo(Idioma.obtener(clave) + " " + detalle,
                "dialogo.error", JOptionPane.ERROR_MESSAGE);
    }

    // Botón Aceptar/OK traducido en todos los mensajes.
    private void mostrarDialogo(String mensaje, String claveTitulo, int tipo) {
        Object aceptar = Idioma.obtener("dialogo.aceptar");
        JOptionPane.showOptionDialog(
                delegado, mensaje, Idioma.obtener(claveTitulo),
                JOptionPane.DEFAULT_OPTION, tipo, null,
                new Object[]{aceptar}, aceptar);
    }

    // Sustituye los botones predeterminados de JOptionPane por Sí/No traducidos.
    private boolean confirmar(String claveMensaje, String claveTitulo) {
        Object[] opciones = {
            Idioma.obtener("dialogo.si"),
            Idioma.obtener("dialogo.no")
        };
        int respuesta = JOptionPane.showOptionDialog(
                delegado,
                Idioma.obtener(claveMensaje),
                Idioma.obtener(claveTitulo),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[1]);
        return respuesta == JOptionPane.YES_OPTION;
    }

    // ERRORES DEL DAO
    private void mostrarError(IOException ex) {
        mostrarErrorConDetalle("mensaje.error.almacenamiento", ex);
    }
}
