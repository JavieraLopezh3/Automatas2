package sintactico;

import java.util.Collections;
import java.util.List;

/**
 * Resultado del análisis ascendente: errores, árbol construido de hojas a raíz,
 * traza de desplazamientos/reducciones y producciones aplicadas.
 */
public final class ResultadoAscendente {

    private final List<ErrorSintactico> errores;
    private final NodoAst raiz;
    private final List<String[]> traza;
    private final List<String> reducciones;
    private final int cantidadEstados;

    public ResultadoAscendente(List<ErrorSintactico> errores, NodoAst raiz, List<String[]> traza,
            List<String> reducciones, int cantidadEstados) {
        this.errores = List.copyOf(errores);
        this.raiz = raiz;
        this.traza = List.copyOf(traza);
        this.reducciones = List.copyOf(reducciones);
        this.cantidadEstados = cantidadEstados;
    }

    public List<ErrorSintactico> getErrores() {
        return Collections.unmodifiableList(errores);
    }

    public boolean esValido() {
        return errores.isEmpty();
    }

    public NodoAst getRaiz() {
        return raiz;
    }

    /** Filas {paso, pila, entrada, acción}. */
    public List<String[]> getTraza() {
        return traza;
    }

    /** Producciones en el orden en que se reconocieron (derivación por la derecha, al revés). */
    public List<String> getReducciones() {
        return reducciones;
    }

    public int getCantidadEstados() {
        return cantidadEstados;
    }
}
