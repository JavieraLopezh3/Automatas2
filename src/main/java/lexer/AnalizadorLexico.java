package lexer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tokens.ComponenteLexico;
import tokens.TipoComponente;

public final class AnalizadorLexico {
    private static final int ERROR_CARACTER_INVALIDO = 500;
    private static final int ERROR_CADENA_NO_CERRADA = 501;
    private static final int ERROR_NUMERO_MAL_FORMADO = 502;
    private static final int ERROR_OPERADOR_INVALIDO = 503;
    private static final int ERROR_CARACTER_INVALIDO_LITERAL = 504;
    private static final int ERROR_COMENTARIO_SIN_CIERRE = 505;
    private static final int ERROR_ASCII_FUERA_DE_RANGO = 506;
    private static final int ERROR_CIERRE_INESPERADO = 507;
    private static final int ERROR_DELIMITADOR_SIN_CIERRE = 508;
    private static final int ERROR_ESTRUCTURA_INVALIDA = 509;
    private static final int ERROR_TOKEN_PEGADO = 510;
    private static final int ERROR_FALTA_PUNTO_Y_COMA = 511;

    private static final Map<String, TipoComponente> PALABRAS_RESERVADAS = construirPalabrasReservadas();

    // -------------------------------------------------------------------------
    // AFD - Tabla de transiciones
    // -------------------------------------------------------------------------
    // Estados del automata
    private static final int EST_INICIO        =  0;  // estado inicial

    // Estados intermedios (no aceptadores) para operadores de dos caracteres
    private static final int EST_IGUAL         =  1;  // leyo '='
    private static final int EST_EXCLAMACION   =  2;  // leyo '!'
    private static final int EST_MENOR         =  3;  // leyo '<'
    private static final int EST_MAYOR         =  4;  // leyo '>'
    private static final int EST_AMPERSAND     =  5;  // leyo '&'
    private static final int EST_PIPE          =  6;  // leyo '|'
    private static final int EST_MAS           =  7;  // leyo '+'
    private static final int EST_MENOS         =  8;  // leyo '-'
    private static final int EST_ASTERISCO     =  9;  // leyo '*'

    // Estados de aceptacion para tokens simples (un caracter)
    private static final int EST_AC_ASIGNACION      = 20;
    private static final int EST_AC_MODULO          = 21;
    private static final int EST_AC_LLAVE_ABRE      = 22;
    private static final int EST_AC_LLAVE_CIERRA    = 23;
    private static final int EST_AC_PAR_ABRE        = 24;
    private static final int EST_AC_PAR_CIERRA      = 25;
    private static final int EST_AC_COR_ABRE        = 26;
    private static final int EST_AC_COR_CIERRA      = 27;
    private static final int EST_AC_PUNTO_Y_COMA    = 28;
    private static final int EST_AC_COMA            = 29;
    private static final int EST_AC_PUNTO           = 30;
    private static final int EST_AC_DOS_PUNTOS      = 31;
    private static final int EST_AC_SUMA            = 32;
    private static final int EST_AC_RESTA           = 33;
    private static final int EST_AC_MULTIPLICACION  = 34;
    private static final int EST_AC_DIVISION        = 35;
    private static final int EST_AC_NEGACION        = 36;
    private static final int EST_AC_INCREMENTO      = 37;
    private static final int EST_AC_DECREMENTO      = 38;

    // Estados de aceptacion para tokens de dos caracteres
    private static final int EST_AC_IGUALDAD        = 40;
    private static final int EST_AC_DIFERENTE       = 41;
    private static final int EST_AC_MENOR_QUE       = 42;
    private static final int EST_AC_MENOR_O_IGUAL   = 43;
    private static final int EST_AC_MAYOR_QUE       = 44;
    private static final int EST_AC_MAYOR_O_IGUAL   = 45;
    private static final int EST_AC_Y_LOGICO        = 46;
    private static final int EST_AC_O_LOGICO        = 47;

    // Estados de error del AFD
    private static final int EST_ERR_OP_INVALIDO    = 60;  // operador invalido (!, &, |, ++, --, **)
    private static final int EST_ERR_ASCII          = 61;  // caracter fuera de ASCII
    private static final int EST_ERR_INVALIDO       = 62;  // caracter no reconocido

    // Estados especiales: delegan a metodos de escaneo complejos
    private static final int EST_DELEG_IDENTIFICADOR = 70;
    private static final int EST_DELEG_NUMERO        = 71;
    private static final int EST_DELEG_CADENA        = 72;
    private static final int EST_DELEG_CARACTER      = 73;
    private static final int EST_DELEG_DIAGONAL      = 74;  // '/' -> puede ser DIV, // o /* */

    // Numero total de estados
    private static final int NUM_ESTADOS = 80;
    // Numero de columnas: 128 caracteres ASCII + 1 columna extra para >127
    private static final int NUM_COLS    = 129;
    // Indice de la columna para caracteres fuera de ASCII (>127)
    private static final int COL_FUERA_ASCII = 128;

    /**
     * Tabla de transiciones del AFD.
     * TABLA[estado][columna] = siguiente estado
     * columna = codigo ASCII del caracter (0-127), o COL_FUERA_ASCII para >127
     */
    private static final int[][] TABLA = construirTablaTransiciones();

    /**
     * Mapa de estado de aceptacion -> TipoComponente que produce.
     * Solo los estados EST_AC_* tienen entrada aqui.
     */
    private static final Map<Integer, TipoComponente> TIPO_POR_ESTADO = construirTipoPorEstado();

