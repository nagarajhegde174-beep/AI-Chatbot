const express = require("express");
const router  = express.Router();
const { adminController } = require("../controller/admin");
const { userAuth }  = require("../middlewares/userAuth");
const { checkRole } = require("../middlewares/checkRole");

const adminOnly = [userAuth, checkRole(["admin"])];

// Auth
router.post("/login",                            adminController.login);

// Librarian management
router.post("/addlibrarian",    ...adminOnly,    adminController.addLibrarian);
router.get("/librarians",       ...adminOnly,    adminController.getLibrarians);
router.delete("/librarian/:id", ...adminOnly,    adminController.deleteLibrarian);

// Member management
router.get("/members",          ...adminOnly,    adminController.getMembers);
router.put("/users/:id/toggle", ...adminOnly,    adminController.toggleUserStatus);
router.get("/members/:id/history", ...adminOnly, adminController.getMemberBorrowHistory);

// Fine configuration
router.get("/fine-config",      ...adminOnly,    adminController.getFineConfig);
router.put("/fine-config",      ...adminOnly,    adminController.updateFineConfig);

module.exports = router;
