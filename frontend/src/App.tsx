import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { api } from "./api/client";
import { useAuth } from "./auth";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Expenses from "./pages/Expenses";
import Occasions from "./pages/Occasions";
import Investments from "./pages/Investments";
import Income from "./pages/Income";
import Balances from "./pages/Balances";
import ListsPage from "./pages/Lists";
import AuditLog from "./pages/AuditLog";

const NAV = [
  { to: "/dashboard", label: "Dashboard", icon: "📊" },
  { to: "/expenses", label: "Expenses", icon: "💸" },
  { to: "/occasions", label: "Occasions", icon: "🧳" },
  { to: "/income", label: "Income", icon: "💰" },
  { to: "/investments", label: "Investments", icon: "📈" },
  { to: "/balances", label: "Balances", icon: "🏦" },
  { to: "/lists", label: "Lists", icon: "⚙️" },
  { to: "/audit", label: "Audit Log", icon: "📜" },
];

/** Fetches the .xlsx export (auth-protected) and triggers a browser download. */
async function downloadExport(): Promise<void> {
  const blob = await api.exportWorkbook();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `expense-tracker-${new Date().toISOString().slice(0, 10)}.xlsx`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export default function App() {
  const { username, loading, logout } = useAuth();

  if (loading) {
    return (
      <div className="centered">
        <div className="spinner" />
      </div>
    );
  }

  if (!username) {
    return <Login />;
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <img className="brand-mark" src="/icon.png" alt="" />
          <span className="brand-name">Household</span>
        </div>
        <div className="topbar-user">
          <span className="user">{username}</span>
          <button className="btn-link" onClick={() => void downloadExport()}>
            Export
          </button>
          <button className="btn-link" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <aside className="sidebar">
        <div className="brand">
          <img className="brand-mark" src="/icon.png" alt="" />
          <span className="brand-name">Household</span>
        </div>
        <nav className="nav">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} className="nav-link">
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <span className="user">{username}</span>
          <button className="btn-link" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="content">
        <Routes>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/expenses" element={<Expenses />} />
          <Route path="/occasions" element={<Occasions />} />
          <Route path="/income" element={<Income />} />
          <Route path="/investments" element={<Investments />} />
          <Route path="/balances" element={<Balances />} />
          <Route path="/lists" element={<ListsPage />} />
          <Route path="/audit" element={<AuditLog />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>

      <nav className="bottom-nav">
        {NAV.map((item) => (
          <NavLink key={item.to} to={item.to} className="bottom-link">
            <span className="nav-icon">{item.icon}</span>
            <span className="bottom-label">{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
