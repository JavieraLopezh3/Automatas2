# 🚀 Analizador y Compilador de Lenguaje (Sintáctico, Semántico y Generador de Código)

Este proyecto es la implementación de un compilador completo basado en los principios teóricos del libro **"Compiladores: principios, técnicas y herramientas"** (Segunda Edición) de **Alfredo V. Aho, Monica S. Lam, Ravi Sethi y Jeffrey D. Ullman** (conocido como el *Libro del Dragón*).

El objetivo es transformar un código fuente en un binario ejecutable para la arquitectura **x86**, pasando por todas las fases críticas de la construcción de un compilador.

## 🛠️ Pipeline de Compilación

El proyecto implementa un flujo de trabajo completo dividido en tres etapas principales:

### 1. Frontend (Análisis)
- **Análisis Léxico:** Tokenización del código fuente utilizando un analizador léxico.
- **Análisis Sintáctico:** Construcción de un **AST (Árbol de Sintaxis Abstracta)** mediante análisis descendente y ascendente.
- **Análisis Semántico:** Verificación de la congruencia del código, tipos de datos y resolución de símbolos.

### 2. Middle-end (Optimización)
- **Código Intermedio:** Generación de una representación en **Cuádruplos** ($\text{Op} \mid \text{Arg1} \mid \text{Arg2} \mid \text{Resultado}$).
- **Optimización Independiente de la Máquina:**
    - **Constant Folding:** Evaluación de expresiones constantes en tiempo de compilación.
    - **CSE (Common Subexpression Elimination):** Detección y reutilización de cálculos repetidos.
    - **Dead Code Elimination:** Eliminación de instrucciones cuyos resultados nunca se utilizan.

### 3. Backend (Generación de Código)
- **Asignación de Registros:** Gestión eficiente del uso de registros del CPU (`EAX`, `EBX`, `ECX`, `EDX`) con estrategia de **Spilling** (desalojo a la pila) para optimizar el rendimiento.
- **Generación de Ensamblador:** Traducción de cuádruplos optimizados a mnemónicos de **x86 (NASM)**.
- **Generación de Binario:** Integración con herramientas externas (`nasm` y `ld`) para crear ejecutables reales (`.exe` en Windows, binarios ELF en Linux).

## 💻 Requisitos del Sistema

- **Lenguaje:** Java 17
- **Gestor de Dependencias:** Maven
- **Arquitectura Objetivo:** x86 (32 bits)
- **Herramientas de Ensamblado:** NASM, Binutils (LD)
- **Interfaz Gráfica:** Java Swing

Para ejecutar el proyecto y generar binarios, es necesario tener instalado un **JDK 17** y las siguientes herramientas según el sistema operativo:

### 🐧 En Linux (Ubuntu/Debian/Arch)
Para generar ejecutables de Linux:
```bash
sudo pacman -S nasm binutils  # En Arch Linux
# o
sudo apt-get install nasm binutils # En Ubuntu/Debian
```

### 🪟 En Windows
Para generar ejecutables de Windows (`.exe`):
1. **NASM:** Descargar e instalar desde [nasm.us](https://www.nasm.us/). Agregar la carpeta de instalación al `PATH` del sistema.
2. **MinGW-w64:** Instalar la cadena de herramientas de GCC/Binutils para Windows (vía MSYS2 o standalone). Asegurarse de que `ld.exe` esté disponible en el `PATH`.

## 🚀 Instalación y Ejecución

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/JavieraLopezh3/Automatas2.git
   cd Automatas2
   ```

2. **Compilar el proyecto con Maven:**
   ```bash
   mvn compile
   ```

3. **Ejecutar la aplicación:**
   ```bash
   java -cp target/classes principal.Principal
   ```

## 📖 Guía de Uso
1. **Carga un archivo** de código fuente o escribe uno nuevo en el editor.
2. Pulsa el botón **Analizar** para ejecutar todo el pipeline (Léxico $\rightarrow$ Sintáctico $\rightarrow$ Semántico $\rightarrow$ Optimización).
3. Revisa la pestaña **Ensamblador** para ver el código x86 generado.
4. Selecciona el Sistema Operativo (**Linux** o **Windows**) en el selector de la barra superior.
5. Pulsa **Ejecutar** para generar el binario ejecutable en la carpeta del proyecto.

## 📚 Referencias
- Aho, A. V., Lam, M. S., Sethi, RB., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools*. Pearson Education.
