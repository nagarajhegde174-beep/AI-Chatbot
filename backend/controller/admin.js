const { UserModel }       = require("../model/UserModel");
const { FineConfigModel } = require("../model/FineConfigModel");
const { BorrowModel }     = require("../model/BorrowModel");
const bcrypt = require("bcryptjs");
const jwt    = require("jsonwebtoken");

const adminController = {};

/* ── LOGIN ──────────────────────────────────────────────────────── */
adminController.login = async (req, res) => {
  try {
    const email    = String(req.body?.email    || "").trim().toLowerCase();
    const password = String(req.body?.password || "").trim();
    if (!email || !password)
      return res.status(400).json({ message: "Email and password are required" });

    const envEmail = (process.env.ADMIN_EMAIL || "").trim().toLowerCase();
    const envPass  = (process.env.ADMIN_PASSWORD || "").trim();
    if (envEmail && envPass && email === envEmail && password === envPass) {
      const token = jwt.sign({ email, role: "admin" }, process.env.JWT_SECRET, { expiresIn: "24h" });
      return res.json({ message: "Login successful", token, user: { name: "Env Admin", email, role: "admin" } });
    }

    const user = await UserModel.findOne({ email: { $regex: `^${email}$`, $options: "i" } });
    if (!user) return res.status(400).json({ message: "Invalid email or password" });
    if (!["admin","librarian"].includes(user.role))
      return res.status(403).json({ message: "Access denied." });
    if (user.status === "Inactive")
      return res.status(403).json({ message: "Account deactivated. Contact admin." });

    const isMatch = await bcrypt.compare(password, user.password || "");
    if (!isMatch) return res.status(400).json({ message: "Invalid email or password" });

    const token = jwt.sign(
      { id: user._id, email: user.email, name: user.name, role: user.role },
      process.env.JWT_SECRET, { expiresIn: "24h" }
    );
    return res.json({ message: "Login successful", token, user: { name: user.name, email: user.email, role: user.role } });
  } catch (error) {
    console.error("ADMIN LOGIN ERROR:", error);
    return res.status(500).json({ message: "Server error" });
  }
};

/* ── ADD LIBRARIAN ──────────────────────────────────────────────── */
adminController.addLibrarian = async (req, res) => {
  try {
    const { name, email, password, role } = req.body;
    const normEmail = String(email || "").trim().toLowerCase();
    const existing = await UserModel.findOne({
      email: { $regex: `^${normEmail}$`, $options: "i" },
    });
    if (existing) return res.status(400).json({ message: "Email already exists" });
    const hashedPassword = await bcrypt.hash(String(password || ""), 10);
    const user = new UserModel({ name, email: normEmail, password: hashedPassword, role });
    await user.save();
    res.status(201).json({ message: "Librarian added successfully" });
  } catch (error) {
    res.status(500).json({ message: "Internal Server Error" });
  }
};

/* ── GET ALL LIBRARIANS ─────────────────────────────────────────── */
adminController.getLibrarians = async (req, res) => {
  try {
    const librarians = await UserModel.find({ role: "librarian" }, "-password").sort({ createdAt: -1 });
    res.status(200).json({ error: false, librarians, total: librarians.length });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

/* ── DELETE LIBRARIAN ───────────────────────────────────────────── */
adminController.deleteLibrarian = async (req, res) => {
  try {
    const user = await UserModel.findById(req.params.id);
    if (!user || user.role !== "librarian")
      return res.status(404).json({ message: "Librarian not found" });
    await UserModel.findByIdAndDelete(req.params.id);
    res.status(200).json({ message: "Librarian deleted successfully" });
  } catch (err) {
    res.status(500).json({ message: "Internal Server Error" });
  }
};

/* ── GET ALL MEMBERS ────────────────────────────────────────────── */
adminController.getMembers = async (req, res) => {
  try {
    const members = await UserModel.find({ role: "user" }, "-password").sort({ createdAt: -1 });
    res.status(200).json({ error: false, members, total: members.length });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

/* ── ACTIVATE / DEACTIVATE USER ─────────────────────────────────── */
adminController.toggleUserStatus = async (req, res) => {
  try {
    const user = await UserModel.findById(req.params.id);
    if (!user) return res.status(404).json({ message: "User not found" });
    if (user.role === "admin") return res.status(403).json({ message: "Cannot change admin status" });
    user.status = user.status === "Active" ? "Inactive" : "Active";
    await user.save();
    res.status(200).json({ message: `User ${user.status === "Active" ? "activated" : "deactivated"} successfully`, status: user.status });
  } catch (err) {
    res.status(500).json({ message: "Internal Server Error" });
  }
};

/* ── MEMBER BORROW HISTORY ──────────────────────────────────────── */
adminController.getMemberBorrowHistory = async (req, res) => {
  try {
    const { id } = req.params;
    const history = await BorrowModel.find({ userId: id })
      .populate("bookId", "title author isbn coverImage")
      .sort({ issueDate: -1 });
    const member = await UserModel.findById(id, "-password");
    if (!member) return res.status(404).json({ message: "Member not found" });
    res.status(200).json({ error: false, member, history });
  } catch (err) {
    res.status(500).json({ message: "Internal Server Error" });
  }
};

/* ── FINE CONFIG ────────────────────────────────────────────────── */
adminController.getFineConfig = async (req, res) => {
  try {
    let config = await FineConfigModel.findOne();
    if (!config) config = await FineConfigModel.create({});
    res.status(200).json({ error: false, config });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

adminController.updateFineConfig = async (req, res) => {
  try {
    const { ratePerDay, maxFineCap, gracePeriod } = req.body;
    let config = await FineConfigModel.findOne();
    if (!config) config = new FineConfigModel();
    if (ratePerDay  !== undefined) config.ratePerDay  = ratePerDay;
    if (maxFineCap  !== undefined) config.maxFineCap  = maxFineCap;
    if (gracePeriod !== undefined) config.gracePeriod = gracePeriod;
    config.updatedBy = req.userInfo.id;
    await config.save();
    res.status(200).json({ error: false, message: "Fine configuration updated.", config });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

module.exports = { adminController };
