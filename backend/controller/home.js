const { BookModel } = require("../model/BookModel");
const { BorrowModel } = require("../model/BorrowModel");

const homeController = {};

homeController.getHomeData = async (req, res) => {
  try {
    // books
    const totalBooks = await BookModel.countDocuments({});
    const categories = await BookModel.distinct("category", { category: { $ne: null } });
    const totalCategories = categories.length;

    // issued + borrowers
    const issuedCount = await BorrowModel.countDocuments({ status: "Issued" });
    const borrowerIds = await BorrowModel.distinct("userId", { status: "Issued" });
    const totalBorrowers = borrowerIds.length;

    // latest books for the cards/carousel (adjust limit to your UI)
    const books = await BookModel.find({})
      .sort({ createdAt: -1 })
      .select("title author category coverImage price");

    // return BOTH sets of keys so any Home.jsx variant works
    return res.status(200).json({
      error: false,
      message: "Home data",
      // what some Home.jsx versions expect:
      books,
      totalBooks,
      totalCategories,
      totalActiveStudents: totalBorrowers, // alias
      // what other Home.jsx versions expect (the 4 tiles you see):
      booksCount: totalBooks,
      categoriesCount: totalCategories,
      borrowersCount: totalBorrowers,
      issuedCount,
    });
  } catch (err) {
    console.error("HOME STATS ERROR:", err);
    return res.status(500).json({
      error: true,
      message: "Failed to compute home stats",
      details: err.message,
      // safe fallbacks so UI never crashes
      books: [],
      totalBooks: 0,
      totalCategories: 0,
      totalActiveStudents: 0,
      booksCount: 0,
      categoriesCount: 0,
      borrowersCount: 0,
      issuedCount: 0,
    });
  }
};

module.exports = { homeController };
