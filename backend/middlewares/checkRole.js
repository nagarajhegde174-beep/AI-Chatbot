// Supports both: checkRole("admin") and checkRole(["admin","librarian"])
const checkRole = (allowedRoles) => {
  return (req, res, next) => {
    const allowed = Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles];
    if (!req.userInfo || !allowed.includes(req.userInfo.role)) {
      return res.status(403).json({ error: true, message: "Access Denied: Unauthorized role" });
    }
    next();
  };
};

module.exports = { checkRole };
