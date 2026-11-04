package semantico;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import semantico.ErrorSemantico.Severidad;
import semantico.NodoSemantico.Categoria;
import sintactico.NodoAst;
import tokens.ComponenteLexico;
import tokens.TipoComponente;

/**
 * Analizador semántico: comprueba la CONGRUENCIA del programa una vez que la
 * sintaxis es correcta. Recorre el árbol sintáctico y verifica:
 *
 *   - variables, clases y métodos declarados antes de usarse (tabla de símbolos con ámbitos)
 *   - declaraciones duplicadas
 *   - compatibilidad de tipos en asignaciones, inicializaciones, operadores y argumentos
 *   - condiciones booleanas en if / while / repeat / for
 *   - llamadas: método existente, número y tipo de argumentos
 *   - acceso a miembros y a elementos de arreglos
 *   - retornos congruentes con el tipo del método (y que todos los caminos retornen)
 *   - imports que se pueden resolver, constructores bien nombrados, código inalcanzable
 *
 * Se analizan varios archivos a la vez: las clases declaradas en un archivo son
 * visibles para los demás (para resolver 'import').
 */
public final class AnalizadorSemantico {

    // Códigos de error semántico
    private static final int E_NO_DECLARADO = 801;
    private static final int E_DUPLICADO = 802;
    private static final int E_TIPOS = 803;
    private static final int E_OPERADOR = 804;
    private static final int E_CONDICION = 805;
    private static final int E_RETORNO = 806;
    private static final int E_LLAMADA = 807;
    private static final int E_MIEMBRO = 808;
    private static final int E_INDICE = 809;
    private static final int E_IMPORT = 810;
    private static final int E_CLASE_DUP = 811;
    private static final int E_CONSTRUCTOR = 812;
    private static final int E_USO = 813;
    private static final int A_INALCANZABLE = 850;
    private static final int A_DIV_CERO = 851;
    private static final int A_MIEMBRO_DESCONOCIDO = 852;

    // ─────────────────────────────────────────────────────────────────────────
    // Modelo de símbolos
    // ─────────────────────────────────────────────────────────────────────────
    private static final class Simbolo {
        final String nombre;
        final Tipo tipo;
        final int linea;

        Simbolo(String nombre, Tipo tipo, int linea) {
            this.nombre = nombre;
            this.tipo = tipo;
            this.linea = linea;
        }
    }

    private static final class MetodoInfo {
        final String nombre;
        final Tipo retorno;
        final List<Tipo> params;
        final boolean constructor;

        MetodoInfo(String nombre, Tipo retorno, List<Tipo> params, boolean constructor) {
            this.nombre = nombre;
            this.retorno = retorno;
            this.params = params;
            this.constructor = constructor;
        }

        String firma() {
            StringBuilder sb = new StringBuilder(nombre).append('(');
            for (int i = 0; i < params.size(); i++) {
                sb.append(i > 0 ? ", " : "").append(params.get(i));
            }
            return sb.append(')').toString();
        }
    }

    private static final class ClaseInfo {
        final String nombre;
        final boolean externa;
        final Map<String, Simbolo> campos = new LinkedHashMap<>();
        final Map<String, List<MetodoInfo>> metodos = new LinkedHashMap<>();
        final List<MetodoInfo> constructores = new ArrayList<>();

        ClaseInfo(String nombre, boolean externa) {
            this.nombre = nombre;
            this.externa = externa;
        }
    }

    /** Lo que se lee de un nodo Metodo / Constructor del árbol. */
    private static final class Firma {
        String nombre;
        ComponenteLexico tokNombre;
        Tipo retorno = Tipo.VOID;
        boolean constructor;
        final List<Tipo> tiposParam = new ArrayList<>();
        final List<ComponenteLexico> tokParam = new ArrayList<>();
        NodoAst bloque;
    }

    /** Contexto por archivo. */
    private static final class Ctx {
        final String archivo;
        final List<ErrorSemantico> hallazgos = new ArrayList<>();
        final List<String[]> simbolos = new ArrayList<>();
        final List<List<ComponenteLexico>> imports = new ArrayList<>();
        final Set<String> externas = new LinkedHashSet<>();
        final Map<String, List<MetodoInfo>> funciones = new LinkedHashMap<>();
        final Map<String, Simbolo> variables = new LinkedHashMap<>();

        Ctx(String archivo) {
            this.archivo = archivo;
        }
    }

    /** Operando intermedio durante la evaluación de una expresión. */
    private static final class Val {
        final Tipo tipo;
        final ComponenteLexico pos;
        final boolean cero;
        /** Nodo del árbol semántico de este operando u operación. */
        NodoSemantico sem;

