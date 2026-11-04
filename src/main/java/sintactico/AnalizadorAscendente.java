package sintactico;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sintactico.GramaticaLR.Accion;
import sintactico.GramaticaLR.Produccion;
import tokens.ComponenteLexico;
import tokens.TipoComponente;

/**
 * Analizador sintáctico ASCENDENTE (de abajo hacia arriba): shift-reduce con
 * tabla SLR(1) generada por {@link GramaticaLR}.
 *
 * Lee los tokens de izquierda a derecha, los apila (desplazar) y cuando en la
 * cima de la pila aparece el lado derecho de una producción lo reemplaza por su
 * lado izquierdo (reducir), construyendo el árbol desde las hojas hacia la raíz.
 *
 * Recuperación de errores a nivel de frase:
 *   1. Si basta insertar un token (';', ')', ']', '}' ...) para continuar, lo
 *      inserta virtualmente y reporta "falta X".
 *   2. En otro caso descarta el token inesperado.
 *   Los errores en cascada se suprimen hasta desplazar 2 tokens válidos.
 */
public final class AnalizadorAscendente {

    private static final int ERR_TOKEN_INESPERADO = 700;
    private static final int ERR_TOKEN_FALTANTE = 701;
    private static final int ERR_FIN_ARCHIVO = 702;
    private static final int MAX_ERRORES = 50;
    private static final int MAX_PASOS = 400_000;
    private static final int MAX_FILAS_TRAZA = 20_000;

    /** Candidatos que se intentan insertar virtualmente, por orden de preferencia. */
    private static final String[] INSERTABLES = {
        "PUNTOCOMA", "PAR_C", "COR_C", "LLAVE_C"
    };

    private final GramaticaLR gramatica = GramaticaLR.obtener();

    // estado de una corrida
    private List<ComponenteLexico> entrada;
    private final List<Integer> pilaEstados = new ArrayList<>();
    private final List<NodoAst> pilaNodos = new ArrayList<>();
    private final List<String> pilaSimbolos = new ArrayList<>();

