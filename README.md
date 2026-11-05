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
- **Generación de Binario:** Integración con `nasm` y `ld` para generar ejecutables reales para **Linux (ELF)** y **Windows (PE)**.

## 💻 Tecnologías Utilizadas

- **Lenguaje:** Java 17
- **Gestor de Dependencias:** Maven
- **Arquitectura Objetivo:** x86 (32 bits)
- **Herramientas de Ensamblado:** NASM, Binutils (LD)
- **Interfaz Gráfica:** Java Swing

## 🚀 Instalación y Ejecución

### Requisitos Previos
Para ejecutar el proyecto y generar binarios en Linux, necesitas instalar:
```bash
sudo pacman -S nasm binutils
```

### Compilación y Ejecución
1. Clona el repositorio:
   ```bash
   git clone https://github.com/JavieraLopezh3/Automatas2.git
   cd Automatas2
   ```
2. Compila el proyecto con Maven:
   ```bash
   mvn compile
   ```
3. Ejecuta la aplicación:
   ```bash
   java -cp target/classes principal.Principal
   ```

## 📖 Cómo usar el Analizador
1. **Carga un archivo** de código fuente o escribe uno nuevo.
2. Pulsa el botón **Analizar** para generar el AST, analizar la semántica y obtener los cuádruplos optimizados.
3. Revisa la pestaña **Ensamblador** para ver la traducción a x86.
4. Selecciona el Sistema Operativo (**Linux** o **Windows**) y pulsa **Ejecutar** para generar el binario ejecutable.

