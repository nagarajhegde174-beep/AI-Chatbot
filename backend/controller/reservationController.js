const { ReservationModel } = require("../model/ReservationModel");
const { BookModel }        = require("../model/BookModel");

const reservationController = {};

reservationController.reserveBook = async (req, res) => {
  try {
    const userId = req.userInfo.id;
    const { bookId } = req.params;
    const book = await BookModel.findById(bookId);
    if (!book) return res.status(404).json({ error: true, message: "Book not found" });
    if (book.availableCopies > 0)
      return res.status(400).json({ error: true, message: "Book is available. Please issue it directly." });
    const existing = await ReservationModel.findOne({ bookId, userId, status: { $in: ["Pending","Notified"] } });
    if (existing) return res.status(400).json({ error: true, message: "You already have an active reservation for this book." });
    const reservation = new ReservationModel({ bookId, userId });
    await reservation.save();
    res.status(201).json({ error: false, message: "Book reserved. You will be notified when available.", reservation });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reservationController.cancelReservation = async (req, res) => {
  try {
    const userId = req.userInfo.id;
    const reservation = await ReservationModel.findById(req.params.id);
    if (!reservation) return res.status(404).json({ error: true, message: "Reservation not found" });
    if (reservation.userId.toString() !== userId.toString())
      return res.status(403).json({ error: true, message: "Unauthorized" });
    if (["Fulfilled","Cancelled","Expired"].includes(reservation.status))
      return res.status(400).json({ error: true, message: "Reservation cannot be cancelled." });
    reservation.status = "Cancelled";
    await reservation.save();
    res.status(200).json({ error: false, message: "Reservation cancelled successfully." });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reservationController.getMyReservations = async (req, res) => {
  try {
    const reservations = await ReservationModel.find({ userId: req.userInfo.id })
      .populate("bookId", "title author coverImage category")
      .sort({ createdAt: -1 });
    res.status(200).json({ error: false, reservations });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reservationController.getAllReservations = async (req, res) => {
  try {
    const reservations = await ReservationModel.find()
      .populate("bookId", "title author isbn")
      .populate("userId", "name email membershipId")
      .sort({ createdAt: -1 });
    res.status(200).json({ error: false, reservations, total: reservations.length });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reservationController.notifyNextReservation = async (req, res) => {
  try {
    const next = await ReservationModel.findOne({ bookId: req.params.bookId, status: "Pending" })
      .sort({ createdAt: 1 }).populate("userId", "name email");
    if (!next) return res.status(404).json({ error: true, message: "No pending reservation for this book." });
    next.status = "Notified";
    next.notifiedAt = new Date();
    await next.save();
    res.status(200).json({ error: false, message: `Member ${next.userId.name} notified.`, reservation: next });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

reservationController.fulfillReservation = async (req, res) => {
  try {
    const reservation = await ReservationModel.findById(req.params.id);
    if (!reservation) return res.status(404).json({ error: true, message: "Not found" });
    reservation.status = "Fulfilled";
    await reservation.save();
    res.status(200).json({ error: false, message: "Reservation fulfilled." });
  } catch (err) {
    res.status(500).json({ error: true, message: "Internal Server Error" });
  }
};

module.exports = { reservationController };
