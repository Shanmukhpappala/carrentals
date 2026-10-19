export const vehicleImageFallback = '/car-placeholder.svg'

export function getImageUrlError(value) {
  const imageUrl = value.trim()
  if (!imageUrl) return ''

  try {
    const parsedUrl = new URL(imageUrl)
    const allowedProtocol = parsedUrl.protocol === 'https:' || parsedUrl.protocol === 'http:'
    if (!allowedProtocol || !parsedUrl.hostname || parsedUrl.username || parsedUrl.password) {
      return 'Enter an absolute HTTP or HTTPS image URL.'
    }
    return ''
  } catch {
    return 'Enter a valid image URL, including https://.'
  }
}