import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

/* ══════════════════════════════════════════════════
   NAVBAR — COTTON THEME
   Soft pastels · Playful rounded · Plus Jakarta Sans
   Matches: theme-5-cotton.css
   ══════════════════════════════════════════════════ */

const S = {
  nav: {
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    background: "#ffffff",
    borderBottom: "2px solid #e4ddf5",
    padding: "0 2rem",
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    height: "64px",
    position: "sticky",
    top: 0,
    zIndex: 1000,
    boxShadow: "0 4px 20px rgba(100,60,200,0.06)",
  },
  brand: {
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontWeight: 800,
    fontSize: "1.05rem",
    color: "#2a2050",
    textDecoration: "none",
    letterSpacing: "-0.01em",
    display: "flex",
    alignItems: "center",
    gap: "10px",
  },
  brandBadge: {
    width: 36, height: 36,
    background: "linear-gradient(135deg, #c4b5fd, #93c5fd)",
    borderRadius: "12px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "1rem",
    boxShadow: "0 4px 12px rgba(139,92,246,0.28)",
  },
  navList: {
    display: "flex",
    alignItems: "center",
    gap: "2px",
    listStyle: "none",
    margin: 0, padding: 0,
  },
  navLink: {
    color: "#6b5e95",
    textDecoration: "none",
    fontSize: "0.88rem",
    fontWeight: 500,
    padding: "6px 12px",
    borderRadius: "10px",
    display: "block",
    transition: "all 0.2s ease",
    position: "relative",
  },
  navLinkHover: {
    color: "#8b5cf6",
    background: "rgba(139,92,246,0.08)",
  },
  authGroup: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
  },
  btnLogin: {
    background: "transparent",
    border: "2px solid #e4ddf5",
    color: "#6b5e95",
    borderRadius: "12px",
    padding: "7px 18px",
    fontSize: "0.85rem",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontWeight: 600,
    cursor: "pointer",
    textDecoration: "none",
    display: "inline-block",
    transition: "all 0.2s ease",
  },
  btnSignup: {
    background: "linear-gradient(135deg, #c4b5fd, #93c5fd)",
    border: "none",
    color: "#2a2050",
    borderRadius: "12px",
    padding: "7px 18px",
    fontSize: "0.85rem",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontWeight: 700,
    cursor: "pointer",
    textDecoration: "none",
    display: "inline-block",
    boxShadow: "0 4px 14px rgba(139,92,246,0.28)",
    transition: "transform 0.3s cubic-bezier(0.34,1.56,0.64,1), box-shadow 0.2s ease",
  },
  profileBtn: {
    background: "rgba(139,92,246,0.08)",
    border: "2px solid #e4ddf5",
    color: "#8b5cf6",
    borderRadius: "12px",
    padding: "7px 14px",
    fontSize: "0.85rem",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontWeight: 600,
    cursor: "pointer",
    display: "flex",
    alignItems: "center",
    gap: "6px",
    transition: "all 0.2s ease",
  },
  dropdown: {
    position: "absolute",
    top: "calc(100% + 10px)",
    right: 0,
    background: "#ffffff",
    border: "2px solid #e4ddf5",
    borderRadius: "18px",
    minWidth: "168px",
    overflow: "hidden",
    boxShadow: "0 12px 36px rgba(100,60,200,0.12)",
    zIndex: 100,
  },
  dropdownItem: {
    display: "block",
    width: "100%",
    padding: "10px 18px",
    color: "#6b5e95",
    textDecoration: "none",
    fontSize: "0.88rem",
    fontWeight: 500,
    background: "transparent",
    border: "none",
    textAlign: "left",
    cursor: "pointer",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    transition: "background 0.15s, color 0.15s",
  },
  divider: {
    height: "2px",
    background: "#f3f0fb",
    margin: "3px 0",
  },
  hamburger: {
    background: "rgba(139,92,246,0.08)",
    border: "2px solid #e4ddf5",
    borderRadius: "10px",
    color: "#8b5cf6",
    padding: "6px 12px",
    cursor: "pointer",
    fontSize: "1rem",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontWeight: 700,
  },
  mobileMenu: {
    display: "flex",
    flexDirection: "column",
    gap: "4px",
    padding: "10px 0 14px",
  },
  // active dot indicator
  activeDot: {
    position: "absolute",
    bottom: 2, left: "50%",
    transform: "translateX(-50%)",
    width: 4, height: 4,
    borderRadius: "50%",
    background: "#8b5cf6",
  },
};

