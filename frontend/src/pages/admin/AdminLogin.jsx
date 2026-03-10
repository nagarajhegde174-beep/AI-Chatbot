import { useState } from "react";
import { useForm } from "react-hook-form";
import axios from "axios";
import { useNavigate, Link } from "react-router-dom";
import { Server_URL } from "../../utils/config";
import { showErrorToast, showSuccessToast } from "../../utils/toasthelper";
import "../../animations.css";

export default function AdminLogin() {
  const { register, handleSubmit, formState: { errors } } = useForm();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [showPass, setShowPass] = useState(false);

  const onSubmit = async (data) => {
    setLoading(true);
    try {
      const res = await axios.post(`${Server_URL}admin/login`, data);
      const token = res?.data?.token;
      const role  = res?.data?.user?.role;
      if (!token) throw new Error("Invalid response");
      localStorage.setItem("authToken", token);
      localStorage.setItem("role", role || "admin");
      showSuccessToast("Welcome, Administrator! 🛡️");
      navigate("/admin");
    } catch (error) {
      showErrorToast(error?.response?.data?.message || "Login failed.");
    } finally {
      setLoading(false);
    }
  };

  const S = {
    page: { minHeight:"100vh", background:"#f3f0fb", display:"flex", fontFamily:"'Plus Jakarta Sans',sans-serif", position:"relative", overflow:"hidden" },
    leftPanel: { width:"42%", background:"linear-gradient(145deg,#6d28d9,#8b5cf6,#a78bfa)", padding:"3rem 2.5rem", display:"flex", alignItems:"center", position:"relative", overflow:"hidden", flexShrink:0 },
    formSide: { flex:1, display:"flex", alignItems:"center", justifyContent:"center", padding:"2rem", position:"relative", zIndex:1 },
    card: { background:"#fff", borderRadius:28, padding:"2.5rem 2.2rem", width:"100%", maxWidth:420, boxShadow:"0 20px 60px rgba(100,60,200,0.12)", border:"2px solid #e4ddf5" },
    iconWrap: { width:64, height:64, background:"linear-gradient(135deg,#f9a8d4,#c4b5fd)", borderRadius:"50%", display:"flex", alignItems:"center", justifyContent:"center", margin:"0 auto 1.25rem", boxShadow:"0 8px 24px rgba(236,72,153,0.25)" },
    title: { textAlign:"center", fontSize:"1.6rem", fontWeight:800, color:"#2a2050", marginBottom:"0.3rem" },
    sub: { textAlign:"center", color:"#6b5e95", fontSize:"0.85rem", marginBottom:"1.8rem" },
    field: { marginBottom:"1.2rem" },
    label: { display:"block", fontWeight:600, color:"#2a2050", fontSize:"0.88rem", marginBottom:6 },
    input: { width:"100%", padding:"11px 14px", borderRadius:14, border:"2px solid #e4ddf5", fontFamily:"'Plus Jakarta Sans',sans-serif", fontSize:"0.92rem", color:"#2a2050", background:"#fafaff", boxSizing:"border-box" },
    eyeBtn: { position:"absolute", right:12, top:"50%", transform:"translateY(-50%)", background:"none", border:"none", cursor:"pointer", fontSize:"1rem" },
    error: { color:"#ef4444", fontSize:"0.8rem", marginTop:4, display:"block" },
    btn: { width:"100%", padding:"12px", background:"linear-gradient(135deg,#f9a8d4,#8b5cf6)", border:"none", borderRadius:14, color:"#fff", fontFamily:"'Plus Jakarta Sans',sans-serif", fontWeight:700, fontSize:"1rem", cursor:"pointer", boxShadow:"0 8px 24px rgba(139,92,246,0.3)", marginTop:"0.5rem" },
    spinner: { width:16, height:16, border:"2px solid rgba(255,255,255,0.3)", borderTop:"2px solid #fff", borderRadius:"50%", display:"inline-block", animation:"spin 0.7s linear infinite" },
  };

  return (
    <div style={S.page}>
      <div className="orb orb-pink"   style={{ width:300, height:300, top:-80, right:-60 }} />
      <div className="orb orb-purple" style={{ width:240, height:240, bottom:20, left:-50 }} />

      {/* Left panel */}
      <div style={S.leftPanel} className="anim-fade-left admin-left-panel">
        <div style={{ position:"relative", zIndex:1 }}>
          <div style={{ fontSize:"3.5rem", marginBottom:"1.5rem" }} className="anim-float">🛡️</div>
          <h2 style={{ color:"#fff", fontWeight:800, fontSize:"1.8rem", marginBottom:"0.75rem" }}>Admin Panel</h2>
          <p style={{ color:"rgba(255,255,255,0.8)", lineHeight:1.7, fontSize:"0.95rem" }}>
            Full system control over books, members, librarians, fines, and detailed reports.
          </p>
          <div style={{ marginTop:"2rem", display:"flex", flexDirection:"column", gap:12 }}>
            {["📊 View system reports","👥 Manage members","⚙️ Configure fine rules","📚 Oversee all books"].map((f,i)=>(
              <div key={i} className={`anim-fade-left anim-delay-${i+1}`} style={{ color:"rgba(255,255,255,0.85)", fontSize:"0.9rem" }}>
                <span style={{ background:"rgba(255,255,255,0.2)", borderRadius:8, padding:"4px 10px" }}>{f}</span>
              </div>
            ))}
          </div>
        </div>
        <div style={{ position:"absolute", width:200, height:200, background:"rgba(255,255,255,0.07)", borderRadius:"50%", bottom:-60, right:-40 }} />
      </div>

      {/* Form */}
      <div style={S.formSide}>
        <div style={S.card} className="anim-scale-in">
          <div style={S.iconWrap} className="anim-float">
            <span style={{ fontSize:"1.8rem" }}>🛡️</span>
          </div>
          <h2 style={S.title}>Administrator Login</h2>
          <p style={S.sub}>Restricted access — authorized personnel only</p>

          <form onSubmit={handleSubmit(onSubmit)}>
            <div style={S.field} className="anim-fade-up anim-delay-1">
              <label style={S.label}>Admin Email</label>
              <input type="email" placeholder="admin@college.edu" className="input-animated" style={S.input} {...register("email",{required:"Email is required"})} />
              {errors.email && <span style={S.error}>{errors.email.message}</span>}
            </div>
            <div style={S.field} className="anim-fade-up anim-delay-2">
              <label style={S.label}>Password</label>
              <div style={{ position:"relative" }}>
                <input type={showPass?"text":"password"} placeholder="Enter admin password" className="input-animated" style={{...S.input, paddingRight:44}} {...register("password",{required:"Password is required"})} />
                <button type="button" onClick={()=>setShowPass(!showPass)} style={S.eyeBtn}>{showPass?"🙈":"👁️"}</button>
              </div>
              {errors.password && <span style={S.error}>{errors.password.message}</span>}
            </div>
            <button type="submit" disabled={loading} className="btn-animated anim-fade-up anim-delay-3" style={S.btn}>
              {loading ? <span style={{display:"flex",alignItems:"center",justifyContent:"center",gap:8}}><span style={S.spinner}/>Authenticating…</span> : "Access Admin Panel →"}
            </button>
          </form>

          <div style={{ textAlign:"center", marginTop:"1.5rem" }}>
            <Link to="/login-portal" style={{ color:"#b8aad8", fontSize:"0.82rem" }}>← Back to login portal</Link>
          </div>
        </div>
      </div>

      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');
        @keyframes float{0%,100%{transform:translateY(0)}50%{transform:translateY(-12px)}}
        .anim-float{animation:float 4s ease-in-out infinite;}
        @keyframes spin{to{transform:rotate(360deg)}}
        input::placeholder{color:#b8aad8;}
        @media(max-width:768px){.admin-left-panel{display:none!important;}}
      `}</style>
    </div>
  );
}
