import { useEffect } from 'react'

export default function useDialogAccessibility(dialogRef, onClose, active = true) {
  useEffect(() => {
    if (!active) return undefined
    const dialog = dialogRef.current
    if (!dialog) return undefined

    const previousFocus = document.activeElement
    const previousOverflow = document.body.style.overflow
    const focusableSelector = 'a[href], button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])'
    document.body.style.overflow = 'hidden'

    function handleKeyDown(event) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab') return

      const focusable = [...dialog.querySelectorAll(focusableSelector)]
      if (focusable.length === 0) {
        event.preventDefault()
        dialog.focus()
        return
      }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    dialog.addEventListener('keydown', handleKeyDown)
    const firstFocusable = dialog.querySelector(focusableSelector)
    ;(firstFocusable || dialog).focus()

    return () => {
      document.body.style.overflow = previousOverflow
      dialog.removeEventListener('keydown', handleKeyDown)
      if (previousFocus instanceof HTMLElement) previousFocus.focus()
    }
  }, [active, dialogRef, onClose])
}
