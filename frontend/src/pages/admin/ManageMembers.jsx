import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showSuccessToast, showErrorToast } from "../../utils/toasthelper";

const S = {
  page: { fontFamily:"'Plus Jakarta Sans',sans-serif", background:"#f3f0fb", minHeight:"100vh", padding:"2rem 2.5rem" },
  header: { display:"flex", alignItems:"center", justifyContent:"space-between", marginBottom:"1.75rem", flexWrap:"wrap", gap:"1rem" },
  title: { fontSize:"1.65rem", fontWeight:800, color:"#2a2050", letterSpacing:"-.02em", display:"flex", alignItems:"center", gap:10 },
  titleIcon: { width:44, height:44, background:"linear-gradient(135deg,#c4b5fd,#93c5fd)", borderRadius:14, display:"flex", alignItems:"center", justifyContent:"center", fontSize:"1.2rem", boxShadow:"0 4px 14px rgba(139,92,246,.25)" },
  badge: { background:"rgba(139,92,246,.1)", color:"#8b5cf6", borderRadius:99, padding:"4px 14px", fontSize:".82rem", fontWeight:700 },
  searchBox: { background:"#fff", border:"2px solid #e4ddf5", borderRadius:14, padding:"10px 16px", width:"100%", maxWidth:380, fontSize:".9rem", fontFamily:"'Plus Jakarta Sans',sans-serif", color:"#2a2050", outline:"none", transition:"border .2s" },
  tableWrap: { background:"#fff", border:"2px solid #e4ddf5", borderRadius:24, overflow:"hidden", boxShadow:"0 6px 24px rgba(100,60,200,.08)" },
  th: { padding:"12px 18px", textAlign:"left", fontSize:".7rem", fontWeight:800, letterSpacing:".1em", textTransform:"uppercase", color:"#b8aad8", background:"#f8f6ff", whiteSpace:"nowrap" },
  td: { padding:"14px 18px", fontSize:".88rem", color:"#6b5e95", borderTop:"1px solid #f0ecfa", verticalAlign:"middle" },
  tdStrong: { color:"#2a2050", fontWeight:700, fontSize:".92rem" },
  memberIdCode: { fontFamily:"'IBM Plex Mono',monospace", fontSize:".78rem", background:"#f3f0fb", padding:"3px 8px", borderRadius:8, color:"#8b5cf6", whiteSpace:"nowrap" },
  badgeActive: { background:"#d1fae5", color:"#065f46", padding:"4px 12px", borderRadius:99, fontSize:".78rem", fontWeight:700 },
  badgeInactive: { background:"#fee2e2", color:"#991b1b", padding:"4px 12px", borderRadius:99, fontSize:".78rem", fontWeight:700 },
  btnHistory: { background:"rgba(139,92,246,.09)", border:"2px solid #e4ddf5", color:"#8b5cf6", borderRadius:10, padding:"6px 14px", fontSize:".8rem", fontWeight:600, cursor:"pointer", fontFamily:"'Plus Jakarta Sans',sans-serif", transition:"all .18s", whiteSpace:"nowrap" },
  btnDeactivate: { background:"rgba(239,68,68,.07)", border:"2px solid #fecaca", color:"#ef4444", borderRadius:10, padding:"6px 14px", fontSize:".8rem", fontWeight:600, cursor:"pointer", fontFamily:"'Plus Jakarta Sans',sans-serif", transition:"all .18s", whiteSpace:"nowrap" },
  btnActivate: { background:"rgba(16,185,129,.07)", border:"2px solid #a7f3d0", color:"#059669", borderRadius:10, padding:"6px 14px", fontSize:".8rem", fontWeight:600, cursor:"pointer", fontFamily:"'Plus Jakarta Sans',sans-serif", transition:"all .18s", whiteSpace:"nowrap" },
  empty: { padding:"3rem", textAlign:"center", color:"#b8aad8", fontSize:"1rem" },
  spinner: { width:40, height:40, border:"4px solid #e4ddf5", borderTop:"4px solid #8b5cf6", borderRadius:"50%", animation:"spin .8s linear infinite", margin:"3rem auto" },
  // modal
  overlay: { position:"fixed", inset:0, background:"rgba(42,32,80,.45)", backdropFilter:"blur(4px)", zIndex:2000, display:"flex", alignItems:"center", justifyContent:"center", padding:"1rem" },
  modal: { background:"#fff", borderRadius:24, width:"100%", maxWidth:740, maxHeight:"85vh", display:"flex", flexDirection:"column", boxShadow:"0 24px 60px rgba(100,60,200,.22)", border:"2px solid #e4ddf5" },
  modalHead: { background:"linear-gradient(135deg,#8b5cf6,#6366f1)", borderRadius:"22px 22px 0 0", padding:"1.25rem 1.75rem", display:"flex", justifyContent:"space-between", alignItems:"flex-start" },
  modalTitle: { color:"#fff", fontWeight:800, fontSize:"1.1rem" },
  modalSub: { color:"rgba(255,255,255,.75)", fontSize:".82rem", marginTop:2 },
  modalClose: { background:"rgba(255,255,255,.2)", border:"none", color:"#fff", borderRadius:10, width:32, height:32, cursor:"pointer", fontSize:"1.1rem", display:"flex", alignItems:"center", justifyContent:"center" },
  modalBody: { overflowY:"auto", padding:"1.25rem 1.75rem" },
  modalFoot: { padding:"1rem 1.75rem", borderTop:"2px solid #f0ecfa", display:"flex", justifyContent:"flex-end" },
  mtd: { padding:"12px 14px", fontSize:".86rem", color:"#6b5e95", borderBottom:"1px solid #f3f0fb", verticalAlign:"middle" },
};

