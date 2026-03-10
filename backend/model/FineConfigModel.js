const mongoose = require("mongoose");
const { FineConfigSchema } = require("../schemas/FineConfigSchema");
const FineConfigModel = mongoose.model("FineConfig", FineConfigSchema);
module.exports = { FineConfigModel };
