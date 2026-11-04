package tokens;

public final class ComponenteLexico {
    private final TipoComponente tipo;
    private final String lexema;
    private final int linea;
    private final int columna;
    private final boolean ignorado;
    private final int indiceInicio;
    private final int indiceFin;

    public ComponenteLexico(
        TipoComponente tipo,
        String lexema,
        int linea,
        int columna,
        boolean ignorado,
        int indiceInicio,
        int indiceFin
    ) {
        this.tipo = tipo;
        this.lexema = lexema;
        this.linea = linea;
        this.columna = columna;
        this.ignorado = ignorado;
        this.indiceInicio = indiceInicio;
        this.indiceFin = indiceFin;
    }

    public TipoComponente getTipo() {
        return tipo;
    }

    public String getLexema() {
        return lexema;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    public boolean seIgnora() {
        return ignorado;
    }

    public int getIndiceInicio() {
        return indiceInicio;
    }

    public int getIndiceFin() {
        return indiceFin;
    }
}
