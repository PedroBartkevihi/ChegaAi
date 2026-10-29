package chegaai.limite;

/** Reserva de uma escrita: confirmar somente depois do sucesso; fechar libera a reserva em voo. */
public final class ReservaLimite implements AutoCloseable {
    private final Runnable devolver;
    private final Runnable concluir;
    private boolean confirmada;
    private boolean fechada;
    ReservaLimite(Runnable devolver, Runnable concluir) {
        this.devolver = devolver;
        this.concluir = concluir;
    }
    public synchronized void confirmar() {
        if (confirmada) return;
        if (fechada) throw new IllegalStateException("Reserva já encerrada.");
        confirmada = true;
    }
    @Override
    public synchronized void close() {
        if (fechada) return;
        fechada = true;
        if (confirmada) concluir.run();
        else devolver.run();
    }
}