        Val(Tipo tipo, ComponenteLexico pos, boolean cero) {
            this.tipo = tipo;
            this.pos = pos;
            this.cero = cero;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estado global y de recorrido
    // ─────────────────────────────────────────────────────────────────────────
    private final Map<String, ClaseInfo> clases = new LinkedHashMap<>();
    private final Map<NodoAst, ClaseInfo> infoPorNodo = new IdentityHashMap<>();
    /** Anotaciones para el árbol semántico: nodo "Expresion" -> su nodo semántico (con tipo). */
    private final Map<NodoAst, NodoSemantico> exprSem = new IdentityHashMap<>();
    /** Nodo "Asignacion" -> {tipo del destino, tipo del valor}. */
    private final Map<NodoAst, Tipo[]> tiposAsig = new IdentityHashMap<>();

    private Ctx ctx;
    private ClaseInfo claseActual;
    private MetodoInfo metodoActual;
    private final List<Map<String, Simbolo>> ambitos = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────
    // API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param arboles  árbol sintáctico de cada archivo (por nombre)
     * @param omitidos archivos que no deben evaluarse (nombre → motivo), p. ej. por errores de sintaxis
     */
    public Map<String, ResultadoSemantico> analizar(Map<String, NodoAst> arboles, Map<String, String> omitidos) {
        clases.clear();
        infoPorNodo.clear();
        exprSem.clear();
        tiposAsig.clear();
        Map<String, Ctx> contextos = new LinkedHashMap<>();

        // Pasada 1: declaraciones (clases, atributos, métodos) de TODOS los archivos
        for (Map.Entry<String, NodoAst> e : arboles.entrySet()) {
            Ctx c = new Ctx(e.getKey());
            contextos.put(e.getKey(), c);
            ctx = c;
            claseActual = null;
            metodoActual = null;
            if (e.getValue() != null) {
                try {
                    registrar(e.getValue());
                } catch (RuntimeException ex) {
                    // árbol incompleto: se registra lo que se pudo
                }
            }
        }

        // Pasada 2: cuerpos, solo de los archivos evaluables
        Map<String, ResultadoSemantico> resultados = new LinkedHashMap<>();
        for (Map.Entry<String, NodoAst> e : arboles.entrySet()) {
            String motivo = omitidos == null ? null : omitidos.get(e.getKey());
            if (motivo != null || e.getValue() == null) {
                resultados.put(e.getKey(), ResultadoSemantico.omitido(
                        motivo != null ? motivo : "No se generó árbol sintáctico."));
                continue;
            }
            ctx = contextos.get(e.getKey());
            claseActual = null;
            metodoActual = null;
            ambitos.clear();
            try {
                validarImports();
                recorrerPrograma(e.getValue());
            } catch (RuntimeException ex) {
                ctx.hallazgos.add(new ErrorSemantico(899, Severidad.ERROR, "", "Fallo interno del analizador "
                        + "semántico (" + ex.getClass().getSimpleName() + "). El árbol sintáctico es incompleto.", -1, -1));
            }
            NodoSemantico arbolSem = null;
            try {
                arbolSem = construirArbol(e.getKey(), e.getValue(), ctx.hallazgos, ctx.simbolos);
            } catch (RuntimeException ex) {
                // el árbol semántico es informativo: si falla no se afecta el veredicto
            }
            resultados.put(e.getKey(), new ResultadoSemantico(ctx.hallazgos, ctx.simbolos, arbolSem));
        }
        return resultados;
    }

    /** Conveniencia: analiza un solo árbol. */
    public ResultadoSemantico analizar(NodoAst raiz) {
        Map<String, NodoAst> m = new LinkedHashMap<>();
        m.put("(archivo)", raiz);
        return analizar(m, null).get("(archivo)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilidades sobre el árbol
    // ─────────────────────────────────────────────────────────────────────────
    private static boolean esTok(NodoAst n, TipoComponente t) {
        return n != null && n.getToken() != null && n.getToken().getTipo() == t;
    }

    private static boolean esNodo(NodoAst n, String nombre) {
        return n != null && n.getToken() == null && nombre.equals(n.getNombre());
    }

    private static ComponenteLexico primerToken(NodoAst n) {
        if (n == null) {
            return null;
        }
        if (n.getToken() != null) {
            return n.getToken();
        }
        for (NodoAst h : n.getHijos()) {
            ComponenteLexico t = primerToken(h);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private static String nombreBase(TipoComponente t) {
        switch (t) {
            case TIPO_ENTERO: return "int";
            case TIPO_FLOTANTE: return "float";
            case TIPO_CADENA: return "string";
            case TIPO_CARACTER: return "char";
            case TIPO_BOOLEANO: return "boolean";
            case VACIO: return "void";
            default: return null;
        }
    }

    private Tipo leerTipo(NodoAst n) {
        if (n == null || n.getHijos().isEmpty() || n.getHijos().get(0).getToken() == null) {
            return Tipo.DESCONOCIDO;
        }
        String base = nombreBase(n.getHijos().get(0).getToken().getTipo());
        if (base == null) {
            return Tipo.DESCONOCIDO;
        }
        int dims = 0;
        for (NodoAst h : n.getHijos()) {
            if (esNodo(h, "Array")) {
                for (NodoAst x : h.getHijos()) {
                    if (esTok(x, TipoComponente.CORCHETE_ABRE)) {
                        dims++;
                    }
                }
            }
        }
        return Tipo.de(base, dims);
    }

    private static int contarCorchetes(List<NodoAst> hs) {
        int n = 0;
        for (NodoAst h : hs) {
            if (esTok(h, TipoComponente.CORCHETE_ABRE)) {
                n++;
            }
        }
        return n;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reporte de hallazgos
    // ─────────────────────────────────────────────────────────────────────────
    private void error(int codigo, ComponenteLexico t, String mensaje) {
        agregar(codigo, Severidad.ERROR, t, mensaje);
    }

    private void advertencia(int codigo, ComponenteLexico t, String mensaje) {
        agregar(codigo, Severidad.ADVERTENCIA, t, mensaje);
    }

    private void agregar(int codigo, Severidad sev, ComponenteLexico t, String mensaje) {
        int linea = t != null ? t.getLinea() : -1;
        int columna = t != null ? t.getColumna() : -1;
        for (ErrorSemantico e : ctx.hallazgos) {
            if (e.getLinea() == linea && e.getColumna() == columna && e.getMensaje().equals(mensaje)) {
                return;
            }
        }
        ctx.hallazgos.add(new ErrorSemantico(codigo, sev, t != null ? t.getLexema() : "", mensaje, linea, columna));
    }

    private void simbolo(String ambito, String nombre, String categoria, Tipo tipo, ComponenteLexico t) {
        ctx.simbolos.add(new String[] { ambito, nombre, categoria, tipo.toString(),
                t != null ? String.valueOf(t.getLinea()) : "-" });
    }

    private String ambitoActual() {
        String metodo = metodoActual != null ? metodoActual.nombre : null;
        if (claseActual != null) {
            return claseActual.nombre + (metodo != null ? "." + metodo : "");
        }
        return "(global)" + (metodo != null ? "." + metodo : "");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASADA 1 — registro de declaraciones
    // ─────────────────────────────────────────────────────────────────────────
    private void registrar(NodoAst nodo) {
        for (NodoAst h : nodo.getHijos()) {
            if (h.getToken() != null) {
                continue;
            }
            switch (h.getNombre()) {
                case "Programa":
                case "Bloque":
                    registrar(h);
                    break;
                case "Import":
                    registrarImport(h);
                    break;
                case "Clase":
                    registrarClase(h);
                    break;
                case "Atributo":
                    registrarAtributo(h, null);
                    break;
                case "Metodo":
                case "Constructor":
                    registrarMetodo(h, null);
                    break;
                default:
                    break;
            }
        }
    }

    private void registrarImport(NodoAst imp) {
        List<ComponenteLexico> partes = new ArrayList<>();
        for (NodoAst h : imp.getHijos()) {
            if (h.getToken() != null && (h.getToken().getTipo() == TipoComponente.IDENTIFICADOR
                    || h.getToken().getTipo() == TipoComponente.PUNTO)) {
                partes.add(h.getToken());
            }
        }
        if (!partes.isEmpty()) {
            ctx.imports.add(partes);
        }
    }

    private void registrarClase(NodoAst nodo) {
        ComponenteLexico nombreTok = null;
        for (NodoAst h : nodo.getHijos()) {
            if (esTok(h, TipoComponente.IDENTIFICADOR)) {
                nombreTok = h.getToken();
                break;
            }
        }
        if (nombreTok == null) {
            return;
        }
        String nombre = nombreTok.getLexema();
        ClaseInfo info = new ClaseInfo(nombre, false);
        infoPorNodo.put(nodo, info);
        if (clases.containsKey(nombre)) {
            error(E_CLASE_DUP, nombreTok, "La clase '" + nombre + "' ya está declarada.");
        } else {
            clases.put(nombre, info);
            simbolo("(programa)", nombre, "Clase", Tipo.instancia(nombre), nombreTok);
        }
        ClaseInfo anterior = claseActual;
        claseActual = info;
        for (NodoAst h : nodo.getHijos()) {
            if (esNodo(h, "Atributo")) {
                registrarAtributo(h, info);
            } else if (esNodo(h, "Metodo") || esNodo(h, "Constructor")) {
                registrarMetodo(h, info);
            }
        }
        claseActual = anterior;
    }

    private void registrarAtributo(NodoAst nodo, ClaseInfo clase) {
        List<NodoAst> hs = nodo.getHijos();
        if (hs.isEmpty()) {
            return;
        }
        Tipo t = leerTipo(hs.get(0));
        ComponenteLexico id = null;
        int extra = 0;
        for (int i = 1; i < hs.size(); i++) {
            if (id == null && esTok(hs.get(i), TipoComponente.IDENTIFICADOR)) {
                id = hs.get(i).getToken();
            } else if (esTok(hs.get(i), TipoComponente.CORCHETE_ABRE)) {
                extra++;
            } else if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                break;
            }
        }
        if (id == null) {
            return;
        }
        t = t.arreglo(extra);
        Map<String, Simbolo> destino = clase != null ? clase.campos : ctx.variables;
        String ambito = clase != null ? clase.nombre : "(global)";
        Simbolo previo = destino.get(id.getLexema());
        if (previo != null) {
            error(E_DUPLICADO, id, "El atributo '" + id.getLexema() + "' ya está declarado en "
                    + ambito + " (línea " + previo.linea + ").");
            return;
        }
        destino.put(id.getLexema(), new Simbolo(id.getLexema(), t, id.getLinea()));
        simbolo(ambito, id.getLexema(), "Atributo", t, id);
    }

    private Firma leerFirma(NodoAst nodo) {
        NodoAst fuente = nodo;
        Firma f = new Firma();
        for (NodoAst h : nodo.getHijos()) {
            if (esNodo(h, "Constructor")) {
                fuente = h;
                f.constructor = true;
                break;
            }
        }
        if (esNodo(nodo, "Constructor")) {
            f.constructor = true;
        }
        boolean nombreLeido = false;
        for (NodoAst h : fuente.getHijos()) {
            if (esTok(h, TipoComponente.VACIO)) {
                f.retorno = Tipo.VOID;
            } else if (esNodo(h, "Tipo") && !nombreLeido) {
                f.retorno = leerTipo(h);
            } else if (esTok(h, TipoComponente.IDENTIFICADOR) && !nombreLeido) {
                f.nombre = h.getToken().getLexema();
                f.tokNombre = h.getToken();
                nombreLeido = true;
            } else if (esNodo(h, "Parametro")) {
                List<NodoAst> ph = h.getHijos();
                Tipo pt = ph.isEmpty() ? Tipo.DESCONOCIDO : leerTipo(ph.get(0));
                ComponenteLexico pid = null;
                for (int i = 1; i < ph.size(); i++) {
                    if (pid == null && esTok(ph.get(i), TipoComponente.IDENTIFICADOR)) {
                        pid = ph.get(i).getToken();
                    }
                }
                f.tiposParam.add(pt.arreglo(contarCorchetes(ph)));
                f.tokParam.add(pid);
            } else if (esNodo(h, "Bloque")) {
                f.bloque = h;
            }
        }
        if (f.constructor) {
            f.retorno = Tipo.VOID;
        }
        return f;
    }

    private void registrarMetodo(NodoAst nodo, ClaseInfo clase) {
        Firma f = leerFirma(nodo);
        if (f.nombre == null) {
            return;
        }
        MetodoInfo info = new MetodoInfo(f.nombre, f.retorno, f.tiposParam, f.constructor);

        if (f.constructor) {
            if (clase == null) {
                error(E_CONSTRUCTOR, f.tokNombre, "El constructor '" + f.nombre
                        + "' solo puede declararse dentro de una clase.");
                return;
            }
            if (!f.nombre.equals(clase.nombre)) {
                error(E_CONSTRUCTOR, f.tokNombre, "'" + f.nombre + "' no coincide con el nombre de la clase '"
                        + clase.nombre + "'. Si es un método, falta indicar su tipo de retorno.");
                return;
            }
            for (MetodoInfo otro : clase.constructores) {
                if (otro.params.equals(info.params)) {
                    error(E_DUPLICADO, f.tokNombre, "Ya existe un constructor " + info.firma() + " en la clase '"
                            + clase.nombre + "'.");
                    return;
                }
            }
            clase.constructores.add(info);
            simbolo(clase.nombre, info.firma(), "Constructor", Tipo.VOID, f.tokNombre);
            return;
        }

        Map<String, List<MetodoInfo>> destino = clase != null ? clase.metodos : ctx.funciones;
        List<MetodoInfo> lista = destino.computeIfAbsent(f.nombre, k -> new ArrayList<>());
        for (MetodoInfo otro : lista) {
            if (otro.params.equals(info.params)) {
                error(E_DUPLICADO, f.tokNombre, "El método " + info.firma() + " ya está declarado"
                        + (clase != null ? " en la clase '" + clase.nombre + "'" : "") + ".");
                return;
            }
        }
        lista.add(info);
        simbolo(clase != null ? clase.nombre : "(global)", info.firma(), "Método", info.retorno, f.tokNombre);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PASADA 2 — verificación de cuerpos
    // ─────────────────────────────────────────────────────────────────────────
    private void validarImports() {
        for (List<ComponenteLexico> partes : ctx.imports) {
            boolean calificado = false;
            ComponenteLexico ultimo = null;
            for (ComponenteLexico p : partes) {
                if (p.getTipo() == TipoComponente.PUNTO) {
                    calificado = true;
                } else {
                    ultimo = p;
                }
            }
            if (ultimo == null) {
                continue;
            }
            String nombre = ultimo.getLexema();
            if (calificado) {
                ctx.externas.add(nombre); // java.util.Scanner, etc.: biblioteca externa
                clases.putIfAbsent("@ext:" + nombre, new ClaseInfo(nombre, true));
            } else if (!clases.containsKey(nombre)) {
                error(E_IMPORT, ultimo, "No se encontró la clase '" + nombre
                        + "' que se intenta importar (no está declarada en ningún archivo cargado).");
            }
        }
    }

    private void recorrerPrograma(NodoAst nodo) {
        for (NodoAst h : nodo.getHijos()) {
            if (h.getToken() != null) {
                continue;
            }
            switch (h.getNombre()) {
                case "Programa":
                case "Bloque":
                    recorrerPrograma(h);
                    break;
                case "Clase":
                    analizarClase(h);
                    break;
                case "Atributo":
                    claseActual = null;
                    analizarInicializadorAtributo(h);
                    break;
                case "Metodo":
                case "Constructor":
                    claseActual = null;
                    analizarMetodo(h);
                    break;
                default:
                    break;
            }
        }
    }

    private void analizarClase(NodoAst nodo) {
        ClaseInfo info = infoPorNodo.get(nodo);
        if (info == null) {
            return;
        }
        claseActual = info;
        for (NodoAst h : nodo.getHijos()) {
            if (esNodo(h, "Atributo")) {
                analizarInicializadorAtributo(h);
            } else if (esNodo(h, "Metodo") || esNodo(h, "Constructor")) {
                analizarMetodo(h);
            }
        }
        claseActual = null;
    }

    private void analizarInicializadorAtributo(NodoAst nodo) {
        List<NodoAst> hs = nodo.getHijos();
        if (hs.isEmpty()) {
            return;
        }
        Tipo declarado = leerTipo(hs.get(0));
        ComponenteLexico id = null;
        int extra = 0;
        int asig = -1;
        for (int i = 1; i < hs.size(); i++) {
            if (id == null && esTok(hs.get(i), TipoComponente.IDENTIFICADOR)) {
                id = hs.get(i).getToken();
            } else if (esTok(hs.get(i), TipoComponente.CORCHETE_ABRE)) {
                extra++;
            } else if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                asig = i;
                break;
            }
        }
        if (asig < 0 || asig + 1 >= hs.size() || !esNodo(hs.get(asig + 1), "Expresion")) {
            return;
        }
        ambitos.clear();
        ambitos.add(new HashMap<>());
        metodoActual = null;
        Tipo valor = evaluarExpresion(hs.get(asig + 1));
        verificarAsignacion(declarado.arreglo(extra), valor, hs.get(asig).getToken(),
                "la inicialización del atributo '" + (id != null ? id.getLexema() : "?") + "'");
    }

    private void analizarMetodo(NodoAst nodo) {
        Firma f = leerFirma(nodo);
        if (f.nombre == null || f.bloque == null) {
            return;
        }
        MetodoInfo info = new MetodoInfo(f.nombre, f.retorno, f.tiposParam, f.constructor);
        metodoActual = info;
        ambitos.clear();
        ambitos.add(new HashMap<>());

        for (int i = 0; i < f.tiposParam.size(); i++) {
            ComponenteLexico pid = f.tokParam.get(i);
            if (pid != null) {
                declarar(pid, f.tiposParam.get(i), "Parámetro");
            }
        }
        analizarBloque(f.bloque, false);

        if (!f.constructor && !f.retorno.esVoid() && !f.retorno.esDesconocido() && !siempreRetorna(f.bloque)) {
            error(E_RETORNO, f.tokNombre, "El método '" + f.nombre + "' debe retornar un valor de tipo '"
                    + f.retorno + "' en todos los caminos de ejecución.");
        }
        metodoActual = null;
        ambitos.clear();
    }

    // ── Ámbitos ──────────────────────────────────────────────────────────────
    private void declarar(ComponenteLexico id, Tipo tipo, String categoria) {
        for (Map<String, Simbolo> a : ambitos) {
            Simbolo previo = a.get(id.getLexema());
            if (previo != null) {
                error(E_DUPLICADO, id, "La variable '" + id.getLexema() + "' ya está declarada en este método (línea "
                        + previo.linea + ").");
                return;
            }
        }
        ambitos.get(ambitos.size() - 1).put(id.getLexema(), new Simbolo(id.getLexema(), tipo, id.getLinea()));
        simbolo(ambitoActual(), id.getLexema(), categoria, tipo, id);
    }

    private Simbolo buscarVariable(String nombre) {
        for (int i = ambitos.size() - 1; i >= 0; i--) {
            Simbolo s = ambitos.get(i).get(nombre);
            if (s != null) {
                return s;
            }
        }
        if (claseActual != null) {
            Simbolo s = claseActual.campos.get(nombre);
            if (s != null) {
                return s;
            }
        }
        return ctx.variables.get(nombre);
    }

    /** Clases de java.lang que se pueden usar sin import: no se pueden verificar, pero tampoco son un error. */
    private static final Set<String> CLASES_ESTANDAR = Set.of("Math", "System", "Integer", "Double", "Float",
            "Boolean", "Character", "Long", "Object", "Arrays");

    private boolean esNombreDeClase(String nombre) {
        return clases.containsKey(nombre) || ctx.externas.contains(nombre) || CLASES_ESTANDAR.contains(nombre);
    }

    private ClaseInfo buscarClase(String nombre) {
        ClaseInfo c = clases.get(nombre);
        if (c != null) {
            return c;
        }
        if (ctx.externas.contains(nombre)) {
            return clases.get("@ext:" + nombre);
        }
        return CLASES_ESTANDAR.contains(nombre) ? new ClaseInfo(nombre, true) : null;
    }

    // ── Sentencias ───────────────────────────────────────────────────────────
    private void analizarBloque(NodoAst bloque, boolean nuevoAmbito) {
        if (bloque == null) {
            return;
        }
        if (nuevoAmbito) {
            ambitos.add(new HashMap<>());
        }
        boolean huboRetorno = false;
        boolean avisado = false;
        for (NodoAst s : bloque.getHijos()) {
            if (s.getToken() != null) {
                continue;
            }
            if (huboRetorno && !avisado) {
                advertencia(A_INALCANZABLE, primerToken(s), "Código inalcanzable: aparece después de un 'return'.");
                avisado = true;
            }
            analizarSentencia(s);
            if (esNodo(s, "Return")) {
                huboRetorno = true;
            }
        }
        if (nuevoAmbito) {
            ambitos.remove(ambitos.size() - 1);
        }
    }

    private void analizarSentencia(NodoAst s) {
        switch (s.getNombre()) {
            case "Sentencia":
                for (NodoAst h : s.getHijos()) {
                    if (esNodo(h, "DeclaracionLocal")) {
                        analizarDeclaracion(h);
                    } else if (esNodo(h, "Asignacion")) {
                        analizarAsignacion(h, false);
                    } else if (esNodo(h, "Expresion")) {
                        evaluarExpresion(h);
                    }
                }
                break;
            case "If":
                analizarIf(s);
                break;
            case "While":
                analizarCondicionYBloque(s, "while");
                break;
            case "Exist":
                analizarExist(s);
                break;
            case "Repeat":
                analizarRepeat(s);
                break;
            case "For":
                analizarFor(s);
                break;
            case "Return":
                analizarReturn(s);
                break;
            default:
                break;
        }
    }

    private void analizarDeclaracion(NodoAst d) {
        List<NodoAst> hs = d.getHijos();
        if (hs.isEmpty()) {
            return;
        }
        Tipo t = leerTipo(hs.get(0));
        ComponenteLexico id = null;
        int extra = 0;
        int asig = -1;
        for (int i = 1; i < hs.size(); i++) {
            if (id == null && esTok(hs.get(i), TipoComponente.IDENTIFICADOR)) {
                id = hs.get(i).getToken();
            } else if (esTok(hs.get(i), TipoComponente.CORCHETE_ABRE)) {
                extra++;
            } else if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                asig = i;
                break;
            }
        }
        if (id == null) {
            return;
        }
        t = t.arreglo(extra);
        Tipo valor = null;
        if (asig >= 0 && asig + 1 < hs.size() && esNodo(hs.get(asig + 1), "Expresion")) {
            valor = evaluarExpresion(hs.get(asig + 1));
        }
        declarar(id, t, "Variable local");
        if (valor != null) {
            verificarAsignacion(t, valor, hs.get(asig).getToken(), "la inicialización de '" + id.getLexema() + "'");
        }
    }

    /** @param implicita si el destino puede no estar declarado (variable de 'repeat each') */
    private void analizarAsignacion(NodoAst a, boolean implicita) {
        List<NodoAst> hs = a.getHijos();
        int asig = -1;
        for (int i = 0; i < hs.size(); i++) {
            if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                asig = i;
                break;
            }
        }
        if (asig < 1) {
            return;
        }
        NodoAst rhs = asig + 1 < hs.size() ? hs.get(asig + 1) : null;
        ComponenteLexico opAsig = hs.get(asig).getToken();

        // variable de iteración de 'repeat each': si no existe, se declara con el tipo del valor
        if (implicita && asig == 1 && esTok(hs.get(0), TipoComponente.IDENTIFICADOR)
                && buscarVariable(hs.get(0).getToken().getLexema()) == null
                && !esNombreDeClase(hs.get(0).getToken().getLexema())) {
            Tipo valor = rhs != null ? evaluarExpresion(rhs) : Tipo.DESCONOCIDO;
            declarar(hs.get(0).getToken(), valor, "Variable de iteración");
            tiposAsig.put(a, new Tipo[] { valor, valor });
            return;
        }

        Tipo destino = evaluarCadena(hs, asig);
        Tipo valor = rhs != null ? evaluarExpresion(rhs) : Tipo.DESCONOCIDO;
        tiposAsig.put(a, new Tipo[] { destino, valor });
        if (destino.esReferenciaClase()) {
            error(E_TIPOS, hs.get(0).getToken(), "'" + destino.getBase()
                    + "' es una clase, no una variable: no se le puede asignar un valor.");
            return;
        }
        verificarAsignacion(destino, valor, opAsig, "la asignación");
    }

    private void analizarIf(NodoAst s) {
        List<NodoAst> hs = s.getHijos();
        boolean enElse = false;
        for (NodoAst h : hs) {
            if (esTok(h, TipoComponente.SINO)) {
                enElse = true;
            } else if (esNodo(h, "Expresion") && !enElse) {
                exigirBooleano(evaluarExpresion(h), "if", h);
            } else if (esNodo(h, "Bloque")) {
                analizarBloque(h, true);
            } else if (esNodo(h, "If")) {
                analizarIf(h);
            }
        }
    }

    private void analizarCondicionYBloque(NodoAst s, String nombre) {
        for (NodoAst h : s.getHijos()) {
            if (esNodo(h, "Expresion")) {
                exigirBooleano(evaluarExpresion(h), nombre, h);
            } else if (esNodo(h, "Bloque")) {
                analizarBloque(h, true);
            }
        }
    }

    /** 'exist' es un condicional propio del lenguaje: se verifica su expresión, sin exigir tipo. */
    private void analizarExist(NodoAst s) {
        for (NodoAst h : s.getHijos()) {
            if (esNodo(h, "Expresion")) {
                evaluarExpresion(h);
            } else if (esNodo(h, "Bloque")) {
                analizarBloque(h, true);
            }
        }
    }

    private void analizarRepeat(NodoAst s) {
        ambitos.add(new HashMap<>());
        List<NodoAst> hs = s.getHijos();
        boolean esEach = false;
        boolean esWhile = false;
        for (NodoAst h : hs) {
            if (esTok(h, TipoComponente.CADA)) {
                esEach = true;
            } else if (esTok(h, TipoComponente.MIENTRAS)) {
                esWhile = true;
            }
        }
        boolean condicionVista = false;
        for (NodoAst h : hs) {
            if (esNodo(h, "DeclaracionLocal")) {
                analizarDeclaracion(h);
            } else if (esNodo(h, "Asignacion")) {
                analizarAsignacion(h, esEach);
            } else if (esNodo(h, "Expresion")) {
                if (esEach) {
                    evaluarExpresion(h);
                } else if (!condicionVista || esWhile) {
                    exigirBooleano(evaluarExpresion(h), "repeat", h);
                    condicionVista = true;
                } else {
                    evaluarExpresion(h);
                }
            } else if (esNodo(h, "Bloque")) {
                analizarBloque(h, true);
            }
        }
        ambitos.remove(ambitos.size() - 1);
    }

    private void analizarFor(NodoAst s) {
        ambitos.add(new HashMap<>());
        int parte = 0;
        boolean dentro = false;
        for (NodoAst h : s.getHijos()) {
            if (esTok(h, TipoComponente.PARENTESIS_ABRE) && !dentro) {
                dentro = true;
                continue;
            }
            if (esTok(h, TipoComponente.PUNTO_Y_COMA)) {
                parte++;
                continue;
            }
            if (esNodo(h, "Bloque")) {
                analizarBloque(h, true);
            } else if (esNodo(h, "DeclaracionLocal")) {
                analizarDeclaracion(h);
            } else if (esNodo(h, "Asignacion")) {
                analizarAsignacion(h, false);
            } else if (esNodo(h, "Expresion")) {
                Tipo t = evaluarExpresion(h);
                if (parte == 1) {
                    exigirBooleano(t, "for", h);
                }
            }
        }
        ambitos.remove(ambitos.size() - 1);
    }

    private void analizarReturn(NodoAst s) {
        ComponenteLexico kw = s.getHijos().isEmpty() ? null : s.getHijos().get(0).getToken();
        NodoAst expr = null;
        for (NodoAst h : s.getHijos()) {
            if (esNodo(h, "Expresion")) {
                expr = h;
            }
        }
        Tipo valor = expr != null ? evaluarExpresion(expr) : null;
        if (metodoActual == null) {
            error(E_RETORNO, kw, "'return' fuera de un método.");
            return;
        }
        Tipo esperado = metodoActual.retorno;
        String quien = metodoActual.constructor ? "El constructor" : "El método";
        if (esperado.esVoid()) {
            if (valor != null) {
                error(E_RETORNO, kw, quien + " '" + metodoActual.nombre + "' es void y no puede retornar un valor.");
            }
        } else if (valor == null) {
            error(E_RETORNO, kw, "El método '" + metodoActual.nombre + "' debe retornar un valor de tipo '"
                    + esperado + "'.");
        } else {
            verificarAsignacion(esperado, valor, kw, "el retorno del método '" + metodoActual.nombre + "'");
        }
    }

    private boolean siempreRetorna(NodoAst bloque) {
        if (bloque == null) {
            return false;
        }
        for (NodoAst s : bloque.getHijos()) {
            if (esNodo(s, "Return")) {
                return true;
            }
            if (esNodo(s, "If") && ifSiempreRetorna(s)) {
                return true;
            }
        }
        return false;
    }

    private boolean ifSiempreRetorna(NodoAst nodoIf) {
        boolean enElse = false;
        boolean entonces = false;
        boolean sino = false;
        for (NodoAst h : nodoIf.getHijos()) {
            if (esTok(h, TipoComponente.SINO)) {
                enElse = true;
            } else if (esNodo(h, "Bloque")) {
                if (enElse) {
                    sino = siempreRetorna(h);
                } else {
                    entonces = siempreRetorna(h);
                }
            } else if (esNodo(h, "If") && enElse) {
                sino = ifSiempreRetorna(h);
            }
        }
        return entonces && sino;
    }

    // ── Verificaciones de tipo ───────────────────────────────────────────────
    private void exigirBooleano(Tipo t, String estructura, NodoAst expr) {
        if (t.esDesconocido() || t.esBoolean()) {
            return;
        }
        String detalle = t.esVoid() ? "una llamada que no devuelve valor (void)" : "'" + t + "'";
        error(E_CONDICION, primerToken(expr), "La condición de '" + estructura
                + "' debe ser de tipo boolean, pero es " + detalle + ".");
    }

    private static boolean compatible(Tipo destino, Tipo origen) {
        if (destino.esDesconocido() || origen.esDesconocido()) {
            return true;
        }
        if (origen.esReferenciaClase() || origen.esVoid() || destino.esVoid()) {
            return false;
        }
        if (destino.equals(origen)) {
            return true;
        }
        if (destino.esArreglo() || origen.esArreglo()) {
            return false;
        }
        if (destino.equals(Tipo.FLOAT) && (origen.equals(Tipo.INT) || origen.equals(Tipo.CHAR))) {
            return true;
        }
        return destino.equals(Tipo.INT) && origen.equals(Tipo.CHAR);
    }

    private void verificarAsignacion(Tipo destino, Tipo origen, ComponenteLexico pos, String contexto) {
        if (compatible(destino, origen)) {
            return;
        }
        String msg;
        if (origen.esVoid()) {
            msg = "En " + contexto + ": la expresión no produce valor (es una llamada void).";
        } else if (origen.esReferenciaClase()) {
            msg = "En " + contexto + ": '" + origen.getBase() + "' es una clase, no un valor.";
        } else if (destino.equals(Tipo.INT) && origen.equals(Tipo.FLOAT)) {
            msg = "En " + contexto + ": no se puede convertir 'float' a 'int' (pérdida de precisión).";
        } else {
            msg = "Tipos incompatibles en " + contexto + ": se esperaba '" + destino + "' pero la expresión es '"
                    + origen + "'.";
        }
        error(E_TIPOS, pos, msg);
    }

    // ── Expresiones ──────────────────────────────────────────────────────────
    private Tipo evaluarExpresion(NodoAst e) {
        if (e == null || e.getHijos().isEmpty()) {
            return Tipo.DESCONOCIDO;
        }
        List<NodoAst> hs = e.getHijos();
        int i = 0;
        ComponenteLexico unario = null;
        if (esTok(hs.get(0), TipoComponente.NEGACION) || esTok(hs.get(0), TipoComponente.RESTA)) {
            unario = hs.get(0).getToken();
            i = 1;
        }
        if (i >= hs.size()) {
            return Tipo.DESCONOCIDO;
        }
        List<Val> vals = new ArrayList<>();
        List<ComponenteLexico> ops = new ArrayList<>();
        vals.add(evaluarOperando(hs.get(i), unario));
        i++;
        while (i + 1 < hs.size() && hs.get(i).getToken() != null) {
            ops.add(hs.get(i).getToken());
            i++;
            ComponenteLexico unarioDerecho = null;
            if ((esTok(hs.get(i), TipoComponente.NEGACION) || esTok(hs.get(i), TipoComponente.RESTA))
                    && i + 1 < hs.size()) {
                unarioDerecho = hs.get(i).getToken();
                i++;
            }
            vals.add(evaluarOperando(hs.get(i), unarioDerecho));
            i++;
        }
        int[] cursor = { 0, 0 }; // {operando, operador}
        Val resultado = reducir(vals, ops, cursor, 1);
        if (resultado.sem != null) {
            exprSem.put(e, resultado.sem);
        }
        return resultado.tipo;
    }

    private Val evaluarOperando(NodoAst n, ComponenteLexico unario) {
        ComponenteLexico pos = primerToken(n);
        if (!esNodo(n, "Termino")) {
            return new Val(Tipo.DESCONOCIDO, pos, false);
        }
        Tipo t = evaluarCadena(n.getHijos(), n.getHijos().size());
        NodoSemantico hoja = nodoOperando(n, t);
        boolean cero = false;
        if (n.getHijos().size() == 1 && n.getHijos().get(0).getToken() != null) {
            ComponenteLexico lit = n.getHijos().get(0).getToken();
            if (lit.getTipo() == TipoComponente.NUMERO_ENTERO || lit.getTipo() == TipoComponente.NUMERO_FLOTANTE) {
                try {
                    cero = Double.parseDouble(lit.getLexema()) == 0.0;
                } catch (NumberFormatException ex) {
                    cero = false;
                }
            }
        }
        if (unario != null) {
            if (unario.getTipo() == TipoComponente.NEGACION) {
                if (!t.esDesconocido() && !t.esBoolean()) {
                    error(E_OPERADOR, unario, "El operador '!' requiere un operando boolean, pero se encontró '"
                            + t + "'.");
                }
                t = Tipo.BOOLEAN;
            } else {
                if (!t.esDesconocido() && !t.esNumerico()) {
                    error(E_OPERADOR, unario, "El operador '-' requiere un operando numérico, pero se encontró '"
                            + t + "'.");
                    t = Tipo.DESCONOCIDO;
                } else if (t.equals(Tipo.CHAR)) {
                    t = Tipo.INT;
                }
            }
            cero = false;
            hoja = new NodoSemantico(Categoria.OPERADOR, unario.getLexema(), t.toString(),
                    unario.getTipo() == TipoComponente.NEGACION ? "negación lógica (unario)" : "signo menos (unario)",
                    unario).agregar(hoja);
        }
        Val v = new Val(t, pos, cero);
        v.sem = hoja;
        return v;
    }

    private static int precedencia(ComponenteLexico op) {
        switch (op.getTipo()) {
            case O_LOGICO: return 1;
            case Y_LOGICO: return 2;
            case IGUALDAD: case DIFERENTE: return 3;
            case MENOR_QUE: case MENOR_O_IGUAL: case MAYOR_QUE: case MAYOR_O_IGUAL: return 4;
            case SUMA: case RESTA: return 5;
            default: return 6;
        }
    }

    private Val reducir(List<Val> vals, List<ComponenteLexico> ops, int[] cur, int minPrec) {
        Val izq = vals.get(cur[0]++);
        while (cur[1] < ops.size() && precedencia(ops.get(cur[1])) >= minPrec) {
            ComponenteLexico op = ops.get(cur[1]++);
            Val der = reducir(vals, ops, cur, precedencia(op) + 1);
            Val previo = izq;
            izq = binario(op, previo, der);
            izq.sem = new NodoSemantico(Categoria.OPERADOR, op.getLexema(), izq.tipo.toString(), null, op)
                    .agregar(semDe(previo)).agregar(semDe(der));
        }
        return izq;
    }

    private Val binario(ComponenteLexico op, Val l, Val r) {
        Tipo a = l.tipo;
        Tipo b = r.tipo;
        boolean incierto = a.esDesconocido() || b.esDesconocido();
        String s = op.getLexema();

        switch (op.getTipo()) {
            case Y_LOGICO:
            case O_LOGICO:
                if (!incierto && (!a.esBoolean() || !b.esBoolean())) {
                    error(E_OPERADOR, op, "El operador '" + s + "' requiere operandos boolean, pero se encontró '"
                            + a + "' y '" + b + "'.");
                }
                return new Val(Tipo.BOOLEAN, op, false);

            case MENOR_QUE:
            case MENOR_O_IGUAL:
            case MAYOR_QUE:
            case MAYOR_O_IGUAL:
                if (!incierto && (!a.esNumerico() || !b.esNumerico())) {
                    error(E_OPERADOR, op, "El operador '" + s + "' requiere operandos numéricos, pero se encontró '"
                            + a + "' y '" + b + "'.");
                }
                return new Val(Tipo.BOOLEAN, op, false);

            case IGUALDAD:
            case DIFERENTE:
                if (!incierto && !comparables(a, b)) {
                    error(E_OPERADOR, op, "No se pueden comparar '" + a + "' y '" + b + "' con '" + s + "'.");
                }
                return new Val(Tipo.BOOLEAN, op, false);

            case SUMA:
                if (incierto) {
                    return new Val(a.esDesconocido() ? b : a, op, false);
                }
                if (a.esString() || b.esString()) {
                    boolean otroOk = (a.esString() ? b : a).esPrimitivo() || (a.esString() && b.esString());
                    if (!otroOk) {
                        error(E_OPERADOR, op, "No se puede concatenar '" + a + "' con '" + b + "' usando '+'.");
                        return new Val(Tipo.DESCONOCIDO, op, false);
                    }
                    return new Val(Tipo.STRING, op, false);
                }
                return aritmetico(op, l, r);

            default: // - * / %
                if (incierto) {
                    return new Val(a.esDesconocido() ? b : a, op, false);
                }
                if ((op.getTipo() == TipoComponente.DIVISION || op.getTipo() == TipoComponente.MODULO)
                        && r.cero && a.esNumerico()) {
                    advertencia(A_DIV_CERO, op, "División entre cero: el divisor es la constante 0.");
                }
                return aritmetico(op, l, r);
        }
    }

    private Val aritmetico(ComponenteLexico op, Val l, Val r) {
        Tipo a = l.tipo;
        Tipo b = r.tipo;
        if (!a.esNumerico() || !b.esNumerico()) {
            error(E_OPERADOR, op, "El operador '" + op.getLexema() + "' no se puede aplicar a '" + a + "' y '" + b
                    + "' (se requieren operandos numéricos).");
            return new Val(Tipo.DESCONOCIDO, op, false);
        }
        boolean flotante = a.equals(Tipo.FLOAT) || b.equals(Tipo.FLOAT);
        return new Val(flotante ? Tipo.FLOAT : Tipo.INT, op, false);
    }

    private static boolean comparables(Tipo a, Tipo b) {
        if (a.esReferenciaClase() || b.esReferenciaClase() || a.esVoid() || b.esVoid()) {
            return false;
        }
        if (a.esNumerico() && b.esNumerico()) {
            return true;
        }
        return a.equals(b);
    }

    // ── Cadenas de acceso:  primario { .miembro | [indice] | (args) | ++ | -- } ─
    private Tipo evaluarCadena(List<NodoAst> it, int fin) {
        if (fin <= 0 || it.get(0).getToken() == null) {
            return Tipo.DESCONOCIDO;
        }
        ComponenteLexico t0 = it.get(0).getToken();
        Tipo cur;
        int i = 1;
        switch (t0.getTipo()) {
            case NEW:
                return evaluarNew(it, fin);
            case PARENTESIS_ABRE:
                cur = fin > 1 && esNodo(it.get(1), "Expresion") ? evaluarExpresion(it.get(1)) : Tipo.DESCONOCIDO;
                i = fin;
                break;
            case NUMERO_ENTERO:
                cur = Tipo.INT;
                break;
            case NUMERO_FLOTANTE:
                cur = Tipo.FLOAT;
                break;
            case LITERAL_CADENA:
                cur = Tipo.STRING;
                break;
            case LITERAL_CARACTER:
                cur = Tipo.CHAR;
                break;
            case THIS:
                if (claseActual == null) {
                    error(E_USO, t0, "'this' solo se puede usar dentro de una clase.");
                    cur = Tipo.DESCONOCIDO;
                } else {
                    cur = Tipo.instancia(claseActual.nombre);
                }
                break;
            case IDENTIFICADOR:
                if (fin > 1 && esNodo(it.get(1), "LlamadaMetodo")) {
                    cur = llamadaNoCalificada(t0, it.get(1));
                    i = 2;
                } else {
                    cur = resolverIdentificador(t0);
                }
                break;
            default:
                return Tipo.DESCONOCIDO;
        }

        while (i < fin) {
            NodoAst n = it.get(i);
            if (esTok(n, TipoComponente.PUNTO)) {
                NodoAst nombre = i + 1 < fin ? it.get(i + 1) : null;
                if (!esTok(nombre, TipoComponente.IDENTIFICADOR)) {
                    i++;
                    continue;
                }
                ComponenteLexico nt = nombre.getToken();
                if (i + 2 < fin && esNodo(it.get(i + 2), "LlamadaMetodo")) {
                    cur = llamadaMiembro(cur, nt, it.get(i + 2));
                    i += 3;
                } else {
                    cur = accesoCampo(cur, nt);
                    i += 2;
                }
            } else if (esTok(n, TipoComponente.CORCHETE_ABRE)) {
                int j = i + 1;
                Tipo idx = null;
                ComponenteLexico posIdx = n.getToken();
                if (j < fin && esNodo(it.get(j), "Expresion")) {
                    posIdx = primerToken(it.get(j));
                    idx = evaluarExpresion(it.get(j));
                    j++;
                }
                if (j < fin && esTok(it.get(j), TipoComponente.CORCHETE_CIERRA)) {
                    j++;
                }
                if (!cur.esDesconocido()) {
                    if (!cur.esArreglo()) {
                        error(E_INDICE, n.getToken(), "No se puede indexar un valor de tipo '" + cur
                                + "': no es un arreglo.");
                        cur = Tipo.DESCONOCIDO;
                    } else {
                        cur = cur.elemento();
                    }
                }
                if (idx != null && !idx.esDesconocido() && !idx.equals(Tipo.INT) && !idx.equals(Tipo.CHAR)) {
                    error(E_INDICE, posIdx, "El índice de un arreglo debe ser entero, pero es '" + idx + "'.");
                }
                i = j;
            } else if (esTok(n, TipoComponente.INCREMENTO) || esTok(n, TipoComponente.DECREMENTO)) {
                if (!cur.esDesconocido() && !cur.esNumerico()) {
                    error(E_OPERADOR, n.getToken(), "El operador '" + n.getToken().getLexema()
                            + "' requiere una variable numérica, pero se encontró '" + cur + "'.");
                }
                i++;
            } else if (esNodo(n, "LlamadaMetodo")) {
                evaluarArgumentos(n);
                if (!cur.esDesconocido()) {
                    error(E_LLAMADA, primerToken(n), "Un valor de tipo '" + cur + "' no se puede invocar como método.");
                }
                cur = Tipo.DESCONOCIDO;
                i++;
            } else {
                i++;
            }
        }
        return cur;
    }

    private Tipo resolverIdentificador(ComponenteLexico id) {
        Simbolo s = buscarVariable(id.getLexema());
        if (s != null) {
            return s.tipo;
        }
        if (esNombreDeClase(id.getLexema())) {
            return Tipo.referenciaAClase(id.getLexema());
        }
        // El léxico no tiene palabras reservadas para los booleanos: llegan como identificadores
        if (id.getLexema().equals("true") || id.getLexema().equals("false")) {
            return Tipo.BOOLEAN;
        }
        error(E_NO_DECLARADO, id, "'" + id.getLexema() + "' no ha sido declarado (ni como variable ni como clase"
                + " importada).");
        return Tipo.DESCONOCIDO;
    }

    private Tipo evaluarNew(List<NodoAst> it, int fin) {
        ComponenteLexico kw = it.get(0).getToken();
        String base = fin > 1 && it.get(1).getToken() != null ? nombreBase(it.get(1).getToken().getTipo()) : null;
        if (base == null) {
            return Tipo.DESCONOCIDO;
        }
        int dims = 0;
        for (int i = 2; i < fin; i++) {
            if (esNodo(it.get(i), "Array")) {
                for (NodoAst x : it.get(i).getHijos()) {
                    if (esTok(x, TipoComponente.CORCHETE_ABRE)) {
                        dims++;
                    } else if (esNodo(x, "Expresion")) {
                        Tipo t = evaluarExpresion(x);
                        if (!t.esDesconocido() && !t.equals(Tipo.INT) && !t.equals(Tipo.CHAR)) {
                            error(E_INDICE, primerToken(x), "El tamaño de un arreglo debe ser entero, pero es '"
                                    + t + "'.");
                        }
                    }
                }
            }
        }
        if (dims == 0) {
            error(E_TIPOS, kw, "'new " + base + "' requiere dimensiones de arreglo, por ejemplo: new " + base + "[5].");
            return Tipo.DESCONOCIDO;
        }
        return Tipo.de(base, dims);
    }

    // ── Llamadas ─────────────────────────────────────────────────────────────
    private List<Tipo> evaluarArgumentos(NodoAst llamada) {
        List<Tipo> tipos = new ArrayList<>();
        for (NodoAst h : llamada.getHijos()) {
            if (esNodo(h, "Expresion")) {
                tipos.add(evaluarExpresion(h));
            }
        }
        return tipos;
    }

    private List<ComponenteLexico> posicionesArgumentos(NodoAst llamada) {
        List<ComponenteLexico> pos = new ArrayList<>();
        for (NodoAst h : llamada.getHijos()) {
            if (esNodo(h, "Expresion")) {
                pos.add(primerToken(h));
            }
        }
        return pos;
    }

    private Tipo llamadaNoCalificada(ComponenteLexico nombre, NodoAst llamada) {
        List<Tipo> args = evaluarArgumentos(llamada);
        List<ComponenteLexico> pos = posicionesArgumentos(llamada);
        String n = nombre.getLexema();

        if (claseActual != null && claseActual.metodos.containsKey(n)) {
            return resolverLlamada(claseActual.metodos.get(n), n, args, pos, nombre);
        }
        if (ctx.funciones.containsKey(n)) {
            return resolverLlamada(ctx.funciones.get(n), n, args, pos, nombre);
        }
        if (n.equals("print") || n.equals("println")) {
            for (int i = 0; i < args.size(); i++) {
                if (args.get(i).esVoid()) {
                    error(E_LLAMADA, pos.get(i), "El argumento de '" + n + "' no produce valor (es void).");
                }
            }
            return Tipo.VOID;
        }
        error(E_LLAMADA, nombre, "El método '" + n + "' no está declarado"
                + (claseActual != null ? " en la clase '" + claseActual.nombre + "'" : "") + ".");
        return Tipo.DESCONOCIDO;
    }

    private Tipo resolverLlamada(List<MetodoInfo> candidatos, String nombre, List<Tipo> args,
            List<ComponenteLexico> pos, ComponenteLexico tok) {
        List<MetodoInfo> mismaAridad = new ArrayList<>();
        for (MetodoInfo m : candidatos) {
            if (m.params.size() == args.size()) {
                mismaAridad.add(m);
            }
        }
        if (mismaAridad.isEmpty()) {
            if (candidatos.size() == 1) {
                int esperados = candidatos.get(0).params.size();
                error(E_LLAMADA, tok, "El método '" + candidatos.get(0).firma() + "' espera " + esperados
                        + " argumento" + (esperados == 1 ? "" : "s") + " pero se recibieron " + args.size() + ".");
            } else {
                error(E_LLAMADA, tok, "Ninguna versión de '" + nombre + "' acepta " + args.size() + " argumentos.");
            }
            return candidatos.get(0).retorno;
        }
        for (MetodoInfo m : mismaAridad) {
            boolean ok = true;
            for (int i = 0; i < args.size(); i++) {
                if (!compatible(m.params.get(i), args.get(i))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return m.retorno;
            }
        }
        MetodoInfo m = mismaAridad.get(0);
        for (int i = 0; i < args.size(); i++) {
            if (!compatible(m.params.get(i), args.get(i))) {
                error(E_LLAMADA, pos.get(i), "Argumento " + (i + 1) + " de '" + m.firma() + "': se esperaba '"
                        + m.params.get(i) + "' pero se recibió '" + args.get(i) + "'.");
                break;
            }
        }
        return m.retorno;
    }

    /** Métodos predefinidos de arreglos y cadenas: {nombre, retorno, parámetros...}. "$E" = tipo del elemento. */
    private static final String[][] METODOS_ARREGLO = {
        { "exists", "boolean", "$E" }, { "contains", "boolean", "$E" }, { "length", "int" },
        { "size", "int" }, { "isEmpty", "boolean" }, { "add", "void", "$E" },
    };
    private static final String[][] METODOS_STRING = {
        { "length", "int" }, { "charAt", "char", "int" }, { "equals", "boolean", "string" },
        { "isEmpty", "boolean" }, { "toUpperCase", "string" }, { "toLowerCase", "string" }, { "trim", "string" },
        { "contains", "boolean", "string" }, { "indexOf", "int", "string" }, { "substring", "string", "int" },
        { "substring", "string", "int", "int" },
    };

    private Tipo llamadaMiembro(Tipo receptor, ComponenteLexico nombre, NodoAst llamada) {
        List<Tipo> args = evaluarArgumentos(llamada);
        List<ComponenteLexico> pos = posicionesArgumentos(llamada);
        if (receptor.esDesconocido()) {
            return Tipo.DESCONOCIDO;
        }
        String n = nombre.getLexema();

        if (receptor.esArreglo()) {
            return metodoPredefinido(METODOS_ARREGLO, receptor, receptor.elemento(), nombre, args, pos);
        }
        if (receptor.esString()) {
            return metodoPredefinido(METODOS_STRING, receptor, null, nombre, args, pos);
        }
        if (receptor.esPrimitivo()) {
            error(E_MIEMBRO, nombre, "El tipo '" + receptor + "' no tiene métodos (se intentó llamar a '" + n + "').");
            return Tipo.DESCONOCIDO;
        }
        ClaseInfo c = buscarClase(receptor.getBase());
        if (c == null || c.externa) {
            return Tipo.DESCONOCIDO; // biblioteca externa: no se puede verificar
        }
        List<MetodoInfo> candidatos = c.metodos.get(n);
        if (candidatos == null) {
            error(E_LLAMADA, nombre, "La clase '" + c.nombre + "' no tiene un método llamado '" + n + "'.");
            return Tipo.DESCONOCIDO;
        }
        return resolverLlamada(candidatos, n, args, pos, nombre);
    }

    private Tipo metodoPredefinido(String[][] tabla, Tipo receptor, Tipo elemento, ComponenteLexico nombre,
            List<Tipo> args, List<ComponenteLexico> pos) {
        String n = nombre.getLexema();
        boolean existeNombre = false;
        for (String[] m : tabla) {
            if (!m[0].equals(n)) {
                continue;
            }
            existeNombre = true;
            if (m.length - 2 != args.size()) {
                continue;
            }
            for (int i = 0; i < args.size(); i++) {
                String esperado = m[2 + i];
                Tipo te = esperado.equals("$E") ? elemento : Tipo.de(esperado, 0);
                if (te != null && !compatible(te, args.get(i))) {
                    error(E_LLAMADA, pos.get(i), "Argumento " + (i + 1) + " de '" + n + "': se esperaba '" + te
                            + "' pero se recibió '" + args.get(i) + "'.");
                    break;
                }
            }
            return Tipo.de(m[1], 0);
        }
        if (existeNombre) {
            error(E_LLAMADA, nombre, "Número de argumentos incorrecto al llamar a '" + n + "' sobre '" + receptor + "'.");
        } else {
            advertencia(A_MIEMBRO_DESCONOCIDO, nombre, "El método '" + n + "' no es un método predefinido de '"
                    + receptor + "'; no se pudo verificar.");
        }
        return Tipo.DESCONOCIDO;
    }

    private Tipo accesoCampo(Tipo receptor, ComponenteLexico nombre) {
        if (receptor.esDesconocido()) {
            return Tipo.DESCONOCIDO;
        }
        String n = nombre.getLexema();
        if (receptor.esArreglo()) {
            if (n.equals("length")) {
                return Tipo.INT;
            }
            advertencia(A_MIEMBRO_DESCONOCIDO, nombre, "'" + n + "' no es un campo predefinido de '" + receptor + "'.");
            return Tipo.DESCONOCIDO;
        }
        if (receptor.esPrimitivo()) {
            error(E_MIEMBRO, nombre, "El tipo '" + receptor + "' no tiene campos (se intentó acceder a '" + n + "').");
            return Tipo.DESCONOCIDO;
        }
        ClaseInfo c = buscarClase(receptor.getBase());
        if (c == null || c.externa) {
            return Tipo.DESCONOCIDO;
        }
        Simbolo campo = c.campos.get(n);
        if (campo == null) {
            error(E_MIEMBRO, nombre, "La clase '" + c.nombre + "' no tiene un atributo llamado '" + n + "'.");
            return Tipo.DESCONOCIDO;
        }
        return campo.tipo;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ÁRBOL SEMÁNTICO: el programa decorado con tipos, ámbitos y errores
    // ─────────────────────────────────────────────────────────────────────────
    private Firma firmaSem;

    private static NodoSemantico semDe(Val v) {
        if (v.sem != null) {
            return v.sem;
        }
        return new NodoSemantico(Categoria.OPERANDO, "?", v.tipo.toString(), null, v.pos);
    }

    private static String textoDe(NodoAst n) {
        StringBuilder sb = new StringBuilder();
        textoDe(n, sb);
        return sb.toString();
    }

    private static void textoDe(NodoAst n, StringBuilder sb) {
        if (n.getToken() != null) {
            sb.append(n.getToken().getLexema());
            TipoComponente t = n.getToken().getTipo();
            if (t == TipoComponente.NEW || t == TipoComponente.COMA) {
                sb.append(' ');
            }
            return;
        }
        for (NodoAst h : n.getHijos()) {
            textoDe(h, sb);
        }
    }

    /** Operando de una expresión (un "Termino"): su texto, su tipo y sus subexpresiones. */
    private NodoSemantico nodoOperando(NodoAst n, Tipo t) {
        NodoSemantico s = new NodoSemantico(Categoria.OPERANDO, textoDe(n), t.toString(), clasificarOperando(n),
                primerToken(n));
        agregarSubexpresiones(n, s);
        return s;
    }

    private void agregarSubexpresiones(NodoAst n, NodoSemantico destino) {
        for (NodoAst h : n.getHijos()) {
            if (h.getToken() != null) {
                continue;
            }
            if (esNodo(h, "Expresion")) {
                destino.agregar(envolver("subexpresión", exprSem.get(h), h));
            } else if (esNodo(h, "LlamadaMetodo")) {
                int k = 1;
                for (NodoAst a : h.getHijos()) {
                    if (esNodo(a, "Expresion")) {
                        destino.agregar(envolver("argumento " + k++, exprSem.get(a), a));
                    }
                }
            } else {
                agregarSubexpresiones(h, destino);
            }
        }
    }

    private String clasificarOperando(NodoAst n) {
        List<NodoAst> hs = n.getHijos();
        if (hs.isEmpty() || hs.get(0).getToken() == null) {
            return null;
        }
        ComponenteLexico t0 = hs.get(0).getToken();
        switch (t0.getTipo()) {
            case NUMERO_ENTERO:
            case NUMERO_FLOTANTE:
            case LITERAL_CADENA:
            case LITERAL_CARACTER:
                return hs.size() == 1 ? "literal" : null;
            case NEW:
                return "creación (new)";
            case THIS:
                return "objeto actual (this)";
            case PARENTESIS_ABRE:
                return "expresión entre paréntesis";
            case IDENTIFICADOR:
                if (hs.size() == 1) {
                    String nombre = t0.getLexema();
                    for (Map<String, Simbolo> amb : ambitos) {
                        if (amb.containsKey(nombre)) {
                            return "variable";
                        }
                    }
                    if (claseActual != null && claseActual.campos.containsKey(nombre)) {
                        return "atributo de " + claseActual.nombre;
                    }
                    if (ctx.variables.containsKey(nombre)) {
                        return "atributo";
                    }
                    if (nombre.equals("true") || nombre.equals("false")) {
                        return "literal booleano";
                    }
                    return esNombreDeClase(nombre) ? "clase" : null;
                }
                for (NodoAst h : hs) {
                    if (esTok(h, TipoComponente.INCREMENTO)) {
                        return "incremento";
                    }
                    if (esTok(h, TipoComponente.DECREMENTO)) {
                        return "decremento";
                    }
                }
                for (NodoAst h : hs) {
                    if (esNodo(h, "LlamadaMetodo")) {
                        return "llamada a método";
                    }
                }
                return "acceso a miembro";
            default:
                return null;
        }
    }

    /** Presenta una expresión con el rol que cumple (condición, argumento, valor...). */
    private static NodoSemantico envolver(String rol, NodoSemantico sem, NodoAst origen) {
        ComponenteLexico pos = primerToken(origen);
        if (sem == null) {
            return new NodoSemantico(Categoria.EXPRESION, rol, null, null, pos);
        }
        if (sem.getHijos().isEmpty()) {
            return new NodoSemantico(Categoria.EXPRESION, rol + ": " + sem.getEtiqueta(), sem.getTipo(),
                    sem.getNota(), pos);
        }
        return new NodoSemantico(Categoria.EXPRESION, rol, sem.getTipo(), null, pos).agregar(sem);
    }

    private NodoSemantico construirArbol(String archivo, NodoAst raiz, List<ErrorSemantico> hallazgos,
            List<String[]> simbolos) {
        firmaSem = null;
        NodoSemantico r = new NodoSemantico(Categoria.PROGRAMA, "Programa", null, archivo, primerToken(raiz));
        NodoAst base = raiz;
        List<NodoAst> internos = new ArrayList<>();
        for (NodoAst h : raiz.getHijos()) {
            if (h.getToken() == null) {
                internos.add(h);
            }
        }
        if (internos.size() == 1 && esNodo(internos.get(0), "Programa")) {
            base = internos.get(0); // evita un "Programa" dentro de otro "Programa"
        }
        semPrograma(base, r);
        adjuntarHallazgos(r, hallazgos);
        if (!simbolos.isEmpty()) {
            NodoSemantico tabla = new NodoSemantico(Categoria.SIMBOLOS, "Tabla de símbolos",
                    String.valueOf(simbolos.size()), null, -1, -1);
            for (String[] f : simbolos) {
                int linea = -1;
                try {
                    linea = Integer.parseInt(f[4]);
                } catch (NumberFormatException ex) {
                    // sin línea
                }
                tabla.agregar(new NodoSemantico(Categoria.SIMBOLO, f[1], f[3], f[0] + " · " + f[2]
                        + (linea > 0 ? " · línea " + linea : ""), linea, 1));
            }
            r.agregar(tabla);
        }
        return r;
    }

    private void semPrograma(NodoAst nodo, NodoSemantico destino) {
        for (NodoAst h : nodo.getHijos()) {
            if (h.getToken() != null) {
                continue;
            }
            switch (h.getNombre()) {
                case "Programa": {
                    NodoSemantico sub = new NodoSemantico(Categoria.PROGRAMA, "Programa", null, null, primerToken(h));
                    semPrograma(h, sub);
                    destino.agregar(sub);
                    break;
                }
                case "Bloque":
                    semPrograma(h, destino);
                    break;
                case "Import":
                    destino.agregar(semImport(h));
                    break;
                case "Clase":
                    destino.agregar(semClase(h));
                    break;
                case "Atributo":
                    claseActual = null;
                    destino.agregar(semAtributo(h));
                    break;
                case "Metodo":
                case "Constructor":
                    claseActual = null;
                    destino.agregar(semMetodo(h));
                    break;
                default:
                    break;
            }
        }
    }

    private NodoSemantico semImport(NodoAst h) {
        StringBuilder sb = new StringBuilder();
        for (NodoAst x : h.getHijos()) {
            if (x.getToken() != null && (x.getToken().getTipo() == TipoComponente.IDENTIFICADOR
                    || x.getToken().getTipo() == TipoComponente.PUNTO)) {
                sb.append(x.getToken().getLexema());
            }
        }
        String txt = sb.toString();
        String nota;
        if (txt.contains(".")) {
            nota = "clase externa (no se verifica)";
        } else {
            ClaseInfo c = clases.get(txt);
            nota = c != null && !c.externa ? "resuelto: clase declarada en los archivos cargados"
                    : "no se encontró la clase";
        }
        return new NodoSemantico(Categoria.IMPORT, "import " + txt, null, nota, primerToken(h));
    }

    private NodoSemantico semClase(NodoAst nodo) {
        ClaseInfo info = infoPorNodo.get(nodo);
        ComponenteLexico nombre = null;
        for (NodoAst h : nodo.getHijos()) {
            if (esTok(h, TipoComponente.IDENTIFICADOR)) {
                nombre = h.getToken();
                break;
            }
        }
        NodoSemantico c = new NodoSemantico(Categoria.CLASE, "Clase " + (nombre != null ? nombre.getLexema() : "?"),
                nombre != null ? nombre.getLexema() : null, null, nombre != null ? nombre : primerToken(nodo));
        claseActual = info;
        int atributos = 0;
        int metodos = 0;
        for (NodoAst h : nodo.getHijos()) {
            if (esNodo(h, "Atributo")) {
                c.agregar(semAtributo(h));
                atributos++;
            } else if (esNodo(h, "Metodo") || esNodo(h, "Constructor")) {
                c.agregar(semMetodo(h));
                metodos++;
            }
        }
        claseActual = null;
        return new NodoSemantico(Categoria.CLASE, c.getEtiqueta(), c.getTipo(),
                atributos + (atributos == 1 ? " atributo, " : " atributos, ") + metodos
                        + (metodos == 1 ? " método" : " métodos"), c.getLinea(), c.getColumna())
                .agregarTodos(c.getHijos());
    }

    private NodoSemantico semAtributo(NodoAst h) {
        List<NodoAst> hs = h.getHijos();
        if (hs.isEmpty()) {
            return new NodoSemantico(Categoria.ATRIBUTO, "Atributo", null, null, primerToken(h));
        }
        Tipo t = leerTipo(hs.get(0));
        ComponenteLexico id = null;
        int extra = 0;
        int asig = -1;
        for (int i = 1; i < hs.size(); i++) {
            if (id == null && esTok(hs.get(i), TipoComponente.IDENTIFICADOR)) {
                id = hs.get(i).getToken();
            } else if (esTok(hs.get(i), TipoComponente.CORCHETE_ABRE)) {
                extra++;
            } else if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                asig = i;
                break;
            }
        }
        NodoSemantico a = new NodoSemantico(Categoria.ATRIBUTO, "Atributo " + (id != null ? id.getLexema() : "?"),
                t.arreglo(extra).toString(), "atributo de clase", id != null ? id : primerToken(h));
        if (asig >= 0 && asig + 1 < hs.size() && esNodo(hs.get(asig + 1), "Expresion")) {
            a.agregar(envolver("valor inicial", exprSem.get(hs.get(asig + 1)), hs.get(asig + 1)));
        }
        return a;
    }

    private NodoSemantico semMetodo(NodoAst nodo) {
        Firma f = leerFirma(nodo);
        if (f.nombre == null) {
            return new NodoSemantico(Categoria.METODO, "Método", null, null, primerToken(nodo));
        }
        MetodoInfo info = new MetodoInfo(f.nombre, f.retorno, f.tiposParam, f.constructor);
        NodoSemantico m = new NodoSemantico(Categoria.METODO,
                (f.constructor ? "Constructor " : "Método ") + info.firma(),
                f.constructor ? null : f.retorno.toString(), f.constructor ? "constructor" : null, f.tokNombre);
        if (!f.tiposParam.isEmpty()) {
            NodoSemantico ps = new NodoSemantico(Categoria.PARAMETROS, "Parámetros", null, null, f.tokParam.get(0));
            for (int i = 0; i < f.tiposParam.size(); i++) {
                ComponenteLexico pid = f.tokParam.get(i);
                ps.agregar(new NodoSemantico(Categoria.PARAMETRO, pid != null ? pid.getLexema() : "?",
                        f.tiposParam.get(i).toString(), "parámetro", pid));
            }
            m.agregar(ps);
        }
        firmaSem = f;
        if (f.bloque != null) {
            m.agregar(semBloque(f.bloque, "Cuerpo"));
        }
        firmaSem = null;
        return m;
    }

    private NodoSemantico semBloque(NodoAst bloque, String titulo) {
        List<String> declaradas = new ArrayList<>();
        for (NodoAst s : bloque.getHijos()) {
            if (esNodo(s, "Sentencia")) {
                for (NodoAst h : s.getHijos()) {
                    if (esNodo(h, "DeclaracionLocal")) {
                        Object[] d = leerDeclaracion(h);
                        if (d[1] != null) {
                            declaradas.add(((ComponenteLexico) d[1]).getLexema() + " : " + d[0]);
                        }
                    }
                }
            }
        }
        NodoSemantico b = new NodoSemantico(Categoria.AMBITO, titulo, null,
                declaradas.isEmpty() ? "ámbito sin variables propias" : "ámbito: " + String.join(", ", declaradas),
                primerToken(bloque));
        for (NodoAst s : bloque.getHijos()) {
            if (s.getToken() != null) {
                continue;
            }
            b.agregar(semSentencia(s));
        }
        return b;
    }

    /** @return {Tipo completo, token del nombre, nodo de la expresión inicial o null} */
    private Object[] leerDeclaracion(NodoAst d) {
        List<NodoAst> hs = d.getHijos();
        if (hs.isEmpty()) {
            return new Object[] { Tipo.DESCONOCIDO, null, null };
        }
        Tipo t = leerTipo(hs.get(0));
        ComponenteLexico id = null;
        int extra = 0;
        NodoAst valor = null;
        for (int i = 1; i < hs.size(); i++) {
            if (id == null && esTok(hs.get(i), TipoComponente.IDENTIFICADOR)) {
                id = hs.get(i).getToken();
            } else if (esTok(hs.get(i), TipoComponente.CORCHETE_ABRE)) {
                extra++;
            } else if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                if (i + 1 < hs.size() && esNodo(hs.get(i + 1), "Expresion")) {
                    valor = hs.get(i + 1);
                }
                break;
            }
        }
        return new Object[] { t.arreglo(extra), id, valor };
    }

    private NodoSemantico semDeclaracion(NodoAst d) {
        Object[] x = leerDeclaracion(d);
        ComponenteLexico id = (ComponenteLexico) x[1];
        NodoSemantico n = new NodoSemantico(Categoria.DECLARACION, "Declaración " + (id != null ? id.getLexema() : "?"),
                x[0].toString(), "variable local", id != null ? id : primerToken(d));
        if (x[2] != null) {
            n.agregar(envolver("valor inicial", exprSem.get((NodoAst) x[2]), (NodoAst) x[2]));
        }
        return n;
    }

    private NodoSemantico semAsignacion(NodoAst a) {
        List<NodoAst> hs = a.getHijos();
        int asig = -1;
        for (int i = 0; i < hs.size(); i++) {
            if (esTok(hs.get(i), TipoComponente.ASIGNACION)) {
                asig = i;
                break;
            }
        }
        Tipo[] tt = tiposAsig.get(a);
        Tipo destino = tt != null ? tt[0] : Tipo.DESCONOCIDO;
        Tipo valor = tt != null ? tt[1] : Tipo.DESCONOCIDO;
        String nota = "asignación";
        if (!destino.esDesconocido() && !valor.esDesconocido() && !destino.equals(valor)
                && compatible(destino, valor)) {
            nota = "conversión implícita " + valor + " → " + destino;
        }
        NodoSemantico n = new NodoSemantico(Categoria.ASIGNACION, "=", destino.toString(), nota,
                asig >= 0 ? hs.get(asig).getToken() : primerToken(a));
        if (asig >= 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < asig; i++) {
                textoDe(hs.get(i), sb);
            }
            n.agregar(new NodoSemantico(Categoria.OPERANDO, sb.toString(), destino.toString(), "destino",
                    primerToken(hs.get(0))));
            if (asig + 1 < hs.size() && esNodo(hs.get(asig + 1), "Expresion")) {
                NodoSemantico v = exprSem.get(hs.get(asig + 1));
                n.agregar(v != null ? v : envolver("valor", null, hs.get(asig + 1)));
            }
        }
        return n;
    }

    private NodoSemantico semSentencia(NodoAst s) {
        switch (s.getNombre()) {
            case "Sentencia": {
                List<NodoSemantico> partes = new ArrayList<>();
                for (NodoAst h : s.getHijos()) {
                    if (esNodo(h, "DeclaracionLocal")) {
                        partes.add(semDeclaracion(h));
                    } else if (esNodo(h, "Asignacion")) {
                        partes.add(semAsignacion(h));
                    } else if (esNodo(h, "Expresion")) {
                        NodoSemantico e = exprSem.get(h);
                        partes.add(new NodoSemantico(Categoria.SENTENCIA, "Expresión", e != null ? e.getTipo() : null,
                                null, primerToken(h)).agregar(e));
                    }
                }
                if (partes.size() == 1) {
                    return partes.get(0);
                }
                NodoSemantico g = new NodoSemantico(Categoria.SENTENCIA, "Sentencia", null, null, primerToken(s));
                return g.agregarTodos(partes);
            }
            case "If":
                return semIf(s, "if");
            case "While":
                return semCondicionYBloque(s, "while");
            case "Exist":
                return semCondicionYBloque(s, "exist");
            case "Repeat":
                return semRepeat(s);
            case "For":
                return semFor(s);
            case "Return":
                return semReturn(s);
            default:
                return null;
        }
    }

    private NodoSemantico semIf(NodoAst s, String nombre) {
        NodoSemantico n = new NodoSemantico(Categoria.CONTROL, nombre, null, null, primerToken(s));
        boolean enElse = false;
        for (NodoAst h : s.getHijos()) {
            if (esTok(h, TipoComponente.SINO)) {
                enElse = true;
            } else if (esNodo(h, "Expresion") && !enElse) {
                n.agregar(envolver("condición", exprSem.get(h), h));
            } else if (esNodo(h, "Bloque")) {
                n.agregar(semBloque(h, enElse ? "sino" : "entonces"));
            } else if (esNodo(h, "If")) {
                n.agregar(semIf(h, "sino si"));
            }
        }
        return n;
    }

    private NodoSemantico semCondicionYBloque(NodoAst s, String nombre) {
        NodoSemantico n = new NodoSemantico(Categoria.CONTROL, nombre, null, null, primerToken(s));
        for (NodoAst h : s.getHijos()) {
            if (esNodo(h, "Expresion")) {
                n.agregar(envolver(nombre.equals("exist") ? "expresión" : "condición", exprSem.get(h), h));
            } else if (esNodo(h, "Bloque")) {
                n.agregar(semBloque(h, "cuerpo"));
            }
        }
        return n;
    }

    private NodoSemantico semRepeat(NodoAst s) {
        boolean esEach = false;
        boolean esWhile = false;
        for (NodoAst h : s.getHijos()) {
            if (esTok(h, TipoComponente.CADA)) {
                esEach = true;
            } else if (esTok(h, TipoComponente.MIENTRAS)) {
                esWhile = true;
            }
        }
        NodoSemantico n = new NodoSemantico(Categoria.CONTROL,
                esEach ? "repeat each" : esWhile ? "repeat while" : "repeat", null, null, primerToken(s));
        for (NodoAst h : s.getHijos()) {
            if (esNodo(h, "DeclaracionLocal")) {
                n.agregar(semDeclaracion(h));
            } else if (esNodo(h, "Asignacion")) {
                n.agregar(semAsignacion(h));
            } else if (esNodo(h, "Expresion")) {
                n.agregar(envolver(esEach ? "expresión" : "condición", exprSem.get(h), h));
            } else if (esNodo(h, "Bloque")) {
                n.agregar(semBloque(h, "cuerpo"));
            }
        }
        return n;
    }

    private NodoSemantico semFor(NodoAst s) {
        NodoSemantico n = new NodoSemantico(Categoria.CONTROL, "for", null, null, primerToken(s));
        int parte = 0;
        boolean dentro = false;
        String[] roles = { "inicio", "condición", "actualización" };
        for (NodoAst h : s.getHijos()) {
            if (esTok(h, TipoComponente.PARENTESIS_ABRE) && !dentro) {
                dentro = true;
            } else if (esTok(h, TipoComponente.PUNTO_Y_COMA)) {
                parte++;
            } else if (esNodo(h, "Bloque")) {
                n.agregar(semBloque(h, "cuerpo"));
            } else if (esNodo(h, "DeclaracionLocal")) {
                n.agregar(semDeclaracion(h));
            } else if (esNodo(h, "Asignacion")) {
                n.agregar(semAsignacion(h));
            } else if (esNodo(h, "Expresion")) {
                n.agregar(envolver(roles[Math.min(parte, 2)], exprSem.get(h), h));
            }
        }
        return n;
    }

    private NodoSemantico semReturn(NodoAst s) {
        NodoAst expr = null;
        for (NodoAst h : s.getHijos()) {
            if (esNodo(h, "Expresion")) {
                expr = h;
            }
        }
        NodoSemantico valor = expr != null ? exprSem.get(expr) : null;
        String esperado = firmaSem != null ? "el método espera " + firmaSem.retorno : null;
        NodoSemantico n = new NodoSemantico(Categoria.RETORNO, "return", valor != null ? valor.getTipo()
                : expr != null ? null : "void", esperado, primerToken(s));
        if (valor != null) {
            n.agregar(valor);
        }
        return n;
    }

    // ── Errores y avisos colgados del nodo donde ocurren ─────────────────────
    private static void recolectar(NodoSemantico n, List<NodoSemantico> destino) {
        destino.add(n);
        for (NodoSemantico h : n.getHijos()) {
            recolectar(h, destino);
        }
    }

    private void adjuntarHallazgos(NodoSemantico raiz, List<ErrorSemantico> hallazgos) {
        List<NodoSemantico> todos = new ArrayList<>();
        recolectar(raiz, todos);
        List<ErrorSemantico> orden = new ArrayList<>(hallazgos);
        orden.sort(java.util.Comparator.comparingInt(ErrorSemantico::getLinea)
                .thenComparingInt(ErrorSemantico::getColumna));
        for (ErrorSemantico h : orden) {
            NodoSemantico objetivo = elegirNodo(todos, h, raiz);
            objetivo.agregar(new NodoSemantico(h.esError() ? Categoria.ERROR : Categoria.AVISO, h.getMensaje(), null,
                    h.getLinea() >= 1 ? h.getLinea() + ":" + h.getColumna() : null, h.getLinea(), h.getColumna()));
            objetivo.marcar(h.esError());
        }
    }

    private static NodoSemantico elegirNodo(List<NodoSemantico> todos, ErrorSemantico h, NodoSemantico raiz) {
        if (h.getLinea() < 1) {
            return raiz;
        }
        NodoSemantico exacto = null;
        NodoSemantico delaLinea = null;
        for (NodoSemantico n : todos) {
            if (n.getLinea() != h.getLinea()) {
                continue;
            }
            if (n.getColumna() == h.getColumna()) {
                exacto = n; // el último en preorden es el más profundo
            }
            if (delaLinea == null) {
                switch (n.getCategoria()) {
                    case DECLARACION: case ASIGNACION: case CONTROL: case RETORNO: case SENTENCIA:
                    case METODO: case ATRIBUTO: case CLASE: case IMPORT:
                        delaLinea = n;
                        break;
                    default:
                        break;
                }
            }
        }
        if (exacto != null) {
            return exacto;
        }
        return delaLinea != null ? delaLinea : raiz;
    }
}
