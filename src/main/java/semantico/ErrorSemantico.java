package semantico;

/** Error o advertencia detectado durante el análisis semántico (congruencia). */
public final class ErrorSemantico {

    public enum Severidad { ERROR, ADVERTENCIA }

    private final int codigo;
    private final Severidad severidad;
    private final String lexema;
    private final String mensaje;
    private final int linea;
    private final int columna;

    public ErrorSemantico(int codigo, Severidad severidad, String lexema, String mensaje, int linea, int columna) {
        this.codigo = codigo;
        this.severidad = severidad;
        this.lexema = lexema;
        this.mensaje = mensaje;
        this.linea = linea;
        this.columna = columna;
    }

    public int getCodigo() {
        return codigo;
    }

    public Severidad getSeveridad() {
        return severidad;
    }

    public boolean esError() {
        return severidad == Severidad.ERROR;
    }

    public String getLexema() {
        return lexema;
    }

    public String getMensaje() {
        return mensaje;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }
}
