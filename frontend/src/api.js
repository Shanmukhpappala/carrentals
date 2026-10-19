const API_BASE_URL = (import.meta.env.DEV ? '' : import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const AUTH_TOKEN_KEY = 'milepost.authToken'

let authToken = window.localStorage.getItem(AUTH_TOKEN_KEY)
let unauthorizedHandler = () => {}

export function setAuthToken(token) {
  authToken = token || null
  if (authToken) window.localStorage.setItem(AUTH_TOKEN_KEY, authToken)
  else window.localStorage.removeItem(AUTH_TOKEN_KEY)
}

export function getAuthToken() {
  return window.localStorage.getItem(AUTH_TOKEN_KEY)
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = typeof handler === 'function' ? handler : () => {}
}

function getErrorMessage(status, payload, hadToken, path) {
  if (status === 400) return payload?.message || 'Please check the information and try again.'
  if (status === 401) {
    if (hadToken) return 'Your session has expired. Please sign in again.'
    if (path === '/api/auth/login' || path === '/api/admin/auth/login') return 'Email or password was not accepted.'
    return 'Sign in to access this information.'
  }
  if (status === 403) return 'You do not have permission to do that.'
  if (status === 404) return 'That item could not be found.'
  if (status === 409) return payload?.message || 'This request conflicts with current data. Please refresh and try again.'
  if (status >= 500) return 'The service could not complete your request. Please try again later.'
  return payload?.message || 'The request could not be completed.'
}

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'application/json')

  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const hadToken = Boolean(authToken)
  if (authToken) headers.set('Authorization', `Bearer ${authToken}`)

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
      body: options.body === undefined || typeof options.body === 'string'
        ? options.body
        : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError('Cannot reach the rental service. Check that the backend is running and try again.')
  }

  let payload = null
  if (response.status !== 204) {
    try {
      payload = await response.json()
    } catch {
      payload = null
    }
  }

  if (!response.ok) {
    if (response.status === 401 && hadToken) {
      setAuthToken(null)
      unauthorizedHandler()
    }
    throw new ApiError(getErrorMessage(response.status, payload, hadToken, path), response.status)
  }

  return payload
}

export const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
}