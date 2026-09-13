import { Activity, Boxes, Gauge, LogOut, Network, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';

type User = { id: number; name: string; email: string };
type Role = 'ADMIN' | 'DEVELOPER' | 'API_CONSUMER';
type Account = { id: number; name: string; email: string; role: Role; scopes: string[] };
type AuthResponse = { accessToken: string; refreshToken: string; tokenType: string; expiresInSeconds: number; refreshExpiresInSeconds: number; account: Account };
type Session = { token: string; refreshToken: string; account: Account };
type Health = 'online' | 'offline' | 'checking';
type OrchestratorTask = { id: string; title: string; state: string; mode: string; activeAgentId?: string | null; updatedAt: string };
type Approval = { id: string; taskId: string; actionType: string; riskLevel: string; status: string; createdAt: string };
type ModelProvider = { providerId: string; available: boolean; local: boolean; zeroCost: boolean; model: string; detail: string };
type Invocation = { id: string; kind: string; targetId: string; status: string; agentId?: string | null; startedAt: string };
type EmergencyStop = { active: boolean; changedAt: string; reason: string; cancelledTasks: number };

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
  const [tasks, setTasks] = useState<OrchestratorTask[]>([]);
  const [approvals, setApprovals] = useState<Approval[]>([]);
  const [providers, setProviders] = useState<ModelProvider[]>([]);
  const [invocations, setInvocations] = useState<Invocation[]>([]);
  const [emergencyStop, setEmergencyStop] = useState<EmergencyStop | null>(null);
  const [health, setHealth] = useState<Health>('checking');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [authMessage, setAuthMessage] = useState('');
  const canWriteUsers = useMemo(() => session?.account.scopes?.includes('users:write') ?? false, [session]);
  const canControlOrchestrator = useMemo(() => session?.account.scopes?.includes('orchestrator:write') ?? false, [session]);
  const healthyProviders = useMemo(() => providers.filter(provider => provider.available).length, [providers]);

  const expireSession = useCallback(() => {
    sessionStorage.removeItem(SESSION_KEY); setSession(null); setUsers([]); setTasks([]); setApprovals([]); setProviders([]); setInvocations([]); setEmergencyStop(null);
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

      const [usersResponse, tasksResponse, approvalsResponse, providersResponse, invocationsResponse, stopResponse] = await Promise.all([
        protectedFetch('/api/users'),
        protectedFetch('/api/orchestrator/tasks'),
        protectedFetch('/api/orchestrator/approvals/pending'),
        protectedFetch('/api/orchestrator/models/providers'),
        protectedFetch('/api/orchestrator/invocations'),
        protectedFetch('/api/orchestrator/control/emergency-stop')
      ]);

      if (usersResponse?.ok) setUsers(await usersResponse.json());
      if (tasksResponse?.ok) setTasks(await tasksResponse.json());
      if (approvalsResponse?.ok) setApprovals(await approvalsResponse.json());
      if (providersResponse?.ok) setProviders(await providersResponse.json());
      if (invocationsResponse?.ok) setInvocations(await invocationsResponse.json());
      if (stopResponse?.ok) setEmergencyStop(await stopResponse.json());
      setMessage('');
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
    sessionStorage.removeItem(SESSION_KEY); setSession(null); setUsers([]); setTasks([]); setApprovals([]); setProviders([]); setInvocations([]); setEmergencyStop(null); setMessage('');
  }

  async function createUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!session) return;
    const form = new FormData(event.currentTarget);
    const response = await protectedFetch('/api/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: form.get('name'), email: form.get('email') }) });
    if (!response) return;
    if (!response.ok) { const body = await response.json().catch(() => ({})); setMessage(body.message ?? (response.status === 429 ? 'Rate limit reached. Try again shortly.' : 'Could not create user')); return; }
    event.currentTarget.reset(); setMessage('User created; Redis user caches invalidated.'); await refresh();
  }

  async function toggleEmergencyStop() {
    if (!canControlOrchestrator || !emergencyStop) return;
    const nextActive = !emergencyStop.active;
    const prompt = nextActive
      ? 'Engage emergency stop? Active Aetheris tasks will be cancelled and new model/tool/MCP executions will be blocked.'
      : 'Release emergency stop and allow new Aetheris executions?';
    if (!window.confirm(prompt)) return;
    const response = await protectedFetch(`/api/orchestrator/control/emergency-stop/${nextActive ? 'engage' : 'release'}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason: nextActive ? 'Owner engaged from Syntra operations dashboard' : 'Owner released from Syntra operations dashboard' })
    });
    if (response?.ok) setEmergencyStop(await response.json());
    else setMessage('Unable to change emergency-stop state.');
    await refresh();
  }

  if (!session) return <div className="authShell"><div className="authCard"><div className="brand authBrand"><div className="brandMark">A</div><div><strong>Aetheris</strong><span>Control Plane</span></div></div><p className="eyebrow">SYNTRA + AETHERIS · GOVERNED OPS</p><h1>Sign in</h1><p className="authIntro">Authenticate to the platform control plane, including the governed Syntra/Aetheris orchestration APIs.</p><form onSubmit={login}><label>Email<input name="email" type="email" required autoComplete="username" placeholder="poojana@aetheris.local"/></label><label>Password<input name="password" type="password" required autoComplete="current-password" placeholder="••••••••"/></label><button type="submit"><ShieldCheck size={17}/>Sign in to Aetheris</button></form>{authMessage && <p className="authError">{authMessage}</p>}<div className="authNote"><ShieldCheck size={15}/><span>JWT scopes + Redis traffic protection + orchestrator policy boundaries.</span></div></div></div>;

  return <div className="shell"><aside className="sidebar"><div className="brand"><div className="brandMark">A</div><div><strong>Aetheris</strong><span>Control Plane</span></div></div><nav><a className="active"><Gauge size={18}/>Overview</a><a><Network size={18}/>Gateway</a><a><Boxes size={18}/>Services</a><a><ShieldCheck size={18}/>Identity</a><a><Activity size={18}/>Syntra Ops</a></nav><div className="identityCard"><span>{session.account.name}</span><strong>{session.account.role}</strong><small>{session.account.email}</small><small>{session.account.scopes?.join(' · ')}</small><button onClick={logout}><LogOut size={15}/>Sign out</button></div><div className="stage">AI STAGE 4 <span>Governed Execution</span></div></aside><main><header><div><p className="eyebrow">PLATFORM + AI OPERATIONS</p><h1>Control plane</h1><p>Authenticated services plus policy-gated Syntra/Aetheris tasks, models, approvals and execution evidence.</p></div><button onClick={refresh} disabled={loading}><RefreshCw size={17} className={loading ? 'spin' : ''}/>Refresh</button></header><section className="metrics"><Metric icon={<Activity/>} label="Gateway" value={health === 'online' ? 'Healthy' : health === 'checking' ? 'Checking' : 'Offline'} hint="JWT + Redis + policy routes" tone={health}/><Metric icon={<Boxes/>} label="AI Tasks" value={String(tasks.length)} hint={`${tasks.filter(task => !['COMPLETED','FAILED','CANCELLED'].includes(task.state)).length} active/recent`}/><Metric icon={<Network/>} label="Models" value={`${healthyProviders}/${providers.length || 1}`} hint="Healthy approved providers"/><Metric icon={<ShieldCheck/>} label="Emergency Stop" value={emergencyStop?.active ? 'ENGAGED' : 'Ready'} hint={emergencyStop?.active ? `${emergencyStop.cancelledTasks} tasks cancelled` : 'Local kill switch armed'}/></section><section className="grid"><article className="panel architecture"><div className="panelHead"><div><p className="eyebrow">GOVERNED REQUEST PATH</p><h2>Architecture</h2></div><span className="liveDot">LIVE</span></div><div className="flow"><Node name="Syntra UI" meta="Owner intent"/><Arrow/><Node name="Gateway" meta="JWT + scopes" glow/><Arrow/><Node name="Orchestrator" meta="Policy + audit"/><Arrow/><Node name="Adapters" meta="Sandboxed tools"/></div></article><article className="panel"><div className="panelHead"><div><p className="eyebrow">OWNER CONTROL</p><h2>Emergency stop</h2></div><span className={`permission ${emergencyStop?.active ? 'denied' : 'allowed'}`}>{emergencyStop?.active ? 'ENGAGED' : 'READY'}</span></div><p className="message">{emergencyStop?.reason ?? 'Loading control state…'}</p><button className="dangerControl" onClick={toggleEmergencyStop} disabled={!canControlOrchestrator || !emergencyStop}>{emergencyStop?.active ? 'Release emergency stop' : 'Engage emergency stop'}</button>{!canControlOrchestrator && <p className="message warning">Read-only orchestrator scope. Control actions require orchestrator:write.</p>}</article></section><section className="opsGrid"><article className="panel"><div className="panelHead"><div><p className="eyebrow">SYNTRA / AETHERIS TASKS</p><h2>Recent execution</h2></div><code>{tasks.length} tasks</code></div><div className="opsList">{tasks.slice(0,6).map(task => <div className="opsItem" key={task.id}><div><strong>{task.title}</strong><span>{task.activeAgentId ?? 'No active agent'} · {task.mode}</span></div><StatusPill value={task.state}/></div>)}{tasks.length === 0 && <div className="empty compact">No orchestrator tasks yet.</div>}</div></article><article className="panel"><div className="panelHead"><div><p className="eyebrow">OWNER APPROVALS</p><h2>Pending gates</h2></div><code>{approvals.length} pending</code></div><div className="opsList">{approvals.slice(0,6).map(approval => <div className="opsItem" key={approval.id}><div><strong>{approval.actionType}</strong><span>{approval.riskLevel} · task {approval.taskId.slice(0,8)}</span></div><StatusPill value={approval.status}/></div>)}{approvals.length === 0 && <div className="empty compact">No pending owner approvals.</div>}</div></article></section><section className="opsGrid"><article className="panel"><div className="panelHead"><div><p className="eyebrow">MODEL ROUTER</p><h2>Provider health</h2></div><code>local-first</code></div><div className="opsList">{providers.map(provider => <div className="opsItem" key={provider.providerId}><div><strong>{provider.providerId} · {provider.model}</strong><span>{provider.local ? 'LOCAL' : 'REMOTE'} · {provider.zeroCost ? 'ZERO COST' : 'PAID CAPABLE'}</span></div><StatusPill value={provider.available ? 'HEALTHY' : 'OFFLINE'}/></div>)}{providers.length === 0 && <div className="empty compact">No model providers reported.</div>}</div></article><article className="panel"><div className="panelHead"><div><p className="eyebrow">AUDIT STREAM</p><h2>Recent invocations</h2></div><code>{invocations.length} entries</code></div><div className="opsList">{invocations.slice(0,6).map(item => <div className="opsItem" key={item.id}><div><strong>{item.kind} · {item.targetId}</strong><span>{item.agentId ?? 'system'} · {new Date(item.startedAt).toLocaleTimeString()}</span></div><StatusPill value={item.status}/></div>)}{invocations.length === 0 && <div className="empty compact">No model/tool/MCP invocations yet.</div>}</div></article></section><article className="panel users"><div className="panelHead"><div><p className="eyebrow">PLATFORM USERS</p><h2>Users</h2></div><code>users:read · GET /api/users</code></div>{users.length === 0 ? <div className="empty">No users returned from the protected user resource.</div> : <div className="table">{users.map(user => <div className="row" key={user.id}><span className="avatar">{user.name.slice(0,1).toUpperCase()}</span><strong>{user.name}</strong><span>{user.email}</span><code>#{user.id}</code></div>)}</div>}{canWriteUsers && <form onSubmit={createUser} className="inlineCreate"><label>Name<input name="name" required maxLength={100} placeholder="Ada Lovelace"/></label><label>Email<input name="email" type="email" required placeholder="ada@example.com"/></label><button type="submit">Create user</button></form>}{message && <p className="message">{message}</p>}</article></main></div>;
}

function Metric({icon,label,value,hint,tone}:{icon:React.ReactNode;label:string;value:string;hint:string;tone?:Health}) { return <article className="metric"><div className="metricIcon">{icon}</div><div><span>{label}</span><strong className={tone ? `status ${tone}` : ''}>{value}</strong><small>{hint}</small></div></article>; }
function Node({name,meta,glow}:{name:string;meta:string;glow?:boolean}) { return <div className={`node ${glow ? 'glow' : ''}`}><strong>{name}</strong><span>{meta}</span></div>; }
function Arrow(){ return <span className="arrow">→</span>; }
function StatusPill({value}:{value:string}) { const safe=value.toLowerCase().replaceAll('_','-'); return <span className={`statusPill ${safe}`}>{value.replaceAll('_',' ')}</span>; }
