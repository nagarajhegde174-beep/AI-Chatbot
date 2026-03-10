import { useEffect, lazy, Suspense } from 'react';
import { BrowserRouter as Router, Routes, Route, useNavigate, useLocation } from "react-router-dom";
import { jwtDecode } from "jwt-decode";
import './App.css';
import UserLayout from "./layout/userlayout";
import AdminLayout from "./layout/adminlayout";

// Existing pages
const Login          = lazy(() => import("./pages/user/login"));
const Register       = lazy(() => import('./pages/user/register'));
const Home           = lazy(() => import("./pages/user/home"));
const Books          = lazy(() => import('./pages/user/books'));
const AllCategories  = lazy(() => import('./pages/user/allcategories'));
const AdminDashboard = lazy(() => import('./pages/admin/admindashboard'));
const AdminLogin     = lazy(() => import('./pages/admin/AdminLogin'));
const AddBookForm    = lazy(() => import('./pages/admin/addbook'));
const ViewBooks      = lazy(() => import('./pages/admin/viewbook'));
const AddLibrarian   = lazy(() => import('./pages/admin/AddLibrarian'));
const BookDetails    = lazy(() => import('./pages/user/bookdetails'));
const ProfilePage    = lazy(() => import('./pages/user/profile'));
const LibrarianRequests = lazy(() => import('./pages/librarian/LibrarianRequest'));
const ReturnRequest  = lazy(() => import('./pages/librarian/ReturnRequest'));
const AboutUs        = lazy(() => import('./pages/user/AboutUs'));
const ContactUs      = lazy(() => import('./pages/user/ContactUs'));
const BooksBorrowed  = lazy(() => import('./pages/librarian/BooksBorrowed'));
const ForgotPassword = lazy(() => import('./pages/user/ForgetPassword/ForgetPassword'));
const VerifyOTP      = lazy(() => import('./pages/user/ForgetPassword/VerifyOtp'));
const ResetPassword  = lazy(() => import('./pages/user/ForgetPassword/UpdatePassword'));

// ✅ NEW feature pages
const Reservations    = lazy(() => import('./pages/user/Reservations'));
const MyFines         = lazy(() => import('./pages/user/MyFines'));
const Reports         = lazy(() => import('./pages/admin/Reports'));
const ManageMembers   = lazy(() => import('./pages/admin/ManageMembers'));
const FineManagement  = lazy(() => import('./pages/admin/FineManagement'));
const FineConfig      = lazy(() => import('./pages/admin/FineConfig'));
const AllReservations = lazy(() => import('./pages/admin/AllReservations'));

// ✅ NEW separate login pages
const LoginPortal    = lazy(() => import('./pages/auth/LoginPortal'));
const LibrarianLogin = lazy(() => import('./pages/auth/LibrarianLogin'));
const StudentLogin   = lazy(() => import('./pages/auth/StudentLogin'));

const Preloader = () => (
  <div style={{ minHeight:"100vh", background:"#f3f0fb", display:"flex", flexDirection:"column", alignItems:"center", justifyContent:"center", fontFamily:"'Plus Jakarta Sans',sans-serif" }}>
    <div style={{ width:44, height:44, border:"3px solid #e4ddf5", borderTop:"3px solid #8b5cf6", borderRadius:"50%", animation:"spin 0.8s linear infinite", marginBottom:12 }} />
    <p style={{ color:"#6b5e95", fontSize:"0.9rem" }}>Loading&hellip;</p>
    <style>{`@keyframes spin{to{transform:rotate(360deg)}}`}</style>
  </div>
);

function App() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const token = localStorage.getItem("authToken");
    if (token && location.pathname === "/") {
      try {
        const decoded = jwtDecode(token);
        if (decoded.role === "admin" || decoded.role === "librarian") {
          navigate("/admin");
        }
      } catch (err) {
        localStorage.removeItem("authToken");
      }
    }
  }, [location.pathname]);

  return (
    <Suspense fallback={<Preloader />}>
      <Routes>
        {/* ── Separate Login Pages ───────────────────────── */}
        <Route path="/login-portal"    element={<LoginPortal />} />
        <Route path="/admin-login"     element={<AdminLogin />} />
        <Route path="/librarian-login" element={<LibrarianLogin />} />

        {/* ── User / Public routes ──────────────────────── */}
        <Route path="/" element={<UserLayout />}>
          <Route index                  element={<Home />} />
          <Route path="books"           element={<Books />} />
          <Route path="bookdetails/:id" element={<BookDetails />} />
          <Route path="category"        element={<AllCategories />} />
          <Route path="register"        element={<Register />} />
          <Route path="login"           element={<Login />} />
          <Route path="student-login"   element={<StudentLogin />} />
          <Route path="aboutus"         element={<AboutUs />} />
          <Route path="contactus"       element={<ContactUs />} />
          <Route path="forgetPassword"  element={<ForgotPassword />} />
          <Route path="verifyotp"       element={<VerifyOTP />} />
          <Route path="resetpass"       element={<ResetPassword />} />
          {/* ✅ NEW user feature routes */}
          <Route path="reservations"    element={<Reservations />} />
          <Route path="my-fines"        element={<MyFines />} />
        </Route>

        {/* ── Admin / Librarian routes ───────────────────── */}
        <Route path="/admin" element={<AdminLayout />}>
          <Route index                  element={<AdminDashboard />} />
          <Route path="addbook"         element={<AddBookForm />} />
          <Route path="viewbook"        element={<ViewBooks />} />
          <Route path="addlibrarian"    element={<AddLibrarian />} />
          <Route path="issuerequest"    element={<LibrarianRequests />} />
          <Route path="returnrequest"   element={<ReturnRequest />} />
          <Route path="issued"          element={<BooksBorrowed />} />
          {/* ✅ NEW admin feature routes */}
          <Route path="members"         element={<ManageMembers />} />
          <Route path="reservations"    element={<AllReservations />} />
          <Route path="fines"           element={<FineManagement />} />
          <Route path="fine-config"     element={<FineConfig />} />
          <Route path="reports"         element={<Reports />} />
        </Route>

        {/* ── User profile ──────────────────────────────── */}
        <Route path="/user" element={<UserLayout />}>
          <Route index element={<ProfilePage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

export default App;
