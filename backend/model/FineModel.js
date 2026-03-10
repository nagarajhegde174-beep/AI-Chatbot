const mongoose = require("mongoose");
const { FineSchema } = require("../schemas/FineSchema");
const FineModel = mongoose.model("Fine", FineSchema);
module.exports = { FineModel };
