import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import useDialogAccessibility from './useDialogAccessibility'
import { formatINR as formatAmount } from './vehiclePresentation'

export default function CustomerDashboard({ view, onProfileUpdated, bookingConfirmation = '' }) {
  const [profile, setProfile] = useState(null)
  const [bookings, setBookings] = useState([])
  const [profileForm, setProfileForm] = useState({ name: '', phone: '' })
  const [selectedBooking, setSelectedBooking] = useState(null)
  const [bookingDetailLoading, setBookingDetailLoading] = useState(false)
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [loadFailed, setLoadFailed] = useState(false)
  const [message, setMessage] = useState('')
  const [reloadCount, setReloadCount] = useState(0)
  const bookingDialogRef = useRef(null)
  const closeBookingDialog = useCallback(() => setSelectedBooking(null), [])

  useDialogAccessibility(bookingDialogRef, closeBookingDialog, Boolean(selectedBooking))

  useEffect(() => {
    let isCurrent = true
    const loadRequest = view === 'profile' ? api.get('/api/users/me') : api.get('/api/bookings/my')
    loadRequest.then((result) => {
      if (!isCurrent) return
      if (view === 'profile') {
        setProfile(result)
        setProfileForm({ name: result.name || '', phone: result.phone || '' })
      } else {
        setBookings(result)
      }
      setLoadFailed(false)
    }).catch((requestError) => {
      if (isCurrent) {
        setError(requestError.message)
        setLoadFailed(true)
      }
    }).finally(() => {
      if (isCurrent) setLoading(false)
    })

    return () => {
      isCurrent = false
    }
  }, [view, reloadCount])

  function retryLoad() {
    setError('')
    setLoadFailed(false)
    setLoading(true)
    setReloadCount((current) => current + 1)
  }

  async function saveProfile(event) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setMessage('')
    try {
      const updated = await api.put('/api/users/me', { name: profileForm.name.trim(), phone: profileForm.phone.trim() })
      setProfile(updated)
      setProfileForm({ name: updated.name, phone: updated.phone })
      onProfileUpdated(updated)
      setMessage('Your profile has been updated.')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  async function showBooking(bookingId) {
    setError('')
    setBookingDetailLoading(true)
    try {
      setSelectedBooking(await api.get(`/api/bookings/${bookingId}`))
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBookingDetailLoading(false)
    }
  }

  async function cancelBooking(bookingId) {
    if (!window.confirm('Cancel this booking? This action cannot be undone.')) return
    setBusyId(bookingId)
    setError('')
    setMessage('')
    try {
      const updated = await api.patch(`/api/bookings/${bookingId}/cancel`)
      setBookings((current) => current.map((booking) => booking.id === bookingId ? updated : booking))
      if (selectedBooking?.id === bookingId) setSelectedBooking(updated)
      setMessage('The booking was cancelled.')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <section className="workspace-panel">
      <div className="workspace-heading customer-heading"><div><p className="eyebrow">MILEPOST / CUSTOMER ACCOUNT</p><h1>{view === 'profile' ? 'Your profile' : 'Trips, all in one place.'}</h1><p>{view === 'profile' ? 'Keep your contact details up to date for your rental requests.' : 'Review your request history and keep track of each trip.'}</p></div></div>
      {bookingConfirmation && <p className="notice notice-success" role="status">{bookingConfirmation}</p>}
      {error && <div className="notice notice-error" role="alert"><span>{error}</span>{loadFailed && <button className="text-button" type="button" onClick={retryLoad}>Try again</button>}</div>}
      {message && <p className="notice notice-success" role="status">{message}</p>}
      {loading ? <div className="empty-state" role="status">Loading your account...</div> : loadFailed ? (
        <div className="empty-state"><strong>Your account could not be loaded.</strong></div>
      ) : view === 'profile' ? (
        <form className="profile-form" onSubmit={saveProfile}>
          <label className="form-field"><span>Full name</span><input autoComplete="name" required value={profileForm.name} onChange={(event) => setProfileForm({ ...profileForm, name: event.target.value })} /></label>
          <label className="form-field"><span>Email</span><input value={profile?.email || ''} readOnly /></label>
          <label className="form-field"><span>Phone</span><input autoComplete="tel" required value={profileForm.phone} onChange={(event) => setProfileForm({ ...profileForm, phone: event.target.value })} /></label>
          <div className="profile-actions"><span>Account type: {profile?.role}</span><button className="button button-primary" type="submit" disabled={saving}>{saving ? 'Saving...' : 'Save profile'}</button></div>
        </form>
      ) : bookings.length === 0 ? (
        <div className="empty-state customer-empty-state"><span className="empty-state-mark" aria-hidden="true">M</span><strong>No bookings yet.</strong><span>Your first trip starts with a vehicle that fits your plans.</span><Link className="button button-primary" to="/">Explore available cars <span aria-hidden="true">{'->'}</span></Link></div>
      ) : (
        <>
        <div className="customer-booking-summary" aria-label="Booking summary">
          <article><span>ALL TRIPS</span><strong>{bookings.length}</strong><small>In your account</small></article>
          <article><span>IN PROGRESS</span><strong>{bookings.filter((booking) => ['REQUESTED', 'CONFIRMED', 'ACTIVE'].includes(booking.status)).length}</strong><small>Requests and active rentals</small></article>
          <article><span>COMPLETED</span><strong>{bookings.filter((booking) => booking.status === 'COMPLETED').length}</strong><small>Finished rentals</small></article>
        </div>
        <div className="booking-list">
          {bookings.map((booking) => <article className="booking-card" key={booking.id}>
            <div className="booking-card-top"><span className="booking-id">MILEPOST TRIP / {booking.id}</span><span className={`status status-${booking.status?.toLowerCase().replace(/_/g, '-')}`}>{booking.status?.replace(/_/g, ' ')}</span></div>
            <div className="booking-card-main"><div className="booking-card-vehicle"><span className="booking-car-monogram" aria-hidden="true">{booking.carBrand?.slice(0, 1) || 'M'}</span><div><p className="car-make">{booking.carBrand}</p><h2>{booking.carModel}</h2></div></div>
              <div className="booking-card-dates"><div><span>PICKUP</span><strong>{booking.pickupDate}</strong></div><i aria-hidden="true" /><div><span>RETURN</span><strong>{booking.returnDate}</strong></div></div>
              <div className="booking-card-total"><span>BOOKED TOTAL</span><strong>{formatAmount(booking.totalAmount)}</strong><small>Rate at request: {formatAmount(booking.pricePerDay)} / day</small></div></div>
            <div className="booking-card-footer"><span>Booking amount is fixed at the rate shown when requested.</span><div className="booking-actions"><button className="text-button" type="button" disabled={bookingDetailLoading} onClick={() => showBooking(booking.id)}>{bookingDetailLoading ? 'Loading...' : 'Booking details'}</button>{['REQUESTED', 'CONFIRMED'].includes(booking.status) && <button className="text-button danger-text" type="button" disabled={busyId === booking.id} onClick={() => cancelBooking(booking.id)}>{busyId === booking.id ? 'Cancelling...' : 'Cancel trip'}</button>}</div></div>
          </article>)}
        </div>
        </>
      )}

      {selectedBooking && <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) closeBookingDialog() }}>
        <section className="detail-dialog booking-detail-dialog" ref={bookingDialogRef} tabIndex="-1" role="dialog" aria-modal="true" aria-labelledby="booking-title">
          <button className="dialog-close" type="button" onClick={closeBookingDialog} aria-label="Close booking details">x</button>
          <p className="eyebrow">BOOKING / {selectedBooking.id}</p><h2 id="booking-title">{selectedBooking.carBrand} {selectedBooking.carModel}</h2>
          <dl className="detail-specs"><div><dt>STATUS</dt><dd><span className={`status status-${selectedBooking.status?.toLowerCase().replace(/_/g, '-')}`}>{selectedBooking.status?.replace(/_/g, ' ')}</span></dd></div><div><dt>BOOKED ON</dt><dd>{selectedBooking.createdAt}</dd></div><div><dt>PICKUP</dt><dd>{selectedBooking.pickupDate}</dd></div><div><dt>RETURN</dt><dd>{selectedBooking.returnDate}</dd></div><div><dt>DAYS</dt><dd>{selectedBooking.numberOfDays}</dd></div><div><dt>BOOKED TOTAL</dt><dd>{formatAmount(selectedBooking.totalAmount)}</dd></div><div><dt>CURRENT DAILY RATE</dt><dd>{formatAmount(selectedBooking.pricePerDay)} / day</dd></div></dl>
          {['REQUESTED', 'CONFIRMED'].includes(selectedBooking.status) && <button className="button button-danger" type="button" disabled={busyId === selectedBooking.id} onClick={() => cancelBooking(selectedBooking.id)}>{busyId === selectedBooking.id ? 'Cancelling...' : 'Cancel booking'}</button>}
        </section>
      </div>}
    </section>
  )
}
