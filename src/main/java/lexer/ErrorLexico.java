package lexer;

public final class ErrorLexico {
    private final int codigo;
    private final String lexema;
    private final String mensaje;
    private final int linea;
    private final int columna;
    private final int indiceInicio;
    private final int indiceFin;

    public ErrorLexico(int codigo, String lexema, String mensaje, int linea, int columna, int indiceInicio, int indiceFin) {
        this.codigo = codigo;
        this.lexema = lexema;
        this.mensaje = mensaje;
        this.linea = linea;
        this.columna = columna;
        this.indiceInicio = indiceInicio;
        this.indiceFin = indiceFin;
    }

    public int getCodigo() {
        return codigo;
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

    public int getIndiceInicio() {
        return indiceInicio;
    }

    public int getIndiceFin() {
        return indiceFin;
    }
}
