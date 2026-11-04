package semantico;

import java.util.Set;

/**
 * Tipo de un valor en el análisis semántico: tipo base + número de dimensiones
 * de arreglo. Los tipos de clase se representan con el nombre de la clase.
 */
public final class Tipo {

    public static final Tipo INT = new Tipo("int", 0, false);
    public static final Tipo FLOAT = new Tipo("float", 0, false);
    public static final Tipo STRING = new Tipo("string", 0, false);
    public static final Tipo CHAR = new Tipo("char", 0, false);
    public static final Tipo BOOLEAN = new Tipo("boolean", 0, false);
    public static final Tipo VOID = new Tipo("void", 0, false);
    /** Tipo ya reportado como erróneo o no verificable: se acepta en cualquier contexto. */
    public static final Tipo DESCONOCIDO = new Tipo("?", 0, false);

    private static final Set<String> PRIMITIVOS = Set.of("int", "float", "string", "char", "boolean");

    private final String base;
    private final int dims;
    private final boolean referenciaClase;

    private Tipo(String base, int dims, boolean referenciaClase) {
        this.base = base;
        this.dims = dims;
        this.referenciaClase = referenciaClase;
    }

    public static Tipo de(String base, int dims) {
        Tipo t;
        switch (base) {
            case "int": t = INT; break;
            case "float": t = FLOAT; break;
            case "string": t = STRING; break;
            case "char": t = CHAR; break;
            case "boolean": t = BOOLEAN; break;
            case "void": t = VOID; break;
            case "?": return DESCONOCIDO;
            default: t = new Tipo(base, 0, false);
        }
        return dims == 0 ? t : new Tipo(t.base, dims, false);
    }

    /** Instancia de una clase (por ejemplo, el valor de 'this'). */
    public static Tipo instancia(String clase) {
        return new Tipo(clase, 0, false);
    }

    /** El nombre de una clase usado como calificador:  Clase.metodo(...) */
    public static Tipo referenciaAClase(String clase) {
        return new Tipo(clase, 0, true);
    }

    public String getBase() {
        return base;
    }

    public int getDims() {
        return dims;
    }

    public boolean esReferenciaClase() {
        return referenciaClase;
    }

    public boolean esDesconocido() {
        return this == DESCONOCIDO || base.equals("?");
    }

    public boolean esArreglo() {
        return dims > 0;
    }

    public boolean esVoid() {
        return dims == 0 && base.equals("void");
    }

    public boolean esPrimitivo() {
        return dims == 0 && PRIMITIVOS.contains(base);
    }

    public boolean esNumerico() {
        return dims == 0 && !referenciaClase && (base.equals("int") || base.equals("float") || base.equals("char"));
    }

    public boolean esBoolean() {
        return dims == 0 && base.equals("boolean");
    }

    public boolean esString() {
        return dims == 0 && base.equals("string");
    }

    public boolean esClase() {
        return dims == 0 && !PRIMITIVOS.contains(base) && !base.equals("void") && !base.equals("?");
    }

    public Tipo elemento() {
        return dims <= 1 ? de(base, 0) : new Tipo(base, dims - 1, false);
    }

    public Tipo arreglo(int n) {
        return n == 0 ? this : new Tipo(base, dims + n, false);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Tipo)) {
            return false;
        }
        Tipo t = (Tipo) o;
        return base.equals(t.base) && dims == t.dims && referenciaClase == t.referenciaClase;
    }

    @Override
    public int hashCode() {
        return base.hashCode() * 31 + dims + (referenciaClase ? 7 : 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(referenciaClase ? "clase " + base : base);
        for (int i = 0; i < dims; i++) {
            sb.append("[]");
        }
        return sb.toString();
    }
}
