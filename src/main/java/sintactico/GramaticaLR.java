package sintactico;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tokens.TipoComponente;

/**
 * Gramática libre de contexto del lenguaje escrita para análisis ASCENDENTE
 * (abajo → arriba) y generador de la tabla SLR(1) a partir de ella.
 *
 * G = (VT, VN, S, P)
 *   VT = nombres visibles de {@link TipoComponente} (+ '$' fin de entrada)
 *   VN = los símbolos que aparecen a la izquierda de '->'
 *   S  = Codigo
 *   P  = reglas de {@link #REGLAS}
 *
 * La gramática es recursiva por la izquierda (apropiada para LR) y encodea la
 * precedencia de operadores por niveles: || , && , ==/!= , relacionales, + - , * / % ,
 * unarios, postfijos.
 */
public final class GramaticaLR {

    public static final String EPSILON = "ε";
    public static final String FIN = "$";
    public static final String INICIO_AUMENTADO = "Codigo'";
    public static final String SIMBOLO_INICIAL = "Codigo";

    /** Una producción  izq -> der[0] der[1] ...   (der vacío = ε). */
    public static final class Produccion {
        public final int id;
        public final String izq;
        public final List<String> der;

        Produccion(int id, String izq, List<String> der) {
            this.id = id;
            this.izq = izq;
            this.der = der;
        }

        @Override
        public String toString() {
            return izq + " → " + (der.isEmpty() ? EPSILON : String.join(" ", der));
        }
    }

    /** Acción de la tabla ACTION. */
    public enum TipoAccion { DESPLAZAR, REDUCIR, ACEPTAR }

    public static final class Accion {
        public final TipoAccion tipo;
        /** Estado destino (desplazar) o número de producción (reducir). */
        public final int valor;

        Accion(TipoAccion tipo, int valor) {
            this.tipo = tipo;
            this.valor = valor;
        }

