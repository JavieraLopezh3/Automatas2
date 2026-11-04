package sintactico;

import java.util.ArrayList;
import java.util.List;

import tokens.ComponenteLexico;

public class NodoAst {
    private String nombre;
    private ComponenteLexico token;
    private List<NodoAst> hijos;

    public NodoAst(String nombre) {
        this.nombre = nombre;
        this.token = null;
        this.hijos = new ArrayList<>();
    }

    public NodoAst(ComponenteLexico token) {
        this.nombre = token.getTipo().getNombreVisible();
        this.token = token;
        this.hijos = new ArrayList<>();
    }

    public void agregarHijo(NodoAst hijo) {
        if (hijo != null) {
            this.hijos.add(hijo);
        }
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    public ComponenteLexico getToken() {
        return token;
    }

    public List<NodoAst> getHijos() {
        return hijos;
    }

    public void imprimir(String prefijo, boolean esUltimo) {
        System.out.println(prefijo + (esUltimo ? "└── " : "├── ") + nombre
                + (token != null ? " (" + token.getLexema() + ")" : ""));
        for (int i = 0; i < hijos.size(); i++) {
            hijos.get(i).imprimir(prefijo + (esUltimo ? "    " : "│   "), i == hijos.size() - 1);
        }
    }
}
