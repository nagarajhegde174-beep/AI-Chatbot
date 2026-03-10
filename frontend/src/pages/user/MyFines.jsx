import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showErrorToast } from "../../utils/toasthelper";

function MyFines() {
  const [fines, setFines]     = useState([]);
  const [totalDue, setTotalDue] = useState(0);
  const [loading, setLoading] = useState(true);

  const fetchFines = async () => {
    try {
      const res = await axios.get(`${Server_URL}fines/my`, {
        headers: { Authorization: `Bearer ${getAuthToken()}` },
      });
      setFines(res.data.fines || []);
      setTotalDue(res.data.totalDue || 0);
    } catch (err) {
      showErrorToast("Failed to fetch fines");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchFines(); }, []);

  return (
    <div style={{ minHeight: "100vh", background: "#f8fafc", padding: "2rem" }}>
      <div className="container">
        <h2 style={{ color: "#2c3e50", fontWeight: 700, marginBottom: "1.5rem" }}>💰 My Fines</h2>

        {/* Summary card */}
        <div className="row mb-4">
          <div className="col-md-4">
            <div className="card border-0 shadow-sm text-center p-4" style={{ borderLeft: "4px solid #e74c3c", borderRadius: 12 }}>
              <h6 className="text-muted">Total Due</h6>
              <h2 style={{ color: "#e74c3c", fontWeight: 700 }}>₹{totalDue}</h2>
            </div>
          </div>
          <div className="col-md-4">
            <div className="card border-0 shadow-sm text-center p-4" style={{ borderLeft: "4px solid #f39c12", borderRadius: 12 }}>
              <h6 className="text-muted">Unpaid Fines</h6>
              <h2 style={{ color: "#f39c12", fontWeight: 700 }}>{fines.filter(f => !f.paidStatus).length}</h2>
            </div>
          </div>
          <div className="col-md-4">
            <div className="card border-0 shadow-sm text-center p-4" style={{ borderLeft: "4px solid #2ecc71", borderRadius: 12 }}>
              <h6 className="text-muted">Paid Fines</h6>
              <h2 style={{ color: "#2ecc71", fontWeight: 700 }}>{fines.filter(f => f.paidStatus).length}</h2>
            </div>
          </div>
        </div>

        {loading ? (
          <div className="text-center py-5"><div className="spinner-border text-primary" /></div>
        ) : fines.length === 0 ? (
          <div className="card text-center p-5 shadow-sm border-0">
            <div style={{ fontSize: "3rem" }}>✅</div>
            <h5 className="mt-3 text-muted">No fines! You're all clear.</h5>
          </div>
        ) : (
          <div className="card shadow-sm border-0" style={{ borderRadius: 12 }}>
            <div className="table-responsive">
              <table className="table table-hover mb-0">
                <thead style={{ background: "linear-gradient(90deg,#e74c3c,#c0392b)", color: "white" }}>
                  <tr>
                    <th className="p-3">#</th>
                    <th className="p-3">Book</th>
                    <th className="p-3">Due Date</th>
                    <th className="p-3">Return Date</th>
                    <th className="p-3">Days Overdue</th>
                    <th className="p-3">Fine Amount</th>
                    <th className="p-3">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {fines.map((f, i) => (
                    <tr key={f._id}>
                      <td className="p-3">{i + 1}</td>
                      <td className="p-3"><strong>{f.bookId?.title}</strong></td>
                      <td className="p-3">{f.borrowId?.dueDate ? new Date(f.borrowId.dueDate).toLocaleDateString() : "—"}</td>
                      <td className="p-3">{f.borrowId?.returnDate ? new Date(f.borrowId.returnDate).toLocaleDateString() : "—"}</td>
                      <td className="p-3">{f.daysOverdue}</td>
                      <td className="p-3"><strong style={{ color: "#e74c3c" }}>₹{f.amount}</strong></td>
                      <td className="p-3">
                        {f.paidStatus
                          ? <span className="badge bg-success">Paid</span>
                          : <span className="badge bg-danger">Unpaid</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        <p className="text-muted mt-3" style={{ fontSize: "0.85rem" }}>
          ℹ️ Fines are calculated at ₹5/day after due date. Please contact the librarian to pay your fines.
        </p>
      </div>
    </div>
  );
}

export default MyFines;
