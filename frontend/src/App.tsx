import { useEffect, useEffectEvent, useMemo, useState } from 'react'
import Decimal from 'decimal.js'
import {
  Activity, ArrowDownLeft, ArrowLeftRight, ArrowRight, BadgeCheck,
  Bell, Check, CircleHelp, Clock3, CreditCard, FileClock,
  Globe2, LayoutDashboard, LogOut, Menu, Plus, RefreshCw, ShieldCheck,
  ShieldX, Wallet as WalletIcon, X,
} from 'lucide-react'
import {
  api, createIdempotencyKey, formatDate, formatMoney,
  type AuditEvent, type AuthResponse, type Currency, type FundingPayment,
  type Language, type Profile, type Quote, type Transfer, type Wallet,
} from './api'
import './App.css'

type View = 'overview' | 'send' | 'exchange' | 'funding' | 'admin'

const text = {
  en: {
    brand: 'CrossBorder', workspace: 'PERSONAL SPACE', overview: 'Overview', send: 'Send money', exchange: 'Exchange', funding: 'Add funds', admin: 'Operations', help: 'Help',
    overviewTitle: 'Your wallets', overviewSub: 'Balances across NGN and XAF.', accounts: 'Wallet accounts', addNgn: 'Add NGN wallet', addXaf: 'Add XAF wallet', total: 'TOTAL WALLET VALUE', kyc: 'KYC status', active: 'ACTIVE', opened: 'Opened', sendAction: 'Send', addAction: 'Add funds', noWallets: 'Start with a wallet', noWalletsSub: 'Create an NGN or XAF wallet to receive and move money.', activity: 'Recent activity', noActivity: 'Your transactions will appear here', noActivitySub: 'The API does not currently expose a transaction history list.', shortcuts: 'Quick actions', sendWallet: 'Send to a wallet', sendWalletSub: 'Same-currency transfer', convert: 'Exchange currencies', convertSub: 'Quote valid for 60 seconds', addFunds: 'Add funds', addFundsSub: 'Sandbox payment intent', refresh: 'Refresh',
    transferTitle: 'Send money', transferSub: 'Move funds between wallets in the same currency.', transferDetails: 'Transfer details', fromWallet: 'From wallet', chooseWallet: 'Choose a wallet', recipient: 'Recipient wallet ID', recipientHint: 'Enter a known wallet ID. The backend does not provide recipient search.', amount: 'Amount', review: 'Review transfer', busy: 'Working…', transferNotice: 'Risk checks run before funds move. Large or frequent transfers may be held for review.', transferResult: 'Transfer result', completed: 'completed', flagged: 'flagged', failed: 'failed', noFundsMoved: 'No funds moved. An administrator must review this transfer.', newTransfer: 'Start another transfer',
    exchangeTitle: 'Currency exchange', exchangeSub: 'Get a quote, review the fees, then confirm.', quote: 'Exchange quote', youSend: 'You send', youReceive: 'You receive', sourceAmount: 'Source amount', sourceWallet: 'Source wallet', quoteButton: 'Get quote', liveQuote: 'LIVE QUOTE', effectiveRate: 'Effective rate', flatFee: 'Flat fee', spread: 'Spread included', destinationWallet: 'Destination wallet', chooseDestination: 'Choose destination wallet', confirmExchange: 'Confirm exchange', quoteExpired: 'Quote expired', quoteNudge: 'Your quote locks amounts and fees for 60 seconds. The server applies the final calculation.',
    fundingTitle: 'Add funds', fundingSub: 'Create a sandbox payment intent.', paymentIntent: 'Payment details', walletToFund: 'Wallet to fund', payerEmail: 'Payer email', createIntent: 'Create payment intent', paymentReference: 'PAYMENT REFERENCE', pending: 'PENDING', paymentPending: 'Payment created. The wallet is credited only after the server accepts a valid signed gateway webhook.', sandboxNotice: 'Sandbox only. A verified gateway webhook is required to credit this wallet.', continuePayment: 'Continue to payment', anotherIntent: 'Create another intent',
    adminTitle: 'Operations review', adminSub: 'Review flagged transfers and sensitive activity.', flaggedTransfers: 'Flagged transfers', auditLog: 'Audit log', recentEvents: 'recent events', noFlagged: 'No flagged transfers', noFlaggedSub: 'Flagged transfers wait here until reviewed.', approve: 'Approve', reject: 'Reject', controlTitle: 'Operations controls', controlSub: 'Decisions are audited. Approval may post ledger entries.', walletId: 'Wallet ID', freeze: 'Freeze wallet', unfreeze: 'Unfreeze wallet',
    signIn: 'Sign in', createAccount: 'Create account', welcome: 'Welcome back.', signInSub: 'Access your cross-border wallets.', email: 'Email address', password: 'Password', passwordHint: 'At least 12 characters', signInButton: 'Sign in', createButton: 'Create account', protected: 'Your information is protected.',
    errorOffline: 'Could not reach the API. Check that the backend is running.', language: 'Language', account: 'Account', profile: 'Customer profile', yourWallet: 'YOUR WALLETS', sameCurrency: 'This flow supports same-currency transfers only.', networkError: 'Something went wrong.', quoteWarning: 'Use a fresh quote if this one expires.', secure: 'Secure account', noWalletOption: 'Create a matching wallet first.', amountInvalid: 'Enter a valid positive amount.', selectWallets: 'Choose both wallets before continuing.', noQuote: 'Request a quote before confirming.', confirmTitle: 'Confirm this action?', rejectReason: 'Review reason', reviewQueue: 'REVIEW QUEUE', auditEmpty: 'No audit events available.', flaggedHold: 'Held for review', feeAndRate: 'Rate and fees are supplied by the server.', signOut: 'Sign out', allBalances: 'Separate currency balances', accountType: 'PERSONAL WALLET', navLabel: 'MAIN MENU', sidebarHint: 'NGN · XAF', quickSend: 'Transfer', authFooter: 'Built for cross-border life.', dataStatus: 'Live account data', balance: 'Available balance', copied: 'Copied', copiedId: 'Wallet ID copied', copy: 'Copy ID', frozen: 'FROZEN', rate: 'Indicative reference', rateValue: '1 NGN = 0.42 XAF', fieldRequired: 'This field is required.',
  },
  fr: {
    brand: 'CrossBorder', workspace: 'ESPACE PERSONNEL', overview: 'Vue d’ensemble', send: 'Envoyer', exchange: 'Changer', funding: 'Ajouter des fonds', admin: 'Opérations', help: 'Aide',
    overviewTitle: 'Vos portefeuilles', overviewSub: 'Soldes en NGN et XAF.', accounts: 'Comptes portefeuille', addNgn: 'Ajouter NGN', addXaf: 'Ajouter XAF', total: 'VALEUR TOTALE', kyc: 'Statut KYC', active: 'ACTIF', opened: 'Ouvert le', sendAction: 'Envoyer', addAction: 'Alimenter', noWallets: 'Commencez avec un portefeuille', noWalletsSub: 'Créez un portefeuille NGN ou XAF pour recevoir et envoyer des fonds.', activity: 'Activité récente', noActivity: 'Vos opérations apparaîtront ici', noActivitySub: 'L’API ne fournit pas encore de liste d’historique.', shortcuts: 'Actions rapides', sendWallet: 'Envoyer à un portefeuille', sendWalletSub: 'Transfert dans la même devise', convert: 'Changer de devise', convertSub: 'Cotation valable 60 secondes', addFunds: 'Ajouter des fonds', addFundsSub: 'Intention de paiement sandbox', refresh: 'Actualiser',
    transferTitle: 'Envoyer de l’argent', transferSub: 'Transférez entre portefeuilles dans la même devise.', transferDetails: 'Détails du transfert', fromWallet: 'Portefeuille source', chooseWallet: 'Choisir un portefeuille', recipient: 'Identifiant du portefeuille destinataire', recipientHint: 'Saisissez un identifiant connu. Le backend ne propose pas de recherche de destinataire.', amount: 'Montant', review: 'Vérifier le transfert', busy: 'En cours…', transferNotice: 'Les contrôles de risque s’exécutent avant le mouvement des fonds.', transferResult: 'Résultat du transfert', completed: 'terminé', flagged: 'signalé', failed: 'échoué', noFundsMoved: 'Aucun fonds déplacé. Un administrateur doit examiner ce transfert.', newTransfer: 'Nouveau transfert',
    exchangeTitle: 'Change de devise', exchangeSub: 'Obtenez une cotation, vérifiez les frais, puis confirmez.', quote: 'Cotation de change', youSend: 'Vous envoyez', youReceive: 'Vous recevez', sourceAmount: 'Montant source', sourceWallet: 'Portefeuille source', quoteButton: 'Obtenir une cotation', liveQuote: 'COTATION ACTIVE', effectiveRate: 'Taux effectif', flatFee: 'Frais fixes', spread: 'Marge incluse', destinationWallet: 'Portefeuille destinataire', chooseDestination: 'Choisir un portefeuille', confirmExchange: 'Confirmer le change', quoteExpired: 'Cotation expirée', quoteNudge: 'Les montants et frais sont verrouillés pendant 60 secondes.',
    fundingTitle: 'Ajouter des fonds', fundingSub: 'Créez une intention de paiement sandbox.', paymentIntent: 'Détails du paiement', walletToFund: 'Portefeuille à créditer', payerEmail: 'E-mail du payeur', createIntent: 'Créer une intention', paymentReference: 'RÉFÉRENCE DE PAIEMENT', pending: 'EN ATTENTE', paymentPending: 'Le portefeuille est crédité après réception d’un webhook signé valide.', sandboxNotice: 'Sandbox uniquement. Un webhook vérifié est nécessaire pour créditer ce portefeuille.', continuePayment: 'Continuer vers le paiement', anotherIntent: 'Créer une autre intention',
    adminTitle: 'Revue des opérations', adminSub: 'Examinez les transferts signalés et les actions sensibles.', flaggedTransfers: 'Transferts signalés', auditLog: 'Journal d’audit', recentEvents: 'événements récents', noFlagged: 'Aucun transfert signalé', noFlaggedSub: 'Les transferts signalés attendent une validation.', approve: 'Approuver', reject: 'Rejeter', controlTitle: 'Contrôles opérationnels', controlSub: 'Les décisions sont auditées. Une approbation peut déplacer des fonds.', walletId: 'Identifiant du portefeuille', freeze: 'Geler', unfreeze: 'Dégeler',
    signIn: 'Connexion', createAccount: 'Créer un compte', welcome: 'Bon retour.', signInSub: 'Accédez à vos portefeuilles transfrontaliers.', email: 'Adresse e-mail', password: 'Mot de passe', passwordHint: '12 caractères minimum', signInButton: 'Se connecter', createButton: 'Créer un compte', protected: 'Vos informations sont protégées.',
    errorOffline: 'API inaccessible. Vérifiez que le backend est démarré.', language: 'Langue', account: 'Compte', profile: 'Profil client', yourWallet: 'VOS PORTEFEUILLES', sameCurrency: 'Ce flux accepte uniquement les transferts dans la même devise.', amountInvalid: 'Saisissez un montant positif valide.', selectWallets: 'Choisissez les deux portefeuilles.', noQuote: 'Demandez une cotation avant de confirmer.', confirmTitle: 'Confirmer cette action ?', rejectReason: 'Motif de décision', reviewQueue: 'FILE DE REVUE', auditEmpty: 'Aucun événement d’audit disponible.', flaggedHold: 'En attente de revue', feeAndRate: 'Le taux et les frais proviennent du serveur.', signOut: 'Déconnexion', allBalances: 'Soldes séparés par devise', accountType: 'PORTEFEUILLE PERSONNEL', navLabel: 'MENU PRINCIPAL', sidebarHint: 'NGN · XAF', quickSend: 'Transfert', authFooter: 'Pensé pour les échanges transfrontaliers.', dataStatus: 'Données du compte en direct', balance: 'Solde disponible', copied: 'Copié', copiedId: 'Identifiant copié', copy: 'Copier ID', frozen: 'GELÉ', rate: 'Taux de référence', rateValue: '1 NGN = 0,42 XAF', fieldRequired: 'Ce champ est obligatoire.', networkError: 'Une erreur est survenue.', secure: 'Compte sécurisé', noWalletOption: 'Créez d’abord un portefeuille correspondant.',
  },
} satisfies Record<Language, Record<string, string>>

