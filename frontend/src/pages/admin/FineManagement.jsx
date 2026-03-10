import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showSuccessToast, showErrorToast } from "../../utils/toasthelper";

export default function FineManagement() {
  const [fines, setFines]     = useState([]);
  const [stats, setStats]     = useState({ totalAmount:0, totalCollected:0, totalPending:0 });
  const [filter, setFilter]   = useState("all");
  const [loading, setLoading] = useState(true);
  const headers = { Authorization: `Bearer ${getAuthToken()}` };

  const fetchFines = async () => {
    setLoading(true);
    try {
      const r = await axios.get(`${Server_URL}fines`, { headers });
      setFines(r.data.fines || []);
      setStats({ totalAmount:r.data.totalAmount||0, totalCollected:r.data.totalCollected||0, totalPending:r.data.totalPending||0 });
    } catch { showErrorToast("Failed to fetch fines"); }
    finally { setLoading(false); }
  };

  const markPaid = async (id) => {
    try { const r = await axios.put(`${Server_URL}fines/pay/${id}`, {}, { headers }); showSuccessToast(r.data.message); fetchFines(); }
    catch (e) { showErrorToast(e.response?.data?.message || "Failed to mark as paid"); }
  };

  useEffect(() => { fetchFines(); }, []);

  const displayed = fines.filter(f =>
    filter === "all" ? true : filter === "paid" ? f.paidStatus : !f.paidStatus
  );

  const statCards = [
    { label:"Total Fines",  val:`₹${stats.totalAmount}`,     icon:"💰", bg:"#fee2e2", iconColor:"#991b1b", valColor:"#ef4444" },
    { label:"Collected",    val:`₹${stats.totalCollected}`,  icon:"✅", bg:"#d1fae5", iconColor:"#065f46", valColor:"#10b981" },
    { label:"Pending",      val:`₹${stats.totalPending}`,    icon:"⏳", bg:"#fef3c7", iconColor:"#92400e", valColor:"#f59e0b" },
    { label:"Total Records",val:fines.length,                icon:"📋", bg:"#dbeafe", iconColor:"#1e40af", valColor:"#6366f1" },
  ];

  return (
    <>
      <style>{`
        @keyframes spin{to{transform:rotate(360deg)}}
        .frow:hover td{background:#faf8ff !important;}
        .ftab{border:2px solid #e4ddf5;background:#fff;color:#6b5e95;border-radius:10px;padding:6px 18px;font-size:.83rem;font-weight:600;cursor:pointer;font-family:'Plus Jakarta Sans',sans-serif;transition:all .18s;}
        .ftab:hover{background:#f3f0fb;color:#8b5cf6;border-color:#c4b5fd;}
        .ftab.active-all{background:#2a2050;border-color:#2a2050;color:#fff;}
        .ftab.active-unpaid{background:#ef4444;border-color:#ef4444;color:#fff;}
        .ftab.active-paid{background:#10b981;border-color:#10b981;color:#fff;}
      `}</style>
      <div style={{fontFamily:"'Plus Jakarta Sans',sans-serif",background:"#f3f0fb",minHeight:"100vh",padding:"2rem 2.5rem"}}>

        {/* Header */}
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:"1.75rem",flexWrap:"wrap",gap:"1rem"}}>
          <div style={{display:"flex",alignItems:"center",gap:14}}>
            <span style={{width:44,height:44,background:"linear-gradient(135deg,#fca5a5,#fde68a)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(239,68,68,.2)"}}>💰</span>
            <div>
              <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>Fine Management</h2>
              <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}>Track overdue fines and manage payments</p>
            </div>
          </div>
        </div>

        {/* Stat cards */}
        <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(180px,1fr))",gap:"1rem",marginBottom:"1.75rem"}}>
          {statCards.map(c=>(
            <div key={c.label} style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:22,padding:"1.4rem 1.5rem",boxShadow:"0 4px 16px rgba(100,60,200,.07)",display:"flex",alignItems:"center",gap:14,transition:"transform .2s,box-shadow .2s"}}>
              <span style={{width:46,height:46,background:c.bg,borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.3rem",flexShrink:0}}>{c.icon}</span>
              <div>
                <div style={{fontSize:"1.6rem",fontWeight:800,color:c.valColor,fontFamily:"'IBM Plex Mono',monospace",lineHeight:1}}>{c.val}</div>
                <div style={{fontSize:".72rem",fontWeight:700,color:"#b8aad8",textTransform:"uppercase",letterSpacing:".08em",marginTop:3}}>{c.label}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Filter tabs */}
        <div style={{display:"flex",gap:8,marginBottom:"1.25rem",flexWrap:"wrap",alignItems:"center"}}>
          <span style={{fontSize:".8rem",fontWeight:700,color:"#b8aad8",textTransform:"uppercase",letterSpacing:".08em",marginRight:4}}>Filter:</span>
          {[["all","All Fines"],["unpaid","Unpaid"],["paid","Paid"]].map(([val,label])=>(
            <button key={val} className={`ftab${filter===val?` active-${val}`:""}`} onClick={()=>setFilter(val)}>{label}</button>
          ))}
          <span style={{marginLeft:"auto",fontSize:".82rem",color:"#b8aad8"}}>{displayed.length} records</span>
        </div>

        {/* Table */}
        {loading ? (
          <div style={{width:40,height:40,border:"4px solid #e4ddf5",borderTop:"4px solid #8b5cf6",borderRadius:"50%",animation:"spin .8s linear infinite",margin:"3rem auto"}}/>
        ) : (
          <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:24,overflow:"hidden",boxShadow:"0 6px 24px rgba(100,60,200,.08)"}}>
            {displayed.length === 0 ? (
              <div style={{padding:"3rem",textAlign:"center",color:"#b8aad8"}}>
                <div style={{fontSize:"2.5rem",marginBottom:".5rem"}}>💰</div>
                No fines found.
              </div>
            ) : (
              <table style={{width:"100%",borderCollapse:"collapse"}}>
                <thead>
                  <tr style={{background:"#f8f6ff",borderBottom:"2px solid #e4ddf5"}}>
                    {["#","Member","Book","Days Overdue","Amount","Status","Date","Action"].map(h=>(
                      <th key={h} style={{padding:"12px 18px",textAlign:"left",fontSize:".7rem",fontWeight:800,letterSpacing:".1em",textTransform:"uppercase",color:"#b8aad8",whiteSpace:"nowrap"}}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {displayed.map((f,i)=>(
                    <tr key={f._id} className="frow">
                      <td style={{padding:"14px 18px",color:"#b8aad8",fontWeight:700,fontSize:".85rem",borderTop:"1px solid #f0ecfa"}}>{i+1}</td>
                      <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                        <div style={{fontWeight:700,color:"#2a2050",fontSize:".9rem"}}>{f.memberId?.name}</div>
                        <span style={{fontFamily:"'IBM Plex Mono',monospace",fontSize:".73rem",background:"#f3f0fb",padding:"2px 6px",borderRadius:6,color:"#8b5cf6"}}>{f.memberId?.membershipId || "—"}</span>
                      </td>
                      <td style={{padding:"14px 18px",color:"#6b5e95",fontSize:".86rem",borderTop:"1px solid #f0ecfa",maxWidth:180}}>
                        <div style={{fontWeight:600,color:"#2a2050",overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap"}}>{f.bookId?.title}</div>
                      </td>
                      <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                        <span style={{background:"#fef3c7",color:"#92400e",padding:"4px 10px",borderRadius:99,fontSize:".76rem",fontWeight:700}}>{f.daysOverdue} days</span>
                      </td>
                      <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                        <span style={{fontFamily:"'IBM Plex Mono',monospace",fontWeight:800,fontSize:"1.05rem",color:"#ef4444"}}>₹{f.amount}</span>
                      </td>
                      <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                        {f.paidStatus
                          ? <span style={{background:"#d1fae5",color:"#065f46",padding:"4px 10px",borderRadius:99,fontSize:".76rem",fontWeight:700}}>✓ Paid</span>
                          : <span style={{background:"#fee2e2",color:"#991b1b",padding:"4px 10px",borderRadius:99,fontSize:".76rem",fontWeight:700}}>✗ Unpaid</span>
                        }
                      </td>
                      <td style={{padding:"14px 18px",color:"#6b5e95",fontSize:".84rem",borderTop:"1px solid #f0ecfa"}}>{new Date(f.date).toLocaleDateString()}</td>
                      <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                        {!f.paidStatus && (
                          <button onClick={()=>markPaid(f._id)} style={{background:"linear-gradient(135deg,#6ee7b7,#34d399)",border:"none",color:"#064e3b",borderRadius:10,padding:"7px 14px",fontSize:".8rem",fontWeight:700,cursor:"pointer",fontFamily:"'Plus Jakarta Sans',sans-serif",boxShadow:"0 2px 8px rgba(16,185,129,.25)"}}>
                            ✅ Mark Paid
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </>
  );
}
