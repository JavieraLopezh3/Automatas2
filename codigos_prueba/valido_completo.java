{
import java.util.Scanner;

public class Calculadora {
    int total = 0;
    float promedio;
    string nombre = "calc";
    float [] datos;
    boolean activo = true;

    public Calculadora(string nombre) {
        this.nombre = nombre;
        datos = new float[10];
    }

    public int sumar(int a, int b) {
        return a + b;
    }

    public float dividir(float a, float b) {
        if (b == 0) {
            return 0;
        } else if (b < 0) {
            return a / -b;
        } else {
            return a / b;
        }
    }

    public boolean esPar(int n) {
        return n % 2 == 0;
    }

    public void llenar(int cantidad) {
        for (int i = 0; i < cantidad; i = i + 1) {
            datos[i] = i * 1.5;
            total++;
        }
        int j = 0;
        while (j < cantidad && activo) {
            j++;
        }
        repeat (while j > 0) {
            j--;
        }
    }

    public float media() {
        float suma = 0;
        for (int k = 0; k < datos.length; k++) {
            suma = suma + datos[k];
        }
        promedio = suma / total;
        return promedio;
    }

    public string describir() {
        string s = "Total: " + total + " nombre " + nombre;
        print(s);
        return s;
    }
}

public void main() {
    int x = 5;
    float y = x;
    char c = 'a';
    int codigo = c;
    boolean ok = x > 3 && y < 10.5 || !(x == 2);
    Calculadora.sumar(x, 3);
    string t = "abc";
    if (t.equals("abc") && t.length() > 2) {
        print(t.charAt(0));
    }
}
}
