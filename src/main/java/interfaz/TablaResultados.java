package interfaz;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

/** Tabla de resultados con estilo plano, estado vacío y salto a la línea del archivo al seleccionar una fila. */
final class TablaResultados extends JPanel {

    interface Salto {
        void ir(String archivo, int linea, int columna);
    }

    private final DefaultTableModel modelo;
    private final JTable tabla;
    private final CardLayout cartas = new CardLayout();
    private final EstadoVacio vacio = new EstadoVacio();
    private final String vacioTitulo;
    private final String vacioDetalle;
    private final boolean vacioEsBueno;
    private boolean analizado;
    private int[] anchos;

    TablaResultados(String[] columnas, int[] anchos, String vacioTitulo, String vacioDetalle, boolean vacioEsBueno,
            Salto salto) {
        super(new BorderLayout());
        this.vacioTitulo = vacioTitulo;
        this.vacioDetalle = vacioDetalle;
        this.vacioEsBueno = vacioEsBueno;

        modelo = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int fila, int col) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        tabla.setRowHeight(32);
        tabla.setShowVerticalLines(false);
        tabla.setShowHorizontalLines(true);
        tabla.setGridColor(new Color(0xEBEFF1));
        tabla.setIntercellSpacing(new Dimension(0, 1));
        tabla.setSelectionBackground(Tema.SELECCION);
        tabla.setSelectionForeground(Tema.TINTA);
        tabla.setBackground(Tema.SUPERFICIE);
        tabla.setFillsViewportHeight(true);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setFocusable(true);
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        tabla.setDefaultRenderer(Object.class, new Celda());

        JTableHeader cab = tabla.getTableHeader();
        cab.setReorderingAllowed(false);
        cab.setPreferredSize(new Dimension(10, 36));
        cab.setDefaultRenderer(new Cabecera());

        this.anchos = anchos.clone();
        int flexible = 0;
        for (int i = 1; i < anchos.length; i++) {
            if (anchos[i] > anchos[flexible]) {
                flexible = i;
            }
        }
        // Solo la columna más ancha (el mensaje) se estira; las demás conservan su ancho
        for (int i = 0; i < anchos.length && i < tabla.getColumnCount(); i++) {
            TableColumn c = tabla.getColumnModel().getColumn(i);
            if (i == flexible) {
                c.setMinWidth(160);
                c.setPreferredWidth(anchos[i]);
            } else {
                c.setMinWidth(anchos[i]);
                c.setMaxWidth(anchos[i]);
                c.setPreferredWidth(anchos[i]);
                c.setResizable(false);
            }
        }

        tabla.getSelectionModel().addListSelectionListener(e -> {
            int fila = tabla.getSelectedRow();
            if (e.getValueIsAdjusting() || fila < 0 || salto == null) {
                return;
            }
            int cPos = modelo.findColumn("Pos.");
            int cArchivo = modelo.findColumn("Archivo");
            if (cPos < 0 || cArchivo < 0) {
                return;
            }
            String[] partes = String.valueOf(modelo.getValueAt(fila, cPos)).split(":");
            try {
                int linea = Integer.parseInt(partes[0].trim());
                int col = partes.length > 1 ? Integer.parseInt(partes[1].trim()) : 1;
                salto.ir(String.valueOf(modelo.getValueAt(fila, cArchivo)), linea, col);
            } catch (NumberFormatException ignorada) {
                // posición no válida: no hay a dónde saltar
            }
        });

        JScrollPane sp = new JScrollPane(tabla);
        sp.setColumnHeaderView(tabla.getTableHeader());
        sp.getColumnHeader().setBackground(Tema.SUPERFICIE);
        Tema.estilizarScroll(sp);

