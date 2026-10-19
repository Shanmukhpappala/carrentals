import { Link, Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './authContext'

export default function ProtectedRoute({ role, children }) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'signingOut') return null

  if (auth.status === 'loading') {
    return <section className="workspace-panel" role="status">Restoring your session...</section>
  }

  if (auth.status === 'error') {
    return (
      <section className="workspace-panel" role="alert">
        <h1>Unable to restore your session</h1>
        <p>{auth.error || 'The rental service could not verify your session.'}</p>
        <button className="button button-primary" type="button" onClick={auth.retryRestore}>Try again</button>
      </section>
    )
  }

  if (auth.status !== 'authenticated') {
    const loginPath = role === 'ADMIN' ? '/admin/login' : '/login'
    return <Navigate replace to={loginPath} state={{ from: location }} />
  }

  if (auth.user?.role !== role) {
    return (
      <section className="workspace-panel" role="alert">
        <p className="eyebrow">ACCESS RESTRICTED</p>
        <h1>This area is not available to your account.</h1>
        <p>Your account does not have the required {role.toLowerCase()} role.</p>
        <Link className="button button-primary" to={auth.user?.role === 'ADMIN' ? '/admin' : '/'}>Return to your area</Link>
      </section>
    )
  }

  return children
}
