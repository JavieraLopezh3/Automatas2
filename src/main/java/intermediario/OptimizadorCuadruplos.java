package intermediario;

import java.util.*;

/**
 * Optimizador de código intermedio.
 * Implementa técnicas de optimización independiente de la máquina
 * basadas en el Capítulo 6 y 7 de Aho, Lam, Sethi y Ullman.
 */
public class OptimizadorCuadruplos {

    /**
     * Aplica una serie de pasadas de optimización a la lista de cuádruplos.
     */
    public List<Cuadruplo> optimizar(List<Cuadruplo> originales) {
        List<Cuadruplo> actual = nuevosCuadruplos(originales);
        
        // Pasada 1: Evaluación de Constantes (Constant Folding)
        actual = evaluarConstantes(actual);
        
        // Pasada 2: Eliminación de Subexpresiones Comunes (CSE)
        actual = eliminarSubexpresionesComunes(actual);
        
        // Pasada 3: Eliminación de Código Muerto (Básica)
        actual = eliminarCodigoMuerto(actual);
        
        return actual;
    }

    private List<Cuadruplo> nuevosCuadruplos(List<Cuadruplo> lista) {
        return new ArrayList<>(lista);
    }

    /**
     * Optimización: Constant Folding
     * Si una operación tiene dos operandos constantes, se calcula el resultado en tiempo de compilación.
     */
    private List<Cuadruplo> evaluarConstantes(List<Cuadruplo> cuads) {
        List<Cuadruplo> optimizados = new ArrayList<>();
        Map<String, String> constantes = new HashMap<>();

        for (Cuadruplo c : cuads) {
            String op = c.getOp();
            String a1 = c.getArg1();
            String a2 = c.getArg2();
            String res = c.getResult();

            // Sustituir operandos por constantes ya calculadas
            if (constantes.containsKey(a1)) a1 = constantes.get(a1);
            if (constantes.containsKey(a2)) a2 = constantes.get(a2);

            if (esNumero(a1) && esNumero(a2) && (op != null)) {
                try {
                    double v1 = Double.parseDouble(a1);
                    double v2 = Double.parseDouble(a2);
                    double resultado = 0;

                    switch (op) {
                        case "+": resultado = v1 + v2; break;
                        case "-": resultado = v1 - v2; break;
                        case "*": resultado = v1 * v2; break;
                        case "/": resultado = v1 / v2; break;
                        default: 
                            optimizados.add(new Cuadruplo(op, a1, a2, res));
                            continue;
                    }
                    
                    String resStr = String.valueOf(resultado);
                    constantes.put(res, resStr);
                    // Convertimos la operación en una asignación simple de constante
                    optimizados.add(new Cuadruplo("=", resStr, null, res));
                } catch (NumberFormatException e) {
                    optimizados.add(new Cuadruplo(op, a1, a2, res));
                }
            } else {
                optimizados.add(new Cuadruplo(op, a1, a2, res));
            }
        }
        return optimizados;
    }

    /**
     * Optimización: Common Subexpression Elimination (CSE)
     * Detecta expresiones idénticas y reutiliza el resultado anterior.
     */
    private List<Cuadruplo> eliminarSubexpresionesComunes(List<Cuadruplo> cuads) {
        List<Cuadruplo> optimizados = new ArrayList<>();
        // Mapa de (op + arg1 + arg2) -> resultado_temporal
        Map<String, String> expresiones = new HashMap<>();

        for (Cuadruplo c : cuads) {
            String op = c.getOp();
            String a1 = c.getArg1();
            String a2 = c.getArg2();
            String res = c.getResult();

            if (op != null && !op.equals("=") && !op.equals("LABEL") && !op.equals("GOTO") && !op.equals("IF_FALSE")) {
                String llave = op + "|" + a1 + "|" + a2;
                if (expresiones.containsKey(llave)) {
                    // Ya calculamos esto, sustituimos la operación por una asignación del temporal previo
                    optimizados.add(new Cuadruplo("=", expresiones.get(llave), null, res));
                    continue;
                }
                expresiones.put(llave, res);
            }
            optimizados.add(c);
        }
        return optimizados;
    }

    /**
     * Optimización: Dead Code Elimination (Básica)
     * Elimina asignaciones a temporales que nunca son leídos posteriormente.
     */
    private List<Cuadruplo> eliminarCodigoMuerto(List<Cuadruplo> cuads) {
        Set<String> usados = new HashSet<>();
        
        // Primera pasada: Identificar qué temporales se usan como argumentos
        for (Cuadruplo c : cuads) {
            if (c.getArg1() != null) usados.add(c.getArg1());
            if (c.getArg2() != null) usados.add(c.getArg2());
        }

        List<Cuadruplo> optimizados = new ArrayList<>();
        for (Cuadruplo c : cuads) {
            String res = c.getResult();
            // Si el resultado es un temporal y nunca se usa, eliminamos la instrucción
            // (Solo si no es una etiqueta o instrucción de control)
            if (res != null && res.startsWith("t") && !usados.contains(res)) {
                if (!c.getOp().equals("LABEL") && !c.getOp().equals("GOTO") && !c.getOp().equals("IF_FALSE")) {
                    continue; 
                }
            }
            optimizados.add(c);
        }
        return optimizados;
    }

    private boolean esNumero(String s) {
        if (s == null) return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
