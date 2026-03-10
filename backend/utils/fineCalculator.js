/**
 * Calculates overdue fine.
 * Keeps your original ₹10/day default but now supports config params.
 */
function calculateFine(dueDate, returnDate, ratePerDay = 10, maxCap = 500, gracePeriod = 0) {
  const today = new Date();
  const effectiveReturnDate = returnDate ? new Date(returnDate) : today;
  const due = new Date(dueDate);

  if (effectiveReturnDate > due) {
    const diffTime = effectiveReturnDate - due;
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) - gracePeriod;
    if (diffDays <= 0) return 0;
    return Math.min(diffDays * ratePerDay, maxCap);
  }
  return 0;
}

module.exports = calculateFine;
