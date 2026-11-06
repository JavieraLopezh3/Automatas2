package intermediario;

import java.io.*;
import java.nio.file.*;

/**
 * Clase encargada de transformar el código ensamblador en un binario ejecutable.
 * Soporta rutas configurables para herramientas externas (NASM, LD),
 * permitiendo la distribución portable.
 */
public class GeneradorEjecutable {
    private String nasmPath = "nasm";
    private String ldPath = "ld";
    private final boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

    public void configurarRutas(String rutaBinarios) {
        if (rutaBinarios != null && !rutaBinarios.isEmpty()) {
            this.nasmPath = rutaBinarios + (isWindows ? "\\" : "/") + "nasm" + (isWindows ? ".exe" : "");
            this.ldPath = rutaBinarios + (isWindows ? "\\" : "/") + "ld" + (isWindows ? ".exe" : "");
        }
    }

    public String generarEjecutableLinux(String asmCode, String nombreArchivo) {
        return procesarBinario(asmCode, nombreArchivo, nasmPath, "-f", "elf32", ldPath, "-m", "elf_i386", ".exe");
    }

    public String generarEjecutableWindows(String asmCode, String nombreArchivo) {
        return procesarBinario(asmCode, nombreArchivo, nasmPath, "-f", "win32", ldPath, "-m", "i386pe", ".exe");
    }

    private String procesarBinario(String asmCode, String nombreArchivo, String assembler, String asmFlag, String asmFormat, String linker, String linkFlag, String linkFormat, String extension) {
        String asmFile = nombreArchivo + ".asm";
        String objFile = nombreArchivo + ".o";
        String exeFile = nombreArchivo + extension;

        try {
            Files.write(Paths.get(asmFile), asmCode.getBytes());

            if (!ejecutarComando(assembler, asmFlag, asmFormat, asmFile, "-o", objFile)) {
                return "Error en el ensamblador (" + assembler + "). Verifique la instalación.";
            }

            if (!ejecutarComando(linker, linkFlag, linkFormat, objFile, "-o", exeFile)) {
                return "Error en el enlazador (" + linker + "). Verifique la instalación.";
            }

            new File(exeFile).setExecutable(true);
            return "Ejecutable generado exitosamente: " + exeFile;

        } catch (IOException | InterruptedException e) {
            return "Error crítico: " + e.getMessage();
        }
    }

    private boolean ejecutarComando(String... comando) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando);
        return pb.start().waitFor() == 0;
    }
}
