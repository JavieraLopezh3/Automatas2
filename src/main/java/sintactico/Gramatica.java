package sintactico;

import java.util.EnumSet;
import java.util.Set;

import tokens.TipoComponente;

/**
 * Gramática formal del lenguaje.
 * Define los conjuntos FIRST y FOLLOW de cada no terminal,
 * utilizados por el analizador sintáctico para validar tokens
 * y generar mensajes de error precisos.
 *
 * Cuádrupla: G = (VT, VN, S, P)
 *   VT  = TipoComponente (terminales definidos en el enum)
 *   VN  = {Programa, Clase, Metodo, Bloque, Sentencia, Expresion,
 *          Termino, Declaracion, Asignacion, Parametro, Import}
 *   S   = Programa  (símbolo inicial)
 *   P   = reglas implementadas como métodos en AnalizadorSintactico
 */
public class Gramatica {

    // =========================================================================
    // FIRST — qué tokens pueden iniciar cada no terminal
    // =========================================================================

    /**
     * FIRST(TipoDato) = { int, float, string, char, boolean }
     */
    public static final Set<TipoComponente> FIRST_TIPO_DATO = EnumSet.of(
            TipoComponente.TIPO_ENTERO,
            TipoComponente.TIPO_FLOTANTE,
            TipoComponente.TIPO_CADENA,
            TipoComponente.TIPO_CARACTER,
            TipoComponente.TIPO_BOOLEANO);

    /**
     * FIRST(TipoRetorno) = FIRST(TipoDato) ∪ { void }
     */
    public static final Set<TipoComponente> FIRST_TIPO_RETORNO;
    static {
        FIRST_TIPO_RETORNO = EnumSet.copyOf(FIRST_TIPO_DATO);
        FIRST_TIPO_RETORNO.add(TipoComponente.VACIO);
    }

    /**
     * FIRST(Expresion) = { NUM_INT, NUM_FLOAT, CADENA, CARACTER, ID, this, (, - }
     */
    public static final Set<TipoComponente> FIRST_EXPRESION = EnumSet.of(
            TipoComponente.NUMERO_ENTERO,
            TipoComponente.NUMERO_FLOTANTE,
            TipoComponente.LITERAL_CADENA,
            TipoComponente.LITERAL_CARACTER,
            TipoComponente.IDENTIFICADOR,
            TipoComponente.THIS,
            TipoComponente.PARENTESIS_ABRE,
            TipoComponente.RESTA,
            TipoComponente.NEGACION,
            TipoComponente.NEW);

    /**
     * FIRST(Sentencia) = FIRST(Expresion) ∪ FIRST(TipoDato)
     *                  ∪ { if, while, repeat, for, exist, return }
     */
    public static final Set<TipoComponente> FIRST_SENTENCIA;
    static {
        FIRST_SENTENCIA = EnumSet.of(
                TipoComponente.SI,
                TipoComponente.MIENTRAS,
                TipoComponente.REPETIR,
                TipoComponente.FOR,
                TipoComponente.EXISTE,
                TipoComponente.RETORNAR,
                TipoComponente.IDENTIFICADOR,
                TipoComponente.THIS);
        FIRST_SENTENCIA.addAll(FIRST_TIPO_DATO);
        FIRST_SENTENCIA.addAll(FIRST_EXPRESION); // sentencia-expresión: (a+b); -x; !y; new int[3];
    }

    /**
     * FIRST(Bloque) = { { }
     */
    public static final Set<TipoComponente> FIRST_BLOQUE = EnumSet.of(
            TipoComponente.LLAVE_ABRE);

    /**
     * FIRST(Clase) = { public }
     */
    public static final Set<TipoComponente> FIRST_CLASE = EnumSet.of(
            TipoComponente.PUBLICO);

    /**
     * FIRST(Metodo) = { public, private } ∪ FIRST(TipoRetorno)
     */
    public static final Set<TipoComponente> FIRST_METODO;
    static {
        FIRST_METODO = EnumSet.of(
                TipoComponente.PUBLICO,
                TipoComponente.PRIVADO);
        FIRST_METODO.addAll(FIRST_TIPO_RETORNO);
    }

