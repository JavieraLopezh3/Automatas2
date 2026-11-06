package interfaz;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import lexer.AnalizadorLexico;
import lexer.ErrorLexico;
import lexer.ResultadoAnalisisLexico;
import semantico.AnalizadorSemantico;
import semantico.ErrorSemantico;
import semantico.ResultadoSemantico;
import sintactico.AnalizadorAscendente;
import sintactico.AnalizadorSintactico;
import sintactico.ErrorSintactico;
import sintactico.NodoAst;
import sintactico.ResultadoAnalisisSintactico;
import sintactico.ResultadoAscendente;
import intermediario.GeneradorIntermedio;
import intermediario.GeneradorEnsamblador;
import intermediario.OptimizadorCuadruplos;
import intermediario.GeneradorEjecutable;
import tokens.CategoriaComponente;
import tokens.ComponenteLexico;

/** Interfaz completa del analizador: editor a la izquierda, resultados a la derecha, veredicto abajo. */
public final class PanelAnalizador extends JPanel {

    private final AnalizadorLexico lexer = new AnalizadorLexico();
    private final AnalizadorSintactico analizadorDescendente = new AnalizadorSintactico();
    private final AnalizadorAscendente analizadorAscendente = new AnalizadorAscendente();
    private final AnalizadorSemantico analizadorSemantico = new AnalizadorSemantico();
    private javax.swing.JComboBox<GeneradorEnsamblador.TargetOS> selectorOS;

    private final Segmentado lectura = new Segmentado(2,
            new String[] { "↓ Descendente", "↑ Ascendente", "Ambas" },
            new String[] {
                "Lee de arriba hacia abajo: construye el árbol desde la raíz hasta las hojas",
                "Lee de abajo hacia arriba: construye el árbol desde las hojas hasta la raíz",
                "Ejecuta las dos lecturas y compara sus resultados" });

    // Izquierda
    private final JTabbedPane pestanasEditor = new JTabbedPane();
    private final Map<String, EditorCodigo> editores = new LinkedHashMap<>();
    private final CardLayout cartasIzquierda = new CardLayout();
    private final JPanel izquierda = new JPanel(cartasIzquierda);

    // Derecha
    private final JTabbedPane pestanasResultado = new JTabbedPane();
    private final ArbolAst arbol = new ArbolAst();
    private final ArbolSemantico arbolSemantico = new ArbolSemantico(this::irA);
    private final javax.swing.JTextArea tCuadruplos = new javax.swing.JTextArea();
    private final javax.swing.JTextArea tEnsamblador = new javax.swing.JTextArea();
    private final TablaResultados tTokens;
    private final TablaResultados tLexicos;
    private final TablaResultados tSintacticos;
    private final Map<String, String> rawAsmCache = new HashMap<>();
    private PestanasUI.Cabecera cabTokens;
    private PestanasUI.Cabecera cabLexicos;
    private PestanasUI.Cabecera cabSintacticos;

