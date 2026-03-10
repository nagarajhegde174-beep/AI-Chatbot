// backend/controller/user.js
const { UserModel } = require("../model/UserModel");
const { ContactModel } = require("../model/ContactModel");
const { OtpModel } = require("../model/OtpModel");
const nodemailer = require("nodemailer");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");

const userController = {};

/* -------------------- Registration -------------------- */
userController.userRegistration = async (req, res) => {
  try {
    const name = String(req.body?.name || "").trim();
    const email = String(req.body?.email || "").trim().toLowerCase();
    const password = String(req.body?.password || "").trim();
    const stream = req.body?.stream;
    const year = req.body?.year;
    const role = req.body?.role || "user";

    if (!name || !email || !password) {
      return res.status(400).json({ message: "Name, email and password are required" });
    }

    const existingUser = await UserModel.findOne({
      email: { $regex: `^${email}$`, $options: "i" },
    });
    if (existingUser) {
      return res.status(400).json({ message: "Email already exists" });
    }

    const hashedPassword = await bcrypt.hash(password, 10);

    const user = new UserModel({
      name,
      email,
      password: hashedPassword,
      stream,
      year,
      role,
    });

    await user.save();
    return res.status(201).json({ message: "User registered successfully" });
  } catch (error) {
    console.error("Registration error:", error);
    return res.status(500).json({ message: "Internal Server Error" });
  }
};

/* -------------------- Login -------------------- */
userController.login = async (req, res) => {
  try {
    const email = String(req.body?.email || "").trim().toLowerCase();
    const password = String(req.body?.password || "").trim();

    if (!email || !password) {
      return res.status(400).json({ message: "Email and password are required" });
    }

    // Case-insensitive exact match
    const user = await UserModel.findOne({
      email: { $regex: `^${email}$`, $options: "i" },
    });

    // TEMP DEBUG (remove later)
    console.log("LOGIN email:", email);
    console.log("LOGIN user doc found:", !!user, user?.email, user?.role);

    if (!user) {
      return res.status(400).json({ message: "Invalid email or password" });
    }

    const isMatch = await bcrypt.compare(password, user.password || "");
    console.log("LOGIN bcrypt match:", isMatch);

    if (!isMatch) {
      return res.status(400).json({ message: "Invalid email or password" });
    }

    // ✅ Block inactive users
    if (user.status === "Inactive") {
      return res.status(403).json({ message: "Your account has been deactivated. Please contact the administrator." });
    }

    const token = jwt.sign(
      { id: user._id, role: user.role, email: user.email },
      process.env.JWT_SECRET || "dev_secret", // prefer env, fallback for local
      { expiresIn: "24h" }
    );

    return res.json({
      message: "Login successful",
      token,
      user: { name: user.name, email: user.email, role: user.role },
    });
  } catch (error) {
    console.error("USER LOGIN ERROR:", error);
    return res.status(500).json({ message: "Server error" });
  }
};

/* -------------------- Get Users -------------------- */
userController.getUsers = async (req, res) => {
  try {
    const user = await UserModel.find({}, "-password");
    const totalUser = user.length;
    return res
      .status(200)
      .json({ error: false, message: "users fetched successfully", user, totalUser });
  } catch (error) {
    return res
      .status(500)
      .json({ error: true, message: "internal server error", error: error.message });
  }
};

/* -------------------- Profile -------------------- */
userController.profile = async (req, res) => {
  try {
    const { id } = req.userInfo;
    const user = await UserModel.findById(id).select("-password");
    if (!user) {
      return res.status(404).json({ error: true, message: "no such user" });
    }
    return res.json({ error: false, message: "user fetched successfully", user });
  } catch (error) {
    console.error("Profile Fetch Error:", error);
    return res.status(500).json({ error: true, message: "Internal Server error" });
  }
};

/* -------------------- Contact -------------------- */
userController.addContact = async (req, res) => {
  const { name, email, subject, message } = req.body;
  if (!name || !email || !subject || !message) {
    return res.status(400).json({ error: "All fields are required" });
  }
  try {
    const newContact = new ContactModel({ name, email, subject, message });
    await newContact.save();
    return res
      .status(200)
      .json({ success: true, message: "Your message has been sent! We will get back to you soon." });
  } catch (error) {
    console.error("Error saving contact:", error.message);
    return res.status(500).json({ error: "Server error while saving message" });
  }
};

/* -------------------- Mailer -------------------- */
const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: process.env.EMAIL_USER,     // keep
    pass: process.env.EMAIL_PASS,     // keep
  },
});

/* -------------------- Forgot / OTP -------------------- */
userController.forgotPassword = async (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  try {
    const user = await UserModel.findOne({
      email: { $regex: `^${email}$`, $options: "i" },
    });
    if (!user) return res.status(400).json({ message: "User not found" });

    const otp = Math.floor(100000 + Math.random() * 900000).toString();

    await OtpModel.findOneAndUpdate(
      { email },
      { otp, createdAt: new Date() },
      { upsert: true, new: true }
    );

    await transporter.sendMail({
      from: process.env.EMAIL_USER,   // fix: was process.env.EMAIL
      to: email,
      subject: "Your OTP for Password Reset",
      html: `<p>Your OTP is <strong>${otp}</strong>. It is valid for 10 minutes.</p>`,
    });

    return res.json({ message: "OTP sent to your email" });
  } catch (error) {
    console.error("Forgot password error:", error);
    return res.status(500).json({ message: "Server error" });
  }
};

userController.verifyOTP = async (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  const otp = String(req.body?.otp || "").trim();
  try {
    const record = await OtpModel.findOne({ email });
    if (!record || record.otp !== otp) {
      return res.status(400).json({ message: "Invalid OTP" });
    }

    const otpAge = (new Date() - new Date(record.createdAt)) / (1000 * 60);
    if (otpAge > 10) return res.status(400).json({ message: "OTP expired" });

    return res.json({ message: "OTP verified" });
  } catch (err) {
    return res.status(500).json({ message: "Server error" });
  }
};

userController.resetPassword = async (req, res) => {
  const email = String(req.body?.email || "").trim().toLowerCase();
  const newPassword = String(req.body?.newPassword || "").trim();
  try {
    const hashedPassword = await bcrypt.hash(newPassword, 10);
    await UserModel.findOneAndUpdate({ email }, { password: hashedPassword });
    await OtpModel.deleteOne({ email }); // Clean up OTP
    return res.json({ message: "Password reset successful" });
  } catch (err) {
    return res.status(500).json({ message: "Server error" });
  }
};


/* -------------------- Update Profile -------------------- */
userController.updateProfile = async (req, res) => {
  try {
    const { id } = req.userInfo;
    const { name, stream, year } = req.body;
    const updates = {};
    if (name)   updates.name   = String(name).trim();
    if (stream) updates.stream = String(stream).trim();
    if (year)   updates.year   = Number(year);

    const user = await UserModel.findByIdAndUpdate(id, updates, { new: true }).select("-password");
    if (!user) return res.status(404).json({ error: true, message: "User not found" });
    return res.json({ error: false, message: "Profile updated successfully", user });
  } catch (error) {
    console.error("Update Profile Error:", error);
    return res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

module.exports = { userController };