    /**
     * FIRST(Parametro) = FIRST(TipoDato)
     */
    public static final Set<TipoComponente> FIRST_PARAMETRO = EnumSet.copyOf(FIRST_TIPO_DATO);

    /**
     * FIRST(Declaracion) = FIRST(TipoDato)
     */
    public static final Set<TipoComponente> FIRST_DECLARACION = EnumSet.copyOf(FIRST_TIPO_DATO);

    /**
     * FIRST(Asignacion) = { ID }
     */
    public static final Set<TipoComponente> FIRST_ASIGNACION = EnumSet.of(
            TipoComponente.IDENTIFICADOR);

    /**
     * FIRST(Import) = { import }
     */
    public static final Set<TipoComponente> FIRST_IMPORT = EnumSet.of(
            TipoComponente.IMPORTAR);

    /**
     * FIRST(ElementoPrograma) = FIRST(Clase) ∪ FIRST(Metodo)
     *                         ∪ FIRST(TipoDato) ∪ { ID }
     */
    public static final Set<TipoComponente> FIRST_ELEMENTO_PROGRAMA;
    static {
        FIRST_ELEMENTO_PROGRAMA = EnumSet.copyOf(FIRST_CLASE);
        FIRST_ELEMENTO_PROGRAMA.addAll(FIRST_METODO);
        FIRST_ELEMENTO_PROGRAMA.addAll(FIRST_TIPO_DATO);
        FIRST_ELEMENTO_PROGRAMA.add(TipoComponente.IDENTIFICADOR);
    }

    // =========================================================================
    // FOLLOW — qué tokens pueden aparecer DESPUÉS de cada no terminal
    // =========================================================================

    /**
     * FOLLOW(Programa) = { $ }   (fin de entrada, representado como conjunto vacío)
     */
    public static final Set<TipoComponente> FOLLOW_PROGRAMA = EnumSet.noneOf(TipoComponente.class);

    /**
     * FOLLOW(Clase) = FIRST(ElementoPrograma) ∪ { } }
     */
    public static final Set<TipoComponente> FOLLOW_CLASE;
    static {
        FOLLOW_CLASE = EnumSet.copyOf(FIRST_ELEMENTO_PROGRAMA);
        FOLLOW_CLASE.add(TipoComponente.LLAVE_CIERRA);
    }

    /**
     * FOLLOW(Metodo) = FIRST(MiembroClase) ∪ { } }
     */
    public static final Set<TipoComponente> FOLLOW_METODO;
    static {
        FOLLOW_METODO = EnumSet.of(
                TipoComponente.LLAVE_CIERRA,
                TipoComponente.PUBLICO,
                TipoComponente.PRIVADO);
        FOLLOW_METODO.addAll(FIRST_TIPO_DATO);
        FOLLOW_METODO.add(TipoComponente.IDENTIFICADOR);
        FOLLOW_METODO.add(TipoComponente.VACIO);
    }

    /**
     * FOLLOW(Bloque) = FIRST(Sentencia) ∪ { } } ∪ FOLLOW(Metodo)
     */
    public static final Set<TipoComponente> FOLLOW_BLOQUE;
    static {
        FOLLOW_BLOQUE = EnumSet.copyOf(FIRST_SENTENCIA);
        FOLLOW_BLOQUE.add(TipoComponente.LLAVE_CIERRA);
        FOLLOW_BLOQUE.add(TipoComponente.SINO);
    }

    /**
     * FOLLOW(Sentencia) = FIRST(Sentencia) ∪ FIRST(TipoDato) ∪ { }, ; }
     */
    public static final Set<TipoComponente> FOLLOW_SENTENCIA;
    static {
        FOLLOW_SENTENCIA = EnumSet.of(
                TipoComponente.LLAVE_CIERRA,
                TipoComponente.PUNTO_Y_COMA);
        FOLLOW_SENTENCIA.addAll(FIRST_SENTENCIA);
        FOLLOW_SENTENCIA.addAll(FIRST_TIPO_DATO);
    }

