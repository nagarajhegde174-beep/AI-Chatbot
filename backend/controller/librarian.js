const mongoose = require("mongoose");
const { BorrowModel } = require("../model/BorrowModel");
const { BookModel } = require("../model/BookModel");
const calculateFine = require("../utils/fineCalculator");
const { clearCache } = require("../utils/cache");

const librarianController = {};

/* ===================== ISSUED BOOKS ===================== */
librarianController.bookIssued = async (req, res) => {
  try {
    const requests = await BorrowModel.find({ status: "Issued" })
      .populate("userId", "name email")
      .populate("bookId", "title")
      .sort({ createdAt: -1 });

    res.status(200).json({
      message: "Issued books fetched successfully",
      requests,
    });
  } catch (err) {
    console.error("Error fetching issued books", err);
    res.status(500).json({ error: "Server error" });
  }
};

/* ===================== ISSUE REQUESTS ===================== */
librarianController.issueRequest = async (req, res) => {
  try {
    const requests = await BorrowModel.find({ status: "Requested" })
      .populate("userId", "name email")
      .populate("bookId", "title")
      .sort({ createdAt: -1 });

    res.status(200).json({
      message: "Issue requests fetched successfully",
      requests,
    });
  } catch (err) {
    console.error("Error fetching issue requests", err);
    res.status(500).json({ error: "Server error" });
  }
};

/* ===================== APPROVE ISSUE ===================== */
librarianController.approveRequest = async (req, res) => {
  try {
    const borrowId = req.params.id;

    if (!mongoose.Types.ObjectId.isValid(borrowId)) {
      return res.status(400).json({ message: "Invalid borrow id" });
    }

    const borrow = await BorrowModel.findById(borrowId);
    if (!borrow) {
      return res.status(404).json({ message: "Borrow request not found" });
    }

    if (borrow.status !== "Requested") {
      return res.status(400).json({
        message: `Cannot approve request in status "${borrow.status}"`,
      });
    }

    const issuedCount = await BorrowModel.countDocuments({
      userId: borrow.userId,
      status: "Issued",
    });

    if (issuedCount >= 4) {
      return res.status(400).json({
        message: "User already has 4 issued books",
      });
    }

    const book = await BookModel.findById(borrow.bookId);
    if (!book || book.availableCopies < 1) {
      return res.status(400).json({
        message: "Book not available",
      });
    }

    // approve borrow
    borrow.status = "Issued";
    borrow.issueDate = new Date();
    borrow.dueDate = new Date(Date.now() + 15 * 24 * 60 * 60 * 1000);
    borrow.approvedBy = req.userInfo?.id || null;
    await borrow.save();

    // decrement available copies (NO save, NO validation)
    await BookModel.updateOne(
      { _id: book._id },
      { $inc: { availableCopies: -1 } }
    );

    clearCache("homeData");
    res.status(200).json({
      message: "Book issued successfully",
      borrow,
    });
  } catch (err) {
    console.error("approveRequest ERROR:", err);
    res.status(500).json({ message: "Internal Server Error" });
  }
};

/* ===================== RETURN REQUESTS ===================== */
librarianController.returnRequest = async (req, res) => {
  try {
    const requests = await BorrowModel.find({ status: "Requested Return" })
      .populate("userId", "name email")
      .populate("bookId", "title")
      .sort({ createdAt: -1 });

    const requestsWithFine = requests.map((item) => {
      const fine = calculateFine(item.dueDate, item.returnDate);
      return { ...item.toObject(), fine };
    });

    res.status(200).json({
      message: "Return requests fetched successfully",
      requests: requestsWithFine,
    });
  } catch (err) {
    console.error("Error fetching return requests", err);
    res.status(500).json({ error: "Server error" });
  }
};

/* ===================== APPROVE RETURN ===================== */
librarianController.approveReturnRequest = async (req, res) => {
  try {
    const borrowId = req.params.id;

    if (!mongoose.Types.ObjectId.isValid(borrowId)) {
      return res.status(400).json({ message: "Invalid borrow id" });
    }

    const borrow = await BorrowModel.findById(borrowId);
    if (!borrow) {
      return res.status(404).json({ message: "Borrow record not found" });
    }

    if (borrow.status !== "Requested Return") {
      return res.status(400).json({
        message: "Book return not requested or already processed",
      });
    }

    // increment available copies safely (NO book.save())
    await BookModel.updateOne(
      { _id: borrow.bookId },
      { $inc: { availableCopies: 1 } }
    );

    // finalize borrow
    borrow.status = "Returned";
    borrow.returnDate = new Date();
    borrow.approvedBy = req.userInfo?.id || null;
    await borrow.save();

    try {
      clearCache("homeData");
    } catch (e) {
      console.warn("Cache clear failed (ignored)");
    }

    res.status(200).json({
      message: "Book return approved successfully",
    });
  } catch (err) {
    console.error("approveReturnRequest ERROR:", err);
    res.status(500).json({ message: "Internal Server Error" });
  }
};

module.exports = { librarianController };
