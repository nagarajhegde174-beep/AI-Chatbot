const { Schema } = require("mongoose");

function generateMembershipId() {
  const date = new Date();
  const ymd = `${date.getFullYear()}${String(date.getMonth()+1).padStart(2,"0")}${String(date.getDate()).padStart(2,"0")}`;
  const rand = Math.floor(1000 + Math.random() * 9000);
  return `LIB-${ymd}-${rand}`;
}

const UserSchema = new Schema({
  name:         { type: String, required: true },
  email:        { type: String, required: true, unique: true },
  password:     { type: String, required: true },
  role:         { type: String, enum: ["admin","librarian","user"], default: "user" },
  status:       { type: String, enum: ["Active","Inactive"], default: "Active" }, // ✅ NEW
  membershipId: { type: String, unique: true, sparse: true },                     // ✅ NEW
  stream:       { type: String, required: function(){ return this.role === "user"; } },
  year:         { type: Number, required: function(){ return this.role === "user"; } },
}, { timestamps: true });

// Auto-assign membershipId for members
UserSchema.pre("save", function(next) {
  if (this.role === "user" && !this.membershipId) {
    this.membershipId = generateMembershipId();
  }
  next();
});

module.exports = { UserSchema };