function App() {
  const [session, setSession] = useState<AuthResponse | null>(api.session)
  const [language, setLanguage] = useState<Language>(api.language)
  const [view, setView] = useState<View>('overview')
  const [profile, setProfile] = useState<Profile | null>(null)
  const [wallets, setWallets] = useState<Wallet[]>([])
  const [flagged, setFlagged] = useState<Transfer[]>([])
  const [audit, setAudit] = useState<AuditEvent[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [mobileNav, setMobileNav] = useState(false)
  const t: Copy = { ...text.en, ...text[language] }
  const refreshDashboard = useEffectEvent(() => { void loadDashboard() })
  const refreshAdmin = useEffectEvent(() => { void loadAdmin() })

  useEffect(() => { api.onSessionChange = setSession }, [])
  useEffect(() => { api.setLanguage(language) }, [language])
  useEffect(() => { if (session) refreshDashboard() }, [session])
  useEffect(() => { if (view === 'admin' && session?.role === 'ADMIN') refreshAdmin() }, [view, session])

  async function loadDashboard() {
    setLoading(true); setError('')
    try {
      const [nextProfile, nextWallets] = await Promise.all([
        api.request<Profile>('/api/customers/me'), api.request<Wallet[]>('/api/wallets'),
      ])
      setProfile(nextProfile); setWallets(nextWallets)
    } catch (reason) { setError(errorText(reason, t.errorOffline)) } finally { setLoading(false) }
  }
  async function loadAdmin() {
    setLoading(true); setError('')
    try {
      const [nextFlagged, nextAudit] = await Promise.all([
        api.request<Transfer[]>('/api/admin/transfers/flagged'), api.request<AuditEvent[]>('/api/admin/audit?limit=100'),
      ])
      setFlagged(nextFlagged); setAudit(nextAudit)
    } catch (reason) { setError(errorText(reason, t.errorOffline)) } finally { setLoading(false) }
  }
  function announce(value: string) { setNotice(value); window.setTimeout(() => setNotice(''), 4000) }
  async function signOut() {
    const current = session
    try { if (current) await api.request<void>('/api/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken: current.refreshToken }) }) } catch { /* Clear local session even if server logout is unreachable. */ }
    api.setSession(null); setView('overview')
  }
  async function createWallet(currency: Currency) {
    try { await api.request<Wallet>('/api/wallets', { method: 'POST', body: JSON.stringify({ currency }) }); await loadDashboard(); announce(language === 'fr' ? `Portefeuille ${currency} créé` : `${currency} wallet created`) }
    catch (reason) { setError(errorText(reason, t.networkError)) }
  }
  function navigate(next: View) { setView(next); setMobileNav(false); setError('') }

  if (!session) return <AuthScreen language={language} t={t} onLanguage={setLanguage} onSession={value => api.setSession(value)} />

  const items: { id: View; label: string; icon: typeof LayoutDashboard }[] = [
    { id: 'overview', label: t.overview, icon: LayoutDashboard },
    { id: 'send', label: t.send, icon: ArrowLeftRight },
    { id: 'exchange', label: t.exchange, icon: RefreshCw },
    { id: 'funding', label: t.funding, icon: CreditCard },
    ...(session.role === 'ADMIN' ? [{ id: 'admin' as View, label: t.admin, icon: ShieldCheck }] : []),
  ]

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav ? 'sidebar-open' : ''}`}>
      <a className="brand" href="#home" onClick={() => navigate('overview')}><span className="brand-mark"><ArrowLeftRight size={18} /></span><span>{t.brand}<i>.</i></span></a>
      <div className="sidebar-caption">{t.workspace}</div>
      <nav className="sidebar-nav" aria-label={t.navLabel}>{items.map(item => <button key={item.id} className={`nav-item ${view === item.id ? 'selected' : ''}`} onClick={() => navigate(item.id)}><item.icon size={18} strokeWidth={1.8} /><span>{item.label}</span>{view === item.id && <span className="active-mark" />}</button>)}</nav>
      <div className="sidebar-bottom"><div className="rate-card"><span className="live-dot" /><small>{t.rate}</small><strong>{t.rateValue}</strong></div><button className="nav-item help-link" onClick={() => announce(t.help)}><CircleHelp size={18} /><span>{t.help}</span></button></div>
    </aside>
    {mobileNav && <button className="mobile-scrim" aria-label="Close menu" onClick={() => setMobileNav(false)} />}
    <main className="main-area">
      <header className="topbar"><button className="icon-button mobile-menu" aria-label="Open menu" onClick={() => setMobileNav(true)}><Menu size={20} /></button><div className="crumb"><span>{t.workspace}</span><b>/</b><strong>{items.find(item => item.id === view)?.label}</strong></div><div className="top-actions"><label className="language-picker"><Globe2 size={15} /><select aria-label={t.language} value={language} onChange={event => setLanguage(event.target.value as Language)}><option value="en">EN</option><option value="fr">FR</option></select></label><button className="icon-button notification" aria-label="Notifications" onClick={() => announce(language === 'fr' ? 'Aucune nouvelle notification.' : 'No new notifications.')}><Bell size={18} /></button><span className="top-divider" /><button className="account-button" onClick={() => void signOut()}><span className="avatar">{session.email[0]?.toUpperCase()}</span><span className="account-email">{session.email}</span><LogOut size={15} aria-label={t.signOut} /></button></div></header>
      <div className="content-area">
        <div className="page-heading"><div><p className="eyebrow">CROSS-BORDER WALLET</p><h1>{pageTitle(view, t)}</h1><p className="page-subtitle">{pageSubtitle(view, t)}</p></div><div className="secure-badge"><ShieldCheck size={15} />{t.secure}</div></div>
        {error && <div className="alert alert-error" role="alert"><ShieldX size={17} /><span>{error}</span><button aria-label="Dismiss" onClick={() => setError('')}><X size={16} /></button></div>}
        {notice && <div className="alert alert-success" role="status"><BadgeCheck size={17} /><span>{notice}</span><button aria-label="Dismiss" onClick={() => setNotice('')}><X size={16} /></button></div>}
        {loading && <div className="loading-track"><span /></div>}
        {view === 'overview' && <Overview language={language} t={t} session={session} profile={profile} wallets={wallets} loading={loading} onCreate={createWallet} onNavigate={navigate} onRefresh={loadDashboard} />}
        {view === 'send' && <TransferScreen language={language} t={t} wallets={wallets} onDone={async value => { announce(value); await loadDashboard() }} />}
        {view === 'exchange' && <ExchangeScreen language={language} t={t} wallets={wallets} onDone={async value => { announce(value); await loadDashboard() }} />}
        {view === 'funding' && <FundingScreen language={language} t={t} wallets={wallets} onDone={announce} />}
        {view === 'admin' && session.role === 'ADMIN' && <AdminScreen language={language} t={t} flagged={flagged} audit={audit} onRefresh={loadAdmin} onDone={async value => { announce(value); await loadAdmin() }} />}
      </div>
      <footer className="page-footer"><span>CrossBorder Wallet</span><span>{t.authFooter}</span><span>NGN · XAF</span></footer>
    </main>
  </div>
}

type Copy = typeof text.en
function pageTitle(view: View, t: Copy) { return ({ overview: t.overviewTitle, send: t.transferTitle, exchange: t.exchangeTitle, funding: t.fundingTitle, admin: t.adminTitle })[view] }
function pageSubtitle(view: View, t: Copy) { return ({ overview: t.overviewSub, send: t.transferSub, exchange: t.exchangeSub, funding: t.fundingSub, admin: t.adminSub })[view] }
function errorText(reason: unknown, fallback: string) { return reason instanceof Error ? reason.message : fallback }
function newKey() { return createIdempotencyKey() }

function AuthScreen({ language, t, onLanguage, onSession }: { language: Language; t: Copy; onLanguage: (language: Language) => void; onSession: (session: AuthResponse) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const response = await api.request<AuthResponse>(mode === 'login' ? '/api/auth/login' : '/api/auth/register', {
        method: 'POST',
        body: JSON.stringify(mode === 'login' ? { email, password } : { email, password, preferredLanguage: language }),
      })
      onSession(response)
    } catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusy(false) }
  }
  return <div className="auth-layout"><section className="auth-visual"><div className="auth-visual-head"><Brand label={t.brand} light /><LanguageControl language={language} onLanguage={onLanguage} label={t.language} /></div><div className="auth-visual-body"><span className="visual-kicker"><i /> NGN / XAF WALLET</span><h1>Money moves<br />beyond borders.</h1><p>One clear view of your balances, transfers, and exchange quotes.</p><div className="visual-rate"><span>{t.rate.toUpperCase()}</span><strong>1 NGN <ArrowRight size={17} /> 0.42 XAF</strong><small>Indicative only · confirm with a live quote</small></div></div><div className="auth-visual-foot"><span>01 / NGN</span><span>02 / XAF</span><span>SECURE BY DESIGN</span></div></section><section className="auth-panel"><div className="auth-mobile-head"><Brand label={t.brand} /><LanguageControl language={language} onLanguage={onLanguage} label={t.language} /></div><div className="auth-card"><p className="eyebrow">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</p><h2>{mode === 'login' ? t.welcome : t.createAccount}</h2><p className="auth-subtitle">{t.signInSub}</p><div className="auth-tabs"><button className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError('') }}>{t.signIn}</button><button className={mode === 'register' ? 'active' : ''} onClick={() => { setMode('register'); setError('') }}>{t.createAccount}</button></div><form className="form-stack" onSubmit={submit}><label>{t.email}<input type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="name@example.com" required /></label><label>{t.password}<input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => setPassword(event.target.value)} minLength={12} maxLength={72} placeholder={t.passwordHint} required /></label>{error && <div className="form-error" role="alert">{error}</div>}<button className="primary-button full-button" disabled={busy}>{busy ? <Spinner /> : <>{mode === 'login' ? t.signInButton : t.createButton}<ArrowRight size={16} /></>}</button></form><div className="assurance"><ShieldCheck size={16} /><span>{t.protected}</span></div></div><footer className="auth-footer">© 2026 CrossBorder Wallet <span>·</span> {t.authFooter}</footer></section></div>
}

function Brand({ label, light = false }: { label: string; light?: boolean }) { return <div className={`brand ${light ? 'brand-light' : ''}`}><span className="brand-mark"><ArrowLeftRight size={17} /></span><strong>{label}<i>.</i></strong></div> }
function LanguageControl({ language, onLanguage, label }: { language: Language; onLanguage: (language: Language) => void; label: string }) { return <label className="language-picker"><Globe2 size={15} /><span className="visually-hidden">{label}</span><select aria-label={label} value={language} onChange={event => onLanguage(event.target.value as Language)}><option value="en">EN</option><option value="fr">FR</option></select></label> }
function Spinner() { return <span className="spinner" aria-label="Loading" /> }

function Overview({ language, t, session, profile, wallets, loading, onCreate, onNavigate, onRefresh }: { language: Language; t: Copy; session: AuthResponse; profile: Profile | null; wallets: Wallet[]; loading: boolean; onCreate: (currency: Currency) => void; onNavigate: (view: View) => void; onRefresh: () => void }) {
  const totals = useMemo(() => wallets.reduce((result, wallet) => ({ ...result, [wallet.currency]: (result[wallet.currency] || new Decimal(0)).plus(wallet.balance) }), {} as Partial<Record<Currency, Decimal>>), [wallets])
  return <div className="overview-view"><section className="balance-banner"><div className="balance-banner-copy"><div className="live-label"><span className="live-dot" />{t.dataStatus}</div><p className="eyebrow">{t.total}</p><div className="totals-row"><strong>{moneySummary(totals.NGN, 'NGN', language)}</strong><span className="totals-plus">+</span><strong>{moneySummary(totals.XAF, 'XAF', language)}</strong></div><div className="banner-meta"><span>{session.email}</span><span className="profile-state"><i />{profile?.kycStatus || '—'} KYC</span></div></div><div className="balance-banner-side"><div className="banner-orbit orbit-one" /><div className="banner-orbit orbit-two" /><span className="banner-code">CB / 01</span><button className="banner-refresh" onClick={onRefresh}><RefreshCw size={14} />{t.refresh}</button></div></section>
  <section className="section-block"><div className="section-head"><div><p className="eyebrow">01 — ACCOUNTS</p><h2>{t.accounts}</h2></div><div className="head-actions"><button className="outline-button" onClick={() => onCreate('NGN')}><Plus size={15} />{t.addNgn}</button><button className="outline-button" onClick={() => onCreate('XAF')}><Plus size={15} />{t.addXaf}</button></div></div><div className="wallet-grid">{wallets.map(wallet => <WalletCard key={wallet.id} wallet={wallet} language={language} t={t} onSend={() => onNavigate('send')} onFund={() => onNavigate('funding')} />)}{!wallets.length && !loading && <div className="empty-card"><span className="empty-wallet-mark"><WalletIcon size={21} /></span><h3>{t.noWallets}</h3><p>{t.noWalletsSub}</p><div><button className="primary-button small-button" onClick={() => onCreate('NGN')}><Plus size={15} />NGN</button><button className="outline-button small-button" onClick={() => onCreate('XAF')}><Plus size={15} />XAF</button></div></div>}</div></section>
  <section className="bottom-panels"><div className="activity-panel"><div className="section-head compact-head"><div><p className="eyebrow">02 — ACTIVITY</p><h2>{t.activity}</h2></div><Activity size={19} className="muted-icon" /></div><div className="activity-empty"><span className="empty-activity-icon"><ArrowDownLeft size={16} /></span><div><strong>{t.noActivity}</strong><small>{t.noActivitySub}</small></div></div></div><div className="shortcuts-panel"><p className="eyebrow">03 — SHORTCUTS</p><h2>{t.shortcuts}</h2><Shortcut icon={<ArrowLeftRight size={17} />} tone="coral" title={t.sendWallet} subtitle={t.sendWalletSub} onClick={() => onNavigate('send')} /><Shortcut icon={<RefreshCw size={17} />} tone="green" title={t.convert} subtitle={t.convertSub} onClick={() => onNavigate('exchange')} /><Shortcut icon={<CreditCard size={17} />} tone="gold" title={t.addFunds} subtitle={t.addFundsSub} onClick={() => onNavigate('funding')} /></div></section></div>
}
function moneySummary(value: Decimal | undefined, currency: Currency, language: Language) { return value ? formatMoney(value, currency, language) : `— ${currency}` }
function WalletCard({ wallet, language, t, onSend, onFund }: { wallet: Wallet; language: Language; t: Copy; onSend: () => void; onFund: () => void }) {
  return <article className={`wallet-card currency-${wallet.currency.toLowerCase()}`}><div className="wallet-card-header"><span className={`currency-badge ${wallet.currency.toLowerCase()}`}>{wallet.currency}</span><span className={`wallet-status ${wallet.status.toLowerCase()}`}><i />{wallet.status === 'FROZEN' ? t.frozen : t.active}</span></div><p className="wallet-region">{wallet.currency === 'NGN' ? 'NIGERIA · NAIRA' : 'CEMAC · CFA FRANC'}</p><p className="wallet-balance">{formatMoney(wallet.balance, wallet.currency, language)}</p><div className="wallet-foot"><span>•••• {wallet.id.slice(-4).toUpperCase()}</span><span>{t.opened} {new Date(wallet.createdAt).toLocaleDateString(language === 'fr' ? 'fr-FR' : 'en-GB')}</span></div><div className="wallet-actions"><button onClick={onSend}><ArrowLeftRight size={15} />{t.sendAction}</button><button onClick={onFund}><Plus size={15} />{t.addAction}</button></div></article>
}
function Shortcut({ icon, tone, title, subtitle, onClick }: { icon: React.ReactNode; tone: string; title: string; subtitle: string; onClick: () => void }) { return <button className="shortcut-row" onClick={onClick}><span className={`shortcut-icon ${tone}`}>{icon}</span><span className="shortcut-copy"><strong>{title}</strong><small>{subtitle}</small></span><ArrowRight size={16} /></button> }

function TransferScreen({ language, t, wallets, onDone }: { language: Language; t: Copy; wallets: Wallet[]; onDone: (value: string) => void }) {
  const [sourceId, setSourceId] = useState('')
  const [destinationId, setDestinationId] = useState('')
  const [amount, setAmount] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<Transfer | null>(null)
  const source = wallets.find(wallet => wallet.id === sourceId)
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setError('')
    if (!validAmount(amount, source?.currency || 'NGN')) { setError(t.amountInvalid); return }
    if (!sourceId || !destinationId) { setError(t.selectWallets); return }
    setBusy(true)
    try {
      const transfer = await api.request<Transfer>('/api/transfers', {
        method: 'POST', headers: { 'Idempotency-Key': newKey() },
        body: JSON.stringify({ sourceWalletId: sourceId, destinationWalletId: destinationId, amount }),
      })
      setResult(transfer); onDone(`${t.transferTitle}: ${transfer.state}`)
    } catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusy(false) }
  }
  return <div className="workflow-layout"><section className="workflow-panel"><div className="workflow-title"><span className="step-mark">01</span><div><p className="eyebrow">WALLET TRANSFER</p><h2>{t.transferDetails}</h2></div></div>{result ? <TransferResultCard transfer={result} language={language} t={t} reset={() => setResult(null)} /> : <form className="form-stack workflow-form" onSubmit={submit}><label>{t.fromWallet}<select value={sourceId} onChange={event => { setSourceId(event.target.value); setDestinationId('') }} required><option value="">{t.chooseWallet}</option>{wallets.map(wallet => <option key={wallet.id} value={wallet.id}>{wallet.currency} · {formatMoney(wallet.balance, wallet.currency, language)} · {wallet.status}</option>)}</select></label><label>{t.recipient}<input value={destinationId} onChange={event => setDestinationId(event.target.value)} required placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" /></label><small className="helper-text">{t.recipientHint}</small><label>{t.amount} ({source?.currency || 'NGN'})<input inputMode="decimal" value={amount} onChange={event => setAmount(event.target.value)} required placeholder={source?.currency === 'XAF' ? '0' : '0.00'} /></label>{error && <div className="form-error" role="alert">{error}</div>}<div className="notice-line"><ShieldCheck size={17} /><span>{t.transferNotice}</span></div><button className="primary-button wide-button" disabled={busy}>{busy ? <Spinner /> : <>{t.review}<ArrowRight size={16} /></>}</button></form>}</section><aside className="workflow-aside"><span className="aside-index">01 / MOVEMENT</span><div className="route-symbol"><span>YOU</span><i /><span>WALLET</span></div><h3>{language === 'fr' ? 'Chaque mouvement est vérifié.' : 'Every movement is checked.'}</h3><p>{language === 'fr' ? 'Le risque est évalué avant toute écriture au grand livre.' : 'Risk is evaluated before any ledger entry is posted.'}</p><div className="aside-separator" /><p className="fine-print">{t.sameCurrency}</p></aside></div>
}

function TransferResultCard({ transfer, language, t, reset }: { transfer: Transfer; language: Language; t: Copy; reset: () => void }) {
  const state = transfer.state.toLowerCase()
  const label = transfer.state === 'COMPLETED' ? t.completed : transfer.state === 'FLAGGED' ? t.flagged : t.failed
  return <div className="result-panel"><span className={`result-symbol ${state}`}>{transfer.state === 'COMPLETED' ? <Check size={22} /> : transfer.state === 'FLAGGED' ? <Clock3 size={22} /> : <ShieldX size={22} />}</span><p className="eyebrow">TRANSFER RESULT</p><h3>{t.transferResult}</h3><p className="result-amount">{formatMoney(transfer.amount, transfer.currency, language)}</p><span className={`state-pill ${state}`}>{label.toUpperCase()}</span>{transfer.state === 'FLAGGED' && <p className="result-description">{t.noFundsMoved}</p>}{transfer.failureCode && <p className="failure-code">{transfer.failureCode.replaceAll('_', ' ')}</p>}<button className="outline-button wide-button" onClick={reset}>{t.newTransfer}</button></div>
}

function ExchangeScreen({ language, t, wallets, onDone }: { language: Language; t: Copy; wallets: Wallet[]; onDone: (value: string) => void }) {
  const [sourceCurrency, setSourceCurrency] = useState<Currency>('NGN')
  const destinationCurrency: Currency = sourceCurrency === 'NGN' ? 'XAF' : 'NGN'
  const [sourceWalletId, setSourceWalletId] = useState('')
  const [destinationWalletId, setDestinationWalletId] = useState('')
  const [amount, setAmount] = useState('')
  const [quote, setQuote] = useState<Quote | null>(null)
  const [secondsLeft, setSecondsLeft] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const sourceWallets = wallets.filter(wallet => wallet.currency === sourceCurrency)
  const destinationWallets = wallets.filter(wallet => wallet.currency === destinationCurrency)
  useEffect(() => {
    if (!quote) return
    const tick = () => setSecondsLeft(Math.max(0, Math.floor((new Date(quote.expiresAt).getTime() - Date.now()) / 1000)))
    tick(); const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [quote])
  async function requestQuote(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setQuote(null)
    if (!validAmount(amount, sourceCurrency)) { setError(t.amountInvalid); setBusy(false); return }
    try {
      const next = await api.request<Quote>('/api/fx/quotes', { method: 'POST', body: JSON.stringify({ sourceCurrency, destinationCurrency, sourceAmount: amount }) })
      setQuote(next)
    } catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusy(false) }
  }
  async function confirm() {
    if (!quote || secondsLeft <= 0) { setError(t.quoteExpired); return }
    if (!sourceWalletId || !destinationWalletId) { setError(t.selectWallets); return }
    setBusy(true); setError('')
    try {
      const response = await api.request<Transfer>('/api/transfers/convert', { method: 'POST', headers: { 'Idempotency-Key': newKey() }, body: JSON.stringify({ sourceWalletId, destinationWalletId, quoteId: quote.id }) })
      onDone(`${t.exchangeTitle}: ${response.state}`); setQuote(null); setAmount('')
    } catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusy(false) }
  }
  return <div className="workflow-layout"><section className="workflow-panel"><div className="workflow-title"><span className="step-mark">02</span><div><p className="eyebrow">FX / NGN ⇄ XAF</p><h2>{t.quote}</h2></div></div><form className="form-stack workflow-form" onSubmit={requestQuote}><div className="currency-switch"><label>{t.youSend}<select value={sourceCurrency} onChange={event => { const next = event.target.value as Currency; setSourceCurrency(next); setQuote(null); setSourceWalletId(''); setDestinationWalletId('') }}><option value="NGN">NGN · Naira</option><option value="XAF">XAF · CFA franc</option></select></label><span className="switch-icon"><ArrowLeftRight size={17} /></span><div className="receive-currency"><span>{t.youReceive}</span><strong>{destinationCurrency}</strong><small>{destinationCurrency === 'XAF' ? 'CFA franc' : 'Naira'}</small></div></div><label>{t.sourceAmount}<input inputMode="decimal" value={amount} onChange={event => { setAmount(event.target.value); setQuote(null) }} required placeholder={sourceCurrency === 'NGN' ? '0.00' : '0'} /></label><label>{t.sourceWallet}<select value={sourceWalletId} onChange={event => setSourceWalletId(event.target.value)} required><option value="">{t.chooseWallet}</option>{sourceWallets.map(wallet => <option key={wallet.id} value={wallet.id}>{formatMoney(wallet.balance, wallet.currency, language)} · {wallet.status}</option>)}</select></label>{error && <div className="form-error" role="alert">{error}</div>}<button className="primary-button wide-button" disabled={busy}>{busy ? <Spinner /> : <>{t.quoteButton}<ArrowRight size={16} /></>}</button></form>{quote && <div className="quote-panel"><div className="quote-header"><span className="live-quote"><i />{t.liveQuote}</span><span className={`countdown ${secondsLeft < 10 ? 'urgent' : ''}`}><Clock3 size={14} />00:{String(secondsLeft).padStart(2, '0')}</span></div><div className="quote-total"><span>{t.youReceive}</span><strong>{formatMoney(quote.destinationAmount, quote.destinationCurrency, language)}</strong></div><QuoteLine label={t.effectiveRate} value={new Decimal(quote.effectiveRate).toFixed(6)} /><QuoteLine label={t.flatFee} value={formatMoney(quote.flatFee, quote.sourceCurrency, language)} /><QuoteLine label={t.spread} value={formatMoney(quote.spreadFee, quote.destinationCurrency, language)} /><label className="quote-destination">{t.destinationWallet}<select value={destinationWalletId} onChange={event => setDestinationWalletId(event.target.value)}><option value="">{t.chooseDestination}</option>{destinationWallets.map(wallet => <option key={wallet.id} value={wallet.id}>{formatMoney(wallet.balance, wallet.currency, language)} · {wallet.status}</option>)}</select></label>{error && <div className="form-error" role="alert">{error}</div>}<button className="primary-button wide-button" disabled={busy || !destinationWalletId || secondsLeft <= 0} onClick={() => void confirm()}>{busy ? <Spinner /> : secondsLeft <= 0 ? t.quoteExpired : <>{t.confirmExchange}<ArrowRight size={16} /></>}</button></div>}</section><aside className="workflow-aside"><div className="exchange-emblem"><span>NGN</span><ArrowRight size={18} /><span>XAF</span></div><span className="aside-index">FX / FIXED SANDBOX RATE</span><h3>{t.feeAndRate}</h3><p>{t.quoteNudge}</p><div className="aside-separator" /><p className="fine-print">{language === 'fr' ? 'NGN est stocké en kobo. XAF est stocké en unités entières.' : 'NGN is stored in kobo. XAF is stored in whole units.'}</p></aside></div>
}
function QuoteLine({ label, value }: { label: string; value: string }) { return <div className="quote-line"><span>{label}</span><strong>{value}</strong></div> }

function FundingScreen({ language, t, wallets, onDone }: { language: Language; t: Copy; wallets: Wallet[]; onDone: (value: string) => void }) {
  const [walletId, setWalletId] = useState('')
  const [amount, setAmount] = useState('')
  const [email, setEmail] = useState(api.session?.email || '')
  const [payment, setPayment] = useState<FundingPayment | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const wallet = wallets.find(item => item.id === walletId)
  async function submit(event: React.FormEvent) {
    event.preventDefault(); setError('')
    if (!wallet || !validAmount(amount, wallet.currency)) { setError(t.amountInvalid); return }
    setBusy(true)
    try {
      const result = await api.request<FundingPayment>('/api/funding/payments', { method: 'POST', body: JSON.stringify({ walletId, amount, currency: wallet.currency, email }) })
      setPayment(result); onDone(t.createIntent)
    } catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusy(false) }
  }
  return <div className="workflow-layout"><section className="workflow-panel"><div className="workflow-title"><span className="step-mark">03</span><div><p className="eyebrow">SANDBOX / PAYMENT GATEWAY</p><h2>{t.paymentIntent}</h2></div></div>{payment ? <div className="funding-result"><span className="result-symbol pending"><Clock3 size={21} /></span><p className="eyebrow">{t.paymentReference}</p><strong className="reference-value">{payment.reference}</strong><div className="payment-total">{formatMoney(payment.amount, payment.currency, language)}</div><span className="state-pill pending">{payment.status}</span><p className="result-description">{t.paymentPending}</p>{payment.authorizationUrl && <a className="primary-button full-button anchor-button" href={payment.authorizationUrl} target="_blank" rel="noreferrer">{t.continuePayment}<ArrowRight size={16} /></a>}<button className="text-button reset-link" onClick={() => setPayment(null)}>{t.anotherIntent}</button></div> : <form className="form-stack workflow-form" onSubmit={submit}><label>{t.walletToFund}<select value={walletId} onChange={event => setWalletId(event.target.value)} required><option value="">{t.chooseWallet}</option>{wallets.map(item => <option key={item.id} value={item.id}>{item.currency} · {formatMoney(item.balance, item.currency, language)}</option>)}</select></label><label>{t.amount}<input inputMode="decimal" value={amount} onChange={event => setAmount(event.target.value)} required placeholder={wallet?.currency === 'XAF' ? '0' : '0.00'} /></label><label>{t.payerEmail}<input type="email" value={email} onChange={event => setEmail(event.target.value)} required /></label>{error && <div className="form-error" role="alert">{error}</div>}<div className="notice-line warning"><Clock3 size={17} /><span>{t.sandboxNotice}</span></div><button className="primary-button wide-button" disabled={busy}>{busy ? <Spinner /> : <>{t.createIntent}<ArrowRight size={16} /></>}</button></form>}</section><aside className="workflow-aside"><span className="funding-icon"><CreditCard size={22} /></span><span className="aside-index">GATEWAY / SANDBOX</span><h3>{language === 'fr' ? 'Paiement vérifié avant crédit.' : 'Verified before credit.'}</h3><p>{language === 'fr' ? 'La référence, le montant, la devise et la signature sont vérifiés avant le grand livre.' : 'Reference, amount, currency, and signature are checked before a ledger entry is posted.'}</p><div className="aside-separator" /><span className="gateway-state"><i />{language === 'fr' ? 'Adaptateur fictif par défaut' : 'Fake gateway by default'}</span></aside></div>
}

function AdminScreen({ language, t, flagged, audit, onRefresh, onDone }: { language: Language; t: Copy; flagged: Transfer[]; audit: AuditEvent[]; onRefresh: () => void; onDone: (value: string) => void }) {
  const [busyId, setBusyId] = useState('')
  const [error, setError] = useState('')
  const [walletId, setWalletId] = useState('')
  async function review(transfer: Transfer, decision: 'approve' | 'reject') {
    if (!window.confirm(t.confirmTitle)) return
    setBusyId(transfer.id); setError('')
    try {
      const response = await api.request<Transfer>(`/api/admin/transfers/${transfer.id}/${decision}`, { method: 'POST', ...(decision === 'reject' ? { body: JSON.stringify({ reason: 'Reviewed in operations console' }) } : {}) })
      onDone(`${decision === 'approve' ? t.approve : t.reject}: ${response.state}`)
    } catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusyId('') }
  }
  async function walletAction(action: 'freeze' | 'unfreeze') {
    if (!walletId.trim()) { setError(t.walletId); return }
    if (!window.confirm(t.confirmTitle)) return
    setBusyId(walletId); setError('')
    try { await api.request<void>(`/api/admin/wallets/${walletId.trim()}/${action}`, { method: 'POST' }); onDone(action === 'freeze' ? t.freeze : t.unfreeze); setWalletId('') }
    catch (reason) { setError(errorText(reason, t.networkError)) } finally { setBusyId('') }
  }
  return <div className="admin-layout"><section className="admin-primary"><div className="section-heading"><div><p className="eyebrow">ADMIN / REVIEW QUEUE</p><h2>{t.flaggedTransfers}</h2></div><button className="outline-button" onClick={onRefresh}><RefreshCw size={15} />{t.refresh}</button></div>{error && <div className="form-error" role="alert">{error}</div>}{flagged.length ? <div className="review-list">{flagged.map(transfer => <article className="review-item" key={transfer.id}><span className="review-symbol"><ShieldCheck size={17} /></span><div className="review-copy"><strong>{formatMoney(transfer.amount, transfer.currency, language)} {transfer.currency}</strong><small>{transfer.sourceWalletId.slice(0, 8)}… → {transfer.destinationWalletId.slice(0, 8)}…</small><small>{formatDate(transfer.createdAt, language)}{transfer.failureCode ? ` · ${transfer.failureCode}` : ''}</small></div><div className="review-controls"><button className="outline-button approve" disabled={busyId === transfer.id} onClick={() => void review(transfer, 'approve')}><BadgeCheck size={15} />{t.approve}</button><button className="outline-button reject" disabled={busyId === transfer.id} onClick={() => void review(transfer, 'reject')}><ShieldX size={15} />{t.reject}</button></div></article>)}</div> : <div className="admin-empty"><ShieldCheck size={22} /><strong>{t.noFlagged}</strong><span>{t.noFlaggedSub}</span></div>}<div className="section-heading audit-heading"><div><p className="eyebrow">ADMIN / AUDIT</p><h2>{t.auditLog}</h2></div><span className="muted-label">{audit.length} {t.recentEvents}</span></div><div className="audit-list">{audit.slice(0, 15).map(event => <div className="audit-item" key={event.id}><span className="audit-symbol"><FileClock size={15} /></span><div><strong>{event.action.replaceAll('_', ' ')}</strong><small>{event.entityType}{event.entityId ? ` · ${event.entityId}` : ''}</small></div><time>{formatDate(event.createdAt, language)}</time></div>)}{!audit.length && <p className="quiet-empty">{t.auditEmpty}</p>}</div></section><aside className="admin-aside"><span className="admin-emblem"><ShieldCheck size={24} /></span><p className="eyebrow">CONTROL / ACCESS</p><h3>{t.controlTitle}</h3><p>{t.controlSub}</p><div className="aside-separator" /><label>{t.walletId}<input value={walletId} onChange={event => setWalletId(event.target.value)} placeholder="xxxxxxxx-xxxx-…" /></label><button className="outline-button full-button" disabled={Boolean(busyId)} onClick={() => void walletAction('freeze')}><ShieldX size={15} />{t.freeze}</button><button className="outline-button full-button" disabled={Boolean(busyId)} onClick={() => void walletAction('unfreeze')}><BadgeCheck size={15} />{t.unfreeze}</button></aside></div>
}

function validAmount(value: string, currency: Currency) {
  try {
    const amount = new Decimal(value)
    return amount.isFinite() && amount.gt(0) && amount.decimalPlaces() <= (currency === 'NGN' ? 2 : 0)
  } catch { return false }
}

export default App