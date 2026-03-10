import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showSuccessToast, showErrorToast } from "../../utils/toasthelper";

function Reservations() {
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchReservations = async () => {
    try {
      const res = await axios.get(`${Server_URL}reservations/my`, {
        headers: { Authorization: `Bearer ${getAuthToken()}` },
      });
      setReservations(res.data.reservations || []);
    } catch (err) {
      showErrorToast("Failed to fetch reservations");
    } finally {
      setLoading(false);
    }
  };

  const cancelReservation = async (id) => {
    if (!confirm("Cancel this reservation?")) return;
    try {
      const res = await axios.delete(`${Server_URL}reservations/cancel/${id}`, {
        headers: { Authorization: `Bearer ${getAuthToken()}` },
      });
      showSuccessToast(res.data.message);
      fetchReservations();
    } catch (err) {
      showErrorToast(err.response?.data?.message || "Failed to cancel");
    }
  };

  useEffect(() => { fetchReservations(); }, []);

  const statusBadge = (status) => {
    const map = {
      Pending:   "warning",
      Notified:  "info",
      Fulfilled: "success",
      Cancelled: "secondary",
      Expired:   "danger",
    };
    return <span className={`badge bg-${map[status] || "secondary"}`}>{status}</span>;
  };

  return (
    <div style={{ minHeight: "100vh", background: "#f8fafc", padding: "2rem" }}>
      <div className="container">
        <h2 style={{ color: "#2c3e50", fontWeight: 700, marginBottom: "1.5rem" }}>
          📋 My Reservations
        </h2>

        {loading ? (
          <div className="text-center py-5"><div className="spinner-border text-primary" /></div>
        ) : reservations.length === 0 ? (
          <div className="card text-center p-5 shadow-sm border-0">
            <div style={{ fontSize: "3rem" }}>📚</div>
            <h5 className="mt-3 text-muted">No reservations found</h5>
            <p className="text-muted">When a book is unavailable, you can reserve it from the book details page.</p>
          </div>
        ) : (
          <div className="card shadow-sm border-0" style={{ borderRadius: 12 }}>
            <div className="table-responsive">
              <table className="table table-hover mb-0">
                <thead style={{ background: "linear-gradient(90deg,#3498db,#2980b9)", color: "white" }}>
                  <tr>
                    <th className="p-3">#</th>
                    <th className="p-3">Book</th>
                    <th className="p-3">Author</th>
                    <th className="p-3">Category</th>
                    <th className="p-3">Reserved On</th>
                    <th className="p-3">Status</th>
                    <th className="p-3">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {reservations.map((r, i) => (
                    <tr key={r._id}>
                      <td className="p-3">{i + 1}</td>
                      <td className="p-3"><strong>{r.bookId?.title}</strong></td>
                      <td className="p-3">{r.bookId?.author}</td>
                      <td className="p-3">{r.bookId?.category}</td>
                      <td className="p-3">{new Date(r.reservationDate).toLocaleDateString()}</td>
                      <td className="p-3">{statusBadge(r.status)}</td>
                      <td className="p-3">
                        {["Pending", "Notified"].includes(r.status) && (
                          <button
                            className="btn btn-sm btn-outline-danger"
                            onClick={() => cancelReservation(r._id)}
                          >
                            Cancel
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default Reservations;
