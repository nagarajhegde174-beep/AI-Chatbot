// utils/config.js
export const Server_URL =
  (import.meta.env.VITE_BACKEND_URL?.replace(/\/+$/, "") ||
    "http://localhost:5000") + "/";
