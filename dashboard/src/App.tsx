import { Activity, Boxes, Gauge, LogOut, Network, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

type User = { id: number; name: string; email: string };
type Role = 'ADMIN' | 'DEVELOPER' | 'API_CONSUMER';
type Account = { id: number; name: string; email: string; role: Role; scopes: string[] };
type AuthResponse = { accessToken: string; refreshToken: string; tokenType: string; expiresInSeconds: number; refreshExpiresInSeconds: number; account: Account };
type Session = { token: string; refreshToken: string; account: Account };
type Health = 'online' | 'offline' | 'checking';

const SESSION_KEY = 'aetheris.session';

function readSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    return raw ? JSON.parse(raw) as Session : null;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

function writeSession(session: Session) { sessionStorage.setItem(SESSION_KEY, JSON.stringify(session)); }

async function rotateSession(current: Session): Promise<Session | null> {
  const response = await fetch('/api/auth/refresh', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: current.refreshToken })
  });
  if (!response.ok) return null;
  const auth = await response.json() as AuthResponse;
  const next = { token: auth.accessToken, refreshToken: auth.refreshToken, account: auth.account };
  writeSession(next);
  return next;
}

export default function App() {
  const [session, setSession] = useState<Session | null>(() => readSession());
  const [users, setUsers] = useState<User[]>([]);
  const [health, setHealth] = useState<Health>('checking');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [authMessage, setAuthMessage] = useState('');
  const canWriteUsers = useMemo(() => session?.account.scopes?.includes('users:write') ?? false, [session]);

  const expireSession = useCallback(() => {
    sessionStorage.removeItem(SESSION_KEY); setSession(null); setUsers([]);
    setAuthMessage('Your session ended. Sign in again.');
  }, []);

  const protectedFetch = useCallback(async (url: string, init: RequestInit = {}) => {
    if (!session) return null;
    const withToken = (token: string) => fetch(url, { ...init, headers: { ...(init.headers ?? {}), Authorization: `Bearer ${token}` } });
    let response = await withToken(session.token);
    if (response.status !== 401) return response;
    const rotated = await rotateSession(session);
    if (!rotated) { expireSession(); return null; }
    setSession(rotated);
    return withToken(rotated.token);
  }, [session, expireSession]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const healthResponse = await fetch('/actuator/health');
      setHealth(healthResponse.ok ? 'online' : 'offline');
      if (!session) { setUsers([]); return; }
      const usersResponse = await protectedFetch('/api/users');
      if (!usersResponse) return;
      if (usersResponse.status === 429) throw new Error('Rate limit reached. Try again shortly.');
      if (!usersResponse.ok) throw new Error('User service unavailable');
      setUsers(await usersResponse.json()); setMessage('');
    } catch (error) {
      setHealth('offline'); setMessage(error instanceof Error ? error.message : 'Unable to reach Aetheris');
    } finally { setLoading(false); }
  }, [session, protectedFetch]);

  useEffect(() => { refresh(); }, [refresh]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setAuthMessage('');
    const form = new FormData(event.currentTarget);
    const response = await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: form.get('email'), password: form.get('password') }) });
    if (!response.ok) { const body = await response.json().catch(() => ({})); setAuthMessage(body.message ?? (response.status === 429 ? 'Too many sign-in attempts. Try again shortly.' : 'Sign in failed')); return; }
    const auth = await response.json() as AuthResponse;
    const nextSession = { token: auth.accessToken, refreshToken: auth.refreshToken, account: auth.account };
    writeSession(nextSession); setSession(nextSession); setAuthMessage('');
  }

  async function logout() {
    const current = session;
    if (current?.refreshToken) await fetch('/api/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: current.refreshToken }) }).catch(() => undefined);
    sessionStorage.removeItem(SESSION_KEY); setSession(null); setUsers([]); setMessage('');
  }

  async function createUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!session) return;
    const form = new FormData(event.currentTarget);
    const response = await protectedFetch('/api/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: form.get('name'), email: form.get('email') }) });
    if (!response) return;
    if (!response.ok) { const body = await response.json().catch(() => ({})); setMessage(body.message ?? (response.status === 429 ? 'Rate limit reached. Try again shortly.' : 'Could not create user')); return; }
    event.currentTarget.reset(); setMessage('User created; Redis user caches invalidated.'); await refresh();
  }

  if (!session) return <div className="authShell"><div className="authCard"><div className="brand authBrand"><div className="brandMark">A</div><div><strong>Aetheris</strong><span>Control Plane</span></div></div><p className="eyebrow">STAGE 3 · REDIS TRAFFIC LAYER</p><h1>Sign in</h1><p className="authIntro">Authenticate through Aetheris. Redis now backs distributed rate limits and shared user caching.</p><form onSubmit={login}><label>Email<input name="email" type="email" required autoComplete="username" placeholder="poojana@aetheris.local"/></label><label>Password<input name="password" type="password" required autoComplete="current-password" placeholder="••••••••"/></label><button type="submit"><ShieldCheck size={17}/>Sign in to Aetheris</button></form>{authMessage && <p className="authError">{authMessage}</p>}<div className="authNote"><ShieldCheck size={15}/><span>JWT scopes + rotating sessions + Redis-backed traffic protection.</span></div></div></div>;

  return <div className="shell"><aside className="sidebar"><div className="brand"><div className="brandMark">A</div><div><strong>Aetheris</strong><span>Control Plane</span></div></div><nav><a className="active"><Gauge size={18}/>Overview</a><a><Network size={18}/>Gateway</a><a><Boxes size={18}/>Services</a><a><ShieldCheck size={18}/>Identity</a><a><Activity size={18}/>Observability</a></nav><div className="identityCard"><span>{session.account.name}</span><strong>{session.account.role}</strong><small>{session.account.email}</small><small>{session.account.scopes?.join(' · ')}</small><button onClick={logout}><LogOut size={15}/>Sign out</button></div><div className="stage">STAGE 3 <span>Redis Cache + Rate Limits</span></div></aside><main><header><div><p className="eyebrow">PLATFORM OVERVIEW</p><h1>Control plane</h1><p>Authenticated platform with scoped authorization, Redis caching, and distributed traffic control.</p></div><button onClick={refresh} disabled={loading}><RefreshCw size={17} className={loading ? 'spin' : ''}/>Refresh</button></header><section className="metrics"><Metric icon={<Activity/>} label="Gateway" value={health === 'online' ? 'Healthy' : health === 'checking' ? 'Checking' : 'Offline'} hint="JWT + Redis rate limit" tone={health}/><Metric icon={<Boxes/>} label="Platform" value={health === 'online' ? '3 / 3' : '0 / 3'} hint="User + Identity + Redis"/><Metric icon={<Users/>} label="Users" value={String(users.length)} hint="Redis cached reads"/><Metric icon={<ShieldCheck/>} label="Role" value={session.account.role} hint={`${session.account.scopes?.length ?? 0} effective scopes`}/></section><section className="grid"><article className="panel architecture"><div className="panelHead"><div><p className="eyebrow">DISTRIBUTED REQUEST PATH</p><h2>Architecture</h2></div><span className="liveDot">LIVE</span></div><div className="flow"><Node name="Dashboard" meta=":3000"/><Arrow/><Node name="Gateway" meta="JWT + Rate Limit" glow/><Arrow/><Node name="User Service" meta="Redis Cache"/><Arrow/><Node name="PostgreSQL" meta=":5432"/></div></article><article className="panel"><div className="panelHead"><div><p className="eyebrow">PERMISSION TEST</p><h2>Create user</h2></div><span className={`permission ${canWriteUsers ? 'allowed' : 'denied'}`}>{canWriteUsers ? 'users:write' : 'READ ONLY'}</span></div><form onSubmit={createUser}><label>Name<input name="name" required maxLength={100} placeholder="Ada Lovelace" disabled={!canWriteUsers}/></label><label>Email<input name="email" type="email" required placeholder="ada@example.com" disabled={!canWriteUsers}/></label><button type="submit" disabled={!canWriteUsers}>POST /api/users</button></form>{!canWriteUsers && <p className="message warning">This identity has users:read but not users:write.</p>}{message && <p className="message">{message}</p>}</article></section><article className="panel users"><div className="panelHead"><div><p className="eyebrow">REDIS-CACHED GATEWAY RESPONSE</p><h2>Users</h2></div><code>users:read · GET /api/users</code></div>{users.length === 0 ? <div className="empty">No users returned from the protected user resource.</div> : <div className="table">{users.map(user => <div className="row" key={user.id}><span className="avatar">{user.name.slice(0,1).toUpperCase()}</span><strong>{user.name}</strong><span>{user.email}</span><code>#{user.id}</code></div>)}</div>}</article></main></div>;
}

function Metric({icon,label,value,hint,tone}:{icon:React.ReactNode;label:string;value:string;hint:string;tone?:Health}) { return <article className="metric"><div className="metricIcon">{icon}</div><div><span>{label}</span><strong className={tone ? `status ${tone}` : ''}>{value}</strong><small>{hint}</small></div></article>; }
function Node({name,meta,glow}:{name:string;meta:string;glow?:boolean}) { return <div className={`node ${glow ? 'glow' : ''}`}><strong>{name}</strong><span>{meta}</span></div>; }
function Arrow(){ return <span className="arrow">→</span>; }