        @Override
        public String toString() {
            switch (tipo) {
                case DESPLAZAR: return "d" + valor;
                case REDUCIR:   return "r" + valor;
                default:        return "acc";
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reglas del lenguaje (notación:  NoTerminal -> simbolos | alternativa )
    // ─────────────────────────────────────────────────────────────────────────
    private static final String[] REGLAS = {
        // ── Estructura del archivo ───────────────────────────────────────────
        "Codigo       -> Codigo Programa | Programa",
        "Programa     -> LLAVE_A Imports Elementos LLAVE_C",
        "Imports      -> Imports Import | ε",
        "Import       -> IMPORT ID RutaImport PUNTOCOMA",
        "RutaImport   -> RutaImport PUNTO ID | ε",
        "Elementos    -> Elementos Elemento | ε",
        "Elemento     -> Clase | Miembro | LLAVE_A Elementos LLAVE_C",

        // ── Clases y miembros ────────────────────────────────────────────────
        "Clase        -> PUBLIC CLASS ID LLAVE_A Miembros LLAVE_C",
        "Miembros     -> Miembros Miembro | ε",
        "Miembro      -> Metodo | Atributo | Constructor",
        "Atributo     -> Tipo ID Dims PUNTOCOMA | Tipo ID Dims ASIG Expresion PUNTOCOMA",
        "Metodo       -> Modificador Tipo ID PAR_A Parametros PAR_C Bloque"
                + " | Modificador VOID ID PAR_A Parametros PAR_C Bloque"
                + " | Tipo ID PAR_A Parametros PAR_C Bloque"
                + " | VOID ID PAR_A Parametros PAR_C Bloque",
        "Constructor  -> Modificador ID PAR_A Parametros PAR_C Bloque | ID PAR_A Parametros PAR_C Bloque",
        "Modificador  -> PUBLIC | PRIVATE",
        "Parametros   -> ListaParam | ε",
        "ListaParam   -> ListaParam COMA Parametro | Parametro",
        "Parametro    -> Tipo ID Dims",

        // ── Tipos ────────────────────────────────────────────────────────────
        "Tipo         -> TipoDato | TipoDato COR_A COR_C",
        "TipoDato     -> INT | FLOAT | STRING | CHAR | BOOLEAN",
        "Dims         -> Dims COR_A COR_C | ε",

        // ── Sentencias ───────────────────────────────────────────────────────
        "Bloque       -> LLAVE_A Sentencias LLAVE_C",
        "Sentencias   -> Sentencias Sentencia | ε",
        "Sentencia    -> Declaracion PUNTOCOMA | Asignacion PUNTOCOMA | Expresion PUNTOCOMA"
                + " | If | While | Repeat | For | Exist | Return",
        "Declaracion  -> Tipo ID Dims | Tipo ID Dims ASIG Expresion",
        "Asignacion   -> Postfijo ASIG Expresion",
        "If           -> IF PAR_A Expresion PAR_C Bloque"
                + " | IF PAR_A Expresion PAR_C Bloque ELSE Bloque"
                + " | IF PAR_A Expresion PAR_C Bloque ELSE If",
        "While        -> WHILE PAR_A Expresion PAR_C Bloque",
        "Exist        -> EXIST PAR_A Expresion PAR_C Bloque",
        "Repeat       -> REPEAT PAR_A WHILE Expresion PAR_C Bloque"
                + " | REPEAT PAR_A EACH PAR_A Asignacion DOS_PUNTOS Expresion PAR_C PAR_C Bloque"
                + " | REPEAT PAR_A Declaracion PUNTOCOMA Expresion PUNTOCOMA Asignacion PAR_C Bloque"
                + " | REPEAT PAR_A Expresion PAR_C Bloque",
        "For          -> FOR PAR_A ForInit PUNTOCOMA ForCond PUNTOCOMA ForPaso PAR_C Bloque",
        "ForInit      -> Declaracion | Asignacion | Expresion | ε",
        "ForCond      -> Expresion | ε",
        "ForPaso      -> Asignacion | Expresion | ε",
        "Return       -> RETURN PUNTOCOMA | RETURN Expresion PUNTOCOMA",

        // ── Expresiones (un no terminal por nivel de precedencia) ────────────
        "Expresion    -> Expresion OR ExpY | ExpY",
        "ExpY         -> ExpY AND ExpIgual | ExpIgual",
        "ExpIgual     -> ExpIgual IGUAL ExpRel | ExpIgual DIFER ExpRel | ExpRel",
        "ExpRel       -> ExpRel MENOR ExpAdd | ExpRel MEN_IG ExpAdd | ExpRel MAYOR ExpAdd"
                + " | ExpRel MAY_IG ExpAdd | ExpAdd",
        "ExpAdd       -> ExpAdd SUMA ExpMul | ExpAdd RESTA ExpMul | ExpMul",
        "ExpMul       -> ExpMul MULT Unaria | ExpMul DIV Unaria | ExpMul MOD Unaria | Unaria",
        "Unaria       -> RESTA Operando | NOT Operando | Operando",
        "Operando     -> Postfijo | Nuevo",
        "Nuevo        -> NEW TipoDato DimsNew",
        "DimsNew      -> DimsNew COR_A Expresion COR_C | DimsNew COR_A COR_C | ε",
        "Postfijo     -> Primaria | Postfijo PAR_A Args PAR_C | Postfijo COR_A Expresion COR_C"
                + " | Postfijo COR_A COR_C | Postfijo PUNTO ID | Postfijo INC | Postfijo DEC",
        "Primaria     -> ID | NUM_INT | NUM_FLOAT | CADENA | CARACTER | THIS | PAR_A Expresion PAR_C",
        "Args         -> ListaArgs | ε",
        "ListaArgs    -> ListaArgs COMA Expresion | Expresion",
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Estructuras calculadas
    // ─────────────────────────────────────────────────────────────────────────
    private final List<Produccion> producciones = new ArrayList<>();
    private final Set<String> terminales = new LinkedHashSet<>();
    private final Set<String> noTerminales = new LinkedHashSet<>();
    private final Map<String, Set<String>> first = new HashMap<>();
    private final Map<String, Set<String>> follow = new HashMap<>();
    private final Set<String> anulables = new HashSet<>();

    /** Estados del autómata LR(0): cada elemento es "indiceProduccion:punto". */
    private final List<List<int[]>> estados = new ArrayList<>();
    private final List<Map<String, Integer>> transiciones = new ArrayList<>();
    private final List<Map<String, Accion>> accion = new ArrayList<>();
    private final List<Map<String, Integer>> irA = new ArrayList<>();
    private final List<String> conflictos = new ArrayList<>();

    private static GramaticaLR instancia;

    /** La tabla se calcula una sola vez y se reutiliza. */
    public static synchronized GramaticaLR obtener() {
        if (instancia == null) {
            instancia = new GramaticaLR();
        }
        return instancia;
    }

    private GramaticaLR() {
        for (TipoComponente t : TipoComponente.values()) {
            terminales.add(t.getNombreVisible());
        }
        terminales.add(FIN);
        cargarReglas();
        calcularAnulablesFirstFollow();
        construirAutomata();
        construirTablas();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Lectura de las reglas
    // ─────────────────────────────────────────────────────────────────────────
    private void cargarReglas() {
        // producción 0: S' -> S
        producciones.add(new Produccion(0, INICIO_AUMENTADO, List.of(SIMBOLO_INICIAL)));
        noTerminales.add(INICIO_AUMENTADO);

        Map<String, List<List<String>>> porIzq = new LinkedHashMap<>();
        for (String regla : REGLAS) {
            String[] partes = regla.split("->", 2);
            String izq = partes[0].trim();
            noTerminales.add(izq);
            for (String alt : partes[1].split("\\|")) {
                List<String> der = new ArrayList<>();
                for (String s : alt.trim().split("\\s+")) {
                    if (!s.isEmpty() && !s.equals(EPSILON)) {
                        der.add(s);
                    }
                }
                porIzq.computeIfAbsent(izq, k -> new ArrayList<>()).add(der);
            }
        }
        for (Map.Entry<String, List<List<String>>> e : porIzq.entrySet()) {
            for (List<String> der : e.getValue()) {
                producciones.add(new Produccion(producciones.size(), e.getKey(), der));
            }
        }
        // validar que todo símbolo sea terminal o no terminal conocido
        for (Produccion p : producciones) {
            for (String s : p.der) {
                if (!terminales.contains(s) && !noTerminales.contains(s)) {
                    throw new IllegalStateException("Símbolo desconocido '" + s + "' en la producción " + p);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Anulables, FIRST y FOLLOW
    // ─────────────────────────────────────────────────────────────────────────
    private void calcularAnulablesFirstFollow() {
        for (String nt : noTerminales) {
            first.put(nt, new LinkedHashSet<>());
            follow.put(nt, new LinkedHashSet<>());
        }
        boolean cambio = true;
        while (cambio) {
            cambio = false;
            for (Produccion p : producciones) {
                if (!anulables.contains(p.izq) && todosAnulables(p.der, 0)) {
                    anulables.add(p.izq);
                    cambio = true;
                }
            }
        }
        cambio = true;
        while (cambio) {
            cambio = false;
            for (Produccion p : producciones) {
                Set<String> f = first.get(p.izq);
                for (String s : p.der) {
                    if (terminales.contains(s)) {
                        cambio |= f.add(s);
                        break;
                    }
                    cambio |= f.addAll(first.get(s));
                    if (!anulables.contains(s)) {
                        break;
                    }
                }
            }
        }
        follow.get(INICIO_AUMENTADO).add(FIN);
        cambio = true;
        while (cambio) {
            cambio = false;
            for (Produccion p : producciones) {
                for (int i = 0; i < p.der.size(); i++) {
                    String s = p.der.get(i);
                    if (!noTerminales.contains(s)) {
                        continue;
                    }
                    Set<String> fs = follow.get(s);
                    boolean restoAnulable = true;
                    for (int j = i + 1; j < p.der.size(); j++) {
                        String t = p.der.get(j);
                        if (terminales.contains(t)) {
                            cambio |= fs.add(t);
                            restoAnulable = false;
                            break;
                        }
                        cambio |= fs.addAll(first.get(t));
                        if (!anulables.contains(t)) {
                            restoAnulable = false;
                            break;
                        }
                    }
                    if (restoAnulable) {
                        cambio |= fs.addAll(follow.get(p.izq));
                    }
                }
            }
        }
    }

    private boolean todosAnulables(List<String> der, int desde) {
        for (int i = desde; i < der.size(); i++) {
            if (!anulables.contains(der.get(i))) {
                return false;
            }
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Autómata LR(0)
    // ─────────────────────────────────────────────────────────────────────────
    private static String clave(List<int[]> items) {
        StringBuilder sb = new StringBuilder();
        for (int[] it : items) {
            sb.append(it[0]).append(':').append(it[1]).append(';');
        }
        return sb.toString();
    }

    private List<int[]> cierre(List<int[]> kernel) {
        List<int[]> resultado = new ArrayList<>(kernel);
        Set<String> vistos = new HashSet<>();
        for (int[] it : kernel) {
            vistos.add(it[0] + ":" + it[1]);
        }
        Deque<int[]> pendientes = new ArrayDeque<>(kernel);
        while (!pendientes.isEmpty()) {
            int[] it = pendientes.pop();
            Produccion p = producciones.get(it[0]);
            if (it[1] >= p.der.size()) {
                continue;
            }
            String siguiente = p.der.get(it[1]);
            if (!noTerminales.contains(siguiente)) {
                continue;
            }
            for (Produccion q : producciones) {
                if (q.izq.equals(siguiente) && vistos.add(q.id + ":0")) {
                    int[] nuevo = { q.id, 0 };
                    resultado.add(nuevo);
                    pendientes.push(nuevo);
                }
            }
        }
        resultado.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        return resultado;
    }

    private void construirAutomata() {
        Map<String, Integer> indice = new HashMap<>();
        List<int[]> kernel0 = new ArrayList<>();
        kernel0.add(new int[] { 0, 0 });
        List<int[]> i0 = cierre(kernel0);
        estados.add(i0);
        indice.put(clave(i0), 0);
        transiciones.add(new LinkedHashMap<>());

        for (int e = 0; e < estados.size(); e++) {
            Map<String, List<int[]>> porSimbolo = new LinkedHashMap<>();
            for (int[] it : estados.get(e)) {
                Produccion p = producciones.get(it[0]);
                if (it[1] < p.der.size()) {
                    porSimbolo.computeIfAbsent(p.der.get(it[1]), k -> new ArrayList<>())
                            .add(new int[] { it[0], it[1] + 1 });
                }
            }
            for (Map.Entry<String, List<int[]>> en : porSimbolo.entrySet()) {
                List<int[]> destino = cierre(en.getValue());
                String k = clave(destino);
                Integer idx = indice.get(k);
                if (idx == null) {
                    idx = estados.size();
                    estados.add(destino);
                    transiciones.add(new LinkedHashMap<>());
                    indice.put(k, idx);
                }
                transiciones.get(e).put(en.getKey(), idx);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Tablas ACTION / GOTO (SLR(1))
    // ─────────────────────────────────────────────────────────────────────────
    private void construirTablas() {
        for (int e = 0; e < estados.size(); e++) {
            accion.add(new LinkedHashMap<>());
            irA.add(new LinkedHashMap<>());
        }
        for (int e = 0; e < estados.size(); e++) {
            Map<String, Accion> fila = accion.get(e);

            for (Map.Entry<String, Integer> t : transiciones.get(e).entrySet()) {
                if (terminales.contains(t.getKey())) {
                    fila.put(t.getKey(), new Accion(TipoAccion.DESPLAZAR, t.getValue()));
                } else {
                    irA.get(e).put(t.getKey(), t.getValue());
                }
            }
            for (int[] it : estados.get(e)) {
                Produccion p = producciones.get(it[0]);
                if (it[1] != p.der.size()) {
                    continue;
                }
                if (p.id == 0) {
                    fila.put(FIN, new Accion(TipoAccion.ACEPTAR, 0));
                    continue;
                }
                for (String a : follow.get(p.izq)) {
                    Accion existente = fila.get(a);
                    Accion nueva = new Accion(TipoAccion.REDUCIR, p.id);
                    if (existente == null) {
                        fila.put(a, nueva);
                    } else if (existente.tipo == TipoAccion.DESPLAZAR) {
                        conflictos.add("Estado " + e + ", símbolo " + a + ": desplazar/reducir con " + p
                                + " (se prefiere desplazar)");
                    } else if (existente.tipo == TipoAccion.REDUCIR && existente.valor != p.id) {
                        conflictos.add("Estado " + e + ", símbolo " + a + ": reducir/reducir entre "
                                + producciones.get(existente.valor) + " y " + p);
                        if (p.id < existente.valor) {
                            fila.put(a, nueva);
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────────────────────────────────────
    public List<Produccion> getProducciones() {
        return producciones;
    }

    public Produccion getProduccion(int id) {
        return producciones.get(id);
    }

    public Accion getAccion(int estado, String terminal) {
        return accion.get(estado).get(terminal);
    }

    public Integer getIrA(int estado, String noTerminal) {
        return irA.get(estado).get(noTerminal);
    }

    /** Terminales con acción definida en el estado (lo que "se esperaba" ahí). */
    public List<String> getEsperados(int estado) {
        return new ArrayList<>(accion.get(estado).keySet());
    }

    public int getCantidadEstados() {
        return estados.size();
    }

    public List<String> getConflictos() {
        return conflictos;
    }

    public boolean esTerminal(String simbolo) {
        return terminales.contains(simbolo);
    }

    public Set<String> getNoTerminales() {
        return noTerminales;
    }

    public Set<String> getFirst(String noTerminal) {
        return first.get(noTerminal);
    }

    public Set<String> getFollow(String noTerminal) {
        return follow.get(noTerminal);
    }

    public boolean esAnulable(String noTerminal) {
        return anulables.contains(noTerminal);
    }

    /** Resumen legible de la gramática: producciones, FIRST y FOLLOW. */
    public String describir() {
        StringBuilder sb = new StringBuilder();
        sb.append("GRAMÁTICA LR  (").append(producciones.size() - 1).append(" producciones, ")
                .append(noTerminales.size() - 1).append(" no terminales, ")
                .append(estados.size()).append(" estados SLR(1))\n\n");
        for (Produccion p : producciones) {
            if (p.id == 0) {
                continue;
            }
            sb.append(String.format("%3d. %s%n", p.id, p));
        }
        sb.append("\nFIRST / FOLLOW\n");
        for (String nt : noTerminales) {
            if (nt.equals(INICIO_AUMENTADO)) {
                continue;
            }
            sb.append(String.format("  %-12s FIRST=%s  FOLLOW=%s%n", nt, first.get(nt), follow.get(nt)));
        }
        sb.append("\nConflictos de la tabla: ").append(conflictos.size()).append('\n');
        for (String c : conflictos) {
            sb.append("  - ").append(c).append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return Arrays.toString(REGLAS);
    }
}
