package sintactico;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tokens.ComponenteLexico;
import tokens.TipoComponente;

/**
 * Analizador sintáctico encargado de validar la estructura del código
 * basándose en la gramática definida. Construye un AST (Árbol de Sintaxis Abstracta).
 */
public final class AnalizadorSintactico {

    private static final int ERR_SE_ESPERABA = 601;
    private static final int ERR_TOKEN_INESPERADO = 600;
    private static final int ERR_EXPRESION = 603;
    private static final int MAX_ERRORES = 100; // Límite para evitar bucles infinitos

    private List<ComponenteLexico> tokens;
    private int pos;
    private List<ErrorSintactico> errores;
    /** Nivel de anidamiento (clase/bloque) en el que se produjo cada error; -1 = recuperado localmente. */
    private List<Integer> nivelesError;
    private int nivelActual;

    public ResultadoAnalisisSintactico analizar(List<ComponenteLexico> componentes) {
        this.tokens = componentes.stream()
                .filter(c -> c.getTipo() != TipoComponente.COMENTARIO)
                .collect(Collectors.toList());
        this.pos = 0;
        this.errores = new ArrayList<>();
        this.nivelesError = new ArrayList<>();
        this.nivelActual = 0;

        if (this.tokens.isEmpty()) {
            agregarError(ERR_TOKEN_INESPERADO, null, "No hay tokens para analizar");
            return new ResultadoAnalisisSintactico(errores, null);
        }

        // Un archivo puede contener varios programas { ... } { ... }
        List<NodoAst> programas = new ArrayList<>();
        while (!fueraDeRango()) {
            int antes = pos;
            programas.add(analizarPrograma());
            if (pos == antes) {
                consumir(); // garantiza progreso
            }
        }

        NodoAst raiz;
        if (programas.size() == 1) {
            raiz = programas.get(0);
        } else {
            raiz = new NodoAst("Codigo");
            for (NodoAst programa : programas) {
                raiz.agregarHijo(programa);
            }
        }

        return new ResultadoAnalisisSintactico(errores, raiz);
    }

    // ==================== ESTRUCTURA PRINCIPAL ====================