export default function ManageMembers() {
  const [members, setMembers]     = useState([]);
  const [selected, setSelected]   = useState(null);
  const [history, setHistory]     = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [loading, setLoading]     = useState(true);
  const [search, setSearch]       = useState("");
  const headers = { Authorization: `Bearer ${getAuthToken()}` };

  const fetchMembers = async () => {
    setLoading(true);
    try { const r = await axios.get(`${Server_URL}admin/members`, { headers }); setMembers(r.data.members || []); }
    catch { showErrorToast("Failed to fetch members"); }
    finally { setLoading(false); }
  };

  const toggleStatus = async (id) => {
    try { const r = await axios.put(`${Server_URL}admin/users/${id}/toggle`, {}, { headers }); showSuccessToast(r.data.message); fetchMembers(); }
    catch (e) { showErrorToast(e.response?.data?.message || "Failed to update status"); }
  };

  const viewHistory = async (m) => {
    setSelected(m); setShowModal(true); setHistory([]);
    try { const r = await axios.get(`${Server_URL}admin/members/${m._id}/history`, { headers }); setHistory(r.data.history || []); }
    catch { showErrorToast("Failed to load history"); }
  };

  useEffect(() => { fetchMembers(); }, []);

  const filtered = members.filter(m =>
    m.name?.toLowerCase().includes(search.toLowerCase()) ||
    m.email?.toLowerCase().includes(search.toLowerCase()) ||
    m.membershipId?.toLowerCase().includes(search.toLowerCase())
  );

  const statusBadge = (status) => (
    <span style={status === "Active" ? S.badgeActive : S.badgeInactive}>
      {status === "Active" ? "✓ Active" : "✗ Inactive"}
    </span>
  );

  return (
    <>
      <style>{`@keyframes spin{to{transform:rotate(360deg)}} .mmrow:hover td{background:#faf8ff !important;}`}</style>
      <div style={S.page}>

        {/* Header */}
        <div style={S.header}>
          <div style={{display:"flex",alignItems:"center",gap:14}}>
            <span style={S.titleIcon}>👥</span>
            <div>
              <h2 style={S.title}>Manage Members</h2>
              <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}>View, activate, or deactivate library members</p>
            </div>
          </div>
          <span style={S.badge}>{members.length} members</span>
        </div>

        {/* Search */}
        <div style={{marginBottom:"1.25rem"}}>
          <input
            style={S.searchBox}
            placeholder="🔍  Search by name, email or membership ID…"
            value={search}
            onChange={e=>setSearch(e.target.value)}
            onFocus={e=>e.target.style.borderColor="#8b5cf6"}
            onBlur={e=>e.target.style.borderColor="#e4ddf5"}
          />
        </div>

        {/* Table */}
        {loading ? <div style={S.spinner}/> : (
          <div style={S.tableWrap}>
            <table style={{width:"100%",borderCollapse:"collapse"}}>
              <thead>
                <tr>
                  {["#","Membership ID","Member","Email","Stream","Year","Status","Actions"].map(h=>(
                    <th key={h} style={S.th}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr><td colSpan={8} style={S.empty}>No members found.</td></tr>
                ) : filtered.map((m, i) => (
                  <tr key={m._id} className="mmrow">
                    <td style={{...S.td,color:"#b8aad8",fontWeight:700,width:40}}>{i+1}</td>
                    <td style={S.td}><span style={S.memberIdCode}>{m.membershipId || "—"}</span></td>
                    <td style={{...S.td,...S.tdStrong}}>{m.name}</td>
                    <td style={S.td}>{m.email}</td>
                    <td style={S.td}>{m.stream || "—"}</td>
                    <td style={S.td}>{m.year || "—"}</td>
                    <td style={S.td}>{statusBadge(m.status || "Active")}</td>
                    <td style={S.td}>
                      <div style={{display:"flex",gap:8,flexWrap:"nowrap"}}>
                        <button style={S.btnHistory} onClick={()=>viewHistory(m)}>📋 History</button>
                        <button
                          style={m.status==="Active" ? S.btnDeactivate : S.btnActivate}
                          onClick={()=>toggleStatus(m._id)}
                        >
                          {m.status==="Active" ? "🚫 Deactivate" : "✅ Activate"}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* History Modal */}
      {showModal && (
        <div style={S.overlay} onClick={e=>e.target===e.currentTarget&&setShowModal(false)}>
          <div style={S.modal}>
            <div style={S.modalHead}>
              <div>
                <div style={S.modalTitle}>📋 Borrowing History — {selected?.name}</div>
                <div style={S.modalSub}>{selected?.membershipId} · {selected?.email}</div>
              </div>
              <button style={S.modalClose} onClick={()=>setShowModal(false)}>✕</button>
            </div>
            <div style={S.modalBody}>
              {history.length === 0 ? (
                <p style={{textAlign:"center",color:"#b8aad8",padding:"2rem"}}>No borrowing history found.</p>
              ) : (
                <table style={{width:"100%",borderCollapse:"collapse"}}>
                  <thead>
                    <tr>{["Book","Issue Date","Due Date","Return Date","Status"].map(h=><th key={h} style={{...S.th,background:"#f8f6ff"}}>{h}</th>)}</tr>
                  </thead>
                  <tbody>
                    {history.map(h=>(
                      <tr key={h._id}>
                        <td style={S.mtd}>
                          <strong style={{color:"#2a2050"}}>{h.bookId?.title}</strong>
                          <div style={{fontSize:".78rem",color:"#b8aad8"}}>{h.bookId?.author}</div>
                        </td>
                        <td style={S.mtd}>{new Date(h.issueDate).toLocaleDateString()}</td>
                        <td style={S.mtd}>{new Date(h.dueDate).toLocaleDateString()}</td>
                        <td style={S.mtd}>{h.returnDate ? new Date(h.returnDate).toLocaleDateString() : "—"}</td>
                        <td style={S.mtd}>
                          <span style={{padding:"3px 10px",borderRadius:99,fontSize:".75rem",fontWeight:700,
                            background: h.status==="Returned"?"#d1fae5": h.status==="Issued"?"#dbeafe": h.status==="Requested Return"?"#fef3c7":"#f3f4f6",
                            color: h.status==="Returned"?"#065f46": h.status==="Issued"?"#1e40af": h.status==="Requested Return"?"#92400e":"#374151"
                          }}>{h.status}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
            <div style={S.modalFoot}>
              <button onClick={()=>setShowModal(false)} style={{background:"#f3f0fb",border:"2px solid #e4ddf5",color:"#6b5e95",borderRadius:12,padding:"8px 22px",fontWeight:600,cursor:"pointer",fontFamily:"'Plus Jakarta Sans',sans-serif"}}>Close</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
