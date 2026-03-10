const mongoose = require("mongoose");
const { ReservationSchema } = require("../schemas/ReservationSchema");
const ReservationModel = mongoose.model("Reservation", ReservationSchema);
module.exports = { ReservationModel };
