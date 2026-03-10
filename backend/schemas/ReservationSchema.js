const { Schema } = require("mongoose");

const ReservationSchema = new Schema({
  bookId:          { type: Schema.Types.ObjectId, ref: "Book", required: true },
  userId:          { type: Schema.Types.ObjectId, ref: "User", required: true },
  reservationDate: { type: Date, default: Date.now },
  expiryDate:      { type: Date, default: () => new Date(Date.now() + 7*24*60*60*1000) },
  status:          { type: String, enum: ["Pending","Notified","Fulfilled","Cancelled","Expired"], default: "Pending" },
  notifiedAt:      { type: Date, default: null },
}, { timestamps: true });

module.exports = { ReservationSchema };
