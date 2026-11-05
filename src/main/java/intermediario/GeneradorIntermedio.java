package intermediario;

import java.util.ArrayList;
import java.util.List;
import sintactico.NodoAst;

/**
 * Generador de código intermedio en forma de cuádruplos.
 * Basado en el Capítulo 6 de Aho, Lam, Sethi y Ullman.
 * Implementa saltos condicionales y no condicionales para estructuras de control.
 */
public class GeneradorIntermedio {
    private int temporalCount = 0;
    private int labelCount = 0;
    private final List<Cuadruplo> quadruples = new ArrayList<>();

    private String newTemporal() {
        return "t" + (++temporalCount);
    }

    private String newLabel() {
        return "L" + (++labelCount);
    }

    public String generar(NodoAst nodo) {
        if (nodo == null) return "";

        String nombre = nodo.getNombre();
        List<NodoAst> hijos = nodo.getHijos();

        // 1. Nodo hoja (Identificador o Constante)
        if (hijos.isEmpty()) {
            if (nodo.getToken() != null) {
                return nodo.getToken().getLexema();
            }
            return nombre;
        }

        // 2. Estructuras de Control
        if (nombre.equals("While")) {
            return generarWhile(hijos);
        } else if (nombre.equals("If")) {
            return generarIf(hijos);
        } else if (nombre.equals("Bloque")) {
            for (NodoAst hijo : hijos) {
                generar(hijo);
            }
            return "";
        } else if (nombre.equals("Sentencia")) {
            // El nodo Sentencia suele envolver una expresión o una declaración
            if (!hijos.isEmpty()) return generar(hijos.get(0));
            return "";
        }

        // 3. Operaciones Binarias
        if (hijos.size() == 2) {
            String izq = generar(hijos.get(0));
            String der = generar(hijos.get(1));
            
            if (nombre.contains("Asignacion")) {
                quadruples.add(new Cuadruplo("=", der, null, izq));
                return izq;
            }

            String res = newTemporal();
            String op = mapearOperador(nombre);
            quadruples.add(new Cuadruplo(op, izq, der, res));
            return res;
        }

        // 4. Operaciones Unarias
        if (hijos.size() == 1) {
            String arg = generar(hijos.get(0));
            String res = newTemporal();
            String op = nombre.contains("Negacion") ? "umins" : "not";
            quadruples.add(new Cuadruplo(op, arg, null, res));
            return res;
        }

        // Recorrido genérico para nodos desconocidos
        for (NodoAst hijo : hijos) {
            generar(hijo);
        }

        return "";
    }

    private String generarWhile(List<NodoAst> hijos) {
        String labelStart = newLabel();
        String labelEnd = newLabel();

        // Etiqueta de inicio del ciclo
        quadruples.add(new Cuadruplo("LABEL", null, null, labelStart));

        // Evaluación de la condición (asumiendo que el primer hijo es la condición)
        String cond = generar(hijos.get(0));

        // Salto si la condición es falsa -> Fin del ciclo
        quadruples.add(new Cuadruplo("IF_FALSE", cond, null, labelEnd));

        // Cuerpo del ciclo (segundo hijo)
        if (hijos.size() > 1) {
            generar(hijos.get(1));
        }

        // Salto incondicional al inicio
        quadruples.add(new Cuadruplo("GOTO", null, null, labelStart));

        // Etiqueta de fin del ciclo
        quadruples.add(new Cuadruplo("LABEL", null, null, labelEnd));

        return "";
    }

    private String generarIf(List<NodoAst> hijos) {
        String labelElse = newLabel();
        String labelEnd = newLabel();

        // Condición
        String cond = generar(hijos.get(0));

        // Si la condición es falsa, saltar al else o al final
        quadruples.add(new Cuadruplo("IF_FALSE", cond, null, 
            (hijos.size() > 2) ? labelElse : labelEnd));

        // Bloque "Entonces" (Then)
        if (hijos.size() > 1) {
            generar(hijos.get(1));
        }

        // Salto al final para evitar ejecutar el else
        quadruples.add(new Cuadruplo("GOTO", null, null, labelEnd));

        // Bloque "Sino" (Else)
        if (hijos.size() > 2) {
            quadruples.add(new Cuadruplo("LABEL", null, null, labelElse));
            generar(hijos.get(2));
        }

        quadruples.add(new Cuadruplo("LABEL", null, null, labelEnd));

        return "";
    }

    private String mapearOperador(String nombre) {
        if (nombre.contains("Suma")) return "+";
        if (nombre.contains("Resta")) return "-";
        if (nombre.contains("Multiplicacion")) return "*";
        if (nombre.contains("Division")) return "/";
        return nombre;
    }

    public List<Cuadruplo> getQuadruples() {
        return quadruples;
    }

    public void imprimirCuadruplos() {
        System.out.println("\n--- Tabla de Cuádruplos (Código Intermedio) ---");
        System.out.println(String.format("%-10s %-10s %-10s %-10s", "Op", "Arg1", "Arg2", "Result"));
        System.out.println("--------------------------------------------------");
        for (Cuadruplo c : quadruples) {
            System.out.println(c);
        }
        System.out.println("--------------------------------------------------\n");
    }
}
