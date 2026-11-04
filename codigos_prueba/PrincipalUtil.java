{
import Util;
public class PrincipalUtil {
    int matriz[][];
    float[] datos;

    public int usarDespues(int n) { return definidaDespues(n) + 1; }
    public int definidaDespues(int n) { return n * 2; }

    public void todo() {
        matriz = new int[3][3];
        matriz[1][2] = -5;
        int v = matriz[0][0] + (-3) * 2;
        datos = new float[4];
        datos[0] = 3;
        datos[1] = v;
        float f = Util.abs(-2.5);
        int g = Util.abs(-4);
        Util.saludar();
        char c = 'a';
        int cod = c + 1;
        boolean mismo = c == 'b';
        string s = "x" + 1 + 2.5 + 'c' + true;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matriz[i][j] = i * j;
            }
        }
        int k = 10;
        repeat (while k > 0) { k = k - 1; }
        if (k == 0 || (v > 1 && !mismo)) { print(s); }
    }
}
}
