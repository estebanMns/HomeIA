import type { Metadata } from 'next';
import LoginBrand from '@/components/auth/LoginBrand';
import RegisterForm from '@/components/auth/RegisterForm';

export const metadata: Metadata = { title: 'Crear cuenta · HomeIA' };

/** Server Component: panel de marca renderizado en el servidor; solo el formulario es de cliente. */
export default function RegisterPage() {
  return (
    <div className="login-page">
      <LoginBrand variant="register" />
      <RegisterForm />
    </div>
  );
}
