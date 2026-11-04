package interfaz;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/** Pestañas planas con subrayado en la activa. Las cabeceras son componentes propios (con contador y cierre). */
final class PestanasUI extends BasicTabbedPaneUI {

    static void aplicar(JTabbedPane tabs) {
        tabs.setUI(new PestanasUI());
        tabs.setOpaque(true);
        tabs.setBackground(Tema.SUPERFICIE);
        tabs.setFocusable(false);
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        tabInsets = new Insets(0, 4, 0, 4);
        selectedTabPadInsets = new Insets(0, 0, 0, 0);
        tabAreaInsets = new Insets(0, 8, 0, 8);
        contentBorderInsets = new Insets(1, 0, 0, 0);
    }

    @Override
    protected int getTabLabelShiftX(int tp, int ti, boolean sel) {
        return 0;
    }

    @Override
    protected int getTabLabelShiftY(int tp, int ti, boolean sel) {
        return 0;
    }

    @Override
    protected void paintTabBackground(Graphics g, int tp, int ti, int x, int y, int w, int h, boolean sel) {
        // sin fondo
    }

    @Override
    protected void paintTabBorder(Graphics g, int tp, int ti, int x, int y, int w, int h, boolean sel) {
        if (sel) {
            g.setColor(Tema.PRIMARIO);
            g.fillRect(x + 4, y + h - 2, w - 8, 2);
        }
    }

    @Override
    protected void paintFocusIndicator(Graphics g, int tp, java.awt.Rectangle[] rects, int ti, java.awt.Rectangle iconRect,
            java.awt.Rectangle textRect, boolean sel) {
        // sin indicador
    }

    @Override
    protected void paintContentBorderTopEdge(Graphics g, int tp, int si, int x, int y, int w, int h) {
        g.setColor(Tema.LINEA);
        g.fillRect(x, y, w, 1);
    }

    @Override
    protected void paintContentBorderLeftEdge(Graphics g, int tp, int si, int x, int y, int w, int h) {
    }

    @Override
    protected void paintContentBorderRightEdge(Graphics g, int tp, int si, int x, int y, int w, int h) {
    }

    @Override
    protected void paintContentBorderBottomEdge(Graphics g, int tp, int si, int x, int y, int w, int h) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    /** Cabecera de una pestaña: título, contador opcional y botón de cierre opcional. */
    static final class Cabecera extends JPanel {

        private final JLabel etiqueta = new JLabel();
        private final Insignia insignia = new Insignia();

        Cabecera(JTabbedPane tabs, String titulo, boolean cerrable, Runnable alCerrar) {
            super(new java.awt.GridBagLayout());
            setOpaque(false);
            etiqueta.setText(titulo);
            etiqueta.setFont(Tema.ui(Font.PLAIN, 13f));
            java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
            c.gridy = 0;
            c.anchor = java.awt.GridBagConstraints.WEST;
            add(etiqueta, c);
            insignia.setVisible(false);
            c.insets = new Insets(0, 7, 0, 0);
            add(insignia, c);
            if (cerrable) {
                c.insets = new Insets(0, 6, 0, 0);
                add(new Cierre(alCerrar), c);
            }
            MouseAdapter seleccionar = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = tabs.indexOfTabComponent(Cabecera.this);
                    if (i >= 0) {
                        tabs.setSelectedIndex(i);
                    }
                }
            };
            addMouseListener(seleccionar);
            tabs.addChangeListener(e -> pintarEstado(tabs));
            pintarEstado(tabs);
        }

        private void pintarEstado(JTabbedPane tabs) {
            int i = tabs.indexOfTabComponent(this);
            boolean activa = i >= 0 && i == tabs.getSelectedIndex();
            etiqueta.setForeground(activa ? Tema.TINTA : Tema.PIZARRA);
        }

        /** Muestra un contador junto al título; con alerta se pinta en rojo. */
        void contador(int n, boolean alerta) {
            insignia.fijar(n, alerta);
            insignia.setVisible(true);
            revalidate();
            repaint();
        }

        void ocultarContador() {
            insignia.setVisible(false);
            revalidate();
            repaint();
        }

        void titulo(String t) {
            etiqueta.setText(t);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width + 4, 40);
        }
    }

    private static final class Insignia extends JComponent {
        private String texto = "0";
        private boolean alerta;

        void fijar(int n, boolean alerta) {
            this.texto = String.valueOf(n);
            this.alerta = alerta;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Tema.ui(Font.BOLD, 11f));
            return new Dimension(Math.max(22, fm.stringWidth(texto) + 14), 18);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Tema.suavizar(g0);
            g.setColor(alerta ? Tema.ERROR_SUAVE : new Color(0xE9EEF0));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.setFont(Tema.ui(Font.BOLD, 11f));
            g.setColor(alerta ? Tema.ERROR : Tema.PIZARRA);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(texto, (getWidth() - fm.stringWidth(texto)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    private static final class Cierre extends JComponent {
        private boolean sobre;

        Cierre(Runnable alCerrar) {
            setPreferredSize(new Dimension(18, 18));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setToolTipText("Cerrar archivo");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    sobre = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    sobre = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    alCerrar.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Tema.suavizar(g0);
            if (sobre) {
                g.setColor(Tema.ERROR_SUAVE);
                g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            }
            g.setColor(sobre ? Tema.ERROR : Tema.SUAVE);
            g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int c = getWidth() / 2;
            g.drawLine(c - 3, c - 3, c + 3, c + 3);
            g.drawLine(c - 3, c + 3, c + 3, c - 3);
            g.dispose();
        }
    }
}
