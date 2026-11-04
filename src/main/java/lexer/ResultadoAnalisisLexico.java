package lexer;

import java.util.Collections;
import java.util.List;
import tokens.ComponenteLexico;

public final class ResultadoAnalisisLexico {
    private final List<ComponenteLexico> componentes;
    private final List<ErrorLexico> errores;

    public ResultadoAnalisisLexico(List<ComponenteLexico> componentes, List<ErrorLexico> errores) {
        this.componentes = List.copyOf(componentes);
        this.errores = List.copyOf(errores);
    }

    public List<ComponenteLexico> getComponentes() {
        return Collections.unmodifiableList(componentes);
    }

    public List<ErrorLexico> getErrores() {
        return Collections.unmodifiableList(errores);
    }

    public long getCantidadIgnorados() {
        return componentes.stream().filter(ComponenteLexico::seIgnora).count();
    }
}
