import { Sparkles, TrendingDown } from 'lucide-react';
import { Badge, Logo } from '@/components/ui/Primitives';

/**
 * Server Component (sin 'use client'): panel de marca de las pantallas de ingreso y registro.
 * No tiene estado ni eventos, así que se renderiza en el servidor y llega como HTML; su código
 * no se envía al navegador. Logo y Badge son Client Components que se incrustan como islas.
 */
export default function LoginBrand({ variant }: { variant: 'login' | 'register' }) {
  if (variant === 'register') {
    return (
      <div className="login-brand">
        <Logo />
        <div className="login-message">
          <div>
            Crea tu
            <br />
            cuenta <em>en HomeIA.</em>
          </div>
          <p>Tu casa queda lista con valores por defecto. Después puedes ajustar habitaciones, dispositivos y tarifa.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="login-brand">
      <Logo />
      <div className="login-message">
        <Badge tone="blue">
          <Sparkles size={12} /> Energía inteligente
        </Badge>
        <div>
          Tu casa ahorra
          <br />
          energía <em>sola.</em>
        </div>
        <p>HomeIA entiende cómo vives, optimiza cada espacio y reduce tu consumo sin que tengas que pensarlo.</p>
      </div>
      <div className="login-stat">
        <TrendingDown size={24} />
        <div>
          <strong>Automatización activa</strong>
          <span>apagado inteligente de habitaciones vacías</span>
        </div>
      </div>
    </div>
  );
}
