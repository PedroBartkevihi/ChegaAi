import { useEffect, useState } from "react";
// true quando "ativo" continua verdadeiro por mais de "ms" milissegundos
export function useDemora(ativo, ms = 5000) {
  const [demorou, setDemorou] = useState(false);
  useEffect(() => {
    if (!ativo) {
      setDemorou(false);
      return;
    }
    const timer = setTimeout(() => setDemorou(true), ms);
    return () => clearTimeout(timer);
  }, [ativo, ms]);
  return demorou;
}
export const AVISO_SERVIDOR_ACORDANDO =
  "O servidor estava em repouso e está acordando. Na primeira visita isso pode levar cerca de 30 segundos.";
