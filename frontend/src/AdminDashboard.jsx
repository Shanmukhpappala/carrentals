import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from './api'
import VehicleImage from './VehicleImage'
import { getImageUrlError } from './vehicleImageUtils'
import { formatINR as formatAmount, getVehicleCategory } from './vehiclePresentation'

const currentYear = new Date().getFullYear()
const emptyCar = {
  brand: '', model: '', registrationNumber: '', year: String(currentYear), fuelType: '',
  transmission: '', seatingCapacity: '5', pricePerDay: '', location: '', description: '',
  imageUrl: '',
}

function carPayload(form) {
  return {
    brand: form.brand.trim(),
    model: form.model.trim(),
    registrationNumber: form.registrationNumber.trim(),
    year: Number(form.year),
    fuelType: form.fuelType.trim(),
    transmission: form.transmission.trim(),
    seatingCapacity: Number(form.seatingCapacity),
    pricePerDay: Number(form.pricePerDay),
    location: form.location.trim(),
    imageUrl: form.imageUrl.trim(),
    description: form.description.trim(),
  }
}

function statusBadgeClass(status) {
  return `status status-${String(status || '').toLowerCase().replace(/_/g, '-')}`
}

function uniqueImageUrl(car, cars) {
  const url = car?.imageUrl?.trim()
  return url && cars.filter((candidate) => candidate.imageUrl?.trim() === url).length === 1 ? url : ''
}