    private NodoAst analizarPrograma() {
        NodoAst nodo = new NodoAst("Programa");

        if (!esActual(TipoComponente.LLAVE_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '{' al inicio del programa");
            // Saltar hasta el siguiente '{' para poder analizar el resto del archivo
            while (!fueraDeRango() && !esActual(TipoComponente.LLAVE_ABRE)) {
                consumir();
            }
            return nodo;
        }

        nodo.agregarHijo(new NodoAst(actual()));
        consumir();
        nivelActual++;

        while (!fueraDeRango() && esActual(TipoComponente.IMPORTAR)) {
            nodo.agregarHijo(analizarImport());
        }

        while (!fueraDeRango() && !esActual(TipoComponente.LLAVE_CIERRA)) {
            int inicio = pos;
            int errAntes = errores.size();
            nodo.agregarHijo(analizarElementoPrograma());
            recuperar(inicio, errAntes);
        }
        nivelActual--;

        if (!esActual(TipoComponente.LLAVE_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '}' al final del programa");
            return nodo;
        }

        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        return nodo;
    }

    private NodoAst analizarElementoPrograma() {
        TipoComponente tipo = tipoActual();

        if (tipo == TipoComponente.PUBLICO && mirarSiguienteEs(TipoComponente.CLASE)) {
            return analizarClase();
        }

        if (tipo == TipoComponente.PUBLICO || tipo == TipoComponente.PRIVADO) {
            return analizarMetodo();
        }

        if (tipo == TipoComponente.VACIO || esTipoDato(tipo)) {
            if (mirarSiguienteEs(TipoComponente.IDENTIFICADOR) &&
                    mirarPosicionToken(pos + 2) == TipoComponente.PARENTESIS_ABRE) {
                return analizarMetodo();
            }
            return analizarAtributo();
        }

        if (tipo == TipoComponente.IDENTIFICADOR && mirarSiguienteEs(TipoComponente.PARENTESIS_ABRE)) {
            return analizarConstructor();
        }

        if (tipo == TipoComponente.LLAVE_ABRE) {
            return analizarBloque();
        }

        agregarError(ERR_TOKEN_INESPERADO, actual(),
                Gramatica.mensajeErrorFirst("ElementoPrograma", Gramatica.FIRST_ELEMENTO_PROGRAMA, tipoActual()));
        consumir();
        return new NodoAst("ElementoDesconocido");
    }

    private NodoAst analizarClase() {
        NodoAst nodo = new NodoAst("Clase");

        if (!esActual(TipoComponente.PUBLICO)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'public'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.CLASE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'class'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (esActual(TipoComponente.IDENTIFICADOR)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        } else {
            agregarErrorLocal(ERR_TOKEN_INESPERADO, actual(), "Se esperaba el nombre de la clase");
        }

        if (!esActual(TipoComponente.LLAVE_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '{'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();
        nivelActual++;

        while (!fueraDeRango() && !esActual(TipoComponente.LLAVE_CIERRA)) {
            int inicio = pos;
            int errAntes = errores.size();
            nodo.agregarHijo(analizarMiembroClase());
            recuperar(inicio, errAntes);
        }
        nivelActual--;

        if (!esActual(TipoComponente.LLAVE_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '}'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        return nodo;
    }

    private NodoAst analizarMiembroClase() {
        TipoComponente tipo = tipoActual();

        if (tipo == TipoComponente.PUBLICO || tipo == TipoComponente.PRIVADO) {
            return analizarMetodo();
        }

        if (tipo == TipoComponente.VACIO || esTipoDato(tipo)) {
            if (mirarSiguienteEs(TipoComponente.IDENTIFICADOR) &&
                    mirarPosicionToken(pos + 2) == TipoComponente.PARENTESIS_ABRE) {
                return analizarMetodo();
            }
            return analizarAtributo();
        }

        if (tipo == TipoComponente.IDENTIFICADOR && mirarSiguienteEs(TipoComponente.PARENTESIS_ABRE)) {
            return analizarConstructor();
        }

        agregarError(ERR_TOKEN_INESPERADO, actual(),
                Gramatica.mensajeErrorFirst("MiembroClase", Gramatica.FIRST_METODO, tipoActual()));
        consumir();
        return new NodoAst("MiembroDesconocido");
    }

    // ==================== MÉTODOS ====================

    private NodoAst analizarMetodo() {
        NodoAst nodo = new NodoAst("Metodo");

        if (esActual(TipoComponente.PUBLICO) || esActual(TipoComponente.PRIVADO)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        }

        if (esActual(TipoComponente.VACIO)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        } else if (esTipoDato(tipoActual())) {
            nodo.agregarHijo(analizarTipo());
        } else if (esActual(TipoComponente.IDENTIFICADOR) && mirarSiguienteEs(TipoComponente.PARENTESIS_ABRE)) {
            nodo.agregarHijo(analizarConstructor());
            return nodo;
        } else {
            agregarError(ERR_TOKEN_INESPERADO, actual(),
                    Gramatica.mensajeErrorFirst("TipoRetorno", Gramatica.FIRST_TIPO_RETORNO, tipoActual()));
            return nodo;
        }

        if (!esActual(TipoComponente.IDENTIFICADOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba nombre del método");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_CIERRA) &&
                (esTipoDato(tipoActual()) || esActual(TipoComponente.VACIO))) {
            nodo.agregarHijo(analizarParametro());
            while (esActual(TipoComponente.COMA)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
                nodo.agregarHijo(analizarParametro());
            }
        }

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Este es el punto crítico: analizar el bloque del método con sentencias
        nodo.agregarHijo(analizarBloqueMetodo());

        return nodo;
    }

    private NodoAst analizarParametro() {
        NodoAst nodo = new NodoAst("Parametro");

        if (!esTipoDato(tipoActual())) {
            agregarError(ERR_TOKEN_INESPERADO, actual(),
                    Gramatica.mensajeErrorFirst("Parametro", Gramatica.FIRST_PARAMETRO, tipoActual()));
            return nodo;
        }
        nodo.agregarHijo(analizarTipo());

        if (!esActual(TipoComponente.IDENTIFICADOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba nombre del parámetro");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Manejar corchetes opcionales después del identificador (ej: datos[])
        while (esActual(TipoComponente.CORCHETE_ABRE)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']'");
            } else {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
        }

        return nodo;
    }

    private NodoAst analizarTipo() {
        NodoAst nodo = new NodoAst("Tipo");

        if (!esTipoDato(tipoActual())) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba tipo de dato");
            return nodo;
        }

        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Opcionalmente consumir corchetes que vengan inmediatamente después del tipo
        // Esto es para soportar "int[]" pero no consumirá si no hay corchetes
        if (esActual(TipoComponente.CORCHETE_ABRE)) {
            NodoAst arreglo = new NodoAst("Array");
            arreglo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']'");
            } else {
                arreglo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
            nodo.agregarHijo(arreglo);
        }

        return nodo;
    }

    private NodoAst analizarConstructor() {
        NodoAst nodo = new NodoAst("Constructor");

        if (!esActual(TipoComponente.IDENTIFICADOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba nombre del constructor");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_CIERRA) &&
                (esTipoDato(tipoActual()) || esActual(TipoComponente.VACIO))) {
            nodo.agregarHijo(analizarParametro());
            while (esActual(TipoComponente.COMA)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
                nodo.agregarHijo(analizarParametro());
            }
        }

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarBloqueMetodo());

        return nodo;
    }

    private NodoAst analizarImport() {
        NodoAst nodo = new NodoAst("Import");

        // import
        if (!esActual(TipoComponente.IMPORTAR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'import'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // nombre del paquete/clase (puede tener puntos)
        if (!esActual(TipoComponente.IDENTIFICADOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba nombre del paquete o clase");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Más identificadores con puntos: java.util.Scanner
        while (esActual(TipoComponente.PUNTO)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();

            if (!esActual(TipoComponente.IDENTIFICADOR)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba identificador después de '.'");
                return nodo;
            }
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        }

        // ;
        if (!esActual(TipoComponente.PUNTO_Y_COMA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ';' al final del import");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        return nodo;
    }

    // ==================== ATRIBUTOS ====================

    private NodoAst analizarAtributo() {
        NodoAst nodo = new NodoAst("Atributo");

        if (!esTipoDato(tipoActual())) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba tipo de dato");
            return nodo;
        }
        nodo.agregarHijo(analizarTipo());

        if (!esActual(TipoComponente.IDENTIFICADOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba nombre");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Manejar corchetes opcionales después del identificador (ej: montos[])
        while (esActual(TipoComponente.CORCHETE_ABRE)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']'");
            } else {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
        }

        if (esActual(TipoComponente.ASIGNACION)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarExpresion());
        }

        if (esActual(TipoComponente.PUNTO_Y_COMA)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        } else {
            errorPuntoYComaFaltante("al final de la declaración del atributo");
        }

        return nodo;
    }

    // ==================== BLOQUES ====================

    private NodoAst analizarBloqueMetodo() {
        NodoAst nodo = new NodoAst("Bloque");

        if (!esActual(TipoComponente.LLAVE_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '{'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nivelActual++;
        while (!fueraDeRango() && !esActual(TipoComponente.LLAVE_CIERRA)) {
            int inicio = pos;
            int errAntes = errores.size();
            nodo.agregarHijo(analizarSentencia());
            recuperar(inicio, errAntes);
        }
        nivelActual--;

        if (!esActual(TipoComponente.LLAVE_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '}'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        return nodo;
    }

  private NodoAst analizarBloque() {
    NodoAst nodo = new NodoAst("Bloque");

    if (!esActual(TipoComponente.LLAVE_ABRE)) {
      agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '{'");
      return nodo;
    }
    nodo.agregarHijo(new NodoAst(actual()));
    consumir();

    nivelActual++;
    while (!fueraDeRango() && !esActual(TipoComponente.LLAVE_CIERRA)) {
      int inicio = pos;
      int errAntes = errores.size();
      nodo.agregarHijo(analizarElementoPrograma());
      recuperar(inicio, errAntes);
    }
    nivelActual--;

    if (!esActual(TipoComponente.LLAVE_CIERRA)) {
      agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '}'");
      return nodo;
    }
    nodo.agregarHijo(new NodoAst(actual()));
    consumir();

    return nodo;
  }

    // ==================== SENTENCIAS ====================

  private NodoAst analizarSentencia() {
    if (fueraDeRango()) {
      return new NodoAst("SentenciaVacia");
    }

    TipoComponente tipo = tipoActual();

    if (tipo == TipoComponente.MIENTRAS) {
      return analizarWhile();
    }
    if (tipo == TipoComponente.SI) {
      return analizarIf();
    }
    if (tipo == TipoComponente.REPETIR) {
      return analizarRepeat();
    }
    if (tipo == TipoComponente.FOR) {
      return analizarFor();
    }
    if (tipo == TipoComponente.EXISTE) {
      return analizarExist();
    }
    if (tipo == TipoComponente.RETORNAR) {
      return analizarReturn();
    }
    if (esTipoDato(tipo)) {
      NodoAst nodo = new NodoAst("Sentencia");
      nodo.agregarHijo(analizarDeclaracionLocal());
      esperarPuntoYComa();
      return nodo;
    }
    if (tipo == TipoComponente.IDENTIFICADOR || tipo == TipoComponente.THIS) {
      if (hayAsignacionDespues()) {
        NodoAst nodo = new NodoAst("Sentencia");
        nodo.agregarHijo(analizarAsignacion());
        esperarPuntoYComa();
        return nodo;
      } else {
        NodoAst nodo = new NodoAst("Sentencia");
        nodo.agregarHijo(analizarExpresion());
        esperarPuntoYComa();
        return nodo;
      }
    }

    if (esInicioExpresion(tipo)) { // sentencia-expresión que no empieza con ID/this
      NodoAst nodo = new NodoAst("Sentencia");
      nodo.agregarHijo(analizarExpresion());
      esperarPuntoYComa();
      return nodo;
    }

    agregarError(ERR_TOKEN_INESPERADO, actual(),
            Gramatica.mensajeErrorFirst("Sentencia", Gramatica.FIRST_SENTENCIA, tipoActual()));
    consumir();
    return new NodoAst("SentenciaError");
  }

    private NodoAst analizarWhile() {
        NodoAst nodo = new NodoAst("While");

        if (!esActual(TipoComponente.MIENTRAS)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'while'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarExpresion());

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarBloqueMetodo());

        return nodo;
    }

    private NodoAst analizarIf() {
        NodoAst nodo = new NodoAst("If");

        if (!esActual(TipoComponente.SI)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'if'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarExpresion());

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            if (!fueraDeRango()) {
                consumir();
            }
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarBloqueMetodo());

        if (esActual(TipoComponente.SINO)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (esActual(TipoComponente.SI)) {
                nodo.agregarHijo(analizarIf()); // else if encadenado
            } else {
                nodo.agregarHijo(analizarBloqueMetodo());
            }
        }

        return nodo;
    }

    private NodoAst analizarRepeat() {
        NodoAst nodo = new NodoAst("Repeat");

        if (!esActual(TipoComponente.REPETIR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'repeat'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (esActual(TipoComponente.MIENTRAS)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarExpresion());
        } else if (esActual(TipoComponente.CADA)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
                return nodo;
            }
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarAsignacion());
            if (!esActual(TipoComponente.DOS_PUNTOS)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ':'");
                return nodo;
            }
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarExpresion());
            if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
                if (!fueraDeRango()) {
                    consumir();
                }
                return nodo;
            }
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        } else if (esTipoDato(tipoActual())) {
            nodo.agregarHijo(analizarDeclaracionLocal());
            if (esActual(TipoComponente.PUNTO_Y_COMA)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
            nodo.agregarHijo(analizarExpresion());
            if (esActual(TipoComponente.PUNTO_Y_COMA)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
            nodo.agregarHijo(analizarAsignacion());
        } else {
            nodo.agregarHijo(analizarExpresion());
        }

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");            if (!fueraDeRango()) {
                consumir();
            }            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarBloqueMetodo());

        return nodo;
    }

    private NodoAst analizarFor() {
        NodoAst nodo = new NodoAst("For");

        if (!esActual(TipoComponente.FOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'for'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (esTipoDato(tipoActual())) {
            nodo.agregarHijo(analizarDeclaracionLocal());
        } else if ((esActual(TipoComponente.IDENTIFICADOR) || esActual(TipoComponente.THIS)) && hayAsignacionDespues()) {
            nodo.agregarHijo(analizarAsignacion());
        } else if (esInicioExpresion(tipoActual())) {
            nodo.agregarHijo(analizarExpresion());
        }

        if (esActual(TipoComponente.PUNTO_Y_COMA)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        } else {
            errorPuntoYComaFaltante("en el encabezado del for");
        }

        if (esInicioExpresion(tipoActual())) {
            nodo.agregarHijo(analizarExpresion());
        }

        if (esActual(TipoComponente.PUNTO_Y_COMA)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
        } else {
            errorPuntoYComaFaltante("en el encabezado del for");
        }

        if ((esActual(TipoComponente.IDENTIFICADOR) || esActual(TipoComponente.THIS)) && hayAsignacionDespues()) {
            nodo.agregarHijo(analizarAsignacion());
        } else if (esInicioExpresion(tipoActual())) {
            nodo.agregarHijo(analizarExpresion());
        }

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            if (!fueraDeRango()) {
                consumir();
            }
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarBloqueMetodo());
        return nodo;
    }

    private NodoAst analizarExist() {
        NodoAst nodo = new NodoAst("Exist");

        if (!esActual(TipoComponente.EXISTE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'exist'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarExpresion());

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            if (!fueraDeRango()) {
                consumir();
            }
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarBloqueMetodo());

        return nodo;
    }

    private NodoAst analizarReturn() {
        NodoAst nodo = new NodoAst("Return");

        if (!esActual(TipoComponente.RETORNAR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba 'return'");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!fueraDeRango() && esInicioExpresion(tipoActual())) {
            nodo.agregarHijo(analizarExpresion());
        }

        esperarPuntoYComa();
        return nodo;
    }

    // ==================== DECLARACIONES LOCALES ====================

    private NodoAst analizarDeclaracionLocal() {
        NodoAst nodo = new NodoAst("DeclaracionLocal");

        if (!esTipoDato(tipoActual())) {
            agregarError(ERR_TOKEN_INESPERADO, actual(),
                    Gramatica.mensajeErrorFirst("Declaracion", Gramatica.FIRST_DECLARACION, tipoActual()));
            return nodo;
        }
        nodo.agregarHijo(analizarTipo());

        if (!esActual(TipoComponente.IDENTIFICADOR)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba nombre de variable");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Manejar corchetes opcionales después del identificador (ej: arr[])
        while (esActual(TipoComponente.CORCHETE_ABRE)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']'");
            } else {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
        }

        if (esActual(TipoComponente.ASIGNACION)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarExpresion());
        }

        return nodo;
    }

    private NodoAst analizarAsignacion() {
        NodoAst nodo = new NodoAst("Asignacion");

        if (!esActual(TipoComponente.IDENTIFICADOR) && !esActual(TipoComponente.THIS)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba identificador");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        // Destino: id, id[expr], this.campo, obj.campo[expr] ...
        while (esActual(TipoComponente.CORCHETE_ABRE) || esActual(TipoComponente.PUNTO)) {
            if (esActual(TipoComponente.PUNTO)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
                if (!esActual(TipoComponente.IDENTIFICADOR)) {
                    agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba identificador despues de '.'");
                    return nodo;
                }
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
                continue;
            }
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            if (esInicioExpresion(tipoActual())) {
                nodo.agregarHijo(analizarExpresion());
            }
            if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                agregarErrorLocal(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']'");
            } else {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
        }

        if (!esActual(TipoComponente.ASIGNACION)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '='");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        nodo.agregarHijo(analizarExpresion());

        return nodo;
    }

    // ==================== EXPRESIONES ====================

    private NodoAst analizarExpresion() {
        NodoAst nodo = new NodoAst("Expresion");

        if (!esInicioExpresion(tipoActual())) {
            agregarError(ERR_EXPRESION, actual(),
                    Gramatica.mensajeErrorFirst("Expresion", Gramatica.FIRST_EXPRESION, tipoActual()));
            return nodo;
        }

        if (esActual(TipoComponente.NEGACION) || esActual(TipoComponente.RESTA)) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarTermino());
        } else {
            nodo.agregarHijo(analizarTermino());
        }

        while (!fueraDeRango() && esOperador(tipoActual())) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            // unario después de un operador:  a / -b ,  x || !y
            if (esActual(TipoComponente.NEGACION) || esActual(TipoComponente.RESTA)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            }
            nodo.agregarHijo(analizarTermino());
        }

        return nodo;
    }

    private NodoAst analizarTermino() {
        NodoAst nodo = new NodoAst("Termino");
        TipoComponente tipo = tipoActual();

        if (tipo == TipoComponente.NEW) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            
            // Consumir el tipo de dato base
            if (esTipoDato(tipoActual())) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
            } else {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba tipo de dato después de 'new'");
                return nodo;
            }
            
            // Manejar corchetes con dimensiones para new (ej: new float[0], new int[cantidad+1])
            if (esActual(TipoComponente.CORCHETE_ABRE)) {
                NodoAst arreglo = new NodoAst("Array");
                while (esActual(TipoComponente.CORCHETE_ABRE)) {
                    arreglo.agregarHijo(new NodoAst(actual()));
                    consumir();
                    
                    // Puede haber una expresión dentro de los corchetes para indicar la dimensión
                    if (esInicioExpresion(tipoActual())) {
                        arreglo.agregarHijo(analizarExpresion());
                    }
                    
                    if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                        agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']' después de dimensión");
                    } else {
                        arreglo.agregarHijo(new NodoAst(actual()));
                        consumir();
                    }
                }
                nodo.agregarHijo(arreglo);
            }
            
            return nodo;
        }

        if (tipo == TipoComponente.IDENTIFICADOR ||
                tipo == TipoComponente.NUMERO_ENTERO ||
                tipo == TipoComponente.NUMERO_FLOTANTE ||
                tipo == TipoComponente.LITERAL_CADENA ||
                tipo == TipoComponente.LITERAL_CARACTER ||
                tipo == TipoComponente.THIS) {

            nodo.agregarHijo(new NodoAst(actual()));
            consumir();

            while (true) {
                if (esActual(TipoComponente.PARENTESIS_ABRE)) {
                    nodo.agregarHijo(analizarLlamadaMetodo());
                    continue;
                }
                if (esActual(TipoComponente.CORCHETE_ABRE)) {
                    nodo.agregarHijo(new NodoAst(actual()));
                    consumir();
                    if (esInicioExpresion(tipoActual())) {
                        nodo.agregarHijo(analizarExpresion());
                    }
                    if (!esActual(TipoComponente.CORCHETE_CIERRA)) {
                        agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ']'");
                    } else {
                        nodo.agregarHijo(new NodoAst(actual()));
                        consumir();
                    }
                    continue;
                }
                if (esActual(TipoComponente.PUNTO)) {
                    nodo.agregarHijo(new NodoAst(actual()));
                    consumir();
                    if (!esActual(TipoComponente.IDENTIFICADOR)) {
                        agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba identificador despues de '.'");
                        return nodo;
                    }
                    nodo.agregarHijo(new NodoAst(actual()));
                    consumir();
                    continue;
                }
                if (esActual(TipoComponente.INCREMENTO) || esActual(TipoComponente.DECREMENTO)) {
                    nodo.agregarHijo(new NodoAst(actual()));
                    consumir();
                    continue;
                }
                break;
            }
            return nodo;
        }

        if (tipo == TipoComponente.PARENTESIS_ABRE) {
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            nodo.agregarHijo(analizarExpresion());
            if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
                agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
                if (!fueraDeRango()) {
                    consumir();
                }
                return nodo;
            }
            nodo.agregarHijo(new NodoAst(actual()));
            consumir();
            return nodo;
        }

        agregarError(ERR_EXPRESION, actual(),
                Gramatica.mensajeErrorFirst("Termino", Gramatica.FIRST_EXPRESION, tipoActual()));
        return nodo;
    }

    private NodoAst analizarLlamadaMetodo() {
        NodoAst nodo = new NodoAst("LlamadaMetodo");

        if (!esActual(TipoComponente.PARENTESIS_ABRE)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba '('");
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        if (!esActual(TipoComponente.PARENTESIS_CIERRA) && esInicioExpresion(tipoActual())) {
            nodo.agregarHijo(analizarExpresion());
            while (esActual(TipoComponente.COMA)) {
                nodo.agregarHijo(new NodoAst(actual()));
                consumir();
                nodo.agregarHijo(analizarExpresion());
            }
        }

        if (!esActual(TipoComponente.PARENTESIS_CIERRA)) {
            agregarError(ERR_TOKEN_INESPERADO, actual(), "Se esperaba ')'");
            if (!fueraDeRango()) {
                consumir();
            }
            return nodo;
        }
        nodo.agregarHijo(new NodoAst(actual()));
        consumir();

        return nodo;
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private TipoComponente mirarPosicionToken(int posicion) {
        if (posicion >= tokens.size())
            return null;
        ComponenteLexico token = tokens.get(posicion);
        return token != null ? token.getTipo() : null;
    }

    private boolean mirarSiguienteEs(TipoComponente tipo) {
        if (fueraDeRango() || pos + 1 >= tokens.size()) {
            return false;
        }
        ComponenteLexico next = tokens.get(pos + 1);
        return next != null && next.getTipo() == tipo;
    }

    private void esperarPuntoYComa() {
        if (esActual(TipoComponente.PUNTO_Y_COMA)) {
            consumir();
        } else {
            errorPuntoYComaFaltante("al final de la sentencia");
        }
    }

    /**
     * Reporta un ';' faltante en la posición donde debió ir (al final del token
     * anterior) y NO consume nada: equivale a insertar un ';' virtual.
     */
    private void errorPuntoYComaFaltante(String donde) {
        ComponenteLexico anterior = (pos > 0 && pos <= tokens.size()) ? tokens.get(pos - 1) : actual();
        String encontrado = actual() != null ? "'" + actual().getLexema() + "'" : "fin de archivo";
        String mensaje = "Falta ';' " + donde + " (se encontró " + encontrado + ")";
        agregarErrorLocal(ERR_SE_ESPERABA, anterior, mensaje);
    }

    // ==================== CONSULTAS A GRAMATICA (FIRST/FOLLOW) ====================

    /** Delega a Gramatica.FIRST_TIPO_DATO */
    private boolean esTipoDato(TipoComponente tipo) {
        return tipo != null && Gramatica.FIRST_TIPO_DATO.contains(tipo);
    }

    /** Delega a Gramatica.FIRST_EXPRESION */
    private boolean esInicioExpresion(TipoComponente tipo) {
        return tipo != null && Gramatica.FIRST_EXPRESION.contains(tipo);
    }

    /** Operadores aritméticos y relacionales válidos en expresiones */
    private boolean esOperador(TipoComponente tipo) {
        return tipo == TipoComponente.SUMA
                || tipo == TipoComponente.RESTA
                || tipo == TipoComponente.MULTIPLICACION
                || tipo == TipoComponente.DIVISION
                || tipo == TipoComponente.MODULO
                || tipo == TipoComponente.IGUALDAD
                || tipo == TipoComponente.DIFERENTE
                || tipo == TipoComponente.MENOR_QUE
                || tipo == TipoComponente.MENOR_O_IGUAL
                || tipo == TipoComponente.MAYOR_QUE
                || tipo == TipoComponente.MAYOR_O_IGUAL
                || tipo == TipoComponente.Y_LOGICO
                || tipo == TipoComponente.O_LOGICO;
    }

    /**
     * Mira hacia adelante (sin consumir) para saber si la sentencia que inicia en
     * el token actual es una asignación: (ID|this) { .ID | [ ... ] } '='
     */
    private boolean hayAsignacionDespues() {
        if (fueraDeRango()) {
            return false;
        }
        int i = pos + 1;
        while (i < tokens.size()) {
            TipoComponente t = tokens.get(i).getTipo();
            if (t == TipoComponente.PUNTO) {
                i += 2; // '.' ID
            } else if (t == TipoComponente.CORCHETE_ABRE) {
                int profundidad = 1;
                i++;
                while (i < tokens.size() && profundidad > 0) {
                    TipoComponente u = tokens.get(i).getTipo();
                    if (u == TipoComponente.CORCHETE_ABRE) {
                        profundidad++;
                    } else if (u == TipoComponente.CORCHETE_CIERRA) {
                        profundidad--;
                    }
                    i++;
                }
            } else {
                break;
            }
        }
        return i < tokens.size() && tokens.get(i).getTipo() == TipoComponente.ASIGNACION;
    }

    // ==================== MÉTODOS DE CONTROL ====================

    private boolean fueraDeRango() {
        return pos >= tokens.size();
    }

    private ComponenteLexico actual() {
        if (fueraDeRango()) {
            return null;
        }
        return tokens.get(pos);
    }

    private String getLexemaActual() {
        ComponenteLexico actual = actual();
        return actual != null ? actual.getLexema() : "EOF";
    }

    private TipoComponente tipoActual() {
        ComponenteLexico actual = actual();
        return actual != null ? actual.getTipo() : null;
    }

    private boolean esActual(TipoComponente tipo) {
        if (fueraDeRango()) {
            return false;
        }
        ComponenteLexico actual = actual();
        return actual != null && actual.getTipo() == tipo;
    }

    private void consumir() {
        if (!fueraDeRango()) {
            pos++;
        }
    }

    /** Error que obliga a re-sincronizar la sentencia/miembro actual (modo pánico). */
    private void agregarError(int codigo, ComponenteLexico token, String mensaje) {
        registrar(codigo, token, mensaje, nivelActual);
    }

    /** Error del que el parser ya se recuperó (por ejemplo, un ';' virtual): no salta tokens. */
    private void agregarErrorLocal(int codigo, ComponenteLexico token, String mensaje) {
        registrar(codigo, token, mensaje, -1);
    }

    private void registrar(int codigo, ComponenteLexico token, String mensaje, int nivel) {
        if (errores.size() >= MAX_ERRORES) {
            if (errores.size() == MAX_ERRORES) {
                ComponenteLexico ultimo = tokens.isEmpty() ? null : tokens.get(tokens.size() - 1);
                errores.add(new ErrorSintactico(600, "EOF", "Demasiados errores de sintaxis. Análisis detenido.",
                        ultimo != null ? ultimo.getLinea() : -1, ultimo != null ? ultimo.getColumna() : -1));
                nivelesError.add(-1);
            }
            return;
        }
        // En fin de archivo se reporta sobre el último token real (no en la línea -1)
        boolean esEof = token == null;
        ComponenteLexico ref = esEof && !tokens.isEmpty() ? tokens.get(tokens.size() - 1) : token;
        String lexema = esEof ? "EOF" : token.getLexema();
        int linea = ref != null ? ref.getLinea() : -1;
        int columna = ref != null ? ref.getColumna() : -1;

        for (ErrorSintactico e : errores) {
            if (e.getLinea() == linea && e.getColumna() == columna && e.getMensaje().equals(mensaje)) {
                return; // evita duplicados idénticos
            }
        }
        errores.add(new ErrorSintactico(codigo, lexema, mensaje, linea, columna));
        nivelesError.add(nivel);
    }

    /**
     * Recuperación en modo pánico por delimitadores balanceados. Si dentro de la
     * sentencia/miembro que empezó en 'inicio' hubo un error que dejó el parser a
     * medias, salta hasta el final de esa sentencia (';' o '}' que balancea) sin
     * tragarse la '}' que pertenece al bloque contenedor.
     */
    private void recuperar(int inicio, int errAntes) {
        boolean hayError = false;
        for (int i = errAntes; i < errores.size() && i < nivelesError.size(); i++) {
            if (nivelesError.get(i) == nivelActual) {
                hayError = true;
                break;
            }
        }
        if (hayError) {
            // llaves que quedaron abiertas dentro de lo ya consumido
            int profundidad = 0;
            for (int i = inicio; i < pos && i < tokens.size(); i++) {
                TipoComponente t = tokens.get(i).getTipo();
                if (t == TipoComponente.LLAVE_ABRE) {
                    profundidad++;
                } else if (t == TipoComponente.LLAVE_CIERRA) {
                    profundidad--;
                }
            }
            if (profundidad < 0) {
                profundidad = 0;
            }
            while (!fueraDeRango()) {
                TipoComponente t = tipoActual();
                if (t == TipoComponente.PUNTO_Y_COMA && profundidad == 0) {
                    consumir();
                    break;
                }
                if (t == TipoComponente.LLAVE_ABRE) {
                    profundidad++;
                } else if (t == TipoComponente.LLAVE_CIERRA) {
                    if (profundidad == 0) {
                        break; // pertenece al bloque contenedor
                    }
                    profundidad--;
                    if (profundidad == 0) {
                        consumir();
                        break;
                    }
                }
                consumir();
            }
        }
        if (pos == inicio && !fueraDeRango() && !esActual(TipoComponente.LLAVE_CIERRA)) {
            consumir(); // garantiza progreso
        } else if (pos == inicio && esActual(TipoComponente.LLAVE_CIERRA)) {
            // '}' sobrante a nivel de contenedor: lo tratará el contenedor
            consumir();
        }
    }
}
