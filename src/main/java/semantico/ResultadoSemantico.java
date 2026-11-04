package semantico;

import java.util.Collections;
import java.util.List;

/** Resultado del análisis semántico de un archivo. */
public final class ResultadoSemantico {

    private final List<ErrorSemantico> hallazgos;
    private final List<String[]> tablaSimbolos;
    private final boolean evaluado;
    private final String motivoOmision;
    private final NodoSemantico arbol;

    public ResultadoSemantico(List<ErrorSemantico> hallazgos, List<String[]> tablaSimbolos) {
        this(hallazgos, tablaSimbolos, null);
    }

    public ResultadoSemantico(List<ErrorSemantico> hallazgos, List<String[]> tablaSimbolos, NodoSemantico arbol) {
        List<ErrorSemantico> ordenados = new java.util.ArrayList<>(hallazgos);
        ordenados.sort(java.util.Comparator.comparingInt(ErrorSemantico::getLinea)
                .thenComparingInt(ErrorSemantico::getColumna));
        this.hallazgos = List.copyOf(ordenados);
        this.tablaSimbolos = List.copyOf(tablaSimbolos);
        this.evaluado = true;
        this.motivoOmision = null;
        this.arbol = arbol;
    }

    private ResultadoSemantico(String motivo) {
        this.hallazgos = List.of();
        this.tablaSimbolos = List.of();
        this.evaluado = false;
        this.motivoOmision = motivo;
        this.arbol = null;
    }

    /** Resultado de un archivo que no pudo evaluarse (p. ej. por errores sintácticos). */
    public static ResultadoSemantico omitido(String motivo) {
        return new ResultadoSemantico(motivo);
    }

    public List<ErrorSemantico> getHallazgos() {
        return Collections.unmodifiableList(hallazgos);
    }

    /** Filas {ámbito, nombre, categoría, tipo, línea}. */
    public List<String[]> getTablaSimbolos() {
        return Collections.unmodifiableList(tablaSimbolos);
    }

    /** Árbol semántico (programa decorado con tipos y errores); null si el archivo no se evaluó. */
    public NodoSemantico getArbol() {
        return arbol;
    }

    public boolean fueEvaluado() {
        return evaluado;
    }

    public String getMotivoOmision() {
        return motivoOmision;
    }

    public long getCantidadErrores() {
        return hallazgos.stream().filter(ErrorSemantico::esError).count();
    }

    public long getCantidadAdvertencias() {
        return hallazgos.size() - getCantidadErrores();
    }

    /** Congruente = se evaluó y no hay errores semánticos (las advertencias no cuentan). */
    public boolean esCongruente() {
        return evaluado && getCantidadErrores() == 0;
    }
}