const NAV_LINKS = [
  { to: "/", label: "Home" },
  { to: "/books", label: "Books" },
  { to: "/category", label: "Category" },
  { to: "/aboutus", label: "About" },
  { to: "/contactus", label: "Contact" },
  { to: "/reservations", label: "Reservations" },
  { to: "/my-fines", label: "My Fines" },
];

function NavLinkItem({ to, label, onClick }) {
  const [hov, setHov] = useState(false);
  return (
    <li style={{ listStyle: "none" }}>
      <Link
        to={to} onClick={onClick}
        style={{ ...S.navLink, ...(hov ? S.navLinkHover : {}) }}
        onMouseEnter={() => setHov(true)}
        onMouseLeave={() => setHov(false)}
      >{label}</Link>
    </li>
  );
}

export default function Navbar() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const token = localStorage.getItem("authToken");
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem("authToken");
    navigate("/login-portal");
  };

  return (
    <>
      <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet" />
      <nav style={S.nav}>
        <Link to="/" style={S.brand}>
          <span style={S.brandBadge}>📚</span>
          AGC Library
        </Link>

        <ul style={S.navList} className="cotton-nav-desktop">
          {NAV_LINKS.map(l => <NavLinkItem key={l.to} to={l.to} label={l.label} />)}
        </ul>

        <div style={S.authGroup}>
          {token ? (
            <div style={{ position: "relative" }}>
              <button style={S.profileBtn} onClick={() => setDropdownOpen(!dropdownOpen)}>
                👤 Profile
                <span style={{ fontSize: "0.55rem", opacity: 0.7 }}>▼</span>
              </button>
              {dropdownOpen && (
                <div style={S.dropdown}>
                  <Link
                    to="/user" style={S.dropdownItem}
                    onClick={() => setDropdownOpen(false)}
                    onMouseEnter={e => { e.target.style.background = "#f3f0fb"; e.target.style.color = "#8b5cf6"; }}
                    onMouseLeave={e => { e.target.style.background = "transparent"; e.target.style.color = "#6b5e95"; }}
                  >My Profile</Link>
                  <div style={S.divider} />
                  <button
                    style={S.dropdownItem} onClick={handleLogout}
                    onMouseEnter={e => { e.target.style.background = "rgba(239,68,68,0.05)"; e.target.style.color = "#ef4444"; }}
                    onMouseLeave={e => { e.target.style.background = "transparent"; e.target.style.color = "#6b5e95"; }}
                  >Logout</button>
                </div>
              )}
            </div>
          ) : (
            <>
              <Link to="/login-portal" style={S.btnLogin}>Login</Link>
              <Link
                to="/register"
                style={S.btnSignup}
                onMouseEnter={e => { e.target.style.transform = "translateY(-2px) scale(1.03)"; e.target.style.boxShadow = "0 8px 20px rgba(139,92,246,0.35)"; }}
                onMouseLeave={e => { e.target.style.transform = "none"; e.target.style.boxShadow = "0 4px 14px rgba(139,92,246,0.28)"; }}
              >Sign Up</Link>
            </>
          )}
          <button style={S.hamburger} onClick={() => setMenuOpen(!menuOpen)} className="cotton-hamburger">
            {menuOpen ? "✕" : "☰"}
          </button>
        </div>
      </nav>

      {menuOpen && (
        <div style={{ background: "#faf7f2", borderBottom: "2px solid #e4ddf5", padding: "0 2rem" }}>
          <div style={S.mobileMenu}>
            {NAV_LINKS.map(l => <NavLinkItem key={l.to} to={l.to} label={l.label} onClick={() => setMenuOpen(false)} />)}
          </div>
        </div>
      )}

      <style>{`
        @media (min-width: 993px) { .cotton-hamburger { display: none !important; } }
        @media (max-width: 992px) { .cotton-nav-desktop { display: none !important; } .cotton-hamburger { display: block !important; } }
      `}</style>
    </>
  );
}
