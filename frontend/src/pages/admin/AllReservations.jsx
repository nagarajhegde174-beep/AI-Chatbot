import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showSuccessToast, showErrorToast } from "../../utils/toasthelper";

const STATUS_STYLE = {
  Pending:   { bg:"#fef3c7", color:"#92400e", label:"⏳ Pending"   },
  Notified:  { bg:"#dbeafe", color:"#1e40af", label:"🔔 Notified"  },
  Fulfilled: { bg:"#d1fae5", color:"#065f46", label:"✅ Fulfilled"  },
  Cancelled: { bg:"#f3f4f6", color:"#374151", label:"✕ Cancelled"  },
  Expired:   { bg:"#fee2e2", color:"#991b1b", label:"⌛ Expired"   },
};

export default function AllReservations() {
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading]           = useState(true);
  const [filter, setFilter]             = useState("All");
  const headers = { Authorization: `Bearer ${getAuthToken()}` };

  const fetchReservations = async () => {
    setLoading(true);
    try { const r = await axios.get(`${Server_URL}reservations`, { headers }); setReservations(r.data.reservations || []); }
    catch { showErrorToast("Failed to fetch reservations"); }
    finally { setLoading(false); }
  };

  const notifyMember = async (bookId) => {
    try { const r = await axios.put(`${Server_URL}reservations/notify/${bookId}`, {}, { headers }); showSuccessToast(r.data.message); fetchReservations(); }
    catch (e) { showErrorToast(e.response?.data?.message || "Failed to notify"); }
  };

  useEffect(() => { fetchReservations(); }, []);

  const statuses  = ["All", "Pending", "Notified", "Fulfilled", "Cancelled", "Expired"];
  const displayed = filter === "All" ? reservations : reservations.filter(r => r.status === filter);

  const counts = statuses.reduce((acc, s) => {
    acc[s] = s === "All" ? reservations.length : reservations.filter(r=>r.status===s).length;
    return acc;
  }, {});

  return (
    <>
      <style>{`
        @keyframes spin{to{transform:rotate(360deg)}}
        .rrow:hover td{background:#faf8ff !important;}
        .rfilter{border:2px solid #e4ddf5;background:#fff;color:#6b5e95;border-radius:10px;padding:6px 16px;font-size:.82rem;font-weight:600;cursor:pointer;font-family:'Plus Jakarta Sans',sans-serif;transition:all .18s;}
        .rfilter:hover,.rfilter.active{background:#8b5cf6;border-color:#8b5cf6;color:#fff;}
      `}</style>
      <div style={{fontFamily:"'Plus Jakarta Sans',sans-serif",background:"#f3f0fb",minHeight:"100vh",padding:"2rem 2.5rem"}}>

        {/* Header */}
        <div style={{display:"flex",alignItems:"center",justifyContent:"space-between",marginBottom:"1.75rem",flexWrap:"wrap",gap:"1rem"}}>
          <div style={{display:"flex",alignItems:"center",gap:14}}>
            <span style={{width:44,height:44,background:"linear-gradient(135deg,#c4b5fd,#93c5fd)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(139,92,246,.25)"}}>📋</span>
            <div>
              <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>All Reservations</h2>
              <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}>Manage book reservation queue and notify members</p>
            </div>
          </div>
          <span style={{background:"rgba(139,92,246,.1)",color:"#8b5cf6",borderRadius:99,padding:"4px 14px",fontSize:".82rem",fontWeight:700}}>{reservations.length} total</span>
        </div>

        {/* Summary cards */}
        <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(120px,1fr))",gap:"1rem",marginBottom:"1.5rem"}}>
          {[
            { label:"Pending",   val:counts.Pending,   bg:"#fef3c7", color:"#92400e", icon:"⏳" },
            { label:"Notified",  val:counts.Notified,  bg:"#dbeafe", color:"#1e40af", icon:"🔔" },
            { label:"Fulfilled", val:counts.Fulfilled, bg:"#d1fae5", color:"#065f46", icon:"✅" },
            { label:"Cancelled", val:counts.Cancelled, bg:"#f3f4f6", color:"#374151", icon:"✕"  },
          ].map(c=>(
            <div key={c.label} style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:18,padding:"1.1rem 1.25rem",boxShadow:"0 2px 8px rgba(100,60,200,.06)",display:"flex",alignItems:"center",gap:12}}>
              <span style={{width:38,height:38,background:c.bg,borderRadius:12,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1rem",color:c.color,flexShrink:0}}>{c.icon}</span>
              <div>
                <div style={{fontSize:"1.5rem",fontWeight:800,color:"#2a2050",fontFamily:"'IBM Plex Mono',monospace",lineHeight:1}}>{c.val}</div>
                <div style={{fontSize:".72rem",fontWeight:700,color:"#b8aad8",textTransform:"uppercase",letterSpacing:".06em"}}>{c.label}</div>
              </div>
            </div>
          ))}
        </div>

        {/* Filters */}
        <div style={{display:"flex",gap:8,flexWrap:"wrap",marginBottom:"1.25rem"}}>
          {statuses.map(s=>(
            <button key={s} className={`rfilter${filter===s?" active":""}`} onClick={()=>setFilter(s)}>
              {s} {counts[s]>0&&<span style={{marginLeft:4,opacity:.7}}>({counts[s]})</span>}
            </button>
          ))}
        </div>

        {/* Table */}
        {loading ? (
          <div style={{width:40,height:40,border:"4px solid #e4ddf5",borderTop:"4px solid #8b5cf6",borderRadius:"50%",animation:"spin .8s linear infinite",margin:"3rem auto"}}/>
        ) : (
          <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:24,overflow:"hidden",boxShadow:"0 6px 24px rgba(100,60,200,.08)"}}>
            {displayed.length === 0 ? (
              <div style={{padding:"3rem",textAlign:"center",color:"#b8aad8"}}>
                <div style={{fontSize:"2.5rem",marginBottom:".5rem"}}>📋</div>
                No reservations found.
              </div>
            ) : (
              <table style={{width:"100%",borderCollapse:"collapse"}}>
                <thead>
                  <tr style={{background:"#f8f6ff",borderBottom:"2px solid #e4ddf5"}}>
                    {["#","Book","Member","Membership ID","Reserved On","Status","Action"].map(h=>(
                      <th key={h} style={{padding:"12px 18px",textAlign:"left",fontSize:".7rem",fontWeight:800,letterSpacing:".1em",textTransform:"uppercase",color:"#b8aad8",whiteSpace:"nowrap"}}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {displayed.map((r,i)=>{
                    const st = STATUS_STYLE[r.status] || STATUS_STYLE.Cancelled;
                    return (
                      <tr key={r._id} className="rrow">
                        <td style={{padding:"14px 18px",color:"#b8aad8",fontWeight:700,fontSize:".85rem",borderTop:"1px solid #f0ecfa"}}>{i+1}</td>
                        <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                          <div style={{fontWeight:700,color:"#2a2050",fontSize:".9rem"}}>{r.bookId?.title}</div>
                          <div style={{fontSize:".75rem",color:"#b8aad8",marginTop:2}}>ISBN: {r.bookId?.isbn || "—"}</div>
                        </td>
                        <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                          <div style={{fontWeight:600,color:"#2a2050",fontSize:".88rem"}}>{r.userId?.name}</div>
                          <div style={{fontSize:".75rem",color:"#b8aad8",marginTop:2}}>{r.userId?.email}</div>
                        </td>
                        <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                          <span style={{fontFamily:"'IBM Plex Mono',monospace",fontSize:".78rem",background:"#f3f0fb",padding:"3px 8px",borderRadius:8,color:"#8b5cf6"}}>{r.userId?.membershipId || "—"}</span>
                        </td>
                        <td style={{padding:"14px 18px",color:"#6b5e95",fontSize:".86rem",borderTop:"1px solid #f0ecfa"}}>{new Date(r.reservationDate).toLocaleDateString()}</td>
                        <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                          <span style={{padding:"4px 12px",borderRadius:99,fontSize:".76rem",fontWeight:700,background:st.bg,color:st.color}}>{st.label}</span>
                        </td>
                        <td style={{padding:"14px 18px",borderTop:"1px solid #f0ecfa"}}>
                          {r.status === "Pending" && (
                            <button onClick={()=>notifyMember(r.bookId?._id)} style={{background:"linear-gradient(135deg,#c4b5fd,#93c5fd)",border:"none",color:"#2a2050",borderRadius:10,padding:"7px 14px",fontSize:".8rem",fontWeight:700,cursor:"pointer",fontFamily:"'Plus Jakarta Sans',sans-serif",boxShadow:"0 2px 8px rgba(139,92,246,.2)"}}>
                              🔔 Notify
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </>
  );
}
