package interfaz;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.plaf.basic.BasicTreeUI;
import sintactico.NodoAst;
import tokens.CategoriaComponente;
import tokens.ComponenteLexico;
import tokens.TipoComponente;

/** Árbol sintáctico navegable: los no terminales en negrita y los tokens con el color del editor. */
final class ArbolAst extends JPanel {

    /** Contenido de un nodo del árbol; opcionalmente recuerda a qué línea del archivo corresponde. */
    static final class Dato {
        final String plano;
        final String html;
        final String archivo;
        final int linea;
        final int columna;

        Dato(String plano, String html) {
            this(plano, html, null, -1, -1);
        }

        Dato(String plano, String html, String archivo, int linea, int columna) {
            this.plano = plano;
            this.html = html;
            this.archivo = archivo;
            this.linea = linea;
            this.columna = columna;
        }

        @Override
        public String toString() {
            return plano;
        }
    }

    private final DefaultMutableTreeNode raiz = new DefaultMutableTreeNode(new Dato("raiz", "raiz"));
    private final DefaultTreeModel modelo = new DefaultTreeModel(raiz);
    private final JTree arbol = new JTree(modelo);
    private final CardLayout cartas = new CardLayout();
    private final JPanel contenedor = new JPanel(cartas);
    private final EstadoVacio vacio = new EstadoVacio();
    private boolean analizado;

