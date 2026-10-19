export function formatINR(amount) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(Number(amount))
}

export function getVehicleCategory(car) {
  const name = `${car.brand} ${car.model}`.toLowerCase()
  if (/ertiga|innova|carens|xl6|marazzo/.test(name) || Number(car.seatingCapacity) >= 7) return 'MPV'
  if (/swift|baleno|i20|altroz|punch|tiago|polo|brio|jazz|wagon/.test(name)) return 'Hatchback'
  if (/city|corolla|slavia|verna|virtus|dzire|aura|amaze|civic|m3/.test(name)) return 'Sedan'
  if (/nexon|creta|seltos|venue|sonet|harrier|fortuner|thar|brezza|vitara|scorpio|xuv|taigun|kushaq|hector|safari/.test(name)) return 'SUV'
  return 'Car'
}
