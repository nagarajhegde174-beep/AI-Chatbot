const { Schema } = require("mongoose");

const FineSchema = new Schema({
  memberId:    { type: Schema.Types.ObjectId, ref: "User",         required: true },
  borrowId:    { type: Schema.Types.ObjectId, ref: "BorrowedBooks", required: true },
  bookId:      { type: Schema.Types.ObjectId, ref: "Book",         required: true },
  amount:      { type: Number, required: true, default: 0 },
  paidStatus:  { type: Boolean, default: false },
  paidAt:      { type: Date, default: null },
  daysOverdue: { type: Number, default: 0 },
  ratePerDay:  { type: Number, default: 10 },
  date:        { type: Date, default: Date.now },
}, { timestamps: true });

module.exports = { FineSchema };