    private static int[][] construirTablaTransiciones() {
        int[][] t = new int[NUM_ESTADOS][NUM_COLS];

        // Por defecto todo va a error de caracter invalido
        for (int[] fila : t) {
            java.util.Arrays.fill(fila, EST_ERR_INVALIDO);
        }

        // Columna especial para caracteres fuera de ASCII
        for (int e = 0; e < NUM_ESTADOS; e++) {
            t[e][COL_FUERA_ASCII] = EST_ERR_ASCII;
        }

        // --- Desde EST_INICIO ---
        // Espacios en blanco y saltos de linea: se manejan antes de consultar la tabla
        // Letras y '_' -> identificador/palabra reservada
        for (char c = 'a'; c <= 'z'; c++) t[EST_INICIO][c] = EST_DELEG_IDENTIFICADOR;
        for (char c = 'A'; c <= 'Z'; c++) t[EST_INICIO][c] = EST_DELEG_IDENTIFICADOR;
        t[EST_INICIO]['_'] = EST_DELEG_IDENTIFICADOR;

        // Digitos -> numero
        for (char c = '0'; c <= '9'; c++) t[EST_INICIO][c] = EST_DELEG_NUMERO;

        // Cadena y caracter literal
        t[EST_INICIO]['"']  = EST_DELEG_CADENA;
        t[EST_INICIO]['\''] = EST_DELEG_CARACTER;

        // Diagonal -> puede ser /, // o /* */
        t[EST_INICIO]['/']  = EST_DELEG_DIAGONAL;

        // Operadores que pueden ser de uno o dos caracteres
        t[EST_INICIO]['=']  = EST_IGUAL;
        t[EST_INICIO]['!']  = EST_EXCLAMACION;
        t[EST_INICIO]['<']  = EST_MENOR;
        t[EST_INICIO]['>']  = EST_MAYOR;
        t[EST_INICIO]['&']  = EST_AMPERSAND;
        t[EST_INICIO]['|']  = EST_PIPE;
        t[EST_INICIO]['+']  = EST_MAS;
        t[EST_INICIO]['-']  = EST_MENOS;
        t[EST_INICIO]['*']  = EST_ASTERISCO;

        // Operadores y delimitadores de un solo caracter
        t[EST_INICIO]['%']  = EST_AC_MODULO;
        t[EST_INICIO]['{']  = EST_AC_LLAVE_ABRE;
        t[EST_INICIO]['}']  = EST_AC_LLAVE_CIERRA;
        t[EST_INICIO]['(']  = EST_AC_PAR_ABRE;
        t[EST_INICIO][')']  = EST_AC_PAR_CIERRA;
        t[EST_INICIO]['[']  = EST_AC_COR_ABRE;
        t[EST_INICIO][']']  = EST_AC_COR_CIERRA;
        t[EST_INICIO][';']  = EST_AC_PUNTO_Y_COMA;
        t[EST_INICIO][',']  = EST_AC_COMA;
        t[EST_INICIO]['.']  = EST_AC_PUNTO;
        t[EST_INICIO][':']  = EST_AC_DOS_PUNTOS;

        // --- Desde EST_IGUAL ('=') ---
        // Si el siguiente es '=' -> IGUALDAD, cualquier otra cosa -> ASIGNACION (retroceder)
        java.util.Arrays.fill(t[EST_IGUAL], EST_AC_ASIGNACION);
        t[EST_IGUAL]['=']            = EST_AC_IGUALDAD;
        t[EST_IGUAL][COL_FUERA_ASCII] = EST_AC_ASIGNACION;

        // --- Desde EST_EXCLAMACION ('!') ---
        // '!=' es un operador valido; '!' tambien es valido como negacion logica
        java.util.Arrays.fill(t[EST_EXCLAMACION], EST_AC_NEGACION);
        t[EST_EXCLAMACION]['=']            = EST_AC_DIFERENTE;
        t[EST_EXCLAMACION][COL_FUERA_ASCII] = EST_AC_NEGACION;

        // --- Desde EST_MENOR ('<') ---
        java.util.Arrays.fill(t[EST_MENOR], EST_AC_MENOR_QUE);
        t[EST_MENOR]['=']            = EST_AC_MENOR_O_IGUAL;
        t[EST_MENOR][COL_FUERA_ASCII] = EST_AC_MENOR_QUE;

        // --- Desde EST_MAYOR ('>') ---
        java.util.Arrays.fill(t[EST_MAYOR], EST_AC_MAYOR_QUE);
        t[EST_MAYOR]['=']            = EST_AC_MAYOR_O_IGUAL;
        t[EST_MAYOR][COL_FUERA_ASCII] = EST_AC_MAYOR_QUE;

        // --- Desde EST_AMPERSAND ('&') ---
        // Solo '&&' es valido
        java.util.Arrays.fill(t[EST_AMPERSAND], EST_ERR_OP_INVALIDO);
        t[EST_AMPERSAND]['&']            = EST_AC_Y_LOGICO;
        t[EST_AMPERSAND][COL_FUERA_ASCII] = EST_ERR_OP_INVALIDO;

        // --- Desde EST_PIPE ('|') ---
        // Solo '||' es valido
        java.util.Arrays.fill(t[EST_PIPE], EST_ERR_OP_INVALIDO);
        t[EST_PIPE]['|']            = EST_AC_O_LOGICO;
        t[EST_PIPE][COL_FUERA_ASCII] = EST_ERR_OP_INVALIDO;

        // --- Desde EST_MAS ('+') ---
        // '+' simple es valido; '++' es incremento
        java.util.Arrays.fill(t[EST_MAS], EST_AC_SUMA);
        t[EST_MAS]['+']            = EST_AC_INCREMENTO;
        t[EST_MAS][COL_FUERA_ASCII] = EST_AC_SUMA;

        // --- Desde EST_MENOS ('-') ---
        // '-' simple es valido; '--' es decremento
        java.util.Arrays.fill(t[EST_MENOS], EST_AC_RESTA);
        t[EST_MENOS]['-']            = EST_AC_DECREMENTO;
        t[EST_MENOS][COL_FUERA_ASCII] = EST_AC_RESTA;

        // --- Desde EST_ASTERISCO ('*') ---
        // '*' simple es valido; '**' es error
        java.util.Arrays.fill(t[EST_ASTERISCO], EST_AC_MULTIPLICACION);
        t[EST_ASTERISCO]['*']            = EST_ERR_OP_INVALIDO;
        t[EST_ASTERISCO][COL_FUERA_ASCII] = EST_AC_MULTIPLICACION;

        return t;
    }

    private static Map<Integer, TipoComponente> construirTipoPorEstado() {
        Map<Integer, TipoComponente> m = new HashMap<>();
        m.put(EST_AC_ASIGNACION,     TipoComponente.ASIGNACION);
        m.put(EST_AC_MODULO,         TipoComponente.MODULO);
        m.put(EST_AC_LLAVE_ABRE,     TipoComponente.LLAVE_ABRE);
        m.put(EST_AC_LLAVE_CIERRA,   TipoComponente.LLAVE_CIERRA);
        m.put(EST_AC_PAR_ABRE,       TipoComponente.PARENTESIS_ABRE);
        m.put(EST_AC_PAR_CIERRA,     TipoComponente.PARENTESIS_CIERRA);
        m.put(EST_AC_COR_ABRE,       TipoComponente.CORCHETE_ABRE);
        m.put(EST_AC_COR_CIERRA,     TipoComponente.CORCHETE_CIERRA);
        m.put(EST_AC_PUNTO_Y_COMA,   TipoComponente.PUNTO_Y_COMA);
        m.put(EST_AC_COMA,           TipoComponente.COMA);
        m.put(EST_AC_PUNTO,          TipoComponente.PUNTO);
        m.put(EST_AC_DOS_PUNTOS,     TipoComponente.DOS_PUNTOS);
        m.put(EST_AC_SUMA,           TipoComponente.SUMA);
        m.put(EST_AC_RESTA,          TipoComponente.RESTA);
        m.put(EST_AC_MULTIPLICACION, TipoComponente.MULTIPLICACION);
        m.put(EST_AC_DIVISION,       TipoComponente.DIVISION);
        m.put(EST_AC_NEGACION,       TipoComponente.NEGACION);
        m.put(EST_AC_INCREMENTO,     TipoComponente.INCREMENTO);
        m.put(EST_AC_DECREMENTO,     TipoComponente.DECREMENTO);
        m.put(EST_AC_IGUALDAD,       TipoComponente.IGUALDAD);
        m.put(EST_AC_DIFERENTE,      TipoComponente.DIFERENTE);
        m.put(EST_AC_MENOR_QUE,      TipoComponente.MENOR_QUE);
        m.put(EST_AC_MENOR_O_IGUAL,  TipoComponente.MENOR_O_IGUAL);
        m.put(EST_AC_MAYOR_QUE,      TipoComponente.MAYOR_QUE);
        m.put(EST_AC_MAYOR_O_IGUAL,  TipoComponente.MAYOR_O_IGUAL);
        m.put(EST_AC_Y_LOGICO,       TipoComponente.Y_LOGICO);
        m.put(EST_AC_O_LOGICO,       TipoComponente.O_LOGICO);
        return m;
    }

