package interfaz;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Colores, tipografías y utilidades de dibujo compartidas por toda la interfaz. */
final class Tema {

    private Tema() {
    }

    // ── Colores ──────────────────────────────────────────────────────────────
    static final Color TINTA = new Color(0x14232B);
    static final Color PIZARRA = new Color(0x5B6B75);
    static final Color SUAVE = new Color(0x9AA7AF);
    static final Color LINEA = new Color(0xDDE3E7);
    static final Color FONDO = new Color(0xF4F6F7);
    static final Color SUPERFICIE = Color.WHITE;

    static final Color PRIMARIO = new Color(0x0E5A63);
    static final Color PRIMARIO_OSCURO = new Color(0x0A444B);
    static final Color PRIMARIO_SUAVE = new Color(0xE3F0F1);
    static final Color SELECCION = new Color(0xD3E8EA);

    static final Color ERROR = new Color(0xB3283D);
    static final Color ERROR_SUAVE = new Color(0xFBEAEC);
    static final Color AVISO = new Color(0xB8740F);
    static final Color AVISO_SUAVE = new Color(0xFCF3E2);
    static final Color OK = new Color(0x2F7D57);
    static final Color OK_SUAVE = new Color(0xE6F3EC);

    // ── Colores de sintaxis del editor ───────────────────────────────────────
    static final Color SIN_CLAVE = PRIMARIO;
    static final Color SIN_TIPO = new Color(0x7A4EA3);
    static final Color SIN_NUMERO = new Color(0xB8640F);
    static final Color SIN_CADENA = OK;
    static final Color SIN_COMENTARIO = new Color(0x8895A0);
    static final Color SIN_SIMBOLO = PIZARRA;

    // ── Tipografías (con alternativas según el sistema) ──────────────────────
    private static final Set<String> FUENTES = new HashSet<>(
            Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    static final String FAMILIA_UI = primera("Segoe UI", "Inter", "SF Pro Text", "Helvetica Neue", "Noto Sans",
            "DejaVu Sans", Font.SANS_SERIF);
    static final String FAMILIA_MONO = primera("Cascadia Code", "Consolas", "JetBrains Mono", "SF Mono", "Menlo",
            "DejaVu Sans Mono", Font.MONOSPACED);

    private static String primera(String... nombres) {
        for (String n : nombres) {
            if (FUENTES.contains(n)) {
                return n;
            }
        }
        return nombres[nombres.length - 1];
    }

    static Font ui(int estilo, float tamano) {
        return new Font(FAMILIA_UI, estilo, Math.round(tamano)).deriveFont(tamano);
    }

    static Font mono(float tamano) {
        return new Font(FAMILIA_MONO, Font.PLAIN, Math.round(tamano)).deriveFont(tamano);
    }

    /** Tecla modificadora de atajos (Ctrl en Windows/Linux, Cmd en Mac). */
    static int atajo() {
        try {
            return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException e) {
            return java.awt.event.InputEvent.CTRL_DOWN_MASK;
        }
    }

    // ── Dibujo ───────────────────────────────────────────────────────────────
    /** Copia del contexto gráfico con suavizado. Quien la pida debe hacer dispose(). */
    static Graphics2D suavizar(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Scroll fino y discreto, sin bordes. */
    static void estilizarScroll(JScrollPane sp) {
        sp.setBorder(null);
        sp.getViewport().setBackground(SUPERFICIE);
        sp.getVerticalScrollBar().setUI(new ScrollFino());
        sp.getHorizontalScrollBar().setUI(new ScrollFino());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(11, 11));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(11, 11));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getHorizontalScrollBar().setUnitIncrement(16);
        sp.setOpaque(false);
    }

    private static final class ScrollFino extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return cero();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return cero();
        }

        private static JButton cero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            // transparente
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = suavizar(g);
            g2.setColor(isThumbRollover() || isDragging ? new Color(0x93A1A9) : new Color(0xC5CED3));
            boolean vertical = scrollbar.getOrientation() == JScrollBar.VERTICAL;
            int arco = 7;
            if (vertical) {
                g2.fillRoundRect(r.x + 2, r.y + 1, r.width - 4, r.height - 2, arco, arco);
            } else {
                g2.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height - 4, arco, arco);
            }
            g2.dispose();
        }
    }
}
