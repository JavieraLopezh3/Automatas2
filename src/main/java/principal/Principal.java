package principal;

import interfaz.VentanaAnalizadorLexico;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Principal {

    private Principal() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            configurarApariencia();
            VentanaAnalizadorLexico ventana = new VentanaAnalizadorLexico();
            ventana.setVisible(true);
        });
    }

    private static void configurarApariencia() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
            // La interfaz puede continuar con la apariencia predeterminada.
        }
    }
}
