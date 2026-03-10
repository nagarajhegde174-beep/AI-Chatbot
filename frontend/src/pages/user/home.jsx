import "../../animations.css";
import { useEffect, useState } from "react";
import api from "../../lib/api";

// --- simple helpers ---
const num = (v) => (Number.isFinite(+v) ? +v : 0);
const byCategories = (books = []) => {
  const map = new Map();
  for (const b of books) {
    const key = b?.category || "Uncategorized";
    const entry = map.get(key) || {
      name: key,
      booksCount: 0,
      // pick first non-empty cover as thumbnail
      image: b?.coverImage || "",
    };
    entry.booksCount += 1;
    if (!entry.image && b?.coverImage) entry.image = b.coverImage;
    map.set(key, entry);
  }
  return Array.from(map.values());
};

export default function Home() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [err, setErr] = useState("");

  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const res = await api.get("/home");
        const d = res?.data?.data ?? res?.data ?? {};
        if (alive) setData(d);
      } catch (e) {
        if (alive) setErr(e?.response?.data?.message || "Failed to load data");
      } finally {
        if (alive) setLoading(false);
      }
    })();
    return () => (alive = false);
  }, []);

  if (loading) return <div style={{ padding: 16 }}>Loading…</div>;
  if (!data) return <div style={{ padding: 16, color: "#b91c1c" }}>{err}</div>;

  const totalCategories =
    data.totalCategories ?? data.categoriesCount ?? 0;
  const totalBooks = data.totalBooks ?? data.booksCount ?? 0;
  const totalActiveStudents =
    data.totalActiveStudents ?? data.borrowersCount ?? 0;
  const issuedCount = data.issuedCount ?? data.totalIssued ?? 0;
  const books = Array.isArray(data.books) ? data.books : [];

  const categories = byCategories(books); // derive cards like Vercel
  const latest = books.slice(0, 8); // “New Arrivals”

  return (
    <div style={{ fontFamily: "system-ui, Segoe UI, Roboto, sans-serif" }}>
      {/* HERO */}
      <section
        style={{
          position: "relative",
          height: 380,
          background:
            "url(/library-bg.jpg) center/cover, linear-gradient(#111,#111)",
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            background: "rgba(0,0,0,.45)",
            display: "grid",
            placeItems: "center",
            textAlign: "center",
            color: "#fff",
            padding: 24,
          }}
        >
          <div>
            <h1 style={{ fontSize: 40, fontWeight: 800, margin: 6 }}>
              Welcome to College Central Library
            </h1>
            <p style={{ opacity: 0.9, marginBottom: 16 }}>
              Access academic resources, textbooks, and research materials
            </p>
            <a
              href="/books"
              style={{
                background: "#0d6efd",
                color: "#fff",
                padding: "10px 16px",
                borderRadius: 8,
                textDecoration: "none",
                display: "inline-flex",
                alignItems: "center",
                gap: 8,
                fontWeight: 600,
              }}
            >
              <span role="img" aria-label="book">📘</span> Browse collections
            </a>
          </div>
        </div>
      </section>

      {/* STATS */}
      <section
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 16,
          padding: 24,
          maxWidth: 1200,
          margin: "0 auto",
        }}
      >
        <StatCard title="Total Categories" value={`${num(totalCategories)}+`} icon="📚" />
        <StatCard title="Total Books" value={`${num(totalBooks)}+`} icon="📖" />
        <StatCard title="Active Students" value={num(totalActiveStudents)} icon="🧑‍🎓" />
        <StatCard title="Issued Books" value={num(issuedCount)} icon="📗" />
      </section>

      {/* BROWSE BY CATEGORIES */}
      <SectionTitle
        title="Browse By Categories"
        subtitle="Find resources for your courses"
      />
      <section
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))",
          gap: 18,
          padding: 24,
          maxWidth: 1200,
          margin: "0 auto",
        }}
      >
        {categories.map((c) => (
          <div
            key={c.name}
            style={{
              background: "#fff",
              border: "1px solid #e5e7eb",
              borderRadius: 12,
              overflow: "hidden",
              boxShadow: "0 2px 8px rgba(0,0,0,.06)",
            }}
          >
            <img
              src={c.image || "/placeholder.jpg"}
              alt={c.name}
              style={{ width: "100%", height: 160, objectFit: "cover" }}
              onError={(e) => (e.currentTarget.src = "/placeholder.jpg")}
            />
            <div style={{ padding: 14 }}>
              <div style={{ fontSize: 18, fontWeight: 700 }}>{c.name}</div>
              <div style={{ color: "#6b7280", marginTop: 4 }}>
                Books: {c.booksCount}
              </div>
              <a
                href={`/category/${encodeURIComponent(c.name)}`}
                style={{
                  marginTop: 10,
                  display: "inline-block",
                  padding: "8px 12px",
                  background: "#0d6efd",
                  color: "#fff",
                  borderRadius: 8,
                  textDecoration: "none",
                  fontWeight: 600,
                }}
              >
                Browse
              </a>
            </div>
          </div>
        ))}
        {categories.length === 0 && (
          <div style={{ opacity: 0.7 }}>No categories yet.</div>
        )}
      </section>

      {/* NEW ARRIVALS */}
      <SectionTitle
        title="New Arrivals"
        subtitle="Recently added to our collection"
      />
      <section
        style={{
          background: "#f7f9fc",
          padding: "28px 24px 40px",
          marginBottom: 24,
        }}
      >
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
            gap: 18,
            maxWidth: 1200,
            margin: "0 auto",
          }}
        >
          {latest.map((b) => (
            <div
              key={b._id}
              style={{
                background: "#fff",
                border: "1px solid #e5e7eb",
                borderRadius: 12,
                overflow: "hidden",
                boxShadow: "0 2px 8px rgba(0,0,0,.06)",
              }}
            >
              <img
                src={b.coverImage || "/placeholder.jpg"}
                alt={b.title}
                style={{ width: "100%", height: 260, objectFit: "cover" }}
                onError={(e) => (e.currentTarget.src = "/placeholder.jpg")}
              />
              <div style={{ padding: 14 }}>
                <div style={{ fontWeight: 700 }}>{b.title}</div>
                <div style={{ color: "#6b7280", fontSize: 14 }}>{b.author}</div>
                <span
                  style={{
                    display: "inline-block",
                    marginTop: 8,
                    background: "#0d6efd",
                    color: "#fff",
                    padding: "4px 10px",
                    borderRadius: 999,
                    fontSize: 12,
                    fontWeight: 600,
                  }}
                >
                  {b.category || "General"}
                </span>
              </div>
            </div>
          ))}
          {latest.length === 0 && (
            <div style={{ opacity: 0.7, padding: 8 }}>No new arrivals yet.</div>
          )}
        </div>
      </section>

      {/* LIBRARY HOURS */}
      <SectionTitle title="Library Hours" />
      <section
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))",
          gap: 18,
          padding: "0 24px 28px",
          maxWidth: 900,
          margin: "0 auto",
        }}
      >
        <HoursCard
          title="Regular Hours"
          lines={[
            "Monday - Friday: 8:00 AM - 8:00 PM",
            "Saturday: 10:00 AM - 5:00 PM",
            "Sunday: Closed",
          ]}
        />
        <HoursCard
          title="Exam Period"
          lines={["Monday - Sunday: 7:00 AM - 11:00 PM"]}
        />
      </section>
    </div>
  );
}

