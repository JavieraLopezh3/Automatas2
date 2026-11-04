package interfaz;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JPanel;

/** Mensaje centrado para cuando una vista aún no tiene contenido. */
final class EstadoVacio extends JPanel {

    enum Tono { NEUTRO, OK }

    private Tono tono = Tono.NEUTRO;
    private String titulo = "";
    private String detalle = "";

    EstadoVacio() {
        setOpaque(true);
        setBackground(Tema.SUPERFICIE);
    }

    void fijar(Tono tono, String titulo, String detalle) {
        this.tono = tono;
        this.titulo = titulo;
        this.detalle = detalle;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = Tema.suavizar(g0);
        int cx = getWidth() / 2;
        int cy = getHeight() / 2 - 28;

        boolean ok = tono == Tono.OK;
        g.setColor(ok ? Tema.OK_SUAVE : new Color(0xEEF1F3));
        g.fillOval(cx - 24, cy - 24, 48, 48);
        g.setColor(ok ? Tema.OK : Tema.SUAVE);
        g.setStroke(new java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        if (ok) {
            g.drawPolyline(new int[] { cx - 9, cx - 2, cx + 10 }, new int[] { cy + 1, cy + 8, cy - 7 }, 3);
        } else {
            g.drawLine(cx - 9, cy, cx + 9, cy);
        }

        g.setFont(Tema.ui(Font.BOLD, 15f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(ok ? Tema.OK : Tema.TINTA);
        int y = cy + 24 + 28;
        g.drawString(titulo, cx - fm.stringWidth(titulo) / 2, y);

        g.setFont(Tema.ui(Font.PLAIN, 13f));
        fm = g.getFontMetrics();
        g.setColor(Tema.PIZARRA);
        g.drawString(detalle, cx - fm.stringWidth(detalle) / 2, y + 24);
        g.dispose();
    }
}
