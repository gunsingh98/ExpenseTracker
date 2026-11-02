import { useEffect, useRef, useState } from "react";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
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

// Mobile bottom nav: the first pages are always visible, the rest sit under "More".
const BOTTOM_PRIMARY = ["/dashboard", "/expenses", "/income", "/investments"];

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

      <BottomNav />
    </div>
  );
}

/**
 * Mobile bottom bar: four primary pages plus a "More" button that opens a menu
 * of the remaining pages above the bar. "More" shows as active while one of
 * those pages is open; the menu closes on navigation, outside tap, or Escape.
 */
function BottomNav() {
  const primary = NAV.filter((item) => BOTTOM_PRIMARY.includes(item.to));
  const more = NAV.filter((item) => !BOTTOM_PRIMARY.includes(item.to));
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLElement>(null);
  const moreActive = more.some((item) => pathname.startsWith(item.to));

  useEffect(() => setOpen(false), [pathname]);

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <nav className="bottom-nav" ref={ref}>
      {primary.map((item) => (
        <NavLink key={item.to} to={item.to} className="bottom-link">
          <span className="nav-icon">{item.icon}</span>
          <span className="bottom-label">{item.label}</span>
        </NavLink>
      ))}
      <button
        type="button"
        className={`bottom-link${moreActive ? " active" : ""}`}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <span className="nav-icon">☰</span>
        <span className="bottom-label">More</span>
      </button>
      {open && (
        <div className="bottom-more" role="menu">
          {more.map((item) => (
            <NavLink key={item.to} to={item.to} className="bottom-more-link" role="menuitem">
              <span className="nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </div>
      )}
    </nav>
  );
}
