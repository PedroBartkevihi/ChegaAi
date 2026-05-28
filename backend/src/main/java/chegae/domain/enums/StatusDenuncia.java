package chegae.domain.enums;

/**
 * Status do ciclo de vida de uma denúncia no painel de moderação.
 *
 * <p>Mapeado pela constraint {@code ck_denuncia_status} do DDL:
 * {@code CHECK (status_denuncia IN ('ABERTA','EM_ANALISE','VALIDADA','REJEITADA','ENCERRADA'))}</p>
 *
 * <p>Diagrama de estados (UC13):</p>
 * <pre>
 *  [ABERTA] ──(moderador seleciona)──► [EM_ANALISE]
 *  [EM_ANALISE] ──(procedente)───────► [VALIDADA]    → conta +1 denúncia ao denunciado
 *  [EM_ANALISE] ──(improcedente)─────► [REJEITADA]
 *  [VALIDADA]   ──(penalidade criada)► [ENCERRADA]
 *  [REJEITADA]  ──(arquivada)────────► [ENCERRADA]
 * </pre>
 */
public enum StatusDenuncia {

    /** Denúncia registrada pelo usuário, aguardando análise do moderador. */
    ABERTA,

    /** Moderador abriu a análise da denúncia (UC13 — passo 3). */
    EM_ANALISE,

    /**
     * Denúncia considerada procedente pelo moderador.
     * Incrementa {@code qtdDenunciasValidadas} do denunciado (RN005).
     */
    VALIDADA,

    /** Denúncia considerada improcedente (spam ou sem fundamento). */
    REJEITADA,

    /**
     * Denúncia encerrada após decisão final (penalidade aplicada
     * ou arquivada sem punição).
     */
    ENCERRADA
}
