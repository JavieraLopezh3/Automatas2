package intermediario;

import java.io.*;
import java.nio.file.*;

/**
 * Clase encargada de transformar el código ensamblador en un binario ejecutable.
 * Soporta generación para Linux (ELF) y Windows (PE).
 */
public class GeneradorEjecutable {

    /**
     * Genera el ejecutable para Linux.
     */
    public String generarEjecutableLinux(String asmCode, String nombreArchivo) {
        return procesarBinario(asmCode, nombreArchivo, "nasm", "-f", "elf32", "ld", "-m", "elf_i386", ".exe");
    }

    /**
     * Genera el ejecutable para Windows.
     * Nota: Requiere que el sistema tenga instalado nasm y el enlazador de MinGW (i686-w64-mingw32-ld).
     */
    public String generarEjecutableWindows(String asmCode, String nombreArchivo) {
        // En Windows se usa el formato win32 y un linker compatible con PE
        return procesarBinario(asmCode, nombreArchivo, "nasm", "-f", "win32", "i686-w64-mingw32-ld", "-m", "i386pe", ".exe");
    }

    private String procesarBinario(String asmCode, String nombreArchivo, String assembler, String asmFlag, String asmFormat, String linker, String linkFlag, String linkFormat, String extension) {
        String asmFile = nombreArchivo + ".asm";
        String objFile = nombreArchivo + ".o";
        String exeFile = nombreArchivo + extension;

        try {
            // 1. Guardar el código ensamblador
            Files.write(Paths.get(asmFile), asmCode.getBytes());

            // 2. Ensamblar
            ProcessBuilder pbAsm = new ProcessBuilder(assembler, asmFlag, asmFormat, asmFile, "-o", objFile);
            if (pbAsm.start().waitFor() != 0) {
                return "Error en el ensamblador (" + assembler + "). Verifique la instalación.";
            }

            // 3. Enlazar
            ProcessBuilder pbLd = new ProcessBuilder(linker, linkFlag, linkFormat, objFile, "-o", exeFile);
            if (pbLd.start().waitFor() != 0) {
                return "Error en el enlazador (" + linker + "). Verifique la instalación de MinGW para Windows.";
            }

            File file = new File(exeFile);
            file.setExecutable(true);

            return "Ejecutable generado exitosamente: " + exeFile;

        } catch (IOException | InterruptedException e) {
            return "Error crítico: " + e.getMessage();
        }
    }
}
