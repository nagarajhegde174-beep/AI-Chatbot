import { useState } from "react";
import { useNavigate } from "react-router-dom";
import "../../animations.css";

/* ─────────────────────────────────────────────────────────
   LoginPortal — role selector landing page
   Cotton Theme: #2a2050, #8b5cf6, #c4b5fd, #93c5fd
───────────────────────────────────────────────────────── */

const ROLES = [
  {
    key: "student",
    icon: "🎓",
    title: "Student / Member",
    subtitle: "Access library, borrow books & manage reservations",
    gradient: "linear-gradient(135deg, #c4b5fd 0%, #93c5fd 100%)",
    glow: "rgba(139,92,246,0.25)",
    to: "/login",
  },
  {
    key: "librarian",
    icon: "📚",
    title: "Librarian",
    subtitle: "Manage book issues, returns & member requests",
    gradient: "linear-gradient(135deg, #6ee7b7 0%, #93c5fd 100%)",
    glow: "rgba(16,185,129,0.22)",
    to: "/librarian-login",
  },
  {
    key: "admin",
    icon: "🛡️",
    title: "Administrator",
    subtitle: "Full system control, reports & configuration",
    gradient: "linear-gradient(135deg, #f9a8d4 0%, #c4b5fd 100%)",
    glow: "rgba(236,72,153,0.22)",
    to: "/admin-login",
  },
];

function RoleCard({ role, delay }) {
  const [hov, setHov] = useState(false);
  const navigate = useNavigate();

  return (
    <div
      className={`anim-fade-up anim-delay-${delay}`}
      onClick={() => navigate(role.to)}
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => setHov(false)}
      style={{
        background: "#ffffff",
        border: `2px solid ${hov ? "#c4b5fd" : "#e4ddf5"}`,
        borderRadius: 28,
        padding: "2.2rem 2rem",
        cursor: "pointer",
        textAlign: "center",
        transform: hov ? "translateY(-10px) scale(1.03)" : "translateY(0) scale(1)",
        boxShadow: hov
          ? `0 20px 50px ${role.glow}, 0 0 0 2px rgba(196,181,253,0.3)`
          : "0 6px 24px rgba(100,60,200,0.08)",
        transition: "all 0.35s cubic-bezier(0.34,1.56,0.64,1)",
      }}
    >
      {/* Icon blob */}
      <div
        style={{
          width: 80,
          height: 80,
          borderRadius: "50%",
          background: role.gradient,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontSize: "2rem",
          margin: "0 auto 1.25rem",
          boxShadow: `0 8px 24px ${role.glow}`,
          transform: hov ? "scale(1.12) rotate(6deg)" : "scale(1) rotate(0)",
          transition: "transform 0.4s cubic-bezier(0.34,1.56,0.64,1)",
        }}
      >
        {role.icon}
      </div>

      <h3
        style={{
          fontFamily: "'Plus Jakarta Sans', sans-serif",
          fontWeight: 800,
          fontSize: "1.2rem",
          color: "#2a2050",
          marginBottom: "0.5rem",
        }}
      >
        {role.title}
      </h3>
      <p
        style={{
          color: "#6b5e95",
          fontSize: "0.88rem",
          lineHeight: 1.6,
          marginBottom: "1.4rem",
        }}
      >
        {role.subtitle}
      </p>

      <div
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: "6px",
          background: hov ? role.gradient : "#f3f0fb",
          color: hov ? "#2a2050" : "#8b5cf6",
          fontFamily: "'Plus Jakarta Sans', sans-serif",
          fontWeight: 700,
          fontSize: "0.88rem",
          padding: "8px 22px",
          borderRadius: 999,
          transition: "all 0.3s ease",
          boxShadow: hov ? `0 4px 16px ${role.glow}` : "none",
        }}
      >
        Login as {role.title.split(" ")[0]}
        <span style={{ transform: hov ? "translateX(4px)" : "none", transition: "transform 0.2s ease" }}>→</span>
      </div>
    </div>
  );
}

export default function LoginPortal() {
  return (
    <div
      style={{
        minHeight: "100vh",
        background: "#f3f0fb",
        fontFamily: "'Plus Jakarta Sans', sans-serif",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "2rem",
        position: "relative",
        overflow: "hidden",
      }}
    >
      {/* Floating orbs */}
      <div className="orb orb-purple" style={{ width: 320, height: 320, top: -80, left: -80 }} />
      <div className="orb orb-blue"   style={{ width: 260, height: 260, top: 100, right: -60, animationDelay: "2s" }} />
      <div className="orb orb-pink"   style={{ width: 200, height: 200, bottom: -60, left: "40%" }} />

      {/* Header */}
      <div className="anim-fade-down" style={{ textAlign: "center", marginBottom: "2.5rem", position: "relative", zIndex: 1 }}>
        <div
          style={{
            width: 64,
            height: 64,
            background: "linear-gradient(135deg, #c4b5fd, #93c5fd)",
            borderRadius: 20,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            fontSize: "1.8rem",
            margin: "0 auto 1.2rem",
            boxShadow: "0 8px 28px rgba(139,92,246,0.3)",
            animation: "float 4s ease-in-out infinite",
          }}
        >
          📚
        </div>
        <h1
          style={{
            fontSize: "clamp(1.8rem, 4vw, 2.6rem)",
            fontWeight: 800,
            color: "#2a2050",
            letterSpacing: "-0.03em",
            marginBottom: "0.5rem",
          }}
        >
          AGC Library Portal
        </h1>
        <p style={{ color: "#6b5e95", fontSize: "1rem" }}>
          Select your role to continue
        </p>
      </div>

      {/* Role Cards */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
          gap: "1.5rem",
          maxWidth: 820,
          width: "100%",
          position: "relative",
          zIndex: 1,
        }}
      >
        {ROLES.map((role, i) => (
          <RoleCard key={role.key} role={role} delay={i + 1} />
        ))}
      </div>

      {/* Footer note */}
      <p
        className="anim-fade-up anim-delay-5"
        style={{ color: "#b8aad8", fontSize: "0.82rem", marginTop: "2rem", textAlign: "center" }}
      >
        AGC Library Management System • Academic Resource Center
      </p>

      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');
        @keyframes float {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-14px); }
        }
      `}</style>
    </div>
  );
}
