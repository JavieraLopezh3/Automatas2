# Análisis y correcciones — Lenguajes y Autómatas II

## Qué se agregó (lo que pediste)

| Pedido | Cómo quedó |
|---|---|
| Leer **de arriba hacia abajo** | Analizador **descendente** (descenso recursivo, usa FIRST/FOLLOW). Construye el árbol de la raíz hacia las hojas. |
| Leer **de abajo hacia arriba** ("viceversa") | Analizador **ascendente** *shift-reduce* con tabla **SLR(1)** generada automáticamente desde la gramática (`GramaticaLR.java`: 125 producciones, 247 estados, **0 conflictos**). Construye el árbol de las hojas hacia la raíz y muestra la traza de desplazamientos/reducciones. |
| **Dar los errores** | Errores léxicos, sintácticos (de cada dirección) y semánticos, con línea y columna. Se muestra qué dirección lo detectó. |
| **Congruencia** | Nuevo **analizador semántico** (`src/semantico/`): variables/clases/métodos declarados, duplicados, tipos en asignaciones y operadores, condiciones boolean, llamadas (nombre, cantidad y tipo de argumentos), retornos, arreglos, imports entre archivos, código inalcanzable. Pestaña **⚖ Congruencia** con veredicto por archivo. |

En la ventana hay un selector **Lectura**: `↓ descendente`, `↑ ascendente` o `⇅ ambos y comparar`.
Si los dos analizadores no coinciden en si un archivo es válido, la pestaña Congruencia lo marca ("No — revisar").

## Errores que tenía el proyecto y se corrigieron

1. **`TipoComponente`**: `NEW` y `SUMA` tenían el mismo código (20). `NEW` ahora es 51.
2. **Léxico – falsos positivos**: rechazaba expresiones válidas como `a / -b`, `x || !y`, `= this`, y marcaba `charAt`, `intentos`, etc. como "token pegado". Corregido (los errores reales como `intx = 5` o `5 + * 3` se siguen detectando).
3. **Parser – un solo programa**: solo leía el primer `{ ... }`; ahora lee todos los de un archivo (como `Impuestos.java`, que tiene 2 programas).
4. **Parser – `;` obligatorios**: faltaba exigir `;` en atributos y en el encabezado del `for`; ahora se reporta.
5. **Parser – `else if`**, **asignaciones a `this.campo = ...` / `obj.campo[i] = ...`**, **unarios después de operador** (`a / -b`) no se soportaban.
6. **Recuperación de errores**: un solo error causaba una cascada de errores falsos. Ahora hay recuperación por delimitadores y se evitan duplicados.
7. **Errores de fin de archivo** salían en la línea `-1`; ahora apuntan al último token.
8. **`FIRST(Sentencia)`** en `Gramatica.java` no coincidía con lo que el parser aceptaba.

## Cosas a tener en cuenta

- El léxico **no tiene `true`/`false` como palabras reservadas**; el semántico los trata como booleanos.
- El semántico solo se ejecuta si el archivo no tiene errores léxicos ni sintácticos (si no, el árbol sería poco confiable). Dice "NO EVALUADO" y por qué.
- `print`, y los métodos de `string`/arreglos (`length`, `charAt`, `exists`...) son predefinidos. Clases importadas con punto (`import java.util.Scanner;`) se consideran externas y no se verifican.
- `Impuestos.java` (tu ejemplo) sale **NO CONGRUENTE** a propósito: usa `monto` sin declarar, multiplica un arreglo y `AgregarImpuesto` no retorna en todos los caminos.
- Se agregaron dos archivos de prueba en `codigos_prueba/`: `valido_completo.java` (debe salir CONGRUENTE) y `errores_semanticos.java` (27 errores a propósito).

## Cómo ejecutar
Abre la carpeta como proyecto en NetBeans y pulsa Run (F6). Requiere JDK 17+.
(Nota: este documento mencionaba `compilar.sh` / `compilar.bat`, pero esos archivos no están en el proyecto.)

## Archivos nuevos
`sintactico/GramaticaLR.java`, `AnalizadorAscendente.java`, `ResultadoAscendente.java`, y todo `semantico/`.

## Interfaz (rediseño)
Código en `src/interfaz/`: `PanelAnalizador` (pantalla completa), `EditorCodigo` (coloreado en vivo con el propio
analizador léxico, números de línea, marcas de error), `TablaResultados`, `ArbolAst` (árbol navegable),
`Segmentado`, `Boton`, `PestanasUI`, `EstadoVacio` y `Tema` (colores y tipografías).
Solo hay cuatro pestañas de resultados: Árbol, Tokens, Errores léxicos y Errores sintácticos (las incongruencias
semánticas aparecen en esta última, con origen «Incongruencia»).

## Árbol semántico (nuevo)
Pestaña **Árbol semántico** (junto a «Árbol»). Es el programa ya *decorado* por el análisis semántico:

- Cada nodo muestra su **tipo deducido** (`: int`, `: float[]`, `: boolean`...) y una nota (variable, atributo, literal, parámetro, llamada...).
- Los **operadores** se organizan por precedencia y muestran el tipo resultante (`a * 1.5` → `float`).
- Las **asignaciones** muestran tipo del destino y del valor, e indican las conversiones implícitas (`int → float`).
- Cada bloque indica su **ámbito** (las variables que declara). Los `return` indican el tipo que el método espera.
- Los **errores y avisos cuelgan del nodo exacto** donde ocurren (✖ en rojo, ⚠ en ámbar), y los nodos padre muestran cuántos errores contienen.
- Al final se incluye la **tabla de símbolos** del archivo.
- Clic en un nodo salta a su línea en el editor; «Ir a errores» despliega los caminos hasta cada problema.

Código: `semantico/NodoSemantico.java` (nodo), `AnalizadorSemantico.java` (anota tipos al evaluar y construye el árbol;
no cambia las reglas de congruencia), `ResultadoSemantico.getArbol()` e `interfaz/ArbolSemantico.java` (pestaña).
Las pestañas de resultados quedan: Árbol, **Árbol semántico**, Tokens, Errores léxicos, Errores sintácticos.
Si el archivo tiene errores léxicos o sintácticos el árbol semántico no se construye (se explica el motivo).
