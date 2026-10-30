package chegaai.limite;

/** Tempo monotônico, separado do relógio das regras de negócio. */
@FunctionalInterface
public interface RelogioLimites {
    long agoraNanos();
}
