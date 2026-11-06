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

    private static class ComandoResult {
        final int exitCode;
        final String output;
        ComandoResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private String procesarBinario(String asmCode, String nombreArchivo, String assembler, String asmFlag, String asmFormat, String linker, String linkFlag, String linkFormat, String extension) {
        if (asmCode == null || asmCode.trim().isEmpty()) {
            return "Error: El código ensamblador está vacío. Verifique que el análisis haya sido exitoso y sin errores.";
        }
        
        String trimmedAsm = asmCode.trim();
        String asmFile = nombreArchivo + ".asm";
        String objFile = nombreArchivo + ".o";
        String exeFile = nombreArchivo + extension;

        try {
            Files.write(Paths.get(asmFile), trimmedAsm.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ComandoResult resAsm = ejecutarComando(assembler, asmFlag, asmFormat, asmFile, "-o", objFile);
            if (resAsm.exitCode != 0) {
                return "Error en el ensamblador (NASM):\n" + resAsm.output;
            }

            ComandoResult resLd = ejecutarComando(linker, linkFlag, linkFormat, objFile, "-o", exeFile);
            if (resLd.exitCode != 0) {
                return "Error en el enlazador (LD):\n" + resLd.output;
            }

            new File(exeFile).setExecutable(true);
            return "Ejecutable generado exitosamente: " + exeFile;

        } catch (IOException | InterruptedException e) {
            return "Error crítico: " + e.getMessage();
        }
    }

    private ComandoResult ejecutarComando(String... comando) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = p.waitFor();
        p.destroy();
        return new ComandoResult(exitCode, output.toString());
    }
}

