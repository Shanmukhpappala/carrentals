import { useEffect, useState } from 'react'
import { api, getAuthToken, setAuthToken, setUnauthorizedHandler } from './api'
import { AuthContext } from './authContext'

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => ({
    status: getAuthToken() ? 'loading' : 'unauthenticated',
    user: null,
    error: '',
  }))
  const [restoreAttempt, setRestoreAttempt] = useState(0)

  useEffect(() => {
    let isCurrent = true
    setUnauthorizedHandler(() => {
      setAuthToken(null)
      setSession({ status: 'unauthenticated', user: null, error: '' })
    })

    const token = getAuthToken()
    if (!token) {
      return () => setUnauthorizedHandler(null)
    }

    setAuthToken(token)

    async function restoreSession() {
      try {
        const user = await api.get('/api/users/me')
        if (isCurrent) setSession({ status: 'authenticated', user, error: '' })
      } catch (customerError) {
        if (customerError.status === 403) {
          try {
            await api.get('/api/admin/bookings')
            if (isCurrent) {
              setSession({
                status: 'authenticated',
                user: { role: 'ADMIN', name: 'Administrator' },
                error: '',
              })
            }
          } catch (adminError) {
            if (!isCurrent) return
            if (adminError.status === 401) {
              setAuthToken(null)
              setSession({ status: 'unauthenticated', user: null, error: '' })
            } else {
              setSession({ status: 'error', user: null, error: adminError.message })
            }
          }
          return
        }

        if (!isCurrent) return
        if (customerError.status === 401) {
          setAuthToken(null)
          setSession({ status: 'unauthenticated', user: null, error: '' })
        } else {
          setSession({ status: 'error', user: null, error: customerError.message })
        }
      }
    }

    restoreSession()
    return () => {
      isCurrent = false
      setUnauthorizedHandler(null)
    }
  }, [restoreAttempt])

  function signIn(token, user) {
    setAuthToken(token)
    setSession({ status: 'authenticated', user, error: '' })
  }

  function signOut() {
    setAuthToken(null)
    setSession({ status: 'signingOut', user: null, error: '' })
  }

  function completeSignOut() {
    setSession({ status: 'unauthenticated', user: null, error: '' })
  }

  function updateUser(user) {
    setSession((current) => ({ ...current, user: { ...current.user, ...user } }))
  }

  function retryRestore() {
    setSession({ status: 'loading', user: null, error: '' })
    setRestoreAttempt((attempt) => attempt + 1)
  }

  return (
    <AuthContext.Provider value={{ ...session, signIn, signOut, completeSignOut, updateUser, retryRestore }}>
      {children}
    </AuthContext.Provider>
  )
}

