package sintactico;

import java.util.Collections;
import java.util.List;

/**
 * Encapsula el resultado del analisis sintactico:
 * la lista de errores detectados (vacia si el programa es valido).
 */
public final class ResultadoAnalisisSintactico {

    private final List<ErrorSintactico> errores;
    private final NodoAst raiz;

    public ResultadoAnalisisSintactico(List<ErrorSintactico> errores, NodoAst raiz) {
        this.errores = List.copyOf(errores);
        this.raiz = raiz;
    }

    /** Lista inmutable de errores sintacticos. */
    public List<ErrorSintactico> getErrores() {
        return Collections.unmodifiableList(errores);
    }

    /** Devuelve true si no se encontro ningun error sintactico. */
    public boolean esValido() {
        return errores.isEmpty();
    }

    public NodoAst getRaiz() {
        return raiz;
    }
}