    public ResultadoAnalisisLexico analizar(String fuente) {
        String entrada = normalizarSaltosDeLinea(fuente);
        List<ComponenteLexico> componentes = new ArrayList<>();
        List<ErrorLexico> errores = new ArrayList<>();

        int indice = 0;
        int linea = 1;
        int columna = 1;

        while (indice < entrada.length()) {
            char actual = entrada.charAt(indice);

            // Salto de linea: no pasa por la tabla, se maneja directamente
            if (actual == '\n') {
                indice++;
                linea++;
                columna = 1;
                continue;
            }

            // Espacios en blanco: no pasan por la tabla
            if (actual == ' ' || actual == '\t' || actual == '\f') {
                indice++;
                columna++;
                continue;
            }

            // Si encontramos un punto seguido de digitos, es un numero flotante que inicia con punto.
            if (actual == '.' && indice + 1 < entrada.length() && Character.isDigit(entrada.charAt(indice + 1))) {
                EstadoEscaneo resultado = escanearNumero(entrada, indice, linea, columna);
                if (resultado.error != null) errores.add(resultado.error);
                else componentes.add(resultado.componente);
                indice = resultado.siguienteIndice;
                linea  = resultado.siguienteLinea;
                columna = resultado.siguienteColumna;
                continue;
            }

            // Consultar la tabla de transiciones del AFD.
            // La columna es el codigo ASCII del caracter, o COL_FUERA_ASCII si >127.
            int col = (actual <= 127) ? actual : COL_FUERA_ASCII;
            int estadoAFD = TABLA[EST_INICIO][col];

            // --- Estados de delegacion: escaneo complejo ---
            if (estadoAFD == EST_DELEG_IDENTIFICADOR) {
                int inicio = indice;
                int columnaInicio = columna;
                indice++;
                columna++;
                while (indice < entrada.length() && esLetraODigitoAsciiOGuionBajo(entrada.charAt(indice))) {
                    indice++;
                    columna++;
                }
                String lexema = entrada.substring(inicio, indice);
                TipoComponente tipo = PALABRAS_RESERVADAS.getOrDefault(lexema, TipoComponente.IDENTIFICADOR);
                componentes.add(new ComponenteLexico(tipo, lexema, linea, columnaInicio, false, inicio, indice));
                continue;
            }

            if (estadoAFD == EST_DELEG_NUMERO) {
                EstadoEscaneo resultado = escanearNumero(entrada, indice, linea, columna);
                if (resultado.error != null) errores.add(resultado.error);
                else componentes.add(resultado.componente);
                indice = resultado.siguienteIndice;
                linea  = resultado.siguienteLinea;
                columna = resultado.siguienteColumna;
                continue;
            }

            if (estadoAFD == EST_DELEG_CADENA) {
                EstadoEscaneo resultado = escanearCadena(entrada, indice, linea, columna);
                if (resultado.error != null) errores.add(resultado.error);
                else componentes.add(resultado.componente);
                indice = resultado.siguienteIndice;
                linea  = resultado.siguienteLinea;
                columna = resultado.siguienteColumna;
                continue;
            }

            if (estadoAFD == EST_DELEG_CARACTER) {
                EstadoEscaneo resultado = escanearLiteralCaracter(entrada, indice, linea, columna);
                if (resultado.error != null) errores.add(resultado.error);
                else componentes.add(resultado.componente);
                indice = resultado.siguienteIndice;
                linea  = resultado.siguienteLinea;
                columna = resultado.siguienteColumna;
                continue;
            }

            if (estadoAFD == EST_DELEG_DIAGONAL) {
                EstadoEscaneo resultado = escanearFamiliaDiagonal(entrada, indice, linea, columna);
                if (resultado.error != null) errores.add(resultado.error);
                else componentes.add(resultado.componente);
                indice = resultado.siguienteIndice;
                linea  = resultado.siguienteLinea;
                columna = resultado.siguienteColumna;
                continue;
            }

            // --- Estados intermedios del AFD: operadores de dos caracteres ---
            // El estado intermedio consume el primer caracter y mira el segundo
            // para decidir si acepta un token de dos caracteres o retrocede.
            if (estadoAFD >= EST_IGUAL && estadoAFD <= EST_ASTERISCO) {
                char siguiente = ojear(entrada, indice + 1);
                int colSig = (siguiente != '\0' && siguiente <= 127) ? siguiente : COL_FUERA_ASCII;
                int estadoFinal = TABLA[estadoAFD][colSig];

                // Tokens de dos caracteres: consumir ambos
                if (estadoFinal == EST_AC_IGUALDAD     || estadoFinal == EST_AC_DIFERENTE   ||
                    estadoFinal == EST_AC_MENOR_O_IGUAL || estadoFinal == EST_AC_MAYOR_O_IGUAL ||
                    estadoFinal == EST_AC_Y_LOGICO      || estadoFinal == EST_AC_O_LOGICO ||
                    estadoFinal == EST_AC_INCREMENTO   || estadoFinal == EST_AC_DECREMENTO) {
                    String lexema = entrada.substring(indice, indice + 2);
                    componentes.add(new ComponenteLexico(
                        TIPO_POR_ESTADO.get(estadoFinal), lexema, linea, columna, false, indice, indice + 2));
                    indice  += 2;
                    columna += 2;
                    continue;
                }

                // Error de operador invalido (!, &, |, ++, --, **)
                if (estadoFinal == EST_ERR_OP_INVALIDO) {
                    // Consumir todos los caracteres repetidos del mismo tipo
                    int inicio = indice;
                    int columnaInicio = columna;
                    while (indice < entrada.length() && entrada.charAt(indice) == actual) {
                        indice++;
                        columna++;
                    }
                    String lexema = entrada.substring(inicio, indice);
                    errores.add(new ErrorLexico(
                        ERROR_OPERADOR_INVALIDO,
                        lexema,
                        "Operador invalido o incompleto: '" + lexema + "' no pertenece al lenguaje.",
                        linea,
                        columnaInicio,
                        inicio,
                        indice
                    ));
                    continue;
                }

                // Token de un caracter (retroceso logico: el segundo caracter no se consume)
                TipoComponente tipo = TIPO_POR_ESTADO.get(estadoFinal);
                if (tipo != null) {
                    String lexema = String.valueOf(actual);
                    componentes.add(new ComponenteLexico(tipo, lexema, linea, columna, false, indice, indice + 1));
                    indice++;
                    columna++;
                    continue;
                }
            }

            // --- Estados de aceptacion directa (tokens de un caracter) ---
            TipoComponente tipoDirecto = TIPO_POR_ESTADO.get(estadoAFD);
            if (tipoDirecto != null) {
                componentes.add(new ComponenteLexico(
                    tipoDirecto, String.valueOf(actual), linea, columna, false, indice, indice + 1));
                indice++;
                columna++;
                continue;
            }

            // --- Estados de error ---
            if (estadoAFD == EST_ERR_ASCII) {
                componentes.add(new ComponenteLexico(
                    TipoComponente.COMENTARIO,
                    String.valueOf(actual),
                    linea, columna, true, indice, indice + 1
                ));
                indice++;
                columna++;
                continue;
            }

            // EST_ERR_INVALIDO u otro estado no reconocido
            errores.add(new ErrorLexico(
                ERROR_CARACTER_INVALIDO,
                String.valueOf(actual),
                "Caracter invalido: no pertenece al alfabeto del lenguaje.",
                linea, columna, indice, indice + 1
            ));
            indice++;
            columna++;
        }

        validarEstructuraBasica(entrada, componentes, errores);
        ordenarErrores(errores);
        return new ResultadoAnalisisLexico(componentes, errores);
    }

