const { Schema } = require("mongoose");

const FineConfigSchema = new Schema({
  ratePerDay:  { type: Number, default: 10 },
  maxFineCap:  { type: Number, default: 500 },
  gracePeriod: { type: Number, default: 0 },
  updatedBy:   { type: Schema.Types.ObjectId, ref: "User" },
}, { timestamps: true });

module.exports = { FineConfigSchema };