export default function AdminDashboard() {
  const [cars, setCars] = useState([])
  const [bookings, setBookings] = useState([])
  const [form, setForm] = useState(emptyCar)
  const [editingCar, setEditingCar] = useState(null)
  const [lookupId, setLookupId] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [busyId, setBusyId] = useState(null)
  const [statusBusy, setStatusBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [imagePreview, setImagePreview] = useState({ url: '', status: 'empty' })
  const [editorOpen, setEditorOpen] = useState(false)
  const [vehicleSearch, setVehicleSearch] = useState('')
  const [bookingSearch, setBookingSearch] = useState('')
  const [bookingFilter, setBookingFilter] = useState('ALL')
  const imageUrlError = getImageUrlError(form.imageUrl)
  const normalizedImageUrl = form.imageUrl.trim()
  const previewStatus = imagePreview.url === normalizedImageUrl
    ? imagePreview.status
    : !normalizedImageUrl ? 'empty' : imageUrlError ? 'invalid' : 'loading'

  const availableCount = cars.filter((car) => car.status === 'AVAILABLE').length
  const summaryCards = [
    { label: 'Total vehicles', value: cars.length, detail: 'Full managed inventory', tone: 'summary-neutral' },
    { label: 'Available', value: availableCount, detail: 'Returned as bookable by the fleet API', tone: 'summary-positive' },
    { label: 'Unavailable', value: cars.length - availableCount, detail: 'Inactive or in maintenance', tone: 'summary-muted' },
    { label: 'Pending bookings', value: bookings.filter((booking) => booking.status === 'REQUESTED').length, detail: 'Awaiting admin review', tone: 'summary-pending' },
  ]

  const filteredCars = cars.filter((car) => `${car.brand} ${car.model} ${car.registrationNumber} ${car.location} ${car.year}`.toLowerCase().includes(vehicleSearch.trim().toLowerCase()))
  const filteredBookings = bookings.filter((booking) => {
    const searchableText = `${booking.id} ${booking.carBrand} ${booking.carModel} ${booking.userName} ${booking.pickupDate} ${booking.returnDate}`.toLowerCase()
    return (bookingFilter === 'ALL' || booking.status === bookingFilter) && searchableText.includes(bookingSearch.trim().toLowerCase())
  })

  async function reload(showProgress = true) {
    if (showProgress) {
      setLoading(true)
      setError('')
    }
    try {
      const [availableCars, allBookings] = await Promise.all([
        api.get('/api/admin/cars'),
        api.get('/api/admin/bookings'),
      ])
      setCars(availableCars)
      setBookings(allBookings)
      return true
    } catch (requestError) {
      setError(requestError.message)
      return false
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let isCurrent = true
    Promise.all([api.get('/api/admin/cars'), api.get('/api/admin/bookings')])
      .then(([availableCars, allBookings]) => {
        if (!isCurrent) return
        setCars(availableCars)
        setBookings(allBookings)
      })
      .catch((requestError) => {
        if (isCurrent) setError(requestError.message)
      })
      .finally(() => {
        if (isCurrent) setLoading(false)
      })
    return () => {
      isCurrent = false
    }
  }, [])

  useEffect(() => {
    if (editorOpen) document.getElementById('vehicle-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [editorOpen])

  function openNewCarForm() {
    setEditingCar(null)
    setForm(emptyCar)
    setError('')
    setMessage('')
    setEditorOpen(true)
  }

  async function loadCarForEdit(carId) {
    setError('')
    setMessage('')
    setLookupId(String(carId))
    try {
      const car = await api.get(`/api/admin/cars/${carId}`)
      setEditorOpen(true)
      setEditingCar(car)
      setForm({
        brand: car.brand || '', model: car.model || '', registrationNumber: car.registrationNumber || '',
        year: String(car.year || ''), fuelType: car.fuelType || '', transmission: car.transmission || '',
        seatingCapacity: String(car.seatingCapacity || ''), pricePerDay: String(car.pricePerDay || ''),
        location: car.location || '', description: car.description || '', imageUrl: car.imageUrl || '',
      })
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  function clearEditor() {
    setEditorOpen(false)
    setEditingCar(null)
    setForm(emptyCar)
    setLookupId('')
    setError('')
    setMessage('')
  }

  async function saveCar(event) {
    event.preventDefault()
    if (imageUrlError) {
      setError(imageUrlError)
      return
    }
    setSaving(true)
    setError('')
    setMessage('')
    try {
      if (editingCar) {
        const updatedCar = await api.put(`/api/admin/cars/${editingCar.id}`, carPayload(form))
        setEditingCar(updatedCar)
        setMessage(`Vehicle ${editingCar.id} updated.`)
      } else {
        await api.post('/api/admin/cars', carPayload(form))
        setMessage('Vehicle added to the fleet.')
      }
      clearEditor()
      await reload()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setSaving(false)
    }
  }

  async function changeCarStatus(carId, status) {
    setStatusBusy(true)
    setError('')
    setMessage('')
    try {
      const updated = await api.patch(`/api/admin/cars/${carId}/status`, { status })
      setEditingCar(updated)
      setMessage(`Vehicle status changed to ${updated.status}.`)
      await reload()
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setStatusBusy(false)
    }
  }

  async function runBookingAction(bookingId, action) {
    const confirmations = {
      approve: 'Approve this booking request?',
      reject: 'Reject this booking request?',
      cancel: 'Cancel this booking?',
      pickup: 'Record pickup for this booking?',
      'no-show': 'Mark this booking as no-show?',
      complete: 'Mark this rental as complete?',
    }
    if (confirmations[action] && !window.confirm(confirmations[action])) return

    setBusyId(bookingId)
    setError('')
    setMessage('')
    try {
      const updated = await api.patch(`/api/admin/bookings/${bookingId}/${action}`)
      setBookings((current) => current.map((booking) => booking.id === bookingId ? updated : booking))
      setMessage(`Booking ${bookingId} is now ${updated.status}.`)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setBusyId(null)
    }
  }

  function bookingActions(booking) {
    if (booking.status === 'REQUESTED') return [['approve', 'Approve'], ['reject', 'Reject'], ['cancel', 'Cancel']]
    if (booking.status === 'CONFIRMED') return [['pickup', 'Record pickup'], ['no-show', 'Mark no-show'], ['cancel', 'Cancel']]
    if (booking.status === 'ACTIVE') return [['complete', 'Complete rental']]
    return []
  }

  return (
    <section className="workspace-panel admin-workspace">
      <div className="workspace-heading">
        <div className="admin-heading-copy">
          <p className="eyebrow">MILEPOST / OPERATIONS</p>
          <h1>Fleet control</h1>
          <p>Vehicle inventory and booking requests, grounded in live service data.</p>
        </div>
        <button className="button button-quiet refresh-button" type="button" disabled={loading} onClick={async () => { setMessage(''); const refreshed = await reload(); if (refreshed !== false) setMessage('Fleet and bookings refreshed.') }}>{loading ? 'Refreshing...' : 'Refresh data'}</button>
      </div>

      <nav className="admin-section-nav" aria-label="Admin sections"><Link to="/admin/cars#admin-vehicles">Vehicle directory</Link><Link to="/admin/bookings#admin-bookings">Booking queue</Link><button className="button button-primary" type="button" onClick={openNewCarForm}>+ Add vehicle</button></nav>

      <div className="admin-summary-grid">
        {summaryCards.map((card) => (
          <div className={`summary-card ${card.tone}`} key={card.label}>
            <span>{card.label}</span>
            <strong>{card.value}</strong>
            <small>{card.detail}</small>
          </div>
        ))}
      </div>

      {error && <p className="notice notice-error" role="alert">{error}</p>}
      {message && <p className="notice notice-success" role="status">{message}</p>}

      <section className="admin-section" id="admin-vehicles">
        <div className="section-heading"><div><p className="eyebrow">FLEET</p><h2>Vehicle directory</h2></div><span>{filteredCars.length} of {cars.length} vehicles</span></div>
        <div className="admin-list-toolbar">
          <label className="admin-search"><span className="sr-only">Search vehicles</span><input type="search" placeholder="Search make, model, plate, location..." value={vehicleSearch} onChange={(event) => setVehicleSearch(event.target.value)} /></label>
          <form className="lookup-form" onSubmit={(event) => { event.preventDefault(); if (lookupId) loadCarForEdit(lookupId) }}>
            <label className="form-field"><span>Find vehicle by ID</span><input type="number" min="1" required value={lookupId} onChange={(event) => setLookupId(event.target.value)} /></label>
            <button className="button button-secondary" type="submit" disabled={loading}>Load</button>
          </form>
        </div>
        {loading ? <div className="admin-loading" role="status"><span className="loading-indicator" /> Loading fleet and booking requests...</div> : cars.length === 0 ? <div className="empty-state compact-empty"><strong>No vehicles</strong><span>Add a vehicle to begin managing the fleet.</span></div> : filteredCars.length === 0 ? <div className="empty-state compact-empty"><strong>No matching vehicles</strong><span>Try another search.</span></div> : <div className="admin-car-list">
          {filteredCars.map((car) => <article className="admin-car-row" key={car.id}>
            <VehicleImage className="admin-car-thumb" src={uniqueImageUrl(car, cars)} alt={`${car.brand} ${car.model}`} />
            <div className="admin-car-info"><div className="admin-car-title"><strong>{car.brand} {car.model}</strong><span className="category-pill">{getVehicleCategory(car)}</span><span className={statusBadgeClass(car.status)}>{car.status}</span></div><span>{car.registrationNumber} / {car.year} / {car.fuelType} / {car.transmission} / {car.seatingCapacity} seats</span><span>#{car.id} / {car.location}</span></div>
            <strong className="admin-car-price">{formatAmount(car.pricePerDay)} / day</strong>
            <button className="button button-outline" type="button" disabled={loading} onClick={() => loadCarForEdit(car.id)}>Edit vehicle</button>
          </article>)}
        </div>}

        {editorOpen && <form className="admin-form" id="vehicle-editor" onSubmit={saveCar}>
          <div className="section-heading form-heading"><div><p className="eyebrow">{editingCar ? `EDIT VEHICLE / ${editingCar.id}` : 'ADD VEHICLE'}</p><h2>{editingCar ? `${editingCar.brand} ${editingCar.model}` : 'Add to the fleet'}</h2></div><button className="text-button" type="button" onClick={clearEditor}>Cancel</button></div>
          <div className="admin-form-grid">
            <p className="admin-form-section-label field-wide">Vehicle identity</p>
            <label className="form-field"><span>Brand</span><input required value={form.brand} onChange={(event) => setForm({ ...form, brand: event.target.value })} /></label>
            <label className="form-field"><span>Model</span><input required value={form.model} onChange={(event) => setForm({ ...form, model: event.target.value })} /></label>
            <label className="form-field"><span>Registration number</span><input required value={form.registrationNumber} onChange={(event) => setForm({ ...form, registrationNumber: event.target.value })} /></label>
            <p className="admin-form-section-label field-wide">Technical specifications</p>
            <label className="form-field"><span>Year</span><input type="number" min="1950" max={currentYear + 1} required value={form.year} onChange={(event) => setForm({ ...form, year: event.target.value })} /></label>
            <label className="form-field"><span>Fuel type</span><input required value={form.fuelType} onChange={(event) => setForm({ ...form, fuelType: event.target.value })} /></label>
            <label className="form-field"><span>Transmission</span><input required value={form.transmission} onChange={(event) => setForm({ ...form, transmission: event.target.value })} /></label>
            <label className="form-field"><span>Seats</span><input type="number" min="1" required value={form.seatingCapacity} onChange={(event) => setForm({ ...form, seatingCapacity: event.target.value })} /></label>
            <p className="admin-form-section-label field-wide">Pricing and location</p>
            <label className="form-field"><span>Price per day</span><input type="number" min="0.01" step="0.01" required value={form.pricePerDay} onChange={(event) => setForm({ ...form, pricePerDay: event.target.value })} /></label>
            <label className="form-field"><span>Location</span><input required value={form.location} onChange={(event) => setForm({ ...form, location: event.target.value })} /></label>
            <p className="admin-form-section-label field-wide">Description</p>
            <label className="form-field field-wide"><span>Description</span><textarea rows="3" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
            <p className="admin-form-section-label field-wide">Vehicle image</p>
            <div className="image-url-section field-wide">
              <label className="form-field" htmlFor="vehicle-image-url"><span>Image URL</span><input id="vehicle-image-url" type="url" inputMode="url" autoComplete="url" placeholder="https://example.com/car-image.jpg" value={form.imageUrl} aria-invalid={Boolean(imageUrlError)} aria-describedby="image-url-help image-url-error" onChange={(event) => setForm({ ...form, imageUrl: event.target.value })} /></label>
              <p className="image-url-help" id="image-url-help">Paste a direct link to an image, not a webpage. HTTPS is preferred; the image stays hosted at its original source.</p>
              {imageUrlError && <p className="image-url-error" id="image-url-error" role="alert">{imageUrlError}</p>}
              <div className="image-preview" aria-live="polite">
                {previewStatus === 'ready' ? <img src={normalizedImageUrl} alt="Vehicle image preview" onError={() => setImagePreview({ url: normalizedImageUrl, status: 'failed' })} /> : <VehicleImage src="" alt="Default vehicle placeholder" loading="eager" />}
                {previewStatus === 'loading' && <span className="image-preview-message" role="status">Loading preview...</span>}
                {previewStatus === 'invalid' && <span className="image-preview-message">Enter a valid URL to preview the image.</span>}
                {previewStatus === 'failed' && <span className="image-preview-message">This image could not be loaded. You can still save the vehicle details.</span>}
                {previewStatus === 'empty' && <span className="image-preview-message">No image URL added</span>}
                {previewStatus === 'loading' && <img className="image-preview-source" src={normalizedImageUrl} alt="" onLoad={() => setImagePreview({ url: normalizedImageUrl, status: 'ready' })} onError={() => setImagePreview({ url: normalizedImageUrl, status: 'failed' })} />}
              </div>
              {normalizedImageUrl && <button className="text-button image-clear" type="button" onClick={() => setForm({ ...form, imageUrl: '' })}>Remove image URL</button>}
            </div>
          </div>
          <div className="profile-actions"><span>{editingCar ? 'Status is managed separately.' : 'New vehicles are created as AVAILABLE by the service.'}</span><div className="editor-actions"><button className="button button-outline" type="button" onClick={clearEditor} disabled={saving}>Cancel</button><button className="button button-primary" type="submit" disabled={saving || Boolean(imageUrlError)}>{saving ? 'Saving...' : editingCar ? 'Update vehicle' : 'Add vehicle'}</button></div></div>
        </form>}
        {editingCar && <div className="status-editor"><label className="form-field"><span>Vehicle status</span><select value={editingCar.status} disabled={statusBusy} onChange={(event) => changeCarStatus(editingCar.id, event.target.value)}><option value="AVAILABLE">AVAILABLE</option><option value="INACTIVE">INACTIVE</option><option value="MAINTENANCE">MAINTENANCE</option></select></label>{statusBusy && <span className="form-hint" role="status">Updating vehicle status...</span>}</div>}
        <p className="form-hint">The admin vehicle directory includes available, inactive, and maintenance vehicles.</p>
      </section>

      <section className="admin-section" id="admin-bookings">
        <div className="section-heading"><div><p className="eyebrow">REQUEST QUEUE</p><h2>Booking management</h2></div><span>{filteredBookings.length} of {bookings.length} bookings</span></div>
        <div className="admin-list-toolbar booking-toolbar"><label className="admin-search"><span className="sr-only">Search bookings</span><input type="search" placeholder="Search booking, customer, or vehicle..." value={bookingSearch} onChange={(event) => setBookingSearch(event.target.value)} /></label><label className="booking-filter"><span className="sr-only">Filter booking status</span><select value={bookingFilter} onChange={(event) => setBookingFilter(event.target.value)}><option value="ALL">All statuses</option>{['REQUESTED', 'CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED', 'REJECTED', 'NO_SHOW'].map((status) => <option value={status} key={status}>{status.replace('_', ' ')}</option>)}</select></label></div>
        {loading ? <div className="admin-loading" role="status"><span className="loading-indicator" /> Loading bookings...</div> : bookings.length === 0 ? <div className="empty-state compact-empty"><strong>No booking requests</strong><span>New customer requests will appear here.</span></div> : filteredBookings.length === 0 ? <div className="empty-state compact-empty"><strong>No matching bookings</strong><span>Adjust the search or status filter.</span></div> : <div className="admin-booking-list">
          {filteredBookings.map((booking) => <article className="admin-booking-row" key={booking.id}>
            <div className="admin-booking-info"><span className="booking-id">BOOKING / {booking.id}</span><strong>{booking.carBrand} {booking.carModel}</strong><span>{booking.userName} / {booking.pickupDate} to {booking.returnDate}</span></div>
            <div className="admin-booking-total"><span className={statusBadgeClass(booking.status)}>{booking.status?.replace('_', ' ')}</span><strong>{formatAmount(booking.totalAmount)}</strong><small>Total rental amount</small></div>
            <div className="admin-actions">{bookingActions(booking).map(([action, label]) => <button className={`button ${action === 'reject' || action === 'cancel' ? 'button-outline-danger' : 'button-secondary'}`} type="button" key={action} disabled={busyId === booking.id} onClick={() => runBookingAction(booking.id, action)}>{busyId === booking.id ? 'Updating...' : label}</button>)}</div>
          </article>)}
        </div>}
      </section>
    </section>
  )
}
