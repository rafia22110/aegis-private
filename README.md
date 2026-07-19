Aegis Mirror — Private Repo (Backend / Real User Data)
מאגר זה חייב להישאר פרטי לחלוטין. אין לפרסמו, ואין להעביר ממנו קוד אל ה-repo הציבורי.

מה יש כאן
db/schema.sql — סכימת SQLite מלאה עם 15 הטבלאות (users, activeprofile, networkpolicies, generatedaliases, trafficlogs, ועוד).
server/perseus_proxy.py — Perseus Proxy: key exchange, alias generation, sync receiver.
.env.example — תבנית בלבד. יש למלא ערכים אמיתיים ב-.env מקומי שלא יעלה ל-git.

אבטחה
.env נמצא ב-.gitignore ולעולם לא מתועד ב-history.
כל secret אמיתי (מפתחות LLM, chiper keys) חייב לשבת רק כ-GitHub Secrets בסביבת ה-CI הפרטית.
אין לשכפל טבלאות מ-schema.sql אל שום קובץ ציבורי.
