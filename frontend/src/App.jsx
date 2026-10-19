import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import AdminDashboard from './AdminDashboard'
import AuthDialog from './AuthDialog'
import { api } from './api'
import ProtectedRoute from './ProtectedRoute'
import { useAuth } from './authContext'
import CustomerDashboard from './CustomerDashboard'
import VehicleImage from './VehicleImage'
import useDialogAccessibility from './useDialogAccessibility'
import { formatINR, getVehicleCategory } from './vehiclePresentation'
import './App.css'

function getToday() {
  const date = new Date()
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 10)
}

function getInclusiveRentalDays(pickupDate, returnDate) {
  if (!pickupDate || !returnDate || returnDate < pickupDate) return 0
  const [pickupYear, pickupMonth, pickupDay] = pickupDate.split('-').map(Number)
  const [returnYear, returnMonth, returnDay] = returnDate.split('-').map(Number)
  const pickup = Date.UTC(pickupYear, pickupMonth - 1, pickupDay)
  const returnTime = Date.UTC(returnYear, returnMonth - 1, returnDay)
  return Math.round((returnTime - pickup) / 86400000) + 1
}

function hasModelSpecificImage(car) {
  if (!car?.imageUrl || !car?.model) return false
  try {
    const imagePath = decodeURIComponent(new URL(car.imageUrl).pathname).toLowerCase()
    const modelTokens = String(car.model).toLowerCase().match(/[a-z0-9]+/g) || []
    return modelTokens.some((token) => token.length >= 3 && imagePath.includes(token))
  } catch {
    return false
  }
}

function hasUniqueImageUrl(car, vehicles) {
  const imageUrl = car?.imageUrl?.trim()
  return Boolean(imageUrl && vehicles.filter((vehicle) => vehicle.imageUrl?.trim() === imageUrl).length === 1)
}

