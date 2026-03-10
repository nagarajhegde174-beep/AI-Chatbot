const express = require("express");
const router  = express.Router();
const { reservationController } = require("../controller/reservationController");
const { userAuth }  = require("../middlewares/userAuth");
const { checkRole } = require("../middlewares/checkRole");

router.post("/reserve/:bookId",  userAuth, checkRole(["user"]),                   reservationController.reserveBook);
router.delete("/cancel/:id",     userAuth, checkRole(["user"]),                   reservationController.cancelReservation);
router.get("/my",                userAuth, checkRole(["user"]),                   reservationController.getMyReservations);
router.get("/",                  userAuth, checkRole(["admin","librarian"]),      reservationController.getAllReservations);
router.put("/notify/:bookId",    userAuth, checkRole(["admin","librarian"]),      reservationController.notifyNextReservation);
router.put("/fulfill/:id",       userAuth, checkRole(["admin","librarian"]),      reservationController.fulfillReservation);

module.exports = router;
