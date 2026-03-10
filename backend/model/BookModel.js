const mongoose = require("mongoose");
const { BookSchema } = require("../schemas/BookSchema");

const BookModel = mongoose.model("Book", BookSchema);
module.exports = { BookModel };