    public ResultadoAscendente analizar(List<ComponenteLexico> componentes) {
        entrada = componentes.stream()
                .filter(c -> !c.seIgnora() && c.getTipo() != TipoComponente.COMENTARIO)
                .collect(Collectors.toCollection(ArrayList::new));
        pilaEstados.clear();
        pilaNodos.clear();
        pilaSimbolos.clear();

        List<ErrorSintactico> errores = new ArrayList<>();
        List<String[]> traza = new ArrayList<>();
        List<String> reducciones = new ArrayList<>();

        if (entrada.isEmpty()) {
            errores.add(new ErrorSintactico(ERR_TOKEN_INESPERADO, "EOF", "No hay tokens para analizar", -1, -1));
            return new ResultadoAscendente(errores, null, traza, reducciones, gramatica.getCantidadEstados());
        }

        pilaEstados.add(0);
        int i = 0;
        int paso = 0;
        int desdeUltimoError = 99; // tokens desplazados desde el último error
        NodoAst raiz = null;

        while (paso++ < MAX_PASOS) {
            int estado = pilaEstados.get(pilaEstados.size() - 1);
            ComponenteLexico actual = i < entrada.size() ? entrada.get(i) : null;
            String simbolo = actual == null ? GramaticaLR.FIN : actual.getTipo().getNombreVisible();
            Accion accion = gramatica.getAccion(estado, simbolo);

            if (accion == null) {
                // ── ERROR ────────────────────────────────────────────────────
                boolean reportar = desdeUltimoError >= 2;
                desdeUltimoError = 0;

                if (errores.size() >= MAX_ERRORES) {
                    ComponenteLexico ultimo = entrada.get(entrada.size() - 1);
                    errores.add(new ErrorSintactico(ERR_TOKEN_INESPERADO, "EOF",
                            "Demasiados errores sintácticos. Análisis ascendente detenido.",
                            ultimo.getLinea(), ultimo.getColumna()));
                    break;
                }

                String faltante = candidatoAInsertar(estado, simbolo);
                if (faltante != null) {
                    ComponenteLexico previo = i > 0 ? entrada.get(i - 1) : actual;
                    if (reportar) {
                        errores.add(errorFaltante(faltante, previo, actual, estado));
                    }
                    entrada.add(i, virtual(faltante, previo));
                    registrarTraza(traza, paso, i, "ERROR → inserta '" + simbolo(faltante) + "' virtual");
                    continue;
                }

                if (actual == null) {
                    // fin de archivo sin posibilidad de completar
                    ComponenteLexico ultimo = entrada.get(entrada.size() - 1);
                    if (reportar) {
                        errores.add(new ErrorSintactico(ERR_FIN_ARCHIVO, "EOF",
                                "Fin de archivo inesperado. " + describirEsperados(estado),
                                ultimo.getLinea(), ultimo.getColumna()));
                    }
                    registrarTraza(traza, paso, i, "ERROR → fin de archivo inesperado");
                    break;
                }

                if (reportar) {
                    errores.add(new ErrorSintactico(ERR_TOKEN_INESPERADO, actual.getLexema(),
                            "Token inesperado '" + actual.getLexema() + "' (" + simbolo + "). "
                                    + describirEsperados(estado),
                            actual.getLinea(), actual.getColumna()));
                }
                registrarTraza(traza, paso, i, "ERROR → descarta '" + actual.getLexema() + "'");
                i++;
                continue;
            }

            switch (accion.tipo) {
                case DESPLAZAR: {
                    registrarTraza(traza, paso, i, "Desplazar  " + simbolo + "  → estado " + accion.valor);
                    pilaEstados.add(accion.valor);
                    pilaSimbolos.add(simbolo);
                    pilaNodos.add(new NodoAst(actual));
                    i++;
                    desdeUltimoError++;
                    break;
                }
                case REDUCIR: {
                    Produccion p = gramatica.getProduccion(accion.valor);
                    registrarTraza(traza, paso, i, "Reducir  " + p);
                    reducciones.add(p.toString());
                    reducir(p);
                    break;
                }
                case ACEPTAR: {
                    registrarTraza(traza, paso, i, "ACEPTAR");
                    raiz = pilaNodos.isEmpty() ? null : podar(pilaNodos.get(pilaNodos.size() - 1));
                    return new ResultadoAscendente(errores, raiz, traza, reducciones,
                            gramatica.getCantidadEstados());
                }
                default:
                    break;
            }
        }

        // Análisis interrumpido: se devuelve lo construido hasta ahora
        NodoAst parcial = new NodoAst("ArbolParcial");
        for (NodoAst n : pilaNodos) {
            parcial.agregarHijo(podar(n));
        }
        return new ResultadoAscendente(errores, parcial, traza, reducciones, gramatica.getCantidadEstados());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reducción y construcción del árbol (hojas → raíz)
    // ─────────────────────────────────────────────────────────────────────────
    private void reducir(Produccion p) {
        int n = p.der.size();
        List<NodoAst> hijos = new ArrayList<>(pilaNodos.subList(pilaNodos.size() - n, pilaNodos.size()));
        for (int k = 0; k < n; k++) {
            pilaEstados.remove(pilaEstados.size() - 1);
            pilaSimbolos.remove(pilaSimbolos.size() - 1);
            pilaNodos.remove(pilaNodos.size() - 1);
        }

        NodoAst nodo;
        if (n > 1 && p.izq.equals(p.der.get(0)) && hijos.get(0) != null) {
            // lista recursiva por la izquierda: se aplana en lugar de anidar
            nodo = hijos.get(0);
            for (int k = 1; k < n; k++) {
                nodo.agregarHijo(hijos.get(k));
            }
        } else {
            nodo = new NodoAst(p.izq);
            for (NodoAst h : hijos) {
                nodo.agregarHijo(h);
            }
        }

        int cima = pilaEstados.get(pilaEstados.size() - 1);
        Integer destino = gramatica.getIrA(cima, p.izq);
        if (destino == null) {
            throw new IllegalStateException("GOTO indefinido: estado " + cima + ", " + p.izq);
        }
        pilaEstados.add(destino);
        pilaSimbolos.add(p.izq);
        pilaNodos.add(nodo);
    }

    /** Colapsa la cadena de niveles de precedencia y elimina no terminales vacíos (ε). */
    private NodoAst podar(NodoAst nodo) {
        if (nodo.getToken() != null) {
            return nodo;
        }
        List<NodoAst> nuevos = new ArrayList<>();
        for (NodoAst h : nodo.getHijos()) {
            NodoAst ph = podar(h);
            if (ph.getToken() == null && ph.getHijos().isEmpty()) {
                continue; // producción ε
            }
            nuevos.add(ph);
        }
        nodo.getHijos().clear();
        for (NodoAst h : nuevos) {
            nodo.agregarHijo(h);
        }
        // un nivel de expresión con un único hijo no terminal no aporta información
        if (esNivelIntermedio(nodo.getNombre()) && nodo.getHijos().size() == 1
                && nodo.getHijos().get(0).getToken() == null) {
            return nodo.getHijos().get(0);
        }
        return nodo;
    }

    private static boolean esNivelIntermedio(String nombre) {
        switch (nombre) {
            case "ExpY": case "ExpIgual": case "ExpRel": case "ExpAdd": case "ExpMul":
            case "Unaria": case "Operando": case "Postfijo": case "Primaria":
            case "Miembro": case "Elemento": case "TipoDato": case "Modificador":
                return true;
            default:
                return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recuperación de errores
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve un terminal que, insertado antes del token actual, permite seguir
     * analizando (el token actual debe poder desplazarse después), o null.
     */
    private String candidatoAInsertar(int estado, String simboloActual) {
        for (String c : INSERTABLES) {
            if (c.equals(simboloActual)) {
                continue;
            }
            if (simular(new ArrayList<>(pilaEstados), c, simboloActual)) {
                return c;
            }
        }
        return null;
    }

    /** ¿Se pueden procesar 'primero' y luego 'segundo' sin error desde esta pila? */
    private boolean simular(List<Integer> pila, String primero, String segundo) {
        return consumir(pila, primero) && consumir(pila, segundo);
    }

    /** Procesa un terminal: aplica reducciones hasta poder desplazarlo. */
    private boolean consumir(List<Integer> pila, String terminal) {
        for (int guarda = 0; guarda < 10_000; guarda++) {
            Accion a = gramatica.getAccion(pila.get(pila.size() - 1), terminal);
            if (a == null) {
                return false;
            }
            switch (a.tipo) {
                case DESPLAZAR:
                    pila.add(a.valor);
                    return true;
                case ACEPTAR:
                    return true;
                case REDUCIR: {
                    Produccion p = gramatica.getProduccion(a.valor);
                    for (int k = 0; k < p.der.size(); k++) {
                        pila.remove(pila.size() - 1);
                    }
                    Integer destino = gramatica.getIrA(pila.get(pila.size() - 1), p.izq);
                    if (destino == null) {
                        return false;
                    }
                    pila.add(destino);
                    break;
                }
                default:
                    return false;
            }
        }
        return false;
    }

    private ComponenteLexico virtual(String terminal, ComponenteLexico ref) {
        TipoComponente tipo = null;
        for (TipoComponente t : TipoComponente.values()) {
            if (t.getNombreVisible().equals(terminal)) {
                tipo = t;
                break;
            }
        }
        int linea = ref != null ? ref.getLinea() : -1;
        int columna = ref != null ? ref.getColumna() + ref.getLexema().length() : -1;
        int idx = ref != null ? ref.getIndiceFin() : 0;
        return new ComponenteLexico(tipo, simbolo(terminal), linea, columna, false, idx, idx);
    }

    private ErrorSintactico errorFaltante(String faltante, ComponenteLexico previo, ComponenteLexico actual,
            int estado) {
        String encontrado = actual == null ? "fin de archivo" : "'" + actual.getLexema() + "'";
        String donde;
        switch (faltante) {
            case "PUNTOCOMA": donde = "al final de la sentencia"; break;
            case "PAR_C":     donde = "para cerrar el paréntesis"; break;
            case "COR_C":     donde = "para cerrar el corchete"; break;
            case "LLAVE_C":   donde = "para cerrar el bloque"; break;
            default:          donde = "en este punto"; break;
        }
        String mensaje = "Falta '" + simbolo(faltante) + "' " + donde + " (se encontró " + encontrado + ")";
        return new ErrorSintactico(ERR_TOKEN_FALTANTE, actual == null ? "EOF" : actual.getLexema(), mensaje,
                previo != null ? previo.getLinea() : -1, previo != null ? previo.getColumna() : -1);
    }

    private String describirEsperados(int estado) {
        List<String> esperados = gramatica.getEsperados(estado);
        List<String> legibles = new ArrayList<>();
        for (String e : esperados) {
            legibles.add(simbolo(e));
            if (legibles.size() >= 10) {
                break;
            }
        }
        String mas = esperados.size() > legibles.size() ? ", …" : "";
        return "Se esperaba uno de: { " + String.join(", ", legibles) + mas + " }";
    }

    /** Nombre de terminal → símbolo que el programador escribe. */
    static String simbolo(String t) {
        switch (t) {
            case "PUNTOCOMA": return ";";
            case "COMA": return ",";
            case "PUNTO": return ".";
            case "DOS_PUNTOS": return ":";
            case "LLAVE_A": return "{";
            case "LLAVE_C": return "}";
            case "PAR_A": return "(";
            case "PAR_C": return ")";
            case "COR_A": return "[";
            case "COR_C": return "]";
            case "ASIG": return "=";
            case "SUMA": return "+";
            case "RESTA": return "-";
            case "MULT": return "*";
            case "DIV": return "/";
            case "MOD": return "%";
            case "IGUAL": return "==";
            case "DIFER": return "!=";
            case "MENOR": return "<";
            case "MEN_IG": return "<=";
            case "MAYOR": return ">";
            case "MAY_IG": return ">=";
            case "AND": return "&&";
            case "OR": return "||";
            case "NOT": return "!";
            case "INC": return "++";
            case "DEC": return "--";
            case "ID": return "identificador";
            case "NUM_INT": return "número entero";
            case "NUM_FLOAT": return "número decimal";
            case "CADENA": return "cadena";
            case "CARACTER": return "carácter";
            case "$": return "fin de archivo";
            default: return t.toLowerCase();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Traza  (Paso | Pila | Entrada | Acción)
    // ─────────────────────────────────────────────────────────────────────────
    private void registrarTraza(List<String[]> traza, int paso, int i, String accion) {
        if (traza.size() >= MAX_FILAS_TRAZA) {
            return;
        }
        StringBuilder pila = new StringBuilder();
        int desde = Math.max(0, pilaSimbolos.size() - 8);
        if (desde > 0) {
            pila.append("… ");
        }
        for (int k = desde; k < pilaSimbolos.size(); k++) {
            pila.append(pilaSimbolos.get(k)).append(' ');
        }
        StringBuilder ent = new StringBuilder();
        for (int k = i; k < Math.min(entrada.size(), i + 6); k++) {
            ent.append(entrada.get(k).getTipo().getNombreVisible()).append(' ');
        }
        if (i + 6 < entrada.size()) {
            ent.append("… ");
        }
        ent.append('$');
        traza.add(new String[] {
            String.valueOf(traza.size() + 1),
            "[" + pilaEstados.get(pilaEstados.size() - 1) + "] " + pila.toString().trim(),
            ent.toString(),
            accion
        });
    }

    /** Resumen de la tabla útil para depurar. */
    public static String resumenTabla() {
        return GramaticaLR.obtener().describir();
    }
}
