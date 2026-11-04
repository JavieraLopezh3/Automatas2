package semantico;

import java.util.ArrayList;
import java.util.List;

import tokens.ComponenteLexico;

/**
 * Nodo del árbol semántico: un nodo del programa ya "decorado" con lo que el análisis semántico
 * dedujo de él (su tipo, su ámbito, si es congruente o no).
 */
public final class NodoSemantico {

    /** Clase de nodo; sirve para darle un color/icono distinto en la interfaz. */
    public enum Categoria {
        ARCHIVO, PROGRAMA, IMPORT, CLASE, ATRIBUTO, METODO, PARAMETROS, PARAMETRO,
        AMBITO, DECLARACION, ASIGNACION, CONTROL, RETORNO, SENTENCIA,
        EXPRESION, OPERADOR, OPERANDO, SIMBOLOS, SIMBOLO, ERROR, AVISO
    }

    private final Categoria categoria;
    private final String etiqueta;
    private final String tipo;
    private final String nota;
    private final int linea;
    private final int columna;
    private final List<NodoSemantico> hijos = new ArrayList<>();
    private boolean conError;
    private boolean conAviso;

    public NodoSemantico(Categoria categoria, String etiqueta, String tipo, String nota, ComponenteLexico pos) {
        this(categoria, etiqueta, tipo, nota, pos != null ? pos.getLinea() : -1, pos != null ? pos.getColumna() : -1);
    }

    public NodoSemantico(Categoria categoria, String etiqueta, String tipo, String nota, int linea, int columna) {
        this.categoria = categoria;
        this.etiqueta = etiqueta;
        this.tipo = tipo;
        this.nota = nota;
        this.linea = linea;
        this.columna = columna;
    }

    public NodoSemantico agregar(NodoSemantico hijo) {
        if (hijo != null) {
            hijos.add(hijo);
        }
        return this;
    }

    public NodoSemantico agregarTodos(List<NodoSemantico> nuevos) {
        for (NodoSemantico n : nuevos) {
            agregar(n);
        }
        return this;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Tipo deducido (por ejemplo "int" o "float[]"), o null si el nodo no tiene tipo. */
    public String getTipo() {
        return tipo;
    }

    /** Dato adicional (ámbito, firma, etc.), o null. */
    public String getNota() {
        return nota;
    }

    public int getLinea() {
        return linea;
    }

    public int getColumna() {
        return columna;
    }

    public List<NodoSemantico> getHijos() {
        return hijos;
    }

    /** El nodo mismo es el origen de un error semántico. */
    public boolean tieneError() {
        return conError;
    }

    public boolean tieneAviso() {
        return conAviso;
    }

    void marcar(boolean esError) {
        if (esError) {
            conError = true;
        } else {
            conAviso = true;
        }
    }

    /** Cuántos errores (no avisos) hay en este nodo y debajo de él. */
    public int contarErrores() {
        int n = categoria == Categoria.ERROR ? 1 : 0;
        for (NodoSemantico h : hijos) {
            n += h.contarErrores();
        }
        return n;
    }

    public int contarAvisos() {
        int n = categoria == Categoria.AVISO ? 1 : 0;
        for (NodoSemantico h : hijos) {
            n += h.contarAvisos();
        }
        return n;
    }

    public void imprimir(String prefijo, boolean ultimo) {
        System.out.println(prefijo + (ultimo ? "└── " : "├── ") + etiqueta
                + (tipo != null ? "  : " + tipo : "") + (nota != null ? "  [" + nota + "]" : ""));
        for (int i = 0; i < hijos.size(); i++) {
            hijos.get(i).imprimir(prefijo + (ultimo ? "    " : "│   "), i == hijos.size() - 1);
        }
    }
}
