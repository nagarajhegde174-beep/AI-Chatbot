const { BorrowModel }  = require("../model/BorrowModel");
const { BookModel }    = require("../model/BookModel");
const { UserModel }    = require("../model/UserModel");
const { FineModel }    = require("../model/FineModel");
const calculateFine    = require("../utils/fineCalculator");

const reportController = {};

reportController.issuedBooksReport = async (req, res) => {
  try {
    const { from, to } = req.query;
    const filter = { status: "Issued" };
    if (from || to) {
      filter.issueDate = {};
      if (from) filter.issueDate.$gte = new Date(from);
      if (to)   filter.issueDate.$lte = new Date(to);
    }
    const records = await BorrowModel.find(filter)
      .populate("bookId", "title author isbn category")
      .populate("userId", "name email membershipId stream year")
      .sort({ issueDate: -1 });
    res.status(200).json({ error: false, total: records.length, records });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reportController.overdueBooksReport = async (req, res) => {
  try {
    const now = new Date();
    const records = await BorrowModel.find({ status: { $in: ["Issued","Requested Return"] }, dueDate: { $lt: now } })
      .populate("bookId", "title author isbn")
      .populate("userId", "name email membershipId")
      .sort({ dueDate: 1 });
    const enriched = records.map(r => {
      const daysOverdue = Math.floor((now - new Date(r.dueDate)) / (1000*60*60*24));
      const fine = calculateFine(r.dueDate, null);
      return { ...r.toObject(), daysOverdue, fine };
    });
    res.status(200).json({ error: false, total: enriched.length, records: enriched });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reportController.fineCollectionReport = async (req, res) => {
  try {
    const { paid } = req.query;
    const filter = {};
    if (paid === "true")  filter.paidStatus = true;
    if (paid === "false") filter.paidStatus = false;
    const fines = await FineModel.find(filter)
      .populate("memberId", "name email membershipId")
      .populate("bookId",   "title author")
      .sort({ date: -1 });
    const totalAmount    = fines.reduce((s,f)=>s+f.amount,0);
    const totalCollected = fines.filter(f=>f.paidStatus).reduce((s,f)=>s+f.amount,0);
    const totalPending   = fines.filter(f=>!f.paidStatus).reduce((s,f)=>s+f.amount,0);
    res.status(200).json({ error: false, fines, totalAmount, totalCollected, totalPending });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reportController.mostBorrowedBooks = async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 10;
    const result = await BorrowModel.aggregate([
      { $group: { _id: "$bookId", borrowCount: { $sum: 1 } } },
      { $sort:  { borrowCount: -1 } },
      { $limit: limit },
      { $lookup: { from: "books", localField: "_id", foreignField: "_id", as: "book" } },
      { $unwind: "$book" },
      { $project: { borrowCount: 1, "book.title": 1, "book.author": 1, "book.category": 1, "book.isbn": 1, "book.coverImage": 1 } },
    ]);
    res.status(200).json({ error: false, books: result });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reportController.memberActivityReport = async (req, res) => {
  try {
    const { memberId } = req.query;
    const filter = memberId ? { userId: memberId } : {};
    const borrows = await BorrowModel.find(filter)
      .populate("bookId", "title author category")
      .populate("userId", "name email membershipId stream year")
      .sort({ issueDate: -1 });
    const memberMap = {};
    for (const b of borrows) {
      if (!b.userId) continue;
      const uid = b.userId._id.toString();
      if (!memberMap[uid]) memberMap[uid] = { member: b.userId, totalBorrowed: 0, currentlyHeld: 0, returned: 0, overdue: 0, history: [] };
      memberMap[uid].totalBorrowed++;
      if (["Issued","Requested Return"].includes(b.status)) {
        memberMap[uid].currentlyHeld++;
        if (new Date(b.dueDate) < new Date()) memberMap[uid].overdue++;
      }
      if (b.status === "Returned") memberMap[uid].returned++;
      memberMap[uid].history.push(b);
    }
    const members = Object.values(memberMap);
    res.status(200).json({ error: false, total: members.length, members });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reportController.summaryReport = async (req, res) => {
  try {
    const now = new Date();
    const [totalBooks, totalMembers, totalIssued, totalOverdue, finesPending, finesCollected] = await Promise.all([
      BookModel.countDocuments(),
      UserModel.countDocuments({ role: "user" }),
      BorrowModel.countDocuments({ status: "Issued" }),
      BorrowModel.countDocuments({ status: { $in: ["Issued","Requested Return"] }, dueDate: { $lt: now } }),
      FineModel.aggregate([{ $match: { paidStatus: false } }, { $group: { _id: null, total: { $sum: "$amount" } } }]),
      FineModel.aggregate([{ $match: { paidStatus: true  } }, { $group: { _id: null, total: { $sum: "$amount" } } }]),
    ]);
    res.status(200).json({ error: false, summary: { totalBooks, totalMembers, totalIssued, totalOverdue, totalFinesPending: finesPending[0]?.total || 0, totalFinesCollected: finesCollected[0]?.total || 0 } });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

module.exports = { reportController };
