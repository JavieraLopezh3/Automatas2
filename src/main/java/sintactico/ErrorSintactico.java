package sintactico;

/**
 * Representa un error detectado durante el analisis sintactico.
 * Cada instancia es inmutable una vez construida.
 */
public final class ErrorSintactico {

    private final int    codigo;
    private final String lexema;
    private final String mensaje;
    private final int    linea;
    private final int    columna;

    public ErrorSintactico(int codigo, String lexema, String mensaje, int linea, int columna) {
        this.codigo  = codigo;
        this.lexema  = lexema;
        this.mensaje = mensaje;
        this.linea   = linea;
        this.columna = columna;
    }

    public int    getCodigo()  { return codigo;  }
    public String getLexema()  { return lexema;  }
    public String getMensaje() { return mensaje; }
    public int    getLinea()   { return linea;   }
    public int    getColumna() { return columna; }
}
