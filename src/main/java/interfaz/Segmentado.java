package interfaz;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/** Selector de una sola opción (control segmentado). */
final class Segmentado extends JComponent {

    private final String[] opciones;
    private final String[] ayudas;
    private int indice;
    private int sobre = -1;
    private Runnable alCambiar = () -> {
    };

    Segmentado(int inicial, String[] opciones, String[] ayudas) {
        this.opciones = opciones;
        this.ayudas = ayudas;
        this.indice = inicial;
        setFont(Tema.ui(Font.PLAIN, 13f));
        setFocusable(true);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("");
        setRequestFocusEnabled(false);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = segmentoEn(e.getX());
                if (i >= 0) {
                    setIndice(i);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                sobre = -1;
                repaint();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = segmentoEn(e.getX());
                if (i != sobre) {
                    sobre = i;
                    repaint();
                }
            }
        });
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "izq");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "der");
        getActionMap().put("izq", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setIndice(Math.max(0, indice - 1));
            }
        });
        getActionMap().put("der", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setIndice(Math.min(opciones.length - 1, indice + 1));
            }
        });
    }

    int getIndice() {
        return indice;
    }

    void setIndice(int nuevo) {
        if (nuevo != indice) {
            indice = nuevo;
            repaint();
            alCambiar.run();
        }
    }

    void alCambiar(Runnable r) {
        this.alCambiar = r;
    }

    private int[] anchos() {
        FontMetrics fm = getFontMetrics(getFont());
        int[] a = new int[opciones.length];
        for (int i = 0; i < a.length; i++) {
            a[i] = fm.stringWidth(opciones[i]) + 30;
        }
        return a;
    }

    private int segmentoEn(int x) {
        int pos = 3;
        int[] a = anchos();
        for (int i = 0; i < a.length; i++) {
            if (x >= pos && x < pos + a[i]) {
                return i;
            }
            pos += a[i];
        }
        return -1;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = segmentoEn(e.getX());
        return i >= 0 ? ayudas[i] : null;
    }

    @Override
    public Dimension getPreferredSize() {
        int total = 6;
        for (int a : anchos()) {
            total += a;
        }
        return new Dimension(total, 36);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Tema.suavizar(g0);
        int w = getWidth();
        int h = getHeight();
        g.setColor(new java.awt.Color(0xE9EEF0));
        g.fillRoundRect(0, 0, w, h, 11, 11);

        g.setFont(getFont());
        FontMetrics fm = g.getFontMetrics();
        int x = 3;
        int[] a = anchos();
        for (int i = 0; i < a.length; i++) {
            boolean activo = i == indice;
            if (activo) {
                g.setColor(Tema.SUPERFICIE);
                g.fillRoundRect(x, 3, a[i], h - 6, 9, 9);
                g.setColor(Tema.LINEA);
                g.drawRoundRect(x, 3, a[i] - 1, h - 7, 9, 9);
            }
            g.setColor(activo ? Tema.PRIMARIO : (i == sobre ? Tema.TINTA : Tema.PIZARRA));
            g.drawString(opciones[i], x + (a[i] - fm.stringWidth(opciones[i])) / 2,
                    (h - fm.getHeight()) / 2 + fm.getAscent());
            x += a[i];
        }
        if (isFocusOwner()) {
            g.setColor(new java.awt.Color(Tema.PRIMARIO.getRed(), Tema.PRIMARIO.getGreen(),
                    Tema.PRIMARIO.getBlue(), 150));
            g.drawRoundRect(0, 0, w - 1, h - 1, 11, 11);
        }
        g.dispose();
    }
}