        JPanel contenedor = new JPanel(cartas);
        contenedor.add(vacio, "vacio");
        contenedor.add(sp, "tabla");
        add(contenedor, BorderLayout.CENTER);
        refrescar();
        modelo.addTableModelListener(e -> refrescar(contenedor));
        this.contenedor = contenedor;
    }

    private JPanel contenedor;

    private void refrescar(JPanel c) {
        refrescar();
    }

    private void refrescar() {
        if (modelo.getRowCount() > 0) {
            if (contenedor != null) {
                cartas.show(contenedor, "tabla");
            }
            return;
        }
        if (analizado) {
            vacio.fijar(vacioEsBueno ? EstadoVacio.Tono.OK : EstadoVacio.Tono.NEUTRO, vacioTitulo, vacioDetalle);
        } else {
            vacio.fijar(EstadoVacio.Tono.NEUTRO, "Aún sin resultados", "Pulsa Analizar para ver aquí el resultado.");
        }
        if (contenedor != null) {
            cartas.show(contenedor, "vacio");
        }
    }

    /** Con un solo archivo abierto la columna Archivo sobra: se oculta (sigue en el modelo para el salto). */
    void ocultarArchivo(boolean ocultar) {
        int idx = modelo.findColumn("Archivo");
        if (idx < 0 || idx >= tabla.getColumnCount()) {
            return;
        }
        TableColumn c = tabla.getColumnModel().getColumn(idx);
        int w = ocultar ? 0 : anchos[idx];
        c.setMinWidth(w);
        c.setMaxWidth(w);
        c.setPreferredWidth(w);
    }

    void agregar(Object... fila) {
        modelo.addRow(fila);
    }

    int filas() {
        return modelo.getRowCount();
    }

    void limpiar() {
        analizado = false;
        modelo.setRowCount(0);
        refrescar();
    }

    /** Se llama al terminar de llenar la tabla tras un análisis. */
    void terminar() {
        analizado = true;
        refrescar();
    }

    // ── Dibujo de celdas ─────────────────────────────────────────────────────
    private static boolean esNumerica(String col) {
        return col.equals("Nº") || col.equals("Pos.") || col.equals("Código");
    }

    private final class Celda extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco, int fila,
                int col) {
            super.getTableCellRendererComponent(t, valor, sel, false, fila, col);
            String nombre = t.getColumnName(col);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setBackground(sel ? Tema.SELECCION : Tema.SUPERFICIE);
            setIcon(null);
            setToolTipText(null);
            setHorizontalAlignment(esNumerica(nombre) ? SwingConstants.RIGHT : SwingConstants.LEFT);

            if (esNumerica(nombre)) {
                setFont(Tema.mono(12f));
                setForeground(Tema.PIZARRA);
            } else if (nombre.equals("Lexema")) {
                setFont(Tema.mono(13f));
                setForeground(Tema.TINTA);
            } else if (nombre.equals("Origen")) {
                setFont(Tema.ui(Font.PLAIN, 13f));
                setForeground(Tema.TINTA);
                String s = String.valueOf(valor);
                setIcon(new Punto(s.startsWith("Advertencia") ? Tema.AVISO : Tema.ERROR));
                setIconTextGap(9);
            } else if (nombre.equals("Mensaje")) {
                setFont(Tema.ui(Font.PLAIN, 13f));
                setForeground(Tema.TINTA);
                setToolTipText(String.valueOf(valor));
            } else if (nombre.equals("Archivo") || nombre.equals("Categoría")) {
                setFont(Tema.ui(Font.PLAIN, 13f));
                setForeground(Tema.PIZARRA);
            } else {
                setFont(Tema.ui(Font.PLAIN, 13f));
                setForeground(Tema.TINTA);
            }
            return this;
        }
    }

    private static final class Cabecera extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco, int fila,
                int col) {
            super.getTableCellRendererComponent(t, valor, false, false, fila, col);
            setFont(Tema.ui(Font.BOLD, 12f));
            setForeground(Tema.PIZARRA);
            setBackground(Tema.SUPERFICIE);
            setOpaque(true);
            setHorizontalAlignment(esNumerica(String.valueOf(valor)) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.LINEA),
                    BorderFactory.createEmptyBorder(0, 12, 0, 12)));
            return this;
        }
    }

    private static final class Punto implements Icon {
        private final Color color;

        Punto(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Tema.suavizar(g);
            g2.setColor(color);
            g2.fillOval(x, y, 8, 8);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 8;
        }

        @Override
        public int getIconHeight() {
            return 8;
        }
    }
}
