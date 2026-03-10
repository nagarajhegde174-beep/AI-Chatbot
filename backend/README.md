✅ STEP 1 — Create .env File in Backend

Go to:

Library-Mnagement-System/
   └── backend/

Inside backend, create a file named:

.env

⚠️ Important:

File name must be exactly .env

Not .env.txt

Not env

No extension

✅ STEP 2 — Add Required Variables

Open .env and paste this:

# ==============================
# DATABASE
# ==============================
MONGO_URI=mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/library

# ==============================
# JWT AUTH
# ==============================
JWT_SECRET=your_super_secret_key_here

# ==============================
# ADMIN SEED
# ==============================
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=admin123

# ==============================
# EMAIL SERVICE (For OTP/Reset)
# ==============================
EMAIL_USER=your_email@gmail.com
EMAIL_PASS=your_email_password
EMAIL_SERVICE=gmail

# ==============================
# CLOUDINARY (Book Images)
# ==============================
CLOUD_NAME=your_cloud_name
CLOUD_API_KEY=your_cloud_api_key
CLOUD_API_SECRET=your_cloud_api_secret
✅ STEP 3 — Get MongoDB Connection String

Go to MongoDB Atlas

Click Cluster

Click Connect

Click Drivers

Copy connection string

It looks like:

mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority

Now modify it to:

mongodb+srv://username:password@cluster0.xxxxx.mongodb.net/library

⚠️ Use lowercase library
⚠️ Replace username and password

Paste into:

MONGO_URI=
✅ STEP 4 — Create Strong JWT Secret

Generate random secret:

You can use:

node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"

Paste output into:

JWT_SECRET=

Example:

JWT_SECRET=9a8b7c6d5e4f3a2b1c9d8e7f6a5b4c3d
✅ STEP 5 — Setup Gmail (For Nodemailer)

If using Gmail:

Enable 2-Step Verification

Generate App Password

Use that app password in:

EMAIL_PASS=

Example:

EMAIL_USER=librarysystem@gmail.com
EMAIL_PASS=abcd efgh ijkl mnop
EMAIL_SERVICE=gmail
✅ STEP 6 — Setup Cloudinary

Go to https://cloudinary.com

Dashboard → Copy:

Cloud Name

API Key

API Secret

Paste into:

CLOUD_NAME=
CLOUD_API_KEY=
CLOUD_API_SECRET=
✅ STEP 7 — Make Sure dotenv Is Loaded

In your index.js or server.js (top of file):

require("dotenv").config();

Or safer:

require("dotenv").config({ path: __dirname + "/.env" });
✅ STEP 8 — Restart Server

Stop server.

Then run:

nodemon index.js

OR

node seedAdmin.js
🔎 Debug Checklist (If Something Fails)

Print this in backend:

console.log(process.env.MONGO_URI);

