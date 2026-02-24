import '@testing-library/jest-dom'

// Polyfill ResizeObserver para jsdom (usado por Radix UI primitives)
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
