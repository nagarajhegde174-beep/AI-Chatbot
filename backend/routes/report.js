const express = require("express");
const router  = express.Router();
const { reportController } = require("../controller/reportController");
const { userAuth }  = require("../middlewares/userAuth");
const { checkRole } = require("../middlewares/checkRole");

const staffOnly = checkRole(["admin","librarian"]);

router.get("/summary",          userAuth, staffOnly, reportController.summaryReport);
router.get("/issued",           userAuth, staffOnly, reportController.issuedBooksReport);
router.get("/overdue",          userAuth, staffOnly, reportController.overdueBooksReport);
router.get("/fines",            userAuth, staffOnly, reportController.fineCollectionReport);
router.get("/most-borrowed",    userAuth, staffOnly, reportController.mostBorrowedBooks);
router.get("/member-activity",  userAuth, staffOnly, reportController.memberActivityReport);

module.exports = router;
