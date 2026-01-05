/**
 * Format a date string from the server to local timezone
 * Server sends dates in format 'YYYY-MM-DD HH:mm:ss' (assumed UTC)
 */
export function formatToLocalTime(dateString: string | null | undefined): string {
  if (!dateString) return '-'

  try {
    // Treat the server time as UTC by appending 'Z'
    const utcDate = new Date(dateString.replace(' ', 'T') + 'Z')

    if (isNaN(utcDate.getTime())) {
      return dateString // Return original if parsing fails
    }

    // Format to local timezone
    return utcDate.toLocaleString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    })
  } catch {
    return dateString // Return original if any error
  }
}

/**
 * Format a date string to a short format (date only)
 */
export function formatToLocalDate(dateString: string | null | undefined): string {
  if (!dateString) return '-'

  try {
    const utcDate = new Date(dateString.replace(' ', 'T') + 'Z')

    if (isNaN(utcDate.getTime())) {
      return dateString
    }

    return utcDate.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    })
  } catch {
    return dateString
  }
}
