import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { api } from './api'
import VehicleImage from './VehicleImage'

const emptyForm = { name: '', email: '', password: '', phone: '' }

export default function AuthDialog({ featuredCar, mode, onAuthenticated }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const dialogRef = useRef(null)
  const location = useLocation()
  const navigate = useNavigate()
  const message = location.state?.message || ''

  useEffect(() => {
    const dialogElement = dialogRef.current
    const handleEscape = (event) => {
      if (event.key === 'Escape') navigate('/')
    }
    const handleTab = (event) => {
      if (event.key !== 'Tab' || !dialogElement) return
      const focusable = [...dialogElement.querySelectorAll('a[href], button:not(:disabled), input:not(:disabled)')]
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (!first || !last) return
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', handleEscape)
    dialogElement?.querySelector('input[type="email"]')?.focus()
    dialogElement?.addEventListener('keydown', handleTab)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleEscape)
      dialogElement?.removeEventListener('keydown', handleTab)
      if (previousFocus instanceof HTMLElement) previousFocus.focus()
    }
  }, [navigate])

  function navigateModes(event) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return
    event.preventDefault()
    const modes = ['login', 'register', 'admin']
    const destinations = ['/login', '/register', '/admin/login']
    const tabs = [...event.currentTarget.querySelectorAll('[role="tab"]')]
    const currentIndex = modes.indexOf(mode)
    const direction = event.key === 'ArrowRight' ? 1 : -1
    const nextIndex = (currentIndex + direction + modes.length) % modes.length
    navigate(destinations[nextIndex], { state: { from: location.state?.from } })
    tabs[nextIndex]?.focus()
  }

  async function submit(event) {
    event.preventDefault()
    setSubmitting(true)
    setError('')

    try {
      if (mode === 'register') {
        await api.post('/api/auth/register', {
          name: form.name.trim(),
          email: form.email.trim(),
          password: form.password,
          phone: form.phone.trim(),
        })
        navigate('/login', { state: { message: 'Account created. Sign in to continue.', from: location.state?.from } })
        return
      }

      const endpoint = mode === 'admin' ? '/api/admin/auth/login' : '/api/auth/login'
      const result = await api.post(endpoint, { email: form.email.trim(), password: form.password })
      if (!result?.token || !result?.role) throw new Error('The sign-in response was incomplete.')
      if (mode === 'admin' && result.role !== 'ADMIN') throw new Error('This account does not have administrator access.')
      if (mode === 'login' && result.role !== 'CUSTOMER') throw new Error('Use the admin sign-in tab for administrator accounts.')
      setForm((current) => ({ ...current, password: '' }))
      onAuthenticated(result, location.state?.from)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop auth-backdrop">
      <section className="auth-dialog" ref={dialogRef} role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <Link className="dialog-close" to="/" aria-label="Return to fleet">×</Link>
        <div className="auth-shell">
          <div className="auth-visual" aria-hidden="true">
            <VehicleImage className="auth-vehicle-image" src={featuredCar?.imageUrl} alt="" loading="eager" />
            <div className="auth-visual-brand"><span className="wordmark-mark">M</span><span>MILEPOST</span></div>
            <div className="auth-visual-copy">
              <p className="eyebrow">A BETTER WAY TO GO</p>
              <h3>Make room for the journey.</h3>
              <p>Well-kept vehicles, thoughtful service, and a rental experience that stays simple.</p>
              {featuredCar && <div className="auth-featured-car"><span>FEATURED VEHICLE</span><strong>{featuredCar.brand} {featuredCar.model}</strong><small>{featuredCar.year} / {featuredCar.transmission}</small></div>}
            </div>
          </div>

          <div className="auth-panel">
            <p className="eyebrow">ACCOUNT ACCESS</p>
            <h2 id="auth-title">{mode === 'register' ? 'Create your account' : mode === 'admin' ? 'Admin sign in' : 'Welcome back'}</h2>

            <div className="auth-tabs" role="tablist" aria-label="Account access" onKeyDown={navigateModes}>
              <Link to="/login" state={{ from: location.state?.from }} role="tab" tabIndex={mode === 'login' ? 0 : -1} aria-selected={mode === 'login'} className={mode === 'login' ? 'selected' : ''}>Customer login</Link>
              <Link to="/register" state={{ from: location.state?.from }} role="tab" tabIndex={mode === 'register' ? 0 : -1} aria-selected={mode === 'register'} className={mode === 'register' ? 'selected' : ''}>Register</Link>
              <Link to="/admin/login" state={{ from: location.state?.from }} role="tab" tabIndex={mode === 'admin' ? 0 : -1} aria-selected={mode === 'admin'} className={mode === 'admin' ? 'selected' : ''}>Admin login</Link>
            </div>

            <form className="stack-form" onSubmit={submit} aria-busy={submitting}>
              {mode === 'register' && <>
                <label className="form-field"><span>Full name</span><input autoComplete="name" required value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
                <label className="form-field"><span>Phone</span><input autoComplete="tel" required value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
              </>}
              <label className="form-field"><span>Email</span><input type="email" autoComplete="email" required value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label>
              <label className="form-field">
                <span>Password</span>
                <div className="password-field">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
                    minLength={mode === 'register' ? 6 : undefined}
                    required
                    value={form.password}
                    onChange={(event) => setForm({ ...form, password: event.target.value })}
                  />
                  <button
                    className="password-toggle"
                    type="button"
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    onClick={() => setShowPassword((current) => !current)}
                  >
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
              </label>
              {error && <p className="notice notice-error" role="alert">{error}</p>}
              {message && <p className="notice notice-success" role="status">{message}</p>}
              <button className="button button-primary form-submit" type="submit" disabled={submitting}>{submitting ? 'Please wait...' : mode === 'register' ? 'Create account' : 'Sign in'}</button>
            </form>
          </div>
        </div>
      </section>
    </div>
  )
}