function App() {
  const { user, status, signIn, signOut: clearSession, completeSignOut, updateUser } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const today = getToday()
  const [cars, setCars] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [dates, setDates] = useState({ pickupDate: '', returnDate: '' })
  const [activeDates, setActiveDates] = useState(null)
  const [detailResult, setDetailResult] = useState({ id: null, car: null, error: '' })
  const [bookingDates, setBookingDates] = useState({ pickupDate: '', returnDate: '' })
  const [bookingError, setBookingError] = useState('')
  const [bookingPending, setBookingPending] = useState(false)
  const [bookingConfirmation, setBookingConfirmation] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)
  const [fleetSearch, setFleetSearch] = useState('')
  const [fuelFilter, setFuelFilter] = useState('all')
  const [categoryFilter, setCategoryFilter] = useState('all')
  const [fleetSort, setFleetSort] = useState('default')
  const [fleetRefresh, setFleetRefresh] = useState(0)
  const [bookingUnavailable, setBookingUnavailable] = useState(false)
  const detailDialogRef = useRef(null)
  const detailCarId = location.pathname.match(/^\/cars\/([^/]+)\/?$/)?.[1]
  const isFleetRoute = location.pathname === '/' || Boolean(detailCarId)
  const selectedCar = detailResult.id === detailCarId ? detailResult.car : null
  const detailsError = detailResult.id === detailCarId ? detailResult.error : ''
  const detailsLoading = Boolean(detailCarId && detailResult.id !== detailCarId)
  const rentalDays = getInclusiveRentalDays(bookingDates.pickupDate, bookingDates.returnDate)
  const estimatedTotal = selectedCar && rentalDays > 0 ? Number(selectedCar.pricePerDay) * rentalDays : null
  const vehicleUnavailable = bookingUnavailable || selectedCar?.status !== 'AVAILABLE'
  const closeVehicleDialog = useCallback(() => navigate('/'), [navigate])

  useDialogAccessibility(detailDialogRef, closeVehicleDialog, Boolean(selectedCar))

  useEffect(() => {
    if (status === 'signingOut' && location.pathname === '/') completeSignOut()
  }, [status, location.pathname, completeSignOut])

  const fuelTypes = [...new Set(cars.map((car) => car.fuelType).filter(Boolean))]
  const vehicleCategories = [...new Set(cars.map(getVehicleCategory))].sort()
  const matchingCars = cars.filter((car) => {
    const searchableText = `${car.brand} ${car.model} ${car.year} ${car.location} ${car.transmission} ${car.fuelType}`.toLowerCase()
    return searchableText.includes(fleetSearch.trim().toLowerCase()) && (fuelFilter === 'all' || car.fuelType === fuelFilter) && (categoryFilter === 'all' || getVehicleCategory(car) === categoryFilter)
  })
  const visibleCars = [...matchingCars].sort((first, second) => {
    if (fleetSort === 'price-asc') return Number(first.pricePerDay) - Number(second.pricePerDay)
    if (fleetSort === 'price-desc') return Number(second.pricePerDay) - Number(first.pricePerDay)
    if (fleetSort === 'year-desc') return Number(second.year) - Number(first.year)
    return Number(first.id) - Number(second.id)
  })
  const heroCar = cars.filter((car) => hasModelSpecificImage(car) && hasUniqueImageUrl(car, cars))
    .sort((first, second) => Number(second.pricePerDay) - Number(first.pricePerDay))[0] || null
  const featuredCar = cars.filter((car) => car.id !== heroCar?.id && hasUniqueImageUrl(car, cars))
    .sort((first, second) => Number(second.pricePerDay) - Number(first.pricePerDay))[0] || null

  useEffect(() => {
    let isCurrent = true
    const params = new URLSearchParams()
    if (activeDates) {
      params.set('pickupDate', activeDates.pickupDate)
      params.set('returnDate', activeDates.returnDate)
    }

    api.get(`/api/cars${params.size ? `?${params}` : ''}`)
      .then((results) => {
        if (isCurrent) {
          setCars(results)
          setError('')
        }
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
  }, [activeDates, fleetRefresh])

  function applyAvailability(event) {
    event.preventDefault()
    if (Boolean(dates.pickupDate) !== Boolean(dates.returnDate)) {
      setError('Choose both a pickup date and a return date to check availability.')
      return
    }
    if (dates.pickupDate && dates.pickupDate < today) {
      setError('The pickup date cannot be in the past.')
      return
    }
    if (dates.pickupDate && dates.returnDate < dates.pickupDate) {
      setError('The return date cannot be before the pickup date.')
      return
    }
    const nextDates = dates.pickupDate ? { ...dates } : null
    const sameDates = activeDates?.pickupDate === nextDates?.pickupDate && activeDates?.returnDate === nextDates?.returnDate
    if (sameDates && !error) return
    setLoading(true)
    setError('')
    if (sameDates) setFleetRefresh((current) => current + 1)
    else setActiveDates(nextDates)
  }

  useEffect(() => {
    if (!detailCarId) return undefined

    let isCurrent = true
    api.get(`/api/cars/${encodeURIComponent(detailCarId)}`)
      .then((car) => { if (isCurrent) setDetailResult({ id: detailCarId, car, error: '' }) })
      .catch((requestError) => { if (isCurrent) setDetailResult({ id: detailCarId, car: null, error: requestError.message }) })

    return () => { isCurrent = false }
  }, [detailCarId])

  function openCarDetails(carId) {
    setBookingError('')
    setBookingUnavailable(false)
    setBookingDates(activeDates || { pickupDate: '', returnDate: '' })
    navigate(`/cars/${carId}`)
  }

  function handleAuthenticated(result, requestedLocation) {
    const { token, ...userDetails } = result
    signIn(token, userDetails)
    const requestedPath = requestedLocation?.pathname || ''
    const requestedIsAdmin = requestedPath.startsWith('/admin')
    const canReturnToRequestedPath = requestedPath && (userDetails.role === 'ADMIN' ? requestedIsAdmin : !requestedIsAdmin)
    const destination = canReturnToRequestedPath
      ? `${requestedPath}${requestedLocation.search || ''}${requestedLocation.hash || ''}`
      : userDetails.role === 'ADMIN' ? '/admin' : '/bookings'
    navigate(destination, { replace: true })
  }

  function signOutUser() {
    clearSession()
    setMenuOpen(false)
    navigate('/', { replace: true })
  }

  async function submitBooking(event) {
    event.preventDefault()
    if (!user) {
      navigate('/login', { state: { from: location } })
      return
    }
    if (user.role !== 'CUSTOMER') {
      setBookingError('Administrator accounts cannot create customer bookings.')
      return
    }
    if (!bookingDates.pickupDate || !bookingDates.returnDate) {
      setBookingError('Choose both a pickup date and a return date.')
      return
    }
    if (bookingDates.pickupDate < today) {
      setBookingError('The pickup date cannot be in the past.')
      return
    }
    if (bookingDates.returnDate < bookingDates.pickupDate) {
      setBookingError('The return date cannot be earlier than the pickup date.')
      return
    }
    if (vehicleUnavailable) {
      setBookingError('This vehicle is no longer available for those dates. Choose another vehicle or date range.')
      return
    }

    setBookingPending(true)
    setBookingError('')
    try {
      await api.post('/api/bookings', {
        carId: selectedCar.id,
        pickupDate: bookingDates.pickupDate,
        returnDate: bookingDates.returnDate,
      })
      setBookingConfirmation('Your booking request was sent successfully.')
      navigate('/bookings')
    } catch (requestError) {
      setBookingError(requestError.status === 409
        ? `${requestError.message} Refresh availability or choose different dates.`
        : requestError.message)
      if (requestError.status === 409) {
        const params = new URLSearchParams({ pickupDate: bookingDates.pickupDate, returnDate: bookingDates.returnDate })
        try {
          const availableCars = await api.get(`/api/cars?${params}`)
          setCars(availableCars)
          if (!availableCars.some((car) => car.id === selectedCar.id)) setBookingUnavailable(true)
        } catch {
          // The booking API remains authoritative if the availability refresh also fails.
        }
      }
    } finally {
      setBookingPending(false)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <Link className="wordmark" to="/" aria-label="Milepost home"><span className="wordmark-mark">M</span><span>MILEPOST</span></Link>
        <span className="topbar-label">CAR RENTAL / FLEET</span>
        <button className="mobile-menu-button" type="button" aria-controls="main-navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? 'Close' : 'Menu'}</button>
        <nav className={`topbar-actions ${menuOpen ? 'is-open' : ''}`} id="main-navigation" aria-label="Main navigation">
          {user?.role === 'ADMIN' && <><NavLink className={() => `topbar-link ${isFleetRoute ? 'active' : ''}`} to="/" onClick={() => setMenuOpen(false)}>Fleet</NavLink><NavLink className={({ isActive }) => `topbar-link ${isActive ? 'active' : ''}`} to="/admin" end onClick={() => setMenuOpen(false)}>Admin desk</NavLink></>}
          {user?.role === 'CUSTOMER' && <><NavLink className={() => `topbar-link ${isFleetRoute ? 'active' : ''}`} to="/" onClick={() => setMenuOpen(false)}>Fleet</NavLink><NavLink className={({ isActive }) => `topbar-link ${isActive ? 'active' : ''}`} to="/bookings" onClick={() => setMenuOpen(false)}>My bookings</NavLink><NavLink className={({ isActive }) => `topbar-link ${isActive ? 'active' : ''}`} to="/profile" onClick={() => setMenuOpen(false)}>Profile</NavLink></>}
          <span className={`connection-state ${loading ? 'is-checking' : error ? 'is-unavailable' : ''}`} role="status" aria-live="polite">
            <i /> {loading ? 'Checking fleet' : error ? 'Fleet unavailable' : 'Live fleet'}
          </span>
          {user ? <><span className="topbar-user">{user.name}</span><button className="topbar-button" type="button" onClick={signOutUser}>Sign out</button></> : <Link className="topbar-button" to="/login" onClick={() => setMenuOpen(false)}>Sign in</Link>}
        </nav>
      </header>

      {isFleetRoute && <section className="catalog" id="top">
        <section className="fleet-hero" aria-labelledby="fleet-title">
          <div className="fleet-hero-copy">
            <p className="eyebrow">MILEPOST / DRIVE YOUR WAY</p>
            <h1 id="fleet-title">Take the long way home.</h1>
            <p>Find a car that fits the trip. Compare live vehicles, clear daily rates and date-based availability in one place.</p>
            <div className="fleet-hero-facts"><span><strong>{loading ? '—' : String(visibleCars.length).padStart(2, '0')}</strong> cars available to explore</span><span>Rates shown in INR</span></div>
            <div className="hero-actions"><a className="button button-primary" href="#fleet-inventory">Explore the fleet <span aria-hidden="true">{'->'}</span></a>{user ? <Link className="hero-secondary-link" to={user.role === 'ADMIN' ? '/admin' : '/bookings'}>{user.role === 'ADMIN' ? 'Open admin desk' : 'View my trips'}</Link> : <Link className="hero-secondary-link" to="/register">Create an account <span aria-hidden="true">{'->'}</span></Link>}</div>
          </div>
            <div className="fleet-hero-vehicle" aria-label={heroCar ? `Featured vehicle: ${heroCar.brand} ${heroCar.model}` : 'Milepost fleet'}>
             {heroCar ? <VehicleImage src={heroCar.imageUrl} alt={`${heroCar.brand} ${heroCar.model}`} fallbackAlt="Stylized vehicle illustration" loading="eager" /> : <div className="fleet-hero-placeholder" aria-hidden="true" />}
             <div className="fleet-hero-caption"><span>{heroCar ? 'A VEHICLE IN THE LIVE FLEET' : 'MILEPOST / CAR RENTALS'}</span><strong>{heroCar ? `${heroCar.brand} ${heroCar.model}` : 'Find your next drive'}</strong>{heroCar && <small>{getVehicleCategory(heroCar)} / {heroCar.seatingCapacity} seats</small>}</div>
          </div>
        </section>

        {featuredCar && <section className="featured-vehicle" aria-labelledby="featured-vehicle-title">
          <div className="featured-vehicle-image"><VehicleImage src={featuredCar.imageUrl} alt={`${featuredCar.brand} ${featuredCar.model}`} loading="eager" /><span>IN THE FLEET</span></div>
          <div className="featured-vehicle-copy"><p className="eyebrow">A CLOSER LOOK / {getVehicleCategory(featuredCar)}</p><h2 id="featured-vehicle-title">{featuredCar.brand} {featuredCar.model}</h2><p>{featuredCar.description || `${featuredCar.year} model with ${featuredCar.transmission?.toLowerCase()} transmission and ${featuredCar.seatingCapacity} seats.`}</p><div className="featured-vehicle-facts"><span>{featuredCar.year} model</span><span>{featuredCar.fuelType}</span><span>{featuredCar.seatingCapacity} seats</span></div><div className="featured-vehicle-action"><strong>{formatINR(featuredCar.pricePerDay)} <small>/ day</small></strong><button className="button button-secondary" type="button" onClick={() => openCarDetails(featuredCar.id)}>View vehicle <span aria-hidden="true">{'->'}</span></button></div></div>
        </section>}

        <form className="availability-form" onSubmit={applyAvailability}>
          <div className="availability-intro"><p className="eyebrow">PLAN YOUR RENTAL</p><strong>When are you heading out?</strong></div>
          <label><span>Pickup date</span><input type="date" min={today} value={dates.pickupDate} onChange={(event) => setDates({ ...dates, pickupDate: event.target.value })} /></label>
          <label><span>Return date</span><input type="date" min={dates.pickupDate || today} value={dates.returnDate} onChange={(event) => setDates({ ...dates, returnDate: event.target.value })} /></label>
          <button className="button button-primary" type="submit">Check availability <span aria-hidden="true">{'->'}</span></button>
          {activeDates && <button className="button button-quiet clear-dates" type="button" onClick={() => { setDates({ pickupDate: '', returnDate: '' }); setLoading(true); setError(''); setActiveDates(null) }}>Clear dates</button>}
        </form>

        {error && <div className="notice notice-error" role="alert"><span>{error}</span><button className="text-button" type="button" onClick={() => { setLoading(true); setError(''); setFleetRefresh((current) => current + 1) }}>Try again</button></div>}
        <div className="fleet-toolbar" id="fleet-inventory">
          <div><p className="eyebrow">MILEPOST VEHICLES</p><h2>Explore the fleet</h2><p className="fleet-result-count" aria-live="polite">{loading ? 'Loading available vehicles…' : `Showing ${visibleCars.length} of ${cars.length} available vehicles`}</p></div>
          <div className="fleet-tools">
            <label className="fleet-search"><span className="sr-only">Search vehicles</span><input type="search" placeholder="Search make, model, location..." value={fleetSearch} onChange={(event) => setFleetSearch(event.target.value)} /></label>
            <label className="fleet-filter"><span className="sr-only">Filter by vehicle category</span><select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}><option value="all">All vehicle types</option>{vehicleCategories.map((category) => <option key={category} value={category}>{category}</option>)}</select></label>
            <label className="fleet-filter"><span className="sr-only">Filter by fuel type</span><select value={fuelFilter} onChange={(event) => setFuelFilter(event.target.value)}><option value="all">All fuel types</option>{fuelTypes.map((fuelType) => <option key={fuelType} value={fuelType}>{fuelType}</option>)}</select></label>
            <label className="fleet-filter fleet-sort"><span className="sr-only">Sort vehicles</span><select value={fleetSort} onChange={(event) => setFleetSort(event.target.value)}><option value="default">Sort: fleet order</option><option value="price-asc">Price: low to high</option><option value="price-desc">Price: high to low</option><option value="year-desc">Model year: newest</option></select></label>
          </div>
        </div>

        {loading ? <div className="car-grid" role="status" aria-label="Loading vehicles">{Array.from({ length: 6 }, (_, index) => <div className="car-skeleton" key={index}><div className="skeleton-image" /><div className="skeleton-line skeleton-title" /><div className="skeleton-line" /><div className="skeleton-line skeleton-short" /></div>)}</div> : !error && cars.length === 0 ? <div className="empty-state"><strong>No vehicles match those dates.</strong><span>Try another date range or clear the filters.</span></div> : null}

        {!loading && !error && visibleCars.length > 0 && <div className="car-grid">
          {visibleCars.map((car) => {
            const imageUrl = hasUniqueImageUrl(car, cars) ? car.imageUrl.trim() : ''
            return <article className="car-card" key={car.id}>
            <div className="car-visual">
              <VehicleImage src={imageUrl} alt={`${car.brand} ${car.model}`} fallbackAlt="Stylized vehicle illustration" />
              <span className="availability-tag"><i /> {car.status}</span>
              <span className="vehicle-category-tag">{getVehicleCategory(car)}</span>
            </div>
            <div className="car-card-content">
              <div className="car-title-row"><div><p className="car-make">{car.brand} / {car.year}</p><h2>{car.model}</h2></div><span className="car-price">{formatINR(car.pricePerDay)}<small> / day</small></span></div>
              <div className="car-spec-list"><span>{car.transmission}</span><span>{car.fuelType}</span><span>{car.seatingCapacity} seats</span></div>
              <div className="car-card-footer"><span className="car-location">{car.location}</span><button className="button button-secondary card-action" type="button" onClick={() => openCarDetails(car.id)}>View & book <span aria-hidden="true">{'->'}</span></button></div>
            </div>
          </article>
          })}
        </div>}
        {!loading && !error && cars.length > 0 && visibleCars.length === 0 && <div className="empty-state"><strong>No vehicles found.</strong><span>Try another search or filter combination.</span><button className="text-button" type="button" onClick={() => { setFleetSearch(''); setFuelFilter('all'); setCategoryFilter('all') }}>Clear filters</button></div>}

        {!loading && !error && <section className="journey-section" aria-labelledby="journey-title">
          <div className="journey-heading"><p className="eyebrow">THREE SIMPLE STEPS</p><h2 id="journey-title">From search to the open road.</h2><p>Choose a vehicle from the live fleet. Your dates are checked by the rental service before a request is placed.</p></div>
          <div className="journey-steps">
            <article><span>01</span><h3>Find your fit</h3><p>Compare vehicle types, daily rates and specifications in one place.</p></article>
            <article><span>02</span><h3>Choose your dates</h3><p>Set pickup and return dates to check availability for your trip.</p></article>
            <article><span>03</span><h3>Send a request</h3><p>Sign in to request a booking, then follow its status from your account.</p></article>
          </div>
          <div className="journey-cta"><div><strong>Ready for your next drive?</strong><span>Explore the vehicles currently available in the fleet.</span></div><a className="button button-primary" href="#fleet-inventory">Browse vehicles <span aria-hidden="true">{'->'}</span></a></div>
        </section>}

        {detailsError && <div className="detail-error"><p className="notice notice-error" role="alert">{detailsError}</p><button className="button button-outline" type="button" onClick={() => navigate('/')}>Return to fleet</button></div>}
        {detailsLoading && <div className="dialog-backdrop"><section className="detail-dialog" role="status" aria-label="Vehicle details"><p>Loading vehicle details...</p></section></div>}
        {selectedCar && <div className="dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) closeVehicleDialog() }}>
          <section className="detail-dialog" ref={detailDialogRef} tabIndex="-1" role="dialog" aria-modal="true" aria-labelledby="detail-title">
            <button className="dialog-close" type="button" onClick={closeVehicleDialog} aria-label="Close vehicle details">x</button>
            <p className="eyebrow">VEHICLE / {selectedCar.id}</p><div className="detail-image"><VehicleImage src={hasUniqueImageUrl(selectedCar, cars) ? selectedCar.imageUrl : ''} alt={`${selectedCar.brand} ${selectedCar.model}`} fallbackAlt="Stylized vehicle illustration" loading="eager" /></div><h2 id="detail-title">{selectedCar.brand} {selectedCar.model}</h2>
            <p className="detail-description">{selectedCar.description || 'A well-maintained vehicle ready for your next trip.'}</p>
            <dl className="detail-specs">
              <div><dt>YEAR</dt><dd>{selectedCar.year}</dd></div><div><dt>TRANSMISSION</dt><dd>{selectedCar.transmission}</dd></div>
              <div><dt>FUEL</dt><dd>{selectedCar.fuelType}</dd></div><div><dt>SEATS</dt><dd>{selectedCar.seatingCapacity}</dd></div>
              <div><dt>LOCATION</dt><dd>{selectedCar.location}</dd></div><div><dt>TYPE</dt><dd>{getVehicleCategory(selectedCar)}</dd></div><div><dt>RATE</dt><dd>{formatINR(selectedCar.pricePerDay)} / day</dd></div>
            </dl>
            {user?.role === 'ADMIN' ? <p className="notice">Admin accounts cannot request customer bookings.</p> : <form className="booking-form" onSubmit={submitBooking}>
              <p className="eyebrow">REQUEST THIS VEHICLE</p>
              <div className="booking-date-fields">
                <label className="form-field"><span>Pickup</span><input type="date" min={today} required value={bookingDates.pickupDate} onChange={(event) => { setBookingDates({ ...bookingDates, pickupDate: event.target.value }); setBookingUnavailable(false); setBookingError('') }} /></label>
                <label className="form-field"><span>Return</span><input type="date" min={bookingDates.pickupDate || today} required value={bookingDates.returnDate} onChange={(event) => { setBookingDates({ ...bookingDates, returnDate: event.target.value }); setBookingUnavailable(false); setBookingError('') }} /></label>
              </div>
              {rentalDays > 0 && <p className="form-hint">Estimated total for {rentalDays} inclusive {rentalDays === 1 ? 'day' : 'days'}: <strong>{formatINR(estimatedTotal)}</strong> at the current daily rate.</p>}
              {vehicleUnavailable && <p className="notice notice-error" role="alert">This vehicle is no longer available. Choose another vehicle or date range.</p>}
              {!user && <p className="form-hint">Sign in as a customer to send a booking request.</p>}
              {bookingError && <p className="notice notice-error" role="alert">{bookingError}</p>}
              <button className="button button-primary form-submit" type="submit" disabled={bookingPending || vehicleUnavailable}>{bookingPending ? 'Sending request...' : vehicleUnavailable ? 'Vehicle unavailable' : user ? 'Request booking' : 'Sign in to request'}</button>
            </form>}
          </section>
        </div>}
      </section>}
      <Routes>
        <Route path="/" element={null} />
        <Route path="/cars/:id" element={null} />
        <Route path="/login" element={<AuthDialog key="login" mode="login" featuredCar={featuredCar} onAuthenticated={handleAuthenticated} />} />
        <Route path="/register" element={<AuthDialog key="register" mode="register" featuredCar={featuredCar} onAuthenticated={handleAuthenticated} />} />
        <Route path="/admin/login" element={<AuthDialog key="admin-login" mode="admin" featuredCar={featuredCar} onAuthenticated={handleAuthenticated} />} />
        <Route path="/bookings" element={<ProtectedRoute role="CUSTOMER"><CustomerDashboard key="bookings" view="bookings" onProfileUpdated={updateUser} bookingConfirmation={bookingConfirmation} /></ProtectedRoute>} />
        <Route path="/profile" element={<ProtectedRoute role="CUSTOMER"><CustomerDashboard key="profile" view="profile" onProfileUpdated={updateUser} /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute role="ADMIN"><AdminDashboard /></ProtectedRoute>} />
        <Route path="/admin/cars" element={<ProtectedRoute role="ADMIN"><AdminDashboard /></ProtectedRoute>} />
        <Route path="/admin/bookings" element={<ProtectedRoute role="ADMIN"><AdminDashboard /></ProtectedRoute>} />
        <Route path="*" element={<NotFound />} />
      </Routes>
      <footer className="page-footer"><div className="footer-brand"><span className="wordmark-mark">M</span><span>MILEPOST <small>CAR RENTALS</small></span></div><span>Choose your vehicle. Set your dates. Let the rental service confirm availability.</span><a href="#top">Back to top ↑</a></footer>
    </main>
  )
}

function NotFound() {
  return (
    <section className="workspace-panel">
      <p className="eyebrow">PAGE NOT FOUND</p>
      <h1>This route does not exist.</h1>
      <Link className="button button-primary" to="/">Return to the fleet</Link>
    </section>
  )
}

export default App
