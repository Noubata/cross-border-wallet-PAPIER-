import Decimal from 'decimal.js'

export type Language = 'en' | 'fr'
export type Role = 'USER' | 'ADMIN'
export type Currency = 'NGN' | 'XAF'
export type AuthResponse = {
  userId: string
  email: string
  role: Role
  accessToken: string
  refreshToken: string
  accessTokenExpiresInSeconds: number
}
export type Profile = { customerId: string; preferredLanguage: Language; kycStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' }
export type Wallet = { id: string; currency: Currency; balance: string; status: 'ACTIVE' | 'FROZEN'; createdAt: string }
export type Transfer = {
  id: string
  sourceWalletId: string
  destinationWalletId: string
  amount: string
  currency: Currency
  fee: string
  state: 'PENDING' | 'FLAGGED' | 'COMPLETED' | 'FAILED'
  failureCode: string | null
  createdAt: string
  completedAt: string | null
  destinationAmount?: string
  destinationCurrency?: Currency
  quoteId?: string | null
}
export type Quote = {
  id: string
  sourceCurrency: Currency
  destinationCurrency: Currency
  sourceAmount: string
  destinationAmount: string
  grossDestinationAmount: string
  flatFee: string
  spreadFee: string
  midRate: string
  effectiveRate: string
  expiresAt: string
}
export type FundingPayment = {
  id: string
  reference: string
  walletId: string
  amount: string
  currency: Currency
  status: string
  authorizationUrl: string | null
  createdAt: string
  completedAt: string | null
}
export type AuditEvent = {
  id: string
  actorId: string | null
  action: string
  entityType: string
  entityId: string | null
  metadata: Record<string, unknown>
  createdAt: string
}
type Problem = { detail?: string; title?: string; status?: number }

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const SESSION_KEY = 'crossborder.session'

function readStoredSession(): AuthResponse | null {
  try {
    const stored = localStorage.getItem(SESSION_KEY)
    return stored ? JSON.parse(stored) as AuthResponse : null
  } catch {
    return null
  }
}

export class ApiClient {
  session: AuthResponse | null = readStoredSession()
  language: Language = (localStorage.getItem('crossborder.language') as Language) || 'en'
  onSessionChange: (session: AuthResponse | null) => void = () => undefined

  setSession(session: AuthResponse | null) {
    this.session = session
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else localStorage.removeItem(SESSION_KEY)
    this.onSessionChange(session)
  }

  setLanguage(language: Language) {
    this.language = language
    localStorage.setItem('crossborder.language', language)
  }

  async request<T>(path: string, init: RequestInit = {}, allowRefresh = true): Promise<T> {
    const headers = new Headers(init.headers)
    headers.set('Accept-Language', this.language)
    if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
    if (this.session?.accessToken) headers.set('Authorization', `Bearer ${this.session.accessToken}`)
    const response = await fetch(`${API_BASE}${path}`, { ...init, headers })

    if (response.status === 401 && allowRefresh && this.session?.refreshToken && !path.startsWith('/api/auth/')) {
      try {
        const refreshed = await this.request<AuthResponse>('/api/auth/refresh', {
          method: 'POST',
          body: JSON.stringify({ refreshToken: this.session.refreshToken }),
        }, false)
        this.setSession(refreshed)
        return this.request<T>(path, init, false)
      } catch {
        this.setSession(null)
        throw new Error(this.language === 'fr' ? 'Session expirée. Veuillez vous reconnecter.' : 'Session expired. Please sign in again.')
      }
    }

    if (!response.ok) {
      let problem: Problem = {}
      try { problem = await response.json() as Problem } catch { /* Empty gateway response */ }
      throw new Error(problem.detail || problem.title || `Request failed (${response.status})`)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }
}

export const api = new ApiClient()
export const createIdempotencyKey = () => crypto.randomUUID()

export function formatMoney(value: string | number | Decimal, currency: Currency, language: Language) {
  const amount = Decimal.isDecimal(value) ? (value as Decimal).toNumber() : Number(value)
  return new Intl.NumberFormat(language === 'fr' ? 'fr-FR' : 'en-GB', {
    style: 'currency', currency,
    maximumFractionDigits: currency === 'NGN' ? 2 : 0,
  }).format(amount)
}

export function formatDate(value: string, language: Language) {
  return new Intl.DateTimeFormat(language === 'fr' ? 'fr-FR' : 'en-GB', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value))
}