import { useState } from 'react'
import { getImageUrlError, vehicleImageFallback } from './vehicleImageUtils'

export default function VehicleImage({ src, alt, fallbackAlt = 'Stylized vehicle illustration', className, loading = 'lazy', ...props }) {
  const imageUrl = typeof src === 'string' ? src.trim() : ''
  const urlError = getImageUrlError(imageUrl)
  const [failedUrl, setFailedUrl] = useState(null)
  const useFallback = !imageUrl || Boolean(urlError) || failedUrl === imageUrl
  const source = useFallback ? vehicleImageFallback : imageUrl

  return (
    <img
      {...props}
      className={className}
      src={source}
      alt={useFallback ? fallbackAlt : alt}
      loading={loading}
      onError={() => {
        if (source !== vehicleImageFallback) setFailedUrl(imageUrl)
      }}
    />
  )
}