// --- presentational bits ---
function StatCard({ title, value, icon }) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e5e7eb",
        borderRadius: 12,
        padding: 18,
        textAlign: "center",
        boxShadow: "0 2px 8px rgba(0,0,0,.06)",
      }}
    >
      <div style={{ fontSize: 28, marginBottom: 6 }}>{icon}</div>
      <div style={{ fontSize: 28, fontWeight: 800 }}>{value}</div>
      <div style={{ color: "#6b7280", marginTop: 4 }}>{title}</div>
    </div>
  );
}

function SectionTitle({ title, subtitle }) {
  return (
    <div style={{ textAlign: "center", padding: "16px 24px 0" }}>
      <h2 style={{ fontSize: 30, fontWeight: 800, color: "#1f2937" }}>
        {title}
      </h2>
      {subtitle && (
        <p style={{ color: "#6b7280", marginTop: 6 }}>{subtitle}</p>
      )}
    </div>
  );
}

function HoursCard({ title, lines }) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e5e7eb",
        borderRadius: 12,
        padding: 20,
        textAlign: "center",
        boxShadow: "0 2px 8px rgba(0,0,0,.06)",
      }}
    >
      <div style={{ fontSize: 24, marginBottom: 8 }}>🕘</div>
      <div style={{ fontWeight: 700, fontSize: 18 }}>{title}</div>
      <div style={{ marginTop: 6, color: "#4b5563", lineHeight: 1.6 }}>
        {lines.map((t, i) => (
          <div key={i}>{t}</div>
        ))}
      </div>
    </div>
  );
}
