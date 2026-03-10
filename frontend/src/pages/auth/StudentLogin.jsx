import { useState } from "react";
import { useForm } from "react-hook-form";
import axios from "axios";
import { useNavigate, Link } from "react-router-dom";
import { Server_URL } from "../../utils/config";
import { showErrorToast, showSuccessToast } from "../../utils/toasthelper";
import "../../animations.css";

export default function StudentLogin() {
  const { register, handleSubmit, formState: { errors } } = useForm();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPass, setShowPass] = useState(false);

  const onSubmit = async (data) => {
    setLoading(true);
    try {
      const res = await axios.post(`${Server_URL}users/login`, {
        email: data.email,
        password: data.password,
      });
      const token = res?.data?.token;
      const role  = res?.data?.user?.role;
      if (!token || !role) throw new Error("Invalid login response");

      if (role !== "user") {
        showErrorToast("This login is for students only.");
        return;
      }
      localStorage.setItem("authToken", token);
      localStorage.setItem("role", role);
      showSuccessToast("Welcome back! 🎓");
      navigate("/");
    } catch (error) {
      const msg = error?.response?.data?.message || error?.message || "Login failed.";
      showErrorToast(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.page}>
      {/* Orbs */}
      <div className="orb orb-purple" style={{ width: 280, height: 280, top: -60, left: -60 }} />
      <div className="orb orb-blue"   style={{ width: 220, height: 220, bottom: 40, right: -40 }} />

      <div style={styles.card} className="anim-scale-in">
        {/* Icon */}
        <div style={styles.iconWrap} className="anim-float">
          <span style={{ fontSize: "1.8rem" }}>🎓</span>
        </div>

        <h2 style={styles.title}>Student Login</h2>
        <p style={styles.sub}>Access your library account</p>

        <form onSubmit={handleSubmit(onSubmit)}>
          {/* Email */}
          <div style={styles.field} className="anim-fade-up anim-delay-1">
            <label style={styles.label}>Email Address</label>
            <input
              type="email"
              placeholder="student@college.edu"
              className="input-animated"
              style={styles.input}
              {...register("email", { required: "Email is required" })}
            />
            {errors.email && <span style={styles.error}>{errors.email.message}</span>}
          </div>

          {/* Password */}
          <div style={styles.field} className="anim-fade-up anim-delay-2">
            <label style={styles.label}>Password</label>
            <div style={{ position: "relative" }}>
              <input
                type={showPass ? "text" : "password"}
                placeholder="Enter password"
                className="input-animated"
                style={{ ...styles.input, paddingRight: 44 }}
                {...register("password", { required: "Password is required" })}
              />
              <button
                type="button"
                onClick={() => setShowPass(!showPass)}
                style={styles.eyeBtn}
              >
                {showPass ? "🙈" : "👁️"}
              </button>
            </div>
            {errors.password && <span style={styles.error}>{errors.password.message}</span>}
          </div>

          {/* Forgot */}
          <div style={{ textAlign: "right", marginBottom: "1.5rem" }} className="anim-fade-up anim-delay-3">
            <Link to="/forgetPassword" style={styles.forgotLink}>Forgot Password?</Link>
          </div>

          {/* Submit */}
          <button
            type="submit"
            disabled={loading}
            className="btn-animated anim-fade-up anim-delay-4"
            style={styles.btn}
          >
            {loading ? (
              <span style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 8 }}>
                <span style={styles.spinner} />
                Logging in…
              </span>
            ) : "Login →"}
          </button>
        </form>

        {/* Links */}
        <div style={{ textAlign: "center", marginTop: "1.5rem" }} className="anim-fade-up anim-delay-5">
          <span style={{ color: "#b8aad8", fontSize: "0.88rem" }}>Don&apos;t have an account? </span>
          <Link to="/register" style={{ color: "#8b5cf6", fontWeight: 700, fontSize: "0.88rem" }}>Register</Link>
        </div>

        <div style={{ textAlign: "center", marginTop: "1rem" }}>
          <Link to="/login-portal" style={{ color: "#b8aad8", fontSize: "0.82rem" }}>← Back to login portal</Link>
        </div>
      </div>

      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');
        @keyframes float { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-12px)} }
        .anim-float { animation: float 4s ease-in-out infinite; }
        input::placeholder { color: #b8aad8; }
      `}</style>
    </div>
  );
}

const styles = {
  page: {
    minHeight: "100vh",
    background: "#f3f0fb",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    padding: "2rem",
    position: "relative",
    overflow: "hidden",
  },
  card: {
    background: "#ffffff",
    borderRadius: 28,
    padding: "2.5rem 2.2rem",
    width: "100%",
    maxWidth: 420,
    boxShadow: "0 20px 60px rgba(100,60,200,0.12)",
    border: "2px solid #e4ddf5",
    position: "relative",
    zIndex: 1,
  },
  iconWrap: {
    width: 64, height: 64,
    background: "linear-gradient(135deg, #c4b5fd, #93c5fd)",
    borderRadius: "50%",
    display: "flex", alignItems: "center", justifyContent: "center",
    margin: "0 auto 1.25rem",
    boxShadow: "0 8px 24px rgba(139,92,246,0.28)",
  },
  title: {
    textAlign: "center",
    fontSize: "1.6rem",
    fontWeight: 800,
    color: "#2a2050",
    marginBottom: "0.3rem",
  },
  sub: {
    textAlign: "center",
    color: "#6b5e95",
    fontSize: "0.9rem",
    marginBottom: "1.8rem",
  },
  field: { marginBottom: "1.2rem" },
  label: {
    display: "block",
    fontWeight: 600,
    color: "#2a2050",
    fontSize: "0.88rem",
    marginBottom: "6px",
  },
  input: {
    width: "100%",
    padding: "11px 14px",
    borderRadius: 14,
    border: "2px solid #e4ddf5",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontSize: "0.92rem",
    color: "#2a2050",
    background: "#fafaff",
    boxSizing: "border-box",
  },
  eyeBtn: {
    position: "absolute",
    right: 12, top: "50%", transform: "translateY(-50%)",
    background: "none", border: "none", cursor: "pointer", fontSize: "1rem",
  },
  error: {
    color: "#ef4444",
    fontSize: "0.8rem",
    marginTop: 4,
    display: "block",
  },
  forgotLink: {
    color: "#8b5cf6",
    textDecoration: "none",
    fontSize: "0.85rem",
    fontWeight: 600,
  },
  btn: {
    width: "100%",
    padding: "12px",
    background: "linear-gradient(135deg, #c4b5fd, #8b5cf6)",
    border: "none",
    borderRadius: 14,
    color: "#fff",
    fontFamily: "'Plus Jakarta Sans', sans-serif",
    fontWeight: 700,
    fontSize: "1rem",
    cursor: "pointer",
    boxShadow: "0 8px 24px rgba(139,92,246,0.32)",
  },
  spinner: {
    width: 16, height: 16,
    border: "2px solid rgba(255,255,255,0.3)",
    borderTop: "2px solid #fff",
    borderRadius: "50%",
    display: "inline-block",
    animation: "spin 0.7s linear infinite",
  },
};
