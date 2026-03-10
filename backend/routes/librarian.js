const express = require("express");
const router  = express.Router();
const { librarianController } = require("../controller/librarian");
const { userAuth }  = require("../middlewares/userAuth");
const { checkRole } = require("../middlewares/checkRole");

// Both admin and librarian can see issued books
router.get("/bookissued",          userAuth, checkRole(["admin","librarian"]), librarianController.bookIssued);

// Both admin and librarian can see/approve issue requests
router.get("/issuerequest",        userAuth, checkRole(["admin","librarian"]), librarianController.issueRequest);
router.put("/approverequest/:id",  userAuth, checkRole(["admin","librarian"]), librarianController.approveRequest);

// Both admin and librarian can see/approve return requests
router.get("/returnrequest",       userAuth, checkRole(["admin","librarian"]), librarianController.returnRequest);
router.put("/approvereturnrequest/:id", userAuth, checkRole(["admin","librarian"]), librarianController.approveReturnRequest);

module.exports = router;
