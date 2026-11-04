package interfaz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.undo.UndoManager;
import lexer.AnalizadorLexico;
import tokens.CategoriaComponente;
import tokens.ComponenteLexico;
import tokens.TipoComponente;

/**
 * Editor de código: números de línea, coloreado de tokens en vivo (usa el propio
 * analizador léxico) y marcas en las líneas con error.
 */
final class EditorCodigo extends JPanel {

    private static final float TAMANO = 13.5f;

    private final PanelTexto texto = new PanelTexto();
    private final Margen margen = new Margen();
    private final JScrollPane scroll;
    private final AnalizadorLexico lexer = new AnalizadorLexico();
    private final javax.swing.Timer retraso;
    private final UndoManager deshacer = new UndoManager();
    private final Map<Integer, Boolean> marcas = new HashMap<>();
    private final List<Object> resaltados = new ArrayList<>();

    private final SimpleAttributeSet estiloBase = new SimpleAttributeSet();
    private final SimpleAttributeSet estiloClave = atributos(Tema.SIN_CLAVE, true, false);
    private final SimpleAttributeSet estiloTipo = atributos(Tema.SIN_TIPO, false, false);
    private final SimpleAttributeSet estiloNumero = atributos(Tema.SIN_NUMERO, false, false);
    private final SimpleAttributeSet estiloCadena = atributos(Tema.SIN_CADENA, false, false);
    private final SimpleAttributeSet estiloComentario = atributos(Tema.SIN_COMENTARIO, false, true);
    private final SimpleAttributeSet estiloSimbolo = atributos(Tema.SIN_SIMBOLO, false, false);

