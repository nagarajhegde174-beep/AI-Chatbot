const express = require("express");
const router  = express.Router();
const { fineController } = require("../controller/fineController");
const { userAuth }  = require("../middlewares/userAuth");
const { checkRole } = require("../middlewares/checkRole");

router.get("/config",              userAuth, checkRole(["admin","librarian"]), fineController.getFineConfig);
router.put("/config",              userAuth, checkRole(["admin"]),             fineController.updateFineConfig);
router.post("/generate/:borrowId", userAuth, checkRole(["admin","librarian"]), fineController.generateFine);
router.get("/",                    userAuth, checkRole(["admin","librarian"]), fineController.getAllFines);
router.get("/my",                  userAuth, checkRole(["user"]),              fineController.getMyFines);
router.put("/pay/:id",             userAuth, checkRole(["admin","librarian"]), fineController.markFinePaid);

module.exports = router;
