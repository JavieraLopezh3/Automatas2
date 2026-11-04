package interfaz;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Cursor;
import javax.swing.JButton;

/** Botón plano con tres niveles de énfasis. */
final class Boton extends JButton {

    enum Tipo { PRIMARIO, SECUNDARIO, TEXTO }

    private final Tipo tipo;

    Boton(String texto, Tipo tipo) {
        super(texto);
        this.tipo = tipo;
        setFont(Tema.ui(tipo == Tipo.PRIMARIO ? Font.BOLD : Font.PLAIN, 13f));
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);
        setRequestFocusEnabled(false); // el clic con ratón no deja un anillo de foco; con Tab sí aparece
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int relleno = tipo == Tipo.PRIMARIO ? 22 : 16;
        return new Dimension(fm.stringWidth(getText()) + relleno * 2, 34);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Tema.suavizar(g0);
        int w = getWidth();
        int h = getHeight();
        boolean sobre = getModel().isRollover();
        boolean presionado = getModel().isArmed() && getModel().isPressed();
        boolean activo = isEnabled();

        Color fondo = null;
        Color borde = null;
        Color texto;
        switch (tipo) {
            case PRIMARIO:
                fondo = !activo ? new Color(0xAFC4C7) : (presionado || sobre ? Tema.PRIMARIO_OSCURO : Tema.PRIMARIO);
                texto = Color.WHITE;
                break;
            case SECUNDARIO:
                fondo = sobre ? Tema.FONDO : Tema.SUPERFICIE;
                borde = sobre ? new Color(0xB5C1C8) : Tema.LINEA;
                texto = Tema.TINTA;
                break;
            default:
                fondo = sobre ? new Color(0xEAEEF0) : null;
                texto = sobre ? Tema.TINTA : Tema.PIZARRA;
                break;
        }
        if (fondo != null) {
            g.setColor(fondo);
            g.fillRoundRect(0, 0, w - 1, h - 1, 9, 9);
        }
        if (borde != null) {
            g.setColor(borde);
            g.drawRoundRect(0, 0, w - 1, h - 1, 9, 9);
        }
        if (isFocusOwner()) {
            g.setColor(new Color(Tema.PRIMARIO.getRed(), Tema.PRIMARIO.getGreen(), Tema.PRIMARIO.getBlue(), 150));
            g.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        }
        g.setFont(getFont());
        g.setColor(texto);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}