    ArbolAst() {
        super(new BorderLayout());
        arbol.setRootVisible(false);
        arbol.setShowsRootHandles(true);
        arbol.setRowHeight(26);
        arbol.setBackground(Tema.SUPERFICIE);
        arbol.setUI(new InterfazArbol());
        arbol.setCellRenderer(new Pintor());
        arbol.setToggleClickCount(1);

        JScrollPane sp = new JScrollPane(arbol);
        Tema.estilizarScroll(sp);

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        barra.setBackground(Tema.SUPERFICIE);
        barra.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.LINEA));
        Boton expandir = new Boton("Expandir todo", Boton.Tipo.TEXTO);
        Boton contraer = new Boton("Contraer todo", Boton.Tipo.TEXTO);
        expandir.addActionListener(e -> expandirTodo());
        contraer.addActionListener(e -> contraerTodo());
        barra.add(expandir);
        barra.add(contraer);

        JPanel conArbol = new JPanel(new BorderLayout());
        conArbol.add(barra, BorderLayout.NORTH);
        conArbol.add(sp, BorderLayout.CENTER);
        contenedor.add(vacio, "vacio");
        contenedor.add(conArbol, "arbol");
        add(contenedor, BorderLayout.CENTER);
        refrescar();
    }

    // ── API ──────────────────────────────────────────────────────────────────
    void limpiar() {
        analizado = false;
        raiz.removeAllChildren();
        modelo.reload();
        refrescar();
    }

    void agregarArchivo(String archivo, NodoAst descendente, NodoAst ascendente, int reducciones) {
        DefaultMutableTreeNode nodoArchivo = new DefaultMutableTreeNode(
                dato(archivo, "<b>" + esc(archivo) + "</b>"));
        raiz.add(nodoArchivo);
        if (descendente != null) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(dato("Descendente",
                    "<span style='color:#5B6B75'>↓ De la raíz a las hojas</span>"));
            n.add(construir(descendente));
            nodoArchivo.add(n);
        }
        if (ascendente != null) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(dato("Ascendente",
                    "<span style='color:#5B6B75'>↑ De las hojas a la raíz, " + reducciones + " reducciones</span>"));
            n.add(construir(ascendente));
            nodoArchivo.add(n);
        }
    }

    void terminar() {
        analizado = true;
        modelo.reload();
        expandirHasta(4);
        refrescar();
    }

    // ── Construcción ─────────────────────────────────────────────────────────
    private DefaultMutableTreeNode construir(NodoAst nodo) {
        ComponenteLexico t = nodo.getToken();
        DefaultMutableTreeNode n;
        if (t != null) {
            String color = hex(colorDe(t.getTipo()));
            n = new DefaultMutableTreeNode(dato(t.getLexema(),
                    "<span style='font-family:" + Tema.FAMILIA_MONO + ";color:" + color + "'>" + esc(t.getLexema())
                            + "</span> &nbsp;<span style='color:#9AA7AF;font-size:10px'>"
                            + esc(t.getTipo().getNombreVisible()) + "</span>"));
        } else {
            n = new DefaultMutableTreeNode(dato(nodo.getNombre(), "<b>" + esc(nodo.getNombre()) + "</b>"));
        }
        for (NodoAst h : nodo.getHijos()) {
            n.add(construir(h));
        }
        return n;
    }

    private static java.awt.Color colorDe(TipoComponente tipo) {
        CategoriaComponente cat = tipo.getCategoria();
        switch (cat) {
            case PALABRA_RESERVADA:
                switch (tipo) {
                    case TIPO_ENTERO:
                    case TIPO_FLOTANTE:
                    case TIPO_CADENA:
                    case TIPO_CARACTER:
                    case TIPO_BOOLEANO:
                    case VACIO:
                    case CLASE:
                        return Tema.SIN_TIPO;
                    default:
                        return Tema.SIN_CLAVE;
                }
            case LITERAL:
                return tipo == TipoComponente.LITERAL_CADENA || tipo == TipoComponente.LITERAL_CARACTER
                        ? Tema.SIN_CADENA : Tema.SIN_NUMERO;
            case COMENTARIO:
                return Tema.SIN_COMENTARIO;
            case IDENTIFICADOR:
                return Tema.TINTA;
            default:
                return Tema.SIN_SIMBOLO;
        }
    }

    private static Dato dato(String plano, String html) {
        return new Dato(plano, html);
    }

    private static String hex(java.awt.Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Expandir / contraer ──────────────────────────────────────────────────
    private void expandirHasta(int nivel) {
        for (int i = 0; i < arbol.getRowCount(); i++) {
            TreePath p = arbol.getPathForRow(i);
            if (p != null && p.getPathCount() <= nivel) {
                arbol.expandRow(i);
            }
        }
    }

    private void expandirTodo() {
        int fila = 0;
        while (fila < arbol.getRowCount() && fila < 20000) {
            arbol.expandRow(fila);
            fila++;
        }
    }

    private void contraerTodo() {
        for (int i = arbol.getRowCount() - 1; i >= 0; i--) {
            arbol.collapseRow(i);
        }
        expandirHasta(2);
    }

    private void refrescar() {
        if (raiz.getChildCount() > 0) {
            cartas.show(contenedor, "arbol");
        } else {
            vacio.fijar(EstadoVacio.Tono.NEUTRO, analizado ? "No se generó un árbol" : "Aún sin resultados",
                    analizado ? "El archivo está vacío o tiene un error crítico."
                            : "Pulsa Analizar para ver aquí la estructura del programa.");
            cartas.show(contenedor, "vacio");
        }
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────
    static final class Pintor extends JLabel implements TreeCellRenderer {
        Pintor() {
            setOpaque(true);
            setFont(Tema.ui(Font.PLAIN, 13f));
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8));
        }

        @Override
        public Component getTreeCellRendererComponent(JTree t, Object valor, boolean sel, boolean expandido,
                boolean hoja, int fila, boolean foco) {
            Object u = ((DefaultMutableTreeNode) valor).getUserObject();
            setText("<html><body style='color:#14232B'>" + (u instanceof Dato ? ((Dato) u).html : u) + "</body></html>");
            setBackground(sel ? Tema.SELECCION : Tema.SUPERFICIE);
            return this;
        }
    }

    /** Indicadores de expansión tipo chevron, sin líneas de guía. */
    static final class InterfazArbol extends BasicTreeUI {
        InterfazArbol() {
            super();
        }

        @Override
        public void installUI(javax.swing.JComponent c) {
            super.installUI(c);
            setLeftChildIndent(8);
            setRightChildIndent(16);
        }

        @Override
        protected void paintHorizontalPartOfLeg(Graphics g, Rectangle clip, Insets insets, Rectangle bounds,
                TreePath path, int row, boolean expanded, boolean beenExpanded, boolean leaf) {
        }

        @Override
        protected void paintVerticalPartOfLeg(Graphics g, Rectangle clip, Insets insets, TreePath path) {
        }

        @Override
        protected void paintExpandControl(Graphics g, Rectangle clip, Insets insets, Rectangle bounds,
                TreePath path, int row, boolean expanded, boolean beenExpanded, boolean leaf) {
            if (leaf) {
                return;
            }
            Graphics2D g2 = Tema.suavizar(g);
            int cx = bounds.x - getRightChildIndent() + 1;
            int cy = bounds.y + bounds.height / 2;
            g2.setColor(Tema.SUAVE);
            g2.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            if (expanded) {
                g2.drawPolyline(new int[] { cx - 3, cx, cx + 3 }, new int[] { cy - 1, cy + 2, cy - 1 }, 3);
            } else {
                g2.drawPolyline(new int[] { cx - 1, cx + 2, cx - 1 }, new int[] { cy - 3, cy, cy + 3 }, 3);
            }
            g2.dispose();
        }
    }
}