    /**
     * FOLLOW(Expresion) = { ), ;, }, ,, :, ], operadores relacionales }
     */
    public static final Set<TipoComponente> FOLLOW_EXPRESION = EnumSet.of(
            TipoComponente.PARENTESIS_CIERRA,
            TipoComponente.PUNTO_Y_COMA,
            TipoComponente.LLAVE_CIERRA,
            TipoComponente.COMA,
            TipoComponente.DOS_PUNTOS,
            TipoComponente.CORCHETE_CIERRA,
            TipoComponente.LLAVE_ABRE);

    /**
     * FOLLOW(Declaracion) = { ; }
     */
    public static final Set<TipoComponente> FOLLOW_DECLARACION = EnumSet.of(
            TipoComponente.PUNTO_Y_COMA,
            TipoComponente.PARENTESIS_CIERRA);

    /**
     * FOLLOW(Asignacion) = { ; }
     */
    public static final Set<TipoComponente> FOLLOW_ASIGNACION = EnumSet.of(
            TipoComponente.PUNTO_Y_COMA,
            TipoComponente.DOS_PUNTOS,
            TipoComponente.PARENTESIS_CIERRA);

    /**
     * FOLLOW(Parametro) = { ), , }
     */
    public static final Set<TipoComponente> FOLLOW_PARAMETRO = EnumSet.of(
            TipoComponente.PARENTESIS_CIERRA,
            TipoComponente.COMA);

    /**
     * FOLLOW(Condicion) — lo que sigue a la condición de un if/while/exist
     * = { { }
     */
    public static final Set<TipoComponente> FOLLOW_CONDICION = EnumSet.of(
            TipoComponente.LLAVE_ABRE,
            TipoComponente.PARENTESIS_CIERRA);

    /**
     * FOLLOW(Import) = FIRST(ElementoPrograma) ∪ { import, } }
     */
    public static final Set<TipoComponente> FOLLOW_IMPORT;
    static {
        FOLLOW_IMPORT = EnumSet.copyOf(FIRST_ELEMENTO_PROGRAMA);
        FOLLOW_IMPORT.add(TipoComponente.IMPORTAR);
        FOLLOW_IMPORT.add(TipoComponente.LLAVE_CIERRA);
    }

    // =========================================================================
    // Método auxiliar: genera el mensaje de error estándar FIRST/FOLLOW
    // =========================================================================

    /**
     * Genera un mensaje de error indicando que el token encontrado no pertenece
     * al conjunto FIRST del no terminal esperado.
     *
     * @param noTerminal  nombre del no terminal (ej. "Sentencia")
     * @param conjunto    conjunto FIRST esperado
     * @param tokenActual token encontrado (puede ser null = EOF)
     */
    public static String mensajeErrorFirst(String noTerminal,
                                           Set<TipoComponente> conjunto,
                                           TipoComponente tokenActual) {
        String tok = tokenActual != null ? "'" + tokenActual.getNombreVisible() + "'" : "EOF";
        return "El token " + tok + " no pertenece a FIRST(" + noTerminal + "). "
                + "Se esperaba uno de: " + nombresVisibles(conjunto);
    }

    /**
     * Genera un mensaje de error indicando que el token encontrado no pertenece
     * al conjunto FOLLOW del no terminal en cuestión.
     */
    public static String mensajeErrorFollow(String noTerminal,
                                            Set<TipoComponente> conjunto,
                                            TipoComponente tokenActual) {
        String tok = tokenActual != null ? "'" + tokenActual.getNombreVisible() + "'" : "EOF";
        return "El token " + tok + " no pertenece a FOLLOW(" + noTerminal + "). "
                + "Se esperaba uno de: " + nombresVisibles(conjunto);
    }

    /** Convierte un conjunto de TipoComponente a una lista legible de nombres. */
    private static String nombresVisibles(Set<TipoComponente> conjunto) {
        if (conjunto.isEmpty()) return "{ $ }";
        StringBuilder sb = new StringBuilder("{ ");
        for (TipoComponente t : conjunto) {
            sb.append(t.getNombreVisible()).append(", ");
        }
        // quitar última coma
        sb.setLength(sb.length() - 2);
        sb.append(" }");
        return sb.toString();
    }
}
