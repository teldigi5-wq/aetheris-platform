import { Activity, Boxes, Database, Gauge, Network, RefreshCw, ShieldCheck, Users } from 'lucide-react';
import { FormEvent, useCallback, useEffect, useState } from 'react';

type User = { id: number; name: string; email: string };
type Health = 'online' | 'offline' | 'checking';

const GATEWAY = '';

export default function App() {
  const [users, setUsers] = useState<User[]>([]);
  const [health, setHealth] = useState<Health>('checking');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const healthResponse = await fetch(`${GATEWAY}/actuator/health`);
      setHealth(healthResponse.ok ? 'online' : 'offline');

      const usersResponse = await fetch(`${GATEWAY}/api/users`);
      if (usersResponse.status === 401) {
        setUsers([]);
        setMessage('Authentication required for protected user APIs. Sign-in UI arrives in Stage 2.3.');
        return;
      }
      if (!usersResponse.ok) throw new Error('User service unavailable');
      setUsers(await usersResponse.json());
      setMessage('');
    } catch (error) {
      setHealth('offline');
      setMessage(error instanceof Error ? error.message : 'Unable to reach Aetheris');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  async function createUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const response = await fetch(`${GATEWAY}/api/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: form.get('name'), email: form.get('email') })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      setMessage(body.message ?? 'Could not create user');
      return;
    }
    event.currentTarget.reset();
    setMessage('User created through Aetheris Gateway');
    await refresh();
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><div className="brandMark">A</div><div><strong>Aetheris</strong><span>Control Plane</span></div></div>
        <nav>
          <a className="active"><Gauge size={18}/>Overview</a>
          <a><Network size={18}/>Gateway</a>
          <a><Boxes size={18}/>Services</a>
          <a><ShieldCheck size={18}/>Identity</a>
          <a><Activity size={18}/>Observability</a>
        </nav>
        <div className="stage">STAGE 2.2 <span>JWT Enforcement</span></div>
      </aside>

      <main>
        <header><div><p className="eyebrow">PLATFORM OVERVIEW</p><h1>Control plane</h1><p>Live view of the local Aetheris development environment.</p></div><button onClick={refresh} disabled={loading}><RefreshCw size={17} className={loading ? 'spin' : ''}/>Refresh</button></header>

        <section className="metrics">
          <Metric icon={<Activity/>} label="Gateway" value={health === 'online' ? 'Healthy' : health === 'checking' ? 'Checking' : 'Offline'} hint="Spring Cloud Gateway" tone={health}/>
          <Metric icon={<Boxes/>} label="Services" value={health === 'online' ? '2 / 2' : '0 / 2'} hint="User + identity"/>
          <Metric icon={<Users/>} label="Users" value={String(users.length)} hint="Protected resource"/>
          <Metric icon={<Database/>} label="Data layer" value={health === 'online' ? 'PostgreSQL' : 'Unknown'} hint="Local container"/>
        </section>

        <section className="grid">
          <article className="panel architecture"><div className="panelHead"><div><p className="eyebrow">REQUEST PATH</p><h2>Architecture</h2></div><span className="liveDot">LIVE</span></div><div className="flow"><Node name="Dashboard" meta=":3000"/><Arrow/><Node name="Gateway + JWT" meta=":8080" glow/><Arrow/><Node name="User Service" meta=":8081"/><Arrow/><Node name="PostgreSQL" meta=":5432"/></div></article>

          <article className="panel"><div className="panelHead"><div><p className="eyebrow">PROTECTED API</p><h2>Create user</h2></div></div><form onSubmit={createUser}><label>Name<input name="name" required maxLength={100} placeholder="Ada Lovelace"/></label><label>Email<input name="email" type="email" required placeholder="ada@example.com"/></label><button type="submit">POST /api/users</button></form>{message && <p className="message">{message}</p>}</article>
        </section>

        <article className="panel users"><div className="panelHead"><div><p className="eyebrow">GATEWAY RESPONSE</p><h2>Users</h2></div><code>GET /api/users</code></div>{users.length === 0 ? <div className="empty">Protected by JWT. Authenticate through /api/auth/login, then call this route with a Bearer token.</div> : <div className="table">{users.map(user => <div className="row" key={user.id}><span className="avatar">{user.name.slice(0,1).toUpperCase()}</span><strong>{user.name}</strong><span>{user.email}</span><code>#{user.id}</code></div>)}</div>}</article>
      </main>
    </div>
  );
}

function Metric({icon,label,value,hint,tone}:{icon:React.ReactNode;label:string;value:string;hint:string;tone?:Health}) { return <article className="metric"><div className="metricIcon">{icon}</div><div><span>{label}</span><strong className={tone ? `status ${tone}` : ''}>{value}</strong><small>{hint}</small></div></article>; }
function Node({name,meta,glow}:{name:string;meta:string;glow?:boolean}) { return <div className={`node ${glow ? 'glow' : ''}`}><strong>{name}</strong><span>{meta}</span></div>; }
function Arrow(){ return <span className="arrow">→</span>; }
