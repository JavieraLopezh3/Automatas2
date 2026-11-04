package interfaz;

import java.awt.Dimension;
import javax.swing.JFrame;

/** Ventana principal. Todo el contenido vive en {@link PanelAnalizador}. */
public final class VentanaAnalizadorLexico extends JFrame {

    public VentanaAnalizadorLexico() {
        super("Analizador léxico y sintáctico");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(new PanelAnalizador());
        setMinimumSize(new Dimension(1000, 640));
        setSize(1380, 840);
        setLocationRelativeTo(null);
    }
}
