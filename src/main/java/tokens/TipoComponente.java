package tokens;

public enum TipoComponente {
    SI(1, "IF", CategoriaComponente.PALABRA_RESERVADA, "Sentencia condicional estandar"),
    IMPORTAR(2, "IMPORT", CategoriaComponente.PALABRA_RESERVADA, "Importacion de modulos externos"),
    PUBLICO(3, "PUBLIC", CategoriaComponente.PALABRA_RESERVADA, "Modificador de acceso publico"),
    PRIVADO(4, "PRIVATE", CategoriaComponente.PALABRA_RESERVADA, "Modificador de acceso privado"),
    CLASE(5, "CLASS", CategoriaComponente.PALABRA_RESERVADA, "Declaracion de clase"),
    VACIO(6, "VOID", CategoriaComponente.PALABRA_RESERVADA, "Tipo de retorno sin valor"),
    TIPO_ENTERO(7, "INT", CategoriaComponente.PALABRA_RESERVADA, "Tipo de dato entero"),
    TIPO_FLOTANTE(8, "FLOAT", CategoriaComponente.PALABRA_RESERVADA, "Tipo de dato flotante"),
    TIPO_CADENA(9, "STRING", CategoriaComponente.PALABRA_RESERVADA, "Tipo de dato cadena"),
    TIPO_CARACTER(10, "CHAR", CategoriaComponente.PALABRA_RESERVADA, "Tipo de dato caracter"),
    TIPO_BOOLEANO(11, "BOOLEAN", CategoriaComponente.PALABRA_RESERVADA, "Tipo de dato booleano"),
    SINO(12, "ELSE", CategoriaComponente.PALABRA_RESERVADA, "Rama alternativa del if"),
    EXISTE(13, "EXIST", CategoriaComponente.PALABRA_RESERVADA, "Condicional personalizado"),
    REPETIR(14, "REPEAT", CategoriaComponente.PALABRA_RESERVADA, "Ciclo repeat"),
    MIENTRAS(15, "WHILE", CategoriaComponente.PALABRA_RESERVADA, "Ciclo while"),
    CADA(16, "EACH", CategoriaComponente.PALABRA_RESERVADA, "Iterador para repeat each"),
    RETORNAR(17, "RETURN", CategoriaComponente.PALABRA_RESERVADA, "Retorno de valor en funcion"),
    THIS(18, "THIS", CategoriaComponente.PALABRA_RESERVADA, "Referencia al objeto actual"),
    FOR(19, "FOR", CategoriaComponente.PALABRA_RESERVADA, "Ciclo for"),
    NEW(51, "NEW", CategoriaComponente.PALABRA_RESERVADA, "Creacion de nuevos arreglos o objetos"),

    IDENTIFICADOR(100, "ID", CategoriaComponente.IDENTIFICADOR, "Nombre de variable, clase o metodo"),
    NUMERO_ENTERO(101, "NUM_INT", CategoriaComponente.LITERAL, "Numero entero sin decimales"),
    NUMERO_FLOTANTE(102, "NUM_FLOAT", CategoriaComponente.LITERAL, "Numero con punto decimal"),
    LITERAL_CADENA(103, "CADENA", CategoriaComponente.LITERAL, "Texto entre comillas dobles"),
    LITERAL_CARACTER(104, "CARACTER", CategoriaComponente.LITERAL, "Caracter entre comillas simples"),

    SUMA(20, "SUMA", CategoriaComponente.OPERADOR, "Suma de valores numericos"),
    RESTA(21, "RESTA", CategoriaComponente.OPERADOR, "Resta de valores numericos"),
    MULTIPLICACION(22, "MULT", CategoriaComponente.OPERADOR, "Multiplicacion"),
    DIVISION(23, "DIV", CategoriaComponente.OPERADOR, "Division"),
    MODULO(24, "MOD", CategoriaComponente.OPERADOR, "Modulo"),
    ASIGNACION(25, "ASIG", CategoriaComponente.OPERADOR, "Asignacion de valor"),
    IGUALDAD(26, "IGUAL", CategoriaComponente.OPERADOR, "Comparacion de igualdad"),
    DIFERENTE(27, "DIFER", CategoriaComponente.OPERADOR, "Comparacion de diferencia"),
    MENOR_QUE(28, "MENOR", CategoriaComponente.OPERADOR, "Comparacion menor que"),
    MENOR_O_IGUAL(29, "MEN_IG", CategoriaComponente.OPERADOR, "Comparacion menor o igual que"),
    MAYOR_QUE(30, "MAYOR", CategoriaComponente.OPERADOR, "Comparacion mayor que"),
    MAYOR_O_IGUAL(31, "MAY_IG", CategoriaComponente.OPERADOR, "Comparacion mayor o igual que"),
    Y_LOGICO(32, "AND", CategoriaComponente.OPERADOR, "Conjuncion logica"),
    O_LOGICO(33, "OR", CategoriaComponente.OPERADOR, "Disyuncion logica"),
    NEGACION(34, "NOT", CategoriaComponente.OPERADOR, "Negacion logica"),
    INCREMENTO(35, "INC", CategoriaComponente.OPERADOR, "Incremento de valor"),
    DECREMENTO(36, "DEC", CategoriaComponente.OPERADOR, "Decremento de valor"),

    LLAVE_ABRE(40, "LLAVE_A", CategoriaComponente.DELIMITADOR, "Apertura de bloque"),
    LLAVE_CIERRA(41, "LLAVE_C", CategoriaComponente.DELIMITADOR, "Cierre de bloque"),
    PARENTESIS_ABRE(42, "PAR_A", CategoriaComponente.DELIMITADOR, "Apertura de parentesis"),
    PARENTESIS_CIERRA(43, "PAR_C", CategoriaComponente.DELIMITADOR, "Cierre de parentesis"),
    CORCHETE_ABRE(44, "COR_A", CategoriaComponente.DELIMITADOR, "Apertura de corchete"),
    CORCHETE_CIERRA(45, "COR_C", CategoriaComponente.DELIMITADOR, "Cierre de corchete"),
    PUNTO_Y_COMA(46, "PUNTOCOMA", CategoriaComponente.DELIMITADOR, "Fin de sentencia"),
    COMA(47, "COMA", CategoriaComponente.DELIMITADOR, "Separador de argumentos"),
    PUNTO(48, "PUNTO", CategoriaComponente.DELIMITADOR, "Acceso a miembro o punto decimal"),
    COMENTARIO(49, "COMENTARIO", CategoriaComponente.COMENTARIO, "Comentario ignorado por el analizador"),
    DOS_PUNTOS(50, "DOS_PUNTOS", CategoriaComponente.DELIMITADOR, "Separador usado en repeat each");

    private final int codigo;
    private final String nombreVisible;
    private final CategoriaComponente categoria;
    private final String descripcion;

    TipoComponente(int codigo, String nombreVisible, CategoriaComponente categoria, String descripcion) {
        this.codigo = codigo;
        this.nombreVisible = nombreVisible;
        this.categoria = categoria;
        this.descripcion = descripcion;
    }
    
    public int getCodigo() {
        return codigo;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public CategoriaComponente getCategoria() {
        return categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
