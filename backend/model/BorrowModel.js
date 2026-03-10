const mongoose = require("mongoose");
const { BorrowSchema } = require("../schemas/BorrowSchema");

const BorrowModel = mongoose.model("BorrowedBooks", BorrowSchema);
module.exports = { BorrowModel };
