import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Bar, Pie } from "react-chartjs-2";
import "chart.js/auto";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showErrorToast } from "../../utils/toasthelper";

const TABS = [
  { key:"summary",     label:"Summary",      icon:"📊" },
  { key:"issued",      label:"Issued Books",  icon:"📗" },
  { key:"overdue",     label:"Overdue",       icon:"⚠️" },
  { key:"fines",       label:"Fines",         icon:"💰" },
  { key:"mostBorrowed",label:"Most Borrowed", icon:"🔥" },
];

const TH = { padding:"12px 18px", textAlign:"left", fontSize:".7rem", fontWeight:800, letterSpacing:".1em", textTransform:"uppercase", color:"#b8aad8", background:"#f8f6ff", whiteSpace:"nowrap" };
const TD = { padding:"13px 18px", fontSize:".86rem", color:"#6b5e95", borderTop:"1px solid #f0ecfa", verticalAlign:"middle" };

export default function Reports() {
  const [tab, setTab]                   = useState("summary");
  const [summary, setSummary]           = useState(null);
  const [issuedBooks, setIssuedBooks]   = useState([]);
  const [overdueBooks, setOverdueBooks] = useState([]);
  const [fines, setFines]               = useState({ fines:[], totalAmount:0, totalCollected:0, totalPending:0 });
  const [mostBorrowed, setMostBorrowed] = useState([]);
  const [loading, setLoading]           = useState(false);
  const headers = { Authorization: `Bearer ${getAuthToken()}` };

  const load = async (key, url, setter) => {
    setLoading(true);
    try { const r = await axios.get(`${Server_URL}${url}`, { headers }); setter(r.data); }
    catch { showErrorToast("Failed to load data"); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    if (tab === "summary")      load("summary",      "reports/summary",       d => setSummary(d.summary));
    if (tab === "issued")       load("issued",       "reports/issued",        d => setIssuedBooks(d.records || []));
    if (tab === "overdue")      load("overdue",      "reports/overdue",       d => setOverdueBooks(d.records || []));
    if (tab === "fines")        load("fines",        "reports/fines",         d => setFines(d));
    if (tab === "mostBorrowed") load("mostBorrowed", "reports/most-borrowed", d => setMostBorrowed(d.books || []));
  }, [tab]);

  const barData = mostBorrowed.length ? {
    labels: mostBorrowed.map(b => b.book?.title?.length > 18 ? b.book.title.slice(0,18)+"…" : b.book?.title),
    datasets: [{ label:"Borrows", data: mostBorrowed.map(b=>b.borrowCount),
      backgroundColor:["#c4b5fd","#93c5fd","#f9a8d4","#6ee7b7","#fde68a","#fca5a5","#a5b4fc","#67e8f9","#d9f99d","#fed7aa"],
      borderRadius:8, borderSkipped:false }],
  } : null;

  const pieData = {
    labels: ["Collected","Pending"],
    datasets: [{ data:[fines.totalCollected, fines.totalPending], backgroundColor:["#6ee7b7","#fca5a5"], borderWidth:0 }],
  };

  const summaryCards = summary ? [
    { label:"Total Books",      val:summary.totalBooks,              icon:"📚", bg:"#dbeafe", valColor:"#6366f1" },
    { label:"Total Members",    val:summary.totalMembers,            icon:"👥", bg:"#d1fae5", valColor:"#10b981" },
    { label:"Currently Issued", val:summary.totalIssued,             icon:"📖", bg:"#fef3c7", valColor:"#f59e0b" },
    { label:"Overdue Books",    val:summary.totalOverdue,            icon:"⚠️", bg:"#fee2e2", valColor:"#ef4444" },
    { label:"Fines Pending",    val:`₹${summary.totalFinesPending}`, icon:"⏳", bg:"#ede9f8", valColor:"#8b5cf6" },
    { label:"Fines Collected",  val:`₹${summary.totalFinesCollected}`,icon:"✅",bg:"#d1fae5", valColor:"#059669" },
  ] : [];

  return (
    <>
      <style>{`
        @keyframes spin{to{transform:rotate(360deg)}}
        .rprow:hover td{background:#faf8ff !important;}
        .rptab{display:flex;align-items:center;gap:10px;width:100%;padding:.75rem 1rem;border:none;background:transparent;color:#6b5e95;font-size:.88rem;font-weight:500;border-radius:14px;cursor:pointer;font-family:'Plus Jakarta Sans',sans-serif;transition:all .18s;text-align:left;}
        .rptab:hover{background:#f3f0fb;color:#8b5cf6;}
        .rptab.active{background:rgba(139,92,246,.12);color:#8b5cf6;font-weight:700;}
        .rptab.active::after{content:'';display:block;width:6px;height:6px;border-radius:50%;background:#8b5cf6;margin-left:auto;flex-shrink:0;box-shadow:0 0 8px rgba(139,92,246,.6);}
      `}</style>
      <div style={{fontFamily:"'Plus Jakarta Sans',sans-serif",background:"#f3f0fb",minHeight:"100vh",display:"flex"}}>

        {/* Sidebar */}
        <aside style={{width:220,background:"#fff",borderRight:"2px solid #e4ddf5",padding:"2rem 1rem",position:"sticky",top:64,height:"calc(100vh - 64px)",flexShrink:0}}>
          <div style={{fontSize:".68rem",fontWeight:800,letterSpacing:".14em",textTransform:"uppercase",color:"#b8aad8",padding:"0 .75rem",marginBottom:"1.25rem"}}>Reports</div>
          <ul style={{listStyle:"none",margin:0,padding:0,display:"flex",flexDirection:"column",gap:4}}>
            {TABS.map(t=>(
              <li key={t.key}>
                <button className={`rptab${tab===t.key?" active":""}`} onClick={()=>setTab(t.key)}>
                  <span style={{fontSize:"1rem"}}>{t.icon}</span> {t.label}
                </button>
              </li>
            ))}
          </ul>
        </aside>

        {/* Main */}
        <main style={{flex:1,padding:"2rem 2.5rem",minWidth:0}}>
          {loading && <div style={{width:40,height:40,border:"4px solid #e4ddf5",borderTop:"4px solid #8b5cf6",borderRadius:"50%",animation:"spin .8s linear infinite",margin:"3rem auto"}}/>}

          {/* SUMMARY */}
          {!loading && tab==="summary" && (
            <>
              <div style={{display:"flex",alignItems:"center",gap:14,marginBottom:"1.75rem"}}>
                <span style={{width:44,height:44,background:"linear-gradient(135deg,#c4b5fd,#93c5fd)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(139,92,246,.25)"}}>📊</span>
                <div>
                  <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>System Summary</h2>
                  <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}>Live overview of library activity</p>
                </div>
              </div>
              <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(200px,1fr))",gap:"1rem"}}>
                {summaryCards.map(c=>(
                  <div key={c.label} style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:22,padding:"1.5rem",boxShadow:"0 4px 14px rgba(100,60,200,.07)",display:"flex",alignItems:"center",gap:14}}>
                    <span style={{width:48,height:48,background:c.bg,borderRadius:16,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.35rem",flexShrink:0}}>{c.icon}</span>
                    <div>
                      <div style={{fontSize:"1.7rem",fontWeight:800,color:c.valColor,fontFamily:"'IBM Plex Mono',monospace",lineHeight:1}}>{c.val}</div>
                      <div style={{fontSize:".72rem",fontWeight:700,color:"#b8aad8",textTransform:"uppercase",letterSpacing:".07em",marginTop:4}}>{c.label}</div>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}

          {/* ISSUED */}
          {!loading && tab==="issued" && (
            <>
              <div style={{display:"flex",alignItems:"center",gap:14,marginBottom:"1.75rem"}}>
                <span style={{width:44,height:44,background:"linear-gradient(135deg,#6ee7b7,#93c5fd)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(16,185,129,.2)"}}>📗</span>
                <div>
                  <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>Issued Books Report</h2>
                  <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}><strong style={{color:"#8b5cf6"}}>{issuedBooks.length}</strong> books currently issued</p>
                </div>
              </div>
              <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:24,overflow:"hidden",boxShadow:"0 6px 24px rgba(100,60,200,.08)"}}>
                <table style={{width:"100%",borderCollapse:"collapse"}}>
                  <thead><tr>{["#","Book","Member","Membership ID","Issue Date","Due Date"].map(h=><th key={h} style={TH}>{h}</th>)}</tr></thead>
                  <tbody>
                    {issuedBooks.length===0 ? <tr><td colSpan={6} style={{...TD,textAlign:"center",padding:"2.5rem",color:"#b8aad8"}}>No issued books.</td></tr>
                    : issuedBooks.map((r,i)=>(
                      <tr key={r._id} className="rprow">
                        <td style={{...TD,color:"#b8aad8",fontWeight:700}}>{i+1}</td>
                        <td style={TD}><strong style={{color:"#2a2050"}}>{r.bookId?.title}</strong><div style={{fontSize:".75rem",color:"#b8aad8"}}>{r.bookId?.author}</div></td>
                        <td style={TD}><strong style={{color:"#2a2050"}}>{r.userId?.name}</strong><div style={{fontSize:".75rem",color:"#b8aad8"}}>{r.userId?.email}</div></td>
                        <td style={TD}><span style={{fontFamily:"'IBM Plex Mono',monospace",fontSize:".76rem",background:"#f3f0fb",padding:"2px 7px",borderRadius:7,color:"#8b5cf6"}}>{r.userId?.membershipId||"—"}</span></td>
                        <td style={TD}>{new Date(r.issueDate).toLocaleDateString()}</td>
                        <td style={TD}>{new Date(r.dueDate).toLocaleDateString()}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {/* OVERDUE */}
          {!loading && tab==="overdue" && (
            <>
              <div style={{display:"flex",alignItems:"center",gap:14,marginBottom:"1.75rem"}}>
                <span style={{width:44,height:44,background:"linear-gradient(135deg,#fca5a5,#fde68a)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(239,68,68,.2)"}}>⚠️</span>
                <div>
                  <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>Overdue Books Report</h2>
                  <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}><strong style={{color:"#ef4444"}}>{overdueBooks.length}</strong> overdue books</p>
                </div>
              </div>
              <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:24,overflow:"hidden",boxShadow:"0 6px 24px rgba(100,60,200,.08)"}}>
                <table style={{width:"100%",borderCollapse:"collapse"}}>
                  <thead><tr>{["#","Book","Member","Due Date","Days Overdue","Fine (₹)"].map(h=><th key={h} style={TH}>{h}</th>)}</tr></thead>
                  <tbody>
                    {overdueBooks.length===0 ? <tr><td colSpan={6} style={{...TD,textAlign:"center",padding:"2.5rem",color:"#b8aad8"}}>No overdue books. 🎉</td></tr>
                    : overdueBooks.map((r,i)=>(
                      <tr key={r._id} className="rprow">
                        <td style={{...TD,color:"#b8aad8",fontWeight:700}}>{i+1}</td>
                        <td style={TD}><strong style={{color:"#2a2050"}}>{r.bookId?.title}</strong></td>
                        <td style={TD}><strong style={{color:"#2a2050"}}>{r.userId?.name}</strong><div style={{fontSize:".75rem",color:"#b8aad8"}}>{r.userId?.email}</div></td>
                        <td style={{...TD,color:"#ef4444",fontWeight:600}}>{new Date(r.dueDate).toLocaleDateString()}</td>
                        <td style={TD}><span style={{background:"#fef3c7",color:"#92400e",padding:"4px 10px",borderRadius:99,fontSize:".76rem",fontWeight:700}}>{r.daysOverdue} days</span></td>
                        <td style={TD}><span style={{fontFamily:"'IBM Plex Mono',monospace",fontWeight:800,fontSize:"1rem",color:"#ef4444"}}>₹{r.fine}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}

          {/* FINES */}
          {!loading && tab==="fines" && (
            <>
              <div style={{display:"flex",alignItems:"center",gap:14,marginBottom:"1.75rem"}}>
                <span style={{width:44,height:44,background:"linear-gradient(135deg,#fde68a,#fca5a5)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(245,158,11,.2)"}}>💰</span>
                <div>
                  <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>Fine Collection Report</h2>
                  <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}>Overview of all fine transactions</p>
                </div>
              </div>
              <div style={{display:"grid",gridTemplateColumns:"repeat(auto-fit,minmax(170px,1fr))",gap:"1rem",marginBottom:"1.5rem"}}>
                {[{l:"Total",v:`₹${fines.totalAmount}`,bg:"#fee2e2",c:"#ef4444"},{l:"Collected",v:`₹${fines.totalCollected}`,bg:"#d1fae5",c:"#10b981"},{l:"Pending",v:`₹${fines.totalPending}`,bg:"#fef3c7",c:"#f59e0b"}].map(s=>(
                  <div key={s.l} style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:18,padding:"1.25rem",boxShadow:"0 4px 14px rgba(100,60,200,.06)"}}>
                    <div style={{fontSize:".68rem",fontWeight:800,color:"#b8aad8",textTransform:"uppercase",letterSpacing:".1em",marginBottom:6}}>{s.l}</div>
                    <div style={{fontFamily:"'IBM Plex Mono',monospace",fontSize:"1.7rem",fontWeight:800,color:s.c}}>{s.v}</div>
                  </div>
                ))}
              </div>
              <div style={{display:"grid",gridTemplateColumns:"260px 1fr",gap:"1.25rem",alignItems:"start"}}>
                <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:22,padding:"1.5rem",boxShadow:"0 4px 14px rgba(100,60,200,.07)"}}>
                  <div style={{fontWeight:700,color:"#2a2050",marginBottom:"1.25rem"}}>Fine Status</div>
                  <Pie data={pieData} options={{maintainAspectRatio:true, plugins:{legend:{position:"bottom",labels:{font:{family:"'Plus Jakarta Sans',sans-serif",size:12},padding:12}}}}}/>
                </div>
                <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:24,overflow:"hidden",boxShadow:"0 6px 24px rgba(100,60,200,.08)"}}>
                  <table style={{width:"100%",borderCollapse:"collapse"}}>
                    <thead><tr>{["#","Member","Book","Amount","Status","Date"].map(h=><th key={h} style={TH}>{h}</th>)}</tr></thead>
                    <tbody>
                      {(fines.fines||[]).length===0 ? <tr><td colSpan={6} style={{...TD,textAlign:"center",padding:"2.5rem",color:"#b8aad8"}}>No fines recorded.</td></tr>
                      : fines.fines.map((f,i)=>(
                        <tr key={f._id} className="rprow">
                          <td style={{...TD,color:"#b8aad8",fontWeight:700}}>{i+1}</td>
                          <td style={TD}><strong style={{color:"#2a2050"}}>{f.memberId?.name}</strong><div style={{fontSize:".73rem",color:"#b8aad8"}}>{f.memberId?.membershipId}</div></td>
                          <td style={{...TD,maxWidth:160}}><div style={{overflow:"hidden",textOverflow:"ellipsis",whiteSpace:"nowrap",color:"#2a2050",fontWeight:600}}>{f.bookId?.title}</div></td>
                          <td style={TD}><span style={{fontFamily:"'IBM Plex Mono',monospace",fontWeight:800,color:"#ef4444"}}>₹{f.amount}</span></td>
                          <td style={TD}>{f.paidStatus?<span style={{background:"#d1fae5",color:"#065f46",padding:"3px 9px",borderRadius:99,fontSize:".74rem",fontWeight:700}}>✓ Paid</span>:<span style={{background:"#fee2e2",color:"#991b1b",padding:"3px 9px",borderRadius:99,fontSize:".74rem",fontWeight:700}}>✗ Unpaid</span>}</td>
                          <td style={TD}>{new Date(f.date).toLocaleDateString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </>
          )}

          {/* MOST BORROWED */}
          {!loading && tab==="mostBorrowed" && (
            <>
              <div style={{display:"flex",alignItems:"center",gap:14,marginBottom:"1.75rem"}}>
                <span style={{width:44,height:44,background:"linear-gradient(135deg,#fca5a5,#fde68a)",borderRadius:14,display:"flex",alignItems:"center",justifyContent:"center",fontSize:"1.2rem",boxShadow:"0 4px 14px rgba(239,68,68,.2)"}}>🔥</span>
                <div>
                  <h2 style={{fontSize:"1.65rem",fontWeight:800,color:"#2a2050",letterSpacing:"-.02em"}}>Most Borrowed Books</h2>
                  <p style={{color:"#b8aad8",fontSize:".83rem",marginTop:2}}>Top books by total borrow count</p>
                </div>
              </div>
              {barData && (
                <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:22,padding:"1.75rem",boxShadow:"0 6px 24px rgba(100,60,200,.08)",marginBottom:"1.5rem"}}>
                  <div style={{fontWeight:700,color:"#2a2050",marginBottom:"1.25rem"}}>📊 Top {mostBorrowed.length} Books</div>
                  <Bar data={barData} options={{responsive:true,plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{stepSize:1},grid:{color:"#f0ecfa"}},x:{grid:{display:false}}}}}/>
                </div>
              )}
              <div style={{background:"#fff",border:"2px solid #e4ddf5",borderRadius:24,overflow:"hidden",boxShadow:"0 6px 24px rgba(100,60,200,.08)"}}>
                <table style={{width:"100%",borderCollapse:"collapse"}}>
                  <thead><tr>{["Rank","Title","Author","Category","Borrows"].map(h=><th key={h} style={TH}>{h}</th>)}</tr></thead>
                  <tbody>
                    {mostBorrowed.length===0 ? <tr><td colSpan={5} style={{...TD,textAlign:"center",padding:"2.5rem",color:"#b8aad8"}}>No data yet.</td></tr>
                    : mostBorrowed.map((b,i)=>(
                      <tr key={i} className="rprow">
                        <td style={TD}>
                          <span style={{width:30,height:30,borderRadius:10,display:"inline-flex",alignItems:"center",justifyContent:"center",fontWeight:800,fontSize:".85rem",
                            background:i===0?"#fde68a":i===1?"#e4ddf5":i===2?"#fca5a5":"#f3f0fb",
                            color:i===0?"#92400e":i===1?"#6b5e95":i===2?"#991b1b":"#b8aad8"}}>
                            {i+1}
                          </span>
                        </td>
                        <td style={TD}><strong style={{color:"#2a2050"}}>{b.book?.title}</strong></td>
                        <td style={TD}>{b.book?.author}</td>
                        <td style={TD}><span style={{background:"#f3f0fb",color:"#8b5cf6",padding:"3px 10px",borderRadius:99,fontSize:".76rem",fontWeight:600}}>{b.book?.category}</span></td>
                        <td style={TD}><span style={{background:"linear-gradient(135deg,#c4b5fd,#93c5fd)",color:"#2a2050",padding:"4px 12px",borderRadius:99,fontSize:".82rem",fontWeight:800}}>{b.borrowCount}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </main>
      </div>
    </>
  );
}