    // Abajo
    private final JPanel franja = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 9));
    private final Map<String, Tono> estadosCongruencia = new HashMap<>();

    private File ultimoDirectorio;

    public PanelAnalizador() {
        super(new BorderLayout());
        setBackground(Tema.FONDO);

        tTokens = new TablaResultados(
                new String[] { "Nº", "Archivo", "Lexema", "Token", "Categoría", "Pos." },
                new int[] { 52, 130, 150, 120, 200, 66 },
                "Sin tokens", "El archivo no contiene código.", false, this::irA);
        tLexicos = new TablaResultados(
                new String[] { "Nº", "Archivo", "Lexema", "Mensaje", "Pos." },
                new int[] { 48, 130, 100, 480, 66 },
                "Sin errores léxicos", "Todos los caracteres forman tokens válidos.", true, this::irA);
        tSintacticos = new TablaResultados(
                new String[] { "Nº", "Archivo", "Origen", "Lexema", "Mensaje", "Pos." },
                new int[] { 48, 130, 152, 88, 480, 66 },
                "Sin errores sintácticos", "La estructura del programa es correcta.", true, this::irA);

        add(construirBarra(), BorderLayout.NORTH);
        add(construirCuerpo(), BorderLayout.CENTER);
        add(construirFranja(), BorderLayout.SOUTH);
        restablecerFranja();
        actualizarIzquierda();
        atajos();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Construcción de la interfaz
    // ═════════════════════════════════════════════════════════════════════════
    private JComponent construirBarra() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(Tema.SUPERFICIE);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.LINEA),
                BorderFactory.createEmptyBorder(11, 20, 11, 20)));

        JLabel titulo = new JLabel("Analizador léxico y sintáctico");
        titulo.setFont(Tema.ui(Font.BOLD, 17f));
        titulo.setForeground(Tema.TINTA);
        JLabel etiquetaLectura = new JLabel("Lectura");
        etiquetaLectura.setFont(Tema.ui(Font.PLAIN, 13f));
        etiquetaLectura.setForeground(Tema.PIZARRA);

        selectorOS = new javax.swing.JComboBox<>(GeneradorEnsamblador.TargetOS.values());
        selectorOS.setBackground(Tema.SUPERFICIE);
        selectorOS.setForeground(Tema.TINTA);
        selectorOS.setFont(Tema.ui(Font.PLAIN, 12f));

        barra.add(fila(16, titulo, separadorVertical(), etiquetaLectura, lectura, Box.createHorizontalStrut(10), new JLabel("OS:"), selectorOS), BorderLayout.WEST);

        Boton nuevo = new Boton("Nuevo", Boton.Tipo.TEXTO);
        Boton ejemplos = new Boton("Ejemplos", Boton.Tipo.TEXTO);
        Boton limpiar = new Boton("Limpiar", Boton.Tipo.TEXTO);
        Boton abrir = new Boton("Abrir archivos", Boton.Tipo.SECUNDARIO);
        Boton analizar = new Boton("Analizar", Boton.Tipo.PRIMARIO);
        Boton ejecutar = new Boton("Ejecutar", Boton.Tipo.PRIMARIO);
        nuevo.setToolTipText("Archivo en blanco (Ctrl+N)");
        ejemplos.setToolTipText("Carga la carpeta codigos_prueba");
        abrir.setToolTipText("Elige archivos de tu equipo (Ctrl+O)");
        analizar.setToolTipText("Analiza todos los archivos abiertos (F5)");
        ejecutar.setToolTipText("Genera el binario ejecutable para Linux");
        nuevo.addActionListener(e -> nuevoArchivo());
        ejemplos.addActionListener(e -> cargarEjemplos());
        limpiar.addActionListener(e -> limpiarTodo());
        abrir.addActionListener(e -> abrirArchivos());
        analizar.addActionListener(e -> analizar());
        ejecutar.addActionListener(e -> generarEjecutable());
        barra.add(fila(6, nuevo, ejemplos, limpiar, Box.createHorizontalStrut(6), abrir, analizar, ejecutar), BorderLayout.EAST);
        return barra;
    }

    private JComponent fila(int separacion, Component... cs) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.X_AXIS));
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] instanceof JComponent) {
                ((JComponent) cs[i]).setAlignmentY(0.5f);
            }
            p.add(cs[i]);
            if (i < cs.length - 1) {
                p.add(Box.createHorizontalStrut(separacion));
            }
        }
        return p;
    }

    private JComponent separadorVertical() {
        JComponent c = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Tema.LINEA);
                g.fillRect(0, 0, 1, getHeight());
            }
        };
        c.setPreferredSize(new Dimension(1, 24));
        c.setMaximumSize(new Dimension(1, 24));
        return c;
    }

    private JComponent construirCuerpo() {
        // Izquierda: pestañas por archivo o pantalla de bienvenida
        PestanasUI.aplicar(pestanasEditor);
        izquierda.setBackground(Tema.SUPERFICIE);
        izquierda.add(construirBienvenida(), "bienvenida");
        izquierda.add(pestanasEditor, "editor");

        // Derecha: resultados
        PestanasUI.aplicar(pestanasResultado);
        pestanasResultado.addTab("", arbol);
        pestanasResultado.setTabComponentAt(0, new PestanasUI.Cabecera(pestanasResultado, "Árbol", false, null));
        pestanasResultado.addTab("", arbolSemantico);
        pestanasResultado.setTabComponentAt(1, new PestanasUI.Cabecera(pestanasResultado, "Árbol semántico", false, null));
        
        tCuadruplos.setEditable(false);
        tCuadruplos.setFont(new Font("Monospaced", Font.PLAIN, 13));
        tCuadruplos.setBackground(Tema.FONDO);
        tCuadruplos.setForeground(Tema.TINTA);
        pestanasResultado.addTab("", new javax.swing.JScrollPane(tCuadruplos));
        pestanasResultado.setTabComponentAt(2, new PestanasUI.Cabecera(pestanasResultado, "Cuádruplos", false, null));
        
        tEnsamblador.setEditable(false);
        tEnsamblador.setFont(new Font("Monospaced", Font.PLAIN, 13));
        tEnsamblador.setBackground(Tema.FONDO);
        tEnsamblador.setForeground(Tema.TINTA);
        pestanasResultado.addTab("", new javax.swing.JScrollPane(tEnsamblador));
        pestanasResultado.setTabComponentAt(3, new PestanasUI.Cabecera(pestanasResultado, "Ensamblador", false, null));
        
        pestanasResultado.addTab("", tTokens);
        cabTokens = new PestanasUI.Cabecera(pestanasResultado, "Tokens", false, null);
        pestanasResultado.setTabComponentAt(4, cabTokens);
        pestanasResultado.addTab("", tLexicos);
        cabLexicos = new PestanasUI.Cabecera(pestanasResultado, "Errores léxicos", false, null);
        pestanasResultado.setTabComponentAt(5, cabLexicos);
        pestanasResultado.addTab("", tSintacticos);
        cabSintacticos = new PestanasUI.Cabecera(pestanasResultado, "Errores sintácticos", false, null);
        pestanasResultado.setTabComponentAt(6, cabSintacticos);

        JSplitPane division = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, izquierda, pestanasResultado) {
            private boolean inicial = true;

            @Override
            public void doLayout() {
                if (inicial && getWidth() > 0) {
                    inicial = false;
                    setDividerLocation((int) (getWidth() * 0.42));
                }
                super.doLayout();
            }
        };
        division.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    {
                        setBorder(null);
                    }

                    @Override
                    public void paint(Graphics g) {
                        g.setColor(Tema.FONDO);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(Tema.LINEA);
                        g.fillRect(0, 0, 1, getHeight());
                        g.fillRect(getWidth() - 1, 0, 1, getHeight());
                    }
                };
            }
        });
        division.setBorder(null);
        division.setDividerSize(8);
        division.setResizeWeight(0.42);
        division.setContinuousLayout(true);
        return division;
    }

    private JComponent construirBienvenida() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Tema.SUPERFICIE);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new java.awt.Insets(0, 0, 6, 0);

        JLabel t = new JLabel("Empieza con un archivo");
        t.setFont(Tema.ui(Font.BOLD, 20f));
        t.setForeground(Tema.TINTA);
        p.add(t, c);

        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 22, 0);
        JLabel d = new JLabel("Ábrelo desde tu equipo, escribe uno nuevo o prueba con los ejemplos.");
        d.setFont(Tema.ui(Font.PLAIN, 13f));
        d.setForeground(Tema.PIZARRA);
        p.add(d, c);

        c.gridy++;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        Boton abrir = new Boton("Abrir archivos", Boton.Tipo.PRIMARIO);
        Boton nuevo = new Boton("Escribir código", Boton.Tipo.SECUNDARIO);
        Boton ejemplos = new Boton("Ver ejemplos", Boton.Tipo.TEXTO);
        abrir.addActionListener(e -> abrirArchivos());
        nuevo.addActionListener(e -> nuevoArchivo());
        ejemplos.addActionListener(e -> cargarEjemplos());
        p.add(fila(8, abrir, nuevo, ejemplos), c);
        return p;
    }

    private JComponent construirFranja() {
        franja.setBackground(Tema.FONDO);
        JPanel envoltura = new JPanel(new BorderLayout());
        envoltura.setBackground(Tema.FONDO);
        envoltura.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Tema.LINEA));
        envoltura.add(franja, BorderLayout.CENTER);
        envoltura.setPreferredSize(new Dimension(10, 46));
        return envoltura;
    }

    private void atajos() {
        tecla("analizar", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::analizar);
        tecla("analizar2", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
                Tema.atajo()), this::analizar);
        tecla("abrir", KeyStroke.getKeyStroke(KeyEvent.VK_O,
                Tema.atajo()), this::abrirArchivos);
        tecla("nuevo", KeyStroke.getKeyStroke(KeyEvent.VK_N,
                Tema.atajo()), this::nuevoArchivo);
    }

    private void tecla(String nombre, KeyStroke ks, Runnable r) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(ks, nombre);
        getActionMap().put(nombre, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Archivos
    // ═════════════════════════════════════════════════════════════════════════
    /** Agrega un archivo con el texto dado (también lo usan las pruebas). */
    public void abrirTexto(String nombre, String contenido) {
        String clave = nombreUnico(nombre);
        EditorCodigo editor = new EditorCodigo(contenido);
        editores.put(clave, editor);
        pestanasEditor.addTab("", editor);
        int i = pestanasEditor.getTabCount() - 1;
        pestanasEditor.setTabComponentAt(i, new PestanasUI.Cabecera(pestanasEditor, clave, true,
                () -> cerrarArchivo(clave)));
        pestanasEditor.setSelectedIndex(i);
        actualizarIzquierda();
    }

    private String nombreUnico(String nombre) {
        if (!editores.containsKey(nombre)) {
            return nombre;
        }
        int n = 2;
        while (editores.containsKey(nombre + " (" + n + ")")) {
            n++;
        }
        return nombre + " (" + n + ")";
    }

    private void cerrarArchivo(String clave) {
        EditorCodigo e = editores.remove(clave);
        if (e != null) {
            pestanasEditor.remove(e);
        }
        actualizarIzquierda();
    }

    private void actualizarIzquierda() {
        cartasIzquierda.show(izquierda, editores.isEmpty() ? "bienvenida" : "editor");
    }

    private void nuevoArchivo() {
        abrirTexto("nuevo.java", "");
        EditorCodigo e = editores.get(pestanasEditor.getTabCount() > 0 ? ultimaClave() : "");
        if (e != null) {
            e.irA(1, 1);
        }
    }

    private String ultimaClave() {
        String ultima = "";
        for (String k : editores.keySet()) {
            ultima = k;
        }
        return ultima;
    }

    private void abrirArchivos() {
        File inicio = ultimoDirectorio;
        if (inicio == null) {
            File ejemplos = new File(System.getProperty("user.dir"), "codigos_prueba");
            inicio = ejemplos.isDirectory() ? ejemplos : new File(System.getProperty("user.home"));
        }
        JFileChooser selector = new JFileChooser(inicio);
        selector.setDialogTitle("Selecciona los archivos a analizar (puedes elegir varios)");
        selector.setMultiSelectionEnabled(true);
        selector.setAcceptAllFileFilterUsed(true);
        selector.addChoosableFileFilter(new FileNameExtensionFilter("Código fuente (*.java, *.txt)", "java", "txt"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        ultimoDirectorio = selector.getCurrentDirectory();
        for (File f : selector.getSelectedFiles()) {
            try {
                abrirTexto(f.getName(), leerTexto(f.toPath()));
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "No se pudo leer: " + f.getName(), "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void cargarEjemplos() {
        Path dir = Paths.get(System.getProperty("user.dir"), "codigos_prueba");
        if (!Files.isDirectory(dir)) {
            JOptionPane.showMessageDialog(this,
                    "No se encontró la carpeta 'codigos_prueba' junto al proyecto.\n"
                            + "Usa «Abrir archivos» para elegir los tuyos.",
                    "Sin ejemplos", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        limpiarTodo();
        try (java.util.stream.Stream<Path> lista = Files.list(dir)) {
            List<Path> archivos = new ArrayList<>();
            lista.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".txt"))
                    .sorted().forEach(archivos::add);
            for (Path p : archivos) {
                abrirTexto(p.getFileName().toString(), leerTexto(p));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error al leer los ejemplos.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** UTF-8 si es posible; si no (Bloc de notas de Windows), ISO-8859-1. */
    private static String leerTexto(Path p) throws IOException {
        byte[] bytes = Files.readAllBytes(p);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private void limpiarTodo() {
        editores.clear();
        pestanasEditor.removeAll();
        limpiarResultados();
        actualizarIzquierda();
    }

    private void limpiarResultados() {
        arbol.limpiar();
        arbolSemantico.limpiar();
        tCuadruplos.setText("");
        tEnsamblador.setText("");
        tTokens.limpiar();
        tLexicos.limpiar();
        tSintacticos.limpiar();
        rawAsmCache.clear();

        cabTokens.ocultarContador();
        cabLexicos.ocultarContador();
        cabSintacticos.ocultarContador();
        for (EditorCodigo e : editores.values()) {
            e.marcarLineas(new HashMap<>());
        }
        restablecerFranja();
    }

    /** Selecciona la pestaña del archivo y lleva el cursor a la posición indicada. */
    private void irA(String archivo, int linea, int columna) {
        EditorCodigo e = editores.get(archivo);
        if (e == null) {
            return;
        }
        pestanasEditor.setSelectedComponent(e);
        e.irA(linea, columna);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Análisis
    // ═════════════════════════════════════════════════════════════════════════
    private enum Tono { OK, ERROR, NEUTRO }

    private static final class Veredicto {
        final String archivo;
        final Tono tono;
        final String texto;
        final String ayuda;

        Veredicto(String archivo, Tono tono, String texto, String ayuda) {
            this.archivo = archivo;
            this.tono = tono;
            this.texto = texto;
            this.ayuda = ayuda;
        }
    }

    /** Ejecuta léxico, sintáctico (según la lectura elegida) y congruencia sobre todos los archivos abiertos. */
    public void analizar() {
        if (editores.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero abre o escribe un archivo.", "Nada que analizar",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        limpiarResultados();

        int modo = lectura.getIndice();
        boolean verDesc = modo != 1;
        boolean verAsc = modo != 0;

        int nTok = 1;
        int nLex = 1;
        int nSint = 1;
        Map<String, NodoAst> arboles = new LinkedHashMap<>();
        Map<String, String> omitidos = new LinkedHashMap<>();
        Map<String, Integer> erroresVistos = new HashMap<>();
        Map<String, Map<Integer, Boolean>> marcas = new HashMap<>();

        for (Map.Entry<String, EditorCodigo> entrada : editores.entrySet()) {
            String nombre = entrada.getKey();
            String fuente = entrada.getValue().getTexto();
            marcas.put(nombre, new HashMap<>());
            int vistos = 0;

            ResultadoAnalisisLexico resLex = lexer.analizar(fuente);
            for (ComponenteLexico c : resLex.getComponentes()) {
                if (!c.seIgnora()) {
                    tTokens.agregar(nTok++, nombre, visible(c.getLexema()), c.getTipo().getNombreVisible(),
                            categoria(c.getTipo().getCategoria()), pos(c.getLinea(), c.getColumna()));
                }
            }
            for (ErrorLexico e : resLex.getErrores()) {
                tLexicos.agregar(nLex++, nombre, visible(e.getLexema()), e.getMensaje(),
                        pos(e.getLinea(), e.getColumna()));
                marcar(marcas, nombre, e.getLinea(), true);
                vistos++;
            }

            long significativos = resLex.getComponentes().stream().filter(c -> !c.seIgnora()).count();
            if (significativos == 0) {
                arboles.put(nombre, null);
                omitidos.put(nombre, "El archivo no contiene código.");
                erroresVistos.put(nombre, vistos);
                continue;
            }

            // Se hacen las dos lecturas siempre; el selector solo decide qué se muestra
            ResultadoAnalisisSintactico resDesc = analizadorDescendente.analizar(resLex.getComponentes());
            ResultadoAscendente resAsc = analizadorAscendente.analizar(resLex.getComponentes());
            arboles.put(nombre, resDesc.getRaiz());

            if (verDesc) {
                for (ErrorSintactico e : resDesc.getErrores()) {
                    tSintacticos.agregar(nSint++, nombre, "Descendente", visible(e.getLexema()),
                            e.getMensaje(), pos(e.getLinea(), e.getColumna()));
                    marcar(marcas, nombre, e.getLinea(), true);
                    vistos++;
                }
            }
            if (verAsc) {
                for (ErrorSintactico e : resAsc.getErrores()) {
                    tSintacticos.agregar(nSint++, nombre, "Ascendente", visible(e.getLexema()),
                            e.getMensaje(), pos(e.getLinea(), e.getColumna()));
                    marcar(marcas, nombre, e.getLinea(), true);
                    vistos++;
                }
            }
            erroresVistos.put(nombre, vistos);

            arbol.agregarArchivo(nombre, verDesc ? resDesc.getRaiz() : null, verAsc ? resAsc.getRaiz() : null,
                    resAsc.getReducciones().size());

            if (!resLex.getErrores().isEmpty()) {
                omitidos.put(nombre, "Hay errores léxicos.");
            } else if (!resDesc.getErrores().isEmpty() || !resAsc.getErrores().isEmpty()) {
                omitidos.put(nombre, "Hay errores sintácticos.");
            }
        }

        // Congruencia: todos los archivos juntos, para que los import se resuelvan entre ellos
        Map<String, ResultadoSemantico> semantica = analizadorSemantico.analizar(arboles, omitidos);
        List<Veredicto> veredictos = new ArrayList<>();
        for (Map.Entry<String, ResultadoSemantico> e : semantica.entrySet()) {
            String nombre = e.getKey();
            ResultadoSemantico rs = e.getValue();
            arbolSemantico.agregarArchivo(nombre, rs);
            for (ErrorSemantico h : rs.getHallazgos()) {
                tSintacticos.agregar(nSint++, nombre, h.esError() ? "Incongruencia" : "Advertencia",
                        visible(h.getLexema()), h.getMensaje(), pos(h.getLinea(), h.getColumna()));
                marcar(marcas, nombre, h.getLinea(), h.esError());
            }

            // Generación de Código Intermedio (Cuádruplos)
            NodoAst raiz = arboles.get(nombre);
            if (raiz != null) {
                GeneradorIntermedio gen = new GeneradorIntermedio();
                gen.generar(raiz);
                List<intermediario.Cuadruplo> cuadsOriginales = gen.getQuadruples();
                
                // --- OPTIMIZACIÓN ---
                OptimizadorCuadruplos optimizador = new OptimizadorCuadruplos();
                List<intermediario.Cuadruplo> cuads = optimizador.optimizar(cuadsOriginales);
                
                tCuadruplos.append("--- Archivo: " + nombre + " (Optimizado) ---\n");
                
                tCuadruplos.append(String.format("%-10s %-10s %-10s %-10s\n", "Op", "Arg1", "Arg2", "Result"));
                tCuadruplos.append("--------------------------------------------------\n");
                for (intermediario.Cuadruplo c : cuads) {
                    tCuadruplos.append(c.toString() + "\n");
                }
                tCuadruplos.append("\n");

                // Generación de Código Objetivo (Ensamblador x86) usando cuádruplos optimizados
                GeneradorEnsamblador genAsm = new GeneradorEnsamblador();
                String asmCode = genAsm.generar(cuads, (GeneradorEnsamblador.TargetOS) selectorOS.getSelectedItem());
                rawAsmCache.put(nombre, asmCode);
                tEnsamblador.append("--- Archivo: " + nombre + " ---\n");
                tEnsamblador.append(asmCode + "\n");
            }

            int vistos = erroresVistos.getOrDefault(nombre, 0);

            if (!rs.fueEvaluado()) {
                if (vistos > 0) {
                    veredictos.add(new Veredicto(nombre, Tono.ERROR, vistos == 1 ? "1 error" : vistos + " errores",
                            "Corrige los errores para poder evaluar la congruencia."));
                    estadosCongruencia.put(nombre, Tono.ERROR);
                } else {
                    veredictos.add(new Veredicto(nombre, Tono.NEUTRO, "Sin evaluar", rs.getMotivoOmision()));
                    estadosCongruencia.put(nombre, Tono.NEUTRO);
                }
            } else if (rs.esCongruente()) {
                long av = rs.getCantidadAdvertencias();
                veredictos.add(new Veredicto(nombre, Tono.OK, av == 0 ? "Congruente"
                        : "Congruente, " + av + (av == 1 ? " aviso" : " avisos"), null));
                estadosCongruencia.put(nombre, Tono.OK);
            } else {
                long n = rs.getCantidadErrores();
                veredictos.add(new Veredicto(nombre, Tono.ERROR, "No congruente, " + n
                        + (n == 1 ? " error" : " errores"), "Revisa la pestaña Errores sintácticos."));
                estadosCongruencia.put(nombre, Tono.ERROR);
            }
        }
        
        boolean varios = editores.size() > 1;
        tTokens.ocultarArchivo(!varios);
        tLexicos.ocultarArchivo(!varios);
        tSintacticos.ocultarArchivo(!varios);
        tTokens.terminar();
        tLexicos.terminar();
        tSintacticos.terminar();
        arbol.terminar();
        arbolSemantico.terminar();


        cabTokens.contador(tTokens.filas(), false);
        cabLexicos.contador(tLexicos.filas(), tLexicos.filas() > 0);
        cabSintacticos.contador(tSintacticos.filas(), tSintacticos.filas() > 0);

        for (Map.Entry<String, EditorCodigo> e : editores.entrySet()) {
            e.getValue().marcarLineas(marcas.getOrDefault(e.getKey(), new HashMap<>()));
        }
        mostrarVeredictos(veredictos);

        if (tSintacticos.filas() > 0) {
            pestanasResultado.setSelectedIndex(4);
        } else if (tLexicos.filas() > 0) {
            pestanasResultado.setSelectedIndex(3);
        } else {
            pestanasResultado.setSelectedIndex(0);
        }
    }

    private void generarEjecutable() {
        if (editores.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Primero abre o escribe un archivo.", "Nada que ejecutar",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String nombre = ultimaClave();
        Tono estado = estadosCongruencia.get(nombre);
        if (estado != Tono.OK) {
            JOptionPane.showMessageDialog(this, "El archivo '" + nombre + "' no es congruente.\n" +
                    "Solo se pueden generar binarios de códigos sin errores semánticos.", 
                    "Error de Congruencia", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String textoAsm = rawAsmCache.get(nombre);
        if (textoAsm == null || textoAsm.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No hay código ensamblador generado para este archivo. Pulsa 'Analizar' primero.", 
                    "Sin código", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String nombreBase = nombre.split("\\.")[0];
        GeneradorEjecutable genExe = new GeneradorEjecutable();
        
        // Configuración de rutas portables: buscar carpeta /bin en el directorio actual
        String rutaEjecucion = System.getProperty("user.dir");
        String rutaBin = rutaEjecucion + File.separator + "bin";
        
        // Verificamos si existe la carpeta bin localmente para usarla como prioridad
        if (new File(rutaBin).exists() && new File(rutaBin).isDirectory()) {
            genExe.configurarRutas(rutaBin);
        }

        String resultado;
        if (selectorOS.getSelectedItem() == GeneradorEnsamblador.TargetOS.LINUX) {
            resultado = genExe.generarEjecutableLinux(textoAsm, nombreBase);
        } else {
            resultado = genExe.generarEjecutableWindows(textoAsm, nombreBase);
        }
        
        JOptionPane.showMessageDialog(this, resultado, "Generación de Ejecutable", 
                resultado.contains("exitosamente") ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    private static void marcar(Map<String, Map<Integer, Boolean>> marcas, String archivo, int linea, boolean error) {
        if (linea >= 1) {
            marcas.get(archivo).merge(linea, error, (a, b) -> a || b);
        }
    }

    private static String pos(int linea, int columna) {
        return linea + ":" + columna;
    }

    private static String visible(String s) {
        if (s == null) {
            return "";
        }
        String r = s.replace("\r", "").replace("\n", "↵").replace("\t", "⇥");
        return r.length() > 40 ? r.substring(0, 40) + "…" : r;
    }

    private static String categoria(CategoriaComponente c) {
        switch (c) {
            case PALABRA_RESERVADA: return "Palabra reservada";
            case IDENTIFICADOR: return "Identificador";
            case LITERAL: return "Literal";
            case OPERADOR: return "Operador";
            case DELIMITADOR: return "Delimitador";
            default: return "Comentario";
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Franja de veredictos
    // ═════════════════════════════════════════════════════════════════════════
    private void restablecerFranja() {
        franja.removeAll();
        JLabel l = new JLabel(editores.isEmpty() ? "Abre un archivo para empezar."
                : "Listo. Pulsa Analizar (F5) para revisar los archivos abiertos.");
        l.setFont(Tema.ui(Font.PLAIN, 13f));
        l.setForeground(Tema.PIZARRA);
        l.setBorder(BorderFactory.createEmptyBorder(3, 12, 0, 0));
        franja.add(l);
        franja.revalidate();
        franja.repaint();
    }

    private void mostrarVeredictos(List<Veredicto> lista) {
        franja.removeAll();
        for (Veredicto v : lista) {
            Etiqueta e = new Etiqueta(v);
            e.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent ev) {
                    irA(v.archivo, 1, 1);
                }
            });
            franja.add(e);
        }
        franja.revalidate();
        franja.repaint();
    }

    /** Etiqueta con el resultado de un archivo: punto de color, nombre y veredicto. */
    private static final class Etiqueta extends JComponent {
        private final Veredicto v;

        Etiqueta(Veredicto v) {
            this.v = v;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(v.ayuda);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics nombre = getFontMetrics(Tema.ui(Font.BOLD, 13f));
            FontMetrics texto = getFontMetrics(Tema.ui(Font.PLAIN, 13f));
            return new Dimension(16 + 8 + 10 + nombre.stringWidth(v.archivo) + 10 + texto.stringWidth(v.texto) + 14,
                    28);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Tema.suavizar(g0);
            Color fondo = v.tono == Tono.OK ? Tema.OK_SUAVE : v.tono == Tono.ERROR ? Tema.ERROR_SUAVE
                    : new Color(0xE9EEF0);
            Color color = v.tono == Tono.OK ? Tema.OK : v.tono == Tono.ERROR ? Tema.ERROR : Tema.PIZARRA;
            g.setColor(fondo);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.setColor(color);
            g.fillOval(12, getHeight() / 2 - 4, 8, 8);

            g.setFont(Tema.ui(Font.BOLD, 13f));
            FontMetrics fm = g.getFontMetrics();
            int base = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g.setColor(Tema.TINTA);
            g.drawString(v.archivo, 28, base);
            int x = 28 + fm.stringWidth(v.archivo) + 10;
            g.setFont(Tema.ui(Font.PLAIN, 13f));
            g.setColor(color);
            g.drawString(v.texto, x, base);
            g.dispose();
        }
    }
}
