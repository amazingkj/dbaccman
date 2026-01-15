/**
 * Theme constants for consistent styling across the application.
 */

// Color palette
export const colors = {
  // Primary colors
  primary: '#5d87ff',
  primaryLight: 'rgba(93, 135, 255, 0.1)',
  primaryHover: '#4570ea',

  // Status colors
  success: '#13deb9',
  successLight: 'rgba(19, 222, 185, 0.1)',
  warning: '#ffae1f',
  warningLight: 'rgba(255, 174, 31, 0.1)',
  danger: '#fa896b',
  dangerLight: 'rgba(250, 137, 107, 0.1)',
  error: '#ff4d4f',
  errorLight: 'rgba(255, 77, 79, 0.1)',
  info: '#49beff',
  infoLight: 'rgba(73, 190, 255, 0.1)',

  // Additional colors
  purple: '#845ef7',
  purpleLight: 'rgba(132, 94, 247, 0.1)',
  red: '#ff6b6b',
  redLight: 'rgba(255, 107, 107, 0.1)',

  // Neutral colors
  text: {
    primary: '#2a3547',
    secondary: '#5a6a85',
    muted: '#8c8c8c',
    disabled: '#bfbfbf',
  },

  // Background colors
  background: {
    default: '#f5f5f5',
    paper: '#ffffff',
    hover: '#fafafa',
    dark: '#2a3547',
  },

  // Border colors
  border: {
    default: '#d9d9d9',
    light: '#f0f0f0',
    dark: '#e0e0e0',
  },
}

// Spacing scale (in pixels)
export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  xxxl: 48,
}

// Border radius
export const borderRadius = {
  sm: 4,
  md: 8,
  lg: 12,
  xl: 16,
  round: '50%',
}

// Box shadows
export const shadows = {
  sm: '0 1px 3px rgba(0,0,0,0.08)',
  md: '0 4px 6px rgba(0,0,0,0.1)',
  lg: '0 6px 16px rgba(0,0,0,0.12)',
  xl: '0 10px 25px rgba(0,0,0,0.15)',
}

// Typography
export const typography = {
  fontFamily: {
    sans: "-apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', system-ui, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
    mono: "'Fira Code', 'Consolas', 'Monaco', monospace",
  },
  fontSize: {
    xs: 11,
    sm: 12,
    md: 13,
    base: 14,
    lg: 16,
    xl: 18,
    '2xl': 22,
    '3xl': 24,
    '4xl': 28,
  },
  fontWeight: {
    normal: 400,
    medium: 500,
    semibold: 600,
    bold: 700,
  },
  lineHeight: {
    tight: 1.2,
    normal: 1.5,
    relaxed: 1.75,
  },
}

// Icon sizes
export const iconSize = {
  xs: 12,
  sm: 14,
  md: 16,
  lg: 18,
  xl: 24,
  xxl: 32,
}

// Z-index scale
export const zIndex = {
  dropdown: 1000,
  sticky: 1020,
  fixed: 1030,
  modalBackdrop: 1040,
  modal: 1050,
  popover: 1060,
  tooltip: 1070,
  notification: 1080,
}

// Transitions
export const transitions = {
  fast: '0.15s ease',
  normal: '0.3s ease',
  slow: '0.5s ease',
}

// Component-specific styles
export const components = {
  card: {
    borderRadius: borderRadius.lg,
    shadow: shadows.sm,
    padding: spacing.lg,
  },
  button: {
    borderRadius: borderRadius.md,
  },
  input: {
    borderRadius: borderRadius.md,
  },
  modal: {
    widthSmall: 400,
    widthMedium: 500,
    widthLarge: 600,
    widthExtraLarge: 800,
  },
  table: {
    headerBg: colors.background.default,
  },
}

// Export default theme object
export default {
  colors,
  spacing,
  borderRadius,
  shadows,
  typography,
  iconSize,
  zIndex,
  transitions,
  components,
}
