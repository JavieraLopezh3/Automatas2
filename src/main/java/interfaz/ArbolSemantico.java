package interfaz;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import semantico.NodoSemantico;
import semantico.ResultadoSemantico;

/**
 * Árbol semántico: el programa con cada nodo anotado con su tipo, los ámbitos de las variables
 * y los errores colgando del nodo donde ocurren. Al hacer clic en un nodo se salta a su línea.
 */
final class ArbolSemantico extends JPanel {

    private final DefaultMutableTreeNode raiz = new DefaultMutableTreeNode(new ArbolAst.Dato("raiz", "raiz"));
    private final DefaultTreeModel modelo = new DefaultTreeModel(raiz);
    private final JTree arbol = new JTree(modelo);
    private final CardLayout cartas = new CardLayout();
    private final JPanel contenedor = new JPanel(cartas);
    private final EstadoVacio vacio = new EstadoVacio();
    private final List<DefaultMutableTreeNode> nodosProblema = new ArrayList<>();
    private final TablaResultados.Salto salto;
    private boolean analizado;
    private boolean cargando;

    ArbolSemantico(TablaResultados.Salto salto) {
        super(new BorderLayout());
        this.salto = salto;
        arbol.setRootVisible(false);
        arbol.setShowsRootHandles(true);
        arbol.setRowHeight(26);
        arbol.setBackground(Tema.SUPERFICIE);
        arbol.setUI(new ArbolAst.InterfazArbol());
        arbol.setCellRenderer(new ArbolAst.Pintor());
        arbol.setToggleClickCount(2);
        arbol.addTreeSelectionListener(e -> {
            if (cargando || e.getNewLeadSelectionPath() == null) {
                return;
            }
            Object u = ((DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent()).getUserObject();
            if (u instanceof ArbolAst.Dato) {
                ArbolAst.Dato d = (ArbolAst.Dato) u;
                if (d.archivo != null && d.linea >= 1) {
                    ArbolSemantico.this.salto.ir(d.archivo, d.linea, Math.max(1, d.columna));
                }
            }
        });

        JScrollPane sp = new JScrollPane(arbol);
        Tema.estilizarScroll(sp);

        JLabel ayuda = new JLabel("Cada nodo muestra su tipo; clic para ir a su línea");
        ayuda.setFont(Tema.ui(Font.PLAIN, 12f));
        ayuda.setForeground(Tema.PIZARRA);
        ayuda.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(Tema.SUPERFICIE);
        barra.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.LINEA));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        botones.setOpaque(false);
        Boton expandir = new Boton("Expandir todo", Boton.Tipo.TEXTO);
        Boton contraer = new Boton("Contraer todo", Boton.Tipo.TEXTO);
        Boton errores = new Boton("Ir a errores", Boton.Tipo.TEXTO);
        expandir.addActionListener(e -> expandirTodo());
        contraer.addActionListener(e -> contraerTodo());
        errores.addActionListener(e -> expandirErrores());
        botones.add(errores);
        botones.add(expandir);
        botones.add(contraer);
        barra.add(ayuda, BorderLayout.CENTER);
        barra.add(botones, BorderLayout.EAST);

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
        nodosProblema.clear();
        modelo.reload();
        refrescar();
    }

    void agregarArchivo(String archivo, ResultadoSemantico rs) {
        cargando = true;
        String veredicto;
        String color;
        if (!rs.fueEvaluado()) {
            veredicto = "sin evaluar";
            color = hex(Tema.PIZARRA);
        } else if (rs.esCongruente()) {
            veredicto = rs.getCantidadAdvertencias() > 0 ? "congruente, " + rs.getCantidadAdvertencias() + " aviso(s)"
                    : "congruente";
            color = hex(Tema.OK);
        } else {
            veredicto = "no congruente, " + rs.getCantidadErrores() + " error(es)";
            color = hex(Tema.ERROR);
        }
        DefaultMutableTreeNode nodoArchivo = new DefaultMutableTreeNode(new ArbolAst.Dato(archivo,
                "<b>" + esc(archivo) + "</b> &nbsp;<span style='color:" + color + ";font-size:11px'>" + esc(veredicto)
                        + "</span>"));
        raiz.add(nodoArchivo);
        if (!rs.fueEvaluado() || rs.getArbol() == null) {
            String motivo = rs.getMotivoOmision() != null ? rs.getMotivoOmision() : "No se generó el árbol.";
            nodoArchivo.add(new DefaultMutableTreeNode(new ArbolAst.Dato(motivo, "<span style='color:"
                    + hex(Tema.PIZARRA) + "'>No se construyó el árbol semántico: " + esc(motivo)
                    + " Corrige los errores y vuelve a analizar.</span>")));
        } else {
            nodoArchivo.add(construir(archivo, rs.getArbol()));
        }
        cargando = false;
    }

    void terminar() {
        analizado = true;
        cargando = true;
        modelo.reload();
        expandirHasta(4);
        expandirErrores();
        cargando = false;
        refrescar();
    }

    // ── Construcción ─────────────────────────────────────────────────────────
    private DefaultMutableTreeNode construir(String archivo, NodoSemantico nodo) {
        DefaultMutableTreeNode n = new DefaultMutableTreeNode(
                new ArbolAst.Dato(nodo.getEtiqueta(), html(nodo), archivo, nodo.getLinea(), nodo.getColumna()));
        for (NodoSemantico h : nodo.getHijos()) {
            n.add(construir(archivo, h));
        }
        if (nodo.getCategoria() == NodoSemantico.Categoria.ERROR || nodo.getCategoria() == NodoSemantico.Categoria.AVISO) {
            nodosProblema.add(n);
        }
        return n;
    }

    private static String html(NodoSemantico n) {
        String etiqueta = esc(n.getEtiqueta());
        String tipo = n.getTipo() != null && !n.getTipo().isEmpty() ? n.getTipo() : null;
        StringBuilder sb = new StringBuilder();
        String fuenteMono = "font-family:" + Tema.FAMILIA_MONO + ";";
        switch (n.getCategoria()) {
            case ERROR:
                sb.append("<span style='color:").append(hex(Tema.ERROR)).append("'><b>✖</b> ").append(etiqueta)
                        .append("</span>");
                if (n.getNota() != null) {
                    sb.append(" &nbsp;").append(nota(n.getNota()));
                }
                return sb.toString();
            case AVISO:
                sb.append("<span style='color:").append(hex(Tema.AVISO)).append("'><b>⚠</b> ").append(etiqueta)
                        .append("</span>");
                if (n.getNota() != null) {
                    sb.append(" &nbsp;").append(nota(n.getNota()));
                }
                return sb.toString();
            case PROGRAMA:
            case CLASE:
            case METODO:
                sb.append("<b><span style='color:").append(hex(Tema.PRIMARIO)).append("'>").append(etiqueta)
                        .append("</span></b>");
                break;
            case CONTROL:
                sb.append("<b><span style='color:").append(hex(Tema.SIN_CLAVE)).append("'>").append(etiqueta)
                        .append("</span></b>");
                break;
            case OPERADOR:
                sb.append("<b><span style='").append(fuenteMono).append("color:").append(hex(Tema.SIN_SIMBOLO))
                        .append("'>").append(etiqueta).append("</span></b>");
                break;
            case OPERANDO:
            case SIMBOLO:
                sb.append("<span style='").append(fuenteMono).append("color:").append(hex(Tema.TINTA)).append("'>")
                        .append(etiqueta).append("</span>");
                break;
            case AMBITO:
            case PARAMETROS:
            case SIMBOLOS:
            case EXPRESION:
                sb.append("<span style='color:").append(hex(Tema.PIZARRA)).append("'><b>").append(etiqueta)
                        .append("</b></span>");
                break;
            default:
                sb.append("<b>").append(etiqueta).append("</b>");
                break;
        }
        if (tipo != null) {
            sb.append(" <span style='color:").append(hex(Tema.SUAVE)).append("'>:</span> <b><span style='color:")
                    .append(hex(Tema.SIN_TIPO)).append("'>").append(esc(tipo)).append("</span></b>");
        }
        if (n.getNota() != null) {
            sb.append(" &nbsp;").append(nota(n.getNota()));
        }
        int errores = n.contarErrores();
        int avisos = n.contarAvisos();
        if (errores > 0) {
            sb.append(" &nbsp;<span style='color:").append(hex(Tema.ERROR)).append(";font-size:10px'><b>")
                    .append(errores).append(errores == 1 ? " error" : " errores").append("</b></span>");
        } else if (avisos > 0) {
            sb.append(" &nbsp;<span style='color:").append(hex(Tema.AVISO)).append(";font-size:10px'><b>")
                    .append(avisos).append(avisos == 1 ? " aviso" : " avisos").append("</b></span>");
        }
        return sb.toString();
    }

    private static String nota(String texto) {
        return "<span style='color:" + hex(Tema.SUAVE) + ";font-size:10px'>" + esc(texto) + "</span>";
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

    /** Despliega todos los caminos que llevan a un error o aviso y deja visible el primero. */
    private void expandirErrores() {
        TreePath primero = null;
        for (DefaultMutableTreeNode nodo : nodosProblema) {
            TreePath ruta = new TreePath(nodo.getPath());
            arbol.expandPath(ruta.getParentPath());
            if (primero == null) {
                primero = ruta;
            }
        }
        if (primero != null) {
            arbol.scrollPathToVisible(primero);
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
            vacio.fijar(EstadoVacio.Tono.NEUTRO, analizado ? "No se generó un árbol semántico" : "Aún sin resultados",
                    analizado ? "El archivo está vacío o tiene un error crítico."
                            : "Pulsa Analizar para ver aquí el programa anotado con tipos y ámbitos.");
            cartas.show(contenedor, "vacio");
        }
    }
}
