package intermediario;

import java.util.*;

public class GeneradorEnsamblador {
    public enum TargetOS { LINUX, WINDOWS }

    private final Map<String, Integer> offsets = new HashMap<>();
    private int currentOffset = -4;
    private final String[] availableRegisters = {"eax", "ebx", "ecx", "edx"};
    private final Map<String, String> varToReg = new HashMap<>();
    private final Map<String, String> regToVar = new HashMap<>();

    private String getStackOffset(String var) {
        if (var == null || var.isEmpty()) return "";
        if (!offsets.containsKey(var)) {
            offsets.put(var, currentOffset);
            currentOffset -= 4;
        }
        return "[ebp" + offsets.get(var) + "]";
    }

    private String allocateRegister(String var, StringBuilder asm) {
        if (var == null) return null;
        if (varToReg.containsKey(var)) return varToReg.get(var);
        
        String reg = buscarRegistroLibre();
        if (reg != null) {
            asignarRegistro(reg, var, asm);
            return reg;
        }
        
        return ejecutarSpilling(var, asm);
    }

    private String buscarRegistroLibre() {
        for (String reg : availableRegisters) {
            if (!regToVar.containsKey(reg)) return reg;
        }
        return null;
    }

    private void asignarRegistro(String reg, String var, StringBuilder asm) {
        asm.append("    mov ").append(reg).append(", ").append(getStackOffset(var)).append("\n");
        varToReg.put(var, reg);
        regToVar.put(reg, var);
    }

    private String ejecutarSpilling(String var, StringBuilder asm) {
        String regToSpill = availableRegisters[0];
        String varToSpill = regToVar.get(regToSpill);
        
        asm.append("    mov ").append(getStackOffset(varToSpill)).append(", ").append(regToSpill).append("\n");
        varToReg.remove(varToSpill);
        
        asignarRegistro(regToSpill, var, asm);
        return regToSpill;
    }

    private void updateRegisterValue(String reg, String var) {
        String oldVar = regToVar.get(reg);
        if (oldVar != null) varToReg.remove(oldVar);
        regToVar.put(reg, var);
        varToReg.put(var, reg);
    }

    public String generar(List<Cuadruplo> cuads, TargetOS os) {
        StringBuilder asm = new StringBuilder();
        asm.append("; Generador x86 con Asignación de Registros\n");
        
        if (os == TargetOS.LINUX) {
            asm.append("section .text\n");
            asm.append("global _start\n\n");
            asm.append("_start:\n");
        } else {
            asm.append("section .text\n");
            asm.append("global _main\n");
            asm.append("extern ExitProcess\n\n");
            asm.append("_main:\n");
        }

        asm.append("    push ebp\n");
        asm.append("    mov ebp, esp\n");
        asm.append("    sub esp, ").append(Math.abs(currentOffset > -4 ? currentOffset : -100)).append("\n\n");

        for (Cuadruplo c : cuads) {
            traducirCuadruplo(c, asm);
        }

        asm.append("\n    mov esp, ebp\n");
        asm.append("    pop ebp\n");

        if (os == TargetOS.LINUX) {
            asm.append("    mov eax, 1\n");
            asm.append("    int 0x80\n");
        } else {
            asm.append("    push 0\n");
            asm.append("    call ExitProcess\n");
        }

        return asm.toString();
    }

    private void traducirCuadruplo(Cuadruplo c, StringBuilder asm) {
        String op = c.getOp();
        String a1 = c.getArg1();
        String a2 = c.getArg2();
        String res = c.getResult();

        switch (op) {
            case "LABEL": asm.append(res).append(":\n"); break;
            case "GOTO": asm.append("    jmp ").append(res).append("\n"); break;
            case "IF_FALSE":
                String regCond = allocateRegister(a1, asm);
                asm.append("    cmp ").append(regCond).append(", 0\n");
                asm.append("    je ").append(res).append("\n");
                break;
            case "=":
                String regVal = allocateRegister(a1, asm);
                asm.append("    mov ").append(getStackOffset(res)).append(", ").append(regVal).append("\n");
                updateRegisterValue(regVal, res);
                break;
            case "+": generarAritmetica("add", a1, a2, res, asm); break;
            case "-": generarAritmetica("sub", a1, a2, res, asm); break;
            case "*": generarAritmetica("imul", a1, a2, res, asm); break;
            case "/": generarDivision(a1, a2, res, asm); break;
            case "umins":
                String regU = allocateRegister(a1, asm);
                asm.append("    neg ").append(regU).append("\n");
                asm.append("    mov ").append(getStackOffset(res)).append(", ").append(regU).append("\n");
                updateRegisterValue(regU, res);
                break;
        }
    }

    private void generarAritmetica(String instr, String a1, String a2, String res, StringBuilder asm) {
        String r1 = allocateRegister(a1, asm);
        String r2 = allocateRegister(a2, asm);
        if (r1.equals(r2)) {
            asm.append("    push ").append(r1).append("\n");
            r2 = allocateRegister(a2, asm);
            asm.append("    pop ").append(r1).append("\n");
        }
        asm.append("    mov eax, ").append(r1).append("\n");
        asm.append("    ").append(instr).append(" eax, ").append(r2).append("\n");
        asm.append("    mov ").append(getStackOffset(res)).append(", eax\n");
        updateRegisterValue("eax", res);
    }

    private void generarDivision(String a1, String a2, String res, StringBuilder asm) {
        String r1 = allocateRegister(a1, asm);
        String r2 = allocateRegister(a2, asm);
        asm.append("    mov eax, ").append(r1).append("\n");
        asm.append("    cdq\n");
        asm.append("    idiv ").append(getStackOffset(r2)).append("\n");
        asm.append("    mov ").append(getStackOffset(res)).append(", eax\n");
        updateRegisterValue("eax", res);
    }
}
