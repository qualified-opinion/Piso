# Batch 17: password-protected backup file (U14)

Jan picked this on 2026-11-06 as the next "Later ideas" item. A backup file holds every amount and name in plain text, so a copy that leaves the phone (Drive, email, a PC) can be read by anyone. It needs no database change.

- **Repo:** `qualified-opinion/Piso`
- **Branch:** `claude/project-thread-vkg33n`, restarted from `main` at `4c250c1` (B16).

## Scope
- **Export File** first asks "Protect this backup?": a password typed twice (at least 8 characters), or **Save without a password** as before. Then the usual save picker opens. A protected file is named `Piso_Backup_<date>_protected.json`.
- The dialog says plainly that Piso doesn't keep the password, so a forgotten password means the backup can't be opened.
- **Import File** and **Paste Text** recognise a protected backup and ask for its password. A wrong password says so and lets Jan try again. Once it's unlocked, the usual restore preview and confirmation follow, with the safety copy and undo as before.
- If the password is lost between the dialog and the save (for example, Android closed Piso while the picker was open), nothing is written and Jan is asked to try again, so a backup meant to be protected is never saved unprotected.
- **Unchanged:** plain backups still save and restore, and Copy Text, Share and CSV stay unprotected. The safety copies inside the app stay as they are, because they never leave the phone and are excluded from Android backup.
- Saving a protected file counts for the backup reminder, as any saved backup file does.

## How the file is protected
The backup JSON is encrypted with AES-256-GCM. The key is derived from the password with PBKDF2-HMAC-SHA256 (150,000 rounds) and a random 16-byte salt, and each save uses a fresh 12-byte IV. The file is a small JSON wrapper (`format: piso-encrypted-backup`) holding the round count, salt, IV and encrypted data. GCM detects a wrong password or any change to the file. The password is held in memory only until the save or unlock finishes, then wiped. It is never stored or logged. An older Piso reports a protected file as "not a valid backup" and doesn't restore anything.

## Tests
`BackupCryptoTest`: the right password returns the same backup; the protected file shows no names or amounts and older readers reject it; a wrong password or a changed file is refused; each save uses a fresh salt and IV; damaged or unsupported files aren't reported as a wrong password; short or mismatched passwords are refused; unlocking keeps the file locked on a wrong password and reads it on the right one.

## Phone checks
Settings, Export File, enter a password twice, save. Open the file in a text viewer and see no names or amounts. Import File, pick it, enter a wrong password and see the message, then the right one and see the restore preview. Export again with "Save without a password" and see it restore with no password asked.

## Out of scope
Protecting Copy Text and Share, changing a saved file's password, and storing or recovering a forgotten password.
