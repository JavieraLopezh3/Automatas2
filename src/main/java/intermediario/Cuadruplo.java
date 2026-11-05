package intermediario;

/**
 * Representa un cuádruplo de código intermedio.
 * Formato: op arg1 arg2 result
 */
public class Cuadruplo {
    private final String op;
    private final String arg1;
    private final String arg2;
    private final String result;

    public Cuadruplo(String op, String arg1, String arg2, String result) {
        this.op = op;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.result = result;
    }

    public String getOp() { return op; }
    public String getArg1() { return arg1; }
    public String getArg2() { return arg2; }
    public String getResult() { return result; }

    @Override
    public String toString() {
        return String.format("%-10s %-10s %-10s %-10s", op, 
            arg1 == null ? "" : arg1, 
            arg2 == null ? "" : arg2, 
            result == null ? "" : result);
    }
}
