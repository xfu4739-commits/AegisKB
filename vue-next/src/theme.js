// CSS is the single source of design values; Ant's algorithms need resolved colors.
export function createTheme(element = document.documentElement) {
  const css = getComputedStyle(element)
  const token = (name) => css.getPropertyValue(`--${name}`).trim()
  return {
    token: {
      colorPrimary: token('primary'),
      colorPrimaryHover: token('primary-hover'),
      colorPrimaryActive: token('primary-hover'),
      colorPrimaryBg: token('accent'),
      colorLink: token('primary'),
      colorLinkHover: token('primary-hover'),
      colorLinkActive: token('primary-hover'),
      colorText: token('text'),
      colorTextSecondary: token('muted'),
      colorBgContainer: token('surface'),
      colorBorder: token('border-strong'),
      fontFamily: token('font-sans'),
      borderRadius: Number.parseFloat(token('radius-control')),
      controlHeight: 40
    }
  }
}
