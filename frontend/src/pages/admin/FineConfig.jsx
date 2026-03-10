import "../../animations.css";
import { useEffect, useState } from "react";
import axios from "axios";
import { Server_URL } from "../../utils/config";
import { getAuthToken } from "../../utils/auth";
import { showSuccessToast, showErrorToast } from "../../utils/toasthelper";

function FineConfig() {
  const [config, setConfig]   = useState({ ratePerDay: 5, maxFineCap: 500, gracePeriod: 0 });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving]   = useState(false);

  const headers = { Authorization: `Bearer ${getAuthToken()}` };

  const fetchConfig = async () => {
    try {
      const res = await axios.get(`${Server_URL}admin/fine-config`, { headers });
      setConfig(res.data.config);
    } catch { showErrorToast("Failed to load fine config"); }
    finally { setLoading(false); }
  };

  const saveConfig = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      const res = await axios.put(`${Server_URL}admin/fine-config`, config, { headers });
      showSuccessToast(res.data.message);
      setConfig(res.data.config);
    } catch (err) {
      showErrorToast(err.response?.data?.message || "Failed to save");
    } finally { setSaving(false); }
  };

  useEffect(() => { fetchConfig(); }, []);

  return (
    <div className="admin-dashboard">
      <div className="row g-0">
        <main className="col-12 admin-main">
          <h2 className="admin-section-title">⚙️ Fine Configuration</h2>
          <p className="text-muted mb-4">Configure the overdue fine rules applied to all members.</p>

          {loading ? (
            <div className="text-center py-5"><div className="spinner-border text-primary" /></div>
          ) : (
            <div className="row">
              <div className="col-md-6">
                <div className="card border-0 shadow-sm p-4" style={{ borderRadius: 12 }}>
                  <form onSubmit={saveConfig}>
                    <div className="mb-4">
                      <label className="form-label fw-bold">💵 Fine Rate (₹ per day)</label>
                      <input
                        type="number" min="0" step="0.5"
                        className="form-control"
                        value={config.ratePerDay}
                        onChange={e => setConfig({ ...config, ratePerDay: Number(e.target.value) })}
                        required
                      />
                      <small className="text-muted">Amount charged per overdue day</small>
                    </div>

                    <div className="mb-4">
                      <label className="form-label fw-bold">🔒 Maximum Fine Cap (₹)</label>
                      <input
                        type="number" min="0"
                        className="form-control"
                        value={config.maxFineCap}
                        onChange={e => setConfig({ ...config, maxFineCap: Number(e.target.value) })}
                        required
                      />
                      <small className="text-muted">Maximum fine per book (0 = no cap)</small>
                    </div>

                    <div className="mb-4">
                      <label className="form-label fw-bold">🕐 Grace Period (days)</label>
                      <input
                        type="number" min="0"
                        className="form-control"
                        value={config.gracePeriod}
                        onChange={e => setConfig({ ...config, gracePeriod: Number(e.target.value) })}
                        required
                      />
                      <small className="text-muted">Free days allowed after due date before fine starts</small>
                    </div>

                    <button className="btn btn-primary w-100" disabled={saving}>
                      {saving ? "Saving..." : "💾 Save Configuration"}
                    </button>
                  </form>
                </div>
              </div>

              {/* Preview card */}
              <div className="col-md-6">
                <div className="card border-0 shadow-sm p-4" style={{ borderRadius: 12, background: "#f0f7ff" }}>
                  <h5 className="fw-bold mb-3">📋 Current Rule Preview</h5>
                  <div className="mb-2">
                    <span className="badge bg-primary me-2">Rate</span>
                    ₹{config.ratePerDay} per overdue day
                  </div>
                  <div className="mb-2">
                    <span className="badge bg-danger me-2">Max Cap</span>
                    ₹{config.maxFineCap} per book
                  </div>
                  <div className="mb-4">
                    <span className="badge bg-success me-2">Grace</span>
                    {config.gracePeriod} day(s) free after due date
                  </div>
                  <hr/>
                  <h6 className="fw-bold">Example:</h6>
                  <p className="text-muted" style={{ fontSize: "0.9rem" }}>
                    If a book is <strong>10 days overdue</strong> (after grace period):
                    <br/>
                    Fine = {config.ratePerDay} × {Math.max(0, 10 - config.gracePeriod)} = <strong>₹{Math.min(config.ratePerDay * Math.max(0, 10 - config.gracePeriod), config.maxFineCap)}</strong>
                  </p>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

export default FineConfig;