    private static EstadoEscaneo escanearNumero(String entrada, int indice, int linea, int columna) {
        int inicio = indice;
        int columnaInicio = columna;
        boolean tieneDigitosAntes = false;

        while (indice < entrada.length() && Character.isDigit(entrada.charAt(indice))) {
            indice++;
            columna++;
            tieneDigitosAntes = true;
        }

        if (indice < entrada.length() && entrada.charAt(indice) == '.') {
            indice++;
            columna++;

            if (indice >= entrada.length() || !Character.isDigit(entrada.charAt(indice))) {
                if (!tieneDigitosAntes) {
                    String lexema = entrada.substring(inicio, indice);
                    return EstadoEscaneo.conError(
                        new ErrorLexico(
                            ERROR_NUMERO_MAL_FORMADO,
                            lexema,
                            "Numero mal formado: se esperaba al menos un digito despues del punto decimal.",
                            linea,
                            columnaInicio,
                            inicio,
                            indice
                        ),
                        indice,
                        linea,
                        columna
                    );
                }
                String lexema = entrada.substring(inicio, indice - 1);
                return EstadoEscaneo.conComponente(
                    new ComponenteLexico(TipoComponente.NUMERO_ENTERO, lexema, linea, columnaInicio, false, inicio, indice - 1),
                    indice - 1,
                    linea,
                    columna - 1
                );
            }

            while (indice < entrada.length() && Character.isDigit(entrada.charAt(indice))) {
                indice++;
                columna++;
            }

            if (indice < entrada.length() && (esLetraAscii(entrada.charAt(indice)) || entrada.charAt(indice) == '_')) {
                while (indice < entrada.length() && esLetraODigitoAsciiOGuionBajo(entrada.charAt(indice))) {
                    indice++;
                    columna++;
                }

                String lexema = entrada.substring(inicio, indice);
                return EstadoEscaneo.conError(
                    new ErrorLexico(
                        ERROR_NUMERO_MAL_FORMADO,
                        lexema,
                        "Numero mal formado: un identificador no puede iniciar con digito.",
                        linea,
                        columnaInicio,
                        inicio,
                        indice
                    ),
                    indice,
                    linea,
                    columna
                );
            }

            String lexema = entrada.substring(inicio, indice);
            return EstadoEscaneo.conComponente(
                new ComponenteLexico(TipoComponente.NUMERO_FLOTANTE, lexema, linea, columnaInicio, false, inicio, indice),
                indice,
                linea,
                columna
            );
        }

        if (indice < entrada.length() && (esLetraAscii(entrada.charAt(indice)) || entrada.charAt(indice) == '_')) {
            while (indice < entrada.length() && esLetraODigitoAsciiOGuionBajo(entrada.charAt(indice))) {
                indice++;
                columna++;
            }

            String lexema = entrada.substring(inicio, indice);
            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_NUMERO_MAL_FORMADO,
                    lexema,
                    "Numero mal formado: un identificador no puede iniciar con digito.",
                    linea,
                    columnaInicio,
                    inicio,
                    indice
                ),
                indice,
                linea,
                columna
            );
        }

        String lexema = entrada.substring(inicio, indice);
        return EstadoEscaneo.conComponente(
            new ComponenteLexico(TipoComponente.NUMERO_ENTERO, lexema, linea, columnaInicio, false, inicio, indice),
            indice,
            linea,
            columna
        );
    }

    private static EstadoEscaneo escanearCadena(String entrada, int indice, int linea, int columna) {
        int inicio = indice;
        int columnaInicio = columna;

        indice++;
        columna++;

        while (indice < entrada.length()) {
            char actual = entrada.charAt(indice);

            if (actual > 127) {
                return EstadoEscaneo.conError(
                    new ErrorLexico(
                        ERROR_ASCII_FUERA_DE_RANGO,
                        String.valueOf(actual),
                        "Caracter fuera del rango ASCII permitido (0-127).",
                        linea,
                        columna,
                        indice,
                        indice + 1
                    ),
                    indice + 1,
                    linea,
                    columna + 1
                );
            }

            if (actual == '\n') {
                String lexema = entrada.substring(inicio, indice);
                return EstadoEscaneo.conError(
                    new ErrorLexico(
                        ERROR_CADENA_NO_CERRADA,
                        lexema,
                        "Cadena no cerrada: se encontro fin de linea antes de la comilla de cierre.",
                        linea,
                        columnaInicio,
                        inicio,
                        indice
                    ),
                    indice,
                    linea,
                    columna
                );
            }

            if (actual == '\\') {
                int columnaError = columna;
                int indiceError = indice;
                indice++;
                columna++;

                if (indice < entrada.length() && entrada.charAt(indice) != '\n') {
                    indice++;
                    columna++;
                }

                while (indice < entrada.length() && entrada.charAt(indice) != '\n') {
                    if (entrada.charAt(indice) == '"') {
                        indice++;
                        columna++;
                        break;
                    }
                    indice++;
                    columna++;
                }

                String lexema = entrada.substring(inicio, indice);
                return EstadoEscaneo.conError(
                    new ErrorLexico(
                        ERROR_CARACTER_INVALIDO,
                        lexema,
                        "Caracter escapado no permitido dentro de cadenas.",
                        linea,
                        columnaError,
                        indiceError,
                        indice
                    ),
                    indice,
                    linea,
                    columna
                );
            }

            if (actual == '"') {
                indice++;
                columna++;
                String lexema = entrada.substring(inicio, indice);
                return EstadoEscaneo.conComponente(
                    new ComponenteLexico(TipoComponente.LITERAL_CADENA, lexema, linea, columnaInicio, false, inicio, indice),
                    indice,
                    linea,
                    columna
                );
            }

            indice++;
            columna++;
        }

        String lexema = entrada.substring(inicio);
        return EstadoEscaneo.conError(
            new ErrorLexico(
                ERROR_CADENA_NO_CERRADA,
                lexema,
                "Cadena no cerrada: se llego al fin del archivo.",
                linea,
                columnaInicio,
                inicio,
                indice
            ),
            indice,
            linea,
            columna
        );
    }

    private static EstadoEscaneo escanearLiteralCaracter(String entrada, int indice, int linea, int columna) {
        int inicio = indice;
        int columnaInicio = columna;

        indice++;
        columna++;

        if (indice >= entrada.length() || entrada.charAt(indice) == '\n') {
            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_CARACTER_INVALIDO_LITERAL,
                    entrada.substring(inicio, indice),
                    "Literal de caracter invalido: falta contenido o cierre.",
                    linea,
                    columnaInicio,
                    inicio,
                    indice
                ),
                indice,
                linea,
                columna
            );
        }

        char actual = entrada.charAt(indice);
        if (actual > 127) {
            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_ASCII_FUERA_DE_RANGO,
                    String.valueOf(actual),
                    "Caracter fuera del rango ASCII permitido (0-127).",
                    linea,
                    columna,
                    indice,
                    indice + 1
                ),
                indice + 1,
                linea,
                columna + 1
            );
        }

        if (actual == '\'') {
            indice++;
            columna++;
            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_CARACTER_INVALIDO_LITERAL,
                    entrada.substring(inicio, indice),
                    "Literal de caracter invalido: no puede estar vacio.",
                    linea,
                    columnaInicio,
                    inicio,
                    indice
                ),
                indice,
                linea,
                columna
            );
        }

        if (actual == '\\') {
            int columnaError = columna;
            int indiceError = indice;
            indice++;
            columna++;

            if (indice < entrada.length() && entrada.charAt(indice) != '\n') {
                indice++;
                columna++;
            }

            while (indice < entrada.length() && entrada.charAt(indice) != '\n') {
                if (entrada.charAt(indice) == '\'') {
                    indice++;
                    columna++;
                    break;
                }
                indice++;
                columna++;
            }

            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_CARACTER_INVALIDO_LITERAL,
                    entrada.substring(inicio, indice),
                    "Literal de caracter invalido: no se permiten caracteres escapados.",
                    linea,
                    columnaError,
                    indiceError,
                    indice
                ),
                indice,
                linea,
                columna
            );
        }

        indice++;
        columna++;

        if (indice >= entrada.length() || entrada.charAt(indice) == '\n') {
            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_CARACTER_INVALIDO_LITERAL,
                    entrada.substring(inicio, indice),
                    "Literal de caracter invalido: falta la comilla simple de cierre.",
                    linea,
                    columnaInicio,
                    inicio,
                    indice
                ),
                indice,
                linea,
                columna
            );
        }

        if (entrada.charAt(indice) == '\'') {
            indice++;
            columna++;
            String lexema = entrada.substring(inicio, indice);
            return EstadoEscaneo.conComponente(
                new ComponenteLexico(TipoComponente.LITERAL_CARACTER, lexema, linea, columnaInicio, false, inicio, indice),
                indice,
                linea,
                columna
            );
        }

        while (indice < entrada.length() && entrada.charAt(indice) != '\n' && entrada.charAt(indice) != '\'') {
            indice++;
            columna++;
        }

        if (indice < entrada.length() && entrada.charAt(indice) == '\'') {
            indice++;
            columna++;
        }

        return EstadoEscaneo.conError(
            new ErrorLexico(
                ERROR_CARACTER_INVALIDO_LITERAL,
                entrada.substring(inicio, indice),
                "Literal de caracter invalido: solo se permite un caracter.",
                linea,
                columnaInicio,
                inicio,
                indice
            ),
            indice,
            linea,
            columna
        );
    }

    private static EstadoEscaneo escanearFamiliaDiagonal(String entrada, int indice, int linea, int columna) {
        int inicio = indice;
        int lineaInicio = linea;
        int columnaInicio = columna;

        if (ojear(entrada, indice + 1) == '/') {
            indice += 2;
            columna += 2;

            while (indice < entrada.length() && entrada.charAt(indice) != '\n') {
                indice++;
                columna++;
            }

            String lexema = entrada.substring(inicio, indice);
            return EstadoEscaneo.conComponente(
                new ComponenteLexico(TipoComponente.COMENTARIO, lexema, lineaInicio, columnaInicio, true, inicio, indice),
                indice,
                linea,
                columna
            );
        }

        if (ojear(entrada, indice + 1) == '*') {
            indice += 2;
            columna += 2;

            while (indice < entrada.length()) {
                char actual = entrada.charAt(indice);

                if (actual == '\n') {
                    indice++;
                    linea++;
                    columna = 1;
                    continue;
                }

                if (actual == '*' && ojear(entrada, indice + 1) == '/') {
                    indice += 2;
                    columna += 2;
                    String lexema = entrada.substring(inicio, indice);
                    return EstadoEscaneo.conComponente(
                        new ComponenteLexico(TipoComponente.COMENTARIO, lexema, lineaInicio, columnaInicio, true, inicio, indice),
                        indice,
                        linea,
                        columna
                    );
                }

                indice++;
                columna++;
            }

            return EstadoEscaneo.conError(
                new ErrorLexico(
                    ERROR_COMENTARIO_SIN_CIERRE,
                    entrada.substring(inicio),
                    "Comentario de bloque sin cierre.",
                    lineaInicio,
                    columnaInicio,
                    inicio,
                    entrada.length()
                ),
                indice,
                linea,
                columna
            );
        }

        return componenteSimple(TipoComponente.DIVISION, "/", indice, linea, columna);
    }

    private static EstadoEscaneo componenteSimple(TipoComponente tipo, String lexema, int indice, int linea, int columna) {
        return componenteConLongitud(tipo, lexema, indice, linea, columna, 1);
    }

    private static EstadoEscaneo componenteConLongitud(
        TipoComponente tipo,
        String lexema,
        int indice,
        int linea,
        int columna,
        int longitud
    ) {
        return EstadoEscaneo.conComponente(
            new ComponenteLexico(tipo, lexema, linea, columna, tipo == TipoComponente.COMENTARIO, indice, indice + longitud),
            indice + longitud,
            linea,
            columna + longitud
        );
    }

    private static void validarEstructuraBasica(
        String entrada,
        List<ComponenteLexico> componentes,
        List<ErrorLexico> errores
    ) {
        List<ErrorLexico> erroresBase = List.copyOf(errores);
        List<ComponenteLexico> componentesVisibles = new ArrayList<>();
        for (ComponenteLexico componente : componentes) {
            if (!componente.seIgnora()) {
                componentesVisibles.add(componente);
            }
        }

        if (componentesVisibles.isEmpty()) {
            return;
        }

        validarDelimitadores(componentesVisibles, errores);
        validarSecuencias(entrada, componentesVisibles, erroresBase, errores);
        validarFinDeSentencia(componentesVisibles, erroresBase, errores);
    }

    private static void validarDelimitadores(List<ComponenteLexico> componentes, List<ErrorLexico> errores) {
        Deque<ComponenteLexico> pila = new ArrayDeque<>();

        for (ComponenteLexico componente : componentes) {
            TipoComponente tipo = componente.getTipo();

            if (esApertura(tipo)) {
                pila.push(componente);
                continue;
            }

            if (!esCierre(tipo)) {
                continue;
            }

            if (pila.isEmpty()) {
                errores.add(new ErrorLexico(
                    ERROR_CIERRE_INESPERADO,
                    componente.getLexema(),
                    "Cierre inesperado: no existe un delimitador de apertura para " + componente.getLexema() + ".",
                    componente.getLinea(),
                    componente.getColumna(),
                    componente.getIndiceInicio(),
                    componente.getIndiceFin()
                ));
                continue;
            }

            ComponenteLexico apertura = pila.peek();
            if (coincideDelimitador(apertura.getTipo(), tipo)) {
                pila.pop();
                continue;
            }

            errores.add(new ErrorLexico(
                ERROR_CIERRE_INESPERADO,
                componente.getLexema(),
                "Cierre incorrecto: se esperaba " + obtenerCierreEsperado(apertura.getTipo()) + " para cerrar "
                    + apertura.getLexema() + ".",
                componente.getLinea(),
                componente.getColumna(),
                componente.getIndiceInicio(),
                componente.getIndiceFin()
            ));
            pila.pop();
        }

        while (!pila.isEmpty()) {
            ComponenteLexico apertura = pila.pop();
            errores.add(new ErrorLexico(
                ERROR_DELIMITADOR_SIN_CIERRE,
                apertura.getLexema(),
                "Delimitador sin cierre: falta " + obtenerCierreEsperado(apertura.getTipo()) + ".",
                apertura.getLinea(),
                apertura.getColumna(),
                apertura.getIndiceInicio(),
                apertura.getIndiceFin()
            ));
        }
    }

    private static void validarSecuencias(
        String entrada,
        List<ComponenteLexico> componentes,
        List<ErrorLexico> erroresBase,
        List<ErrorLexico> errores
    ) {
        ComponenteLexico primero = componentes.get(0);
        if (esInicioInvalido(primero.getTipo())) {
            errores.add(new ErrorLexico(
                ERROR_ESTRUCTURA_INVALIDA,
                primero.getLexema(),
                "La entrada no puede iniciar con " + primero.getLexema() + ".",
                primero.getLinea(),
                primero.getColumna(),
                primero.getIndiceInicio(),
                primero.getIndiceFin()
            ));
        }

        ComponenteLexico ultimo = componentes.get(componentes.size() - 1);
        if (esFinalIncompleto(ultimo.getTipo())) {
            errores.add(new ErrorLexico(
                ERROR_ESTRUCTURA_INVALIDA,
                ultimo.getLexema(),
                "La entrada termina de forma incompleta despues de " + ultimo.getLexema() + ".",
                ultimo.getLinea(),
                ultimo.getColumna(),
                ultimo.getIndiceInicio(),
                ultimo.getIndiceFin()
            ));
        }

        // Identificadores declarados explícitamente (tipo/class/void + nombre): nunca son "tokens pegados"
        java.util.Set<String> nombresDeclarados = new java.util.HashSet<>();
        for (int k = 1; k < componentes.size(); k++) {
            TipoComponente previo = componentes.get(k - 1).getTipo();
            if (componentes.get(k).getTipo() == TipoComponente.IDENTIFICADOR
                && (esTipoDeDato(previo) || previo == TipoComponente.CLASE || previo == TipoComponente.VACIO
                    || previo == TipoComponente.CORCHETE_CIERRA)) {
                nombresDeclarados.add(componentes.get(k).getLexema());
            }
        }

        for (int i = 0; i < componentes.size() - 1; i++) {
            ComponenteLexico actual = componentes.get(i);
            ComponenteLexico siguiente = componentes.get(i + 1);

            if (hayErrorEntre(actual, siguiente, erroresBase)) {
                continue;
            }

            if (!contieneSoloEspaciosOComentarios(entrada, actual.getIndiceFin(), siguiente.getIndiceInicio())) {
                continue;
            }

            TipoComponente tipoActual = actual.getTipo();
            TipoComponente tipoSiguiente = siguiente.getTipo();

            String prefijoPegado = obtenerPrefijoReservadoPegado(actual);
            TipoComponente tipoPrevio = i > 0 ? componentes.get(i - 1).getTipo() : null;
            boolean nombreLegitimo = nombresDeclarados.contains(actual.getLexema())
                || tipoPrevio == TipoComponente.PUNTO          // obj.charAt(...)
                || tipoPrevio == TipoComponente.CLASE                            // class intentos
                || tipoPrevio == TipoComponente.VACIO                            // void intentar()
                || (tipoPrevio != null && esTipoDeDato(tipoPrevio));             // int intentos = 3
            if (prefijoPegado != null && !nombreLegitimo && esSiguienteCompatibleConTokenPegado(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_TOKEN_PEGADO,
                    actual.getLexema(),
                    "Posible token pegado: '" + actual.getLexema()
                        + "' podria estar uniendo la palabra reservada '" + prefijoPegado + "' con un identificador.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    actual.getIndiceFin()
                ));
                continue;
            }

            if (esOperando(tipoActual)
                && esOperando(tipoSiguiente)
                && !haySaltoDeLineaEntre(entrada, actual.getIndiceFin(), siguiente.getIndiceInicio())) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Secuencia invalida: falto un operador o separador entre ambos elementos.",
                    siguiente.getLinea(),
                    siguiente.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.PUNTO && tipoSiguiente != TipoComponente.IDENTIFICADOR) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Uso invalido del punto: despues de '.' se esperaba un identificador.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (esOperadorEstricto(tipoActual) && !puedeIniciarExpresion(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Expresion invalida: despues del operador " + actual.getLexema() + " se esperaba un valor.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.RESTA && !puedeSeguirResta(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Expresion invalida: despues de '-' se esperaba un valor o una agrupacion.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.ASIGNACION && !puedeIniciarExpresion(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Asignacion incompleta: despues de '=' se esperaba un valor.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.COMA && !puedeIniciarDespuesDeComa(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Separador invalido: despues de " + actual.getLexema() + " se esperaba un valor.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.DOS_PUNTOS && !puedeIniciarExpresion(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Separador invalido: despues de " + actual.getLexema() + " se esperaba un valor.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            boolean menosUnario = tipoSiguiente == TipoComponente.RESTA && tipoActual != TipoComponente.LLAVE_ABRE;
            if (esApertura(tipoActual) && !menosUnario && esElementoInvalidoDespuesDeApertura(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + siguiente.getLexema(),
                    "Agrupacion invalida: despues de " + actual.getLexema() + " no puede ir " + siguiente.getLexema() + ".",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.IMPORTAR && tipoSiguiente != TipoComponente.IDENTIFICADOR) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Importacion invalida: despues de import se esperaba un identificador.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (esModificadorAcceso(tipoActual) && !esSiguienteValidoDespuesDeModificador(tipoSiguiente)) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Declaracion invalida: despues de " + actual.getLexema()
                        + " se esperaba class, void o un tipo de dato.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (esTipoDeDato(tipoActual)
                && tipoSiguiente != TipoComponente.IDENTIFICADOR
                && tipoSiguiente != TipoComponente.CORCHETE_ABRE) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Declaracion invalida: despues del tipo de dato se esperaba un identificador.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.CLASE && tipoSiguiente != TipoComponente.IDENTIFICADOR) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Declaracion invalida: despues de class se esperaba un identificador.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.VACIO && tipoSiguiente != TipoComponente.IDENTIFICADOR) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Declaracion invalida: despues de void se esperaba un identificador.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.REPETIR
                && tipoSiguiente != TipoComponente.PARENTESIS_ABRE
                && tipoSiguiente != TipoComponente.CADA) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Estructura invalida: despues de repeat se esperaba each o '('.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.CADA && tipoSiguiente != TipoComponente.PARENTESIS_ABRE) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Estructura invalida: despues de each se esperaba '('.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
                continue;
            }

            if (tipoActual == TipoComponente.SINO
                && tipoSiguiente != TipoComponente.LLAVE_ABRE
                && tipoSiguiente != TipoComponente.SI) {
                errores.add(new ErrorLexico(
                    ERROR_ESTRUCTURA_INVALIDA,
                    actual.getLexema() + " " + siguiente.getLexema(),
                    "Estructura invalida: despues de else se esperaba '{' o if.",
                    actual.getLinea(),
                    actual.getColumna(),
                    actual.getIndiceInicio(),
                    siguiente.getIndiceFin()
                ));
            }
        }
    }

    private static void validarFinDeSentencia(
        List<ComponenteLexico> componentes,
        List<ErrorLexico> erroresBase,
        List<ErrorLexico> errores
    ) {
        int indice = 0;

        while (indice < componentes.size()) {
            int lineaActual = componentes.get(indice).getLinea();
            List<ComponenteLexico> componentesLinea = new ArrayList<>();

            while (indice < componentes.size() && componentes.get(indice).getLinea() == lineaActual) {
                componentesLinea.add(componentes.get(indice));
                indice++;
            }

            if (componentesLinea.isEmpty() || hayErrorEnLinea(lineaActual, erroresBase)) {
                continue;
            }

            if (!requierePuntoYComa(componentesLinea)) {
                continue;
            }

            ComponenteLexico ultimo = componentesLinea.get(componentesLinea.size() - 1);
            if (ultimo.getTipo() == TipoComponente.PUNTO_Y_COMA
                || ultimo.getTipo() == TipoComponente.LLAVE_ABRE
                || ultimo.getTipo() == TipoComponente.LLAVE_CIERRA) {
                continue;
            }

            errores.add(new ErrorLexico(
                ERROR_FALTA_PUNTO_Y_COMA,
                ultimo.getLexema(),
                "Sentencia incompleta: parece faltar ';' al final de la linea.",
                ultimo.getLinea(),
                ultimo.getColumna(),
                ultimo.getIndiceInicio(),
                ultimo.getIndiceFin()
            ));
        }
    }

    private static void ordenarErrores(List<ErrorLexico> errores) {
        errores.sort(
            Comparator.comparingInt(ErrorLexico::getIndiceInicio)
                .thenComparingInt(ErrorLexico::getIndiceFin)
                .thenComparingInt(ErrorLexico::getCodigo)
        );
    }

    private static boolean esInicioInvalido(TipoComponente tipo) {
        return esCierre(tipo)
            || tipo == TipoComponente.COMA
            || tipo == TipoComponente.DOS_PUNTOS
            || tipo == TipoComponente.PUNTO
            || esOperadorEstricto(tipo);
    }

    private static boolean esFinalIncompleto(TipoComponente tipo) {
        return esOperador(tipo)
            || tipo == TipoComponente.COMA
            || tipo == TipoComponente.DOS_PUNTOS
            || tipo == TipoComponente.PUNTO
            || tipo == TipoComponente.IMPORTAR
            || esModificadorAcceso(tipo)
            || esTipoDeDato(tipo)
            || tipo == TipoComponente.CLASE
            || tipo == TipoComponente.VACIO
            || tipo == TipoComponente.SI
            || tipo == TipoComponente.SINO
            || tipo == TipoComponente.EXISTE
            || tipo == TipoComponente.REPETIR
            || tipo == TipoComponente.MIENTRAS
            || tipo == TipoComponente.CADA;
    }

    private static boolean requierePuntoYComa(List<ComponenteLexico> componentesLinea) {
        if (componentesLinea.isEmpty()) {
            return false;
        }

        TipoComponente primero = componentesLinea.get(0).getTipo();
        ComponenteLexico ultimo = componentesLinea.get(componentesLinea.size() - 1);

        if (ultimo.getTipo() == TipoComponente.LLAVE_ABRE || ultimo.getTipo() == TipoComponente.LLAVE_CIERRA) {
            return false;
        }

        if (primero == TipoComponente.IMPORTAR) {
            return true;
        }

        boolean contieneAsignacion = false;
        boolean contieneLlamada = false;

        for (int i = 0; i < componentesLinea.size(); i++) {
            TipoComponente tipo = componentesLinea.get(i).getTipo();

            if (tipo == TipoComponente.ASIGNACION) {
                contieneAsignacion = true;
            }

            if (tipo == TipoComponente.PARENTESIS_ABRE && i > 0
                && componentesLinea.get(i - 1).getTipo() == TipoComponente.IDENTIFICADOR) {
                contieneLlamada = true;
            }
        }

        if (contieneAsignacion) {
            return true;
        }

        if (contieneLlamada && ultimo.getTipo() == TipoComponente.PARENTESIS_CIERRA) {
            return true;
        }

        if (esTipoDeDato(primero)) {
            return true;
        }

        if (esModificadorAcceso(primero)
            && componentesLinea.size() > 1
            && esTipoDeDato(componentesLinea.get(1).getTipo())) {
            return true;
        }

        return false;
    }

    private static boolean esOperando(TipoComponente tipo) {
        return tipo == TipoComponente.IDENTIFICADOR
            || tipo == TipoComponente.NUMERO_ENTERO
            || tipo == TipoComponente.NUMERO_FLOTANTE
            || tipo == TipoComponente.LITERAL_CADENA
            || tipo == TipoComponente.LITERAL_CARACTER;
    }

    private static boolean esOperador(TipoComponente tipo) {
        return esOperadorEstricto(tipo) || tipo == TipoComponente.RESTA;
    }

    private static boolean esOperadorEstricto(TipoComponente tipo) {
        return tipo == TipoComponente.SUMA
            || tipo == TipoComponente.MULTIPLICACION
            || tipo == TipoComponente.DIVISION
            || tipo == TipoComponente.MODULO
            || tipo == TipoComponente.ASIGNACION
            || tipo == TipoComponente.IGUALDAD
            || tipo == TipoComponente.DIFERENTE
            || tipo == TipoComponente.MENOR_QUE
            || tipo == TipoComponente.MENOR_O_IGUAL
            || tipo == TipoComponente.MAYOR_QUE
            || tipo == TipoComponente.MAYOR_O_IGUAL
            || tipo == TipoComponente.Y_LOGICO
            || tipo == TipoComponente.O_LOGICO;
    }

    private static boolean puedeIniciarExpresion(TipoComponente tipo) {
        return esOperando(tipo)
            || tipo == TipoComponente.PARENTESIS_ABRE
            || tipo == TipoComponente.CORCHETE_ABRE
            || tipo == TipoComponente.NEW
            || tipo == TipoComponente.THIS
            || tipo == TipoComponente.NEGACION
            || tipo == TipoComponente.RESTA;
    }

    private static boolean puedeIniciarDespuesDeComa(TipoComponente tipo) {
        return puedeIniciarExpresion(tipo) || esTipoDeDato(tipo);
    }

    private static boolean puedeSeguirResta(TipoComponente tipo) {
        return puedeIniciarExpresion(tipo);
    }

    private static boolean esElementoInvalidoDespuesDeApertura(TipoComponente tipo) {
        return esOperador(tipo)
            || tipo == TipoComponente.COMA
            || tipo == TipoComponente.DOS_PUNTOS
            || tipo == TipoComponente.PUNTO_Y_COMA;
    }

    private static boolean esApertura(TipoComponente tipo) {
        return tipo == TipoComponente.LLAVE_ABRE
            || tipo == TipoComponente.PARENTESIS_ABRE
            || tipo == TipoComponente.CORCHETE_ABRE;
    }

    private static boolean esCierre(TipoComponente tipo) {
        return tipo == TipoComponente.LLAVE_CIERRA
            || tipo == TipoComponente.PARENTESIS_CIERRA
            || tipo == TipoComponente.CORCHETE_CIERRA;
    }

    private static boolean coincideDelimitador(TipoComponente apertura, TipoComponente cierre) {
        return (apertura == TipoComponente.LLAVE_ABRE && cierre == TipoComponente.LLAVE_CIERRA)
            || (apertura == TipoComponente.PARENTESIS_ABRE && cierre == TipoComponente.PARENTESIS_CIERRA)
            || (apertura == TipoComponente.CORCHETE_ABRE && cierre == TipoComponente.CORCHETE_CIERRA);
    }

    private static String obtenerCierreEsperado(TipoComponente apertura) {
        return switch (apertura) {
            case LLAVE_ABRE -> "}";
            case PARENTESIS_ABRE -> ")";
            case CORCHETE_ABRE -> "]";
            default -> "";
        };
    }

    private static boolean esTipoDeDato(TipoComponente tipo) {
        return tipo == TipoComponente.TIPO_ENTERO
            || tipo == TipoComponente.TIPO_FLOTANTE
            || tipo == TipoComponente.TIPO_CADENA
            || tipo == TipoComponente.TIPO_CARACTER
            || tipo == TipoComponente.TIPO_BOOLEANO;
    }

    private static boolean hayErrorEnLinea(int linea, List<ErrorLexico> errores) {
        for (ErrorLexico error : errores) {
            if (error.getLinea() == linea) {
                return true;
            }
        }

        return false;
    }

    private static boolean esModificadorAcceso(TipoComponente tipo) {
        return tipo == TipoComponente.PUBLICO || tipo == TipoComponente.PRIVADO;
    }

    private static boolean esSiguienteValidoDespuesDeModificador(TipoComponente tipo) {
        return tipo == TipoComponente.CLASE
            || tipo == TipoComponente.VACIO
            || tipo == TipoComponente.IDENTIFICADOR
            || esTipoDeDato(tipo);
    }

    private static boolean requiereParentesis(TipoComponente tipo) {
        return tipo == TipoComponente.SI
            || tipo == TipoComponente.MIENTRAS
            || tipo == TipoComponente.EXISTE;
    }

    private static boolean contieneSoloEspaciosOComentarios(String entrada, int inicio, int fin) {
        int indice = Math.max(0, inicio);
        int limite = Math.min(fin, entrada.length());

        while (indice < limite) {
            char actual = entrada.charAt(indice);

            if (Character.isWhitespace(actual)) {
                indice++;
                continue;
            }

            if (actual == '/' && indice + 1 < limite) {
                char siguiente = entrada.charAt(indice + 1);

                if (siguiente == '/') {
                    indice += 2;
                    while (indice < limite && entrada.charAt(indice) != '\n') {
                        indice++;
                    }
                    continue;
                }

                if (siguiente == '*') {
                    indice += 2;
                    boolean cerrado = false;
                    while (indice + 1 < limite) {
                        if (entrada.charAt(indice) == '*' && entrada.charAt(indice + 1) == '/') {
                            indice += 2;
                            cerrado = true;
                            break;
                        }
                        indice++;
                    }

                    if (!cerrado) {
                        return false;
                    }
                    continue;
                }
            }

            return false;
        }

        return true;
    }

    private static boolean haySaltoDeLineaEntre(String entrada, int inicio, int fin) {
        int desde = Math.max(0, inicio);
        int hasta = Math.min(fin, entrada.length());

        for (int indice = desde; indice < hasta; indice++) {
            if (entrada.charAt(indice) == '\n') {
                return true;
            }
        }

        return false;
    }

    private static boolean hayErrorEntre(
        ComponenteLexico actual,
        ComponenteLexico siguiente,
        List<ErrorLexico> errores
    ) {
        for (ErrorLexico error : errores) {
            if (error.getIndiceInicio() >= actual.getIndiceFin()
                && error.getIndiceFin() <= siguiente.getIndiceInicio()) {
                return true;
            }
        }

        return false;
    }

    private static String obtenerPrefijoReservadoPegado(ComponenteLexico componente) {
        if (componente.getTipo() != TipoComponente.IDENTIFICADOR) {
            return null;
        }

        String lexema = componente.getLexema();
        String[] prefijos = { "int", "float", "string", "char", "boolean", "class", "void", "public", "private", "import" };

        for (String prefijo : prefijos) {
            if (lexema.startsWith(prefijo) && lexema.length() > prefijo.length()) {
                char siguiente = lexema.charAt(prefijo.length());
                if (esLetraAscii(siguiente) || siguiente == '_') {
                    return prefijo;
                }
            }
        }

        return null;
    }

    private static boolean esSiguienteCompatibleConTokenPegado(TipoComponente tipo) {
        return tipo == TipoComponente.ASIGNACION
            || tipo == TipoComponente.PUNTO_Y_COMA
            || tipo == TipoComponente.COMA
            || tipo == TipoComponente.PARENTESIS_ABRE
            || tipo == TipoComponente.LLAVE_ABRE;
    }

    private static char ojear(String entrada, int indice) {
        return indice >= 0 && indice < entrada.length() ? entrada.charAt(indice) : '\0';
    }

    private static boolean esLetraAscii(char valor) {
        return (valor >= 'a' && valor <= 'z') || (valor >= 'A' && valor <= 'Z');
    }

    private static boolean esLetraODigitoAsciiOGuionBajo(char valor) {
        return esLetraAscii(valor) || Character.isDigit(valor) || valor == '_';
    }

    private static String normalizarSaltosDeLinea(String valor) {
        return valor.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Map<String, TipoComponente> construirPalabrasReservadas() {
        Map<String, TipoComponente> palabrasReservadas = new HashMap<>();
        palabrasReservadas.put("if", TipoComponente.SI);
        palabrasReservadas.put("import", TipoComponente.IMPORTAR);
        palabrasReservadas.put("public", TipoComponente.PUBLICO);
        palabrasReservadas.put("private", TipoComponente.PRIVADO);
        palabrasReservadas.put("class", TipoComponente.CLASE);
        palabrasReservadas.put("void", TipoComponente.VACIO);
        palabrasReservadas.put("int", TipoComponente.TIPO_ENTERO);
        palabrasReservadas.put("float", TipoComponente.TIPO_FLOTANTE);
        palabrasReservadas.put("string", TipoComponente.TIPO_CADENA);
        palabrasReservadas.put("char", TipoComponente.TIPO_CARACTER);
        palabrasReservadas.put("boolean", TipoComponente.TIPO_BOOLEANO);
        palabrasReservadas.put("else", TipoComponente.SINO);
        palabrasReservadas.put("exist", TipoComponente.EXISTE);
        palabrasReservadas.put("for", TipoComponente.FOR);
        palabrasReservadas.put("new", TipoComponente.NEW);
        palabrasReservadas.put("repeat", TipoComponente.REPETIR);
        palabrasReservadas.put("while", TipoComponente.MIENTRAS);
        palabrasReservadas.put("each", TipoComponente.CADA);
        palabrasReservadas.put("return", TipoComponente.RETORNAR);
        palabrasReservadas.put("this", TipoComponente.THIS);
        return palabrasReservadas;
    }

    private static final class EstadoEscaneo {
        private final ComponenteLexico componente;
        private final ErrorLexico error;
        private final int siguienteIndice;
        private final int siguienteLinea;
        private final int siguienteColumna;

        private EstadoEscaneo(
            ComponenteLexico componente,
            ErrorLexico error,
            int siguienteIndice,
            int siguienteLinea,
            int siguienteColumna
        ) {
            this.componente = componente;
            this.error = error;
            this.siguienteIndice = siguienteIndice;
            this.siguienteLinea = siguienteLinea;
            this.siguienteColumna = siguienteColumna;
        }

        private static EstadoEscaneo conComponente(
            ComponenteLexico componente,
            int siguienteIndice,
            int siguienteLinea,
            int siguienteColumna
        ) {
            return new EstadoEscaneo(componente, null, siguienteIndice, siguienteLinea, siguienteColumna);
        }

        private static EstadoEscaneo conError(
            ErrorLexico error,
            int siguienteIndice,
            int siguienteLinea,
            int siguienteColumna
        ) {
            return new EstadoEscaneo(null, error, siguienteIndice, siguienteLinea, siguienteColumna);
        }
    }
}