    EditorCodigo(String contenido) {
        super(new BorderLayout());
        setBackground(Tema.SUPERFICIE);

        texto.setFont(Tema.mono(TAMANO));
        texto.setBackground(Tema.SUPERFICIE);
        texto.setForeground(Tema.TINTA);
        texto.setCaretColor(Tema.PRIMARIO);
        texto.setSelectionColor(Tema.SELECCION);
        texto.setSelectedTextColor(Tema.TINTA);
        texto.setMargin(new java.awt.Insets(10, 12, 10, 12));

        StyleConstants.setFontFamily(estiloBase, Tema.FAMILIA_MONO);
        StyleConstants.setFontSize(estiloBase, Math.round(TAMANO));
        StyleConstants.setForeground(estiloBase, Tema.TINTA);
        StyleConstants.setBold(estiloBase, false);
        StyleConstants.setItalic(estiloBase, false);
        Style porDefecto = texto.getStyledDocument().getStyle(StyleContext.DEFAULT_STYLE);
        porDefecto.addAttributes(estiloBase);

        scroll = new JScrollPane(texto);
        scroll.setRowHeaderView(margen);
        Tema.estilizarScroll(scroll);
        add(scroll, BorderLayout.CENTER);

        retraso = new javax.swing.Timer(180, e -> recolorear());
        retraso.setRepeats(false);

        texto.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                alEditar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                alEditar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // cambios de atributos (coloreado): no se reacciona
            }
        });
        texto.getDocument().addUndoableEditListener(e -> {
            if (e.getEdit() instanceof AbstractDocument.DefaultDocumentEvent) {
                AbstractDocument.DefaultDocumentEvent de = (AbstractDocument.DefaultDocumentEvent) e.getEdit();
                if (de.getType() != DocumentEvent.EventType.CHANGE) {
                    deshacer.addEdit(e.getEdit());
                }
            }
        });

        accion("tab4", KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), () -> texto.replaceSelection("    "));
        accion("deshacer", KeyStroke.getKeyStroke(KeyEvent.VK_Z, Tema.atajo()), () -> {
                    if (deshacer.canUndo()) {
                        deshacer.undo();
                    }
                });
        accion("rehacer", KeyStroke.getKeyStroke(KeyEvent.VK_Y, Tema.atajo()), () -> {
                    if (deshacer.canRedo()) {
                        deshacer.redo();
                    }
                });

        setTexto(contenido);
    }

    private void accion(String nombre, KeyStroke tecla, Runnable r) {
        texto.getInputMap().put(tecla, nombre);
        texto.getActionMap().put(nombre, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                r.run();
            }
        });
    }

    private static SimpleAttributeSet atributos(Color c, boolean negrita, boolean cursiva) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, c);
        StyleConstants.setBold(a, negrita);
        StyleConstants.setItalic(a, cursiva);
        return a;
    }

    // ── API ──────────────────────────────────────────────────────────────────
    /** Texto exactamente como lo ve el documento (los índices del analizador léxico coinciden). */
    String getTexto() {
        try {
            StyledDocument d = texto.getStyledDocument();
            return d.getText(0, d.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    void setTexto(String contenido) {
        String limpio = contenido.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        texto.setText(limpio);
        texto.setCaretPosition(0);
        deshacer.discardAllEdits();
        marcas.clear();
        quitarResaltados();
        recolorear();
    }

    /** Marca líneas con error (true) o aviso (false). Se pierde al editar el texto. */
    void marcarLineas(Map<Integer, Boolean> nuevas) {
        marcas.clear();
        marcas.putAll(nuevas);
        quitarResaltados();
        StyledDocument d = texto.getStyledDocument();
        Element raiz = d.getDefaultRootElement();
        for (Map.Entry<Integer, Boolean> e : marcas.entrySet()) {
            int idx = e.getKey() - 1;
            if (idx < 0 || idx >= raiz.getElementCount()) {
                continue;
            }
            Element linea = raiz.getElement(idx);
            int p0 = linea.getStartOffset();
            int p1 = Math.min(Math.max(linea.getEndOffset(), p0 + 1), d.getLength() + 1);
            try {
                resaltados.add(texto.getHighlighter().addHighlight(p0, Math.max(p0 + 1, Math.min(p1, d.getLength())),
                        new PintorLinea(e.getValue() ? new Color(0xFBEAEC) : new Color(0xFCF3E2))));
            } catch (BadLocationException ignorada) {
                // línea fuera de rango
            }
        }
        margen.repaint();
    }

    /** Lleva el cursor a la línea/columna indicadas (base 1) y la centra en pantalla. */
    void irA(int linea, int columna) {
        StyledDocument d = texto.getStyledDocument();
        Element raiz = d.getDefaultRootElement();
        int idx = Math.max(0, Math.min(linea - 1, raiz.getElementCount() - 1));
        Element el = raiz.getElement(idx);
        int pos = Math.min(el.getStartOffset() + Math.max(0, columna - 1), Math.max(el.getStartOffset(),
                el.getEndOffset() - 1));
        texto.requestFocusInWindow();
        texto.setCaretPosition(pos);
        try {
            Rectangle2D r = texto.modelToView2D(pos);
            if (r != null) {
                // Centra la línea en pantalla sin salirse de los límites del documento
                JViewport vp = scroll.getViewport();
                int alto = vp.getExtentSize().height;
                int maximo = Math.max(0, texto.getHeight() - alto);
                int y = (int) Math.max(0, Math.min(maximo, r.getY() - alto / 2.0));
                Point actual = vp.getViewPosition();
                vp.setViewPosition(new Point(actual.x, y));
            }
        } catch (BadLocationException ignorada) {
            // se queda donde está
        }
    }

    // ── Internos ─────────────────────────────────────────────────────────────
    private void alEditar() {
        if (!marcas.isEmpty()) {
            marcas.clear();
            quitarResaltados();
        }
        margen.revalidate();
        margen.repaint();
        retraso.restart();
    }

    private void quitarResaltados() {
        for (Object h : resaltados) {
            texto.getHighlighter().removeHighlight(h);
        }
        resaltados.clear();
    }

    private void recolorear() {
        StyledDocument d = texto.getStyledDocument();
        String t = getTexto();
        if (t.isEmpty()) {
            return;
        }
        d.setCharacterAttributes(0, d.getLength(), estiloBase, true);
        List<ComponenteLexico> comps;
        try {
            comps = lexer.analizar(t).getComponentes();
        } catch (RuntimeException ex) {
            return; // un fallo del léxico nunca debe romper la edición
        }
        for (ComponenteLexico c : comps) {
            AttributeSet estilo = estiloDe(c);
            int ini = c.getIndiceInicio();
            int fin = Math.min(c.getIndiceFin(), d.getLength());
            if (estilo != null && ini >= 0 && fin > ini) {
                d.setCharacterAttributes(ini, fin - ini, estilo, false);
            }
        }
        margen.repaint();
    }

    private AttributeSet estiloDe(ComponenteLexico c) {
        TipoComponente tipo = c.getTipo();
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
                        return estiloTipo;
                    default:
                        return estiloClave;
                }
            case LITERAL:
                return tipo == TipoComponente.LITERAL_CADENA || tipo == TipoComponente.LITERAL_CARACTER
                        ? estiloCadena : estiloNumero;
            case COMENTARIO:
                return estiloComentario;
            case OPERADOR:
            case DELIMITADOR:
                return estiloSimbolo;
            default:
                return null;
        }
    }

    // ── Componentes internos ─────────────────────────────────────────────────
    /** JTextPane sin ajuste de línea (el código no se parte). */
    private static final class PanelTexto extends JTextPane {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            Container padre = getParent();
            return padre instanceof JViewport && getUI().getPreferredSize(this).width <= padre.getWidth();
        }
    }

    /** Pinta la línea completa bajo el texto. */
    private static final class PintorLinea implements Highlighter.HighlightPainter {
        private final Color color;

        PintorLinea(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape limites, JTextComponent c) {
            try {
                Rectangle2D r = c.modelToView2D(p0);
                if (r != null) {
                    g.setColor(color);
                    g.fillRect(0, (int) r.getY(), c.getWidth(), (int) Math.ceil(r.getHeight()));
                }
            } catch (BadLocationException ignorada) {
                // nada que pintar
            }
        }
    }

    /** Margen izquierdo con números de línea y barra de color en las líneas marcadas. */
    private final class Margen extends JComponent {

        Margen() {
            setOpaque(true);
            setBackground(new Color(0xFAFBFB));
        }

        @Override
        public Dimension getPreferredSize() {
            int lineas = texto.getStyledDocument().getDefaultRootElement().getElementCount();
            int digitos = Math.max(3, String.valueOf(lineas).length());
            FontMetrics fm = getFontMetrics(Tema.mono(12f));
            return new Dimension(digitos * fm.charWidth('0') + 30, 10);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Tema.suavizar(g0);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Tema.LINEA);
            g.fillRect(getWidth() - 1, 0, 1, getHeight());

            Rectangle clip = g.getClipBounds();
            Element raiz = texto.getStyledDocument().getDefaultRootElement();
            int primera = raiz.getElementIndex(texto.viewToModel2D(new Point(0, clip.y)));
            int ultima = raiz.getElementIndex(texto.viewToModel2D(new Point(0, clip.y + clip.height)));
            g.setFont(Tema.mono(12f));
            FontMetrics fm = g.getFontMetrics();
            for (int i = primera; i <= ultima; i++) {
                try {
                    Rectangle2D r = texto.modelToView2D(raiz.getElement(i).getStartOffset());
                    if (r == null) {
                        continue;
                    }
                    int y = (int) r.getY();
                    int h = (int) Math.ceil(r.getHeight());
                    Boolean marca = marcas.get(i + 1);
                    if (marca != null) {
                        g.setColor(marca ? Tema.ERROR : Tema.AVISO);
                        g.fillRect(0, y, 3, h);
                    }
                    g.setColor(marca == null ? Tema.SUAVE : (marca ? Tema.ERROR : Tema.AVISO));
                    String n = String.valueOf(i + 1);
                    g.drawString(n, getWidth() - 12 - fm.stringWidth(n), y + (h - fm.getHeight()) / 2 + fm.getAscent());
                } catch (BadLocationException ignorada) {
                    // línea no visible
                }
            }
            g.dispose();
        }
    }
}
